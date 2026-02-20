# 区块加载 I/O 限流 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为结构粘贴添加三层 I/O 保护（已加载过滤、速率限制、并发队列），防止玩家跑图时磁盘 I/O 过载。

**Architecture:** 在 `Schematic.pasteSchematic()` 流程中插入三层限流：L1 过滤已加载区块跳过不必要的 `getChunkAtAsync`；L2 全局速率限制器控制每秒区块加载数；L3 全局队列控制并发粘贴数。三层独立运作，逐级削减 I/O 压力。

**Tech Stack:** Paper API 1.21.3 (`getChunkAtAsync`, `isChunkLoaded`, chunk tickets), Bukkit Scheduler, Java CompletableFuture, ConcurrentLinkedQueue

---

### Task 1: Add config fields to DefaultConfig

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/config/DefaultConfig.java:98-118` (add fields after Terra/FAWE section)

**Step 1: Add static fields and getters**

在 `terraCompatibilityMode` 相关字段之后、MythicMobs 字段之前添加：

```java
// Chunk I/O throttling configuration
@Getter
private static int maxConcurrentStructurePastes;
@Getter
private static int maxChunkLoadsPerSecond;
```

**Step 2: Initialize fields in `initializeValues()`**

在 `validateChunkBeforePaste` 初始化之后、MythicMobs 初始化之前添加：

```java
// Chunk I/O throttling configuration
maxConcurrentStructurePastes = ConfigurationEngine.setInt(
        List.of(
                "Maximum number of structure pastes that can run concurrently.",
                "Each paste involves async chunk loading + FAWE block placement.",
                "Lower values reduce disk I/O pressure on HDD servers.",
                "Set to 0 to disable the limit (not recommended for HDD servers)."),
        fileConfiguration, "chunkIOThrottling.maxConcurrentStructurePastes", 2);

maxChunkLoadsPerSecond = ConfigurationEngine.setInt(
        List.of(
                "Maximum number of chunk load requests the plugin can trigger per second.",
                "This limit is shared across all concurrent structure pastes.",
                "Lower values reduce disk I/O spikes but structures take longer to appear.",
                "Set to 0 to disable the limit (not recommended for HDD servers)."),
        fileConfiguration, "chunkIOThrottling.maxChunkLoadsPerSecond", 10);
```

**Step 3: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/config/DefaultConfig.java
git commit -m "feat(io-throttle): add chunk I/O throttling config fields"
```

---

### Task 2: Create ChunkLoadRateLimiter

**Files:**
- Create: `src/main/java/com/magmaguy/betterstructures/util/ChunkLoadRateLimiter.java`

**Step 1: Create the rate limiter class**

