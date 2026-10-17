package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.SourceIceItemMelting;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ss melts dropped ice/snow separately from block melting and ordinary fire damage. @author NingMeng123 */
@Mixin(ItemEntity.class)
public abstract class SourceIceItemMeltingMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mite$sourceTemperatureMelting(CallbackInfo ci) {
        SourceIceItemMelting.itemTick((ItemEntity)(Object)this);
    }
}
