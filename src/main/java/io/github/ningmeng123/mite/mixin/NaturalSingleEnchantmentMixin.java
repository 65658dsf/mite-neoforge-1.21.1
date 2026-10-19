package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.enchantment.NaturalEnchantmentLimits;
import net.minecraft.world.item.enchantment.providers.SingleEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fixed enchantment providers cannot bypass the natural equipment reward cap. @author NingMeng123 */
@Mixin(SingleEnchantment.class)
public abstract class NaturalSingleEnchantmentMixin {
    @ModifyExpressionValue(method = "enchant", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"), require = 1, allow = 1)
    private int mite$generatedLevelLimit(int maximum) {
        return Math.min(maximum, NaturalEnchantmentLimits.maximumLevel(((SingleEnchantment)(Object)this).enchantment()));
    }
}
