package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native block states persist disease, sync it, and reset to healthy on replant without a second save ledger. @author NingMeng123 */
@Mixin(CropBlock.class)
public abstract class CubeCropBlightMixin {
    // BlockPlant excludes explosion drops; keep disease harvest overrides out of the explosion loot path.
    public boolean canDropFromExplosion(BlockState state,BlockGetter level,BlockPos pos,Explosion explosion) {
        return !CubeCropBlight.blighted(state) && !CubeCropBlight.dead(state);
    }
    @Inject(method="createBlockStateDefinition",at=@At("TAIL"))
    private void mite$cropState(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci) {
        if (!((Object)this instanceof io.github.ningmeng123.mite.block.OnionCropBlock)) builder.add(CubeCropBlight.BLIGHTED,CubeCropBlight.DEAD);
    }
    @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
    private void mite$blightTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        if (CubeCropBlight.randomTick(level,pos,state,random)) ci.cancel();
    }
    @Inject(method="isRandomlyTicking",at=@At("HEAD"),cancellable=true)
    private void mite$sourceMatureCropTicks(BlockState state,CallbackInfoReturnable<Boolean> cir) {
        // anr ticks mature crops too: they can still become diseased or progress through existing disease.
        cir.setReturnValue(!CubeCropBlight.dead(state));
    }
    @Inject(method="isValidBonemealTarget",at=@At("HEAD"),cancellable=true)
    private void mite$blightCureTarget(LevelReader level,BlockPos pos,BlockState state,CallbackInfoReturnable<Boolean> cir) {
        if (CubeCropBlight.dead(state)) cir.setReturnValue(false);
        else if (CubeCropBlight.blighted(state)) cir.setReturnValue(true);
    }
    @Inject(method="performBonemeal",at=@At("HEAD"),cancellable=true)
    private void mite$blightCure(ServerLevel level,RandomSource random,BlockPos pos,BlockState state,CallbackInfo ci) {
        if (CubeCropBlight.blighted(state) || CubeCropBlight.dead(state)) { CubeCropBlight.cure(level,pos,state); ci.cancel(); }
    }
}
