package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.rules.OreBlockRules;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla ore IDs keep a persistent counterpart to R196 metadata bit 1. @author NingMeng123 */
@Mixin(Block.class)
public abstract class OrePlacedStateMixin {
    @Shadow protected abstract void registerDefaultState(BlockState state);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mite$naturalOreDefault(Block.Properties properties, CallbackInfo ci) {
        BlockState state = ((Block)(Object)this).defaultBlockState();
        if (state.hasProperty(OreBlockRules.PLACED)) registerDefaultState(state.setValue(OreBlockRules.PLACED, false));
    }

    @Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
    private void mite$orePlacementProperty(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        if ((Object) this instanceof DropExperienceBlock) builder.add(OreBlockRules.PLACED);
    }
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void mite$placedOre(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state != null && state.hasProperty(OreBlockRules.PLACED)) cir.setReturnValue(state.setValue(OreBlockRules.PLACED, true));
    }
    @Inject(method = "getExplosionResistance", at = @At("RETURN"), cancellable = true)
    private void mite$sourceOreResistance(CallbackInfoReturnable<Float> cir) {
        BlockState state = ((Block)(Object)this).defaultBlockState();
        if (OreBlockRules.isManaged(state)) cir.setReturnValue(OreBlockRules.hardness(state, cir.getReturnValue()));
    }
}
