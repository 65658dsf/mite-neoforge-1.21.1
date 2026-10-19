package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.enchantment.NaturalEnchantmentLimits;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Random chest and other loot books cannot directly award player-exclusive levels. @author NingMeng123 */
@Mixin(EnchantRandomlyFunction.class)
public abstract class NaturalRandomEnchantmentMixin {
    @ModifyExpressionValue(method = "enchantItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"), require = 1, allow = 1)
    private static int mite$generatedLevelLimit(int maximum, ItemStack stack, Holder<Enchantment> enchantment, RandomSource random) {
        return Math.min(maximum, NaturalEnchantmentLimits.maximumLevel(enchantment));
    }
}
