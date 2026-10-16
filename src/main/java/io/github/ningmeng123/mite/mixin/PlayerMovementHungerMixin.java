package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** uf.be retains the jump statistic without the modern additional jump fee. @author NingMeng123 */
@Mixin(Player.class)
public abstract class PlayerMovementHungerMixin {
    @Redirect(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"), require = 2)
    private void mite$noModernJumpFee(Player player, float amount) {}
}
