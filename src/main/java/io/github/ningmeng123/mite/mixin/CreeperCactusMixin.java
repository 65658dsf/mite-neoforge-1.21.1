package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.ai.ConspicuousCactus;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The source conspicuous-cactus counter is transient and expires before the next AI update. @author NingMeng123 */
@Mixin(Creeper.class)
public abstract class CreeperCactusMixin {
    @Inject(method="tick",at=@At("HEAD"))
    private void mite$decayCactusMemory(CallbackInfo ci) {
        Creeper self=(Creeper)(Object)this; if(!self.level().isClientSide) ConspicuousCactus.tick(self);
    }
}
