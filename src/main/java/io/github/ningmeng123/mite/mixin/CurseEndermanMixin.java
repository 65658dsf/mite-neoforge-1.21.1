package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** tg.f checks curse aggression while acquiring a target, before pumpkin/gaze exemptions. @author NingMeng123 */
@Mixin(EnderMan.class)
public abstract class CurseEndermanMixin {
    @Inject(method = "isLookingAtMe", at = @At("HEAD"), cancellable = true)
    private void mite$endermenEnemy(Player player, CallbackInfoReturnable<Boolean> cir) {
        EnderMan enderman = (EnderMan)(Object)this;
        // Modern isLookingAtMe is also queried by freeze/teleport goals during pursuit.
        // R196 only calls tg.f while finding a player, never to fake a gaze during pursuit.
        if (enderman.getTarget() != null) return;
        if (enderman.getRandom().nextInt(3) == 0 && PlayerCurses.has(player, R196Curse.ENDERMEN_ENEMY, true))
            cir.setReturnValue(true);
    }
}
