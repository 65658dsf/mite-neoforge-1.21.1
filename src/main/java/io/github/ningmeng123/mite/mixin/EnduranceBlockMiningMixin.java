package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replace the modern per-completed-block fee with the source active-mining charges. @author NingMeng123 */
@Mixin(Block.class)
public abstract class EnduranceBlockMiningMixin {
    @Redirect(method = "playerDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void mite$noSecondCompletionFee(Player player, float amount) { }
}
