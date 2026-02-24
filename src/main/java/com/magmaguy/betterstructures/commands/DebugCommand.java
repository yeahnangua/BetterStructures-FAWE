package com.magmaguy.betterstructures.commands;

import com.magmaguy.betterstructures.config.DefaultConfig;
import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.util.Logger;

import java.util.List;

public class DebugCommand extends AdvancedCommand {
    public DebugCommand() {
        super(List.of("debug"));
        setPermission("betterstructures.*");
        setUsage("/betterstructures debug");
        setDescription("Toggles BetterStructures developer debug messages.");
    }

    @Override
    public void execute(CommandData commandData) {
        boolean enabled = DefaultConfig.toggleDeveloperMessages();
        Logger.sendMessage(commandData.getCommandSender(),
                "&2Developer message 已" + (enabled ? "&a开启" : "&c关闭") + "&2，无需重载。");
    }
}
