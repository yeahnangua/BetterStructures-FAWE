package com.magmaguy.betterstructures.buildingfitter;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.betterstructures.util.ChunkValidationUtil;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Singleton manager for handling structures waiting for chunk generation.
 * <p>
 * When a structure needs to be placed but required chunks are not yet fully generated
 * (common with Terra/FAWE async world generation), this manager queues the structure
 * and monitors chunk readiness. Once all required chunks are ready, the structure
 * is pasted automatically.
 * <p>
 * Thread-safe: Uses concurrent collections for all state management.
 */
public class PendingStructureManager {

    private static PendingStructureManager instance;

    /**
     * Queue of pending structures awaiting chunk generation.
     */
    private final Queue<PendingStructure> pendingQueue = new ConcurrentLinkedQueue<>();

    /**
     * Index mapping chunk coordinates ("chunkX,chunkZ") to the set of structures waiting for that chunk.
     * Enables O(1) lookup when a chunk becomes ready.
     */
    private final Map<String, Set<PendingStructure>> chunkWaiters = new ConcurrentHashMap<>();

    /**
     * Periodic cleanup task for removing timed-out structures.
     */
    private BukkitRunnable cleanupTask;

    /**
     * Cleanup interval in ticks (100 ticks = 5 seconds).
     */
    private static final long CLEANUP_INTERVAL_TICKS = 100L;

    /**
     * Private constructor. Initializes the manager and starts the cleanup task.
     */
    private PendingStructureManager() {
        startCleanupTask();
    }

    /**
     * Gets the singleton instance of the PendingStructureManager.
     * Creates the instance if it does not exist.
     *
     * @return the singleton instance
     */
    public static synchronized PendingStructureManager getInstance() {
        if (instance == null) {
            instance = new PendingStructureManager();
        }
        return instance;
    }

