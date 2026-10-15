package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.NativeArachnidEvents;
import io.github.ningmeng123.mite.entity.R196Spider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only native arachnid target/leap/bright-room attack goals; keep modern armadillo avoidance and lifecycle. @author NingMeng123 */
@Mixin(Spider.class)
public abstract class NativeArachnidMixin extends Monster {
    protected NativeArachnidMixin(EntityType<? extends Monster> type,Level level) { super(type,level); }
    @Inject(method="registerGoals",at=@At("RETURN"))
    private void mite$sourceArachnidGoals(CallbackInfo ci) {
        NativeArachnidEvents.installGoals((Spider)(Object)this,goalSelector,targetSelector);
    }

    /**
     * on.bl writes the source attribute to forward input, while of.bg supplies fixed .1 acceleration.
     * Modern Mob.setSpeed writes BOTH acceleration and input; copying 1/.8 into both makes movement
     * roughly ten/eight times too strong. Keep saved source attributes and translate the controller output.
     */
    @Override public void setSpeed(float sourceInput) {
        Spider spider = (Spider)(Object)this;
        if (NativeArachnidEvents.managed(spider) || spider instanceof R196Spider) {
            super.setSpeed(sourceInput == 0F ? 0F : .1F);
            setZza(sourceInput);
        } else super.setSpeed(sourceInput);
    }
}
