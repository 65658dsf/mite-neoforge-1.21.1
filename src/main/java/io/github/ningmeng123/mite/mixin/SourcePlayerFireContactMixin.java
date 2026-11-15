package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** uf begins burning after ten dry contact ticks, retaining shorter custom contact delays. @author NingMeng123 */
@Mixin(Player.class)
public abstract class SourcePlayerFireContactMixin {
    @ModifyReturnValue(method = "getFireImmuneTicks", at = @At("RETURN"))
    private int mite$sourceContactDelay(int ticks) {
        return Math.min(ticks, 10);
    }
}
