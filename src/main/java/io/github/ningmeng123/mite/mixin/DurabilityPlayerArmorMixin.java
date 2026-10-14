package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source allocation for native fallback callers; managed player damage uses the source defense transaction. @author NingMeng123 */
@Mixin(Player.class)
public abstract class DurabilityPlayerArmorMixin {
    @Inject(method = "hurtArmor", at = @At("HEAD"), cancellable = true)
    private void mite$distributeArmorWear(DamageSource source, float amount, CallbackInfo ci) {
        if (EquipmentDurability.hurtArmor((Player)(Object)this, source, amount)) ci.cancel();
    }
}
