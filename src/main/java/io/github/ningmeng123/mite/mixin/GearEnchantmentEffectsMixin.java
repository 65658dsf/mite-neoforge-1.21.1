package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.progression.EnchantmentEffectBridge;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Effects retain their registry identity and receive a stable, source-aware runtime view. @author NingMeng123 */
@Mixin(Enchantment.class)
public abstract class GearEnchantmentEffectsMixin {
    @Inject(method = "getEffects", at = @At("RETURN"), cancellable = true)
    private <T> void mite$separateSources(DataComponentType<List<T>> type, CallbackInfoReturnable<List<T>> cir) {
        cir.setReturnValue(EnchantmentEffectBridge.effects((Enchantment)(Object)this, type, cir.getReturnValue()));
    }
}
