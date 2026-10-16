package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.curse.CursePlayerEffects;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply movement restrictions where their inherited methods are actually declared. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class CurseLivingEntityMixin {
    @ModifyVariable(method = "setSprinting", at = @At("HEAD"), argsOnly = true)
    private boolean mite$cannotRun(boolean sprinting) {
        return sprinting && !((Object)this instanceof Player player
                && PlayerCurses.has(player, R196Curse.CANNOT_RUN, true));
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 mite$entangledInput(Vec3 input) {
        return (Object)this instanceof Player player ? CursePlayerEffects.movement(player, input) : input;
    }

    @Inject(method = "decreaseAirSupply", at = @At("HEAD"))
    private void mite$learnAirRestriction(int air, CallbackInfoReturnable<Integer> cir) {
        if ((Object)this instanceof Player player) PlayerCurses.has(player, R196Curse.CANNOT_HOLD_BREATH, true);
    }
}
