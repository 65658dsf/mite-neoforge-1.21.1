package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 rl.a(III) always returns null; preserve its actual dormant siege without touching raids. @author NingMeng123 */
@Mixin(VillageSiege.class)
public abstract class R196VillageSiegeMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void mite$dormantReleaseSiege(ServerLevel level, boolean hostile, boolean peaceful, CallbackInfoReturnable<Integer> cir) {
        if (R196MobSpawning.manages(level)) cir.setReturnValue(0);
    }
}
