package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.worldgen.NaturalShriekerOres;
import io.github.ningmeng123.mite.worldgen.ShriekerOreRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Successful bounded generation writes are the only source of natural reward provenance. @author NingMeng123 */
@Mixin(WorldGenRegion.class)
public abstract class NaturalShriekerPlacementMixin implements ShriekerOreRegion {
    @Shadow @Final private ChunkStep generatingStep;
    @Override public int mite$oreWriteRadius() { return generatingStep.blockStateWriteRadius(); }
    @WrapMethod(method = "setBlock")
    private boolean mite$recordNaturalShrieker(BlockPos pos, BlockState state, int flags, int recursion, Operation<Boolean> original) {
        boolean placed = original.call(pos, state, flags, recursion);
        if (placed) NaturalShriekerOres.placed((WorldGenRegion)(Object)this, pos, state);
        return placed;
    }
}
