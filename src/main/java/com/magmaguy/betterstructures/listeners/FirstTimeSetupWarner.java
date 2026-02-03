package com.magmaguy.betterstructures.listeners;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class FirstTimeSetupWarner implements Listener {
    @EventHandler
    public void onPlayerLogin(PlayerJoinEvent event) {
        if (DefaultConfig.isSetupDone()) return;
        if (!event.getPlayer().hasPermission("betterstructures.*")) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!event.getPlayer().isOnline()) return;
                Logger.sendSimpleMessage(event.getPlayer(), "&8&m----------------------------------------------------");
                Logger.sendMessage(event.getPlayer(), "&f初始设置消息:");
                Logger.sendSimpleMessage(event.getPlayer(), "&7欢迎使用 BetterStructures！" +
                        " &c&l看起来你还没有设置 BetterStructures！&2要安装 BetterStructures，请执行 &a/betterstructures initialize &2！");
                Logger.sendSimpleMessage(event.getPlayer(), "&7你可以在此获取支持 &9&nhttps://discord.gg/9f5QSka");
                Logger.sendSimpleMessage(event.getPlayer(), "&c在 /betterstructures setup 中选择一个选项以永久关闭此消息！");
                Logger.sendSimpleMessage(event.getPlayer(), "&8&m----------------------------------------------------");
            }
        }.runTaskLater(MetadataHandler.PLUGIN, 20 * 10);
    }
}
