package com.magmaguy.betterstructures.content;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.betterstructures.config.contentpackages.ContentPackageConfigFields;
import com.magmaguy.betterstructures.config.schematics.SchematicConfig;
import com.magmaguy.betterstructures.config.schematics.SchematicConfigField;
import com.magmaguy.magmacore.menus.ContentPackage;
import com.magmaguy.magmacore.util.ItemStackGenerator;
import com.magmaguy.magmacore.util.Logger;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BSPackage extends ContentPackage {

    @Getter
    private static final Map<String, BSPackage> bsPackages = new HashMap<>();
    private static final String schematicFolder = MetadataHandler.PLUGIN.getDataFolder().getAbsolutePath() + File.separatorChar + "schematics" + File.separatorChar;
    private static final String modulesFolder = MetadataHandler.PLUGIN.getDataFolder().getAbsolutePath() + File.separatorChar + "modules" + File.separatorChar;
    @Getter
    private final ContentPackageConfigFields contentPackageConfigFields;

    public BSPackage(ContentPackageConfigFields contentPackageConfigFields) {
        super();
        this.contentPackageConfigFields = contentPackageConfigFields;
        bsPackages.put(contentPackageConfigFields.getFilename(), this);
    }

    public static void shutdown() {
        bsPackages.clear();
    }

    @Override
    protected void doInstall(Player player) {
        player.closeInventory();
        File folder = getSpecificSchematicFolder();
        if (!folder.exists()) {
            Logger.sendMessage(player, "未找到目录 " + folder.getAbsolutePath());
            return;
        }

        for (File file : folder.listFiles()) {
            if (!file.getName().endsWith(".yml")) continue;
            SchematicConfigField schematicConfigField = SchematicConfig.getSchematicConfiguration(file.getName());
            schematicConfigField.toggleEnabled(true);
        }

        contentPackageConfigFields.setEnabledAndSave(true);

        MetadataHandler.PLUGIN.onDisable();
        MetadataHandler.PLUGIN.onLoad();
        MetadataHandler.PLUGIN.onEnable();
        Logger.sendMessage(player, " 已尝试重载。可能无法完全生效，如有问题请重启服务器！");
        Logger.sendMessage(player, "已安装 " + contentPackageConfigFields.getName());
    }

    @Override
    public void doUninstall(Player player) {
        player.closeInventory();
        File folder = getSpecificSchematicFolder();
        if (!folder.exists()) {
            Logger.sendMessage(player, "未找到目录 " + folder.getAbsolutePath());
            return;
        }

        for (File file : folder.listFiles()) {
            if (!file.getName().endsWith(".yml")) continue;
            SchematicConfigField schematicConfigField = SchematicConfig.getSchematicConfiguration(file.getName());
            schematicConfigField.toggleEnabled(false);
        }

        contentPackageConfigFields.setEnabledAndSave(false);

        MetadataHandler.PLUGIN.onDisable();
        MetadataHandler.PLUGIN.onLoad();
        MetadataHandler.PLUGIN.onEnable();
        Logger.sendMessage(player, " 已尝试重载。可能无法完全生效，如有问题请重启服务器！");

        Logger.sendMessage(player, "已卸载 " + contentPackageConfigFields.getName());
    }

    @Override
    public void doDownload(Player player) {
        player.closeInventory();
        player.sendMessage("----------------------------------------------------");
        Logger.sendMessage(player, "&4请在此下载 &9 " + contentPackageConfigFields.getDownloadLink());
        player.sendMessage("----------------------------------------------------");
    }

    @Override
    protected ItemStack getInstalledItemStack() {
        List<String> lore = new ArrayList<>(contentPackageConfigFields.getDescription());
        lore.addAll(List.of("内容已安装！", "点击卸载！"));
        return ItemStackGenerator.generateItemStack(Material.GREEN_STAINED_GLASS_PANE, contentPackageConfigFields.getName(), lore);
    }

    @Override
    protected ItemStack getPartiallyInstalledItemStack() {
        List<String> lore = new ArrayList<>(contentPackageConfigFields.getDescription());
        lore.addAll(List.of(
                "内容部分安装！",
                "这可能是因为你还没有下载完全，",
                "或者因为某些元素已被手动禁用。",
                "点击下载！"));
        return ItemStackGenerator.generateItemStack(Material.ORANGE_STAINED_GLASS_PANE, contentPackageConfigFields.getName(), lore);
    }

    @Override
    protected ItemStack getNotInstalledItemStack() {
        List<String> lore = new ArrayList<>(contentPackageConfigFields.getDescription());
        lore.addAll(List.of("内容未安装！", "点击安装！"));
        return ItemStackGenerator.generateItemStack(Material.YELLOW_STAINED_GLASS_PANE, contentPackageConfigFields.getName(), lore);
    }

    @Override
    protected ItemStack getNotDownloadedItemStack() {
        List<String> lore = new ArrayList<>(contentPackageConfigFields.getDescription());
        lore.addAll(List.of("内容未下载！", "点击获取下载链接！"));
        return ItemStackGenerator.generateItemStack(Material.RED_STAINED_GLASS_PANE, contentPackageConfigFields.getName(), lore);
    }

    @Override
    protected ContentState getContentState() {
        if (!isInstalled()) return ContentState.NOT_DOWNLOADED;
        if (contentPackageConfigFields.isEnabled()) return ContentState.INSTALLED;
        return ContentState.NOT_INSTALLED;
    }

    private File getSpecificSchematicFolder() {
        return new File(schematicFolder + contentPackageConfigFields.getFolderName());
    }

    private boolean isInstalled() {
        if (contentPackageConfigFields.getContentPackageType().equals(ContentPackageConfigFields.ContentPackageType.MODULAR)){
            return new File(modulesFolder + contentPackageConfigFields.getFolderName()).exists();
        } else {
            return new File(schematicFolder + contentPackageConfigFields.getFolderName()).exists();
        }
    }
}