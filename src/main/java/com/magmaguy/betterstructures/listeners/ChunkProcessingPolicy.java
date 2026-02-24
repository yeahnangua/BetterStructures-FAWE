package com.magmaguy.betterstructures.listeners;

public final class ChunkProcessingPolicy {
    private ChunkProcessingPolicy() {
    }

    public static boolean shouldMarkProcessed(ChunkScanOutcome outcome) {
        return outcome == ChunkScanOutcome.PASTE_SUCCESS;
    }
}
