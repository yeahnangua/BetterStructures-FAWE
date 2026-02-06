package com.magmaguy.betterstructures.modules;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.api.ChestFillEvent;
import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.betterstructures.chests.ChestContents;
import com.magmaguy.betterstructures.config.modulegenerators.ModuleGeneratorsConfigFields;
import com.magmaguy.betterstructures.config.generators.GeneratorConfigFields;
import com.magmaguy.betterstructures.config.treasures.TreasureConfig;
import com.magmaguy.betterstructures.config.treasures.TreasureConfigFields;
import com.magmaguy.betterstructures.structurelocation.StructureLocationManager;
import com.magmaguy.betterstructures.util.WorldEditUtils;
import com.magmaguy.magmacore.util.Logger;
import com.magmaguy.magmacore.util.SpigotMessage;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ModulePasting {
    private final List<InterpretedSign> interpretedSigns = new ArrayList<>();
    private final List<ChestPlacement> chestsToPlace = new ArrayList<>();
    private final List<EntitySpawn> entitiesToSpawn = new ArrayList<>();
    private final String spawnPoolSuffix;
    private final Location startLocation;
    private final boolean createModularWorld;
    private final World world;
    private final File worldFolder;
    private final ModuleGeneratorsConfigFields moduleGeneratorsConfigFields;
    private ModularWorld modularWorld;

    public ModulePasting(World world, File worldFolder, Deque<WFCNode> WFCNodeDeque, String spawnPoolSuffix, Location startLocation, ModuleGeneratorsConfigFields moduleGeneratorsConfigFields) {
        this.spawnPoolSuffix = spawnPoolSuffix;
        this.startLocation = startLocation;
        this.world = world;
        this.worldFolder = worldFolder;
        this.moduleGeneratorsConfigFields = moduleGeneratorsConfigFields;

        // Check debug mode and modular world creation settings from first node
        WFCNode firstNode = WFCNodeDeque.peek();
        this.createModularWorld = firstNode != null && firstNode.getWfcGenerator() != null &&
                firstNode.getWfcGenerator().getModuleGeneratorsConfigFields().isWorldGeneration();

        batchPaste(WFCNodeDeque, interpretedSigns);

        createModularWorld(world, worldFolder);

        // Send notification to players
        if (DefaultConfig.isNewBuildingWarn()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("betterstructures.warn")) {
                    player.spigot().sendMessage(
                            SpigotMessage.commandHoverMessage(
                                    "[BetterStructures] 新的地牢开始生成！请勿关闭服务器。点击传送。执行 \"/betterstructures silent\" 停止警告！",
                                    "点击传送到 " + startLocation.getWorld().getName() + ", " +
                                            startLocation.getBlockX() + ", " + startLocation.getBlockY() + ", " + startLocation.getBlockZ(),
                                    "/betterstructures teleport " + startLocation.getWorld().getName() + " " +
                                            startLocation.getBlockX() + " " + startLocation.getBlockY() + " " + startLocation.getBlockZ())
                    );
                }
            }
        }

        // Record dungeon location to file
        StructureLocationManager.getInstance().recordStructure(
                startLocation,
                moduleGeneratorsConfigFields.getFilename(),
                GeneratorConfigFields.StructureType.DUNGEON
        );
    }

    private static boolean isNbtRichMaterial(Material m) {
        if (m == Material.CHEST || m == Material.TRAPPED_CHEST) return false;
        if (m.name().endsWith("_SIGN") || m.name().endsWith("_WALL_SIGN") || m.name().endsWith("_HANGING_SIGN"))
            return false;

        return switch (m) {
            case SPAWNER,
                 DISPENSER, DROPPER, HOPPER,
                 BEACON, LECTERN, JUKEBOX,
                 COMMAND_BLOCK, REPEATING_COMMAND_BLOCK, CHAIN_COMMAND_BLOCK,
                 PLAYER_HEAD, PLAYER_WALL_HEAD,
                 SCULK_CATALYST, SCULK_SHRIEKER -> true;
            default -> false;
        };
    }

    public static void paste(Clipboard clipboard, Location location, Integer rotation) {
        if (rotation == null) {
            return;
        }

        // Transform the clipboard using the same approach as batch paste
        AffineTransform transform = new AffineTransform().rotateY(normalizeRotation(rotation));
        Clipboard transformedClipboard;
        try {
            transformedClipboard = clipboard.transform(transform);
        } catch (WorldEditException e) {
            Logger.warn("变换剪贴板失败: " + e.getMessage());
            throw new RuntimeException(e);
        }

        // Get dimensions and calculate proper center
        BlockVector3 minPoint = transformedClipboard.getMinimumPoint();

        World world = location.getWorld();
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        // Create edit session for actual placement
        com.sk89q.worldedit.world.World adaptedWorld = BukkitAdapter.adapt(world);

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(adaptedWorld)) {
            editSession.setTrackingHistory(false);
            editSession.setSideEffectApplier(SideEffectSet.none());

            // Process each block using calculated center point as reference
            transformedClipboard.getRegion().forEach(blockPos -> {
                try {
                    BaseBlock baseBlock = transformedClipboard.getFullBlock(blockPos);

                    // Skip air blocks
                    if (baseBlock.getBlockType().getMaterial().isAir()) return;

                    // Calculate world coordinates relative to center point
                    int worldX = baseX + (blockPos.x() - minPoint.x());
                    int worldY = baseY + (blockPos.y() - minPoint.y());
                    int worldZ = baseZ + (blockPos.z() - minPoint.z());

                    // Place the block
                    BlockVector3 worldPos = BlockVector3.at(worldX, worldY, worldZ);
                    editSession.setBlock(worldPos, baseBlock);

                } catch (WorldEditException e) {
                    Logger.warn("放置方块失败 " + blockPos + ": " + e.getMessage());
                }
            });

            pasteArmorStands(transformedClipboard, location, rotation);

        } catch (Exception e) {
            Logger.warn("粘贴建筑失败: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static int normalizeRotation(int rotation) {
        return (360 - rotation) % 360;
    }

    public static void pasteArmorStands(Clipboard clipboard, Location location, Integer rotation) {
        if (rotation == null) rotation = 0;

        AffineTransform transform = new AffineTransform().rotateY(normalizeRotation(rotation));
        Clipboard transformedClipboard;
        try {
            transformedClipboard = clipboard.transform(transform);
        } catch (WorldEditException e) {
            Logger.warn("变换实体剪贴板失败: " + e.getMessage());
            return;
        }

        WorldEditUtils.pasteArmorStandsOnlyFromTransformed(transformedClipboard, location);
    }

    private List<Pasteable> generatePasteMeList(Clipboard clipboard,
                                                Location worldPasteOriginLocation,
                                                Integer rotation,
                                                List<InterpretedSign> interpretedSigns,
                                                List<BedrockCandidate> bedrockCandidates,
                                                List<NbtPlacement> nbtToPlace) {
        List<Pasteable> pasteableList = new ArrayList<>();

        // Apply rotation transformation
        AffineTransform transform = new AffineTransform().rotateY(normalizeRotation(rotation));
        Clipboard transformedClipboard;
        try {
            transformedClipboard = clipboard.transform(transform);
        } catch (WorldEditException e) {
            throw new RuntimeException(e);
        }

        // Get the minimum point of the transformed clipboard to use as reference
        BlockVector3 minPoint = transformedClipboard.getMinimumPoint();

        World world = worldPasteOriginLocation.getWorld();
        int baseX = worldPasteOriginLocation.getBlockX();
        int baseY = worldPasteOriginLocation.getBlockY();
        int baseZ = worldPasteOriginLocation.getBlockZ();

        // Process each block in the transformed clipboard
        transformedClipboard.getRegion().forEach(blockPos -> {
            BaseBlock baseBlock = transformedClipboard.getFullBlock(blockPos);
            BlockData blockData = Bukkit.createBlockData(baseBlock.toImmutableState().getAsString());

            // Skip barriers
            if (blockData.getMaterial().equals(Material.BARRIER)) return;

            // Calculate world coordinates relative to the minimum point
            int worldX = baseX + (blockPos.x() - minPoint.x());
            int worldY = baseY + (blockPos.y() - minPoint.y());
            int worldZ = baseZ + (blockPos.z() - minPoint.z());

            Location pasteLocation = new Location(world, worldX, worldY, worldZ);

            // Handle signs - collect instructions then turn into AIR
            if (blockData.getMaterial().toString().toLowerCase().contains("sign")) {
                List<String> lines = getLines(baseBlock);
                interpretedSigns.add(new InterpretedSign(pasteLocation, lines));

                // Parse sign content for special markers
                for (String line : lines) {
                    if (line.contains("[spawn]") && lines.size() > 1) {
                        try {
                            EntityType entityType = EntityType.valueOf(lines.get(1).toUpperCase());
                            entitiesToSpawn.add(new EntitySpawn(pasteLocation, entityType));
                        } catch (Exception e) {
                            Logger.warn("告示牌中的实体类型无效: " + lines.get(1));
                        }
                    } else if (line.contains("[chest]")) {
                        chestsToPlace.add(new ChestPlacement(pasteLocation, Material.CHEST, rotation));
                    } else if (line.contains("[trapped_chest]")) {
                        chestsToPlace.add(new ChestPlacement(pasteLocation, Material.TRAPPED_CHEST, rotation));
                    }
                }

                // Replace sign with air in the paste list so it won't be deferred as NBT-rich
                blockData = Material.AIR.createBlockData();
            }

            // Collect bedrock positions for deferred async solidity check
            if (blockData.getMaterial().equals(Material.BEDROCK)) {
                bedrockCandidates.add(new BedrockCandidate(pasteLocation));
                return; // Deferred to async EditSession
            }

            // Defer complex NBT blocks (dispensers, spawners, etc.) — handled via BaseBlock in async EditSession
            if (isNbtRichMaterial(blockData.getMaterial())) {
                nbtToPlace.add(new NbtPlacement(pasteLocation, baseBlock));
                return;
            }

            // Normal placement path
            pasteableList.add(new Pasteable(pasteLocation, blockData, baseBlock));
        });

        return pasteableList;
    }

    private List<String> getLines(BaseBlock baseBlock) {
        List<String> strings = new ArrayList<>();
        for (String line : WorldEditUtils.getLines(baseBlock)) {
            if (line != null && !line.isBlank() && line.contains("[pool:"))
                strings.add(line.replace("]", spawnPoolSuffix + "]"));
            else strings.add(line);
        }
        return strings;
    }

    public List<InterpretedSign> batchPaste(Deque<WFCNode> WFCNodeDeque, List<InterpretedSign> interpretedSigns) {
        List<Pasteable> pasteableList = new ArrayList<>();
        List<BedrockCandidate> bedrockCandidates = new ArrayList<>();
        List<NbtPlacement> nbtToPlace = new ArrayList<>();

        // Collect entity paste info while processing blocks
        List<EntityPasteInfo> entityPasteInfos = new ArrayList<>();

        while (!WFCNodeDeque.isEmpty()) {
            WFCNode WFCNode = WFCNodeDeque.poll();
            if (WFCNode == null || WFCNode.getModulesContainer() == null) continue;
            Clipboard clipboard = WFCNode.getModulesContainer().getClipboard();
            if (clipboard == null) continue;

            // Process blocks
            pasteableList.addAll(generatePasteMeList(clipboard, WFCNode.getRealLocation(startLocation),
                    WFCNode.getModulesContainer().getRotation(), interpretedSigns, bedrockCandidates, nbtToPlace));

            // Store entity paste info for later - WITH TRANSFORMED CLIPBOARD
            AffineTransform transform = new AffineTransform().rotateY(normalizeRotation(WFCNode.getModulesContainer().getRotation()));
            try {
                Clipboard transformedClipboard = clipboard.transform(transform);
                entityPasteInfos.add(new EntityPasteInfo(transformedClipboard, WFCNode.getRealLocation(startLocation),
                        WFCNode.getModulesContainer().getRotation()));
            } catch (WorldEditException e) {
                Logger.warn("变换实体剪贴板失败: " + e.getMessage());
            }
        }

        // Switch to async thread for FAWE EditSession paste
        final List<Pasteable> finalPasteableList = pasteableList;
        final List<BedrockCandidate> finalBedrockCandidates = bedrockCandidates;
        final List<NbtPlacement> finalNbtToPlace = nbtToPlace;

        Bukkit.getScheduler().runTaskAsynchronously(MetadataHandler.PLUGIN, () -> {
            try {
                com.sk89q.worldedit.world.World adaptedWorld = BukkitAdapter.adapt(world);

                try (EditSession editSession = WorldEdit.getInstance().newEditSession(adaptedWorld)) {
                    editSession.setTrackingHistory(false);
                    editSession.setSideEffectApplier(SideEffectSet.none());

                    // Place normal blocks via BaseBlock (carries any block state data)
                    for (Pasteable pasteable : finalPasteableList) {
                        BlockVector3 pos = BlockVector3.at(
                                pasteable.location.getBlockX(),
                                pasteable.location.getBlockY(),
                                pasteable.location.getBlockZ());
                        try {
                            editSession.setBlock(pos, pasteable.baseBlock);
                        } catch (WorldEditException e) {
                            Logger.warn("设置方块失败 " + pasteable.location + ": " + e.getMessage());
                        }
                    }

                    // Place NBT-rich blocks (spawners, dispensers, etc.) via BaseBlock which carries NBT
                    for (NbtPlacement np : finalNbtToPlace) {
                        BlockVector3 pos = BlockVector3.at(
                                np.location().getBlockX(),
                                np.location().getBlockY(),
                                np.location().getBlockZ());
                        try {
                            editSession.setBlock(pos, np.baseBlock());
                        } catch (WorldEditException e) {
                            Logger.warn("设置NBT方块失败 " + np.location() + ": " + e.getMessage());
                        }
                    }

                    // Handle bedrock candidates: check solidity async via FAWE, replace non-solid with stone
                    for (BedrockCandidate bc : finalBedrockCandidates) {
                        BlockVector3 pos = BlockVector3.at(
                                bc.location().getBlockX(),
                                bc.location().getBlockY(),
                                bc.location().getBlockZ());
                        try {
                            if (!editSession.getBlock(pos).getBlockType().getMaterial().isSolid()) {
                                editSession.setBlock(pos, BukkitAdapter.adapt(Material.STONE.createBlockData()));
                            }
                        } catch (WorldEditException e) {
                            Logger.warn("处理bedrock候选方块失败 " + bc.location() + ": " + e.getMessage());
                        }
                    }
                } // EditSession auto-closes and flushes

            } catch (Exception e) {
                Logger.warn("FAWE 异步地牢粘贴失败: " + e.getMessage());
                e.printStackTrace();
            }

            // Back to main thread for post-paste processing (entities, chests, etc.)
            Bukkit.getScheduler().runTask(MetadataHandler.PLUGIN, () -> {
                postPasteProcessing(entityPasteInfos);
            });
        });

        return new ArrayList<>();
    }

    private void postPasteProcessing(List<EntityPasteInfo> entityPasteInfos) {
        if (createModularWorld) {
            createModularWorld(world, worldFolder);
            modularWorld.spawnOtherEntities();
        }

        // Paste entities from schematics (armor stands, etc.)
        pasteArmorStandsForBatch(entityPasteInfos);

        for (ChestPlacement chestPlacement : chestsToPlace) {
            Block block = chestPlacement.location.getBlock();
            block.setBlockData(chestPlacement.material.createBlockData(), false);

            if (block.getBlockData() instanceof Chest chest) {
                block.setBlockData(chest, false);

                String treasureFilename = moduleGeneratorsConfigFields.getTreasureFile();
                TreasureConfigFields treasureConfigFields = TreasureConfig.getConfigFields(treasureFilename);
                if (treasureConfigFields != null) {
                    ChestContents chestContents = new ChestContents(treasureConfigFields);
                    Container container = (Container) block.getState();
                    chestContents.rollChestContents(container);
                    ChestFillEvent chestFillEvent = new ChestFillEvent(container, treasureFilename);
                    Bukkit.getServer().getPluginManager().callEvent(chestFillEvent);
                    if (!chestFillEvent.isCancelled())
                        container.update(true);
                }
            }
        }

        // Spawn entities last
        for (EntitySpawn entitySpawn : entitiesToSpawn) {
            try {
                LivingEntity entity = (LivingEntity) world.spawnEntity(entitySpawn.location, entitySpawn.entityType);
                entity.setRemoveWhenFarAway(false);
                entity.setPersistent(true);
            } catch (Exception e) {
                Logger.warn("生成实体失败，类型 " + entitySpawn.entityType + " at " + entitySpawn.location);
            }
        }
    }

    // Helper method to paste entities for all collected clipboards
    private void pasteArmorStandsForBatch(List<EntityPasteInfo> entityPasteInfos) {
        for (EntityPasteInfo info : entityPasteInfos) {
            try {
                WorldEditUtils.pasteArmorStandsOnlyFromTransformed(info.clipboard, info.location);
            } catch (Exception e) {
                Logger.warn("批量粘贴实体失败 " + info.location + ": " + e.getMessage());
            }
        }
    }

    private void createModularWorld(World world, File worldFolder) {
        modularWorld = new ModularWorld(world, worldFolder, interpretedSigns);
    }

    private record BedrockCandidate(Location location) {
    }

    private record NbtPlacement(Location location, BaseBlock baseBlock) {
    }

    // Record to hold entity paste information - now with transformed clipboard
    private record EntityPasteInfo(Clipboard clipboard, Location location, Integer rotation) {
    }

    private record ChestPlacement(Location location, Material material, Integer rotation) {
    }

    private record EntitySpawn(Location location, EntityType entityType) {
    }

    public record InterpretedSign(Location location, List<String> text) {
    }

    private record Pasteable(Location location, BlockData blockData, BaseBlock baseBlock) {
    }
}
