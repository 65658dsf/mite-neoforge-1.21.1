package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Copy poison instances without mutating shared potion/food effect templates. @author NingMeng123 */
@Mixin(MobEffectInstance.class)
public interface MobEffectDurationAccessor {
    @Accessor("duration") void mite$setDuration(int duration);
    @Accessor("hiddenEffect") MobEffectInstance mite$getHiddenEffect();
    @Accessor("hiddenEffect") void mite$setHiddenEffect(MobEffectInstance hidden);
}
