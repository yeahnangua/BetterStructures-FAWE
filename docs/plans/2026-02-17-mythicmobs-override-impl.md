# MythicMobs Override Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add MythicMobs integration that auto-replaces vanilla mobs and EliteMobs bosses with MythicMobs equivalents when structures are placed, plus boss structure markers.

**Architecture:** Intercept mob spawning in `FitAnything.spawnEntities()` at spawn time. Build a type mapping cache on plugin enable by querying MM API. New `MobType` enum values distinguish overridden mobs for correct respawn behavior.

**Tech Stack:** Java 21, Bukkit/Paper API, MythicMobs 5.2.1 API (`io.lumine.mythic`), MagmaCore config framework

---

### Task 1: Add new MobType enum values to MobSpawnConfig

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/mobtracking/MobSpawnConfig.java`

**Step 1: Add VANILLA_MM_OVERRIDE and ELITEMOBS_MM_OVERRIDE enum values**

In `MobSpawnConfig.java`, add two new enum values to the `MobType` enum (line 9-13):

```java
public enum MobType {
    VANILLA,
    ELITEMOBS,
    MYTHICMOBS,
    VANILLA_MM_OVERRIDE,      // Vanilla mob replaced with MM mob (respawns with random re-selection)
    ELITEMOBS_MM_OVERRIDE     // EM boss replaced with MM boss (does not respawn)
}
```

**Step 2: Update shouldRespawn()**

Replace the `shouldRespawn()` method (line 53-55) with:

```java
public boolean shouldRespawn() {
    return mobType != MobType.ELITEMOBS && mobType != MobType.ELITEMOBS_MM_OVERRIDE;
}
```

**Step 3: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (no code references these new enum values yet, so no breakage)

**Step 4: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/mobtracking/MobSpawnConfig.java
git commit -m "feat(mythicmobs-override): add VANILLA_MM_OVERRIDE and ELITEMOBS_MM_OVERRIDE mob types"
```

---

### Task 2: Add config fields to DefaultConfig

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/config/DefaultConfig.java`

**Step 1: Add static fields**

After the Terra/FAWE compatibility fields block (after line 99), add:

```java
// MythicMobs override configuration
@Getter
private static boolean mythicMobsOverrideEnabled;
@Getter
private static boolean mmOverrideReplaceVanillaMobs;
@Getter
private static boolean mmOverrideReplaceEliteMobsBosses;
@Getter
private static List<String> mythicBossList;
```

**Step 2: Initialize values in initializeValues()**

Before the `ConfigurationEngine.fileSaverOnlyDefaults(...)` call at line 283, add:

```java
// MythicMobs override configuration
mythicMobsOverrideEnabled = ConfigurationEngine.setBoolean(
        List.of(
                "Enable MythicMobs override system.",
                "When enabled, vanilla mobs in structures will be replaced with MythicMobs equivalents,",
                "and EliteMobs bosses will be replaced with MythicMobs bosses.",
                "Requires MythicMobs plugin to be installed."),
        fileConfiguration, "mythicMobsOverride.enabled", false);

mmOverrideReplaceVanillaMobs = ConfigurationEngine.setBoolean(
        List.of(
                "Replace vanilla mobs (from [spawn] signs) with MythicMobs equivalents.",
                "BS auto-maps vanilla entity types to MM mobs with the same base type.",
                "For example, all MM mobs with Type: ZOMBIE will replace vanilla zombies."),
        fileConfiguration, "mythicMobsOverride.replaceVanillaMobs", true);

mmOverrideReplaceEliteMobsBosses = ConfigurationEngine.setBoolean(
        List.of(
                "Replace EliteMobs bosses (from [elitemobs] signs) with MythicMobs bosses.",
                "EM bosses will be randomly replaced with a boss from the mythicBossList below."),
        fileConfiguration, "mythicMobsOverride.replaceEliteMobsBosses", true);

mythicBossList = ConfigurationEngine.setList(
        List.of(
                "List of MythicMobs mob IDs that are considered bosses.",
                "Feature 1 (vanilla replace): these mobs are EXCLUDED from the vanilla replacement pool.",
                "Feature 2 (EM boss replace): EM bosses are randomly replaced with one from this list.",
                "Example: DragonLord, SkeletonKing, ZombieOverlord"),
        fileConfiguration, "mythicMobsOverride.mythicBossList",
        List.of("ExampleBoss1", "ExampleBoss2"));
