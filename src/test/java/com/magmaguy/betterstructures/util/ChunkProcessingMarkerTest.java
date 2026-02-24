package com.magmaguy.betterstructures.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChunkProcessingMarkerTest {
    @Test
    void keyUsesStableNamespaceAndPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/magmaguy/betterstructures/util/ChunkProcessingMarker.java"));
        assertTrue(source.contains("new NamespacedKey(\"betterstructures\", \"chunk_processed\")"));
    }
}
