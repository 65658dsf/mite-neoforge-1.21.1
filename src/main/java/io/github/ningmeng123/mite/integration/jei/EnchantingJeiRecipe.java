package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Explicit JEI display for the menu-only golden apple conversion. @author NingMeng123 */
public record EnchantingJeiRecipe(ItemStack input, ItemStack output, int experienceCost) {
    public EnchantingJeiRecipe {
        input = input.copy();
        output = output.copy();
    }

    public static EnchantingJeiRecipe goldenApple() {
        ItemStack input = new ItemStack(Items.GOLDEN_APPLE);
        int energy = R196EnchantmentTable.energy(input, 0, false, 0, 0F);
        return new EnchantingJeiRecipe(input, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
                R196EnchantmentTable.experienceCost(energy));
    }
}
