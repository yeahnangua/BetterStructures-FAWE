package com.magmaguy.betterstructures.mobtracking;

/**
 * Configuration for a mob spawn point within a structure.
 * Used for tracking and respawning mobs.
 */
public class MobSpawnConfig {

    public enum MobType {
        VANILLA,
        ELITEMOBS,
        MYTHICMOBS,
        VANILLA_MM_OVERRIDE,      // Vanilla mob replaced with MM mob (respawns with random re-selection)
        ELITEMOBS_MM_OVERRIDE     // EM boss replaced with MM boss (does not respawn)
    }

    private final MobType mobType;
    private final String mobIdentifier;  // EntityType.name() or boss filename
    private final double relativeX;      // Position relative to structure origin
    private final double relativeY;
    private final double relativeZ;

    public MobSpawnConfig(MobType mobType, String mobIdentifier, double relativeX, double relativeY, double relativeZ) {
        this.mobType = mobType;
        this.mobIdentifier = mobIdentifier;
        this.relativeX = relativeX;
        this.relativeY = relativeY;
        this.relativeZ = relativeZ;
    }

    public MobType getMobType() {
        return mobType;
    }

    public String getMobIdentifier() {
        return mobIdentifier;
    }

    public double getRelativeX() {
        return relativeX;
    }

    public double getRelativeY() {
        return relativeY;
    }

    public double getRelativeZ() {
        return relativeZ;
    }

    /**
     * Check if this mob type should be respawned by BetterStructures.
     * EliteMobs has its own persistence mechanism, so we only track state.
     */
    public boolean shouldRespawn() {
        return mobType != MobType.ELITEMOBS && mobType != MobType.ELITEMOBS_MM_OVERRIDE;
    }
}
