package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import io.github.ningmeng123.mite.survival.CropHarvest;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Melon/pumpkin stems retain source drought death, fruit exhaustion and seed recovery. @author NingMeng123 */
@Mixin(StemBlock.class)
public abstract class StemLifecycleMixin extends BushBlock {
    protected StemLifecycleMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        return super.canSurvive(state,level,pos) && (!SpecialPlantGrowth.managedStem(state)||level.canSeeSky(pos)||level.getRawBrightness(pos,0)>=8);
    }
    @Inject(method="createBlockStateDefinition",at=@At("TAIL"))
    private void mite$deathState(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci) { builder.add(CubeCropBlight.DEAD); }
    @Override protected boolean isRandomlyTicking(BlockState state) { return SpecialPlantGrowth.managedStem(state)?!SpecialPlantGrowth.dead(state):super.isRandomlyTicking(state); }
    @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
    private void mite$sourceStemTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        if(SpecialPlantGrowth.managedStem(state)) { SpecialPlantGrowth.stemTick(level,pos,state,random); ci.cancel(); }
    }
    @Inject(method="isValidBonemealTarget",at=@At("HEAD"),cancellable=true)
    private void mite$inactiveBoneMealTarget(LevelReader level,BlockPos pos,BlockState state,CallbackInfoReturnable<Boolean> cir) {
        // aqq.fertilizeStem exists, but xl.tryFertilize never dispatches to aqq in R196.
        if(SpecialPlantGrowth.managedStem(state)) cir.setReturnValue(false);
    }
    @Inject(method="performBonemeal",at=@At("HEAD"),cancellable=true)
    private void mite$noBoneMealGrowth(ServerLevel level,RandomSource random,BlockPos pos,BlockState state,CallbackInfo ci) {
        if(SpecialPlantGrowth.managedStem(state)) ci.cancel();
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {
        if(!SpecialPlantGrowth.managedStem(state)) return super.getDrops(state,builder);
        if(CropHarvest.reason(builder)==CropHarvest.Reason.SNOW && state.getValue(StemBlock.AGE)==0)
            return List.of(new ItemStack(state.is(Blocks.MELON_STEM)?Items.MELON_SEEDS:Items.PUMPKIN_SEEDS));
        if (SpecialPlantGrowth.dead(state) || state.getValue(StemBlock.AGE)!=0
                || CropHarvest.reason(builder)!=CropHarvest.Reason.PLAYER
                || builder.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS)!=null) return List.of();
        return List.of(new ItemStack(state.is(Blocks.MELON_STEM)?Items.MELON_SEEDS:Items.PUMPKIN_SEEDS));
    }
    @Override public boolean canDropFromExplosion(BlockState state,BlockGetter level,BlockPos pos,Explosion explosion) { return !SpecialPlantGrowth.managedStem(state)&&super.canDropFromExplosion(state,level,pos,explosion); }
}
