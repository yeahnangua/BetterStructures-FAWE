# 结构粘贴区块加载 I/O 限流设计

## 问题

BetterStructures 在粘贴结构时需要预加载结构占地范围 + 1 chunk padding 的所有区块。
当玩家快速跑图（尤其是多人同时跑图）时，大量结构同时触发生成，
导致短时间内产生大量 `getChunkAtAsync` 调用，在 HDD 服务器上可能打爆磁盘随机 I/O。

**I/O 放大估算**：一个 5x5 chunk 的结构 + 1 padding = 7x7 = 49 个区块请求。
如果其中一半未加载，约 25 次磁盘随机读。
多个结构同时触发时，I/O 请求量成倍增加。

## 方案

三层 I/O 保护，逐级削减磁盘压力：

### L1：已加载区块过滤

在调用 `getChunkAtAsync` 之前，先用 `world.isChunkLoaded(cx, cz)` 检查区块是否已在内存中。
已加载的区块直接跳过，不产生任何异步请求。

- 改动位置：`Schematic.pasteSchematic()` 的 Step 2
- 始终启用，无配置项
- 风险：`isChunkLoaded` 检查和实际加载之间存在竞态条件，
  但最坏情况只是多加载一次已加载的区块，Paper 会直接返回

### L2：全局区块加载速率限制器

新建 `ChunkLoadRateLimiter` 类，控制插件每秒触发的 `getChunkAtAsync` 调用数量。

- 使用 `Bukkit.getScheduler().runTaskTimer()` 每 20 tick（1 秒）执行一次
- 每次从全局请求队列中取出最多 `maxChunkLoadsPerSecond` 个请求
- 每个请求关联一个 `CompletableFuture<Chunk>`，加载完成时完成
- 调用方通过 `ChunkLoadRateLimiter.loadChunks(world, chunkKeys)` 获取 `CompletableFuture<Void>`
- 多个并发粘贴共享同一个速率配额

配置项：`maxChunkLoadsPerSecond`，默认 `10`

### L3：全局粘贴并发上限

新建 `StructurePasteQueue` 类，限制同时进行的结构粘贴数量。

- 所有 `pasteSchematic` 调用通过队列调度
- 同时最多 N 个粘贴在执行（包括区块加载 + FAWE 粘贴全流程）
- FIFO 顺序，不做距离排序
- 一个粘贴完成后自动触发下一个
- 队列容量不设上限（结构生成频率受网格间距控制）

配置项：`maxConcurrentStructurePastes`，默认 `2`

## 数据流

```
FitAnything.paste()
  → Schematic.pasteSchematic()
    → StructurePasteQueue.enqueue(pasteTask)          [L3: 并发控制]
      → calculateRequiredChunks()
      → 过滤已加载区块                                  [L1: 减少请求]
      → ChunkLoadRateLimiter.loadChunks(unloadedKeys)  [L2: 速率限制]
        → 每秒最多 N 个 getChunkAtAsync
      → 全部加载完成
      → addPluginChunkTicket
      → executeFaweAsyncPaste
      → removePluginChunkTicket
      → StructurePasteQueue.onPasteComplete()           [触发下一个]
```

## 改动文件

| 文件 | 改动 |
|------|------|
| 新建 `util/ChunkLoadRateLimiter.java` | 全局区块加载速率限制器 |
| 新建 `util/StructurePasteQueue.java` | 全局粘贴队列 |
| `worldedit/Schematic.java` | 集成 L1 过滤 + L2 速率限制 + L3 队列 |
| `config/DefaultConfig.java` | 新增 2 个配置项 |
| `MetadataHandler.java` | 插件启停时初始化/清理限流器 |

## 配置项

```yaml
# 同时进行的最大结构粘贴数量。降低此值可减少磁盘 I/O 压力。
maxConcurrentStructurePastes: 2

# 插件每秒触发的最大区块加载数量（所有粘贴共享此配额）。
maxChunkLoadsPerSecond: 10
```

## 日志（DEBUG 级别）

- 入队：`"Structure paste queued at X,Y,Z (queue size: N, active: M)"`
- 开始：`"Structure paste started at X,Y,Z (skipped N already-loaded chunks, loading M chunks)"`
- 完成：`"Structure paste completed at X,Y,Z (active: M, queued: N)"`

## 区块生命周期说明

预加载的区块在粘贴完成后释放 chunk ticket。
如果区块在玩家 view-distance 内，继续保持加载；否则由 Paper 按正常逻辑卸载写盘。
卸载写回的 I/O 由 Paper 内部管理，不做额外限流。
