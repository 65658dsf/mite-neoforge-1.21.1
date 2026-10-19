package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.enchantment.NaturalEnchantmentLimits;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native librarian book rolls keep the same reward cap as source librarian offers. @author NingMeng123 */
@Mixin(VillagerTrades.EnchantBookForEmeralds.class)
public abstract class NaturalVillagerEnchantmentMixin {
    @ModifyExpressionValue(method = "getOffer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"), require = 1, allow = 1)
    private int mite$generatedLevelLimit(int maximum, @Local Holder<Enchantment> enchantment) {
        return Math.min(maximum, NaturalEnchantmentLimits.maximumLevel(enchantment));
    }
}
