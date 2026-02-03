package com.magmaguy.betterstructures.menus;

import com.magmaguy.betterstructures.content.BSPackage;
import com.magmaguy.magmacore.menus.MenuButton;
import com.magmaguy.magmacore.menus.SetupMenu;
import com.magmaguy.magmacore.util.ChatColorConverter;
import com.magmaguy.magmacore.util.ItemStackGenerator;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BetterStructuresSetupMenu {
    private BetterStructuresSetupMenu() {
    }

    public static void createMenu(Player player) {
        List<BSPackage> rawBsPackages = new ArrayList<>(BSPackage.getBsPackages().values());
        List<BSPackage> bsPackages = rawBsPackages.stream()
                .sorted(Comparator.comparing(pkg ->
                        ChatColor.stripColor(ChatColorConverter.convert(pkg.getContentPackageConfigFields().getName()))))
                .collect(Collectors.toList());

        MenuButton infoButton = new MenuButton(ItemStackGenerator.generateSkullItemStack("magmaguy",
                "&2安装说明:",
                List.of(
                        "&2设置 BetterStructures 的可选/推荐内容:",
                        "&61) &f从 &9https://nightbreak.io/plugin/betterstructures &f下载内容",
                        "&62) &f将内容放入 BetterStructures 的 &2imports &f文件夹",
                        "&63) &f执行 &2/bs reload",
                        "&2完成！",
                        "&6点击获取更多信息和链接！"))) {
            @Override
            public void onClick(Player p) {
                p.closeInventory();
                Logger.sendSimpleMessage(p, "&8&l&m&o---------------------------------------------");
                Logger.sendSimpleMessage(p, "&6&lBetterStructures 安装资源:");
                Logger.sendSimpleMessage(p, "&2&lWiki 页面: &9&nhttps://magmaguy.com/wiki.html");
                Logger.sendSimpleMessage(p, "&2&l视频安装指南: &9&nhttps://www.youtube.com/watch?v=1z47lSxmyq0");
                Logger.sendSimpleMessage(p, "&2&l内容下载链接: &9&nhttps://nightbreak.io/plugin/betterstructures/");
                Logger.sendSimpleMessage(p, "&2&lDiscord 支持: &9&nhttps://discord.gg/9f5QSka");
                Logger.sendSimpleMessage(p, "&8&l&m&o---------------------------------------------");
            }
        };

        new SetupMenu(player, infoButton, bsPackages, new ArrayList<>());
    }
}
