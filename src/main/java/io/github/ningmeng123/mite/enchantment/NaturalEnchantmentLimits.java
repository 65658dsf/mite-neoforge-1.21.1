package io.github.ningmeng123.mite.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/** Generated rewards retain their own level caps independently of player enchanting. @author NingMeng123 */
public final class NaturalEnchantmentLimits {
    private NaturalEnchantmentLimits() {}

    public static int maximumLevel(Holder<Enchantment> enchantment) {
        var definition = enchantment.unwrapKey()
                .map(key -> R196EnchantmentCatalog.definition(key.location().toString())).orElse(null);
        return definition == null ? enchantment.value().getMaxLevel()
                : Math.min(enchantment.value().getMaxLevel(), definition.maxGeneratedLevel());
    }
}
