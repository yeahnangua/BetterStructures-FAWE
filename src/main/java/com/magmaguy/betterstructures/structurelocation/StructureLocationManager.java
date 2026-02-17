package com.magmaguy.betterstructures.structurelocation;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.config.generators.GeneratorConfigFields.StructureType;
import com.magmaguy.betterstructures.mobtracking.MobSpawnConfig;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the persistence of structure locations to YAML files.
 * Each world has its own file in the structure_locations folder.
 */
public class StructureLocationManager {

    private static StructureLocationManager instance;

    private final File storageFolder;

    // Memory cache: worldName -> (locationKey -> StructureLocationData)
    private final Map<String, Map<String, StructureLocationData>> locationCache = new ConcurrentHashMap<>();

    // Set of worlds that have been modified and need saving
    private final Set<String> dirtyWorlds = ConcurrentHashMap.newKeySet();

    // Auto-save task
    private BukkitRunnable saveTask;

    // Save interval in ticks (1 minute = 1200 ticks)
    private static final long SAVE_INTERVAL_TICKS = 1200L;

    private StructureLocationManager() {
        this.storageFolder = new File(MetadataHandler.PLUGIN.getDataFolder(), "structure_locations");
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
        loadAllWorldData();
        startAutoSaveTask();
    }

    /**
     * Gets the singleton instance of the manager.
     */
    public static StructureLocationManager getInstance() {
        if (instance == null) {
            instance = new StructureLocationManager();
        }
        return instance;
    }

    /**
     * Records a newly generated structure location.
     * This method is thread-safe and can be called from any thread.
     *
     * @param location      The location of the structure
     * @param schematicName The name of the schematic/config file
     * @param structureType The type of structure
     */
    public void recordStructure(Location location, String schematicName, StructureType structureType) {
        recordStructure(location, schematicName, structureType, 16, 16, 16);
    }

    /**
     * Records a newly generated structure location with custom bounds.
     *
     * @param location      The location of the structure
     * @param schematicName The name of the schematic/config file
     * @param structureType The type of structure
     * @param radiusX       Structure radius in X direction
     * @param radiusY       Structure radius in Y direction
     * @param radiusZ       Structure radius in Z direction
     */
    public void recordStructure(Location location, String schematicName, StructureType structureType,
                                 int radiusX, int radiusY, int radiusZ) {
        if (location == null || location.getWorld() == null) {
            Logger.warn("Attempted to record structure with null location or world");
            return;
        }

        String worldName = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        String key = generateKey(x, y, z);
        StructureLocationData data = new StructureLocationData(x, y, z, worldName, schematicName,
                structureType, radiusX, radiusY, radiusZ);

        // Update cache
        locationCache.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>()).put(key, data);

