package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Persistent farmland fertilization and source growth modifiers. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class FoodAgriculture {
    public static final BooleanProperty FERTILIZED = BooleanProperty.create("mite_fertilized");
    private FoodAgriculture() {}
    public static boolean fertilized(BlockState soil) { return soil.hasProperty(FERTILIZED) && soil.getValue(FERTILIZED); }
    public static boolean humid(Level level, BlockPos pos) {
        return level.getBiome(pos).value().getModifiedClimateSettings().downfall() > .85F;
    }
    public static boolean hasWater(Level level, BlockPos soil) {
        for (BlockPos p : BlockPos.betweenClosed(soil.offset(-4, 0, -4), soil.offset(4, 1, 4)))
            if (level.getFluidState(p).is(FluidTags.WATER)) return true;
        // aof.m treats adjacent ice, snow layers and snow blocks as irrigation too.
        for (BlockPos p : BlockPos.betweenClosed(soil.offset(-1, 0, -1), soil.offset(1, 1, 1)))
            if (CropSoil.frozenWater(level.getBlockState(p))) return true;
        // Preserve the NeoForge hydration extension for other mods without changing native R196 water distances.
        var state = level.getBlockState(soil);
        if (state.getBlock() instanceof FarmBlock) {
            for (BlockPos p : BlockPos.betweenClosed(soil.offset(-4, 0, -4), soil.offset(4, 1, 4)))
                if (state.canBeHydrated(level, soil, level.getFluidState(p), p)) return true;
            if (net.neoforged.neoforge.common.FarmlandWaterManager.hasBlockWaterTicket(level, soil)) return true;
        }
        return false;
    }
    public static float proximity(Level level, BlockPos pos, Block crop) {
        int count = 0;
        Direction lone = null;
        for (Direction d : Direction.Plane.HORIZONTAL) if (sameProximityPlant(level, pos.relative(d), crop)) { count++; lone = d; }
        if (count == 0) return .5F;
        if (count > 1) return 1F;
        BlockPos neighbor = pos.relative(lone);
        for (Direction d : Direction.Plane.HORIZONTAL)
            if (!neighbor.relative(d).equals(pos) && sameProximityPlant(level, neighbor.relative(d), crop)) return 1F;
        return .75F;
    }
    private static boolean sameProximityPlant(Level level, BlockPos pos, Block crop) {
        BlockState neighbor = level.getBlockState(pos);
        // Dead food crops had distinct BlockCropsDead IDs; dead stems kept their original ID plus bit 8.
        return neighbor.is(crop) && (!(crop instanceof CropBlock) || !CubeCropBlight.dead(neighbor));
    }
    public static void onCropGrowth(Level level, BlockPos soil, RandomSource random) {
        BlockState state = level.getBlockState(soil);
        // anr consumes this draw after every successful growth, even on unfertilized soil.
        if (random.nextInt(256) == 0 && fertilized(state)) level.setBlock(soil, state.setValue(FERTILIZED, false), 2);
    }
    public static boolean soilPlant(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock
                || state.getBlock() instanceof AttachedStemBlock || state.getBlock() instanceof MushroomBlock;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void useManure(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(MiteFoods.get("manure"))) return;
        if (event.isCanceled() || event.getUseItem().isFalse() || event.getEntity().isSpectator()
                || event.getEntity().getCooldowns().isOnCooldown(event.getItemStack().getItem())) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.mayInteract(event.getEntity(), pos)) return;
        BlockState state = level.getBlockState(pos);
        if (soilPlant(state)) { pos = pos.below(); state = level.getBlockState(pos); }
        else if (event.getFace() != Direction.UP) return;
        if (!(state.getBlock() instanceof FarmBlock) || !state.hasProperty(FERTILIZED) || state.getValue(FERTILIZED)) return;
        if (!level.mayInteract(event.getEntity(), pos)
                || !event.getEntity().mayUseItemAt(pos, Direction.UP, event.getItemStack())) return;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(FERTILIZED, true), 2);
            event.getItemStack().consume(1, event.getEntity());
            level.levelEvent(2005, pos.above(), 0);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
    }

}
