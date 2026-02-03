package com.magmaguy.betterstructures.commands;

import com.magmaguy.betterstructures.util.ChunkPregenerator;
import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.command.SenderType;
import com.magmaguy.magmacore.command.arguments.IntegerCommandArgument;
import com.magmaguy.magmacore.command.arguments.ListStringCommandArgument;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public class PregenerateCommand extends AdvancedCommand {
    public PregenerateCommand() {
        super(List.of("pregenerate"));
        addArgument("center", new ListStringCommandArgument(List.of("HERE", "WORLD_CENTER", "WORLD_SPAWN"), "Center of the generation"));
        addArgument("shape", new ListStringCommandArgument(List.of("SQUARE", "CIRCLE"), "Shape of the generation"));
        addArgument("radius", new IntegerCommandArgument("Radius in blocks to generate"));
        addArgument("setWorldBorder", new ListStringCommandArgument(List.of("TRUE", "FALSE"), "Set a world border at the end?"));
        setUsage("/betterstructures pregenerate <centerType> <shape> <radiusInBlocks> <applyWorldBorder>");
        setPermission("betterstructures.*");
        setDescription("Pregenerates chunks from a center point outward in either a square or circle pattern up to the specified radius in blocks.");
        setSenderType(SenderType.PLAYER);
    }

    @Override
    public void execute(CommandData commandData) {
        String centerArg = commandData.getStringArgument("center");
        String shape = commandData.getStringArgument("shape");
        int radius = commandData.getIntegerArgument("radius");
        String setWorldBorderArg = commandData.getStringArgument("setWorldBorder");

        if (radius < 0) {
            Logger.sendMessage(commandData.getCommandSender(), "&c半径必须大于或等于 0。");
            return;
        }

        World world = commandData.getPlayerSender().getWorld();
        Location center;

        // Determine center location based on argument
        switch (centerArg.toUpperCase()) {
            case "HERE":
                center = commandData.getPlayerSender().getLocation();
                break;
            case "WORLD_CENTER":
                center = new Location(world, 0, world.getHighestBlockYAt(0, 0), 0);
                break;
            case "WORLD_SPAWN":
                center = world.getSpawnLocation();
                break;
            default:
                Logger.sendMessage(commandData.getCommandSender(), "&c无效的中心参数。请使用 HERE、WORLD_CENTER 或 WORLD_SPAWN。");
                return;
        }

        boolean setWorldBorder = "TRUE".equalsIgnoreCase(setWorldBorderArg);

        if (!"SQUARE".equalsIgnoreCase(shape) && !"CIRCLE".equalsIgnoreCase(shape)) {
            Logger.sendMessage(commandData.getCommandSender(), "&c无效的形状。请使用 SQUARE 或 CIRCLE。");
            return;
        }

        int radiusInBlocks = radius;
        int radiusInChunks = (int) Math.ceil(radiusInBlocks / 16.0);

        Logger.sendMessage(commandData.getCommandSender(), "&2开始区块预生成，形状: " + shape + ", center: " + centerArg + ", radius: " + radiusInBlocks + " blocks (" + radiusInChunks + " chunks)");
        if (setWorldBorder) {
            Logger.sendMessage(commandData.getCommandSender(), "&2世界边界将设置为与生成区域匹配。");
        }
        Logger.sendMessage(commandData.getCommandSender(), "&7进度将每 30 秒在控制台报告一次。");
        Logger.sendMessage(commandData.getCommandSender(), "&7如需取消请使用 &2/betterstructures cancelPregenerate &7命令。");

        ChunkPregenerator pregenerator = new ChunkPregenerator(world, center, shape, radiusInBlocks, radiusInChunks, setWorldBorder);
        pregenerator.start();
    }
}

