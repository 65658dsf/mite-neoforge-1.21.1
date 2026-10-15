package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** og persists forty disarmed ticks and makes canPickUpLoot false until it expires. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class SourceEnchantmentMobPickupMixin {
    @Shadow private boolean canPickUpLoot;
    @Inject(method = "canPickUpLoot", at = @At("HEAD"), cancellable = true)
    private void mite$disarmedCannotPickUp(CallbackInfoReturnable<Boolean> cir) {
        if (SourceEnchantmentCombat.disarmed((Mob)(Object)this)) cir.setReturnValue(false);
    }
    @Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
    private void mite$directPickupAlsoWaits(ItemEntity item, CallbackInfo ci) {
        if (SourceEnchantmentCombat.disarmed((Mob)(Object)this)) ci.cancel();
    }
    @ModifyExpressionValue(method = "addAdditionalSaveData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;canPickUpLoot()Z"))
    private boolean mite$saveActualPickupPreference(boolean runtimeAllowed) {
        // Saving during the temporary cooldown must not turn CanPickUpLoot off forever after reload.
        return canPickUpLoot;
    }
}
