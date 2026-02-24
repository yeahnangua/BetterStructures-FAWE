# Terra Natural Generation Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复 Terra 相关自然生成链路中的核心逻辑矛盾，确保“仅成功放置才标记 processed”，并提升可观测性与一致性。

**Architecture:** 采用增量重构。保留现有生成架构与网格分布规则，重构处理状态闭环：`ChunkLoadEvent` 只负责调度与重试，成功标记迁移到粘贴成功回调。通过统一的成功/失败信号链路，避免“扫描失败却永久标记”。同时修复已发现的末地高度逻辑 bug 与配置语义偏差。

**Tech Stack:** Java 21, Paper API 1.21.3, Bukkit Scheduler, FAWE async paste pipeline, JUnit 5

---

### Task 1: Add Test Baseline For Processing Policy

**Files:**
- Modify: `build.gradle:31-54`
- Create: `src/main/java/com/magmaguy/betterstructures/listeners/ChunkScanOutcome.java`
- Create: `src/main/java/com/magmaguy/betterstructures/listeners/ChunkProcessingPolicy.java`
- Create: `src/test/java/com/magmaguy/betterstructures/listeners/ChunkProcessingPolicyTest.java`

**Step 1: Write the failing test**

```java
package com.magmaguy.betterstructures.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkProcessingPolicyTest {
    @Test
    void marksOnlyOnPasteSuccess() {
        assertFalse(ChunkProcessingPolicy.shouldMarkProcessed(ChunkScanOutcome.SKIP_PROCESSED));
        assertFalse(ChunkProcessingPolicy.shouldMarkProcessed(ChunkScanOutcome.SCAN_FAILED));
        assertFalse(ChunkProcessingPolicy.shouldMarkProcessed(ChunkScanOutcome.PASTE_FAILED));
        assertTrue(ChunkProcessingPolicy.shouldMarkProcessed(ChunkScanOutcome.PASTE_SUCCESS));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests ChunkProcessingPolicyTest -q`  
Expected: FAIL with missing class errors (`ChunkScanOutcome` / `ChunkProcessingPolicy` not found).

**Step 3: Write minimal implementation + JUnit setup**

```java
package com.magmaguy.betterstructures.listeners;

public enum ChunkScanOutcome {
    SKIP_PROCESSED,
    SCAN_FAILED,
    PASTE_FAILED,
    PASTE_SUCCESS
}
```

```java
package com.magmaguy.betterstructures.listeners;

public final class ChunkProcessingPolicy {
    private ChunkProcessingPolicy() {}

    public static boolean shouldMarkProcessed(ChunkScanOutcome outcome) {
        return outcome == ChunkScanOutcome.PASTE_SUCCESS;
    }
}
```

```gradle
testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

tasks.withType(Test) {
    useJUnitPlatform()
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests ChunkProcessingPolicyTest -q`  
Expected: PASS.

**Step 5: Commit**

```bash
git add build.gradle src/main/java/com/magmaguy/betterstructures/listeners/ChunkScanOutcome.java src/main/java/com/magmaguy/betterstructures/listeners/ChunkProcessingPolicy.java src/test/java/com/magmaguy/betterstructures/listeners/ChunkProcessingPolicyTest.java
git commit -m "test(core): add chunk processing policy baseline"
```

---

### Task 2: Add Shared Chunk Marker Utility

**Files:**
- Create: `src/main/java/com/magmaguy/betterstructures/util/ChunkProcessingMarker.java`
- Modify: `src/main/java/com/magmaguy/betterstructures/listeners/NewChunkLoadEvent.java:38-53`

**Step 1: Write failing test for marker key consistency**

```java
package com.magmaguy.betterstructures.util;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkProcessingMarkerTest {
    @Test
    void keyUsesStableNamespaceAndPath() {
        NamespacedKey key = ChunkProcessingMarker.getProcessedKey();
        assertEquals("betterstructures", key.getNamespace());
        assertEquals("chunk_processed", key.getKey());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests ChunkProcessingMarkerTest -q`  
Expected: FAIL because `ChunkProcessingMarker` does not exist.

**Step 3: Implement marker utility and replace duplicated code in listener**

```java
public final class ChunkProcessingMarker {
    private static final NamespacedKey PROCESSED_KEY = new NamespacedKey("betterstructures", "chunk_processed");

    private ChunkProcessingMarker() {}

    public static NamespacedKey getProcessedKey() {
        return PROCESSED_KEY;
    }

    public static boolean isProcessed(Chunk chunk) {
        return chunk.getPersistentDataContainer().has(PROCESSED_KEY, PersistentDataType.BYTE);
    }

    public static void markProcessed(Chunk chunk) {
        chunk.getPersistentDataContainer().set(PROCESSED_KEY, PersistentDataType.BYTE, (byte) 1);
    }
}
```