```java
package com.magmaguy.betterstructures.util;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Global rate limiter for plugin-triggered chunk loads.
 * Limits the number of getChunkAtAsync calls per second across all concurrent paste operations.
 */
public class ChunkLoadRateLimiter {

    private static final Queue<ChunkLoadRequest> pendingRequests = new ConcurrentLinkedQueue<>();
    private static BukkitTask dispatchTask;

    private record ChunkLoadRequest(org.bukkit.World world, int chunkX, int chunkZ,
                                    CompletableFuture<Chunk> future) {}

    /**
     * Starts the rate limiter dispatch timer.
     * Must be called from the main thread during plugin enable.
     */
    public static void start() {
        int maxPerSecond = DefaultConfig.getMaxChunkLoadsPerSecond();
        if (maxPerSecond <= 0) {
            Logger.info("区块加载速率限制已禁用 (maxChunkLoadsPerSecond=0)");
            return;
        }

        dispatchTask = Bukkit.getScheduler().runTaskTimer(MetadataHandler.PLUGIN, () -> {
            int dispatched = 0;
            while (dispatched < maxPerSecond) {
                ChunkLoadRequest req = pendingRequests.poll();
                if (req == null) break;
                req.world().getChunkAtAsync(req.chunkX(), req.chunkZ(), true)
                        .thenAccept(chunk -> req.future().complete(chunk))
                        .exceptionally(ex -> {
                            req.future().completeExceptionally(ex);
                            return null;
                        });
                dispatched++;
            }
        }, 0L, 20L); // Every 20 ticks = 1 second

        Logger.info("区块加载速率限制器已启动 (上限: " + maxPerSecond + " chunks/s)");
    }

    /**
     * Submits a batch of chunks for rate-limited loading.
     * If the rate limiter is disabled (maxChunkLoadsPerSecond=0), loads immediately without throttling.
     *
     * @param world The world to load chunks in
     * @param chunkKeys Set of encoded chunk keys (chunkX << 32 | chunkZ & 0xFFFFFFFFL)
     * @return CompletableFuture that completes when ALL requested chunks are loaded
     */
    public static CompletableFuture<Void> loadChunks(org.bukkit.World world, Set<Long> chunkKeys) {
        if (chunkKeys.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // If rate limiting is disabled, load directly
        if (DefaultConfig.getMaxChunkLoadsPerSecond() <= 0) {
            List<CompletableFuture<Chunk>> directFutures = new ArrayList<>();
            for (Long key : chunkKeys) {
                int cx = (int) (key >> 32);
                int cz = key.intValue();
                directFutures.add(world.getChunkAtAsync(cx, cz, true));
            }
            return CompletableFuture.allOf(directFutures.toArray(new CompletableFuture[0]));
        }

        // Rate-limited path: enqueue requests
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (Long key : chunkKeys) {
            int cx = (int) (key >> 32);
            int cz = key.intValue();
            CompletableFuture<Chunk> future = new CompletableFuture<>();
            pendingRequests.add(new ChunkLoadRequest(world, cx, cz, future));
            futures.add(future);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Shuts down the rate limiter and cancels any pending requests.
     */
    public static void shutdown() {
        if (dispatchTask != null) {
            dispatchTask.cancel();
            dispatchTask = null;
        }
        // Complete pending requests exceptionally to avoid hanging futures
        ChunkLoadRequest req;
        while ((req = pendingRequests.poll()) != null) {
            req.future().cancel(false);
        }
    }
}
```

**Step 2: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/util/ChunkLoadRateLimiter.java
git commit -m "feat(io-throttle): add global chunk load rate limiter"
```

---

### Task 3: Create StructurePasteQueue

**Files:**
- Create: `src/main/java/com/magmaguy/betterstructures/util/StructurePasteQueue.java`

**Step 1: Create the paste queue class**

```java
package com.magmaguy.betterstructures.util;

import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.magmacore.util.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global queue that limits the number of concurrent structure paste operations.
 * Each paste operation includes async chunk loading + FAWE block placement.
 */
public class StructurePasteQueue {

    private static final Queue<PasteRequest> pasteQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger activePastes = new AtomicInteger(0);

    private record PasteRequest(Runnable task, String locationDesc) {}

    /**
     * Enqueues a structure paste operation.
     * If the concurrency limit is not reached, executes immediately.
     * Otherwise, queues for later execution when a slot becomes available.
     *
     * @param pasteTask The paste operation to execute
     * @param locationDesc Human-readable location for debug logging (e.g., "123,64,-456")
     */
    public static void enqueue(Runnable pasteTask, String locationDesc) {
        int maxConcurrent = DefaultConfig.getMaxConcurrentStructurePastes();

        // If disabled (0), execute immediately without queuing
        if (maxConcurrent <= 0) {
            pasteTask.run();
            return;
        }

        pasteQueue.add(new PasteRequest(pasteTask, locationDesc));
        Logger.debug("Structure paste queued at " + locationDesc +
                " (queue size: " + pasteQueue.size() + ", active: " + activePastes.get() + ")");
        tryExecuteNext();
    }

    /**
     * Must be called when a paste operation completes (including its onComplete callback).
     * Triggers execution of the next queued paste if any.
     */
    public static void onPasteComplete() {
        activePastes.decrementAndGet();
        tryExecuteNext();
    }

