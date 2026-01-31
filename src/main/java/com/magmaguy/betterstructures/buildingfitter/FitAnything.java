package com.magmaguy.betterstructures.buildingfitter;

import com.magmaguy.betterstructures.api.BuildPlaceEvent;
import com.magmaguy.betterstructures.api.ChestFillEvent;
import com.magmaguy.betterstructures.buildingfitter.util.FitUndergroundDeepBuilding;
import com.magmaguy.betterstructures.buildingfitter.util.LocationProjector;
import com.magmaguy.betterstructures.buildingfitter.util.SchematicPicker;
import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.betterstructures.config.generators.GeneratorConfigFields;
import com.magmaguy.betterstructures.mobtracking.MobSpawnConfig;
import com.magmaguy.betterstructures.mobtracking.MobTrackingManager;
import com.magmaguy.betterstructures.schematics.SchematicContainer;
import com.magmaguy.betterstructures.structurelocation.StructureLocationData;
import com.magmaguy.betterstructures.structurelocation.StructureLocationManager;
import com.magmaguy.betterstructures.thirdparty.EliteMobs;
import com.magmaguy.betterstructures.thirdparty.MythicMobs;
import com.magmaguy.betterstructures.thirdparty.WorldGuard;
import com.magmaguy.betterstructures.util.ChunkValidationUtil;
import com.magmaguy.betterstructures.buildingfitter.PendingStructureManager;
import com.magmaguy.betterstructures.util.SurfaceMaterials;
import com.magmaguy.betterstructures.util.WorldEditUtils;
import com.magmaguy.betterstructures.worldedit.Schematic;
import com.magmaguy.magmacore.util.Logger;
import com.magmaguy.magmacore.util.SpigotMessage;
import com.magmaguy.magmacore.util.VersionChecker;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class FitAnything {
    public static boolean worldGuardWarn = false;
    protected final int searchRadius = 1;
    protected final int scanStep = 3;
    private final HashMap<Material, Integer> undergroundPedestalMaterials = new HashMap<>();
    private final HashMap<Material, Integer> surfacePedestalMaterials = new HashMap<>();
    @Getter
    protected SchematicContainer schematicContainer;
    protected double startingScore = 100;
    @Getter
    protected Clipboard schematicClipboard = null;
    @Getter
    protected Vector schematicOffset;
    protected int verticalOffset = 0;
    //At 10% it is assumed a fit is so bad it's better just to skip
    protected double highestScore = 10;
    @Getter
    protected Location location = null;
    protected GeneratorConfigFields.StructureType structureType;
    private Material pedestalMaterial = null;

    public FitAnything(SchematicContainer schematicContainer) {
        this.schematicContainer = schematicContainer;
        this.verticalOffset = schematicContainer.getClipboard().getMinimumPoint().y() - schematicContainer.getClipboard().getOrigin().y();
    }

    public FitAnything() {
    }

    public static void commandBasedCreation(Chunk chunk, GeneratorConfigFields.StructureType structureType, SchematicContainer container) {
        switch (structureType) {
            case SKY:
                new FitAirBuilding(chunk, container);
                break;
            case SURFACE:
                new FitSurfaceBuilding(chunk, container);
                break;
            case LIQUID_SURFACE:
                new FitLiquidBuilding(chunk, container);
                break;
            case UNDERGROUND_DEEP:
                FitUndergroundDeepBuilding.fit(chunk, container);
                break;
            case UNDERGROUND_SHALLOW:
                FitUndergroundShallowBuilding.fit(chunk, container);
                break;
            default:
        }
    }

    protected void randomizeSchematicContainer(Location location, GeneratorConfigFields.StructureType structureType) {
        if (schematicClipboard != null) return;
        schematicContainer = SchematicPicker.pick(location, structureType);
        if (schematicContainer != null) {
            schematicClipboard = schematicContainer.getClipboard();
            verticalOffset = schematicContainer.getClipboard().getMinimumPoint().y() - schematicContainer.getClipboard().getOrigin().y();
        }
    }

    /**
     * Skips chunk validation — used by PendingStructureManager when chunks are already confirmed ready.
     */
    public void pasteBypassValidation(Location location) {
        this.skipChunkValidation = true;
        paste(location);
        this.skipChunkValidation = false;
    }

    private boolean skipChunkValidation = false;

    protected void paste(Location location) {
        BuildPlaceEvent buildPlaceEvent = new BuildPlaceEvent(this);
        Bukkit.getServer().getPluginManager().callEvent(buildPlaceEvent);
        if (buildPlaceEvent.isCancelled()) return;

        // Validate chunks before pasting (Terra/FAWE compatibility)
        if (!skipChunkValidation && DefaultConfig.isValidateChunkBeforePaste() && schematicClipboard != null) {
            int width = schematicClipboard.getDimensions().x();
            int depth = schematicClipboard.getDimensions().z();
            Location pasteLocation = location.clone().add(schematicOffset);

            if (!ChunkValidationUtil.areChunksReadyForStructure(
                    location.getWorld(),
                    pasteLocation.getBlockX(),
                    pasteLocation.getBlockZ(),
                    pasteLocation.getBlockX() + width,
                    pasteLocation.getBlockZ() + depth)) {

                // Queue structure instead of skipping (Terra/FAWE async compatibility)
                if (DefaultConfig.isStructureQueueEnabled()) {
                    boolean queued = PendingStructureManager.getInstance()
                            .queueStructure(this, location, width, depth);
                    if (queued) {
                        Logger.debug("Queued structure for deferred paste at " +
                                location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                                " - waiting for chunks to generate");
                    }
                    return;
                } else {
                    // Original behavior: skip when queue disabled
                    Logger.debug("Skipping structure paste at " +
                            location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                            " - required chunks not fully generated (queue disabled)");
                    return;
                }
            }
        }

        FitAnything fitAnything = this;

        // Set pedestal material before the paste so bedrock blocks get replaced correctly
        assignPedestalMaterial(location);
        if (pedestalMaterial == null)
            switch (location.getWorld().getEnvironment()) {
                case NETHER:
                    pedestalMaterial = Material.NETHERRACK;
                    break;
                case THE_END:
                    pedestalMaterial = Material.END_STONE;
                    break;
                default:
                    pedestalMaterial = Material.STONE;
            }

        // Create a function to provide pedestal material
        Function<Boolean, Material> pedestalMaterialProvider = this::getPedestalMaterial;

        // Paste the schematic with the moved logic
        Schematic.pasteSchematic(
                schematicClipboard,
                location,
                schematicOffset,
                pedestalMaterialProvider,
                onPasteComplete(fitAnything, location)
        );
    }

    private BukkitRunnable onPasteComplete(FitAnything fitAnything, Location location) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (DefaultConfig.isNewBuildingWarn()) {
                    String structureTypeString = fitAnything.structureType.toString().toLowerCase(Locale.ROOT).replace("_", " ");
                    for (Player player : Bukkit.getOnlinePlayers())
                        if (player.hasPermission("betterstructures.warn"))
                            player.spigot().sendMessage(
                                    SpigotMessage.commandHoverMessage("[BetterStructures] New " + structureTypeString + " building generated! Click to teleport. Do \"/betterstructures silent\" to stop getting warnings!",
                                            "Click to teleport to " + location.getWorld().getName() + ", " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "\n Schem name: " + schematicContainer.getConfigFilename(),
                                            "/betterstructures teleport " + location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ())
                            );
                }

                // Record structure location to file
                StructureLocationManager.getInstance().recordStructure(
                        location,
                        schematicContainer.getConfigFilename(),
                        fitAnything.structureType
                );

                if (!(fitAnything instanceof FitAirBuilding)) {
                    try {
                        addPedestal(location);
                    } catch (Exception exception) {
                        Logger.warn("Failed to correctly assign pedestal material!");
                        exception.printStackTrace();
                    }
                    try {
                        if (fitAnything instanceof FitSurfaceBuilding)
                            clearTrees(location);
                    } catch (Exception exception) {
                        Logger.warn("Failed to correctly clear trees!");
                        exception.printStackTrace();
                    }
                }
                try {
                    fillChests();
                } catch (Exception exception) {
                    Logger.warn("Failed to correctly fill chests!");
                    exception.printStackTrace();
                }
                try {
                    spawnEntities();
                } catch (Exception exception) {
                    Logger.warn("Failed to correctly spawn entities!");
                    exception.printStackTrace();
                }
                try{
                    spawnProps(fitAnything.schematicClipboard);
                } catch (Exception exception) {
                    Logger.warn("Failed to correctly spawn props!");
                    exception.printStackTrace();
                }
            }
        };
    }

    private void spawnProps(Clipboard clipboard) {
        // Don't add schematicOffset here - let pasteArmorStandsOnlyFromTransformed handle the alignment
        WorldEditUtils.pasteArmorStandsOnlyFromTransformed(clipboard, location.clone().add(schematicOffset));
    }

    private void assignPedestalMaterial(Location location) {
        if (this instanceof FitAirBuilding) return;
        pedestalMaterial = schematicContainer.getSchematicConfigField().getPedestalMaterial();
        Location lowestCorner = location.clone().add(schematicOffset);

        int maxSurfaceHeightScan = 20;

        //get underground pedestal blocks
        for (int x = 0; x < schematicClipboard.getDimensions().x(); x++)
            for (int z = 0; z < schematicClipboard.getDimensions().z(); z++)
                for (int y = 0; y < schematicClipboard.getDimensions().y(); y++) {
                    Block groundBlock = lowestCorner.clone().add(new Vector(x, y, z)).getBlock();
                    Block aboveBlock = groundBlock.getRelative(BlockFace.UP);

                    if (aboveBlock.getType().isSolid() && groundBlock.getType().isSolid() && !SurfaceMaterials.ignorable(groundBlock.getType()))
                        undergroundPedestalMaterials.merge(groundBlock.getType(), 1, Integer::sum);
                }

        //get above ground pedestal blocks, if any
        for (int x = 0; x < schematicClipboard.getDimensions().x(); x++)
            for (int z = 0; z < schematicClipboard.getDimensions().z(); z++) {
                boolean scanUp = lowestCorner.clone().add(new Vector(x, schematicClipboard.getDimensions().y(), z)).getBlock().getType().isSolid();
                for (int y = 0; y < maxSurfaceHeightScan; y++) {
                    Block groundBlock = lowestCorner.clone().add(new Vector(x, scanUp ? y : -y, z)).getBlock();
                    Block aboveBlock = groundBlock.getRelative(BlockFace.UP);

                    if (!aboveBlock.getType().isSolid() && groundBlock.getType().isSolid()) {
                        surfacePedestalMaterials.merge(groundBlock.getType(), 1, Integer::sum);
                        break;
                    }
                }
            }
    }

    private Material getPedestalMaterial(boolean isPedestalSurface) {
        if (isPedestalSurface) {
            if (surfacePedestalMaterials.isEmpty()) return pedestalMaterial;
            return getRandomMaterialBasedOnWeight(surfacePedestalMaterials);
        } else {
            if (undergroundPedestalMaterials.isEmpty()) return pedestalMaterial;
            return getRandomMaterialBasedOnWeight(undergroundPedestalMaterials);
        }
    }

    public Material getRandomMaterialBasedOnWeight(HashMap<Material, Integer> weightedMaterials) {
        // Calculate the total weight
        int totalWeight = weightedMaterials.values().stream().mapToInt(Integer::intValue).sum();

        // Generate a random number in the range of 0 (inclusive) to totalWeight (exclusive)
        int randomNumber = ThreadLocalRandom.current().nextInt(totalWeight);

        // Iterate through the materials and pick one based on the random number
        int cumulativeWeight = 0;
        for (Map.Entry<Material, Integer> entry : weightedMaterials.entrySet()) {
            cumulativeWeight += entry.getValue();
            if (randomNumber < cumulativeWeight) {
                return entry.getKey();
            }
        }

        // Fallback return, should not occur if the map is not empty and weights are positive
        throw new IllegalStateException("Weighted random selection failed.");
    }

    private void addPedestal(Location location) {
        if (this instanceof FitAirBuilding || this instanceof FitLiquidBuilding) return;
        Location lowestCorner = location.clone().add(schematicOffset);
        for (int x = 0; x < schematicClipboard.getDimensions().x(); x++)
            for (int z = 0; z < schematicClipboard.getDimensions().z(); z++) {
                Location blockLoc = lowestCorner.clone().add(new Vector(x, 0, z));
                // Skip if chunk not loaded to avoid sync chunk loading
                if (!blockLoc.getWorld().isChunkLoaded(blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4)) {
                    continue;
                }
                //Only add pedestals for areas with a solid floor, some schematics can have rounded air edges to better fit terrain
                Block groundBlock = blockLoc.getBlock();
                if (groundBlock.getType().isAir()) continue;
                for (int y = -1; y > -11; y--) {
                    Block block = lowestCorner.clone().add(new Vector(x, y, z)).getBlock();
                    if (SurfaceMaterials.ignorable(block.getType())) {
                        // Use setBlockData with false to disable physics updates
                        Material pedestalMat = getPedestalMaterial(!block.getRelative(BlockFace.UP).getType().isSolid());
                        block.setBlockData(pedestalMat.createBlockData(), false);
                    } else {
                        //Pedestal only fills until it hits the first solid block
                        break;
                    }
                }
            }
    }

    private void clearTrees(Location location) {
        Location highestCorner = location.clone().add(schematicOffset).add(new Vector(0, schematicClipboard.getDimensions().y() + 1, 0));
        boolean detectedTreeElement = true;
        for (int x = 0; x < schematicClipboard.getDimensions().x(); x++)
            for (int z = 0; z < schematicClipboard.getDimensions().z(); z++) {
                Location blockLoc = highestCorner.clone().add(new Vector(x, 0, z));
                // Skip if chunk not loaded to avoid sync chunk loading
                if (!blockLoc.getWorld().isChunkLoaded(blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4)) {
                    continue;
                }
                for (int y = 0; y < 31; y++) {
                    if (!detectedTreeElement) break;
                    detectedTreeElement = false;
                    Block block = highestCorner.clone().add(new Vector(x, y, z)).getBlock();
                    if (SurfaceMaterials.ignorable(block.getType()) && !block.getType().isAir()) {
                        detectedTreeElement = true;
                        // Use setBlockData with false to disable physics updates
                        block.setBlockData(Material.AIR.createBlockData(), false);
                    }
                }
            }
    }

    private void fillChests() {
        if (schematicContainer.getGeneratorConfigFields().getChestContents() != null)
            for (Vector chestPosition : schematicContainer.getChestLocations()) {
                Location chestLocation = LocationProjector.project(location, schematicOffset, chestPosition);
                if (!(chestLocation.getBlock().getState() instanceof Container container)) {
                    Logger.warn("Expected a container for " + chestLocation.getBlock().getType() + " but didn't get it. Skipping this loot!");
                    continue;
                }

                String treasureFilename;
                if (schematicContainer.getChestContents() != null) {
                    schematicContainer.getChestContents().rollChestContents(container);
                    treasureFilename = schematicContainer.getSchematicConfigField().getTreasureFile();
                } else {
                    schematicContainer.getGeneratorConfigFields().getChestContents().rollChestContents(container);
                    treasureFilename = schematicContainer.getGeneratorConfigFields().getTreasureFilename();
                }

                ChestFillEvent chestFillEvent = new ChestFillEvent(container, treasureFilename);
                Bukkit.getServer().getPluginManager().callEvent(chestFillEvent);
                if (!chestFillEvent.isCancelled()) {
                    container.update(true);

                }
            }
    }

    private void spawnEntities() {
        List<UUID> spawnedMobUUIDs = new ArrayList<>();
        List<MobSpawnConfig> mobSpawnConfigs = new ArrayList<>();

        // Spawn vanilla mobs
        for (Vector entityPosition : schematicContainer.getVanillaSpawns().keySet()) {
            Location signLocation = LocationProjector.project(location, schematicOffset, entityPosition).clone();
            // Skip if chunk not loaded to avoid sync chunk loading with FAWE
            if (!signLocation.getWorld().isChunkLoaded(signLocation.getBlockX() >> 4, signLocation.getBlockZ() >> 4)) {
                continue;
            }
            // Use setBlockData with false to disable physics updates
            signLocation.getBlock().setBlockData(Material.AIR.createBlockData(), false);
            //If mobs spawn in corners they might choke on adjacent walls
            signLocation.add(new Vector(0.5, 0, 0.5));
            EntityType entityType = schematicContainer.getVanillaSpawns().get(entityPosition);
            Entity entity = signLocation.getWorld().spawnEntity(signLocation, entityType);
            entity.setPersistent(true);
            if (entity instanceof LivingEntity) {
                ((LivingEntity) entity).setRemoveWhenFarAway(false);
            }

            if (!VersionChecker.serverVersionOlderThan(21, 0) &&
                    entity.getType().equals(EntityType.END_CRYSTAL)) {
                EnderCrystal enderCrystal = (EnderCrystal) entity;
                enderCrystal.setShowingBottom(false);
            }

            // Track mob for respawning (only LivingEntities)
            // Store the actual spawn location relative to structure origin (includes schematicOffset)
            if (entity instanceof LivingEntity && DefaultConfig.isMobTrackingEnabled()) {
                Vector actualOffset = schematicOffset.clone().add(entityPosition);
                spawnedMobUUIDs.add(entity.getUniqueId());
                mobSpawnConfigs.add(new MobSpawnConfig(
                        MobSpawnConfig.MobType.VANILLA,
                        entityType.name(),
                        actualOffset.getX(),
                        actualOffset.getY(),
                        actualOffset.getZ()
                ));
            }
        }

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

            // Use spawnAndReturn to get the entity
            Entity eliteMob = EliteMobs.spawnAndReturn(eliteLocation, bossFilename);
            if (eliteMob == null) return;

            // Track mob (EliteMobs only for state tracking, not respawning)
            // Store actual offset including schematicOffset
            if (DefaultConfig.isMobTrackingEnabled()) {
                Vector actualOffset = schematicOffset.clone().add(elitePosition);
                spawnedMobUUIDs.add(eliteMob.getUniqueId());
                mobSpawnConfigs.add(new MobSpawnConfig(
                        MobSpawnConfig.MobType.ELITEMOBS,
                        bossFilename,
                        actualOffset.getX(),
                        actualOffset.getY(),
                        actualOffset.getZ()
                ));
            }

            Location lowestCorner = location.clone().add(schematicOffset);
            Location highestCorner = lowestCorner.clone().add(new Vector(schematicClipboard.getRegion().getWidth() - 1, schematicClipboard.getRegion().getHeight(), schematicClipboard.getRegion().getLength() - 1));
            if (DefaultConfig.isProtectEliteMobsRegions() &&
                    Bukkit.getPluginManager().getPlugin("WorldGuard") != null &&
                    Bukkit.getPluginManager().getPlugin("EliteMobs") != null) {
                WorldGuard.Protect(lowestCorner, highestCorner, bossFilename, eliteLocation);
            } else {
                if (!worldGuardWarn) {
                    worldGuardWarn = true;
                    Logger.warn("You are not using WorldGuard, so BetterStructures could not protect a boss arena! Using WorldGuard is recommended to guarantee a fair combat experience.");
                }
            }
        }

        // Spawn MythicMobs
        for (Map.Entry<Vector, String> entry : schematicContainer.getMythicMobsSpawns().entrySet()) {
            Location mobLocation = LocationProjector.project(location, schematicOffset, entry.getKey()).clone();
            // Skip if chunk not loaded to avoid sync chunk loading with FAWE
            if (!mobLocation.getWorld().isChunkLoaded(mobLocation.getBlockX() >> 4, mobLocation.getBlockZ() >> 4)) {
                continue;
            }
            mobLocation.getBlock().setBlockData(Material.AIR.createBlockData(), false);

            // Use spawnAndReturn to get the entity
            Entity mythicMob = MythicMobs.spawnAndReturn(mobLocation, entry.getValue());
            if (mythicMob == null) return;

            // Track mob for respawning
            // Store actual offset including schematicOffset
            if (DefaultConfig.isMobTrackingEnabled()) {
                Vector actualOffset = schematicOffset.clone().add(entry.getKey());
                spawnedMobUUIDs.add(mythicMob.getUniqueId());
                mobSpawnConfigs.add(new MobSpawnConfig(
                        MobSpawnConfig.MobType.MYTHICMOBS,
                        entry.getValue(),
                        actualOffset.getX(),
                        actualOffset.getY(),
                        actualOffset.getZ()
                ));
            }
        }

        // Register mobs with tracking manager
        if (DefaultConfig.isMobTrackingEnabled() && !spawnedMobUUIDs.isEmpty()) {
            // Get or create structure location data
            StructureLocationData structureData = StructureLocationManager.getInstance()
                    .getStructureAt(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());

            if (structureData != null) {
                MobTrackingManager.getInstance().registerStructureMobs(structureData, spawnedMobUUIDs, mobSpawnConfigs);
            }
        }
    }
}
