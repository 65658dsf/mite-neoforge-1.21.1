package io.github.ningmeng123.mite.progression;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.OreBlockRules;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Chunk-local provenance prevents equipment experience from recycled player blocks. @author NingMeng123 */
public final class PlayerPlacedBlocks {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mite.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Positions>> POSITIONS =
            ATTACHMENTS.register("player_placed_blocks", () -> AttachmentType.builder(() -> new Positions())
                    .serialize(Positions.CODEC).build());

    private PlayerPlacedBlocks() {}
    public static void register(IEventBus bus) { ATTACHMENTS.register(bus); }

    public static final class Positions {
        public static final Codec<Positions> CODEC = Codec.LONG_STREAM.xmap(
                values -> new Positions(values.toArray()), Positions::packed);
        private final LongOpenHashSet values;
        public Positions() { this(new long[0]); }
        private Positions(long[] values) { this.values = new LongOpenHashSet(values); }
        private synchronized LongStream packed() {
            long[] packed = values.toLongArray();
            Arrays.sort(packed);
            return Arrays.stream(packed);
        }
        private synchronized boolean contains(BlockPos pos) { return values.contains(pos.asLong()); }
        private synchronized boolean add(BlockPos pos) { return values.add(pos.asLong()); }
        private synchronized boolean remove(BlockPos pos) { return values.remove(pos.asLong()); }
        private synchronized boolean isEmpty() { return values.isEmpty(); }
    }

    public static boolean isPlaced(LevelReader level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || level.isOutsideBuildHeight(pos)) return false;
        var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && isPlaced(chunk, pos);
    }

    public static boolean isPlaced(ChunkAccess chunk, BlockPos pos) {
        BlockState state = chunk.getBlockState(pos);
        if (state.hasProperty(OreBlockRules.PLACED) && state.getValue(OreBlockRules.PLACED)) return true;
        Positions data = chunk.getExistingDataOrNull(POSITIONS.get());
        return data != null && data.contains(pos);
    }

    public static void set(Level level, BlockPos pos, boolean placed) {
        if (level instanceof ServerLevel server && !level.isOutsideBuildHeight(pos))
            set(server.getChunkAt(pos), pos, placed);
    }

    public static void set(ChunkAccess chunk, BlockPos pos, boolean placed) {
        if (chunk.isOutsideBuildHeight(pos) || chunk.getPos().x != pos.getX() >> 4 || chunk.getPos().z != pos.getZ() >> 4) return;
        Positions data = placed ? chunk.getData(POSITIONS.get()) : chunk.getExistingDataOrNull(POSITIONS.get());
        if (data == null) return;
        boolean changed = placed ? data.add(pos) : data.remove(pos);
        if (changed) {
            chunk.setUnsaved(true);
            if (data.isEmpty()) chunk.removeData(POSITIONS.get());
        }
    }

    /** Soil conversion, stripping and oxidation retain the player's original block. */
    public static void changed(ChunkAccess chunk, BlockPos pos, BlockState current) {
        if (current.isAir()) set(chunk, pos, false);
    }

    public static Set<BlockPos> movingDestinations(Level level, List<BlockPos> sources, Direction movement) {
        if (level.isClientSide) return Set.of();
        Set<BlockPos> destinations = new HashSet<>();
        for (BlockPos source : sources) if (isPlaced(level, source)) destinations.add(source.relative(movement));
        return destinations;
    }

    public static void movingDestination(Level level, BlockPos pos, Set<BlockPos> destinations) {
        if (!level.isClientSide && destinations != null && level.getBlockState(pos).is(Blocks.MOVING_PISTON))
            set(level, pos, destinations.contains(pos));
    }
}
