package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.NativeArachnidEvents;
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
}