```

**Step 3: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/config/DefaultConfig.java
git commit -m "feat(mythicmobs-override): add config fields for mythicMobsOverride section"
```

---

### Task 3: Add type mapping cache and query methods to MythicMobs.java

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/thirdparty/MythicMobs.java`

**Step 1: Add imports and static cache fields**

Add imports after existing imports (after line 11):

```java
import com.magmaguy.betterstructures.config.DefaultConfig;
import io.lumine.mythic.api.mobs.entities.MythicEntityType;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
```

Add static fields after class declaration (after line 18):

```java
// Cache: Bukkit EntityType -> List of MM mob IDs that share the same base type (excluding bosses)
private static Map<EntityType, List<String>> typeMappingCache = new HashMap<>();
private static boolean cacheBuilt = false;
```

**Step 2: Add MythicEntityType-to-EntityType conversion map**

Add a static method to convert MythicEntityType to Bukkit EntityType:

```java
/**
 * Convert MythicEntityType enum name to Bukkit EntityType.
 * Most names match directly. A few legacy names need mapping.
 */
private static EntityType toBukkitEntityType(MythicEntityType mythicType) {
    if (mythicType == null) return null;
    String name = mythicType.name();

    // Handle known mismatches between MythicMobs and Bukkit naming
    switch (name) {
        case "MUSHROOM_COW": name = "MOOSHROOM"; break;
        case "SNOWMAN": name = "SNOW_GOLEM"; break;
        case "PIG_ZOMBIE": name = "ZOMBIFIED_PIGLIN"; break;
        case "PIG_ZOMBIE_VILLAGER": return null; // No direct Bukkit equivalent
        case "BABY_PIG_ZOMBIE": name = "ZOMBIFIED_PIGLIN"; break;
        case "CUSTOM": return null; // Custom entity types can't be mapped
        default: break;
    }

    // Skip BABY_ variants (they map to the same EntityType as the adult)
    if (name.startsWith("BABY_")) {
        name = name.substring(5); // Remove "BABY_" prefix
    }

    try {
        return EntityType.valueOf(name);
    } catch (IllegalArgumentException e) {
        return null; // Unknown entity type, skip
    }
}
```

**Step 3: Add buildTypeMapping() method**

```java
/**
 * Builds the type mapping cache by querying all registered MythicMobs
 * and grouping them by their base Bukkit EntityType.
 * Mobs in the mythicBossList are excluded from this mapping.
 */
public static void buildTypeMapping() {
    typeMappingCache.clear();
    cacheBuilt = false;

    if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
        Logger.warn("MythicMobs 覆盖已启用，但 MythicMobs 插件未安装！");
        return;
    }

    Set<String> bossList = new HashSet<>(DefaultConfig.getMythicBossList());

    try {
        Collection<io.lumine.mythic.api.mobs.MythicMob> allMobs =
                MythicBukkit.inst().getMobManager().getMobTypes();

        Map<EntityType, List<String>> mapping = new HashMap<>();

        for (io.lumine.mythic.api.mobs.MythicMob mob : allMobs) {
            String mobId = mob.getInternalName();

            // Skip bosses (they go in the boss list, not the vanilla replacement pool)
            if (bossList.contains(mobId)) continue;

            MythicEntityType mythicType = mob.getEntityType();
            EntityType bukkitType = toBukkitEntityType(mythicType);

            if (bukkitType != null) {
                mapping.computeIfAbsent(bukkitType, k -> new ArrayList<>()).add(mobId);
            }
        }

        typeMappingCache = mapping;
        cacheBuilt = true;

        Logger.info("MythicMobs 类型映射缓存已构建: " + mapping.size() + " 个原版类型已映射");
        for (Map.Entry<EntityType, List<String>> entry : mapping.entrySet()) {
            Logger.info("  " + entry.getKey().name() + " → " + entry.getValue());
        }
    } catch (Exception e) {
        Logger.warn("构建 MythicMobs 类型映射缓存失败: " + e.getMessage());
        e.printStackTrace();
    }
}
```

**Step 4: Add query methods**

```java
/**
 * Get a random MM mob ID that matches the given vanilla EntityType.
 * Returns null if no mapping exists.
 */
public static String getRandomMobByType(EntityType entityType) {
    if (!cacheBuilt) return null;
    List<String> candidates = typeMappingCache.get(entityType);
    if (candidates == null || candidates.isEmpty()) return null;
    return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
}

/**
 * Get a random boss from the mythicBossList config.
 * Returns null if the list is empty.
 */
