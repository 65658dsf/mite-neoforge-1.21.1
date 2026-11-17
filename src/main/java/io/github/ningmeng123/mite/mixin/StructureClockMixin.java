package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StrongholdRecovery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Commands and sleeping publish gates immediately instead of waiting for a later tick. @author NingMeng123 */
@Mixin(ServerLevel.class)
public abstract class StructureClockMixin {
    @Inject(method = "setDayTime", at = @At("RETURN"))
    private void mite$publishTime(long time, CallbackInfo ci) {
        R196StructureGeneration.refresh((ServerLevel)(Object)this);
    }
    @Inject(method = "setDefaultSpawnPos", at = @At("RETURN"))
    private void mite$publishSpawn(BlockPos pos, float angle, CallbackInfo ci) {
        R196StructureGeneration.refresh((ServerLevel)(Object)this);
    }

    @Inject(method = "onStructureStartsAvailable", at = @At("RETURN"))
    private void mite$observeStrongholdCandidate(ChunkAccess chunk, CallbackInfo ci) {
        ServerLevel level = (ServerLevel)(Object)this;
        // The native method queues its presence-cache update first. Keep notification behind it.
        StrongholdRecovery.observed(level, chunk.getPos());
    }
}
