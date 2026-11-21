package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.client.PunchyWeaponTransitions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Preserve authored weapon paths during interrupted combos. @author NingMeng123 */
@Pseudo
@Mixin(targets = "punchy.client.state.CustomAnimationResolver", remap = false)
public abstract class PunchyWeaponTransitionMixin {
    // Punchy 3.2b delegates the pose transition to original$playConfiguredClip.
    @WrapOperation(method = {
            "playConfiguredClip(Lpunchy/client/animation/data/AnimationClip;Lpunchy/config/PunchyTuningConfig$CustomAnimationEntry;FZZLpunchy/client/state/CustomAnimationResolver$ClipTransition;)V",
            "original$playConfiguredClip(Lpunchy/client/animation/data/AnimationClip;Lpunchy/config/PunchyTuningConfig$CustomAnimationEntry;FZZLpunchy/client/state/CustomAnimationResolver$ClipTransition;)V"},
            at = @At(value = "INVOKE",
            target = "Lpunchy/client/animation/PoseHandler;playAtTimeRebasedFromCurrentReturnToOrigin(Lpunchy/client/animation/data/AnimationClip;FZF)V"), require = 1)
    private static void mite$crossfadeAuthoredAttack(@Coerce Object handler, @Coerce Object clip,
            float speedMultiplier, boolean holdOnLastFrame, float startTime, Operation<Void> original) {
        if (!PunchyWeaponTransitions.playCrossfade(handler, clip, speedMultiplier, holdOnLastFrame))
            original.call(handler, clip, speedMultiplier, holdOnLastFrame, startTime);
    }
}
