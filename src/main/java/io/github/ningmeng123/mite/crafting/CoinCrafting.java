package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.item.R196CoinItem;
import io.github.ningmeng123.mite.rules.UtilityRules;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

/** Source wd/ItemCoin exchanges are transaction inputs, never right-click XP grants. @author NingMeng123 */
public final class CoinCrafting {
    private CoinCrafting() {}
    public static int cost(ItemStack result) {
        return result.getItem() instanceof R196CoinItem coin ? UtilityRules.totalValue(coin.experienceValue(), result.getCount()) : 0;
    }
    public static int refund(Container grid, int consumption) {
        if (consumption < 0) throw new IllegalArgumentException("Negative coin consumption");
        long refund = 0;
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack input = grid.getItem(slot);
            if (input.getItem() instanceof R196CoinItem coin) refund += (long)coin.experienceValue() * Math.min(consumption, input.getCount());
        }
        return (int)Math.min(refund, Integer.MAX_VALUE);
    }
    public static int refund(CraftingInput input) {
        long refund = 0;
        for (ItemStack stack : input.items()) if (stack.getItem() instanceof R196CoinItem coin) refund += coin.experienceValue();
        return (int)Math.min(refund, Integer.MAX_VALUE);
    }
    public static boolean automatedAllowed(CraftingInput input, ItemStack result) {
        return cost(result) == 0 && refund(input) == 0;
    }
}