public static String getRandomBoss() {
    List<String> bossList = DefaultConfig.getMythicBossList();
    if (bossList == null || bossList.isEmpty()) return null;
    return bossList.get(ThreadLocalRandom.current().nextInt(bossList.size()));
}

/**
 * Check if the type mapping cache has been built and is available.
 */
public static boolean isCacheReady() {
    return cacheBuilt;
}

/**
 * Check if MythicMobs override is effectively enabled
 * (config enabled + MythicMobs installed + cache built).
 */
public static boolean isOverrideActive() {
    return DefaultConfig.isMythicMobsOverrideEnabled()
            && Bukkit.getPluginManager().getPlugin("MythicMobs") != null
            && cacheBuilt;
}

/**
 * Clear the cache (called on disable/reload).
 */
public static void clearCache() {
    typeMappingCache.clear();
    cacheBuilt = false;
}
```

**Step 5: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/thirdparty/MythicMobs.java
git commit -m "feat(mythicmobs-override): add type mapping cache and query methods"
```

---

### Task 4: Modify FitAnything.spawnEntities() — vanilla mob override

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java`

**Step 1: Add import**

Add at the top of the file with other imports:

```java
import com.magmaguy.betterstructures.config.DefaultConfig;
```

(Check first if it's already imported.)

**Step 2: Modify the vanilla mobs spawn loop (lines 365-402)**

Replace the spawn logic within the vanilla mobs loop. After line 376 (`EntityType entityType = ...`), replace the entity spawning and tracking code. The full replacement for lines 376-401:

```java
            EntityType entityType = schematicContainer.getVanillaSpawns().get(entityPosition);

            // MythicMobs override: try to replace vanilla mob with MM equivalent
            Entity entity = null;
            MobSpawnConfig.MobType trackingType = MobSpawnConfig.MobType.VANILLA;
            String trackingIdentifier = entityType.name();

            if (MythicMobs.isOverrideActive() && DefaultConfig.isMmOverrideReplaceVanillaMobs()) {
                String mmMobId = MythicMobs.getRandomMobByType(entityType);
                if (mmMobId != null) {
                    entity = MythicMobs.spawnAndReturn(signLocation, mmMobId + ":1");
                    if (entity != null) {
                        trackingType = MobSpawnConfig.MobType.VANILLA_MM_OVERRIDE;
                        // Store original EntityType name as identifier for re-selection on respawn
                        trackingIdentifier = entityType.name();
                    }
                }
            }

            // Fallback to vanilla spawning if MM override didn't work
            if (entity == null) {
                entity = signLocation.getWorld().spawnEntity(signLocation, entityType);
                entity.setPersistent(true);
                if (entity instanceof LivingEntity) {
                    ((LivingEntity) entity).setRemoveWhenFarAway(false);
                }
                trackingType = MobSpawnConfig.MobType.VANILLA;
                trackingIdentifier = entityType.name();
            }

            if (!VersionChecker.serverVersionOlderThan(21, 0) &&
                    entity.getType().equals(EntityType.END_CRYSTAL)) {
                EnderCrystal enderCrystal = (EnderCrystal) entity;
                enderCrystal.setShowingBottom(false);
            }

            // Track mob for respawning (only LivingEntities)
            if (entity instanceof LivingEntity && DefaultConfig.isMobTrackingEnabled()) {
                Vector actualOffset = schematicOffset.clone().add(entityPosition);
                spawnedMobUUIDs.add(entity.getUniqueId());
                mobSpawnConfigs.add(new MobSpawnConfig(
                        trackingType,
                        trackingIdentifier,
                        actualOffset.getX(),
                        actualOffset.getY(),
                        actualOffset.getZ()
                ));
            }
```

**Step 3: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java
git commit -m "feat(mythicmobs-override): replace vanilla mobs with MM equivalents in structures"
```

---

