package io.github.ningmeng123.mite.worldgen;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteOreBlocks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** One bounded mineral reward for each newly generated natural shrieker. @author NingMeng123 */
public final class NaturalShriekerOres {
    public static final int RADIUS = 5;
    private static final long SALT = 0x4d4954455348524bL;
    private static final ResourceLocation ANCIENT_CITY = ResourceLocation.withDefaultNamespace("ancient_city");
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mite.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Rewarded>> REWARDED = ATTACHMENTS.register(
            "natural_shrieker_ores", () -> AttachmentType.builder(() -> new Rewarded()).serialize(Rewarded.CODEC).build());
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private NaturalShriekerOres() {}
    public static void register(IEventBus bus) { ATTACHMENTS.register(bus); }

    public static final class Rewarded {
        public static final Codec<Rewarded> CODEC = Codec.LONG_STREAM.xmap(Rewarded::new, Rewarded::positions);
        private final LongOpenHashSet positions;
        public Rewarded() { this(LongStream.empty()); }
        private Rewarded(LongStream positions) { this.positions = new LongOpenHashSet(positions.toArray()); }
        private synchronized LongStream positions() {
            long[] sorted = positions.toLongArray();
            Arrays.sort(sorted);
            return Arrays.stream(sorted);
        }
        public synchronized int size() { return positions.size(); }
        private synchronized boolean claim(BlockPos pos) { return positions.add(pos.asLong()); }
    }

    private static final class Context {
        final WorldGenRegion level;
        final boolean city;
        final Set<BlockPos> shriekers = new LinkedHashSet<>();
        final LongOpenHashSet buildings = new LongOpenHashSet();
        boolean flushing;
        Context(WorldGenRegion level, boolean city) { this.level = level; this.city = city; }
    }

    public static final class Scope implements AutoCloseable {
        private final Context previous;
        private final Context current;
        private boolean finished;
        private Scope(WorldGenLevel level, boolean enabled, boolean city) {
            previous = CURRENT.get();
            current = enabled && level instanceof WorldGenRegion region && level.getLevel().dimension().equals(Level.OVERWORLD)
                    ? new Context(region, city) : null;
            if (current == null) CURRENT.remove();
            else CURRENT.set(current);
        }
        public void finish() {
            if (finished || current == null) return;
            finished = true;
            if (previous != null && previous.level == current.level) {
                previous.shriekers.addAll(current.shriekers);
                previous.buildings.addAll(current.buildings);
                return;
            }
            current.flushing = true;
            for (BlockPos shrieker : current.shriekers) reward(current, shrieker);
        }
        @Override public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static Scope patch(WorldGenLevel level) { return new Scope(level, true, false); }
    public static Scope structure(WorldGenLevel level, Structure structure) {
        var id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
        return new Scope(level, ANCIENT_CITY.equals(id), true);
    }

    public static void placed(WorldGenRegion level, BlockPos pos, BlockState requested) {
        Context context = CURRENT.get();
        if (context == null || context.level != level || context.flushing) return;
        if (context.city) context.buildings.add(pos.asLong());
        if (!requested.is(Blocks.SCULK_SHRIEKER) || !requested.getValue(SculkShriekerBlock.CAN_SUMMON)) return;
        ChunkAccess chunk = writableChunk(level, pos);
        if (chunk == null) return;
        BlockState actual = chunk.getBlockState(pos);
        if (actual.is(Blocks.SCULK_SHRIEKER) && actual.getValue(SculkShriekerBlock.CAN_SUMMON)) context.shriekers.add(pos.immutable());
    }

    private static void reward(Context context, BlockPos shrieker) {
        WorldGenRegion level = context.level;
        ChunkAccess owner = writableChunk(level, shrieker);
        if (owner == null) return;
        BlockState state = owner.getBlockState(shrieker);
        if (!state.is(Blocks.SCULK_SHRIEKER) || !state.getValue(SculkShriekerBlock.CAN_SUMMON)) return;
        if (!owner.getData(REWARDED.get()).claim(shrieker)) return;
        owner.setUnsaved(true);
        RandomSource random = RandomSource.create(level.getSeed() ^ Mth.getSeed(shrieker) ^ SALT);
        int target = 1 + random.nextInt(3);
        List<BlockPos> local = new ArrayList<>(), neighboring = new ArrayList<>();
        for (BlockPos candidate : BlockPos.betweenClosed(shrieker.offset(-RADIUS, -RADIUS, -RADIUS), shrieker.offset(RADIUS, RADIUS, RADIUS))) {
            if (candidate.distSqr(shrieker) > RADIUS * RADIUS || context.buildings.contains(candidate.asLong())) continue;
            ChunkAccess chunk = writableChunk(level, candidate);
            if (chunk == null || !rock(chunk.getBlockState(candidate))) continue;
            (chunk == owner ? local : neighboring).add(candidate.immutable());
        }
        shuffle(local, random);
        shuffle(neighboring, random);
        local.addAll(neighboring);
        int generated = 0;
        for (BlockPos candidate : local) {
            if (generated >= target) break;
            ChunkAccess chunk = writableChunk(level, candidate);
            if (chunk == null) continue;
            BlockState current = chunk.getBlockState(candidate);
            if (!rock(current)) continue;
            Block ore = switch (random.nextInt(4)) {
                case 0 -> current.is(Blocks.DEEPSLATE) ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
                case 1 -> current.is(Blocks.DEEPSLATE) ? MiteOreBlocks.DEEPSLATE_MITHRIL_ORE.get() : MiteOreBlocks.MITHRIL_ORE.get();
                case 2 -> current.is(Blocks.DEEPSLATE) ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
                default -> current.is(Blocks.DEEPSLATE) ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE;
            };
            if (level.setBlock(candidate, ore.defaultBlockState(), 2)) generated++;
        }
    }

    private static boolean rock(BlockState state) { return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE); }
    private static void shuffle(List<BlockPos> candidates, RandomSource random) {
        for (int index = candidates.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            BlockPos previous = candidates.set(index, candidates.get(other));
            candidates.set(other, previous);
        }
    }
    private static ChunkAccess writableChunk(WorldGenRegion level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
        int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
        int radius = ((ShriekerOreRegion)level).mite$oreWriteRadius();
        if (level.getCenter().getChessboardDistance(chunkX, chunkZ) > radius || !level.hasChunk(chunkX, chunkZ)) return null;
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
        return chunk == null || chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL) || !level.ensureCanWrite(pos) ? null : chunk;
    }
}
