package com.magmaguy.betterstructures.config.modules;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.modules.ModulesContainer;
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

public class ModulesConfig extends CustomConfig {
    @Getter
    private static final HashMap<String, ModulesConfigFields> moduleConfigurations = new HashMap<>();

    public ModulesConfig() {
        super("modules", ModulesConfigFields.class);
        moduleConfigurations.clear();

        ModulesContainer.initializeSpecialModules();

        File modulesFile = new File(MetadataHandler.PLUGIN.getDataFolder().getAbsolutePath()+ File.separatorChar + "modules");
        if (!modulesFile.exists()) modulesFile.mkdir();

        // Step 1: Collect all .schem files (fast, synchronous)
        List<File> moduleFiles = new ArrayList<>();
        File modulesDir = new File(MetadataHandler.PLUGIN.getDataFolder().getAbsolutePath() + File.separatorChar + "modules");
        if (modulesDir.exists() && modulesDir.listFiles() != null) {
            for (File file : modulesDir.listFiles()) {
                collectSchematicFiles(file, moduleFiles);
            }
        }

        // Step 2: Parallel loading
        Logger.info("Loading " + moduleFiles.size() + " modules...");
        long startTime = System.currentTimeMillis();

        Map<File, Clipboard> clipboards = new ConcurrentHashMap<>();
        moduleFiles.parallelStream().forEach(file -> {
            Clipboard clipboard = Schematic.load(file);
            if (clipboard != null) {
                clipboards.put(file, clipboard);
            }
        });

        Logger.info("Loaded " + clipboards.size() + " modules in " + (System.currentTimeMillis() - startTime) + "ms");

        for (String key : super.getCustomConfigFieldsHashMap().keySet())
            moduleConfigurations.put(key, (ModulesConfigFields) super.getCustomConfigFieldsHashMap().get(key));

        for (File file : clipboards.keySet()) {
            String configurationName = convertFromSchematicFilename(file.getName());
            ModulesConfigFields moduleConfigField = new ModulesConfigFields(configurationName, true);
            new CustomConfig(file.getParent().replace(
                    MetadataHandler.PLUGIN.getDataFolder().getAbsolutePath() + File.separatorChar, ""),
                    ModulesConfigFields.class, moduleConfigField);
            moduleConfigurations.put(configurationName, moduleConfigField);
        }

        moduleConfigurations.values().forEach(ModulesConfigFields::validateClones);

        for (ModulesConfigFields modulesConfigFields : moduleConfigurations.values()) {
            if (!modulesConfigFields.isEnabled()) continue;
            String schematicFilename = convertFromConfigurationFilename(modulesConfigFields.getFilename());
            Clipboard clipboard = null;
            for (File file : clipboards.keySet())
                if (file.getName().equals(schematicFilename)) {
                    clipboard = clipboards.get(file);
                    break;
                }
            ModulesContainer.initializeModulesContainer(
                    clipboard,
                    schematicFilename,
                    modulesConfigFields,
                    modulesConfigFields.getFilename());
        }

        ModulesContainer.postInitializeModulesContainer();

    }

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

    public static ModulesConfigFields getModuleConfiguration(String filename) {
        return moduleConfigurations.get(filename);
    }
}
