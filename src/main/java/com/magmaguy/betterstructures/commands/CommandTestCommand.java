package com.magmaguy.betterstructures.commands;

import com.magmaguy.betterstructures.mobtracking.MobTrackingManager;
import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.command.SenderType;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.entity.Player;

import java.util.List;

public class CommandTestCommand extends AdvancedCommand {
    public CommandTestCommand() {
        super(List.of("commandtest"));
        setPermission("betterstructures.*");
        setUsage("/betterstructures commandtest");
        setDescription("Test the structure cleared commands at your current location.");
        setSenderType(SenderType.PLAYER);
    }

    @Override
    public void execute(CommandData commandData) {
        Player player = (Player) commandData.getCommandSender();
        Logger.sendMessage(player, "&a正在测试你所在位置的建筑清除命令...");
        MobTrackingManager.getInstance().testCommands(player);
        Logger.sendMessage(player, "&a测试完成！请查看控制台获取详情。");
    }
}
