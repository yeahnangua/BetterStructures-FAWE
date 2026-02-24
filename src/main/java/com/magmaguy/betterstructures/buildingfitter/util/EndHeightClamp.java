package com.magmaguy.betterstructures.buildingfitter.util;

public final class EndHeightClamp {
    private EndHeightClamp() {
    }

    public static int clamp(int candidateY, int highestAllowedY, int lowestAllowedY, int schematicHeight, int offsetYAbs) {
        int minY = lowestAllowedY + 1 + offsetYAbs;
        int maxY = highestAllowedY - schematicHeight + offsetYAbs;

        if (maxY < minY) {
            return minY;
        }
        if (candidateY < minY) {
            return minY;
        }
        if (candidateY > maxY) {
            return maxY;
        }
        return candidateY;
    }
}
