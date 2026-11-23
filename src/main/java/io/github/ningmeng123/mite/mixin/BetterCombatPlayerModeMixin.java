package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Let Better Combat honor MITE's preference alongside its own server flags. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.bettercombat.api.CombatFlags", remap = false)
public abstract class BetterCombatPlayerModeMixin {
    @ModifyReturnValue(method = "isAttackDisabled", at = @At("RETURN"))
    private static boolean mite$vanillaMode(boolean disabled, Player player) {
        return disabled || BetterCombatPlayerMode.isVanilla(player);
    }
}
