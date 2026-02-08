package com.magmaguy.betterstructures.util;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Utility for saving YAML configuration files asynchronously.
 * Serializes config to string on the calling thread (to avoid concurrent modification),
 * then writes the file on an async thread.
 */
public class AsyncConfigSaver {

    private AsyncConfigSaver() {
    }

    /**
     * Saves a FileConfiguration to file asynchronously.
     * The config is serialized to a string immediately (on the calling thread),
     * and the file write happens on an async thread.
     *
     * @param config the FileConfiguration to save
     * @param file   the target file
     */
    public static void saveAsync(FileConfiguration config, File file) {
        // Snapshot the config content on the current thread to avoid race conditions
        String data = config.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(MetadataHandler.PLUGIN, () -> {
            try {
                Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
            } catch (IOException e) {
                Logger.warn("异步保存配置失败: " + file.getName());
                e.printStackTrace();
            }
        });
    }
}
