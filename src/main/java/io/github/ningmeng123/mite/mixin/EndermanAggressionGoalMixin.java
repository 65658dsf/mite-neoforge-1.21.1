package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.SourceEndermanAggression;
import io.github.ningmeng123.mite.entity.SourceEndermanAggression.Decision;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** tg.findPlayerToAttack selects a visible player before testing the provocation. @author NingMeng123 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLookForPlayerGoal")
public abstract class EndermanAggressionGoalMixin {
    @Shadow @Final private EnderMan enderman;
    @Shadow @Final private Predicate<LivingEntity> isAngerInducing;
    @Shadow @Final private TargetingConditions startAggroTargetConditions;
    @Shadow @Nullable private Player pendingTarget;
    @Shadow private int aggroTime;
    @Unique private TargetingConditions mite$visibleCandidates;
    @Unique private Decision mite$pendingDecision = Decision.NONE;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mite$sourceCandidateSearch(EnderMan owner, Predicate<LivingEntity> predicate, CallbackInfo ci) {
        // The source searches the nearest legal visible player, not the nearest already-provoked
        // player. Keep native combat/stealth checks but remove the gaze filter from that search.
        mite$visibleCandidates = startAggroTargetConditions.copy().range(SourceEndermanAggression.SEARCH_RANGE)
                .selector(candidate -> !enderman.hasIndirectPassenger(candidate)
                        && enderman.distanceToSqr(candidate) < SourceEndermanAggression.SEARCH_RANGE * SourceEndermanAggression.SEARCH_RANGE);
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void mite$testNearestVisiblePlayer(CallbackInfoReturnable<Boolean> cir) {
        pendingTarget = enderman.getTarget() == null
                ? enderman.level().getNearestPlayer(mite$visibleCandidates, enderman) : null;
        mite$pendingDecision = pendingTarget == null ? Decision.NONE
                : SourceEndermanAggression.decide(enderman, pendingTarget, isAngerInducing);
        if (!mite$pendingDecision.accepted()) pendingTarget = null;
        cir.setReturnValue(pendingTarget != null);
    }

    @Inject(method = "start", at = @At("TAIL"))
    private void mite$immediateItemProvocation(CallbackInfo ci) {
        if (pendingTarget != null && mite$pendingDecision.immediate()) {
            aggroTime = 0;
            // Reuse the native pending-to-target transition and its cancellable target event,
            // without another roll, a fake gaze during pursuit or a duplicate stare sound.
            ((Goal)(Object)this).tick();
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void mite$recheckPendingProvocation(CallbackInfoReturnable<Boolean> cir) {
        if (pendingTarget == null) return; // Acquired targets retain native pursuit and anger rules.
        mite$pendingDecision = mite$visibleCandidates.test(enderman, pendingTarget)
                ? SourceEndermanAggression.decide(enderman, pendingTarget, isAngerInducing) : Decision.NONE;
        if (!mite$pendingDecision.accepted()) {
            cir.setReturnValue(false);
            return;
        }
        if (mite$pendingDecision.immediate()) aggroTime = 0;
        enderman.lookAt(pendingTarget, 10F, 10F);
        cir.setReturnValue(true);
    }

    @Inject(method = "stop", at = @At("TAIL"))
    private void mite$clearPendingDecision(CallbackInfo ci) { mite$pendingDecision = Decision.NONE; }
}
