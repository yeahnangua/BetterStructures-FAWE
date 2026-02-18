package com.magmaguy.betterstructures.thirdparty;

import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.magmacore.util.Logger;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.mobs.entities.MythicEntityType;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Support for MythicMobs, configuration format is "MobID[:level]"
 *
 * @author CarmJos
 */
public class MythicMobs {

    // Cache: Bukkit EntityType -> List of MM mob IDs that share the same base type (excluding bosses)
    private static Map<EntityType, List<String>> typeMappingCache = new HashMap<>();
    private static boolean cacheBuilt = false;

    public static boolean Spawn(Location location, String filename) {
        return spawnAndReturn(location, filename) != null;
    }

    /**
     * Spawns a MythicMob at the set location and returns the spawned entity.
     *
     * @param location Location where the mob should spawn
     * @param filename MobID[:level] format string
     * @return The spawned Entity, or null if spawn failed
     */
    public static Entity spawnAndReturn(Location location, String filename) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("betterstructures.*")) {
                    Logger.sendMessage(player, "&c你的某个内容包使用了 MythicMobs 插件，&4但 MythicMobs 当前未安装在你的服务器上&c！&2你可以在这里下载: &9https://www.spigotmc.org/resources/%E2%9A%94-mythicmobs-free-version-%E2%96%BAthe-1-custom-mob-creator%E2%97%84.5702/");
                }
            }
            return null;
        }

        String[] args = filename.split(":");

        String mobId = args[0];
        MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob(mobId).orElse(null);
        if (mob == null) {
            Logger.warn("生成区域Boss失败！未找到 MythicMob ID: '" + mobId + "'");
            Logger.warn("  - 原始文件名参数: " + filename);
            Logger.warn("  - 位置: " + location.getWorld().getName() + " at " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
            Logger.warn("  - 请确保在 ~/plugins/MythicMobs/Mobs/ 中存在具有此 ID 的生物");
            return null;
        }

        double level;
        try {
            level = Double.parseDouble(args[1]);
        } catch (Exception e) {
            Logger.warn("解析生物等级失败 " + filename + "！");
            return null;
        }

        ActiveMob activeMob = mob.spawn(BukkitAdapter.adapt(location), Math.max(1, level));
        if (activeMob != null && activeMob.getEntity() != null) {
            return activeMob.getEntity().getBukkitEntity();
        }
        return null;
    }

    /**
     * Convert MythicEntityType enum name to Bukkit EntityType.
     * Most names match directly. A few legacy names need mapping.
     */
    private static EntityType toBukkitEntityType(MythicEntityType mythicType) {
        if (mythicType == null) return null;
        String name = mythicType.name();

        // Handle known mismatches between MythicMobs and Bukkit naming
        switch (name) {
            case "MUSHROOM_COW": name = "MOOSHROOM"; break;
            case "SNOWMAN": name = "SNOW_GOLEM"; break;
            case "PIG_ZOMBIE": name = "ZOMBIFIED_PIGLIN"; break;
            case "PIG_ZOMBIE_VILLAGER": return null; // No direct Bukkit equivalent
            case "BABY_PIG_ZOMBIE": name = "ZOMBIFIED_PIGLIN"; break;
            case "CUSTOM": return null; // Custom entity types can't be mapped
            default: break;
        }

        // Skip BABY_ variants (they map to the same EntityType as the adult)
        if (name.startsWith("BABY_")) {
            name = name.substring(5); // Remove "BABY_" prefix
        }

        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null; // Unknown entity type, skip
        }
    }

    /**
     * Builds the type mapping cache by querying all registered MythicMobs
     * and grouping them by their base Bukkit EntityType.
     * Mobs in the mythicBossList are excluded from this mapping.
     */
    public static void buildTypeMapping() {
        typeMappingCache.clear();
        cacheBuilt = false;

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            Logger.warn("MythicMobs 覆盖已启用，但 MythicMobs 插件未安装！");
            return;
        }

        Set<String> bossList = new HashSet<>(DefaultConfig.getMythicBossList());
        Set<String> blacklist = new HashSet<>(DefaultConfig.getMythicMobBlacklist());

        try {
            Collection<MythicMob> allMobs =
                    MythicBukkit.inst().getMobManager().getMobTypes();

            Map<EntityType, List<String>> mapping = new HashMap<>();

            for (MythicMob mob : allMobs) {
                String mobId = mob.getInternalName();

                // Skip bosses (they go in the boss list, not the vanilla replacement pool)
                if (bossList.contains(mobId)) continue;
                // Skip globally blacklisted mobs
                if (blacklist.contains(mobId)) continue;

                MythicEntityType mythicType = mob.getEntityType();
                EntityType bukkitType = toBukkitEntityType(mythicType);

                if (bukkitType != null) {
                    mapping.computeIfAbsent(bukkitType, k -> new ArrayList<>()).add(mobId);
                }
            }

            typeMappingCache = mapping;
            cacheBuilt = true;

            Logger.info("MythicMobs 类型映射缓存已构建: " + mapping.size() + " 个原版类型已映射");
            for (Map.Entry<EntityType, List<String>> entry : mapping.entrySet()) {
                Logger.info("  " + entry.getKey().name() + " → " + entry.getValue());
            }
        } catch (Exception e) {
            Logger.warn("构建 MythicMobs 类型映射缓存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get a random MM mob ID that matches the given vanilla EntityType.
     * Returns null if no mapping exists.
     */
    public static String getRandomMobByType(EntityType entityType) {
        if (!cacheBuilt) return null;
        // If whitelist is enabled, only allow listed entity types
        if (DefaultConfig.isEntityTypeWhitelistEnabled()) {
            List<String> whitelist = DefaultConfig.getEntityTypeWhitelist();
            if (whitelist == null || !whitelist.contains(entityType.name())) return null;
        }
        List<String> candidates = typeMappingCache.get(entityType);
        if (candidates == null || candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /**
     * Get a random boss from the mythicBossList config.
     * Returns null if the list is empty.
     */
    public static String getRandomBoss() {
        List<String> bossList = DefaultConfig.getMythicBossList();
        if (bossList == null || bossList.isEmpty()) return null;
        Set<String> blacklist = new HashSet<>(DefaultConfig.getMythicMobBlacklist());
        List<String> filtered = new ArrayList<>();
        for (String boss : bossList) {
            if (!blacklist.contains(boss)) filtered.add(boss);
        }
        if (filtered.isEmpty()) return null;
        return filtered.get(ThreadLocalRandom.current().nextInt(filtered.size()));
    }

    /**
     * Check if the type mapping cache has been built and is available.
     */
    public static boolean isCacheReady() {
        return cacheBuilt;
    }

    /**
     * Check if MythicMobs override is effectively enabled
     * (config enabled + MythicMobs installed + cache built).
     */
    public static boolean isOverrideActive() {
        return DefaultConfig.isMythicMobsOverrideEnabled()
                && Bukkit.getPluginManager().getPlugin("MythicMobs") != null
                && cacheBuilt;
    }

    /**
     * Clear the cache (called on disable/reload).
     */
    public static void clearCache() {
        typeMappingCache.clear();
        cacheBuilt = false;
    }

}