    private static void tryExecuteNext() {
        int maxConcurrent = DefaultConfig.getMaxConcurrentStructurePastes();
        while (activePastes.get() < maxConcurrent) {
            PasteRequest next = pasteQueue.poll();
            if (next == null) break;
            activePastes.incrementAndGet();
            Logger.debug("Structure paste started at " + next.locationDesc() +
                    " (active: " + activePastes.get() + ", queued: " + pasteQueue.size() + ")");
            next.task().run();
        }
    }

    /**
     * Clears the queue and resets counters. Called during plugin disable.
     */
    public static void shutdown() {
        pasteQueue.clear();
        activePastes.set(0);
    }

    /**
     * Returns the current number of active paste operations (for debug/monitoring).
     */
    public static int getActivePastes() {
        return activePastes.get();
    }

    /**
     * Returns the current queue size (for debug/monitoring).
     */
    public static int getQueueSize() {
        return pasteQueue.size();
    }
}
```

**Step 2: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/util/StructurePasteQueue.java
git commit -m "feat(io-throttle): add global structure paste queue"
```

---

### Task 4: Integrate throttling into Schematic.java

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/worldedit/Schematic.java`

This is the core integration task. The `pasteSchematic` method needs to:
1. Wrap the entire operation in `StructurePasteQueue.enqueue()`
2. Filter already-loaded chunks before requesting async loads
3. Use `ChunkLoadRateLimiter.loadChunks()` instead of direct `getChunkAtAsync` calls
4. Call `StructurePasteQueue.onPasteComplete()` when done

**Step 1: Add imports**

Add to the import section:

```java
import com.magmaguy.betterstructures.util.ChunkLoadRateLimiter;
import com.magmaguy.betterstructures.util.StructurePasteQueue;
```

**Step 2: Rewrite `pasteSchematic` method**

Replace the entire `pasteSchematic` method (lines 128-181) with:

```java
    public static void pasteSchematic(
            Clipboard schematicClipboard,
            Location location,
            Vector schematicOffset,
            Runnable prePasteCallback,
            Function<Boolean, Material> pedestalMaterialProvider,
            Runnable onComplete) {

        org.bukkit.World world = location.getWorld();
        if (world == null) {
            Logger.warn("无法粘贴: 世界为空");
            return;
        }

        String locationDesc = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();

        // L3: Enqueue the entire paste operation through the global paste queue
        StructurePasteQueue.enqueue(() -> {
            // Step 1: Calculate required chunks WITHOUT accessing world
            Set<Long> requiredChunks = calculateRequiredChunks(schematicClipboard, location, schematicOffset);

            // Step 2: L1 - Filter out already-loaded chunks
            Set<Long> unloadedChunks = new HashSet<>();
            int skippedLoaded = 0;
            for (Long key : requiredChunks) {
                int chunkX = (int) (key >> 32);
                int chunkZ = key.intValue();
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    skippedLoaded++;
                } else {
                    unloadedChunks.add(key);
                }
            }

            Logger.debug("Structure paste started at " + locationDesc +
                    " (skipped " + skippedLoaded + " already-loaded chunks, loading " + unloadedChunks.size() + " chunks)");

            if (unloadedChunks.isEmpty()) {
                // All chunks already loaded, proceed directly on main thread
                Bukkit.getScheduler().runTask(MetadataHandler.PLUGIN, () -> {
                    if (prePasteCallback != null) prePasteCallback.run();
                    for (Long key : requiredChunks) {
                        int chunkX = (int) (key >> 32);
                        int chunkZ = key.intValue();
                        world.addPluginChunkTicket(chunkX, chunkZ, MetadataHandler.PLUGIN);
                    }
                    executeFaweAsyncPaste(schematicClipboard, location, schematicOffset,
                            pedestalMaterialProvider, () -> {
                                if (onComplete != null) onComplete.run();
                                StructurePasteQueue.onPasteComplete();
                            }, requiredChunks, world);
                });
            } else {
                // Step 3: L2 - Load unloaded chunks through the rate limiter
                ChunkLoadRateLimiter.loadChunks(world, unloadedChunks)
                        .thenRun(() -> {
                            // Step 4: Main thread — run prePasteCallback and add chunk tickets
                            Bukkit.getScheduler().runTask(MetadataHandler.PLUGIN, () -> {
                                if (prePasteCallback != null) prePasteCallback.run();
                                for (Long key : requiredChunks) {
                                    int chunkX = (int) (key >> 32);
                                    int chunkZ = key.intValue();
                                    world.addPluginChunkTicket(chunkX, chunkZ, MetadataHandler.PLUGIN);
                                }
                                // Step 5: Switch to async thread for FAWE paste
                                executeFaweAsyncPaste(schematicClipboard, location, schematicOffset,
                                        pedestalMaterialProvider, () -> {
                                            if (onComplete != null) onComplete.run();
                                            StructurePasteQueue.onPasteComplete();
                                        }, requiredChunks, world);
                            });
                        });
            }
        }, locationDesc);
    }
