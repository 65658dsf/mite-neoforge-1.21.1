package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.survival.FungalGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both original mushrooms have saved giant-growth progress; bone meal is not their fertilizer. @author NingMeng123 */
@Mixin(MushroomBlock.class)
public abstract class MushroomLifecycleMixin extends BushBlock {
    protected MushroomLifecycleMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Override public boolean canDropFromExplosion(BlockState state,BlockGetter level,BlockPos pos,Explosion explosion) { return !FungalGrowth.mushroom(state)&&super.canDropFromExplosion(state,level,pos,explosion); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(FungalGrowth.GROWTH);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state=super.getStateForPlacement(context);
        return state!=null&&(!FungalGrowth.mushroom(state)||FungalGrowth.placement(context.getLevel(),context.getClickedPos(),state))?state:null;
    }
    @Inject(method="canSurvive",at=@At("HEAD"),cancellable=true)
    private void mite$sourceHabitat(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(FungalGrowth.mushroom(state)) cir.setReturnValue(FungalGrowth.survives(level,pos,state));
    }
    @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
    private void mite$sourceMushroomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        if(FungalGrowth.mushroom(state)) { FungalGrowth.mushroomTick(level,pos,state,random); ci.cancel(); }
    }
    @WrapMethod(method="growMushroom")
    private boolean mite$threeStageGiant(ServerLevel level,BlockPos pos,BlockState state,RandomSource random,Operation<Boolean> original) {
        if(!FungalGrowth.mushroom(state)) return original.call(level,pos,state,random);
        if(!FungalGrowth.canGiant(level,pos,state)) return false;
        BlockState grown=state.setValue(FungalGrowth.GROWTH,Math.min(3,state.getValue(FungalGrowth.GROWTH)+1));
        if(grown.getValue(FungalGrowth.GROWTH)<3) { level.setBlock(pos,grown,2); return true; }
        return original.call(level,pos,grown,random);
    }
    @Inject(method="isValidBonemealTarget",at=@At("HEAD"),cancellable=true)
    private void mite$manureOnly(LevelReader level,BlockPos pos,BlockState state,CallbackInfoReturnable<Boolean> cir) { if(FungalGrowth.mushroom(state)) cir.setReturnValue(false); }
    @Inject(method="performBonemeal",at=@At("HEAD"),cancellable=true)
    private void mite$noBoneMealGiant(ServerLevel level,RandomSource random,BlockPos pos,BlockState state,CallbackInfo ci) { if(FungalGrowth.mushroom(state)) ci.cancel(); }
}
