package com.magmaguy.betterstructures.mobtracking;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.api.StructureClearedEvent;
import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.betterstructures.structurelocation.StructureLocationData;
import com.magmaguy.betterstructures.structurelocation.StructureLocationManager;
import com.magmaguy.betterstructures.thirdparty.EliteMobs;
import com.magmaguy.betterstructures.thirdparty.MythicMobs;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core manager for tracking mobs spawned in structures and handling respawning.
 */
public class MobTrackingManager {

    private static MobTrackingManager instance;

    // Spatial index: worldName -> chunkKey -> Set<StructureLocationData>
    private final Map<String, Map<Long, Set<StructureLocationData>>> worldChunkIndex = new ConcurrentHashMap<>();

    // UUID -> Structure mapping (quick lookup for mob death events)
    private final Map<UUID, StructureLocationData> mobToStructureMap = new ConcurrentHashMap<>();

    // UUID -> Config index mapping (to mark killed mobs)
    private final Map<UUID, Integer> mobToConfigIndexMap = new ConcurrentHashMap<>();

    // Structures currently being processed (to prevent duplicate respawns)
    private final Set<String> processingStructures = ConcurrentHashMap.newKeySet();

    // Proximity check task
    private BukkitTask proximityTask;

    private MobTrackingManager() {
        startProximityCheckTask();
    }

