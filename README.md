# BetterStructures (Fork)

基于 [MagmaGuy/BetterStructures](https://github.com/MagmaGuy/BetterStructures) 的改进分支，针对 **Paper + FastAsyncWorldEdit (FAWE) + Terra** 生态做了深度适配与性能优化。

> 原版插件: [GitHub](https://github.com/MagmaGuy/BetterStructures) | [Modrinth](https://modrinth.com/plugin/betterstructures)

---

## 与原版的主要差异

### 1. FAWE 异步粘贴 (彻底消除主线程阻塞)

原版使用 `WorkloadRunnable` 在主线程上分帧放置方块，大型建筑仍会造成 TPS 抖动。本分支将所有方块粘贴迁移到 FAWE 异步 `EditSession`：

- 普通方块、NBT 方块 (刷怪笼、发射器等)、bedrock 替换全部通过 FAWE 异步线程处理
- 移除了 `WorkloadRunnable` 分帧系统和 NMS 快速路径
- 实体生成和箱子填充在 FAWE 完成后回到主线程执行
- `percentageOfTickUsedForPasting` 配置项已废弃 (不再需要)

**依赖变更**: 硬依赖从 `WorldEdit` 改为 `FastAsyncWorldEdit`。启动时若检测不到 FAWE 将自动禁用插件。

### 2. Terra / FAWE 兼容性

解决了 Terra 等异步世界生成器与 FAWE 配合时的区块状态问题：

- 新增 `ChunkValidationUtil`：通过采样多个位置检测区块是否已完全生成，避免在空区块上放置建筑
- 使用 Paper API `getChunkAtAsync` 异步加载区块，并添加 `PluginChunkTicket` 在粘贴期间保持区块不被卸载
- 所有涉及世界方块访问的操作都确保区块已加载到内存

### 3. PersistentDataContainer 区块标记

原版依赖 `ChunkLoadEvent.isNewChunk()` 判断是否为新区块，服务器重启后已生成但未标记的区块会被重复扫描。本分支改用 Bukkit `PersistentDataContainer` 在区块上持久化标记：

- 已处理的区块写入 `betterstructures:chunk_processed` 标记
- 重启后不会重复扫描已处理的区块

### 4. 结构位置持久化系统

新增 `StructureLocationManager` 和 `StructureLocationData`，将所有生成的建筑位置持久化到 YAML 文件：

- 每个世界独立存储在 `structure_locations/` 目录下
- 记录建筑坐标、类型、原理图名称、包围盒尺寸
- 内存缓存 + 每分钟自动保存脏数据
- 为怪物追踪系统提供数据基础

### 5. 怪物追踪与重生系统

新增 `MobTrackingManager` 和 `MobSpawnConfig`，实现结构内怪物的追踪和重生：

- 记录每个结构内的怪物生成配置 (位置、类型)
- 支持 Vanilla / EliteMobs / MythicMobs 三种怪物类型
- 空间索引 (世界-区块) 快速查找附近结构
- 玩家靠近时触发怪物重生检查
- 怪物全部被击杀后触发 `StructureClearedEvent` 事件

### 6. 中文本地化

所有用户可见的日志、命令反馈、菜单文本均已翻译为中文，涉及 27 个文件。

---

## 环境要求

| 组件 | 要求 |
|------|------|
| Minecraft | 1.14+ (推荐 1.21+) |
| 服务端 | Paper (或其分支如 Purpur) |
| Java | 21+ |
| **FastAsyncWorldEdit** | **必须安装** (不支持原版 WorldEdit) |

### 可选依赖

- EliteMobs - 自定义 Boss
- MythicMobs - 自定义怪物
- WorldGuard - 区域保护
- Terra / Terralith / Iris / TerraformGenerator - 自定义世界生成

---

## 构建

```bash
./gradlew shadowJar
```

产物位于 `testbed/plugins/BetterStructures.jar`。

---

## 完整变更列表

以下为本分支相对于上游的所有提交 (按时间顺序)：

1. **feat: 添加结构 location** - 结构位置持久化系统
2. **feat: 添加怪物追踪和重生系统** - MobTrackingManager, MobSpawnConfig, MobDeathListener, StructureClearedEvent
3. **Merge upstream/master** - 同步上游 2.1.2 版本
4. **feat: add Terra + FAWE compatibility** - 延迟区块扫描，适配 Terra 异步世界生成
5. **feat: add structure queue system** - 结构放置队列，防止并发粘贴冲突
6. **fix: prevent duplicate structure paste** - 修复队列系统重复粘贴
7. **fix: use Paper API for reliable async chunk loading** - 使用 Paper 异步区块 API
8. **feat: add chunk generation check at paste level** - 粘贴前校验区块完整性
9. **refactor: simplify to single-layer chunk check** - 精简为 Schematic 层统一校验
10. **fix: ensure chunks are generated before world block access** - 确保区块已生成 + 并行化原理图加载
11. **汉化** - 全量中文本地化 (27 个文件)
12. **refactor: 将方块粘贴迁移到 FAWE 异步 EditSession** - 彻底消除主线程阻塞，移除 WorkloadRunnable
13. **fix: 用 PersistentDataContainer 替代 isNewChunk** - 持久化区块处理标记，防止重启后重复扫描

---

## 许可证

GPL-3.0，与原版一致。

## 致谢

- 原版作者: [MagmaGuy](https://github.com/MagmaGuy)
- 原版仓库: https://github.com/MagmaGuy/BetterStructures
- Modrinth: https://modrinth.com/plugin/betterstructures
