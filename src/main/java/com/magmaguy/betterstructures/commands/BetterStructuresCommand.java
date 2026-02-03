package com.magmaguy.betterstructures.commands;

import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.command.SenderType;
import com.magmaguy.magmacore.util.Logger;

import java.util.ArrayList;

public class BetterStructuresCommand extends AdvancedCommand {
    public BetterStructuresCommand() {
        super(new ArrayList<>());
        setUsage("/bs");
        setPermission("betterstructures.*");
        setDescription("A basic help command for BetterStructures.");
        setSenderType(SenderType.PLAYER);
    }

    @Override
    public void execute(CommandData commandData) {
        Logger.sendMessage(commandData.getCommandSender(), "BetterStructures 是一个为你的 Minecraft 世界添加随机建筑的插件！");
        Logger.sendMessage(commandData.getCommandSender(), "你可以通过 &2/betterstructures setup &f命令查看已有建筑并下载新建筑。");
        Logger.sendMessage(commandData.getCommandSender(), "安装内容包后，建筑将自动生成在新生成的区块中，无需执行任何命令。");
        Logger.sendMessage(commandData.getCommandSender(), "默认情况下，管理员会收到新建筑生成的通知，直到他们关闭这些消息。");
    }
}
