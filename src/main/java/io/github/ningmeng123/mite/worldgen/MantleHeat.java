package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Source-height-dependent fire contact; placed mantle high above the floor is not a damage trap. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MantleHeat {
    private MantleHeat() {}
    public static boolean boundary(BlockState state) {
        return state.is(MiteWorldBlocks.MANTLE.get()) || state.is(MiteWorldBlocks.CORE.get());
    }
    public static boolean evaporatesWater(LevelAccessor level, BlockPos target) {
        return boundary(level.getBlockState(target.below()));
    }
    public static boolean touchesHotBoundary(Entity entity) {
        var box = entity.getBoundingBox();
        Level level = entity.level();
        int mantleBand = ModernUnderworldTerrain.MAX_MANTLE_Y - ModernUnderworldTerrain.MIN_Y + 1;
        boolean low = level.dimension().equals(MiteWorldDimensions.UNDERWORLD)
                && box.minY <= level.getMinBuildHeight() + mantleBand;
        boolean nether = level.dimension().equals(Level.NETHER) && (box.minY <= 1.0 || box.maxY >= 123.0);
        if (!low && !nether) return false;
        return BlockPos.betweenClosedStream(box.inflate(.001)).anyMatch(pos -> level.hasChunkAt(pos) && boundary(level.getBlockState(pos)));
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || entity.fireImmune()
                || entity instanceof Player player && (player.isCreative() || player.isSpectator())
                || !touchesHotBoundary(entity)) return;
        if (entity instanceof LivingEntity || entity.tickCount % 10 == 0) entity.hurt(entity.damageSources().inFire(), 1F);
        if (!entity.isInWaterRainOrBubble()) {
            int ticks = entity.getRemainingFireTicks();
            // uf.getFireResistance is ten, while modern players start at minus twenty.
            if (entity instanceof Player && ticks < -10) ticks = -10;
            entity.setRemainingFireTicks(ticks + 1);
            if (ticks + 1 == 0) entity.igniteForTicks(170);
        }
    }
}
