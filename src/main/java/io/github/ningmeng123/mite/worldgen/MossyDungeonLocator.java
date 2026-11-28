package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/** Finds ordinary monster rooms in nearby loaded terrain. @author NingMeng123 */
public final class MossyDungeonLocator {
    private static final int SEARCH_RADIUS = 192;
    private static final String GENERATED_ROOM = "mite:natural_monster_room";

    private MossyDungeonLocator() {}

    public static void markGenerated(SpawnerBlockEntity spawner) {
        spawner.getPersistentData().putBoolean(GENERATED_ROOM, true);
    }

    public static boolean isGeneratedRoom(SpawnerBlockEntity spawner) {
        return spawner.getPersistentData().getBoolean(GENERATED_ROOM);
    }

    @Nullable
    public static BlockPos findNearest(ServerLevel level, BlockPos origin) {
        ServerChunkCache chunks = level.getChunkSource();
        BlockPos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        int minX = SectionPos.blockToSectionCoord(origin.getX() - SEARCH_RADIUS);
        int maxX = SectionPos.blockToSectionCoord(origin.getX() + SEARCH_RADIUS);
        int minZ = SectionPos.blockToSectionCoord(origin.getZ() - SEARCH_RADIUS);
        int maxZ = SectionPos.blockToSectionCoord(origin.getZ() + SEARCH_RADIUS);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                var chunk = chunks.getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    int dx = pos.getX() - origin.getX();
                    int dz = pos.getZ() - origin.getZ();
                    double distance = pos.distSqr(origin);
                    if (dx * dx + dz * dz > SEARCH_RADIUS * SEARCH_RADIUS || distance >= nearestDistance
                            || !chunk.getBlockState(pos).is(Blocks.SPAWNER)
                            || !level.getWorldBorder().isWithinBounds(pos)
                            || PlayerPlacedBlocks.isPlaced(chunk, pos)
                            || !(chunk.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) continue;
                    if (isGeneratedRoom(spawner) || looksLikeLegacyRoom(chunks, pos)) {
                        nearest = pos;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean looksLikeLegacyRoom(ServerChunkCache chunks, BlockPos spawner) {
        // The inner 5x5 always fits inside a vanilla room. A few floor cells may have been carved or mined.
        int floor = 0;
        int mossy = 0;
        BlockPos center = spawner.below();
        boolean hasCenterFloor = false;
        for (BlockPos pos : BlockPos.betweenClosed(spawner.offset(-2, -1, -2), spawner.offset(2, -1, 2))) {
            var chunk = chunks.getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            var state = chunk.getBlockState(pos);
            boolean mossyFloor = state.is(Blocks.MOSSY_COBBLESTONE);
            if (mossyFloor || state.is(Blocks.COBBLESTONE)) {
                floor++;
                if (mossyFloor) mossy++;
                if (pos.equals(center)) hasCenterFloor = true;
            }
        }
        if (floor >= 14 && mossy >= 5) return true;
        // A chest glimpsed across a room boundary must not identify a different cage nearby.
        if (!hasCenterFloor) return false;

        for (BlockPos pos : BlockPos.betweenClosed(spawner.offset(-3, 0, -3), spawner.offset(3, 0, 3))) {
            var chunk = chunks.getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockState(pos).is(Blocks.CHEST)
                    && chunk.getBlockEntity(pos) instanceof RandomizableContainer chest
                    && BuiltInLootTables.SIMPLE_DUNGEON.equals(chest.getLootTable())) return true;
        }
        return false;
    }
}
