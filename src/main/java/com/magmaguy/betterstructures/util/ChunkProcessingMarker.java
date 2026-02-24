package com.magmaguy.betterstructures.util;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public final class ChunkProcessingMarker {
    private static final NamespacedKey PROCESSED_KEY = new NamespacedKey("betterstructures", "chunk_processed");

    private ChunkProcessingMarker() {
    }

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