`NewChunkLoadEvent` 用该工具替代本地 `PROCESSED_KEY/isChunkProcessed/markChunkProcessed` 重复定义。

**Step 4: Run tests**

Run: `./gradlew test --tests ChunkProcessingMarkerTest --tests ChunkProcessingPolicyTest -q`  
Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/util/ChunkProcessingMarker.java src/main/java/com/magmaguy/betterstructures/listeners/NewChunkLoadEvent.java src/test/java/com/magmaguy/betterstructures/util/ChunkProcessingMarkerTest.java
git commit -m "refactor(core): centralize chunk processed marker utility"
```

---

### Task 3: Propagate Paste Success/Failure Through Schematic Pipeline

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/worldedit/Schematic.java:128-257`
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java:106-213`

**Step 1: Write failing test for processing policy on paste failure path**

```java
@Test
void doesNotMarkOnPasteFailure() {
    assertFalse(ChunkProcessingPolicy.shouldMarkProcessed(ChunkScanOutcome.PASTE_FAILED));
}
```

**Step 2: Run test to confirm baseline**

Run: `./gradlew test --tests ChunkProcessingPolicyTest -q`  
Expected: PASS (baseline safety test).

**Step 3: Implement callback signal chain**

1. 把 `Schematic.pasteSchematic(..., Runnable onComplete)` 改为 `Consumer<Boolean> onComplete`。  
2. 在 `executeFaweAsyncPaste()` 中维护 `boolean success = true`；异常时置 `false`。  
3. 回到主线程后调用 `onComplete.accept(success)`。  
4. 在 `FitAnything.paste()` 中根据 success 分流：
   - success: 执行原 `onPasteComplete` 内容；
   - failure: 记录 `PASTE_FAILED` 日志，不触发成功后副作用。

**Step 4: Run verification**

Run: `./gradlew compileJava`  
Expected: BUILD SUCCESSFUL.

**Step 5: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/worldedit/Schematic.java src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java
git commit -m "refactor(paste): propagate paste success result through callbacks"
```

---

### Task 4: Make Processed Marking Success-Only And Fix Lifecycle Dedup Window

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/listeners/NewChunkLoadEvent.java:55-139`
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitSurfaceBuilding.java`
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAirBuilding.java`
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitLiquidBuilding.java`
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitUndergroundBuilding.java`

**Step 1: Write failing policy-oriented test**

新增断言，明确“扫描失败不应标记 processed”：

```java
@Test
void doesNotMarkOnScanFailure() {
    assertFalse(ChunkProcessingPolicy.shouldMarkProcessed(ChunkScanOutcome.SCAN_FAILED));
}
```

**Step 2: Run test**

Run: `./gradlew test --tests ChunkProcessingPolicyTest -q`  
Expected: PASS (guard rail).

**Step 3: Implement behavior changes**

1. `NewChunkLoadEvent` 去掉固定 `20L` 定时移除 `loadingChunks`。  
2. 在扫描重试链路终态统一释放 `loadingChunks`（确保覆盖 world invalid / chunk unload / retries exhausted / scan dispatch completed）。  
3. 删除监听器中“扫描后立即 mark processed”的逻辑。  
4. 在各 `Fit*` 类调用 `paste()` 时传入触发 chunk（新增 `paste(Location, Chunk sourceChunk)` 重载）。  
5. 在 `FitAnything` 的 success 分支中调用 `ChunkProcessingMarker.markProcessed(sourceChunk)`，并打 `PASTE_SUCCESS_MARKED` 日志。

**Step 4: Run compile + tests**

Run: `./gradlew test --tests ChunkProcessingPolicyTest -q && ./gradlew compileJava`  
Expected: tests PASS + BUILD SUCCESSFUL.

**Step 5: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/listeners/NewChunkLoadEvent.java src/main/java/com/magmaguy/betterstructures/buildingfitter/FitSurfaceBuilding.java src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAirBuilding.java src/main/java/com/magmaguy/betterstructures/buildingfitter/FitLiquidBuilding.java src/main/java/com/magmaguy/betterstructures/buildingfitter/FitUndergroundBuilding.java src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java
git commit -m "fix(gen): mark chunk processed only after successful paste"
```

---

### Task 5: Align Terra Validation Semantics With Config And Scanner Consistency

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/worldedit/Schematic.java:145-179`
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/util/TerrainAdequacy.java:45-50`
- Modify: `src/main/java/com/magmaguy/betterstructures/util/ChunkValidationUtil.java` (仅保留实际使用方法或补充注释)

**Step 1: Write failing test for config semantic guard**

```java
@Test
void marksOnlyOnSuccessEvenWhenValidationFails() {
    assertFalse(ChunkProcessingPolicy.shouldMarkProcessed(ChunkScanOutcome.PASTE_FAILED));
}
```

**Step 2: Run test**

Run: `./gradlew test --tests ChunkProcessingPolicyTest -q`  
Expected: PASS (guard rail).

