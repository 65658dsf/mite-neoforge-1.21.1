package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.MonsterHunting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Side heads select and shoot independently of ordinary target goals. @author NingMeng123 */
@Mixin(WitherBoss.class)
public abstract class WitherPreyTargetMixin {
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void mite$clearExpiredSideHeadPrey(CallbackInfo ci) {
        WitherBoss wither = (WitherBoss)(Object)this;
        for (int head = 1; head <= 2; head++) {
            var entity = wither.level().getEntity(wither.getAlternativeTarget(head));
            if (entity instanceof LivingEntity target && !MonsterHunting.mayAttackPrey(wither, target))
                wither.setAlternativeTarget(head, 0);
        }
    }

    @WrapOperation(method = "customServerAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getNearbyEntities(Ljava/lang/Class;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<LivingEntity> mite$bloodNightSideHeadCandidates(Level level, Class<LivingEntity> type,
            TargetingConditions conditions, LivingEntity owner, AABB bounds, Operation<List<LivingEntity>> original) {
        WitherBoss wither = (WitherBoss)(Object)this;
        var candidates = new ArrayList<>(original.call(level, type, conditions, owner, bounds));
        candidates.removeIf(target -> !MonsterHunting.mayAttackPrey(wither, target));
        return candidates;
    }

    @Inject(method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void mite$bloodNightSideHeadAttack(int head, LivingEntity target, CallbackInfo ci) {
        WitherBoss wither = (WitherBoss)(Object)this;
        if (head > 0 && !MonsterHunting.mayAttackPrey(wither, target)) ci.cancel();
    }
}
