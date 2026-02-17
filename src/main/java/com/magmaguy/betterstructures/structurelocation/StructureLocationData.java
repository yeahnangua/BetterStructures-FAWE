package com.magmaguy.betterstructures.structurelocation;

import com.magmaguy.betterstructures.config.generators.GeneratorConfigFields.StructureType;
import com.magmaguy.betterstructures.mobtracking.MobSpawnConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data class for storing structure location and mob tracking information.
 */
public class StructureLocationData {
    // Basic info (original fields)
    private final int x;
    private final int y;
    private final int z;
    private final String worldName;
    private final String schematicName;
    private final StructureType structureType;

    // Structure bounds (for proximity detection)
    private final int radiusX;
    private final int radiusY;
    private final int radiusZ;

    // Mob tracking state
    private boolean cleared;
    private long clearedTimestamp;
    private int respawnCount;
    private final Set<UUID> trackedMobUUIDs;
    private final List<MobSpawnConfig> mobSpawnConfigs;
    private final Set<Integer> killedSpawnIndices;  // Indices of mobs that were killed (should not respawn)

    // Metadata
    private final long createdTimestamp;
    private boolean bossStructure;

    /**
     * Constructor for new structures (when generating).
     */
    public StructureLocationData(int x, int y, int z, String worldName, String schematicName,
                                  StructureType structureType, int radiusX, int radiusY, int radiusZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.worldName = worldName;
        this.schematicName = schematicName;
        this.structureType = structureType;
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
        this.cleared = false;
        this.clearedTimestamp = 0;
        this.respawnCount = 0;
        this.trackedMobUUIDs = ConcurrentHashMap.newKeySet();
        this.mobSpawnConfigs = new ArrayList<>();
        this.killedSpawnIndices = ConcurrentHashMap.newKeySet();
        this.createdTimestamp = System.currentTimeMillis();
        this.bossStructure = false;
    }

    /**
     * Constructor for loading from storage.
     */
    public StructureLocationData(int x, int y, int z, String worldName, String schematicName,
                                  StructureType structureType, int radiusX, int radiusY, int radiusZ,
                                  boolean cleared, long clearedTimestamp, int respawnCount,
                                  List<MobSpawnConfig> mobSpawnConfigs, Set<Integer> killedSpawnIndices,
                                  long createdTimestamp, boolean bossStructure) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.worldName = worldName;
        this.schematicName = schematicName;
        this.structureType = structureType;
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
        this.cleared = cleared;
        this.clearedTimestamp = clearedTimestamp;
        this.respawnCount = respawnCount;
        this.trackedMobUUIDs = ConcurrentHashMap.newKeySet();
        this.mobSpawnConfigs = mobSpawnConfigs != null ? new ArrayList<>(mobSpawnConfigs) : new ArrayList<>();
        this.killedSpawnIndices = ConcurrentHashMap.newKeySet();
        if (killedSpawnIndices != null) {
            this.killedSpawnIndices.addAll(killedSpawnIndices);
        }
        this.createdTimestamp = createdTimestamp;
        this.bossStructure = bossStructure;
    }

    /**
     * Backward compatibility constructor (for existing data without mob tracking).
     */
    public StructureLocationData(int x, int y, int z, String worldName, String schematicName, StructureType structureType) {
        this(x, y, z, worldName, schematicName, structureType, 16, 16, 16);
    }

    // Getters for basic info
    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public String getWorldName() {
        return worldName;
    }

    public String schematicName() {
        return schematicName;
    }

    public StructureType structureType() {
        return structureType;
    }

    // Getters for bounds
    public int getRadiusX() {
        return radiusX;
    }

    public int getRadiusY() {
        return radiusY;
    }

    public int getRadiusZ() {
        return radiusZ;
    }

    // Getters and setters for mob tracking state
    public boolean isCleared() {
        return cleared;
    }

    public void setCleared(boolean cleared) {
        this.cleared = cleared;
        if (cleared) {
            this.clearedTimestamp = System.currentTimeMillis();
        }
    }

    public long getClearedTimestamp() {
        return clearedTimestamp;
    }

    public int getRespawnCount() {
        return respawnCount;
    }

    public void incrementRespawnCount() {
        this.respawnCount++;
    }

    public Set<UUID> getTrackedMobUUIDs() {
        return trackedMobUUIDs;
    }

    public void addTrackedMob(UUID uuid) {
        trackedMobUUIDs.add(uuid);
    }

    public void removeTrackedMob(UUID uuid) {
        trackedMobUUIDs.remove(uuid);
    }

    public void clearTrackedMobs() {
        trackedMobUUIDs.clear();
    }

    public List<MobSpawnConfig> getMobSpawnConfigs() {
        return Collections.unmodifiableList(mobSpawnConfigs);
    }

    public void addMobSpawnConfig(MobSpawnConfig config) {
        mobSpawnConfigs.add(config);
    }

    public void setMobSpawnConfigs(List<MobSpawnConfig> configs) {
        mobSpawnConfigs.clear();
        if (configs != null) {
            mobSpawnConfigs.addAll(configs);
        }
    }

    public Set<Integer> getKilledSpawnIndices() {
        return Collections.unmodifiableSet(killedSpawnIndices);
    }

    public void markSpawnAsKilled(int index) {
        killedSpawnIndices.add(index);
    }

    public boolean isSpawnKilled(int index) {
        return killedSpawnIndices.contains(index);
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public boolean isBossStructure() {
        return bossStructure;
    }

    public void setBossStructure(boolean bossStructure) {
        this.bossStructure = bossStructure;
    }

    /**
     * Returns formatted coordinates as "x, y, z" string.
     */
    public String getFormattedCoordinates() {
        return x + ", " + y + ", " + z;
    }

    /**
     * Converts this data to a Bukkit Location.
     */
    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    /**
     * Generates a unique key for this structure location.
     */
    public String getKey() {
        return x + "_" + y + "_" + z;
    }

    /**
     * Check if a location is within this structure's bounds.
     */
    public boolean isWithinBounds(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().getName().equals(worldName)) return false;

        double dx = Math.abs(location.getX() - x);
        double dy = Math.abs(location.getY() - y);
        double dz = Math.abs(location.getZ() - z);

        return dx <= radiusX && dy <= radiusY && dz <= radiusZ;
    }

    /**
     * Check if a location is within a spherical range of this structure.
     */
    public boolean isWithinRange(Location location, double range) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().getName().equals(worldName)) return false;

        double dx = location.getX() - x;
        double dy = location.getY() - y;
        double dz = location.getZ() - z;

        return (dx * dx + dy * dy + dz * dz) <= (range * range);
    }

    /**
     * Check if this structure has any mobs configured for spawning.
     */
    public boolean hasMobSpawns() {
        return !mobSpawnConfigs.isEmpty();
    }

    /**
     * Get the count of currently tracked (alive) mobs.
     */
    public int getAliveMobCount() {
        return trackedMobUUIDs.size();
    }

    /**
     * Get the total configured mob count.
     */
    public int getTotalMobCount() {
        return mobSpawnConfigs.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StructureLocationData that = (StructureLocationData) o;
        return x == that.x && y == that.y && z == that.z && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, worldName);
    }
}
