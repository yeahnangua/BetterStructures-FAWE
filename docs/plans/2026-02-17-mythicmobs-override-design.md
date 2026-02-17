# MythicMobs Override Design

Date: 2026-02-17

## Overview

Add MythicMobs (MM) integration to BetterStructures (BS), enabling automatic replacement of vanilla mobs and EliteMobs bosses with MythicMobs equivalents when structures are placed.

## Features

### Feature 1: Replace Vanilla Mobs with MM Mobs

**Trigger**: When BS places a structure and spawns entities from `[spawn]` signs.

**Behavior**:
- On plugin enable, query MM API to get all registered MM mobs and their base entity types
- Build a `Map<EntityType, List<String>>` cache mapping vanilla types to MM mob IDs
- Exclude mobs listed in `mythicBossList` from this mapping (boss blacklist)
- When spawning a `[spawn]` vanilla mob, look up the entity type in the cache
- If matching MM mobs exist, randomly select one and spawn via `MythicMobs.spawnAndReturn()`
- If no matching MM mobs exist, fall back to vanilla spawning
- Register spawned MM mobs with MobTracking as type `MYTHICMOBS`
- On respawn, randomly re-select from the cache (not fixed to first choice)

**Toggle**: `mythicMobsOverride.replaceVanillaMobs` (boolean)

### Feature 2: Replace EliteMobs Bosses with MM Bosses

**Trigger**: When BS places a structure and spawns entities from `[elitemobs]` signs.

**Behavior**:
- When spawning an `[elitemobs]` boss, instead of calling `EliteMobs.spawnAndReturn()`, randomly select a boss from `mythicBossList`
- Spawn the selected MM boss via `MythicMobs.spawnAndReturn()` at default level 1
- Skip WorldGuard region protection (designed for EM bosses)
- Register with MobTracking as type `MYTHICMOBS`
- If `mythicBossList` is empty, log a warning and fall back to original behavior

**Toggle**: `mythicMobsOverride.replaceEliteMobsBosses` (boolean)

### Feature 3: Boss Structure Marker

**Judgment**: A structure is a "boss structure" if:
- Its `SchematicContainer.eliteMobsSpawns` is non-empty (has `[elitemobs]` signs), OR
- Any of its `[mythicmobs]` sign mob IDs appear in `mythicBossList`

**Storage**: `StructureLocationData` gets a new `isBossStructure` boolean field, persisted to disk.

**Display**: `/bs info` command output includes a line showing boss structure status.

## Configuration

New section in `config.yml`:

```yaml
mythicMobsOverride:
  enabled: true
  replaceVanillaMobs: true
  replaceEliteMobsBosses: true
  mythicBossList:
    - DragonLord
    - SkeletonKing
    - ZombieOverlord
```

- `enabled`: Master switch, requires MythicMobs installed
- `replaceVanillaMobs`: Feature 1 toggle
- `replaceEliteMobsBosses`: Feature 2 toggle
- `mythicBossList`: Single list serving dual purpose:
  - Feature 1: Blacklist (these mobs excluded from vanilla replacement pool)
  - Feature 2: Whitelist (EM bosses replaced with random pick from this list)

## Architecture

### Approach: Intercept at spawn time (in `FitAnything.spawnEntities()`)

Chosen over alternatives (schematic pre-processing, event-based post-processing) for:
- Minimal code changes concentrated in one method
- Natural compatibility with existing MobTracking system
- No need to modify schematic files
- Best runtime efficiency

### MM Type Mapping Cache

Built on plugin enable and on `/bs reload`:
1. `MythicBukkit.inst().getMobManager().getMobTypes()` → all MM mobs
2. For each MM mob, get base entity type via `MythicMob.getEntityType()`
3. Group into `Map<EntityType, List<String>>` excluding boss-listed mobs
4. Cache in `MythicMobs.java` static field

### Spawn Flow (modified)

```
FitAnything.spawnEntities() called
  │
  ├─ [spawn] vanilla mob
  │   ├─ Override enabled + MM installed + cache has mapping?
  │   │   ├─ YES → random MM mob from cache → MythicMobs.spawnAndReturn()
  │   │   └─ NO  → original world.spawnEntity()
  │   └─ Register as MYTHICMOBS or VANILLA accordingly
  │
  ├─ [elitemobs] boss
  │   ├─ Override enabled + MM installed + bossList non-empty?
  │   │   ├─ YES → random boss from mythicBossList → MythicMobs.spawnAndReturn()
  │   │   └─ NO  → original EliteMobs.spawnAndReturn() or skip
  │   └─ Register as MYTHICMOBS or ELITEMOBS accordingly
  │
  └─ [mythicmobs] mob (unchanged)
```

### Respawn Flow (modified)

When MobTrackingManager respawns a mob that was originally a vanilla override:
- Look up original vanilla EntityType from MobSpawnConfig
- Re-query the MM type mapping cache
- Randomly select a new MM mob (may differ from previous spawn)

## Files Changed

| File | Change |
|------|--------|
| `DefaultConfig.java` | Add `mythicMobsOverride` config fields |
| `FitAnything.java` | Modify `spawnEntities()` to intercept vanilla and EM spawns |
| `MythicMobs.java` | Add `buildTypeMapping()`, `getRandomMobByType()`, `getRandomBoss()` |
| `MobTrackingManager.java` | Modify respawn logic for overridden vanilla mobs |
| `BetterStructures.java` | Trigger mapping cache build on enable and reload |
| `StructureLocationData.java` | Add `isBossStructure` field |
| `SchematicContainer.java` | Add `isBossStructure()` method |
| `/bs info` command class | Add boss structure info to output |

## Error Handling

- MythicMobs not installed but feature enabled → warn log, skip all replacements
- MM mob ID invalid (deleted from MM config) → warn log, fallback to vanilla/skip
- MM API query fails → warn log, fallback
- Empty `mythicBossList` with feature 2 enabled → warn log, keep original EM behavior
