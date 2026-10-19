package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native tables and anvils read the configured cap, including other mods' registry entries. @author NingMeng123 */
@Mixin(Enchantment.class)
public abstract class ConfiguredEnchantmentMixin {
    @Inject(method = "getMaxLevel", at = @At("RETURN"), cancellable = true)
    private void mite$configuredMaximum(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ConfiguredEnchantments.maximumLevel((Enchantment)(Object)this, cir.getReturnValue()));
    }

    @Inject(method = {"canEnchant", "isPrimaryItem"}, at = @At("HEAD"), cancellable = true)
    private void mite$disabledEnchantment(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!ConfiguredEnchantments.enabled((Enchantment)(Object)this)) cir.setReturnValue(false);
    }
}
