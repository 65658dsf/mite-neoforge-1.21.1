package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.CollapsePhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notify neighboring soil only after the real cancellable player-removal transaction succeeds. @author NingMeng123 */
@Mixin(ServerPlayerGameMode.class)
public abstract class CollapsePlayerMiningMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "removeBlock", at = @At("RETURN"))
    private void mite$mineUnderminingSupport(BlockPos pos, BlockState removed, boolean canHarvest, CallbackInfoReturnable<Boolean> cir) {
        // jw.b excludes arg (the source standing/wall/redstone torch family).
        if (!cir.getReturnValueZ() || removed.getBlock() instanceof BaseTorchBlock) return;
        CollapsePhysics.undermine(level, pos.above(), player);
        for (Direction direction : Direction.Plane.HORIZONTAL)
            CollapsePhysics.undermine(level, pos.relative(direction), player);
    }
}
