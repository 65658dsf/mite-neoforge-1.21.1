package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.FungalGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 nether wart grows at 1/40 only in the Nether and cannot be planted outdoors. @author NingMeng123 */
@Mixin(NetherWartBlock.class)
public abstract class NetherWartGrowthMixin extends BushBlock {
    protected NetherWartGrowthMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Override public boolean canDropFromExplosion(BlockState state,BlockGetter level,BlockPos pos,Explosion explosion) { return false; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return FungalGrowth.outdoors(context.getLevel(),context.getClickedPos())?null:super.getStateForPlacement(context);
    }
    @Inject(method="isRandomlyTicking",at=@At("HEAD"),cancellable=true)
    private void mite$validateMaturePlantsToo(BlockState state,CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(true); }
    @Inject(method="randomTick",at=@At("HEAD"),cancellable=true)
    private void mite$onlyTheNether(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        if(!level.dimension().equals(Level.NETHER)) { level.destroyBlock(pos,true); ci.cancel(); }
    }
    @ModifyConstant(method="randomTick",constant=@Constant(intValue=10))
    private int mite$sourceGrowthRoll(int nativeBound) { return 40; }
}
