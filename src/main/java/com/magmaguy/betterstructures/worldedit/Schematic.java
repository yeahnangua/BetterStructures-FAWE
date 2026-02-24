package com.magmaguy.betterstructures.worldedit;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.betterstructures.util.ChunkValidationUtil;
import com.magmaguy.magmacore.util.Logger;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class Schematic {
    private static boolean erroredOnce = false;

    private Schematic() {
    }

    /**
     * Loads a schematic from a file
     *
     * @param schematicFile The schematic file to load
     * @return The loaded clipboard or null if loading failed
     */
    public static Clipboard load(File schematicFile) {
        Clipboard clipboard;

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);

        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            clipboard = reader.read();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (NoSuchElementException e) {
            Logger.warn("从建筑模板获取元素失败 " + schematicFile.getName());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            Logger.warn("加载建筑模板 " + schematicFile.getName() + " 失败！99% 的情况下，这是因为你没有使用与你的 Minecraft 服务器匹配的 WorldEdit 版本。请从这里下载 WorldEdit: https://dev.bukkit.org/projects/worldedit 。你可以将鼠标悬停在下载链接上查看兼容的版本。");
            erroredOnce = true;
            if (!erroredOnce) e.printStackTrace();
            else Logger.warn("隐藏此错误的堆栈跟踪，因为已经打印过一次");
            return null;
        }
        return clipboard;
    }

    /**
     * Pastes a schematic synchronously (used for small schematics like modular world elevators).
     * FAWE runtime already optimizes this automatically.
     *
     * @param clipboard The WorldEdit clipboard containing the schematic
     * @param location  The location to paste at
     */
    public static void paste(Clipboard clipboard, Location location) {
        World world = BukkitAdapter.adapt(location.getWorld());
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(location.getX(), location.getY(), location.getZ()))
                    .build();
            Operations.complete(operation);
        } catch (WorldEditException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculates all chunks required for pasting a schematic.
     * This method does NOT access the world - it only uses clipboard dimensions.
     *
     * @param clipboard The schematic clipboard
     * @param location The paste location
     * @param schematicOffset The schematic offset
     * @return Set of chunk keys (use chunkKey() to decode)
     */
    private static Set<Long> calculateRequiredChunks(Clipboard clipboard, Location location, Vector schematicOffset) {
        Set<Long> chunks = new HashSet<>();
        Location adjusted = location.clone().add(schematicOffset);

        // Calculate bounding box from clipboard dimensions (no world access)
        int minX = adjusted.getBlockX();
        int maxX = minX + clipboard.getDimensions().x();
        int minZ = adjusted.getBlockZ();
        int maxZ = minZ + clipboard.getDimensions().z();

        // Convert to chunk coordinates with 1-chunk padding
        for (int cx = (minX >> 4) - 1; cx <= (maxX >> 4) + 1; cx++) {
            for (int cz = (minZ >> 4) - 1; cz <= (maxZ >> 4) + 1; cz++) {
                chunks.add(chunkKey(cx, cz));
            }
        }
        return chunks;
    }

    /**
     * Pastes a schematic using FAWE async EditSession.
     * Ensures all required chunks are generated BEFORE accessing any world blocks.
     *
     * @param schematicClipboard The clipboard containing the schematic
     * @param location The location to paste at
     * @param schematicOffset The offset of the schematic
     * @param prePasteCallback Callback to run AFTER chunks are ready but BEFORE paste (for pedestal assignment)
     * @param pedestalMaterialProvider Function that provides pedestal material based on whether it's a surface block
     * @param onComplete Callback to run when paste is complete
     */
    public static void pasteSchematic(
            Clipboard schematicClipboard,
            Location location,
            Vector schematicOffset,
            Runnable prePasteCallback,
            Function<Boolean, Material> pedestalMaterialProvider,
            Consumer<Boolean> onComplete) {

        org.bukkit.World world = location.getWorld();
        if (world == null) {
            Logger.warn("无法粘贴: 世界为空");
            if (onComplete != null) onComplete.accept(false);
            return;
        }

        // Step 1: Calculate required chunks WITHOUT accessing world
        Set<Long> requiredChunks = calculateRequiredChunks(schematicClipboard, location, schematicOffset);

        // Step 2: Load all required chunks asynchronously
        List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();
        for (Long key : requiredChunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = key.intValue();
            chunkFutures.add(world.getChunkAtAsync(chunkX, chunkZ, true));
        }

        if (chunkFutures.isEmpty()) {
            // No chunks required (empty schematic?), proceed immediately on main thread
            if (!validateRequiredChunks(world, requiredChunks)) {
                if (onComplete != null) onComplete.accept(false);
                return;
            }
            if (prePasteCallback != null) prePasteCallback.run();
            executeFaweAsyncPaste(schematicClipboard, location, schematicOffset,
                    pedestalMaterialProvider, onComplete, requiredChunks, world);
        } else {
            Logger.debug("Loading " + chunkFutures.size() + " chunks before pasting at " +
                    location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());

            CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        // Step 3: Main thread — run prePasteCallback and add chunk tickets
                        Bukkit.getScheduler().runTask(MetadataHandler.PLUGIN, () -> {
                            if (!validateRequiredChunks(world, requiredChunks)) {
                                if (onComplete != null) onComplete.accept(false);
                                return;
                            }
                            if (prePasteCallback != null) prePasteCallback.run();

                            // Add chunk tickets to keep chunks loaded during async paste
                            for (Long key : requiredChunks) {
                                int chunkX = (int) (key >> 32);
                                int chunkZ = key.intValue();
                                world.addPluginChunkTicket(chunkX, chunkZ, MetadataHandler.PLUGIN);
                            }

                            // Step 4: Switch to async thread for FAWE paste
                            executeFaweAsyncPaste(schematicClipboard, location, schematicOffset,
                                    pedestalMaterialProvider, onComplete, requiredChunks, world);
                        });
                    });
        }
    }

    private static boolean validateRequiredChunks(org.bukkit.World world, Set<Long> requiredChunks) {
        if (!DefaultConfig.isValidateChunkBeforePaste()) {
            return true;
        }

        for (Long key : requiredChunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = key.intValue();
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return false;
            }

            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            if (!ChunkValidationUtil.isChunkFullyGenerated(chunk)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Executes the actual block placement using FAWE EditSession on an async thread.
     */
    private static void executeFaweAsyncPaste(
            Clipboard schematicClipboard,
            Location location,
            Vector schematicOffset,
            Function<Boolean, Material> pedestalMaterialProvider,
            Consumer<Boolean> onComplete,
            Set<Long> requiredChunks,
            org.bukkit.World bukkitWorld) {

        Bukkit.getScheduler().runTaskAsynchronously(MetadataHandler.PLUGIN, () -> {
            boolean success = true;
            try {
                World weWorld = BukkitAdapter.adapt(bukkitWorld);
                Location adjustedLocation = location.clone().add(schematicOffset);

                try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                    editSession.setTrackingHistory(false);
                    editSession.setSideEffectApplier(SideEffectSet.none());

                    for (int x = 0; x < schematicClipboard.getDimensions().x(); x++)
                        for (int y = 0; y < schematicClipboard.getDimensions().y(); y++)
                            for (int z = 0; z < schematicClipboard.getDimensions().z(); z++) {
                                BlockVector3 clipboardPos = BlockVector3.at(
                                        x + schematicClipboard.getMinimumPoint().x(),
                                        y + schematicClipboard.getMinimumPoint().y(),
                                        z + schematicClipboard.getMinimumPoint().z());

                                BaseBlock baseBlock = schematicClipboard.getFullBlock(clipboardPos);
                                Material material = BukkitAdapter.adapt(baseBlock.getBlockType());

                                // Skip barriers
                                if (material == Material.BARRIER) continue;

                                int worldX = adjustedLocation.getBlockX() + x;
                                int worldY = adjustedLocation.getBlockY() + y;
                                int worldZ = adjustedLocation.getBlockZ() + z;
                                BlockVector3 worldPos = BlockVector3.at(worldX, worldY, worldZ);

                                if (material == Material.BEDROCK) {
                                    // Check if existing block is solid using FAWE async-safe getBlock
                                    if (editSession.getBlock(worldPos).getBlockType().getMaterial().isSolid()) continue;

                                    // Determine if this is a surface block (block above is not solid in schematic)
                                    boolean isGround = !BukkitAdapter.adapt(schematicClipboard.getBlock(
                                            BlockVector3.at(clipboardPos.x(),
                                                    clipboardPos.y() + 1,
                                                    clipboardPos.z())).getBlockType()).isSolid();

                                    Material pedestalMaterial = pedestalMaterialProvider.apply(isGround);
                                    editSession.setBlock(worldPos, BukkitAdapter.adapt(pedestalMaterial.createBlockData()));
                                } else {
                                    // All blocks (including NBT-rich) go through BaseBlock which carries NBT
                                    editSession.setBlock(worldPos, baseBlock);
                                }
                            }
                } // EditSession auto-closes and flushes

            } catch (Exception e) {
                success = false;
                Logger.warn("FAWE 异步粘贴失败: " + e.getMessage());
                e.printStackTrace();
            }

            // Step 5: Back to main thread — release chunk tickets and run onComplete
            boolean finalSuccess = success;
            Bukkit.getScheduler().runTask(MetadataHandler.PLUGIN, () -> {
                for (Long key : requiredChunks) {
                    int chunkX = (int) (key >> 32);
                    int chunkZ = key.intValue();
                    bukkitWorld.removePluginChunkTicket(chunkX, chunkZ, MetadataHandler.PLUGIN);
                }
                if (onComplete != null) onComplete.accept(finalSuccess);
            });
        });
    }

    /**
     * Creates a unique key for chunk coordinates.
     */
    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
