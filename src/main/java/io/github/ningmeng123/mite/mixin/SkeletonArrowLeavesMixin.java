package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.ranged.SkeletonArrowLeaves;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Filter both native foliage collision paths; do not truncate the later entity/solid-block trace. @author NingMeng123 */
@Mixin(AbstractArrow.class)
public abstract class SkeletonArrowLeavesMixin implements SkeletonArrowLeaves.Origin {
    @Unique private static final EntityDataAccessor<Boolean> MITE_SKELETON_ORIGIN =
            SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BOOLEAN);
    @Unique private static final String MITE_SKELETON_ORIGIN_TAG = "mite_skeleton_arrow_origin";
    @Unique private boolean mite$legacyOriginPending;

    @Unique private AbstractArrow mite$arrow() { return (AbstractArrow)(Object)this; }
    @Override public boolean mite$firedBySkeleton() {
        return mite$arrow().getEntityData().get(MITE_SKELETON_ORIGIN);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void mite$defineSkeletonOrigin(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(MITE_SKELETON_ORIGIN, false);
    }

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void mite$updateSkeletonOrigin(@Nullable Entity owner, CallbackInfo ci) {
        if (mite$arrow().level().isClientSide) return;
        mite$legacyOriginPending = false;
        mite$arrow().getEntityData().set(MITE_SKELETON_ORIGIN, owner instanceof AbstractSkeleton);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mite$saveSkeletonOrigin(CompoundTag tag, CallbackInfo ci) {
        mite$resolveLegacyOrigin();
        // A legacy arrow whose owner is still unloaded must remain eligible for later migration.
        if (!mite$legacyOriginPending) tag.putBoolean(MITE_SKELETON_ORIGIN_TAG, mite$firedBySkeleton());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mite$loadSkeletonOrigin(CompoundTag tag, CallbackInfo ci) {
        mite$legacyOriginPending = !tag.contains(MITE_SKELETON_ORIGIN_TAG);
        mite$arrow().getEntityData().set(MITE_SKELETON_ORIGIN, tag.getBoolean(MITE_SKELETON_ORIGIN_TAG));
        mite$resolveLegacyOrigin();
    }

    @Unique private void mite$resolveLegacyOrigin() {
        if (!mite$legacyOriginPending || mite$arrow().level().isClientSide) return;
        Entity owner = mite$arrow().getOwner();
        if (owner == null) return;
        mite$arrow().getEntityData().set(MITE_SKELETON_ORIGIN, owner instanceof AbstractSkeleton);
        mite$legacyOriginPending = false;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mite$migrateLegacyOrigin(CallbackInfo ci) {
        mite$resolveLegacyOrigin();
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape mite$ignoreLeavesAtArrowOrigin(BlockState state, BlockGetter level, BlockPos pos,
            Operation<VoxelShape> original) {
        return SkeletonArrowLeaves.ignores((AbstractArrow)(Object)this, state)
                ? Shapes.empty() : original.call(state, level, pos);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"), index = 0)
    private ClipContext mite$traceThroughLeaves(ClipContext context) {
        return SkeletonArrowLeaves.filter((AbstractArrow)(Object)this, context);
    }
}
