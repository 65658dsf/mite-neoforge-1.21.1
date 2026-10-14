package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import io.github.ningmeng123.mite.world.DecorativeDoorFrames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The native copper identity uses the same source redstone-only, framed-door rules. @author NingMeng123 */
@Mixin(DoorBlock.class)
public abstract class DecorativeDoorMixin {
    @Inject(method = "type", at = @At("RETURN"), cancellable = true)
    private void mite$metalPathfinding(CallbackInfoReturnable<BlockSetType> cir) {
        if (MiteDecorativeContent.isCopperDoor((DoorBlock) (Object) this)) cir.setReturnValue(BlockSetType.IRON);
    }
    @WrapOperation(method = "onExplosionHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/properties/BlockSetType;canOpenByWindCharge()Z"))
    private boolean mite$noWindUnlock(BlockSetType type, Operation<Boolean> original) {
        return !MiteDecorativeContent.isMetalDoor((DoorBlock) (Object) this) && original.call(type);
    }
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void mite$metalDoorRedstone(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (MiteDecorativeContent.isMetalDoor(state.getBlock())) cir.setReturnValue(InteractionResult.PASS);
    }
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void mite$requireDoorFrame(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null || !MiteDecorativeContent.isMetalDoor(state.getBlock())) return;
        if (!DecorativeDoorFrames.supported(state, context.getLevel(), context.getClickedPos())) {
            state = state.setValue(DoorBlock.HINGE, state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
            cir.setReturnValue(DecorativeDoorFrames.supported(state, context.getLevel(), context.getClickedPos()) ? state : null);
        }
    }
    @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true)
    private void mite$frameSurvival(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (MiteDecorativeContent.isMetalDoor(state.getBlock()) && cir.getReturnValueZ())
            cir.setReturnValue(DecorativeDoorFrames.supported(state, level, pos));
    }
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void mite$lostFrame(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
        if (MiteDecorativeContent.isMetalDoor(state.getBlock()) && direction.getAxis().isHorizontal()
                && !DecorativeDoorFrames.supported(state, level, pos)) {
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
                if (server.getBlockState(lower).is(state.getBlock())) server.destroyBlock(lower, true);
            }
            cir.setReturnValue(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
    }
}
