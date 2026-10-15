package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceBreakPortalGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** rf's closed wooden portal route includes wooden gates and trapdoors as well as doors. @author NingMeng123 */
@Mixin(WalkNodeEvaluator.class)
public abstract class SourceWoodenPortalPathMixin extends NodeEvaluator {
    @Inject(method="getPathType",at=@At("RETURN"),cancellable=true)
    private void mite$woodenPortal(PathfindingContext context,int x,int y,int z,CallbackInfoReturnable<PathType> cir) {
        if(!(mob instanceof PathfinderMob creature) || !R196MonsterAi.managed(creature) || !canOpenDoors() || !canPassDoors()) return;
        var state=context.level().getBlockState(new BlockPos(x,y,z));
        if(SourceBreakPortalGoal.woodenPortal(state)
                && (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)
                || state.getBlock() instanceof TrapDoorBlock && !state.getValue(TrapDoorBlock.OPEN)))
            cir.setReturnValue(PathType.WALKABLE_DOOR);
    }
}
