package com.magmaguy.betterstructures.commands;

import com.magmaguy.betterstructures.structurelocation.StructureLocationData;
import com.magmaguy.betterstructures.structurelocation.StructureLocationManager;
import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.command.SenderType;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class StructureInfoCommand extends AdvancedCommand {
    public StructureInfoCommand() {
        super(List.of("info"));
        setUsage("/bs info");
        setPermission("betterstructures.*");
        setDescription("显示你当前所在结构的详细信息。");
        setSenderType(SenderType.PLAYER);
    }

    @Override
    public void execute(CommandData commandData) {
        Player player = commandData.getPlayerSender();
        Location playerLocation = player.getLocation();
        String worldName = playerLocation.getWorld().getName();

        Collection<StructureLocationData> structures =
                StructureLocationManager.getInstance().getStructuresInWorld(worldName);

        StructureLocationData found = null;
        for (StructureLocationData data : structures) {
            if (data.isWithinBounds(playerLocation)) {
                found = data;
                break;
            }
        }

        if (found == null) {
            Logger.sendMessage(player, "&c你当前不在任何已记录的结构范围内。");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Logger.sendMessage(player, "&a&l===== 结构信息 =====");
        Logger.sendMessage(player, "&6建筑模板: &f" + found.schematicName());
        Logger.sendMessage(player, "&6结构类型: &f" + found.structureType().name());
        Logger.sendMessage(player, "&6世界: &f" + found.getWorldName());
        Logger.sendMessage(player, "&6坐标: &f" + found.getFormattedCoordinates());
        Logger.sendMessage(player, "&6范围: &fX=" + found.getRadiusX()
                + " Y=" + found.getRadiusY()
                + " Z=" + found.getRadiusZ());
        Logger.sendMessage(player, "&6已清除: &f" + (found.isCleared() ? "是" : "否"));
        if (found.isCleared() && found.getClearedTimestamp() > 0) {
            Logger.sendMessage(player, "&6清除时间: &f" + sdf.format(new Date(found.getClearedTimestamp())));
        }
        Logger.sendMessage(player, "&6重生次数: &f" + found.getRespawnCount());
        Logger.sendMessage(player, "&6Boss 结构: &f" + (found.isBossStructure() ? "是" : "否"));
        Logger.sendMessage(player, "&6怪物配置数: &f" + found.getTotalMobCount());
        Logger.sendMessage(player, "&6存活怪物数: &f" + found.getAliveMobCount());
        Logger.sendMessage(player, "&6创建时间: &f" + sdf.format(new Date(found.getCreatedTimestamp())));
    }
}
