package io.github.ningmeng123.mite.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.Mite;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Persistent provenance of actual newly generated trial chamber blocks. @author NingMeng123 */
public final class NaturalTrialBlocks {
    public static final int SCHEMA_VERSION = 1;
    private static final ResourceLocation SURFACE_TRIAL = ResourceLocation.withDefaultNamespace("trial_chambers");
    private static final ResourceLocation UNDERWORLD_TRIAL = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "underworld_trial_chambers");
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mite.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Origins>> ORIGINS =
            ATTACHMENTS.register("natural_trial_blocks", () -> AttachmentType.builder(Origins::new)
                    .serialize(Origins.CODEC).build());
    private static final ThreadLocal<WorldGenLevel> GENERATING = new ThreadLocal<>();

    private NaturalTrialBlocks() {}

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    /** The schema is per chunk, and coordinates use the native packed BlockPos format. */
    public static final class Origins {
        public static final Codec<Origins> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, SCHEMA_VERSION).fieldOf("schema_version").forGetter(Origins::schemaVersion),
                Codec.LONG_STREAM.fieldOf("positions").forGetter(Origins::packedPositions)
        ).apply(instance, Origins::new));

        private final int schemaVersion;
        private final LongOpenHashSet positions;

        public Origins() {
            this(SCHEMA_VERSION, LongStream.empty());
        }

        private Origins(int schemaVersion, LongStream positions) {
            this.schemaVersion = schemaVersion;
            this.positions = new LongOpenHashSet(positions.toArray());
        }

        public int schemaVersion() {
            return schemaVersion;
        }

        public synchronized int size() {
            return positions.size();
        }

        private synchronized LongStream packedPositions() {
            long[] values = positions.toLongArray();
            Arrays.sort(values);
            return Arrays.stream(values);
        }

        private synchronized boolean contains(BlockPos pos) {
            return positions.contains(pos.asLong());
        }

        private synchronized boolean add(BlockPos pos) {
            return positions.add(pos.asLong());
        }

        private synchronized boolean remove(BlockPos pos) {
            return positions.remove(pos.asLong());
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override void close();
    }

    /** Unrelated nested structure placement cannot inherit a trial chamber's provenance. */
    public static Scope generation(WorldGenLevel level, Structure structure) {
        WorldGenLevel previous = GENERATING.get();
        ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
        var dimension = level.getLevel().dimension();
        boolean trial = dimension.equals(Level.OVERWORLD) && SURFACE_TRIAL.equals(id)
                || dimension.equals(MiteWorldDimensions.UNDERWORLD) && UNDERWORLD_TRIAL.equals(id);
        if (trial) GENERATING.set(level);
        else GENERATING.remove();
        return () -> {
            if (previous == null) GENERATING.remove();
            else GENERATING.set(previous);
        };
    }

    /** Called only after the final template write succeeds, excluding barriers and shape updates. */
    public static void recordGenerated(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (GENERATING.get() != level || state.isAir() || state.is(Blocks.BARRIER) || state.is(Blocks.STRUCTURE_VOID)) return;
        ChunkAccess chunk = existingChunk(level, pos);
        // A full chunk's onPlace callbacks may immediately remove an unsupported template block.
        if (chunk != null && chunk.getBlockState(pos).is(state.getBlock())) record(chunk, pos);
    }

    /** Explicit provenance writer for the verified generation entry point and test fixtures. */
    public static void record(ChunkAccess chunk, BlockPos pos) {
        if (!belongsTo(chunk, pos)) return;
        if (chunk.getData(ORIGINS.get()).add(pos)) chunk.setUnsaved(true);
    }

    /** Reads only already available chunks; never searches a structure or loads its start chunk. */
    public static boolean isNatural(LevelReader level, BlockPos pos) {
        if (level.isClientSide() || level.isOutsideBuildHeight(pos)) return false;
        ChunkAccess chunk = existingChunk(level, pos);
        return chunk != null && isNatural(chunk, pos);
    }

    public static boolean isNatural(ChunkAccess chunk, BlockPos pos) {
        if (!belongsTo(chunk, pos)) return false;
        Origins origins = chunk.getExistingDataOrNull(ORIGINS.get());
        return origins != null && origins.contains(pos);
    }

    /** In-place properties and copper surface treatments retain provenance; removal does not. */
    public static void onBlockChanged(ChunkAccess chunk, BlockPos pos, BlockState previous, BlockState current) {
        if (previous == current || previous.is(current.getBlock()) || !isNatural(chunk, pos)) return;
        if (!current.isAir() && sameCopperFamily(previous.getBlock(), current.getBlock())) return;
        Origins origins = chunk.getExistingDataOrNull(ORIGINS.get());
        if (origins != null && origins.remove(pos)) {
            chunk.setUnsaved(true);
            if (origins.size() == 0) chunk.removeData(ORIGINS.get());
        }
    }

    private static boolean sameCopperFamily(Block previous, Block current) {
        Block previousUnwaxed = DataMapHooks.getBlockUnwaxed(previous);
        Block currentUnwaxed = DataMapHooks.getBlockUnwaxed(current);
        if (previousUnwaxed == null) previousUnwaxed = previous;
        if (currentUnwaxed == null) currentUnwaxed = current;
        return previousUnwaxed instanceof WeatheringCopper && currentUnwaxed instanceof WeatheringCopper
                && WeatheringCopper.getFirst(previousUnwaxed) == WeatheringCopper.getFirst(currentUnwaxed);
    }

    private static boolean belongsTo(ChunkAccess chunk, BlockPos pos) {
        return !chunk.isOutsideBuildHeight(pos) && chunk.getPos().x == pos.getX() >> 4 && chunk.getPos().z == pos.getZ() >> 4;
    }

    private static ChunkAccess existingChunk(LevelReader level, BlockPos pos) {
        int x = pos.getX() >> 4;
        int z = pos.getZ() >> 4;
        if (level instanceof ServerLevel server) return server.getChunkSource().getChunkNow(x, z);
        if (level instanceof WorldGenRegion region && region.hasChunk(x, z))
            return region.getChunk(x, z, ChunkStatus.EMPTY, false);
        return null;
    }
}