        // Mark world as dirty
        dirtyWorlds.add(worldName);
    }

    /**
     * Records a structure with full data (used when updating existing structures).
     */
    public void recordStructure(StructureLocationData data) {
        if (data == null) return;

        String worldName = data.getWorldName();
        String key = data.getKey();

        locationCache.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>()).put(key, data);
        dirtyWorlds.add(worldName);
    }

    /**
     * Get a structure at a specific location.
     */
    public StructureLocationData getStructureAt(String worldName, int x, int y, int z) {
        Map<String, StructureLocationData> worldLocations = locationCache.get(worldName);
        if (worldLocations == null) return null;
        return worldLocations.get(generateKey(x, y, z));
    }

    /**
     * Mark a world as dirty (needs saving).
     */
    public void markDirty(String worldName) {
        dirtyWorlds.add(worldName);
    }

    /**
     * Generates a unique key for a location.
     */
    private String generateKey(int x, int y, int z) {
        return x + "_" + y + "_" + z;
    }

    /**
     * Loads all world data from files on startup.
     */
    private void loadAllWorldData() {
        File[] files = storageFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String worldName = file.getName().replace(".yml", "");
            loadWorldData(worldName, file);
        }
    }

    /**
     * Loads data for a single world from its file.
     */
    private void loadWorldData(String worldName, File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection structuresSection = config.getConfigurationSection("structures");

        if (structuresSection == null) return;

        Map<String, StructureLocationData> worldLocations = new ConcurrentHashMap<>();

        for (String key : structuresSection.getKeys(false)) {
            ConfigurationSection locationSection = structuresSection.getConfigurationSection(key);
            if (locationSection == null) continue;

            int x = locationSection.getInt("x");
            int y = locationSection.getInt("y");
            int z = locationSection.getInt("z");
            String schematic = locationSection.getString("schematic", "unknown");
            String typeString = locationSection.getString("type", "UNDEFINED");

            StructureType type;
            try {
                type = StructureType.valueOf(typeString);
            } catch (IllegalArgumentException e) {
                type = StructureType.UNDEFINED;
            }

            // Load new mob tracking fields
            int radiusX = locationSection.getInt("radiusX", 16);
            int radiusY = locationSection.getInt("radiusY", 16);
            int radiusZ = locationSection.getInt("radiusZ", 16);
            boolean cleared = locationSection.getBoolean("cleared", false);
            long clearedTimestamp = locationSection.getLong("clearedTimestamp", 0);
            int respawnCount = locationSection.getInt("respawnCount", 0);
            long createdTimestamp = locationSection.getLong("createdTimestamp", System.currentTimeMillis());
            boolean bossStructure = locationSection.getBoolean("bossStructure", false);

            // Load mob spawn configs
            List<MobSpawnConfig> mobSpawnConfigs = new ArrayList<>();
            List<?> mobSpawnsList = locationSection.getList("mobSpawns");
            if (mobSpawnsList != null) {
                for (Object obj : mobSpawnsList) {
                    if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mobMap = (Map<String, Object>) obj;
                        try {
                            MobSpawnConfig.MobType mobType = MobSpawnConfig.MobType.valueOf(
                                    (String) mobMap.getOrDefault("type", "VANILLA"));
                            String identifier = (String) mobMap.getOrDefault("identifier", "ZOMBIE");
                            double relX = ((Number) mobMap.getOrDefault("relX", 0.0)).doubleValue();
                            double relY = ((Number) mobMap.getOrDefault("relY", 0.0)).doubleValue();
                            double relZ = ((Number) mobMap.getOrDefault("relZ", 0.0)).doubleValue();

                            mobSpawnConfigs.add(new MobSpawnConfig(mobType, identifier, relX, relY, relZ));
                        } catch (Exception e) {
                            Logger.warn("Failed to load mob spawn config: " + e.getMessage());
                        }
                    }
                }
            }

            // Load killed spawn indices
            Set<Integer> killedSpawnIndices = new HashSet<>();
            List<Integer> killedList = locationSection.getIntegerList("killedSpawnIndices");
            killedSpawnIndices.addAll(killedList);

            StructureLocationData data = new StructureLocationData(
                    x, y, z, worldName, schematic, type,
                    radiusX, radiusY, radiusZ,
                    cleared, clearedTimestamp, respawnCount,
                    mobSpawnConfigs, killedSpawnIndices, createdTimestamp, bossStructure
            );

            worldLocations.put(key, data);
        }

        locationCache.put(worldName, worldLocations);
    }

    /**
     * Starts the auto-save task that periodically saves dirty worlds.
     */
    private void startAutoSaveTask() {
        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveAllDirtyWorlds();
            }
        };
        saveTask.runTaskTimerAsynchronously(MetadataHandler.PLUGIN, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    /**
     * Saves all worlds that have been modified.
     */
    public void saveAllDirtyWorlds() {
        Set<String> worldsToSave = new HashSet<>(dirtyWorlds);
        dirtyWorlds.clear();

        for (String worldName : worldsToSave) {
            saveWorldData(worldName);
        }
    }

    /**
     * Saves data for a single world to its file.
     */
    private void saveWorldData(String worldName) {
        Map<String, StructureLocationData> worldLocations = locationCache.get(worldName);
        if (worldLocations == null || worldLocations.isEmpty()) return;

        File file = new File(storageFolder, worldName + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.options().setHeader(Arrays.asList(
                "BetterStructures - Generated Structure Locations",
                "This file is automatically maintained by the plugin.",
                "Each entry contains the coordinates, schematic name, structure type, and mob tracking data."
        ));

        for (Map.Entry<String, StructureLocationData> entry : worldLocations.entrySet()) {
            String key = entry.getKey();
            StructureLocationData data = entry.getValue();

            String path = "structures." + key;
            config.set(path + ".x", data.x());
            config.set(path + ".y", data.y());
            config.set(path + ".z", data.z());
            config.set(path + ".schematic", data.schematicName());
            config.set(path + ".type", data.structureType().name());

            // Save new mob tracking fields
            config.set(path + ".radiusX", data.getRadiusX());
            config.set(path + ".radiusY", data.getRadiusY());
            config.set(path + ".radiusZ", data.getRadiusZ());
            config.set(path + ".cleared", data.isCleared());
            config.set(path + ".clearedTimestamp", data.getClearedTimestamp());
            config.set(path + ".respawnCount", data.getRespawnCount());
            config.set(path + ".createdTimestamp", data.getCreatedTimestamp());
            config.set(path + ".bossStructure", data.isBossStructure());

            // Save mob spawn configs
            if (!data.getMobSpawnConfigs().isEmpty()) {
                List<Map<String, Object>> mobSpawnsList = new ArrayList<>();
                for (MobSpawnConfig mobConfig : data.getMobSpawnConfigs()) {
                    Map<String, Object> mobMap = new LinkedHashMap<>();
                    mobMap.put("type", mobConfig.getMobType().name());
                    mobMap.put("identifier", mobConfig.getMobIdentifier());
                    mobMap.put("relX", mobConfig.getRelativeX());
                    mobMap.put("relY", mobConfig.getRelativeY());
                    mobMap.put("relZ", mobConfig.getRelativeZ());
                    mobSpawnsList.add(mobMap);
                }
                config.set(path + ".mobSpawns", mobSpawnsList);
            }

            // Save killed spawn indices
            if (!data.getKilledSpawnIndices().isEmpty()) {
                config.set(path + ".killedSpawnIndices", new ArrayList<>(data.getKilledSpawnIndices()));
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            Logger.warn("Failed to save structure locations for world: " + worldName);
            e.printStackTrace();
            // Re-mark as dirty to retry next save cycle
            dirtyWorlds.add(worldName);
        }
    }

    /**
     * Gets all structure locations in a specific world.
     *
     * @param worldName The name of the world
     * @return Unmodifiable collection of structure locations
     */
    public Collection<StructureLocationData> getStructuresInWorld(String worldName) {
        Map<String, StructureLocationData> worldLocations = locationCache.get(worldName);
        if (worldLocations == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(worldLocations.values());
    }

    /**
     * Checks if a structure exists at the given location.
     *
     * @param worldName The name of the world
     * @param x         X coordinate
     * @param y         Y coordinate
     * @param z         Z coordinate
     * @return true if a structure exists at this location
     */
    public boolean hasStructureAt(String worldName, int x, int y, int z) {
        Map<String, StructureLocationData> worldLocations = locationCache.get(worldName);
        if (worldLocations == null) return false;
        return worldLocations.containsKey(generateKey(x, y, z));
    }

    /**
     * Shuts down the manager, saving all pending data.
     * Should be called when the plugin is disabled.
     */
    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
        }

        // Save all data synchronously on shutdown
        for (String worldName : locationCache.keySet()) {
            saveWorldData(worldName);
        }

        instance = null;
    }

    /**
     * Reloads all data from files.
     */
    public void reload() {
        saveAllDirtyWorlds();
        locationCache.clear();
        loadAllWorldData();
    }
}
