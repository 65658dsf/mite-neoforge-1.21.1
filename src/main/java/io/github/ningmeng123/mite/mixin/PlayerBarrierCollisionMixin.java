package io.github.ningmeng123.mite.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Let players jump over barriers while preserving their full collision for other entities. @author NingMeng123 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PlayerBarrierCollisionMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void mite$playerBarrierCollision(BlockGetter level, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityCollisionContext entityContext) || !(entityContext.getEntity() instanceof Player)) return;
        var block = ((BlockState)(Object)this).getBlock();
        if (!(block instanceof FenceBlock || block instanceof WallBlock || block instanceof FenceGateBlock)) return;
        VoxelShape shape = cir.getReturnValue();
        if (shape.isEmpty() || shape.max(Direction.Axis.Y) <= 1D) return;
        // Keep the native footprint and grounded obstruction; remove only the extra half-block for players.
        cir.setReturnValue(Shapes.join(shape, Shapes.create(shape.bounds().setMaxY(1D)), BooleanOp.AND));
    }
}
