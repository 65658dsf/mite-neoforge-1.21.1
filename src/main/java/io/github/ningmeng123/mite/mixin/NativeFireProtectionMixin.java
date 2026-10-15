package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.enchantment.NativeProtectionEnchantments;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Change ignition duration without shortening the already-running fire on every tick. @author NingMeng123 */
@Mixin(Entity.class)
public abstract class NativeFireProtectionMixin {
    @ModifyVariable(method = "igniteForTicks", at = @At("HEAD"), argsOnly = true)
    private int mite$sourceFireProtection(int requestedTicks) {
        return NativeProtectionEnchantments.ignitionTicks((Entity)(Object)this, requestedTicks);
    }
}
