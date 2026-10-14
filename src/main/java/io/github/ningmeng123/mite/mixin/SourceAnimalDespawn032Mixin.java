package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.animal.SourceAnimalDespawn032;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only confirmed source animal identities replace native distance-only despawning. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class SourceAnimalDespawn032Mixin {
    @Inject(method="checkDespawn",at=@At("HEAD"),cancellable=true)
    private void mite$sourceAnimalDespawn(CallbackInfo ci) {
        Mob mob=(Mob)(Object)this;
        if(mob.level().isClientSide || !SourceAnimalDespawn032.managed(mob)) return;
        if(!net.neoforged.neoforge.event.EventHooks.checkMobDespawn(mob)) SourceAnimalDespawn032.tryDespawn(mob);
        ci.cancel();
    }
}
