# Changelog

All notable changes to BetterStructures-FAWE will be documented in this file.

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
