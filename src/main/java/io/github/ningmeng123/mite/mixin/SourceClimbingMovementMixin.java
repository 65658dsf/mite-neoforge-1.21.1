package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.rules.EnchantmentCombatRules;
import io.github.ningmeng123.mite.survival.SourceStatusEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Restore of's ladder/vine grip and status-dependent ascent. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class SourceClimbingMovementMixin {
    @ModifyReturnValue(method = "handleOnClimbable", at = @At("RETURN"))
    private Vec3 mite$sourceHorizontalGrip(Vec3 movement) {
        LivingEntity entity = (LivingEntity) (Object) this;
        return mite$sourceClimbable(entity) ? movement.multiply(.8D, 1D, .8D) : movement;
    }

    @ModifyExpressionValue(method = "handleRelativeFrictionAndCalculateMovement",
            at = @At(value = "CONSTANT", args = "doubleValue=0.2"))
    private double mite$sourceClimbingSpeed(double speed) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!mite$sourceClimbable(entity)) return speed;
        var slow = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        var haste = entity.getEffect(MobEffects.MOVEMENT_SPEED);
        boolean inWeb = !(entity instanceof Player player && player.getAbilities().flying)
                && entity.level().getBlockStates(entity.getBoundingBox().deflate(.001D))
                        .anyMatch(state -> state.is(Blocks.COBWEB));
        float factor = EnchantmentCombatRules.movementFactorWithResistance(slow == null ? -1 : slow.getAmplifier(),
                haste == null ? -1 : haste.getAmplifier(), inWeb, R196Enchantments.freeActionResistance(entity));
        // of:849-855 retains thirty percent climbing speed at complete paralysis.
        return speed * (factor < 1F ? factor * .7F + .3F : factor);
    }

    @Unique
    private static boolean mite$sourceClimbable(LivingEntity entity) {
        var state = entity.getInBlockState();
        return SourceStatusEffects.managed(entity) && (state.is(Blocks.LADDER) || state.is(Blocks.VINE))
                && entity.onClimbable();
    }
}
