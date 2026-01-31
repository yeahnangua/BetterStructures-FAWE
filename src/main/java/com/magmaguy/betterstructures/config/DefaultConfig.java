package com.magmaguy.betterstructures.config;

import com.magmaguy.magmacore.config.ConfigurationEngine;
import com.magmaguy.magmacore.config.ConfigurationFile;
import lombok.Getter;

import java.util.List;

public class DefaultConfig extends ConfigurationFile {
    @Getter
    private static int lowestYNormalCustom;
    @Getter
    private static int highestYNormalCustom;
    @Getter
    private static int lowestYNether;
    @Getter
    private static int highestYNether;
    @Getter
    private static int lowestYEnd;
    @Getter
    private static int highestYEnd;
    @Getter
    private static int normalCustomAirBuildingMinAltitude;
    @Getter
    private static int normalCustomAirBuildingMaxAltitude;
    @Getter
    private static int endAirBuildMinAltitude;
    @Getter
    private static int endAirBuildMaxAltitude;
    @Getter
    private static boolean newBuildingWarn;
    @Getter
    private static String regionProtectedMessage;
    @Getter
    private static boolean protectEliteMobsRegions;
    private static DefaultConfig instance;
    @Getter
    private static boolean setupDone;
    @Getter
    private static int modularChunkPastingSpeed = 10;
    @Getter
    private static double percentageOfTickUsedForPasting = 0.2;
    @Getter
    private static double percentageOfTickUsedForPregeneration = 0.1;
    @Getter
    private static double pregenerationTPSPauseThreshold = 12.0;
    @Getter
    private static double pregenerationTPSResumeThreshold = 14.0;

    // Adding getters for the new distance and offset variables
    @Getter
    private static int distanceSurface;
    @Getter
    private static int distanceShallow;
    @Getter
    private static int distanceDeep;
    @Getter
    private static int distanceSky;
    @Getter
    private static int distanceLiquid;
    @Getter
    private static int distanceDungeon;

    @Getter
    private static int maxOffsetSurface;
    @Getter
    private static int maxOffsetShallow;
    @Getter
    private static int maxOffsetDeep;
    @Getter
    private static int maxOffsetSky;
    @Getter
    private static int maxOffsetLiquid;
    @Getter
    private static int maxOffsetDungeon;

    // Mob tracking configuration
    @Getter
    private static boolean mobTrackingEnabled;
    @Getter
    private static int mobRespawnTriggerRadius;
    @Getter
    private static int proximityCheckInterval;
    @Getter
    private static int structureClearedNotifyRadius;
    @Getter
    private static String structureClearedMessage;
    @Getter
    private static List<String> structureClearedCommands;

    // Terra/FAWE compatibility settings
    @Getter
    private static int structureScanDelayTicks;
    @Getter
    private static int structureScanMaxRetries;
    @Getter
    private static boolean terraCompatibilityMode;
    @Getter
    private static boolean validateChunkBeforePaste;

    // Structure queue settings
    @Getter
    private static boolean structureQueueEnabled;
    @Getter
    private static int structureQueueMaxSize;
    @Getter
    private static int structureQueueTimeoutSeconds;

    public DefaultConfig() {
        super("config.yml");
        instance = this;
    }

    public static void toggleSetupDone() {
        setupDone = !setupDone;
        ConfigurationEngine.writeValue(setupDone, instance.file, instance.getFileConfiguration(), "setupDone");
    }

    public static void toggleSetupDone(boolean value) {
        setupDone = value;
        ConfigurationEngine.writeValue(setupDone, instance.file, instance.getFileConfiguration(), "setupDone");
    }


    public static boolean toggleWarnings() {
        newBuildingWarn = !newBuildingWarn;
        ConfigurationEngine.writeValue(newBuildingWarn, instance.file, instance.fileConfiguration, "warnAdminsAboutNewBuildings");
        return newBuildingWarn;
    }