```

**Step 3: Update `executeFaweAsyncPaste` to use the unified onComplete**

Replace the cleanup section at the end of `executeFaweAsyncPaste` (lines 247-255). The `onComplete` callback now includes `StructurePasteQueue.onPasteComplete()`, so the method stays the same except the `onComplete` parameter already handles queue notification. No change needed to `executeFaweAsyncPaste` itself — it already calls `onComplete.run()` at line 254.

Verify: the existing `executeFaweAsyncPaste` calls `if (onComplete != null) onComplete.run();` in the main-thread cleanup block. This is correct — the caller now passes a wrapped `onComplete` that includes both the original callback and `StructurePasteQueue.onPasteComplete()`.

**Step 4: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/worldedit/Schematic.java
git commit -m "feat(io-throttle): integrate 3-layer I/O throttling into paste pipeline"
```

---

### Task 5: Initialize and clean up in BetterStructures.java

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/BetterStructures.java`

**Step 1: Add import**

```java
import com.magmaguy.betterstructures.util.ChunkLoadRateLimiter;
import com.magmaguy.betterstructures.util.StructurePasteQueue;
```

**Step 2: Start rate limiter in `onEnable()`**

Add after the MythicMobs initialization block (after line 99), before CommandManager:

```java
// Initialize chunk I/O throttling
ChunkLoadRateLimiter.start();
```

**Step 3: Add shutdown calls in `onDisable()`**

Add before `Bukkit.getServer().getScheduler().cancelTasks(MetadataHandler.PLUGIN);` (before line 146):

```java
ChunkLoadRateLimiter.shutdown();
StructurePasteQueue.shutdown();
```

**Step 4: Build to verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/BetterStructures.java
git commit -m "feat(io-throttle): initialize and cleanup throttling on plugin lifecycle"
```

---

### Task 6: Build and verify

**Files:** None (verification only)

**Step 1: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 2: Verify generated config.yml includes new fields**

After running the plugin on a test server, check that `config.yml` contains:

```yaml
chunkIOThrottling:
  maxConcurrentStructurePastes: 2
  maxChunkLoadsPerSecond: 10
```

**Step 3: Verify debug logging**

Set BetterStructures to debug mode. Walk into unexplored territory and verify console shows:
- `"Structure paste queued at ..."` messages
- `"Structure paste started at ... (skipped N already-loaded chunks, loading M chunks)"` messages
- `"Structure paste completed at ..."` messages

**Step 4: Final commit with all verification passing**

```bash
git add -A
git commit -m "feat(io-throttle): chunk I/O throttling for structure pasting

Three-layer I/O protection:
- L1: Skip already-loaded chunks
- L2: Global rate limiter (maxChunkLoadsPerSecond)
- L3: Concurrent paste queue (maxConcurrentStructurePastes)"
```