### Task 5: Modify FitAnything.spawnEntities() — EM boss override

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java`

**Step 1: Modify the EliteMobs spawn loop (lines 404-445)**

Replace the EM boss spawn logic. The full replacement for the body of the EM loop:

```java
        // Spawn EliteMobs bosses
        for (Vector elitePosition : schematicContainer.getEliteMobsSpawns().keySet()) {
            Location eliteLocation = LocationProjector.project(location, schematicOffset, elitePosition).clone();
            // Skip if chunk not loaded to avoid sync chunk loading with FAWE
            if (!eliteLocation.getWorld().isChunkLoaded(eliteLocation.getBlockX() >> 4, eliteLocation.getBlockZ() >> 4)) {
                continue;
            }
            eliteLocation.getBlock().setBlockData(Material.AIR.createBlockData(), false);
            eliteLocation.add(new Vector(0.5, 0, 0.5));
            String bossFilename = schematicContainer.getEliteMobsSpawns().get(elitePosition);

            Entity eliteMob = null;
            MobSpawnConfig.MobType trackingType = MobSpawnConfig.MobType.ELITEMOBS;
            String trackingIdentifier = bossFilename;

            // MythicMobs override: try to replace EM boss with MM boss
            if (MythicMobs.isOverrideActive() && DefaultConfig.isMmOverrideReplaceEliteMobsBosses()) {
                String mmBossId = MythicMobs.getRandomBoss();
                if (mmBossId != null) {
                    eliteMob = MythicMobs.spawnAndReturn(eliteLocation, mmBossId + ":1");
                    if (eliteMob != null) {
                        trackingType = MobSpawnConfig.MobType.ELITEMOBS_MM_OVERRIDE;
                        trackingIdentifier = mmBossId;
                    }
                } else {
                    Logger.warn("MythicMobs Boss 列表为空！无法替换 EliteMobs Boss: " + bossFilename);
                }
            }

            // Fallback to original EliteMobs spawning
            if (eliteMob == null) {
                eliteMob = EliteMobs.spawnAndReturn(eliteLocation, bossFilename);
                if (eliteMob == null) return;
                trackingType = MobSpawnConfig.MobType.ELITEMOBS;
                trackingIdentifier = bossFilename;
            }

            // Track mob
            if (DefaultConfig.isMobTrackingEnabled()) {
                Vector actualOffset = schematicOffset.clone().add(elitePosition);
                spawnedMobUUIDs.add(eliteMob.getUniqueId());
                mobSpawnConfigs.add(new MobSpawnConfig(
                        trackingType,
                        trackingIdentifier,
                        actualOffset.getX(),
                        actualOffset.getY(),
                        actualOffset.getZ()
                ));
            }

            // Only set up WorldGuard protection for original EliteMobs bosses (not MM overrides)
            if (trackingType == MobSpawnConfig.MobType.ELITEMOBS) {
                Location lowestCorner = location.clone().add(schematicOffset);
                Location highestCorner = lowestCorner.clone().add(new Vector(schematicClipboard.getRegion().getWidth() - 1, schematicClipboard.getRegion().getHeight(), schematicClipboard.getRegion().getLength() - 1));
                if (DefaultConfig.isProtectEliteMobsRegions() &&
                        Bukkit.getPluginManager().getPlugin("WorldGuard") != null &&
                        Bukkit.getPluginManager().getPlugin("EliteMobs") != null) {
                    WorldGuard.Protect(lowestCorner, highestCorner, bossFilename, eliteLocation);
                } else {
                    if (!worldGuardWarn) {
                        worldGuardWarn = true;
                        Logger.warn("你未使用 WorldGuard，因此 BetterStructures 无法保护Boss竞技场！建议使用 WorldGuard 以保证公平的战斗体验。");
                    }
                }
            }
        }
```

**Step 2: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java
git commit -m "feat(mythicmobs-override): replace EM bosses with MM bosses in structures"
```

---

### Task 6: Modify MobTrackingManager respawn logic

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/mobtracking/MobTrackingManager.java`

**Step 1: Add import**

Ensure these imports exist:

```java
import com.magmaguy.betterstructures.thirdparty.MythicMobs;
import org.bukkit.entity.EntityType;
```

(MythicMobs import is likely already present. Check first.)

**Step 2: Add VANILLA_MM_OVERRIDE case to respawnMobs() switch**

In the `respawnMobs()` method, in the switch statement (around line 366), add a new case after the MYTHICMOBS case and before the ELITEMOBS case:

```java
                case VANILLA_MM_OVERRIDE:
                    // Re-select a random MM mob for this vanilla entity type
                    spawnLoc.getBlock().setBlockData(Material.AIR.createBlockData(), false);
                    spawnLoc.add(new Vector(0.5, 0, 0.5));
                    if (MythicMobs.isOverrideActive()) {
                        try {
                            EntityType originalType = EntityType.valueOf(config.getMobIdentifier());
                            String mmMobId = MythicMobs.getRandomMobByType(originalType);
                            if (mmMobId != null) {
                                spawnedMob = MythicMobs.spawnAndReturn(spawnLoc, mmMobId + ":1");
                            }
                        } catch (IllegalArgumentException e) {
                            Logger.warn("重生覆盖生物失败，无法识别原始类型: " + config.getMobIdentifier());
                        }
                    }
                    // Fallback to vanilla if MM override fails
                    if (spawnedMob == null) {
                        try {
                            EntityType entityType = EntityType.valueOf(config.getMobIdentifier());
                            spawnedMob = world.spawnEntity(spawnLoc, entityType);
                            if (spawnedMob instanceof LivingEntity) {
                                ((LivingEntity) spawnedMob).setRemoveWhenFarAway(false);
                            }
                            spawnedMob.setPersistent(true);
                        } catch (IllegalArgumentException e) {
                            Logger.warn("重生原版生物失败: " + config.getMobIdentifier());
                        }
                    }
                    break;

                case ELITEMOBS_MM_OVERRIDE:
                    // EM boss overrides do not respawn (same as ELITEMOBS)
                    break;
