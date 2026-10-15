package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Connected modern stems keep the old plant's death state after their fruit is removed. @author NingMeng123 */
@Mixin(AttachedStemBlock.class)
public abstract class AttachedStemLifecycleMixin extends BushBlock {
    protected AttachedStemLifecycleMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        return super.canSurvive(state,level,pos) && (!SpecialPlantGrowth.managedStem(state)||level.canSeeSky(pos)||level.getRawBrightness(pos,0)>=8);
    }
    @Inject(method="createBlockStateDefinition",at=@At("TAIL"))
    private void mite$deathState(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci) { builder.add(CubeCropBlight.DEAD); }
    @Override protected boolean isRandomlyTicking(BlockState state) { return SpecialPlantGrowth.managedStem(state)?!SpecialPlantGrowth.dead(state):super.isRandomlyTicking(state); }
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(SpecialPlantGrowth.managedStem(state)) SpecialPlantGrowth.stemTick(level,pos,state,random);
        else super.randomTick(state,level,pos,random);
    }
    @Inject(method="updateShape",at=@At("RETURN"),cancellable=true)
    private void mite$keepDeathAfterFruitRemoval(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,
            BlockPos pos,BlockPos neighborPos,CallbackInfoReturnable<BlockState> cir) {
        if (SpecialPlantGrowth.dead(state) && cir.getReturnValue().hasProperty(CubeCropBlight.DEAD))
            cir.setReturnValue(cir.getReturnValue().setValue(CubeCropBlight.DEAD,true));
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) { return SpecialPlantGrowth.managedStem(state)?List.of():super.getDrops(state,builder); }
}
