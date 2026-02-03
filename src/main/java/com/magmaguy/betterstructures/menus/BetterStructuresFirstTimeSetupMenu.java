package com.magmaguy.betterstructures.menus;

import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.betterstructures.config.schematics.SchematicConfig;
import com.magmaguy.magmacore.menus.MenuButton;
import com.magmaguy.magmacore.util.ItemStackGenerator;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class BetterStructuresFirstTimeSetupMenu {
    public static void createMenu(Player player) {
        new com.magmaguy.magmacore.menus.FirstTimeSetupMenu(
                player,
                "&2BetterStructures",
                "&6为你的服务器添加自定义建筑！",
                createInfoItem(),
                List.of(createGettingStartedItem()));
    }

    private static MenuButton createInfoItem() {
        return new MenuButton(ItemStackGenerator.generateSkullItemStack(
                "magmaguy",
                "&2欢迎使用 BetterStructures！",
                List.of(
                        "&9点击获取完整安装指南链接！",
                        "&2你可以在下方找到基本的入门清单！"))) {
            @Override
            public void onClick(Player player) {
                player.closeInventory();
                Logger.sendSimpleMessage(player, "&8&m-----------------------------------------------------");
                Logger.sendSimpleMessage(player, "&2查看完整安装指南: &9&nhttps://nightbreak.io/plugin/betterstructures/#setup");
                Logger.sendSimpleMessage(player, "&2通过 &6/bs setup &2查看可用内容！");
                Logger.sendSimpleMessage(player, "&2支持与讨论 Discord: &9&nhttps://discord.gg/eSxvPbWYy4");
                Logger.sendSimpleMessage(player, "&8&m-----------------------------------------------------");
            }
        };
    }

    private static MenuButton createGettingStartedItem() {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit") &&
                !Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
            return new MenuButton(ItemStackGenerator.generateItemStack(
                    Material.RED_STAINED_GLASS_PANE,
                    "&cWorldEdit 未安装！",
                    List.of("&c你必须安装 WorldEdit",
                            "&c才能使用 BetterStructures！"))) {
                @Override
                public void onClick(Player player) {
                    player.closeInventory();
                    Logger.sendSimpleMessage(player, "&8&m-----------------------------------------------------");
                    Logger.sendSimpleMessage(player, "&c&l你必须安装 WorldEdit 才能使用 BetterStructures！");
                    Logger.sendSimpleMessage(player, "&c你可以在这里下载: &9&nhttps://dev.bukkit.org/projects/worldedit");
                    Logger.sendSimpleMessage(player, "&4&l请确保下载与你的 Minecraft 版本匹配的 WorldEdit 版本！");
                    Logger.sendSimpleMessage(player, "&8&m-----------------------------------------------------");
                }
            };
        }

        if (SchematicConfig.getSchematicConfigurations().isEmpty()) {
            return new MenuButton(ItemStackGenerator.generateItemStack(
                    Material.YELLOW_STAINED_GLASS_PANE,
                    "&c未安装内容！",
                    List.of("&c未检测到已安装的建筑",
                            "&cBetterStructures 没有可用内容！",
                            "&c点击获取更多信息！"))) {
                @Override
                public void onClick(Player player) {
                    player.closeInventory();
                    Logger.sendSimpleMessage(player, "&8&m-----------------------------------------------------");
                    Logger.sendSimpleMessage(player, "&c&lBetterStructures 需要下载或创建建筑才能工作！");
                    Logger.sendSimpleMessage(player, "&c你可以在这里下载建筑: &9&nhttps://nightbreak.io/plugin/betterstructures/#content");
                    Logger.sendSimpleMessage(player, "&c下载后，将其拖放到 BetterStructures 的 imports 文件夹中并执行 &4/bs reload&c。安装视频: &9&nhttps://www.youtube.com/watch?v=1z47lSxmyq0");
                    Logger.sendSimpleMessage(player, "&4你也可以自己制作内容！查看 Wiki 获取更多信息！&9&nhttps://magmaguy.com/wiki.html");
                    Logger.sendSimpleMessage(player, "&8&m-----------------------------------------------------");
                }
            };
        }

        return new MenuButton(ItemStackGenerator.generateItemStack(
                Material.GREEN_STAINED_GLASS_PANE,
                "&2看起来一切准备就绪！",
                List.of("&a点击此处完成首次设置！"))) {
            @Override
            public void onClick(Player player) {
                DefaultConfig.toggleSetupDone();
                player.closeInventory();
                Logger.sendSimpleMessage(player, "&8&m-----------------------------------------------------");
                Logger.sendSimpleMessage(player, "&2恭喜！你的服务器已准备好开始生成更好的建筑！");
                Logger.sendSimpleMessage(player, "&a要查看当前已安装的内容，请执行命令 &a/betterstructures setup");
                Logger.sendSimpleMessage(player, "&a要生成建筑，请移动到服务器中的新区块！这些必须是全新的、从未生成过的区块。BetterStructures 不会在已探索的区块中生成建筑！");
                Logger.sendSimpleMessage(player, "&a完成！祝你探索愉快！首次设置消息将不再显示。");
                Logger.sendSimpleMessage(player, "&8&m-----------------------------------------------------");
            }
        };
    }

}
