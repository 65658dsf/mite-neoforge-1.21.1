package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Severe slowness reduces buoyancy on both logical sides, including squid contact. @author NingMeng123 */
@Mixin(Player.class)
public abstract class PlayerSluggishSwimmingMixin {
    @Inject(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"), require = 2)
    private void mite$severeSlownessSinksBeforeWaterMovement(Vec3 input, CallbackInfo ci) {
        Player player = (Player)(Object)this;
        if (!player.isInWater() || player.isPassenger() || player.isCreative() || player.isSpectator()
                || player.getAbilities().flying || player.isNoGravity()) return;
        var slow = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow == null) return;
        var speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        double modifier = -.2D * (slow.getAmplifier() + 1) + (speed == null ? 0D : .2D * (speed.getAmplifier() + 1));
        if (modifier >= -.5D) return;
        // Run after swimming's look-direction lift and before the actual move, so holding jump
        // or looking up cannot cancel the drag. Preserve faster existing downward movement.
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Math.min(motion.y, -.08D), motion.z);
    }
}
