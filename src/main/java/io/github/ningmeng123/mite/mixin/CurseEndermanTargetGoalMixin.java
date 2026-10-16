package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source tg.f curse aggression precedes sight; ordinary modern aggression retains its sight check. @author NingMeng123 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLookForPlayerGoal")
public abstract class CurseEndermanTargetGoalMixin {
    @Shadow @Final private EnderMan enderman;
    @Shadow @Final private Predicate<LivingEntity> isAngerInducing;
    @Shadow @Final private TargetingConditions startAggroTargetConditions;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mite$curseBeforeOuterSightCheck(EnderMan enderman, Predicate<LivingEntity> predicate, CallbackInfo ci) {
        // The original predicate performs the curse roll exactly once. Recheck only state here,
        // so the outer TargetingConditions cannot cancel a successful source curse roll by LoS.
        startAggroTargetConditions.ignoreLineOfSight().selector(entity -> isAngerInducing.test(entity)
                && (entity instanceof Player player && PlayerCurses.has(player, R196Curse.ENDERMEN_ENEMY)
                        || this.enderman.getSensing().hasLineOfSight(entity)));
    }
}
