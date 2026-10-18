package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.progression.GearProgressionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retain the original tool through durability, automatic restocking and actual removal. @author NingMeng123 */
@Mixin(ServerPlayerGameMode.class)
public abstract class GearProgressionMiningMixin {
    @Shadow @Final protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock")
    private boolean mite$gearMiningTransaction(BlockPos pos, Operation<Boolean> original) {
        return GearProgressionEvents.mining(player, pos, () -> original.call(pos));
    }

    @Inject(method = "removeBlock", at = @At("RETURN"))
    private void mite$completedGearMining(BlockPos pos, BlockState state, boolean canHarvest,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) GearProgressionEvents.mined(player, pos, canHarvest);
    }
}
