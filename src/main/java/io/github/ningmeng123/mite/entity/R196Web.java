package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.registry.MiteMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** The source unlisted EntityWeb is actually spawned by arachnids; prediction is four ticks. @author NingMeng123 */
public final class R196Web extends ThrowableItemProjectile {
    public R196Web(EntityType<? extends R196Web> type, Level level) { super(type, level); }
    public R196Web(Level level, LivingEntity owner) { super(MiteMobs.WEB.get(), owner, level); }
    @Override protected Item getDefaultItem() { return Items.COBWEB; }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && !level().getFluidState(blockPosition()).isEmpty()) discard();
    }
    public static boolean impact(LivingEntity target, boolean burning) {
        if (target.level().isClientSide) return false;
        if (burning) target.igniteForSeconds(5);
        Vec3 predicted = target.position().add(target.getDeltaMovement().multiply(4, 0, 4));
        BlockPos origin = target.blockPosition(), lead = BlockPos.containing(predicted.x, target.getY(), predicted.z);
        if (!lead.equals(origin) && place(target.level(), lead, burning, target)) return true;
        if (place(target.level(), origin, burning, target)) return true;
        for (BlockPos position : BlockPos.betweenClosed(BlockPos.containing(target.getBoundingBox().minX, target.getBoundingBox().minY, target.getBoundingBox().minZ),
                BlockPos.containing(target.getBoundingBox().maxX, target.getBoundingBox().maxY, target.getBoundingBox().maxZ)))
            if (place(target.level(), position, burning, target)) return true;
        return false;
    }
    public static boolean replaceable(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) == 1;
    }
    public static boolean place(Level level, BlockPos pos, boolean burning, LivingEntity actor) {
        if (!level.hasChunkAt(pos) || !replaceable(level, pos) || !net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, actor)) return false;
        if (!level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3)) return false;
        if (burning) {
            var air = java.util.Arrays.stream(Direction.values()).filter(d -> level.getBlockState(pos.relative(d)).isAir()).toList();
            if (!air.isEmpty()) {
                level.setBlock(pos.relative(air.get(level.random.nextInt(air.size()))), Blocks.FIRE.defaultBlockState(), 3);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos))) entity.igniteForSeconds(5);
            }
        }
        return true;
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (hit.getEntity() instanceof LivingEntity living && !level().isClientSide && impact(living, isOnFire())) discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if (level().isClientSide) return;
        BlockPos position = hit.getBlockPos();
        if (level().getBlockState(position).is(Blocks.FIRE) || !level().getFluidState(position).isEmpty()) { discard(); return; }
        if (!replaceable(level(), position)) position = position.relative(hit.getDirection());
        if (getOwner() instanceof LivingEntity owner && !place(level(), position, isOnFire(), owner)) place(level(), position.above(), isOnFire(), owner);
        discard();
    }
}
