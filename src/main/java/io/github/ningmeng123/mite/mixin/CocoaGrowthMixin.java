package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.TropicalPlantGrowth;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Natural 1/40 cocoa growth replaces 1/5 modern growth; source cocoa cannot use bone meal. @author NingMeng123 */
@Mixin(CocoaBlock.class)
public abstract class CocoaGrowthMixin extends HorizontalDirectionalBlock {
    protected CocoaGrowthMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Override public boolean canDropFromExplosion(BlockState state,BlockGetter level,BlockPos pos,Explosion explosion) { return false; }
    @ModifyConstant(method="randomTick",constant=@Constant(intValue=5))
    private int mite$sourceGrowthRoll(int nativeBound) { return 40; }
    @Inject(method="getStateForPlacement",at=@At("HEAD"),cancellable=true)
    private void mite$naturalJungleCanopy(BlockPlaceContext context,CallbackInfoReturnable<BlockState> cir) {
        if(!TropicalPlantGrowth.cocoaPlacement(context.getLevel(),context.getClickedPos())) cir.setReturnValue(null);
    }
    @Inject(method="isValidBonemealTarget",at=@At("HEAD"),cancellable=true)
    private void mite$noBoneMealTarget(LevelReader level,BlockPos pos,BlockState state,CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(false); }
    @Inject(method="performBonemeal",at=@At("HEAD"),cancellable=true)
    private void mite$noBoneMealGrowth(ServerLevel level,RandomSource random,BlockPos pos,BlockState state,CallbackInfo ci) { ci.cancel(); }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {
        if(state.getValue(CocoaBlock.AGE)<2||builder.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS)!=null) return List.of();
        return List.of(new ItemStack(Items.COCOA_BEANS,builder.getLevel().random.nextFloat()<.5F?2:1));
    }
}
