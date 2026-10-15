package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 cane checks temperature, full daylight and an open column before each age increase. @author NingMeng123 */
@Mixin(SugarCaneBlock.class)
public abstract class SugarCaneGrowthMixin extends Block {
    protected SugarCaneGrowthMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Override public boolean canDropFromExplosion(BlockState state,BlockGetter level,BlockPos pos,Explosion explosion) { return false; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return SpecialPlantGrowth.caneOpenSky(context.getLevel(),context.getClickedPos().above())?super.getStateForPlacement(context):null;
    }
    @Inject(method="canSurvive",at=@At("RETURN"),cancellable=true)
    private void mite$legalHeight(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if(!cir.getReturnValue()) return;
        int height=1;
        while(height<=3 && level.getBlockState(pos.below(height)).is(Blocks.SUGAR_CANE)) height++;
        if(height>3) cir.setReturnValue(false);
    }
    @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
    private void mite$sourceCaneGrowth(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        SpecialPlantGrowth.caneTick(level,pos,state,random); ci.cancel();
    }
}
