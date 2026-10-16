package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** uf.g caps every air reset at ninety ticks while cursed. @author NingMeng123 */
@Mixin(Entity.class)
public abstract class CurseEntityMixin {
    @ModifyVariable(method = "setAirSupply", at = @At("HEAD"), argsOnly = true)
    private int mite$curseAirLimit(int air) {
        return (Object)this instanceof Player player && air > 90
                && PlayerCurses.has(player, R196Curse.CANNOT_HOLD_BREATH) ? 90 : air;
    }
}
