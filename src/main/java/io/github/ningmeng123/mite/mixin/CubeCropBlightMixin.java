package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.survival.CropLifecycle;
import io.github.ningmeng123.mite.survival.CropHarvest;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native block states persist disease, sync it, and reset to healthy on replant without a second save ledger. @author NingMeng123 */
@Mixin(CropBlock.class)
public abstract class CubeCropBlightMixin extends BushBlock {
    protected CubeCropBlightMixin(BlockBehaviour.Properties properties) { super(properties); }
    // BlockPlant excludes explosion drops; keep disease harvest overrides out of the explosion loot path.
    public boolean canDropFromExplosion(BlockState state,BlockGetter level,BlockPos pos,Explosion explosion) {
        return !CropLifecycle.managed(state) && !CubeCropBlight.blighted(state) && !CubeCropBlight.dead(state);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params) {
        return CropHarvest.drops(state,params,super.getDrops(state,params));
    }
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        if(CropLifecycle.managed(state)&&level instanceof ServerLevel server&&!state.canSurvive(level,pos)) {
            boolean nearby=server.getNearestPlayer(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,16,false)!=null;
            CropHarvest.withReason(CropHarvest.Reason.ILLEGAL,server,pos,()->server.destroyBlock(pos,nearby));
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state,side,neighbor,level,pos,neighborPos);
    }
    @Inject(method="canSurvive",at=@At("HEAD"),cancellable=true)
    private void mite$foodCropLegal(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(CropLifecycle.managed(state))cir.setReturnValue(CropLifecycle.canSurvive(state,level,pos));
    }
    @Inject(method="createBlockStateDefinition",at=@At("TAIL"))
    private void mite$cropState(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci) {
        if (!((Object)this instanceof io.github.ningmeng123.mite.block.OnionCropBlock)) builder.add(CubeCropBlight.BLIGHTED,CubeCropBlight.DEAD);
    }
    @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
    private void mite$blightTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        if (CropLifecycle.tick(state,level,pos,random)||CubeCropBlight.randomTick(level,pos,state,random)) ci.cancel();
    }
    @Inject(method="isRandomlyTicking",at=@At("HEAD"),cancellable=true)
    private void mite$sourceMatureCropTicks(BlockState state,CallbackInfoReturnable<Boolean> cir) {
        // anr ticks mature crops too: they can still become diseased or progress through existing disease.
        cir.setReturnValue(!CubeCropBlight.dead(state));
    }
    @Inject(method="isValidBonemealTarget",at=@At("HEAD"),cancellable=true)
    private void mite$blightCureTarget(LevelReader level,BlockPos pos,BlockState state,CallbackInfoReturnable<Boolean> cir) {
        if(CropLifecycle.managed(state))cir.setReturnValue(CubeCropBlight.blighted(state)&&!CubeCropBlight.dead(state));
        else if (CubeCropBlight.dead(state)) cir.setReturnValue(false);
        else if (CubeCropBlight.blighted(state)) cir.setReturnValue(true);
    }
    @Inject(method="performBonemeal",at=@At("HEAD"),cancellable=true)
    private void mite$blightCure(ServerLevel level,RandomSource random,BlockPos pos,BlockState state,CallbackInfo ci) {
        if (CropLifecycle.managed(state)||CubeCropBlight.blighted(state)||CubeCropBlight.dead(state)) { CubeCropBlight.cure(level,pos,state); ci.cancel(); }
    }
    @Inject(method="growCrops",at=@At("HEAD"),cancellable=true)
    private void mite$noInstantFoodCropGrowth(Level level,BlockPos pos,BlockState state,CallbackInfo ci) {
        if(CropLifecycle.managed(state)) {if(level instanceof ServerLevel server)CubeCropBlight.cure(server,pos,state);ci.cancel();}
    }
}
