package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 nn.ac(): ordinary entities wait 900 ticks; Player's existing override remains 10. @author NingMeng123 */
@Mixin(Entity.class)
public abstract class EntityPortalCooldownMixin {
    @Inject(method = "getDimensionChangingDelay", at = @At("HEAD"), cancellable = true)
    private void mite$sourceEntityCooldown(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(900);
    }
}
