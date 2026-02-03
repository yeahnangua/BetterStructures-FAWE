package com.magmaguy.betterstructures.thirdparty;

import com.magmaguy.elitemobs.commands.ReloadCommand;
import com.magmaguy.elitemobs.mobconstructor.custombosses.RegionalBossEntity;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class EliteMobs {
    /**
     * Spawns a 1-time regional boss at the set location
     *
     * @param location Location where the boss should spawn
     * @param filename Filename of the boss, as set in the EliteMobs custombosses configuration folder
     */
    public static boolean Spawn(Location location, String filename) {
        return spawnAndReturn(location, filename) != null;
    }

    /**
     * Spawns a 1-time regional boss at the set location and returns the spawned entity.
     *
     * @param location Location where the boss should spawn
     * @param filename Filename of the boss, as set in the EliteMobs custombosses configuration folder
     * @return The spawned Entity, or null if spawn failed
     */
    public static Entity spawnAndReturn(Location location, String filename) {
        if (Bukkit.getPluginManager().getPlugin("EliteMobs") != null) {
            RegionalBossEntity regionalBossEntity = RegionalBossEntity.SpawnRegionalBoss(filename, location);
            if (regionalBossEntity == null) {
                Logger.warn("生成区域Boss失败 " + filename + "！该Boss的文件名可能与 ~/plugins/EliteMobs/custombosses/ 中的文件名不匹配");
                return null;
            } else {
                regionalBossEntity.spawn(false);
                LivingEntity livingEntity = regionalBossEntity.getLivingEntity();
                return livingEntity;
            }
        } else {
            for (Player player : Bukkit.getOnlinePlayers())
                if (player.hasPermission("betterstructures.*"))
                    Logger.sendMessage(player, "&c你的某个内容包使用了 EliteMobs 插件，&4但 EliteMobs 当前未安装在你的服务器上&c！" +
                            " &2你可以在这里下载: &9https://nightbreak.io/plugin/elitemobs/");
            return null;
        }
    }

    public static void Reload() {
        ReloadCommand.reload(Bukkit.getConsoleSender());
    }
}
