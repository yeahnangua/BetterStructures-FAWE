package com.magmaguy.betterstructures.config.schematics;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.schematics.SchematicContainer;
import com.magmaguy.betterstructures.worldedit.Schematic;
import com.magmaguy.magmacore.config.CustomConfig;
import com.magmaguy.magmacore.util.Logger;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SchematicConfig extends CustomConfig {
    @Getter
    private static final HashMap<String, SchematicConfigField> schematicConfigurations = new HashMap<>();

    public SchematicConfig() {
        super("schematics", SchematicConfigField.class);
        schematicConfigurations.clear();

        File readMeFile = new File(MetadataHandler.PLUGIN.getDataFolder(), "schematics" + File.separatorChar + "ReadMe.txt");
        if (!readMeFile.exists()) {
            readMeFile.getParentFile().mkdirs();
            MetadataHandler.PLUGIN.saveResource("schematics" + File.separatorChar + "ReadMe.txt", false);
        }

        // Step 1: Collect all .schem file paths (fast, synchronous)
        List<File> schematicFiles = new ArrayList<>();
        File schematicsDir = new File(MetadataHandler.PLUGIN.getDataFolder().getAbsolutePath() + File.separatorChar + "schematics");
        if (schematicsDir.exists() && schematicsDir.listFiles() != null) {
            for (File file : schematicsDir.listFiles()) {
                collectSchematicFiles(file, schematicFiles);
            }
        }

        // Step 2: Load all schematics in parallel
        Logger.info("Loading " + schematicFiles.size() + " schematics...");
        long startTime = System.currentTimeMillis();

        Map<File, Clipboard> clipboards = new ConcurrentHashMap<>();

        // Use parallel stream for concurrent loading (uses ForkJoinPool)
        schematicFiles.parallelStream().forEach(file -> {
            Clipboard clipboard = Schematic.load(file);
            if (clipboard != null) {
                clipboards.put(file, clipboard);
            }
        });

        Logger.info("Loaded " + clipboards.size() + " schematics in " + (System.currentTimeMillis() - startTime) + "ms");

        for (String key : super.getCustomConfigFieldsHashMap().keySet())
            schematicConfigurations.put(key, (SchematicConfigField) super.getCustomConfigFieldsHashMap().get(key));

        for (File file : clipboards.keySet()) {
            String configurationName = convertFromSchematicFilename(file.getName());
            SchematicConfigField schematicConfigField = new SchematicConfigField(configurationName, true);
            new CustomConfig(file.getParent().replace(
                    MetadataHandler.PLUGIN.getDataFolder().getAbsolutePath() + File.separatorChar, ""),
                    SchematicConfigField.class, schematicConfigField);
            schematicConfigurations.put(configurationName, schematicConfigField);
        }

        for (SchematicConfigField schematicConfigField : schematicConfigurations.values()) {
            if (!schematicConfigField.isEnabled()) continue;
            String schematicFilename = convertFromConfigurationFilename(schematicConfigField.getFilename());
            Clipboard clipboard = null;
            for (File file : clipboards.keySet())
                if (file.getName().equals(schematicFilename)) {
                    clipboard = clipboards.get(file);
                    break;
                }
            new SchematicContainer(
                    clipboard,
                    schematicFilename,
                    schematicConfigField,
                    schematicConfigField.getFilename());
        }

    }

    /**
     * Recursively collects all .schem files without loading them
     */
    private static void collectSchematicFiles(File file, List<File> schematicFiles) {
        if (file.getName().endsWith(".schem")) {
            schematicFiles.add(file);
        } else if (file.isDirectory() && file.listFiles() != null) {
            for (File iteratedFile : file.listFiles()) {
                collectSchematicFiles(iteratedFile, schematicFiles);
            }
        }
    }

    public static String convertFromSchematicFilename(String schematicFilename) {
        return schematicFilename.replace(".schem", ".yml");
    }

    public static String convertFromConfigurationFilename(String configurationFilename) {
        return configurationFilename.replace(".yml", ".schem");
    }

    public static SchematicConfigField getSchematicConfiguration(String filename) {
        return schematicConfigurations.get(filename);
    }
}
