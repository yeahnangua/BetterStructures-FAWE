package com.magmaguy.betterstructures.commands;

import com.magmaguy.betterstructures.config.treasures.TreasureConfig;
import com.magmaguy.betterstructures.config.treasures.TreasureConfigFields;
import com.magmaguy.betterstructures.util.ItemStackSerialization;
import com.magmaguy.magmacore.command.AdvancedCommand;
import com.magmaguy.magmacore.command.CommandData;
import com.magmaguy.magmacore.command.arguments.IntegerCommandArgument;
import com.magmaguy.magmacore.command.arguments.ListStringCommandArgument;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LootifyCommand extends AdvancedCommand {
    public LootifyCommand() {
        super(List.of("lootify"));
        ArrayList<String> treasures = new ArrayList<>(TreasureConfig.getTreasureConfigurations().keySet());
        addArgument("generator", new ListStringCommandArgument(treasures,"<treasures>"));
        addArgument("rarity", new ListStringCommandArgument("<rarity>"));
        addArgument("minAmount", new IntegerCommandArgument("<minAmount>"));
        addArgument("maxAmount", new IntegerCommandArgument("<maxAmount>"));
        addArgument("weight", new IntegerCommandArgument("<weight>"));
        setPermission("betterstructures.*");
        setUsage("/betterstructures lootify <generator> <rarity> <minAmount> <maxAmount> <weight>");
        setDescription("Adds a held item to the loot settings of a generator");
    }

    @Override
    public void execute(CommandData commandData) {
        lootify(commandData.getStringArgument("generator"),
                commandData.getStringArgument("rarity"),
                commandData.getStringArgument("minAmount"),
                commandData.getStringArgument("maxAmount"),
                commandData.getStringArgument("weight"),
                commandData.getPlayerSender());
    }
    private void lootify(String generator, String rarity, String minAmount, String maxAmount, String weight, Player player) {
        TreasureConfigFields treasureConfigFields = TreasureConfig.getConfigFields(generator);
        if (treasureConfigFields == null) {
            player.sendMessage("[BetterStructures] 无效的生成器！请重试。");
            return;
        }
        //Verify loot table
        if (treasureConfigFields.getRawLoot().get(rarity) == null) {
            player.sendMessage("[BetterStructures] 无效的稀有度！请重试。");
            return;
        }
        int minAmountInt;
        try {
            minAmountInt = Integer.parseInt(minAmount);
        } catch (Exception exception) {
            player.sendMessage("[BetterStructures] 无效的最小数量！请重试。");
            return;
        }
        if (minAmountInt < 1) {
            player.sendMessage("[BetterStructures] 最小数量不应小于 1！此值将不会保存。");
            return;
        }
        int maxAmountInt;
        try {
            maxAmountInt = Integer.parseInt(maxAmount);
        } catch (Exception exception) {
            player.sendMessage("[BetterStructures] 无效的最大数量！请重试。");
            return;
        }
        if (maxAmountInt > 64) {
            player.sendMessage("[BetterStructures] 最大数量不应超过 64！如需多组请创建多个条目。此值将不会保存。");
            return;
        }
        double weightDouble;
        try {
            weightDouble = Double.parseDouble(weight);
        } catch (Exception exception) {
            player.sendMessage("[BetterStructures] 无效的权重！请重试。");
            return;
        }
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (itemStack == null || itemStack.getType().isAir()) {
            player.sendMessage("[BetterStructures] 你需要手持物品才能注册！此值将不会保存。");
            return;
        }
        String info;
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName())
            info = itemStack.getItemMeta().getDisplayName().replace(" ", "_");
        else if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasItemName())
            info = itemStack.getItemMeta().getItemName();
        else
            info = itemStack.getType().toString();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("serialized", ItemStackSerialization.deserializeItem(itemStack));
        configMap.put("amount", minAmount +"-"+maxAmount);
        configMap.put("weight", weightDouble);
        configMap.put("info", info);
        treasureConfigFields.addChestEntry(configMap, rarity, player);
    }
}
