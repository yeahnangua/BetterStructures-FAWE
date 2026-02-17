package com.magmaguy.betterstructures.schematics;

import com.google.common.collect.ArrayListMultimap;
import com.magmaguy.betterstructures.chests.ChestContents;
import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.betterstructures.config.generators.GeneratorConfigFields;
import com.magmaguy.betterstructures.config.schematics.SchematicConfigField;
import com.magmaguy.betterstructures.config.treasures.TreasureConfig;
import com.magmaguy.betterstructures.config.treasures.TreasureConfigFields;
import com.magmaguy.betterstructures.util.WorldEditUtils;
import com.magmaguy.magmacore.util.Logger;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class SchematicContainer {
    @Getter
    private static final ArrayListMultimap<GeneratorConfigFields.StructureType, SchematicContainer> schematics = ArrayListMultimap.create();
    @Getter
    private final Clipboard clipboard;
    @Getter
    private final SchematicConfigField schematicConfigField;
    @Getter
    private final GeneratorConfigFields generatorConfigFields;
    @Getter
    private final String clipboardFilename;
    @Getter
    private final String configFilename;
    @Getter
    private final List<Vector> chestLocations = new ArrayList<>();
    @Getter
    private final HashMap<Vector, EntityType> vanillaSpawns = new HashMap<>();
    @Getter
    private final HashMap<Vector, String> eliteMobsSpawns = new HashMap<>();
    @Getter
    private final HashMap<Vector, String> mythicMobsSpawns = new HashMap<>(); // carm - Support for MythicMobs
    @Getter
    List<AbstractBlock> abstractBlocks = new ArrayList<>();
    @Getter
    private ChestContents chestContents = null;
    @Getter
    private boolean valid = true;

    public SchematicContainer(Clipboard clipboard, String clipboardFilename, SchematicConfigField schematicConfigField, String configFilename) {
        this.clipboard = clipboard;
        this.clipboardFilename = clipboardFilename;
        this.schematicConfigField = schematicConfigField;
        this.configFilename = configFilename;
        generatorConfigFields = schematicConfigField.getGeneratorConfigFields();
        if (generatorConfigFields == null) {
            Logger.warn("为建筑模板 " + schematicConfigField.getFilename() + " 分配生成器失败！这意味着该建筑将不会出现在世界中。");
            return;
        }
        for (int x = 0; x <= clipboard.getDimensions().x(); x++)
            for (int y = 0; y <= clipboard.getDimensions().y(); y++)
                for (int z = 0; z <= clipboard.getDimensions().z(); z++) {
                    BlockVector3 translatedLocation = BlockVector3.at(x, y, z).add(clipboard.getMinimumPoint());
                    BlockState weBlockState = clipboard.getBlock(translatedLocation);
                    Material minecraftMaterial = BukkitAdapter.adapt(weBlockState.getBlockType());
                    if (minecraftMaterial == null) continue;
                    //register chest location
                    if (minecraftMaterial.equals(Material.CHEST) ||
                            minecraftMaterial.equals(Material.TRAPPED_CHEST) ||
                            minecraftMaterial.equals(Material.SHULKER_BOX)) {
                        chestLocations.add(new Vector(x, y, z));
                    }
                    if (minecraftMaterial.equals(Material.ACACIA_SIGN) ||
                            minecraftMaterial.equals(Material.ACACIA_WALL_SIGN) ||
                            minecraftMaterial.equals(Material.SPRUCE_SIGN) ||
                            minecraftMaterial.equals(Material.SPRUCE_WALL_SIGN) ||
                            minecraftMaterial.equals(Material.BIRCH_SIGN) ||
                            minecraftMaterial.equals(Material.BIRCH_WALL_SIGN) ||
                            minecraftMaterial.equals(Material.CRIMSON_SIGN) ||
                            minecraftMaterial.equals(Material.CRIMSON_WALL_SIGN) ||
                            minecraftMaterial.equals(Material.DARK_OAK_SIGN) ||
                            minecraftMaterial.equals(Material.DARK_OAK_WALL_SIGN) ||
                            minecraftMaterial.equals(Material.JUNGLE_SIGN) ||
                            minecraftMaterial.equals(Material.JUNGLE_WALL_SIGN) ||
                            minecraftMaterial.equals(Material.OAK_SIGN) ||
                            minecraftMaterial.equals(Material.OAK_WALL_SIGN) ||
                            minecraftMaterial.equals(Material.WARPED_SIGN) ||
                            minecraftMaterial.equals(Material.WARPED_WALL_SIGN)) {
                        BaseBlock baseBlock = clipboard.getFullBlock(translatedLocation);
                        //For future reference, I don't know how to get the data in any other way than parsing the string. Sorry!
                        String line1 = WorldEditUtils.getLine(baseBlock, 1);

                        //Case for spawning a vanilla mob
                        if (line1.toLowerCase(Locale.ROOT).contains("[spawn]")) {
                            String line2 = WorldEditUtils.getLine(baseBlock, 2).toUpperCase(Locale.ROOT).replaceAll("\"", "");
                            EntityType entityType;
                            try {
                                entityType = EntityType.valueOf(line2);
                            } catch (Exception ex) {
                                if (line2.equalsIgnoreCase("WITHER_CRYSTAL"))
                                    entityType = EntityType.END_CRYSTAL;
                                else {
                                    Logger.warn("无法确定告示牌的实体类型！条目为 " + line2 + "，建筑模板 " + clipboardFilename + " ！请输入有效的实体类型来修复！");
                                    continue;
                                }
                            }
                            vanillaSpawns.put(new Vector(x, y, z), entityType);
                        } else if (line1.toLowerCase(Locale.ROOT).contains("[elitemobs]")) {
                            if (Bukkit.getPluginManager().getPlugin("EliteMobs") == null) {
                                Bukkit.getLogger().warning("[BetterStructures] " + configFilename + " 使用了 EliteMobs Boss，但你未安装 EliteMobs！BetterStructures 不需要 EliteMobs 即可运行，但如果你想要酷炫的 EliteMobs Boss 战斗，请在此安装: https://nightbreak.io/plugin/elitemobs/");
                                Bukkit.getLogger().warning("[BetterStructures] 由于未安装 EliteMobs，" + configFilename + " 将不会被使用。");
                                valid = false;
                                return;
                            }
                            String filename = "";
                            for (int i = 2; i < 5; i++) filename += WorldEditUtils.getLine(baseBlock, i);
                            eliteMobsSpawns.put(new Vector(x, y, z), filename);
                        } else if (line1.toLowerCase(Locale.ROOT).contains("[mythicmobs]")) { // carm start - Support MythicMobs
                            if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
                                Bukkit.getLogger().warning("[BetterStructures] " + configFilename + " 使用了 MythicMobs Boss，但你未安装 MythicMobs！BetterStructures 不需要 MythicMobs 即可运行，但如果你想要 MythicMobs Boss 战斗，请安装 MythicMobs。");
                                Bukkit.getLogger().warning("[BetterStructures] 由于未安装 MythicMobs，" + configFilename + " 将不会被使用。");
                                valid = false;
                                return;
                            }
                            String mob = WorldEditUtils.getLine(baseBlock, 2);
                            String level = WorldEditUtils.getLine(baseBlock, 3);
                            mythicMobsSpawns.put(new Vector(x, y, z), mob + (level.isEmpty() ? "" : ":" + level));
                        } // carm end - Support MythicMobs
                    }
                }
        chestContents = generatorConfigFields.getChestContents();
        if (schematicConfigField.getTreasureFile() != null && !schematicConfigField.getTreasureFile().isEmpty()) {
            TreasureConfigFields treasureConfigFields = TreasureConfig.getConfigFields(schematicConfigField.getFilename());
            if (treasureConfigFields == null) {
                Logger.warn("获取宝藏配置失败 " + schematicConfigField.getTreasureFile());
                return;
            }
            chestContents = schematicConfigField.getChestContents();
        }
        if (valid)
            synchronized (schematics) {
                generatorConfigFields.getStructureTypes().forEach(structureType -> schematics.put(structureType, this));
            }
    }

    public static void shutdown() {
        schematics.clear();
    }

    public boolean isValidEnvironment(World.Environment environment) {
        return generatorConfigFields.getValidWorldEnvironments() == null ||
                generatorConfigFields.getValidWorldEnvironments().isEmpty() ||
                generatorConfigFields.getValidWorldEnvironments().contains(environment);
    }

    /**
     * Validates if a biome is in the list of valid biomes, handling both newer interface-based
     * biomes and older class-based biomes.
     *
     * @param biome The biome to validate
     * @return True if the biome is valid, false otherwise
     */
    public boolean isValidBiome(Object biomeObj) {
        if (generatorConfigFields.getValidBiomesNamespaces() == null) return true;
        if (generatorConfigFields.getValidBiomesNamespaces().isEmpty()) return true;

        // Extract biome identifier based on version
        String biomeString = getBiomeIdentifier(biomeObj);

        for (String validBiome : generatorConfigFields.getValidBiomesNamespaces()) {
            if (biomeString.equals(validBiome)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets a string identifier for a biome that works across different Minecraft versions.
     * Handles both interface (newer) and class (older) implementation of Biome.
     *
     * @param biomeObj The biome to get an identifier for (passed as Object to avoid class casting issues)
     * @return A string identifier for the biome
     */
    private String getBiomeIdentifier(Object biomeObj) {
        // First, try to use reflection to safely handle both class and interface versions
        try {
            // Try to get the getKey method (newer versions)
            java.lang.reflect.Method getKeyMethod = biomeObj.getClass().getMethod("getKey");
            Object key = getKeyMethod.invoke(biomeObj);

            // Get namespace and key from the NamespacedKey
            java.lang.reflect.Method getNamespaceMethod = key.getClass().getMethod("getNamespace");
            java.lang.reflect.Method getKeyNameMethod = key.getClass().getMethod("getKey");

            String namespace = (String) getNamespaceMethod.invoke(key);
            String keyName = (String) getKeyNameMethod.invoke(key);

            return namespace + ":" + keyName;
        } catch (Exception e) {
            // Older versions may use different methods or be enums
            try {
                // If it's an enum, try to get the name
                if (biomeObj.getClass().isEnum()) {
                    String enumName = ((Enum<?>) biomeObj).name().toLowerCase();
                    return "minecraft:" + enumName;
                }

                // Try name() method which might exist in some implementations
                java.lang.reflect.Method nameMethod = biomeObj.getClass().getMethod("name");
                String name = (String) nameMethod.invoke(biomeObj);
                return "minecraft:" + name.toLowerCase();
            } catch (Exception e2) {
                // Last resort - use toString and clean it up
                String fallback = biomeObj.toString();

                // Try to extract the name from common toString() formats
                if (fallback.contains("{") && fallback.contains("}")) {
                    // Handle patterns like "Biome{name=DESERT}"
                    int startIndex = fallback.indexOf("=") + 1;
                    int endIndex = fallback.indexOf("}", startIndex);
                    if (startIndex > 0 && endIndex > startIndex) {
                        fallback = fallback.substring(startIndex, endIndex);
                    }
                } else if (fallback.contains(".")) {
                    // Handle patterns like "ENUM.DESERT"
                    fallback = fallback.substring(fallback.lastIndexOf(".") + 1);
                }

                // Clean up and return with default namespace
                return "minecraft:" + fallback.toLowerCase().trim();
            }
        }
    }

    public boolean isValidYLevel(int yLevel) {
        return generatorConfigFields.getLowestYLevel() <= yLevel && generatorConfigFields.getHighestYLevel() >= yLevel;
    }

    public boolean isValidWorld(String worldName) {
        return generatorConfigFields.getValidWorlds() == null ||
                generatorConfigFields.getValidWorlds().isEmpty() ||
                generatorConfigFields.getValidWorlds().contains(worldName);
    }

    /**
     * Determines if this schematic represents a boss structure.
     * A structure is a boss structure if:
     * - It has EliteMobs spawns ([elitemobs] signs), OR
     * - Any of its MythicMobs spawns reference a mob in the mythicBossList config
     */
    public boolean isBossStructure() {
        // Has EliteMobs bosses = boss structure
        if (!eliteMobsSpawns.isEmpty()) {
            return true;
        }

        // Check if any MythicMobs spawns are in the boss list
        List<String> bossList = DefaultConfig.getMythicBossList();
        if (bossList != null && !bossList.isEmpty()) {
            for (String mmSpawn : mythicMobsSpawns.values()) {
                // MM spawn format is "MobID[:level]", extract just the MobID
                String mobId = mmSpawn.split(":")[0];
                if (bossList.contains(mobId)) {
                    return true;
                }
            }
        }

        return false;
    }
}
