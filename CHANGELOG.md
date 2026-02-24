# Changelog

All notable changes to BetterStructures-FAWE will be documented in this file.

## [2.1.2-FAWE.8]

### Changed

- **成功后标记语义**: `chunk_processed` 语义调整为“仅在结构粘贴成功后写入”。扫描失败或粘贴失败不再写入标记，失败区块可在后续加载时重试；已成功区块仍保持幂等跳过。
- **Terra 兼容校验收敛**: `validateChunkBeforePaste` 现在在粘贴前逐区块执行 `isChunkFullyGenerated`，任一校验失败即终止本次粘贴并返回失败信号。
- **扫描一致性**: `TerrainAdequacy` 对未加载区块改为保守失败，避免与 `Topology` 的未加载处理语义冲突。

### Added

- **可观测性日志事件**: 新增/统一 `SKIP_PROCESSED`、`SCAN_FAILED`、`PASTE_FAILED`、`PASTE_SUCCESS_MARKED` 四类调试日志，覆盖自然生成关键路径。

## [2.1.2-FAWE.7]

### Added

- **原版替换概率**: 新增 `mythicMobsOverride.vanillaReplaceChance` 配置项（0-100），控制功能一（原版生物替换）的触发概率。默认 100（必定替换）。重生时同样适用概率判断，未通过则生成原版生物。不影响功能二（EM Boss 替换）。

## [2.1.2-FAWE.6]

### Added

- **实体类型白名单**: 新增 `mythicMobsOverride.entityTypeWhitelist.enabled` 开关和 `mythicMobsOverride.entityTypeWhitelist.types` 列表。开启后，功能一（原版生物替换）仅替换白名单中指定的原版实体类型（如 ZOMBIE、SKELETON、CREEPER），其他类型保持原样。默认关闭，不影响功能二（EM Boss 替换）。

## [2.1.2-FAWE.5]

### Added

- **全局怪物黑名单**: 新增 `mythicMobsOverride.mobBlacklist` 配置项，黑名单内的 MythicMobs 在功能一（原版生物替换）和功能二（EliteMobs Boss 替换）中均被跳过，不会被选为替换对象。

## [2.1.2-FAWE.4]

### Added

- **MythicMobs 覆盖系统**: 新增 MythicMobs 集成功能，当结构放置时自动将原版生物和 EliteMobs Boss 替换为 MythicMobs 等价生物。
  - 原版生物（`[spawn]` 告示牌）可自动替换为具有相同基础类型的 MythicMobs 生物（如所有 Type: ZOMBIE 的 MM 生物替换原版僵尸）。
  - EliteMobs Boss（`[elitemobs]` 告示牌）可替换为配置列表中的随机 MythicMobs Boss。
  - 新增 `MobType` 枚举值 `VANILLA_MM_OVERRIDE`（重生时重新随机选择）和 `ELITEMOBS_MM_OVERRIDE`（不重生）。
  - 插件启动时自动构建类型映射缓存，查询所有已注册 MythicMobs 并按 Bukkit EntityType 分组。
  - 新增配置项：`mythicMobsOverride.enabled`、`mythicMobsOverride.replaceVanillaMobs`、`mythicMobsOverride.replaceEliteMobsBosses`、`mythicMobsOverride.mythicBossList`。
  - 默认关闭，需手动启用并安装 MythicMobs 插件。
- **Boss 结构标记**: 结构数据新增 `bossStructure` 字段，自动检测包含 EliteMobs 刷怪点或 MythicMobs Boss 的结构，并持久化存储。`/bs info` 命令现显示 Boss 结构状态。

## [2.1.2-FAWE.3]

### Added

- **结构信息查询命令**: 新增 `/bs info` 命令，玩家站在已生成的结构范围内时可查看该结构的详细信息，包括建筑模板名称、结构类型、坐标、范围、怪物状态、创建时间等。

## [2.1.2-FAWE.2]

### Changed

- **Async terrain scanning (P0)**: Moved `runScanners()` in `NewChunkLoadEvent` from synchronous `runTaskLater` to `runTaskAsynchronously`. All terrain fitness scanning (`Topology.scan()`, `TerrainAdequacy.scan()`, `getBlock()`, `getHighestBlockAt()`) now runs off the main thread, eliminating TPS impact during chunk exploration. `markChunkProcessed()` is dispatched back to the main thread for safe `PersistentDataContainer` writes.
- **Thread-safe paste dispatch (P0)**: `FitAnything.paste()` now detects whether it is called from the main thread or an async thread, and automatically schedules `BuildPlaceEvent` and `Schematic.pasteSchematic()` to the main thread when needed.
- **Parallel SchematicContainer initialization (P1)**: `SchematicContainer` creation (triple-nested clipboard scanning for chests, signs, spawns) now runs via `parallelStream()` during startup, with `synchronized` protection on the shared `schematics` multimap.
- **Async config file saving (P2)**: New `AsyncConfigSaver` utility snapshots `FileConfiguration` via `saveToString()` on the calling thread, then writes to disk asynchronously. Applied to `SchematicConfigField.toggleEnabled()` and `ModulesConfigFields.validateClones()`.
- **Non-blocking WFC debug paste (P3)**: Replaced `Thread.sleep(50)` in `WFCNode.debugPaste()` with `runTaskLater(1L)`, eliminating async thread blocking during WFC debug visualization.
- **dungeonScanner dispatched to main thread**: `WFCGenerator` constructor is not async-safe (`BossBar`, non-thread-safe `HashSet`), so `dungeonScanner` is now explicitly scheduled back to the main thread from the async scan pipeline.

### Fixed

- **dungeonScanner IndexOutOfBoundsException**: Fixed pre-existing bug where `ThreadLocalRandom.nextInt()` used `ModuleGeneratorsConfig.getModuleGenerators().size()` as the upper bound instead of the filtered `validatedGenerators.size()`, causing potential `IndexOutOfBoundsException` when some generators are filtered out by world/environment checks.

## [2.1.2-FAWE.1]

Based on upstream BetterStructures 2.1.2. All changes below are relative to the original release.

### Fixed

- **PersistentDataContainer for chunk tracking**: Replaced `isNewChunk` flag with PersistentDataContainer to reliably track processed chunks, preventing duplicate structure placement on server restart.
- **Cross-chunk structure placement**: Fixed 5x5 transparent chunk holes by verifying chunk generation before world block access in `createPasteBlocks()` and `assignPedestalMaterial()`, ensuring all surrounding chunks are loaded before pasting.

### Changed

- **Async block pasting via FAWE**: Migrated block pasting from the WorkloadRunnable frame-splitting system to FAWE asynchronous EditSession, completely eliminating main thread blocking.
  - Hard dependency changed from WorldEdit to FastAsyncWorldEdit in `plugin.yml`.
  - FAWE is detected on startup; plugin disables itself if FAWE is not present.
  - Removed WorkloadRunnable system; all blocks (including NBT) are now pasted through a single FAWE async EditSession.
  - Bedrock checking uses `editSession.getBlock()` asynchronously.
  - Entity spawning and chest filling are deferred to the main thread after async paste completion.
  - Removed deprecated `createSingleBlockClipboard` method from WorldEditUtils.
- **Parallelized schematic loading**: Schematic and module file loading now uses `parallelStream()`, significantly reducing plugin startup time. Timing logs added for diagnostics.

### Added

- **Chinese localization**: Full Chinese language support throughout the codebase.
- **README**: Added project README.

### Notes

- The config option `percentageOfTickUsedForPasting` is now obsolete due to the fully async architecture.