```

**Step 3: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/mobtracking/MobTrackingManager.java
git commit -m "feat(mythicmobs-override): handle MM override mob types in respawn logic"
```

---

### Task 7: Add isBossStructure field to StructureLocationData and persistence

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/structurelocation/StructureLocationData.java`
- Modify: `src/main/java/com/magmaguy/betterstructures/structurelocation/StructureLocationManager.java`

**Step 1: Add field to StructureLocationData**

After the `clearedTimestamp` field (line 31), add:

```java
private boolean bossStructure;
```

**Step 2: Initialize in constructors**

In the "new structures" constructor (line 43-61), add after `this.clearedTimestamp = 0;` (line 55):

```java
this.bossStructure = false;
```

In the "loading from storage" constructor (line 66-90), add a new parameter `boolean bossStructure` after the `createdTimestamp` parameter, and add to the body after `this.createdTimestamp = createdTimestamp;` (line 89):

```java
this.bossStructure = bossStructure;
```

The loading constructor signature becomes:

```java
public StructureLocationData(int x, int y, int z, String worldName, String schematicName,
                              StructureType structureType, int radiusX, int radiusY, int radiusZ,
                              boolean cleared, long clearedTimestamp, int respawnCount,
                              List<MobSpawnConfig> mobSpawnConfigs, Set<Integer> killedSpawnIndices,
                              long createdTimestamp, boolean bossStructure)
```

**Step 3: Add getter and setter**

After the `getCreatedTimestamp()` method (line 204-206), add:

```java
public boolean isBossStructure() {
    return bossStructure;
}

public void setBossStructure(boolean bossStructure) {
    this.bossStructure = bossStructure;
}
```

**Step 4: Update StructureLocationManager — save**

In `saveWorldData()` method (around line 290, after `.createdTimestamp` save), add:

```java
config.set(path + ".bossStructure", data.isBossStructure());
```

**Step 5: Update StructureLocationManager — load**

In `loadWorldData()` method (around line 187, after the `createdTimestamp` read), add:

```java
boolean bossStructure = locationSection.getBoolean("bossStructure", false);
```

And update the constructor call (around line 218-223) to pass the new parameter:

```java
StructureLocationData data = new StructureLocationData(
        x, y, z, worldName, schematic, type,
        radiusX, radiusY, radiusZ,
        cleared, clearedTimestamp, respawnCount,
        mobSpawnConfigs, killedSpawnIndices, createdTimestamp, bossStructure
);
```

**Step 6: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/structurelocation/StructureLocationData.java \
        src/main/java/com/magmaguy/betterstructures/structurelocation/StructureLocationManager.java
git commit -m "feat(mythicmobs-override): add isBossStructure field with persistence"
```

---

### Task 8: Add isBossStructure() method to SchematicContainer

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/schematics/SchematicContainer.java`

**Step 1: Add import**

```java
import com.magmaguy.betterstructures.config.DefaultConfig;
```

**Step 2: Add isBossStructure() method**

Add a new method to `SchematicContainer`:

```java
/**
 * Determines if this schematic represents a boss structure.
 * A structure is a boss structure if:
 * - It has EliteMobs spawns ([elitemobs] signs), OR
 * - Any of its MythicMobs spawns reference a mob in the mythicBossList config
 */
