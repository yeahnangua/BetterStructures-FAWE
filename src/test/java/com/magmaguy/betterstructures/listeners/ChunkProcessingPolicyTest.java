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
