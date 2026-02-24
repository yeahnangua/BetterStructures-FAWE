package com.magmaguy.betterstructures.util;

import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.magmacore.util.Logger;

public class DeveloperLogger {

    private DeveloperLogger() {
    }

    public static void debug(String message) {
        if (!DefaultConfig.isDeveloperMessages()) return;
        Logger.debug(message);
    }
}
