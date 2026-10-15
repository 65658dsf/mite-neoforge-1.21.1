package io.github.ningmeng123.mite.entity.ai;

import io.github.ningmeng123.mite.survival.CropLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/** User-requested modern farmland raiding; distinct from R196 pursuit digging and passive fall trampling. @author NingMeng123 */
public final class MonsterFarmland {
    private MonsterFarmland() {}

    public static boolean handles(PathfinderMob mob) {
        var type = mob.getType();
        // These stationary/flying monsters inherit a ground-navigation object but do not walk its paths.
        return type != EntityType.BLAZE && type != EntityType.VEX && type != EntityType.SHULKER
                && type.getCategory() == MobCategory.MONSTER && mob.getNavigation() instanceof GroundPathNavigation;
    }

    public static boolean idleAndAllowed(PathfinderMob mob) {
        return mob.level() instanceof ServerLevel && handles(mob) && mob.isAlive()
                && (mob.getTarget() == null || !mob.getTarget().isAlive()) && !mob.isPassenger()
                && EventHooks.canEntityGrief(mob.level(), mob);
    }

    public static boolean farm(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(pos.above()) || !level.hasChunkAt(pos)
                || !(level.getBlockState(pos).getBlock() instanceof FarmBlock)) return false;
        var plant = level.getBlockState(pos.above());
        return plant.getFluidState().isEmpty() && (plant.isAir() || plant.getBlock() instanceof BushBlock
                || plant.getBlock() instanceof StemBlock || plant.getBlock() instanceof AttachedStemBlock);
    }

    /** Check all vetoes before changing either cell, so protecting the crop also protects the soil below it. */
    public static boolean flatten(PathfinderMob mob, BlockPos soil) {
        if (!idleAndAllowed(mob) || !(mob.level() instanceof ServerLevel level) || !farm(level, soil)) return false;
        var before = level.getBlockState(soil); var plantPos = soil.above(); var plant = level.getBlockState(plantPos);
        if (!before.canEntityDestroy(level, soil, mob) || !EventHooks.onEntityDestroyBlock(mob, soil, before)) return false;
        if (!plant.isAir() && (!plant.canEntityDestroy(level, plantPos, mob)
                || !EventHooks.onEntityDestroyBlock(mob, plantPos, plant))) return false;
        var event = new BlockEvent.FarmlandTrampleEvent(level, soil,
                net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 1F, mob);
        if (NeoForge.EVENT_BUS.post(event).isCanceled() || !idleAndAllowed(mob)
                || !level.getBlockState(soil).equals(before) || !level.getBlockState(plantPos).equals(plant)) return false;
        if (!plant.isAir()) {
            if (CropLifecycle.managed(plant)) CropLifecycle.trample(level, plantPos, mob);
            else level.destroyBlock(plantPos, false, mob);
        }
        if (!level.getBlockState(soil).equals(before)) return false;
        FarmBlock.turnToDirt(mob, before, level, soil);
        return !(level.getBlockState(soil).getBlock() instanceof FarmBlock);
    }
}