    /**
     * Queues a structure for deferred paste once all required chunks are ready.
     * <p>
     * If all required chunks are already fully generated, the paste is executed immediately.
     * Otherwise, the structure is queued and chunk generation is requested for any pending chunks.
     *
     * @param fitAnything the structure fitter containing schematic and placement data
     * @param location    the target location for the structure
     * @param width       the width of the structure in blocks (X axis)
     * @param depth       the depth of the structure in blocks (Z axis)
     * @return true if the structure was queued or pasted immediately, false if the queue is full
     */
    public boolean queueStructure(FitAnything fitAnything, Location location, int width, int depth) {
        // Check queue size limit
        int maxSize = DefaultConfig.getStructureQueueMaxSize();
        if (pendingQueue.size() >= maxSize) {
            Logger.debug("Structure queue full (" + maxSize + "), skipping structure at " +
                    location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            return false;
        }

        World world = location.getWorld();
        if (world == null) {
            Logger.warn("Cannot queue structure: world is null");
            return false;
        }

        // Calculate required chunks
        Set<String> requiredChunks = ChunkValidationUtil.getRequiredChunks(location, width, depth);
        Set<String> readyChunks = new HashSet<>();
        Set<String> pendingChunks = new HashSet<>();

        // Check which chunks are ready
        for (String chunkKey : requiredChunks) {
            String[] parts = chunkKey.split(",");
            int chunkX = Integer.parseInt(parts[0]);
            int chunkZ = Integer.parseInt(parts[1]);

            if (isChunkReady(world, chunkX, chunkZ)) {
                readyChunks.add(chunkKey);
            } else {
                pendingChunks.add(chunkKey);
            }
        }

        // If all chunks are ready, paste immediately
        if (pendingChunks.isEmpty()) {
            Logger.debug("All chunks ready, executing immediate paste at " +
                    location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            executePaste(fitAnything, location);
            return true;
        }

        // Create pending structure entry
        PendingStructure pending = new PendingStructure(
                fitAnything,
                location,
                requiredChunks,
                readyChunks,
                System.currentTimeMillis()
        );

        // Add to queue
        pendingQueue.add(pending);

        // Register in chunk waiters index
        for (String chunkKey : pendingChunks) {
            chunkWaiters.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(pending);

            // Request chunk generation
            String[] parts = chunkKey.split(",");
            int chunkX = Integer.parseInt(parts[0]);
            int chunkZ = Integer.parseInt(parts[1]);
            requestChunkGeneration(world, chunkX, chunkZ);
        }

        Logger.debug("Queued structure at " + location.getBlockX() + "," + location.getBlockY() + "," +
                location.getBlockZ() + " waiting for " + pendingChunks.size() + " chunks");

        return true;
    }

    /**
     * Called when a chunk becomes ready (loaded and fully generated).
     * Checks all structures waiting for this chunk and pastes any that are now complete.
     *
     * @param chunk the chunk that is now ready
     */
    public void onChunkReady(Chunk chunk) {
        if (chunk == null) return;

        String chunkKey = chunk.getX() + "," + chunk.getZ();

        // Validate chunk is fully generated
        if (!ChunkValidationUtil.isChunkFullyGenerated(chunk)) {
            return;
        }

        // Get all structures waiting for this chunk
        Set<PendingStructure> waiters = chunkWaiters.remove(chunkKey);
        if (waiters == null || waiters.isEmpty()) {
            return;
        }

        // Process each waiting structure
        for (PendingStructure pending : waiters) {
            // Skip already processed structures (prevents duplicate paste)
            if (pending.processed) continue;

            // Mark this chunk as ready
            pending.readyChunks.add(chunkKey);

            // Check if all required chunks are now ready
            if (pending.readyChunks.containsAll(pending.requiredChunks)) {
                // Mark as processed to prevent duplicate paste
                pending.processed = true;

                // Remove from queue
                pendingQueue.remove(pending);

                // Clean up waiter entries for other chunks this structure was waiting on
                cleanupWaiterEntries(pending);

                // Release chunk tickets
                releaseChunkTickets(pending);

                // Execute paste
                Logger.debug("All chunks ready for queued structure at " +
                        pending.location.getBlockX() + "," + pending.location.getBlockY() + "," +
                        pending.location.getBlockZ() + ", executing paste");
                executePaste(pending.fitAnything, pending.location);
            }
        }
    }

    /**
     * Requests chunk generation with a plugin chunk ticket.
     * The ticket keeps the chunk loaded until explicitly released.
     * <p>
     * Uses Spigot-compatible synchronous chunk loading. The actual chunk readiness
     * notification comes from NewChunkLoadEvent when the chunk is fully generated.
     *
     * @param world  the world containing the chunk
     * @param chunkX the chunk X coordinate
     * @param chunkZ the chunk Z coordinate
     */
    private void requestChunkGeneration(World world, int chunkX, int chunkZ) {
        // Add plugin chunk ticket to keep chunk loaded
        world.addPluginChunkTicket(chunkX, chunkZ, MetadataHandler.PLUGIN);

        // Load chunk synchronously (triggers generation if needed)
        // For async generators like Terra/FAWE, the chunk may be in progress
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.loadChunk(chunkX, chunkZ);
        }

        // Schedule a delayed check in case chunk is already ready after loadChunk
        // The NewChunkLoadEvent also triggers onChunkReady, providing redundant coverage
        new BukkitRunnable() {
            @Override
            public void run() {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    onChunkReady(chunk);
                }
            }
        }.runTaskLater(MetadataHandler.PLUGIN, 5L);
    }

    /**
     * Checks if a chunk is ready (loaded and fully generated).
     *
     * @param world  the world containing the chunk
     * @param chunkX the chunk X coordinate
     * @param chunkZ the chunk Z coordinate
     * @return true if the chunk is ready for structure placement
     */
    private boolean isChunkReady(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        return ChunkValidationUtil.isChunkFullyGenerated(chunk);
    }

