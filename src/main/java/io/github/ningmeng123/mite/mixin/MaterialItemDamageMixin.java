package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.rules.MaterialContentRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply source fire/lava protection without inventing a combined FIRE_RESISTANT component. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class MaterialItemDamageMixin {
    @Inject(method = "canBeHurtBy", at = @At("HEAD"), cancellable = true)
    private void mite$materialProtection(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack)(Object)this;
        if (stack.isEmpty()) return;
        var profile = MaterialContentRules.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (profile == null) return;
        if (source.is(DamageTypes.LAVA) ? !profile.lava() : source.is(DamageTypeTags.IS_FIRE) && !profile.fire())
            cir.setReturnValue(false);
    }
}