    @Override
    public void initializeValues() {
        lowestYNormalCustom = ConfigurationEngine.setInt(fileConfiguration, "lowestYNormalCustom", -60);
        highestYNormalCustom = ConfigurationEngine.setInt(fileConfiguration, "highestYNormalCustom", 320);
        lowestYNether = ConfigurationEngine.setInt(fileConfiguration, "lowestYNether", 4);
        highestYNether = ConfigurationEngine.setInt(fileConfiguration, "highestYNether", 120);
        lowestYEnd = ConfigurationEngine.setInt(fileConfiguration, "lowestYEnd", 0);
        highestYEnd = ConfigurationEngine.setInt(fileConfiguration, "highestYEnd", 320);
        normalCustomAirBuildingMinAltitude = ConfigurationEngine.setInt(fileConfiguration, "normalCustomAirBuildingMinAltitude", 80);
        normalCustomAirBuildingMaxAltitude = ConfigurationEngine.setInt(fileConfiguration, "normalCustomAirBuildingMaxAltitude", 120);
        endAirBuildMinAltitude = ConfigurationEngine.setInt(fileConfiguration, "endAirBuildMinAltitude", 80);
        endAirBuildMaxAltitude = ConfigurationEngine.setInt(fileConfiguration, "endAirBuildMaxAltitude", 120);
        newBuildingWarn = ConfigurationEngine.setBoolean(fileConfiguration, "warnAdminsAboutNewBuildings", true);
        regionProtectedMessage = ConfigurationEngine.setString(fileConfiguration, "regionProtectedMessage", "&8[BetterStructures] &cDefeat the zone's bosses to edit blocks!");
        protectEliteMobsRegions = ConfigurationEngine.setBoolean(fileConfiguration, "protectEliteMobsRegions", true);
        setupDone = ConfigurationEngine.setBoolean(fileConfiguration, "setupDone", false);
        modularChunkPastingSpeed = ConfigurationEngine.setInt(fileConfiguration, "modularChunkPastingSpeed", 10);
        percentageOfTickUsedForPasting = ConfigurationEngine.setDouble(List.of("Sets the maximum percentage of a tick that BetterStructures will use to paste builds, however many it maybe trying to generate.", "Ranges from 0.01 to 1, where 0.01 is 1% and 1 is 100%.", "Slower speeds will lower performance impact, but can lead to other problems such as builds suddenly popping in."),fileConfiguration, "percentageOfTickUsedForPasting", 0.2);
        percentageOfTickUsedForPregeneration = ConfigurationEngine.setDouble(List.of("Sets the maximum percentage of a tick that BetterStructures will use for world pregeneration when using the pregenerate command.", "Ranges from 0.01 to 1, where 0.01 is 1% and 1 is 100%.", "This controls how much of each server tick is dedicated to generating chunks, allowing you to balance generation speed with server performance.", "Lower values will generate chunks more slowly but reduce server lag, while higher values will generate faster but may impact server performance."), fileConfiguration, "percentageOfTickUsedForPregeneration", 0.1);
        pregenerationTPSPauseThreshold = ConfigurationEngine.setDouble(List.of("The TPS threshold at which chunk pregeneration will pause to protect server performance.", "When server TPS drops below this value, pregeneration will pause until TPS recovers.", "Default: 12.0"), fileConfiguration, "pregenerationTPSPauseThreshold", 12.0);
        pregenerationTPSResumeThreshold = ConfigurationEngine.setDouble(List.of("The TPS threshold at which chunk pregeneration will resume after being paused.", "Pregeneration will only resume when server TPS is at or above this value.", "Should be higher than the pause threshold to prevent rapid pause/resume cycles.", "Default: 14.0"), fileConfiguration, "pregenerationTPSResumeThreshold", 14.0);

        // Initialize the distances from configuration
        distanceSurface = ConfigurationEngine.setInt(
                List.of(
                        "Sets the distance between structures in the surface of a world.",
                        "Shorter distances between structures will result in more structures overall."),
                fileConfiguration, "distanceSurface", 31);
        distanceShallow = ConfigurationEngine.setInt(
                List.of(
                        "Sets the distance between structures in shallow underground structure generation.",
                        "Shorter distances between structures will result in more structures overall."),fileConfiguration, "distanceShallow", 22);
        distanceDeep = ConfigurationEngine.setInt(
                List.of(
                        "Sets the distance between structures in deep underground structure generation.",
                        "Shorter distances between structures will result in more structures overall."),
                fileConfiguration, "distanceDeep", 22);
        distanceSky = ConfigurationEngine.setInt(
                List.of(
                        "Sets the distance between structures in placed in the air.",
                        "Shorter distances between structures will result in more structures overall."),
                fileConfiguration, "distanceSky", 95);
        distanceLiquid = ConfigurationEngine.setInt(
                List.of(
                        "Sets the distance between structures liquid surfaces such as oceans.",
                        "Shorter distances between structures will result in more structures overall."),
                fileConfiguration, "distanceLiquid", 65);
        distanceDungeon = ConfigurationEngine.setInt(
                List.of(
                        "Sets the distance between dungeons.",
                        "Shorter distances between dungeons will result in more dungeons overall."
                ),
                fileConfiguration, "distanceDungeonV2", 80);

        // Initialize the maximum offsets from configuration
        maxOffsetSurface = ConfigurationEngine.setInt(
                List.of(
                        "Used to tweak the randomization of the distance between structures in the surface of a world.",
                        "Smaller values will result in structures being more on a grid, and larger values will result in them being less predictably placed."),
                fileConfiguration, "maxOffsetSurface", 5);
        maxOffsetShallow = ConfigurationEngine.setInt(
                List.of(
                        "Used to tweak the randomization of the distance between structures in the shallow underworld of a world.",
                        "Smaller values will result in structures being more on a grid, and larger values will result in them being less predictably placed."),
                fileConfiguration, "maxOffsetShallow", 5);
        maxOffsetDeep = ConfigurationEngine.setInt(
                List.of(
                        "Used to tweak the randomization of the distance between structures in the deep underground of a world.",
                        "Smaller values will result in structures being more on a grid, and larger values will result in them being less predictably placed."),
                fileConfiguration, "maxOffsetDeep", 5);
        maxOffsetSky = ConfigurationEngine.setInt(
                List.of(
                        "Used to tweak the randomization of the distance between structures in the sky.",
                        "Smaller values will result in structures being more on a grid, and larger values will result in them being less predictably placed."),
                fileConfiguration, "maxOffsetSky", 5);
        maxOffsetLiquid = ConfigurationEngine.setInt(
                List.of(
                        "Used to tweak the randomization of the distance between structures on oceans.",
                        "Smaller values will result in structures being more on a grid, and larger values will result in them being less predictably placed."),
                fileConfiguration, "maxOffsetLiquid", 5);
        maxOffsetDungeon = ConfigurationEngine.setInt(
                List.of(
                        "Used to tweak the randomization of the distance between dungeons.",
                        "Smaller values will result in dungeons being more on a grid, and larger values will result in them being less predictably placed."),
                fileConfiguration, "maxOffsetDungeonV2", 18);

        // Initialize mob tracking configuration
        mobTrackingEnabled = ConfigurationEngine.setBoolean(
                List.of(
                        "Enable mob tracking and respawn system for structures.",
                        "When enabled, mobs spawned in structures will be tracked and respawned when players approach.",
                        "Structures will be marked as 'cleared' when all mobs are killed."),
                fileConfiguration, "mobTracking.enabled", true);

        mobRespawnTriggerRadius = ConfigurationEngine.setInt(
                List.of(
                        "Radius (in blocks) within which a player must be to trigger mob respawning.",
                        "Uses spherical distance from structure center."),
                fileConfiguration, "mobTracking.respawnTriggerRadius", 30);

        proximityCheckInterval = ConfigurationEngine.setInt(
                List.of(
                        "Interval in ticks to check player proximity to structures.",
                        "20 ticks = 1 second. Higher values reduce server load but delay respawn detection."),
                fileConfiguration, "mobTracking.proximityCheckInterval", 40);

        structureClearedNotifyRadius = ConfigurationEngine.setInt(
                List.of(
                        "Radius (in blocks) to notify players when a structure is cleared.",
                        "Players within this radius will receive the cleared message."),
                fileConfiguration, "mobTracking.clearedNotifyRadius", 50);

        structureClearedMessage = ConfigurationEngine.setString(
                List.of(
                        "Message sent to players when a structure is cleared.",
                        "Placeholders: {structure} = schematic name, {player} = player who killed the last mob"),
                fileConfiguration, "mobTracking.clearedMessage", "&a&lCongratulations! &e{player} &ahas cleared &6{structure}&a!");

        structureClearedCommands = ConfigurationEngine.setList(
                List.of(
                        "Commands to execute when a structure is cleared.",
                        "Commands are executed for each player within the notify radius.",
                        "Placeholders: {player} = player name, {killer} = killer name, {structure} = schematic name",
                        "{world} = world name, {x} {y} {z} = structure center coords",
                        "Example: 'execute in {world} run summon firework_rocket {x} {y} {z}' for multi-world support",
                        "Example: 'give {player} diamond 1' will give the player a diamond",
                        "Use /bs commandtest to test your commands"),
                fileConfiguration, "mobTracking.clearedCommands",
                List.of("execute in {world} run summon firework_rocket {x} {y} {z} {Life:100,LifeTime:60,FireworksItem:{id:firework_rocket,components:{fireworks:{flight_duration:10,explosions:[{shape:large_ball,colors:[I;16701501,8439583,11546150]}]}}}}"));

        // Terra/FAWE compatibility settings
        structureScanDelayTicks = ConfigurationEngine.setInt(
                List.of(
                        "Delay in ticks before scanning a new chunk for structure placement.",
                        "Increase this value if using Terra or other custom world generators with FAWE.",
                        "Default: 2 (minimal delay). Recommended for Terra + FAWE: 40-60 ticks.",
                        "20 ticks = 1 second."),
                fileConfiguration, "terraCompatibility.structureScanDelayTicks", 2);

        structureScanMaxRetries = ConfigurationEngine.setInt(
                List.of(
                        "Maximum number of retry attempts if a chunk is not fully generated.",
                        "Each retry waits structureScanDelayTicks before trying again.",
                        "Set to 0 to disable retries."),
                fileConfiguration, "terraCompatibility.structureScanMaxRetries", 3);

        terraCompatibilityMode = ConfigurationEngine.setBoolean(
                List.of(
                        "Enable enhanced compatibility mode for Terra and other async world generators.",
                        "When enabled, uses longer delays and additional chunk validation.",
                        "Set to true if using Terra, Iris, or similar custom world generators with FAWE."),
                fileConfiguration, "terraCompatibility.enabled", false);

        validateChunkBeforePaste = ConfigurationEngine.setBoolean(
                List.of(
                        "Validate that all chunks required for a structure are fully generated before pasting.",
                        "Prevents structures from being placed on incomplete terrain.",
                        "Disable only if experiencing performance issues."),
                fileConfiguration, "terraCompatibility.validateChunkBeforePaste", true);

        // Structure queue settings
        structureQueueEnabled = ConfigurationEngine.setBoolean(
                List.of(
                        "Enable structure wait queue for Terra/FAWE compatibility.",
                        "When enabled, structures that cannot be placed due to ungenerated chunks",
                        "will be queued and placed once the chunks are ready.",
                        "When disabled, structures are skipped (original behavior)."),
                fileConfiguration, "terraCompatibility.structureQueueEnabled", true);

        structureQueueMaxSize = ConfigurationEngine.setInt(
                List.of(
                        "Maximum number of structures that can wait in the queue.",
                        "Prevents memory issues during rapid world generation.",
                        "Structures beyond this limit will be discarded."),
                fileConfiguration, "terraCompatibility.structureQueueMaxSize", 100);

        structureQueueTimeoutSeconds = ConfigurationEngine.setInt(
                List.of(
                        "Maximum time in seconds a structure can wait in the queue.",
                        "Structures that exceed this timeout will be discarded with a warning.",
                        "Increase if using slow storage or heavy world generation."),
                fileConfiguration, "terraCompatibility.structureQueueTimeoutSeconds", 60);

        ConfigurationEngine.fileSaverOnlyDefaults(fileConfiguration, file);
    }
}