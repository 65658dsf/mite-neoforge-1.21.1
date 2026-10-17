package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.CollapseFallingBlocks;
import io.github.ningmeng123.mite.world.FallingBlockImpact;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** All falling blocks reuse native impact damage; source soil retains its collision rules. @author NingMeng123 */
@Mixin(FallingBlockEntity.class)
public abstract class CollapseFallingEntityMixin implements FallingBlockImpact {
    @Shadow private boolean hurtEntities;
    @Shadow private float fallDamagePerDistance;
    @Shadow private int fallDamageMax;

    @Override public boolean mite$hasImpactDamage() {
        return hurtEntities && Float.isFinite(fallDamagePerDistance) && fallDamagePerDistance > 0F && fallDamageMax > 0;
    }

    @Inject(method = "fall", at = @At("RETURN"))
    private static void mite$enableBlockImpact(Level level, BlockPos pos, BlockState state,
            CallbackInfoReturnable<FallingBlockEntity> cir) {
        CollapseFallingBlocks.configureImpact(cir.getReturnValue());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mite$restoreBlockImpact(CompoundTag tag, CallbackInfo ci) {
        // Upgrade old non-damaging saves without changing valid native or custom settings.
        CollapseFallingBlocks.configureImpact((FallingBlockEntity)(Object)this);
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void mite$ensureBlockImpact(float distance, float multiplier, DamageSource source,
            CallbackInfoReturnable<Boolean> cir) {
        CollapseFallingBlocks.configureImpact((FallingBlockEntity)(Object)this);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/item/FallingBlockEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            shift = At.Shift.AFTER))
    private void mite$sourceCollapseCollision(CallbackInfo ci) {
        CollapseFallingBlocks.afterMove((FallingBlockEntity)(Object)this);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/FallingBlock;isFree(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean mite$sourceCollapseSupport(BlockState state, Operation<Boolean> original) {
        return CollapseFallingBlocks.canContinueThroughSupport((FallingBlockEntity)(Object)this, original.call(state));
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;canBeReplaced(Lnet/minecraft/world/item/context/BlockPlaceContext;)Z"))
    private boolean mite$respectCollapseObstacleVeto(BlockState state, BlockPlaceContext context, Operation<Boolean> original) {
        return CollapseFallingBlocks.canLandReplace((FallingBlockEntity)(Object)this, context.getClickedPos(), state,
                original.call(state, context));
    }
}
