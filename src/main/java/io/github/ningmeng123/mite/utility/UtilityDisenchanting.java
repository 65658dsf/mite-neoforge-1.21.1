package io.github.ningmeng123.mite.utility;

import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** va/vc consumes the bottle entirely, removes actual enchant tags, charges no XP and causes no anvil wear. @author NingMeng123 */
public final class UtilityDisenchanting {
    private UtilityDisenchanting() {}
    public static MaterialAnvilRules.Plan plan(ItemStack first, ItemStack second) {
        if (first.isEmpty() || !second.is(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get()) || !first.isEnchanted()) return null;
        ItemStack result = first.copyWithCount(1);
        result.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return new MaterialAnvilRules.Plan(result, 1, 0, false);
    }
}
