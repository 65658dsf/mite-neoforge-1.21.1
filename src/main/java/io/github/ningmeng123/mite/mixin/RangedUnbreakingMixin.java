package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
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

/** ye applies the same source per-point unbreaking rolls to rods without granting them quality. @author NingMeng123 */
@Mixin(Enchantment.class)
public abstract class RangedUnbreakingMixin {
    @Inject(method = "modifyDurabilityChange", at = @At("HEAD"), cancellable = true)
    private void mite$rodUnbreaking(ServerLevel level, int enchantmentLevel, ItemStack stack, MutableFloat amount, CallbackInfo ci) {
        if ((MiteRangedItems.rodMaterial(stack) != null || MiteRangedItems.carrotRodMaterial(stack) != null)
                && level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING).value() == (Object)this) {
            amount.setValue(EquipmentDurabilityRules.afterUnbreaking(amount.intValue(), enchantmentLevel, level.random::nextFloat));
            ci.cancel();
        }
    }
}
