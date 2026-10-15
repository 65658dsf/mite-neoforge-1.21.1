package io.github.ningmeng123.mite.survival;

import net.minecraft.core.BlockPos;
import io.github.ningmeng123.mite.rules.CropSoilRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/** R196 aof hydration, weather-sensitive evaporation and trampling. @author NingMeng123 */
public final class CropSoil {
    private CropSoil() {}

    public static float dryingChance(float temperature, boolean highHumidity) {
        return CropSoilRules.dryingChance(temperature, highHumidity);
    }

    public static boolean frozenWater(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean nearbyPlant(Level level, BlockPos soil) {
        for (BlockPos pos : BlockPos.betweenClosed(soil.offset(-1, 1, -1), soil.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock
                    || state.getBlock() instanceof AttachedStemBlock) return true;
        }
        return false;
    }

    public static void tick(ServerLevel level, BlockPos soil, BlockState state, RandomSource random) {
        // Neither vanilla hydration extensions nor source water scans should force chunk loads.
        if (!level.isAreaLoaded(soil, 4)) return;
        int moisture = state.getValue(FarmBlock.MOISTURE);
        if (FoodAgriculture.hasWater(level, soil)) {
            if (moisture < 7) level.setBlock(soil, state.setValue(FarmBlock.MOISTURE, 7), 2);
            return;
        }
        if (level.isRainingAt(soil.above())) {
            if (moisture < 7) level.setBlock(soil, state.setValue(FarmBlock.MOISTURE, moisture + 1), 3);
            return;
        }
        float chance = dryingChance(level.getBiome(soil).value().getBaseTemperature(), FoodAgriculture.humid(level, soil));
        if (random.nextFloat() >= chance) return;
        if (moisture > 0) level.setBlock(soil, state.setValue(FarmBlock.MOISTURE, moisture - 1), 2);
        else if (level.isEmptyBlock(soil.above()) && !nearbyPlant(level, soil))
            FarmBlock.turnToDirt(null, state, level, soil);
    }

    public static void hydrateOnPlace(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide && level.isAreaLoaded(pos, 4) && state.getValue(FarmBlock.MOISTURE) < 7
                && FoodAgriculture.hasWater(level, pos))
            level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 7), 2);
    }

    /** Returns whether the native FarmBlock caller may replace this soil with dirt. */
    public static boolean trample(Level level, BlockPos soil, float fallDistance, Entity entity) {
        if (!(level instanceof ServerLevel server) || entity instanceof Slime && !(entity instanceof MagmaCube)
                || level.random.nextFloat() >= fallDistance - .5F) return false;
        if (entity instanceof Player player) {
            if (!level.mayInteract(player, soil) || !level.mayInteract(player, soil.above())) return false;
        } else if (!EventHooks.canEntityGrief(level, entity)) return false;
        // Keep mod protection vetoes while deliberately using R196's probability/size rules.
        var event = new BlockEvent.FarmlandTrampleEvent(level, soil, Blocks.DIRT.defaultBlockState(), fallDistance, entity);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) return false;
        BlockPos plant = soil.above();
        BlockState crop = level.getBlockState(plant);
        if (CropLifecycle.managed(crop)) CropLifecycle.trample(server, plant, entity);
        else if (crop.getBlock() instanceof StemBlock || crop.getBlock() instanceof AttachedStemBlock)
            level.destroyBlock(plant, false, entity);
        else if (crop.getBlock() instanceof BushBlock)
            level.destroyBlock(plant, !(entity instanceof Cow), entity);
        BlockState state = level.getBlockState(soil);
        return state.getBlock() instanceof FarmBlock && state.getValue(FarmBlock.MOISTURE) == 0
                && !FoodAgriculture.fertilized(state);
    }
}
