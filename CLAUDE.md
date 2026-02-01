# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the plugin (outputs to testbed/plugins/BetterStructures.jar)
./gradlew shadowJar

# Clean and build
./gradlew clean shadowJar

# Publish to Maven repository (requires credentials)
./gradlew publish
```

## Project Overview

BetterStructures is a Minecraft Spigot plugin that adds procedurally generated structures to world generation. It uses WorldEdit for schematic handling and supports integration with EliteMobs, MythicMobs, WorldGuard, and other plugins.

**Key Dependencies:**
- Java 17
- MagmaCore (core framework for config, commands, content packages)
- WorldEdit (required - schematic loading and pasting)
- EasyMinecraftGoals (NMS adapter)

## Architecture

### Entry Point
`BetterStructures.java` - Main plugin class. Initializes configs in order, registers listeners, and sets up command handlers.

### Core Systems

**Structure Generation Pipeline:**
1. `NewChunkLoadEvent` - Listens for chunk loads, triggers structure scanning
2. `FitAnything` hierarchy - Polymorphic placement strategies:
   - `FitSurfaceBuilding`, `FitAirBuilding`, `FitUndergroundShallowBuilding`, `FitUndergroundDeepBuilding`, `FitLiquidBuilding`
3. `SchematicContainer` - Holds WorldEdit clipboard data and metadata
4. `TerrainAdequacy` / `Topology` - Evaluate terrain fitness scores

**Wave Function Collapse (WFC) Module System:**
- `WFCGenerator` - Core algorithm with constraint propagation and backtracking
- `WFCLattice` - 3D grid representation with entropy-based node selection
- `ModulesContainer` - Module data with rotation support and boundary tags

**Configuration System (MagmaCore-based):**
- `DefaultConfig` - Global settings (Y ranges, performance, mob tracking)
- `GeneratorConfig` - Structure generation rules per generator
- `SchematicConfig` - Schematic metadata and associations
- `TreasureConfig` - Chest loot tables
- `SpawnPoolsConfig` - Mob spawn pool definitions
- Configs in `config/*.premade/` are auto-generated defaults

**Data Persistence:**
- `StructureLocationManager` - Tracks placed structures in YAML files (`structure_locations/worldname.yml`)
- Uses in-memory cache (ConcurrentHashMap) with 5-minute auto-save
- `MobTrackingManager` - Spatial index for mob respawn tracking

**Content System:**
- `BSPackage` extends MagmaCore's `ContentPackage`
- Content packs in `config/contentpackages/premade/`

### Event API

Custom events in `api/` package:
- `BuildPlaceEvent` - Before structure placement (cancellable)
- `ChestFillEvent` - Before chest content population
- `StructureClearedEvent` - When all mobs in structure are killed
- `WorldGenerationFinishEvent` - When WFC generation completes

### Third-Party Integration

Located in `thirdparty/`:
- `EliteMobs.java` - Boss spawning integration
- `MythicMobs.java` - Custom mob spawning
- `WorldGuard.java` - Region protection flags

### Commands

All registered via MagmaCore's `CommandManager`:
- `/bs place` - Manually place structures
- `/bs lootify` - Convert chest to loot chest
- `/bs pregenerate` / `cancelPregenerate` - World pregeneration
- `/bs generateModules` - Run WFC generation
- `/bs reload` - Reload configs
- `/bs setup` - First-time setup wizard
- `/bs teleport` - Teleport to structures

## Key Patterns

- **Config-driven**: All behavior controlled through YAML configs
- **Reflection-based discovery**: Uses `org.reflections` to find config classes
- **Singleton managers**: `StructureLocationManager.getInstance()`, `MobTrackingManager.getInstance()`
- **Async operations**: WFC generation and structure pasting use background threads
- **Spatial indexing**: Chunk-based spatial queries for mob tracking
