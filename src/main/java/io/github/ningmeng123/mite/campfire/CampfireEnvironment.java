package io.github.ningmeng123.mite.campfire;

import io.github.ningmeng123.mite.Mite;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Local heat, accidental ignition and a loaded-fire index for idle monsters. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class CampfireEnvironment {
    public static final int ENVIRONMENT_INTERVAL = 20;
    /** One attempt per second; an exposed combustible neighbour has a 1/60 chance per attempt. */
    public static final int IGNITION_CHANCE = 60;
    public static final int FIRE_INDEX_EXPIRY = 40;
    private static final int INDEX_CLEANUP_INTERVAL = 200;
    private static final Map<ServerLevel, FireIndex> ACTIVE_FIRES = new WeakHashMap<>();

    private CampfireEnvironment() {}

    public static void tick(ServerLevel level, BlockPos pos) {
        if (!CampfireController.isBurning(level, pos)) {
            forget(level, pos);
            return;
        }
        long time = level.getGameTime();
        FireIndex index = ACTIVE_FIRES.computeIfAbsent(level, ignored -> new FireIndex());
        index.cleanup(time);
        index.chunks.computeIfAbsent(ChunkPos.asLong(pos), ignored -> new HashMap<>()).put(pos.immutable(), time);
        if (time % ENVIRONMENT_INTERVAL != 0) return;
        warmPlayers(level, pos);
        if (level.getRandom().nextInt(IGNITION_CHANCE) == 0) igniteNeighbour(level, pos);
    }

    private static void warmPlayers(ServerLevel level, BlockPos pos) {
        double radiusSquared = CampfireRules.WARMTH_RADIUS * CampfireRules.WARMTH_RADIUS;
        Vec3 center = pos.getCenter();
        for (var player : level.players()) {
            if (player.isAlive() && player.position().distanceToSqr(center) <= radiusSquared)
                player.setTicksFrozen(0);
        }
    }

    /** Removes a stopped fire immediately; all lookups also verify the actual block and fuel. */
    public static void forget(ServerLevel level, BlockPos pos) {
        FireIndex index = ACTIVE_FIRES.get(level);
        if (index == null) return;
        Map<BlockPos, Long> chunk = index.chunks.get(ChunkPos.asLong(pos));
        if (chunk == null) return;
        chunk.remove(pos);
        if (chunk.isEmpty()) index.chunks.remove(ChunkPos.asLong(pos));
    }

    @Nullable public static BlockPos findNearestFire(ServerLevel level, Vec3 origin, double radius) {
        return findNearestFire(level, origin, radius, ignored -> true);
    }

    @Nullable public static BlockPos findNearestFire(ServerLevel level, Vec3 origin, double radius, Predicate<BlockPos> allowed) {
        FireIndex index = ACTIVE_FIRES.get(level);
        if (index == null || radius < 0 || !Double.isFinite(radius)) return null;
        long time = level.getGameTime();
        index.cleanup(time);
        double nearestSquared = radius * radius;
        BlockPos nearest = null;
        int minX = net.minecraft.util.Mth.floor(origin.x - radius) >> 4;
        int maxX = net.minecraft.util.Mth.floor(origin.x + radius) >> 4;
        int minZ = net.minecraft.util.Mth.floor(origin.z - radius) >> 4;
        int maxZ = net.minecraft.util.Mth.floor(origin.z + radius) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long chunkKey = ChunkPos.asLong(x, z);
                Map<BlockPos, Long> chunk = index.chunks.get(chunkKey);
                if (chunk == null) continue;
                var entries = chunk.entrySet().iterator();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    BlockPos candidate = entry.getKey();
                    if (time - entry.getValue() > FIRE_INDEX_EXPIRY || !level.hasChunkAt(candidate)
                            || !CampfireController.isBurning(level, candidate)) {
                        entries.remove();
                        continue;
                    }
                    double distanceSquared = origin.distanceToSqr(candidate.getCenter());
                    if (distanceSquared <= nearestSquared && allowed.test(candidate)) {
                        nearestSquared = distanceSquared;
                        nearest = candidate;
                    }
                }
                if (chunk.isEmpty()) index.chunks.remove(chunkKey);
            }
        }
        return nearest;
    }

    /** Tries only loaded air beside nearby wood or hay; the vanilla fire handles subsequent spread. */
    public static boolean igniteNeighbour(ServerLevel level, BlockPos campfire) {
        if (!CampfireController.isBurning(level, campfire)
                || level.isRainingAt(campfire.above())
                || !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return false;
        var candidates = new ArrayList<BlockPos>();
        for (BlockPos source : BlockPos.betweenClosed(campfire.offset(-1, -1, -1), campfire.offset(1, 1, 1))) {
            if (!level.hasChunkAt(source)) continue;
            BlockState sourceState = level.getBlockState(source);
            if (!isIgnitionSource(sourceState)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos air = source.relative(direction);
                if (sourceState.getFlammability(level, source, direction) > 0 && canIgniteAt(level, air))
                    candidates.add(air.immutable());
            }
        }
        if (candidates.isEmpty()) return false;
        BlockPos air = candidates.get(level.getRandom().nextInt(candidates.size()));
        return level.setBlock(air, BaseFireBlock.getState(level, air), Block.UPDATE_ALL);
    }

    private static boolean isIgnitionSource(BlockState state) {
        // Storage blocks are deliberately absent: the campfire never directly replaces a container.
        return !state.hasBlockEntity() && (state.is(Blocks.HAY_BLOCK) || state.is(BlockTags.LOGS_THAT_BURN)
                || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_TRAPDOORS));
    }

    public static boolean canIgniteAt(ServerLevel level, BlockPos air) {
        if (!level.hasChunkAt(air) || !level.getBlockState(air).isAir() || level.isRainingAt(air)) return false;
        for (Direction direction : Direction.values())
            if (!level.hasChunkAt(air.relative(direction))) return false;
        return BaseFireBlock.getState(level, air).canSurvive(level, air);
    }

    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && !event.getLevel().isClientSide && event.getEntity() instanceof Monster monster)
            InvestigateCampfireGoal.install(monster);
    }

    private static final class FireIndex {
        private final Map<Long, Map<BlockPos, Long>> chunks = new HashMap<>();
        private long nextCleanup;

        private void cleanup(long time) {
            if (time < nextCleanup) return;
            nextCleanup = time + INDEX_CLEANUP_INTERVAL;
            var buckets = chunks.values().iterator();
            while (buckets.hasNext()) {
                Map<BlockPos, Long> bucket = buckets.next();
                bucket.values().removeIf(lastSeen -> time - lastSeen > FIRE_INDEX_EXPIRY);
                if (bucket.isEmpty()) buckets.remove();
            }
        }
    }
}
