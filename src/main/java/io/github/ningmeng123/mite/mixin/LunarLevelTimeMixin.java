package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.rules.LunarCalendar;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelTimeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Align actual engine and client phases with source spawning without changing raw brightness semantics. @author NingMeng123 */
@Mixin(LevelTimeAccess.class)
public interface LunarLevelTimeMixin {
    @Inject(method="getMoonPhase",at=@At("HEAD"),cancellable=true)
    private void mite$sourceMoonPhase(CallbackInfoReturnable<Integer> cir) {
        if((Object)this instanceof Level level && level.dimension().equals(Level.OVERWORLD))
            cir.setReturnValue(LunarCalendar.state(level.getDayTime(),true).phase());
    }
    @Inject(method="getMoonBrightness",at=@At("HEAD"),cancellable=true)
    private void mite$sourcePhaseBrightness(CallbackInfoReturnable<Float> cir) {
        if((Object)this instanceof Level level && level.dimension().equals(Level.OVERWORLD))
            cir.setReturnValue(LunarCalendar.phaseBrightness(LunarCalendar.state(level.getDayTime(),true)));
    }
}