    /**
     * Executes the structure paste on the main thread.
     * If called from an async thread, schedules execution on the main thread.
     *
     * @param fitAnything the structure fitter
     * @param location    the target location
     */
    private void executePaste(FitAnything fitAnything, Location location) {
        if (Bukkit.isPrimaryThread()) {
            fitAnything.pasteBypassValidation(location);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    fitAnything.pasteBypassValidation(location);
                }
            }.runTask(MetadataHandler.PLUGIN);
        }
    }

    /**
     * Removes a pending structure from all waiter sets it was registered in.
     *
     * @param pending the pending structure to clean up
     */
    private void cleanupWaiterEntries(PendingStructure pending) {
        for (String chunkKey : pending.requiredChunks) {
            Set<PendingStructure> waiters = chunkWaiters.get(chunkKey);
            if (waiters != null) {
                waiters.remove(pending);
                // Remove empty sets from the map
                if (waiters.isEmpty()) {
                    chunkWaiters.remove(chunkKey);
                }
            }
        }
    }

    /**
     * Releases all chunk tickets held for a pending structure.
     *
     * @param pending the pending structure whose tickets should be released
     */
    private void releaseChunkTickets(PendingStructure pending) {
        World world = pending.location.getWorld();
        if (world == null) return;

        for (String chunkKey : pending.requiredChunks) {
            String[] parts = chunkKey.split(",");
            int chunkX = Integer.parseInt(parts[0]);
            int chunkZ = Integer.parseInt(parts[1]);
            world.removePluginChunkTicket(chunkX, chunkZ, MetadataHandler.PLUGIN);
        }
    }

    /**
     * Starts the periodic cleanup task that removes timed-out structures.
     */
    private void startCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupTimedOutStructures();
            }
        };

        // Schedule to run periodically - delay startup by one interval
        cleanupTask.runTaskTimer(MetadataHandler.PLUGIN, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    /**
     * Removes structures that have exceeded the timeout period.
     * Cleans up associated waiter entries and releases chunk tickets.
     */
    private void cleanupTimedOutStructures() {
        long timeoutMs = DefaultConfig.getStructureQueueTimeoutSeconds() * 1000L;
        long now = System.currentTimeMillis();

        Iterator<PendingStructure> iterator = pendingQueue.iterator();
        while (iterator.hasNext()) {
            PendingStructure pending = iterator.next();

            if (now - pending.createdTimestamp > timeoutMs) {
                iterator.remove();

                // Clean up waiter entries
                cleanupWaiterEntries(pending);

                // Release chunk tickets
                releaseChunkTickets(pending);

                Logger.warn("Structure queue timeout at " +
                        pending.location.getBlockX() + "," + pending.location.getBlockY() + "," +
                        pending.location.getBlockZ() + " - chunks did not generate in time");
            }
        }
    }

    /**
     * Gets the current number of structures in the pending queue.
     *
     * @return the queue size
     */
    public int getQueueSize() {
        return pendingQueue.size();
    }

    /**
     * Shuts down the manager, cancelling the cleanup task, releasing all chunk tickets,
     * and clearing all pending structures.
     */
    public static synchronized void shutdown() {
        if (instance == null) return;

        // Cancel cleanup task
        if (instance.cleanupTask != null) {
            instance.cleanupTask.cancel();
            instance.cleanupTask = null;
        }

        // Release all chunk tickets for pending structures
        for (PendingStructure pending : instance.pendingQueue) {
            instance.releaseChunkTickets(pending);
        }

        // Clear collections
        instance.pendingQueue.clear();
        instance.chunkWaiters.clear();

        instance = null;
    }

    /**
     * Inner class holding data for a structure pending chunk generation.
     */
    private static class PendingStructure {
        /**
         * The structure fitter containing schematic and placement data.
         */
        final FitAnything fitAnything;

        /**
         * The target paste location.
         */
        final Location location;

        /**
         * Set of all chunk coordinate keys required for this structure (format: "chunkX,chunkZ").
         */
        final Set<String> requiredChunks;

        /**
         * Set of chunk coordinate keys that are ready (mutable, updated as chunks become available).
         */
        final Set<String> readyChunks;

        /**
         * Timestamp when this structure was queued (for timeout tracking).
         */
        final long createdTimestamp;

        /**
         * Whether this structure has already been processed (pasted or cancelled).
         * Prevents duplicate paste attempts from concurrent chunk ready notifications.
         */
        volatile boolean processed = false;

        /**
         * Creates a new PendingStructure.
         *
         * @param fitAnything      the structure fitter
         * @param location         the target paste location
         * @param requiredChunks   all chunks required for pasting
         * @param readyChunks      chunks that were already ready at queue time
         * @param createdTimestamp creation timestamp for timeout tracking
         */
        PendingStructure(FitAnything fitAnything, Location location, Set<String> requiredChunks,
                         Set<String> readyChunks, long createdTimestamp) {
            this.fitAnything = fitAnything;
            this.location = location;
            this.requiredChunks = requiredChunks;
            // Use a thread-safe set for readyChunks since it's modified
            this.readyChunks = ConcurrentHashMap.newKeySet();
            this.readyChunks.addAll(readyChunks);
            this.createdTimestamp = createdTimestamp;
        }
    }
}
