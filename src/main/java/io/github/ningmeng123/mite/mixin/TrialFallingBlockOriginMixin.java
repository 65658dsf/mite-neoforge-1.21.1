package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.github.ningmeng123.mite.worldgen.NaturalTrialFallingBlock;
import io.github.ningmeng123.mite.worldgen.TrialBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Falling template sand keeps provenance when landing, timing out, or being saved in midair. @author NingMeng123 */
@Mixin(FallingBlockEntity.class)
public abstract class TrialFallingBlockOriginMixin implements NaturalTrialFallingBlock {
    @Unique private static final String MITE_NATURAL_TRIAL_BLOCK = "mite:natural_trial_block";
    @Unique private boolean mite$naturalTrialBlock;
    @Unique private BlockPos mite$naturalLanding;
    @Shadow public boolean dropItem;

    @WrapMethod(method = "fall")
    private static FallingBlockEntity mite$carryTrialOrigin(Level level, BlockPos pos, BlockState state,
            Operation<FallingBlockEntity> original) {
        // The original fall method removes the block before returning its newly spawned entity.
        boolean natural = NaturalTrialBlocks.isNatural(level, pos);
        FallingBlockEntity falling = original.call(level, pos, state);
        if (natural) ((NaturalTrialFallingBlock)falling).mite$setNaturalTrialBlock(true);
        return falling;
    }

    @Override public boolean mite$isNaturalTrialBlock() { return mite$naturalTrialBlock; }

    @Override public void mite$setNaturalTrialBlock(boolean natural) {
        mite$naturalTrialBlock = natural;
        // Native DropItem is already serialized and covers failed landings and timeouts. The
        // separate provenance flag is required to restore the source identity of a placed block.
        if (natural) dropItem = false;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mite$beginNaturalFall(CallbackInfo ci) {
        mite$naturalLanding = null;
        if (mite$naturalTrialBlock) dropItem = false;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean mite$rememberNaturalLanding(Level level, BlockPos pos, BlockState state, int flags,
            Operation<Boolean> original) {
        if (!mite$naturalTrialBlock) return original.call(level, pos, state, flags);
        // Neighbor callbacks can immediately break a just-placed unsupported block before the
        // placement method returns. Its entity provenance already proves this transaction natural.
        try (var ignored = TrialBlockDrops.knownNaturalDestruction(level, pos)) {
            boolean placed = original.call(level, pos, state, flags);
            if (placed && level instanceof ServerLevel server && !level.getBlockState(pos).isAir()) {
                NaturalTrialBlocks.record(server.getChunkAt(pos), pos);
                mite$naturalLanding = pos.immutable();
            }
            return placed;
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void mite$restoreNaturalLandingOrigin(CallbackInfo ci) {
        FallingBlockEntity falling = (FallingBlockEntity)(Object)this;
        BlockPos landed = mite$naturalLanding;
        mite$naturalLanding = null;
        // onLand can change concrete powder into solid concrete after the placement call. Record
        // the final surviving block, rather than an intermediate state that immediately disappears.
        if (landed != null && falling.level() instanceof ServerLevel level && !level.getBlockState(landed).isAir())
            NaturalTrialBlocks.record(level.getChunkAt(landed), landed);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mite$saveNaturalFallingOrigin(CompoundTag tag, CallbackInfo ci) {
        if (mite$naturalTrialBlock) tag.putBoolean(MITE_NATURAL_TRIAL_BLOCK, true);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mite$loadNaturalFallingOrigin(CompoundTag tag, CallbackInfo ci) {
        mite$setNaturalTrialBlock(tag.getBoolean(MITE_NATURAL_TRIAL_BLOCK));
    }
}