**Step 3: Implement semantic fixes**

1. 在 `Schematic.pasteSchematic` 的 chunk preload 完成后，若 `validateChunkBeforePaste=true`，逐 chunk 调 `ChunkValidationUtil.isChunkFullyGenerated`。  
2. 任一 chunk 验证失败时，终止本次粘贴并返回 `onComplete.accept(false)`。  
3. `TerrainAdequacy` 对“区块未加载”改为与 `Topology` 一致的保守失败（返回 false），避免评分逻辑冲突。  
4. 清理/注释 `ChunkValidationUtil` 中未使用的方法，避免“死代码式语义”误导。

**Step 4: Run verification**

Run: `./gradlew compileJava && ./gradlew test -q`  
Expected: BUILD SUCCESSFUL + tests PASS.

**Step 5: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/worldedit/Schematic.java src/main/java/com/magmaguy/betterstructures/buildingfitter/util/TerrainAdequacy.java src/main/java/com/magmaguy/betterstructures/util/ChunkValidationUtil.java
git commit -m "fix(terra): enforce validateChunkBeforePaste semantics and align scan rules"
```

---

### Task 6: Fix End-Dimension Logic Bugs And Add Regression Tests

**Files:**
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAirBuilding.java:35-46`
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitUndergroundBuilding.java:217-221`
- Create: `src/main/java/com/magmaguy/betterstructures/buildingfitter/util/EndHeightClamp.java`
- Create: `src/test/java/com/magmaguy/betterstructures/buildingfitter/util/EndHeightClampTest.java`

**Step 1: Write failing tests for pure clamp helper**

```java
class EndHeightClampTest {
    @Test
    void clampsToHighestEndBoundWhenExceedingCeiling() {
        int clamped = EndHeightClamp.clamp(400, 320, 0, 60, 12);
        assertTrue(clamped <= 320);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests EndHeightClampTest -q`  
Expected: FAIL (`EndHeightClamp` missing).

**Step 3: Implement minimal helper and wire logic**

1. 新建 `EndHeightClamp`（纯函数）处理上/下边界夹取。  
2. `FitUndergroundBuilding` 末地分支上限比较改为 `DefaultConfig.getHighestYEnd()`。  
3. `FitAirBuilding` 末地高度随机范围改为 `nextInt(min, max + 1)`。

**Step 4: Run tests and compile**

Run: `./gradlew test --tests EndHeightClampTest -q && ./gradlew compileJava`  
Expected: PASS + BUILD SUCCESSFUL.

**Step 5: Commit**

```bash
git add src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAirBuilding.java src/main/java/com/magmaguy/betterstructures/buildingfitter/FitUndergroundBuilding.java src/main/java/com/magmaguy/betterstructures/buildingfitter/util/EndHeightClamp.java src/test/java/com/magmaguy/betterstructures/buildingfitter/util/EndHeightClampTest.java
git commit -m "fix(end): correct end altitude range and y-bound clamping"
```

---

### Task 7: Observability, Docs, And Final Verification

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `README.zh-CN.md:34-40`
- Modify: `src/main/java/com/magmaguy/betterstructures/listeners/NewChunkLoadEvent.java`
- Modify: `src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java`

**Step 1: Add/adjust debug logs**

关键日志：

1. `SKIP_PROCESSED`
2. `SCAN_FAILED`
3. `PASTE_FAILED`
4. `PASTE_SUCCESS_MARKED`

**Step 2: Update docs**

1. README “PersistentDataContainer 区块标记”段落改为“成功放置后才标记”。  
2. CHANGELOG 记录行为变化与兼容性影响。

**Step 3: Full verification (@verification-before-completion)**

Run:

```bash
./gradlew clean test build
```

Expected: BUILD SUCCESSFUL.

**Step 4: Manual server verification**

在 `testbed` 服进行：

1. `terraCompatibility.enabled=false` 跑新区块，观察日志与自然生成。  
2. `terraCompatibility.enabled=true` + `structureScanDelayTicks=40` + `structureScanMaxRetries=8`，重复验证。  
3. 重启后确认已成功区块不重复刷，失败区块可后续重试。

**Step 5: Final commit**

```bash
git add CHANGELOG.md README.zh-CN.md src/main/java/com/magmaguy/betterstructures/listeners/NewChunkLoadEvent.java src/main/java/com/magmaguy/betterstructures/buildingfitter/FitAnything.java
git commit -m "chore(gen): add observability and docs for success-only chunk processing"
```

---

## Notes For Execution

1. 严格按任务顺序执行，避免一次改动跨多个任务导致回归定位困难。  
2. 每个任务完成后先跑该任务最小验证命令，再提交。  
3. 优先保持 YAGNI：不做额外架构重写，不扩展到与本目标无关模块。  
4. 如执行中发现 `src/test` 依赖 Bukkit 环境导致不稳定，优先保留“纯函数测试 + 手工集成验证”策略。
