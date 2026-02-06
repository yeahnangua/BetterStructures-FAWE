# BetterStructures-FAWE

[中文](README.zh-CN.md)

A performance-focused fork of [MagmaGuy/BetterStructures](https://github.com/MagmaGuy/BetterStructures), deeply adapted for the **Paper + FastAsyncWorldEdit (FAWE) + Terra** ecosystem.

> Original plugin: [GitHub](https://github.com/MagmaGuy/BetterStructures) | [Modrinth](https://modrinth.com/plugin/betterstructures)

---

## Key Differences from Upstream

### 1. FAWE Async Pasting (Zero Main-Thread Blocking)

The original plugin uses `WorkloadRunnable` to place blocks on the main thread in frame-split batches, which still causes TPS drops for large structures. This fork migrates all block pasting to FAWE asynchronous `EditSession`:

- Normal blocks, NBT blocks (spawners, dispensers, etc.), and bedrock replacement are all handled on FAWE async threads
- The `WorkloadRunnable` frame-splitting system and NMS fast paths have been removed
- Entity spawning and chest filling are deferred back to the main thread after FAWE completes
- The `percentageOfTickUsedForPasting` config option is now obsolete

**Dependency change**: Hard dependency changed from `WorldEdit` to `FastAsyncWorldEdit`. The plugin will automatically disable itself if FAWE is not detected on startup.

### 2. Terra / FAWE Compatibility

Resolves chunk state issues when using async world generators like Terra with FAWE:

- Added `ChunkValidationUtil`: samples multiple positions to detect whether a chunk is fully generated, preventing structures from being placed on empty chunks
- Uses Paper API `getChunkAtAsync` for async chunk loading, with `PluginChunkTicket` to keep chunks loaded during pasting
- All world block access operations ensure chunks are loaded into memory first

### 3. PersistentDataContainer Chunk Marking

The original plugin relies on `ChunkLoadEvent.isNewChunk()` to identify new chunks, but after a server restart, previously generated but unmarked chunks get scanned again. This fork uses Bukkit `PersistentDataContainer` to persistently mark chunks:

- Processed chunks are tagged with `betterstructures:chunk_processed`
- No duplicate scanning of already-processed chunks after restart

### 4. Structure Location Persistence

Added `StructureLocationManager` and `StructureLocationData` to persist all generated structure locations to YAML files:

- Each world is stored independently under `structure_locations/`
- Records structure coordinates, type, schematic name, and bounding box dimensions
- In-memory cache with automatic dirty-data saving every minute
- Provides the data foundation for the mob tracking system

### 5. Mob Tracking & Respawn System

Added `MobTrackingManager` and `MobSpawnConfig` for tracking and respawning mobs within structures:

- Records mob spawn configurations (position, type) for each structure
- Supports Vanilla / EliteMobs / MythicMobs mob types
- Spatial index (world-chunk) for fast nearby structure lookups
- Triggers mob respawn checks when players approach
- Fires `StructureClearedEvent` when all mobs in a structure are killed

### 6. Chinese Localization

All user-facing logs, command feedback, and menu text have been translated to Chinese across 27 files.

---

## Requirements

| Component | Requirement |
|-----------|-------------|
| Minecraft | 1.14+ (1.21+ recommended) |
| Server | Paper (or forks like Purpur) |
| Java | 21+ |
| **FastAsyncWorldEdit** | **Required** (vanilla WorldEdit is not supported) |

### Optional Dependencies

- EliteMobs - Custom bosses
- MythicMobs - Custom mobs
- WorldGuard - Region protection
- Terra / Terralith / Iris / TerraformGenerator - Custom world generation

---

## Building

```bash
./gradlew shadowJar
```

Output: `testbed/plugins/BetterStructures.jar`

---

## Full Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

All commits relative to upstream (chronological order):

1. **feat: add structure location** - Structure location persistence system
2. **feat: add mob tracking and respawn system** - MobTrackingManager, MobSpawnConfig, MobDeathListener, StructureClearedEvent
3. **Merge upstream/master** - Sync upstream 2.1.2
4. **feat: add Terra + FAWE compatibility** - Delayed chunk scanning for Terra async world generation
5. **feat: add structure queue system** - Structure placement queue to prevent concurrent paste conflicts
6. **fix: prevent duplicate structure paste** - Fix queue system duplicate pasting
7. **fix: use Paper API for reliable async chunk loading** - Use Paper async chunk API
8. **feat: add chunk generation check at paste level** - Pre-paste chunk integrity verification
9. **refactor: simplify to single-layer chunk check** - Consolidated to Schematic-level unified check
10. **fix: ensure chunks are generated before world block access** - Ensure chunks are generated + parallelize schematic loading
11. **localization** - Full Chinese localization (27 files)
12. **refactor: migrate block pasting to FAWE async EditSession** - Eliminate main-thread blocking entirely, remove WorkloadRunnable
13. **fix: replace isNewChunk with PersistentDataContainer** - Persistent chunk processing markers, prevent duplicate scanning after restart

---

## License

GPL-3.0, same as upstream.

## Credits

- Original author: [MagmaGuy](https://github.com/MagmaGuy)
- Original repository: https://github.com/MagmaGuy/BetterStructures
- Modrinth: https://modrinth.com/plugin/betterstructures
