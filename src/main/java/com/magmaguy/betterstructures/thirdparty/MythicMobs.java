package com.magmaguy.betterstructures.thirdparty;

import com.magmaguy.magmacore.util.Logger;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Support for MythicMobs, configuration format is "MobID[:level]"
 *
 * @author CarmJos
 */
public class MythicMobs {

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

}