public boolean isBossStructure() {
    // Has EliteMobs bosses = boss structure
    if (!eliteMobsSpawns.isEmpty()) {
        return true;
    }

    // Check if any MythicMobs spawns are in the boss list
    List<String> bossList = DefaultConfig.getMythicBossList();
    if (bossList != null && !bossList.isEmpty()) {
        for (String mmSpawn : mythicMobsSpawns.values()) {
            // MM spawn format is "MobID[:level]", extract just the MobID
            String mobId = mmSpawn.split(":")[0];
            if (bossList.contains(mobId)) {
                return true;
            }
        }
    }

    return false;
}
```

**Step 3: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/schematics/SchematicContainer.java
git commit -m "feat(mythicmobs-override): add isBossStructure() method to SchematicContainer"
```

---

### Task 9: Set isBossStructure flag during structure placement

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java`

**Step 1: Set the boss structure flag**

In `spawnEntities()`, at the end of the method where `structureData` is retrieved (around line 478-483), add the boss structure flag setting:

```java
        // Register mobs with tracking manager
        if (DefaultConfig.isMobTrackingEnabled() && !spawnedMobUUIDs.isEmpty()) {
            // Get or create structure location data
            StructureLocationData structureData = StructureLocationManager.getInstance()
                    .getStructureAt(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());

            if (structureData != null) {
                MobTrackingManager.getInstance().registerStructureMobs(structureData, spawnedMobUUIDs, mobSpawnConfigs);
                // Set boss structure flag
                structureData.setBossStructure(schematicContainer.isBossStructure());
                StructureLocationManager.getInstance().markDirty(location.getWorld().getName());
            }
        }
```

**Step 2: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java
git commit -m "feat(mythicmobs-override): set isBossStructure flag during structure placement"
```

---

### Task 10: Update StructureInfoCommand for boss structure display

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/commands/StructureInfoCommand.java`

**Step 1: Add boss structure info line**

After line 62 (`Logger.sendMessage(player, "&6重生次数: ...")`), add:

```java
Logger.sendMessage(player, "&6Boss 结构: &f" + (found.isBossStructure() ? "是" : "否"));
```

**Step 2: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/commands/StructureInfoCommand.java
git commit -m "feat(mythicmobs-override): show boss structure status in /bs info"
```

---

### Task 11: Wire up cache build in BetterStructures.onEnable()

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/BetterStructures.java`

**Step 1: Add import**

```java
import com.magmaguy.betterstructures.thirdparty.MythicMobs;
```

(Check if already imported first.)

**Step 2: Add cache build after mob tracking init**

After the mob tracking initialization block (after line 93), add:

```java
// Initialize MythicMobs override type mapping cache
if (DefaultConfig.isMythicMobsOverrideEnabled()) {
    MythicMobs.buildTypeMapping();
}
```

**Step 3: Add cache clear in onDisable()**

In `onDisable()`, before `MobTrackingManager.shutdown();` (line 136), add:

```java
MythicMobs.clearCache();
```

**Step 4: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/BetterStructures.java
git commit -m "feat(mythicmobs-override): wire up cache build on enable and clear on disable"
```

---

### Task 12: Full build and final verification

**Step 1: Full Gradle build with shadow jar**

Run: `./gradlew shadowJar 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL

**Step 2: Check that the jar was created**

Run: `ls -la testbed/plugins/BetterStructures.jar`
Expected: File exists with recent timestamp

**Step 3: Final commit with all changes (if any unstaged)**

Run: `git status`
Expected: Clean working tree (all changes committed in previous tasks)

**Step 4: Verify commit history**

Run: `git log --oneline -12`
Expected: 11 new commits with `feat(mythicmobs-override):` prefix

---

## Task Dependency Graph

```
Task 1 (MobSpawnConfig enum) ──┐
Task 2 (DefaultConfig)     ────┤
Task 3 (MythicMobs cache)  ────┼── Task 4 (vanilla override) ── Task 5 (EM override) ── Task 9 (set boss flag)
                                │
                                ├── Task 6 (respawn logic)
                                │
Task 7 (StructureLocationData) ─┼── Task 8 (SchematicContainer.isBossStructure)
                                │
                                ├── Task 10 (/bs info)
                                │
                                └── Task 11 (onEnable wiring) ── Task 12 (final build)
```

Tasks 1, 2, 7 can run in parallel.
Tasks 3, 8 depend on 2.
Tasks 4, 5, 6 depend on 1, 2, 3.
Task 9 depends on 4, 5, 8.
Task 10 depends on 7.
Task 11 depends on 3.
Task 12 depends on all previous tasks.
