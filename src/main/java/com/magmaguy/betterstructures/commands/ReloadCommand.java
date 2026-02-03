package com.magmaguy.betterstructures.commands;

import com.magmaguy.betterstructures.MetadataHandler;
import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.util.Logger;

import java.util.List;

public class ReloadCommand extends AdvancedCommand {
    public ReloadCommand() {
        super(List.of("reload"));
        setPermission("betterstructures.*");
        setUsage("/betterstructures reload");
        setDescription("Reloads the plugin.");
    }

    @Override
    public void execute(CommandData commandData) {
        MetadataHandler.PLUGIN.onDisable();
        MetadataHandler.PLUGIN.onLoad();
        MetadataHandler.PLUGIN.onEnable();
        Logger.sendMessage(commandData.getCommandSender(), "已尝试重载。可能无法完全生效，如有问题请重启服务器！");
    }
}
