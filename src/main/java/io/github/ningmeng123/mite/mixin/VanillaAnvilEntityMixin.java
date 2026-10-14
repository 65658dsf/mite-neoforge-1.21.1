package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native iron anvil IDs gain the same source wear storage as added metals. @author NingMeng123 */
@Mixin(AnvilBlock.class)
public abstract class VanillaAnvilEntityMixin extends FallingBlock implements EntityBlock {
    protected VanillaAnvilEntityMixin(BlockBehaviour.Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MaterialAnvilBlockEntity(pos, state); }
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void mite$clearAnvilAbove(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.getBlockState(pos.above()).isCollisionShapeFullBlock(level, pos.above())) cir.setReturnValue(InteractionResult.PASS);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof MaterialAnvilBlockEntity entity) entity.refreshAppearance();
    }
}
