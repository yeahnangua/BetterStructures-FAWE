# BetterStructures-FAWE

[中文](README.zh-CN.md)

A performance-focused fork of [MagmaGuy/BetterStructures](https://github.com/MagmaGuy/BetterStructures), deeply adapted for the **Paper + FastAsyncWorldEdit (FAWE) + Terra** ecosystem.

> Original plugin: [GitHub](https://github.com/MagmaGuy/BetterStructures) | [Modrinth](https://modrinth.com/plugin/betterstructures)
>
> **Download this fork**: [Modrinth](https://modrinth.com/plugin/betterstructures-fawe) | [SpigotMC](https://www.spigotmc.org/resources/betterstructures-fawe.132404/)

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

- Added delayed chunk scanning with retry control (`terraCompatibility.structureScanDelayTicks` / `structureScanMaxRetries`) to better match async generator timing
- Uses Paper API `getChunkAtAsync` for async chunk loading, with `PluginChunkTicket` to keep chunks loaded during pasting
- Enforces pre-paste required-chunk validation (`validateChunkBeforePaste`) before FAWE placement
- End-specific validation is more tolerant for island terrain (ratio-based invalid-chunk cutoff and `chunk.isGenerated()` checks)
- All world block access operations ensure chunks are loaded into memory first

### 3. PersistentDataContainer Chunk Marking

The original plugin relies on `ChunkLoadEvent.isNewChunk()` to identify new chunks, but after a server restart, previously generated but unmarked chunks get scanned again. This fork uses Bukkit `PersistentDataContainer` to persistently mark chunks:

- Chunks are tagged with `betterstructures:chunk_processed` only after successful structure paste
- Scan failures and paste failures do not mark the chunk, so it can retry on future loads
- No duplicate scanning of already-successfully-processed chunks after restart

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

### 7. Developer Diagnostics & Runtime Toggle

Developer diagnostics are now controlled by a dedicated runtime switch:

- Added `debug.developerMessages` config (default `false`) to gate `[BetterStructures] Developer message` output
- Added `/bs debug` command for instant runtime toggle (no `/bs reload` required)
- Unified key generation-path diagnostics behind this gate (`SKIP_PROCESSED`, `SCAN_FAILED`, `PASTE_FAILED`, `PASTE_SUCCESS_MARKED`)

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

Release highlights by version:

| Version | Highlights |
|---------|------------|
| 2.1.2-FAWE.8 | Success-only chunk processed marking, Terra/End validation refinements, generation diagnostics, runtime debug toggle (`/bs debug`), updated default generation distances |
| 2.1.2-FAWE.7 | Added `mythicMobsOverride.vanillaReplaceChance` |
| 2.1.2-FAWE.6 | Added entity-type whitelist for vanilla mob override |
| 2.1.2-FAWE.5 | Added global MythicMobs blacklist |
| 2.1.2-FAWE.4 | Added MythicMobs override system and boss-structure metadata |
| 2.1.2-FAWE.3 | Added `/bs info` structure inspection command |
| 2.1.2-FAWE.2 | Async terrain scanning, thread-safe paste dispatch, startup/load optimizations |
| 2.1.2-FAWE.1 | Base FAWE async paste migration, persistent chunk markers, parallelized schematic loading |

---

## License

GPL-3.0, same as upstream.

## Credits

- Original author: [MagmaGuy](https://github.com/MagmaGuy)
- Original repository: https://github.com/MagmaGuy/BetterStructures
- Modrinth: https://modrinth.com/plugin/betterstructures
