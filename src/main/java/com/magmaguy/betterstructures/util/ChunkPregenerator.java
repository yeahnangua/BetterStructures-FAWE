package com.magmaguy.betterstructures.util;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.api.BuildPlaceEvent;
import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.magmacore.util.Logger;
import com.magmaguy.magmacore.util.WorkloadRunnable;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class ChunkPregenerator implements Listener {
    public static HashSet<ChunkPregenerator> activePregenerators = new HashSet<>();
    
    @Getter
    private final World world;
    @Getter
    private final Location center;
    @Getter
    private final String shape;
    private final boolean setWorldBorder;
    private final double tickUsage;
    private final int maxRadiusBlocks;
    private final int maxRadiusChunks;
    private int actualMaxRadiusChunks = 0;
    private final Set<String> generatedChunks = new HashSet<>();
    private final Set<String> loadedChunks = new HashSet<>(); // Track which chunks have been loaded (to avoid double counting)
    private int newlyGeneratedChunks = 0;
    private int chunksLoadedLast30s = 0;
    private int allChunksLoadedLast30s = 0; // Track all chunks (new or existing) loaded in last 30s
    private int structuresGeneratedTotal = 0;
    private int structuresGeneratedLast30s = 0;
    private Integer expectedChunksTotal = null; // Cached calculation
    private BukkitTask statsTask;
    private BukkitTask tpsMonitorTask;
    private BukkitTask currentWorkloadTask;
    private volatile boolean isCancelled = false;
    private volatile boolean isPaused = false;

    public ChunkPregenerator(World world, Location center, String shape, int maxRadiusBlocks, int maxRadiusChunks, boolean setWorldBorder) {
        this.world = world;
        this.center = center;
        this.shape = shape;
        this.maxRadiusBlocks = maxRadiusBlocks;
        this.maxRadiusChunks = maxRadiusChunks;
        this.setWorldBorder = setWorldBorder;
        this.tickUsage = DefaultConfig.getPercentageOfTickUsedForPregeneration();

        // Register as event listener to track actual structure generation
        Bukkit.getPluginManager().registerEvents(this, MetadataHandler.PLUGIN);
    }

    private int centerChunkX;
    private int centerChunkZ;
    private int currentRadius = 0;

    public void start() {
        centerChunkX = center.getBlockX() >> 4;
        centerChunkZ = center.getBlockZ() >> 4;

        Logger.info("开始区块预生成，形状: " + shape + ", center chunk: (" + centerChunkX + ", " + centerChunkZ + "), radius: " + maxRadiusBlocks + " blocks (" + maxRadiusChunks + " chunks)");

        // Register this pregenerator as active
        activePregenerators.add(this);

        // Start stats reporting task (every 30 seconds = 600 ticks)
        statsTask = Bukkit.getScheduler().runTaskTimer(MetadataHandler.PLUGIN, this::reportStats, 600L, 600L);

        // Start TPS monitoring task (every 2 seconds = 40 ticks)
        tpsMonitorTask = Bukkit.getScheduler().runTaskTimer(MetadataHandler.PLUGIN, this::checkTPSAndPause, 40L, 40L);

        // Start with radius 0 (center chunk)
        generateNextLayer();
    }

    private void checkTPSAndPause() {
        if (isCancelled) {
            return;
        }

        double currentTPS = getTPS();
        double pauseThreshold = DefaultConfig.getPregenerationTPSPauseThreshold();
        double resumeThreshold = DefaultConfig.getPregenerationTPSResumeThreshold();
        
        if (currentTPS < pauseThreshold) {
            if (!isPaused) {
                isPaused = true;
                Logger.warn("暂停区块预生成 - TPS 低于 " + pauseThreshold + " (当前: " + String.format("%.2f", currentTPS) + ")");
                // Cancel the current workload
                if (currentWorkloadTask != null) {
                    currentWorkloadTask.cancel();
                    currentWorkloadTask = null;
                }
            }
        } else if (isPaused && currentTPS >= resumeThreshold) {
            isPaused = false;
            Logger.info("恢复区块预生成 - TPS 恢复至 " + String.format("%.2f", currentTPS) + " (高于 " + resumeThreshold + ")");
            // Resume by generating the current layer again
            generateNextLayer();
        }
    }

    private void generateNextLayer() {
        // Check if cancelled before generating next layer
        if (isCancelled) {
            onCancelled();
            return;
        }

        // Don't start new layer if paused
        if (isPaused) {
            return;
        }

        // Check if we've reached the maximum radius
        if (currentRadius > maxRadiusChunks) {
            onComplete();
            return;
        }

        actualMaxRadiusChunks = currentRadius;

        final boolean[] chunksAdded = {false};

        WorkloadRunnable workload = new WorkloadRunnable(tickUsage, () -> {
            // Check if cancelled before continuing
            if (isCancelled) {
                onCancelled();
                return;
            }

            // When this layer completes, generate the next layer if chunks were added
            if (chunksAdded[0]) {
                currentRadius++;
                generateNextLayer();
            } else {
                // No more chunks to generate, we're done
                onComplete();
            }
        });

        if ("SQUARE".equalsIgnoreCase(shape)) {
            chunksAdded[0] = generateSquareLayer(workload, currentRadius);
        } else if ("CIRCLE".equalsIgnoreCase(shape)) {
            chunksAdded[0] = generateCircleLayer(workload, currentRadius);
        } else {
            Logger.warn("无效的形状: " + shape + "。必须为 SQUARE 或 CIRCLE。");
            onComplete();
            return;
        }

        // If no chunks were added to this layer, skip the workload and move to next layer immediately
        if (!chunksAdded[0]) {
            currentRadius++;
            generateNextLayer();
            return;
        }

        // Store the task so we can cancel it if needed
        currentWorkloadTask = workload.runTaskTimer(MetadataHandler.PLUGIN, 0, 1);
    }

    private boolean generateSquareLayer(WorkloadRunnable workload, int radius) {
        boolean chunksAdded = false;
        // Generate chunks in a square pattern at this radius
        // Top and bottom edges
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            // Top edge
            if (addChunkToWorkload(workload, x, centerChunkZ - radius)) {
                chunksAdded = true;
            }
            // Bottom edge
            if (addChunkToWorkload(workload, x, centerChunkZ + radius)) {
                chunksAdded = true;
            }
        }

        // Left and right edges (excluding corners already processed)
        for (int z = centerChunkZ - radius + 1; z < centerChunkZ + radius; z++) {
            // Left edge
            if (addChunkToWorkload(workload, centerChunkX - radius, z)) {
                chunksAdded = true;
            }
            // Right edge
            if (addChunkToWorkload(workload, centerChunkX + radius, z)) {
                chunksAdded = true;
            }
        }
        return chunksAdded;
    }

    private boolean generateCircleLayer(WorkloadRunnable workload, int radius) {
        boolean chunksAdded = false;
        // Generate chunks in a circle pattern at this radius
        int radiusSquared = radius * radius;
        int nextRadiusSquared = (radius + 1) * (radius + 1);

        // Use a bounding box approach for efficiency
        for (int x = centerChunkX - radius - 1; x <= centerChunkX + radius + 1; x++) {
            for (int z = centerChunkZ - radius - 1; z <= centerChunkZ + radius + 1; z++) {
                int dx = x - centerChunkX;
                int dz = z - centerChunkZ;
                int distanceSquared = dx * dx + dz * dz;

                // Include chunks at exactly this radius
                if (distanceSquared >= radiusSquared && distanceSquared < nextRadiusSquared) {
                    if (addChunkToWorkload(workload, x, z)) {
                        chunksAdded = true;
                    }
                }
            }
        }
        return chunksAdded;
    }

    private boolean addChunkToWorkload(WorkloadRunnable workload, int chunkX, int chunkZ) {
        String chunkKey = chunkX + "," + chunkZ;
        if (generatedChunks.contains(chunkKey)) {
            return false; // Already generated or queued
        }

        generatedChunks.add(chunkKey);
        workload.addWorkload(() -> generateChunk(chunkX, chunkZ));
        return true;
    }

    private void generateChunk(int chunkX, int chunkZ) {
        try {
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }
            // Chunk counting is now handled by ChunkLoadEvent listener
        } catch (Exception e) {
            Logger.warn("生成区块失败 (" + chunkX + ", " + chunkZ + "): " + e.getMessage());
        }
    }

    private void onComplete() {
        cleanup();
        Logger.info("区块预生成完成。已处理 " + generatedChunks.size() + " 个区块，新生成 " + newlyGeneratedChunks + " 个区块，最大半径: " + maxRadiusBlocks + " 格 (" + actualMaxRadiusChunks + " 个区块)");

        if (setWorldBorder) {
            setWorldBorder();
        }
    }

    private void onCancelled() {
        cleanup();
        Logger.info("区块预生成已取消。已处理 " + generatedChunks.size() + " 个区块，新生成 " + newlyGeneratedChunks + " 个区块，已达最大半径: " + (actualMaxRadiusChunks * 16) + " 格 (" + actualMaxRadiusChunks + " 个区块)");
    }

    private void cleanup() {
        // Cancel stats task
        if (statsTask != null) {
            statsTask.cancel();
            statsTask = null;
        }

        // Cancel TPS monitor task
        if (tpsMonitorTask != null) {
            tpsMonitorTask.cancel();
            tpsMonitorTask = null;
        }

        // Cancel current workload task
        if (currentWorkloadTask != null) {
            currentWorkloadTask.cancel();
            currentWorkloadTask = null;
        }

        // Unregister event listener
        HandlerList.unregisterAll(this);

        // Remove from active pregenerators
        activePregenerators.remove(this);
    }

    /**
     * Cancels the pregeneration process.
     */
    public void cancel() {
        isCancelled = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBuildPlace(BuildPlaceEvent event) {
        // Only count structures in the world we're pregenerating
        if (event.getFitAnything().getLocation().getWorld().equals(world)) {
            structuresGeneratedTotal++;
            structuresGeneratedLast30s++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        // Only track chunks in the world we're pregenerating
        if (!event.getWorld().equals(world)) {
            return;
        }
        
        String chunkKey = event.getChunk().getX() + "," + event.getChunk().getZ();
        // Only count if this chunk is part of our pregeneration
        if (generatedChunks.contains(chunkKey)) {
            // Only count each chunk once for total and 30s rate
            if (!loadedChunks.contains(chunkKey)) {
                loadedChunks.add(chunkKey);
                allChunksLoadedLast30s++;
            }
            
            // Only count newly generated chunks
            if (event.isNewChunk()) {
                newlyGeneratedChunks++;
                chunksLoadedLast30s++;
            }
        }
    }

    private void reportStats() {
        int chunksProcessed = generatedChunks.size();
        double tps = getTPS();
        
        // Calculate expected total chunks if not already calculated
        if (expectedChunksTotal == null) {
            expectedChunksTotal = calculateExpectedChunksTotal();
        }

        // Calculate estimated time remaining (use chunksProcessed to match what's displayed)
        String estimatedTimeLeft = calculateEstimatedTimeLeft(chunksProcessed, expectedChunksTotal, allChunksLoadedLast30s);

        Logger.info("=== 预生成统计 ===");
        if (isPaused) {
            Logger.info("状态: 已暂停 (等待 TPS 恢复)");
        }
        Logger.info("已处理区块: " + chunksProcessed + " / " + expectedChunksTotal);
        Logger.info("新生成区块 (总计): " + newlyGeneratedChunks);
        Logger.info("新加载区块 (最近30秒): " + chunksLoadedLast30s);
        Logger.info("当前 TPS: " + String.format("%.2f", tps));
        Logger.info("生成设置:");
        Logger.info("  - 形状: " + shape);
        Logger.info("  - Tick 占用: " + String.format("%.1f", tickUsage * 100) + "%");
        Logger.info("  - 目标半径: " + maxRadiusBlocks + " 格 (" + maxRadiusChunks + " 个区块)");
        Logger.info("  - 当前区块半径: " + currentRadius + " / " + maxRadiusChunks + " (" + (currentRadius * 16) + " / " + maxRadiusBlocks + " 格)");
        Logger.info("  - 已达最大区块半径: " + actualMaxRadiusChunks);
        Logger.info("已生成建筑 (总计): " + structuresGeneratedTotal);
        Logger.info("已生成建筑 (最近30秒): " + structuresGeneratedLast30s);
        if (estimatedTimeLeft != null) {
            Logger.info("预计剩余时间: " + estimatedTimeLeft);
        }
        Logger.info("===========================");

        // Reset 30s counters
        chunksLoadedLast30s = 0;
        allChunksLoadedLast30s = 0;
        structuresGeneratedLast30s = 0;
    }
    
    private String calculateEstimatedTimeLeft(int chunksLoaded, int expectedChunksTotal, int chunksLoadedLast30s) {
        // Can't estimate if we don't have enough data
        if (chunksLoadedLast30s == 0 || expectedChunksTotal == 0) {
            return null;
        }
        
        int remainingChunks = expectedChunksTotal - chunksLoaded;
        if (remainingChunks <= 0) {
            return "完成";
        }
        
        // Calculate rate: chunks per second based on chunks loaded in the last 30 seconds
        // chunksLoadedLast30s is the count over 30 seconds, so divide by 30 to get per second
        double chunksPerSecond = chunksLoadedLast30s / 30.0;
        
        if (chunksPerSecond <= 0) {
            return null;
        }
        
        // Calculate estimated seconds remaining: remaining chunks / chunks per second
        long estimatedSeconds = Math.round(remainingChunks / chunksPerSecond);
        
        // Format as human-readable time
        return formatTime(estimatedSeconds);
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            if (remainingSeconds == 0) {
                return minutes + " minute" + (minutes != 1 ? "s" : "");
            } else {
                return minutes + " minute" + (minutes != 1 ? "s" : "") + " " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "");
            }
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            if (remainingMinutes == 0) {
                return hours + " hour" + (hours != 1 ? "s" : "");
            } else {
                return hours + " hour" + (hours != 1 ? "s" : "") + " " + remainingMinutes + " minute" + (remainingMinutes != 1 ? "s" : "");
            }
        }
    }
    
    private int calculateExpectedChunksTotal() {
        try {
            // Calculate all chunks that will be generated within maxRadiusChunks
            Set<String> chunksToGenerate = new HashSet<>();

            if ("SQUARE".equalsIgnoreCase(shape)) {
                // Square: match generateSquareLayer logic - edge chunks for each radius
                for (int radius = 0; radius <= maxRadiusChunks; radius++) {
                    // Top and bottom edges
                    for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
                        // Top edge
                        chunksToGenerate.add(x + "," + (centerChunkZ - radius));
                        // Bottom edge
                        chunksToGenerate.add(x + "," + (centerChunkZ + radius));
                    }
                    // Left and right edges (excluding corners already processed)
                    for (int z = centerChunkZ - radius + 1; z < centerChunkZ + radius; z++) {
                        // Left edge
                        chunksToGenerate.add((centerChunkX - radius) + "," + z);
                        // Right edge
                        chunksToGenerate.add((centerChunkX + radius) + "," + z);
                    }
                }
            } else if ("CIRCLE".equalsIgnoreCase(shape)) {
                // Circle: all chunks within maxRadiusChunks
                for (int radius = 0; radius <= maxRadiusChunks; radius++) {
                    int radiusSquared = radius * radius;
                    int nextRadiusSquared = (radius + 1) * (radius + 1);
                    for (int x = centerChunkX - radius - 1; x <= centerChunkX + radius + 1; x++) {
                        for (int z = centerChunkZ - radius - 1; z <= centerChunkZ + radius + 1; z++) {
                            int dx = x - centerChunkX;
                            int dz = z - centerChunkZ;
                            int distanceSquared = dx * dx + dz * dz;
                            if (distanceSquared >= radiusSquared && distanceSquared < nextRadiusSquared) {
                                chunksToGenerate.add(x + "," + z);
                            }
                        }
                    }
                }
            }

            return chunksToGenerate.size();
        } catch (Exception e) {
            Logger.warn("计算预期区块总数失败: " + e.getMessage());
            return 0;
        }
    }
    

    private double getTPS() {
        try {
            // Try Paper TPS API using reflection
            Object server = Bukkit.getServer();
            Method getTPSMethod = server.getClass().getMethod("getTPS");
            double[] tps = (double[]) getTPSMethod.invoke(server);
            if (tps != null && tps.length > 0) {
                return Math.min(20.0, Math.max(0.0, tps[0]));
            }
        } catch (Exception e) {
            // Fallback if Paper API not available or reflection fails
        }

        // Fallback: return 20.0 if we can't get TPS
        return 20.0;
    }


    private void setWorldBorder() {
        try {
            // World border size is diameter, so multiply radius by 2
            double borderSize = maxRadiusBlocks * 2.0;

            world.getWorldBorder().setCenter(center.getX(), center.getZ());
            world.getWorldBorder().setSize(borderSize);

            Logger.info("世界边界已设置为大小: " + borderSize + " 格 (半径: " + maxRadiusBlocks + "), 中心: (" + center.getX() + ", " + center.getZ() + ")");
        } catch (Exception e) {
            Logger.warn("设置世界边界失败: " + e.getMessage());
        }
    }
}

