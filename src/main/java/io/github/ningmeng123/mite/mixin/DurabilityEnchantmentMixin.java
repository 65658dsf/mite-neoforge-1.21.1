package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the Unbreaking effect for managed equipment. @author NingMeng123 */
@Mixin(Enchantment.class)
public abstract class DurabilityEnchantmentMixin {
    @Inject(method = "modifyDurabilityChange", at = @At("HEAD"), cancellable = true)
    private void mite$r196Unbreaking(ServerLevel level, int enchantmentLevel, ItemStack stack,
            MutableFloat amount, CallbackInfo ci) {
        if (EquipmentDurability.isManaged(stack) && level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.UNBREAKING).value() == (Object)this) {
            amount.setValue(io.github.ningmeng123.mite.enchantment.R196Enchantments.unbreakingDamage(stack, level, amount.intValue(), enchantmentLevel));
            ci.cancel();
        }
    }

}