    public static MobTrackingManager getInstance() {
        if (instance == null) {
            instance = new MobTrackingManager();
        }
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            if (instance.proximityTask != null) {
                instance.proximityTask.cancel();
            }
            instance.worldChunkIndex.clear();
            instance.mobToStructureMap.clear();
            instance.mobToConfigIndexMap.clear();
            instance.processingStructures.clear();
            instance = null;
        }
    }

    /**
     * Starts the periodic task to check player proximity to structures.
     */
    private void startProximityCheckTask() {
        int checkInterval = DefaultConfig.getProximityCheckInterval();

        proximityTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!DefaultConfig.isMobTrackingEnabled()) return;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerProximity(player);
                }
            }
        }.runTaskTimer(MetadataHandler.PLUGIN, 20L, checkInterval);
    }

    /**
     * Check if a player is near any structures and trigger respawn if needed.
     */
    private void checkPlayerProximity(Player player) {
        int triggerRadius = DefaultConfig.getMobRespawnTriggerRadius();
        Set<StructureLocationData> nearbyStructures = getStructuresNearPlayer(player, triggerRadius);

        for (StructureLocationData structure : nearbyStructures) {
            // Skip cleared structures
            if (structure.isCleared()) continue;

            // Skip structures without mob spawns
            if (!structure.hasMobSpawns()) continue;

            // Check spherical distance
            Location structureLoc = structure.toLocation();
            if (structureLoc == null) continue;

            if (structure.isWithinRange(player.getLocation(), triggerRadius)) {
                checkAndRespawnMobs(structure);
            }
        }
    }

    /**
     * Register a structure in the spatial index for efficient proximity queries.
     */
    public void indexStructure(StructureLocationData structure) {
        String worldName = structure.getWorldName();

        // Calculate chunk range covered by structure
        int triggerRadius = DefaultConfig.getMobRespawnTriggerRadius();
        int minChunkX = (structure.x() - triggerRadius) >> 4;
        int maxChunkX = (structure.x() + triggerRadius) >> 4;
        int minChunkZ = (structure.z() - triggerRadius) >> 4;
        int maxChunkZ = (structure.z() + triggerRadius) >> 4;

        Map<Long, Set<StructureLocationData>> chunkMap =
                worldChunkIndex.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long chunkKey = getChunkKey(cx, cz);
                chunkMap.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet())
                        .add(structure);
            }
        }
    }

    /**
     * Get structures near a player using the spatial index.
     */
    public Set<StructureLocationData> getStructuresNearPlayer(Player player, int radius) {
        Set<StructureLocationData> result = new HashSet<>();
        String worldName = player.getWorld().getName();

        Map<Long, Set<StructureLocationData>> chunkMap = worldChunkIndex.get(worldName);
        if (chunkMap == null) return result;

        Location loc = player.getLocation();
        int playerChunkX = loc.getBlockX() >> 4;
        int playerChunkZ = loc.getBlockZ() >> 4;
        int chunkRadius = (radius >> 4) + 1;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                long chunkKey = getChunkKey(playerChunkX + dx, playerChunkZ + dz);
                Set<StructureLocationData> structures = chunkMap.get(chunkKey);
                if (structures != null) {
                    result.addAll(structures);
                }
            }
        }

        return result;
    }

    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Register mobs spawned for a structure.
     */
    public void registerStructureMobs(StructureLocationData structure, List<UUID> mobUUIDs,
                                       List<MobSpawnConfig> mobConfigs) {
        // Store spawn configs for respawning
        structure.setMobSpawnConfigs(mobConfigs);

        // Track current mobs with their config indices
        for (int i = 0; i < mobUUIDs.size() && i < mobConfigs.size(); i++) {
            UUID uuid = mobUUIDs.get(i);
            structure.addTrackedMob(uuid);
            mobToStructureMap.put(uuid, structure);
            mobToConfigIndexMap.put(uuid, i);
        }

        // Index the structure for proximity queries
        indexStructure(structure);

        Logger.debug("Registered " + mobUUIDs.size() + " mobs for structure at " + structure.getFormattedCoordinates());
    }

    /**
     * Register a single mob spawn (used during respawn).
     */
    public void registerMobSpawn(StructureLocationData structure, Entity mob, int configIndex) {
        if (mob == null) return;

        UUID uuid = mob.getUniqueId();
        structure.addTrackedMob(uuid);
        mobToStructureMap.put(uuid, structure);
        mobToConfigIndexMap.put(uuid, configIndex);
    }

    /**
     * Handle mob death event.
     */
    public void onMobDeath(UUID mobUUID, Player killer) {
        StructureLocationData structure = mobToStructureMap.remove(mobUUID);
        if (structure == null) return;

        // Mark this spawn config as killed (so it won't respawn)
        Integer configIndex = mobToConfigIndexMap.remove(mobUUID);
        if (configIndex != null) {
            structure.markSpawnAsKilled(configIndex);
            Logger.debug("Marked spawn index " + configIndex + " as killed for structure at " + structure.getFormattedCoordinates());
        }

        structure.removeTrackedMob(mobUUID);

        // Check if all mobs are dead
        if (isStructureCleared(structure)) {
            handleStructureCleared(structure, killer);
        }

        // Mark for saving
        StructureLocationManager.getInstance().markDirty(structure.getWorldName());
    }

    /**
     * Check if a mob UUID is being tracked.
     */
    public boolean isTrackedMob(UUID uuid) {
        return mobToStructureMap.containsKey(uuid);
    }

    /**
     * Get the structure associated with a mob.
     */
    public StructureLocationData getStructureByMob(UUID mobUUID) {
        return mobToStructureMap.get(mobUUID);
    }

    /**
     * Check if all mobs in a structure have been killed.
     */
    public boolean isStructureCleared(StructureLocationData structure) {
        if (structure.isCleared()) return true;

        // Validate tracked mobs still exist
        validateTrackedMobs(structure);

        return structure.getAliveMobCount() == 0 && structure.hasMobSpawns();
    }

    /**
     * Validate that tracked mobs still exist in the world.
     */
    private void validateTrackedMobs(StructureLocationData structure) {
        Set<UUID> toRemove = new HashSet<>();

        for (UUID uuid : structure.getTrackedMobUUIDs()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || entity.isDead()) {
                toRemove.add(uuid);
            }
        }

        for (UUID uuid : toRemove) {
            structure.removeTrackedMob(uuid);
            mobToStructureMap.remove(uuid);
        }
    }

    /**
     * Check and respawn mobs for a structure if needed.
     */
    public void checkAndRespawnMobs(StructureLocationData structure) {
        if (structure.isCleared()) return;

        String structureKey = structure.getWorldName() + "_" + structure.getKey();

        // Prevent concurrent processing
        if (!processingStructures.add(structureKey)) return;

        try {
            // Validate existing mobs
            validateTrackedMobs(structure);

            // Check if respawn is needed
            int aliveMobs = structure.getAliveMobCount();
            int totalMobs = structure.getTotalMobCount();

            if (aliveMobs >= totalMobs) return;

            // Respawn missing mobs
            List<Entity> respawnedMobs = respawnMobs(structure);

            if (!respawnedMobs.isEmpty()) {
                structure.incrementRespawnCount();
                StructureLocationManager.getInstance().markDirty(structure.getWorldName());
                Logger.debug("Respawned " + respawnedMobs.size() + " mobs at structure " + structure.getFormattedCoordinates());
            }
        } finally {
            processingStructures.remove(structureKey);
        }
    }

    /**
     * Respawn mobs for a structure.
     */
    private List<Entity> respawnMobs(StructureLocationData structure) {
        List<Entity> respawnedMobs = new ArrayList<>();
        Location structureOrigin = structure.toLocation();

        if (structureOrigin == null) return respawnedMobs;

        World world = structureOrigin.getWorld();
        if (world == null) return respawnedMobs;

        // Track which config indices already have alive mobs
        Set<Integer> aliveConfigIndices = new HashSet<>();
        for (UUID uuid : structure.getTrackedMobUUIDs()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                // This mob is still alive, get its config index
                Integer configIndex = mobToConfigIndexMap.get(uuid);
                if (configIndex != null) {
                    aliveConfigIndices.add(configIndex);
                }
            }
        }

        List<MobSpawnConfig> configs = structure.getMobSpawnConfigs();
        for (int i = 0; i < configs.size(); i++) {
            MobSpawnConfig config = configs.get(i);

            // Skip mobs that were killed (not just despawned)
            if (structure.isSpawnKilled(i)) {
                continue;
            }

            // Skip if a mob for this config index is still alive
            if (aliveConfigIndices.contains(i)) {
                continue;
            }

            // Skip EliteMobs (they have their own persistence)
            if (!config.shouldRespawn()) continue;

            // Calculate spawn location: structure origin + relative offset + 1 block higher for respawn
            Location spawnLoc = structureOrigin.clone().add(
                    config.getRelativeX(),
                    config.getRelativeY() + 1,  // Spawn 1 block higher to avoid getting stuck
                    config.getRelativeZ()
            );

            // Skip if chunk is not loaded to avoid sync chunk loading with FAWE
            if (!spawnLoc.getWorld().isChunkLoaded(spawnLoc.getBlockX() >> 4, spawnLoc.getBlockZ() >> 4)) {
                continue;
            }

            Entity spawnedMob = null;

            switch (config.getMobType()) {
                case VANILLA:
                    try {
                        EntityType entityType = EntityType.valueOf(config.getMobIdentifier());
                        spawnLoc.getBlock().setBlockData(Material.AIR.createBlockData(), false);
                        spawnLoc.add(new Vector(0.5, 0, 0.5));
                        spawnedMob = world.spawnEntity(spawnLoc, entityType);
                        if (spawnedMob instanceof LivingEntity) {
                            ((LivingEntity) spawnedMob).setRemoveWhenFarAway(false);
                        }
                        spawnedMob.setPersistent(true);
                    } catch (IllegalArgumentException e) {
                        Logger.warn("重生原版生物失败: " + config.getMobIdentifier());
                    }
                    break;

                case MYTHICMOBS:
                    spawnLoc.getBlock().setBlockData(Material.AIR.createBlockData(), false);
                    spawnedMob = MythicMobs.spawnAndReturn(spawnLoc, config.getMobIdentifier());
                    break;

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

                case ELITEMOBS:
                    // EliteMobs handles its own persistence, skip respawn
                    break;
            }

            if (spawnedMob != null) {
                respawnedMobs.add(spawnedMob);
                registerMobSpawn(structure, spawnedMob, i);
            }
        }

        return respawnedMobs;
    }

    /**
     * Handle structure being cleared of all mobs.
     */
    private void handleStructureCleared(StructureLocationData structure, Player killer) {
        // Fire event
        StructureClearedEvent event = new StructureClearedEvent(structure, killer);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) return;

        // Mark as cleared
        structure.setCleared(true);

        // Notify nearby players
        notifyNearbyPlayers(structure, killer);

        // Mark for saving
        StructureLocationManager.getInstance().markDirty(structure.getWorldName());

        Logger.info("位于 " + structure.getFormattedCoordinates() + " 的建筑已被清除！");
    }

    /**
     * Notify players near the structure about the clearing.
     */
    private void notifyNearbyPlayers(StructureLocationData structure, Player killer) {
        Location center = structure.toLocation();
        if (center == null) return;

        int notifyRadius = DefaultConfig.getStructureClearedNotifyRadius();
        String message = DefaultConfig.getStructureClearedMessage()
                .replace("{structure}", structure.schematicName())
                .replace("{player}", killer != null ? killer.getName() : "Unknown");

        // Translate color codes
        message = message.replace("&", "\u00A7");

        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distance(center) <= notifyRadius) {
                player.sendMessage(message);
                // Play celebration sound
                try {
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                } catch (Exception ignored) {
                    // Sound might not exist on older versions
                }
            }
        }
    }

    /**
     * Test structure cleared commands at a player's location.
     */
    public void testCommands(Player player) {
        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        Logger.info("Testing cleared commands for player " + player.getName() + " at " + worldName + " " + x + " " + y + " " + z);

        for (String command : DefaultConfig.getStructureClearedCommands()) {
            if (command == null || command.isEmpty()) continue;

            String parsedCommand = command
                    .replace("{player}", player.getName())
                    .replace("{structure}", "TestStructure")
                    .replace("{killer}", player.getName())
                    .replace("{world}", worldName)
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y))
                    .replace("{z}", String.valueOf(z));

            try {
                Logger.info("执行测试命令: " + parsedCommand);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
            } catch (Exception e) {
                Logger.warn("执行测试命令失败: " + parsedCommand + " - " + e.getMessage());
            }
        }
    }

    /**
     * Rebuild spatial index from StructureLocationManager data.
     */
    public void rebuildIndex() {
        worldChunkIndex.clear();
        mobToStructureMap.clear();

        for (String worldName : Bukkit.getWorlds().stream().map(World::getName).toList()) {
            for (StructureLocationData structure : StructureLocationManager.getInstance().getStructuresInWorld(worldName)) {
                if (structure.hasMobSpawns() && !structure.isCleared()) {
                    indexStructure(structure);
                }
            }
        }

        Logger.info("已重建生物追踪空间索引");
    }
}
