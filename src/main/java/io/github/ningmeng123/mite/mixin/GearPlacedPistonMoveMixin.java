package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Every piston destination inherits exactly its source block's player provenance. @author NingMeng123 */
@Mixin(PistonBaseBlock.class)
public abstract class GearPlacedPistonMoveMixin {
    @WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;getToPush()Ljava/util/List;"))
    private List<BlockPos> mite$rememberPlayerSources(PistonStructureResolver resolver, Operation<List<BlockPos>> original,
            Level level, BlockPos piston, Direction facing, boolean extending,
            @Share("miteGearPlacedDestinations") LocalRef<Set<BlockPos>> destinations) {
        List<BlockPos> sources = original.call(resolver);
        destinations.set(PlayerPlacedBlocks.movingDestinations(level, sources, extending ? facing : facing.getOpposite()));
        return sources;
    }

    @WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean mite$movePlayerSources(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original,
            @Share("miteGearPlacedDestinations") LocalRef<Set<BlockPos>> destinations) {
        boolean changed = original.call(level, pos, state, flags);
        if (changed) PlayerPlacedBlocks.movingDestination(level, pos, destinations.get());
        return changed;
    }
}
