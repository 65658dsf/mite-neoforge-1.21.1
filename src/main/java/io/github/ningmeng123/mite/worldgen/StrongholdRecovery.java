package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.mixin.ChunkMapAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Lost finite candidates move to untouched chunks after their ordinary unlock. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class StrongholdRecovery {
    private static final ResourceLocation STRONGHOLD = ResourceLocation.withDefaultNamespace("stronghold");
    private static final int CANDIDATE_CHECKS_PER_SECOND = 4;
    private static final ConcurrentMap<ServerLevel, State> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ChunkGeneratorStructureState, Published> PUBLISHED = new ConcurrentHashMap<>();

    private record Published(ConcentricRingsStructurePlacement placement, List<ChunkPos> positions) {}

    private static final class State {
        final ChunkGeneratorStructureState generator;
        final ConcentricRingsStructurePlacement placement;
        final Holder<Structure> stronghold;
        final List<ChunkPos> originals;
        final StrongholdRecoveryData data;
        final AtomicBoolean needsCheck = new AtomicBoolean(true);
        final Set<ChunkPos> completed = ConcurrentHashMap.newKeySet();
        long nextCheckAt = Long.MIN_VALUE;
        State(ServerLevel level, ConcentricRingsStructurePlacement placement, List<ChunkPos> originals) {
            generator = level.getChunkSource().getGeneratorState();
            this.placement = placement;
            this.originals = List.copyOf(originals);
            stronghold = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(STRONGHOLD).orElseThrow();
            data = level.getDataStorage().computeIfAbsent(StrongholdRecoveryData.FACTORY, StrongholdRecoveryData.NAME);
        }
        void publish() {
            PUBLISHED.put(generator, new Published(placement, data.positions(originals)));
        }
    }

    private StrongholdRecovery() {}

    /** Native placement checks, locating and ender eyes all consume this same immutable list. */
    public static List<ChunkPos> positions(ChunkGeneratorStructureState generator,
            ConcentricRingsStructurePlacement placement, List<ChunkPos> original) {
        Published published = PUBLISHED.get(generator);
        return published != null && published.placement() == placement && placement.count() != 0
                ? published.positions() : original;
    }

    /** Watch only finite candidates, behind the native main-thread presence-cache update. */
    public static void observed(ServerLevel level, ChunkPos coordinate) {
        State state = STATES.get(level);
        if (state == null) return;
        Published published = PUBLISHED.get(state.generator);
        if (published != null && published.positions().contains(coordinate)) {
            level.getServer().execute(() -> {
                Published current = PUBLISHED.get(state.generator);
                if (STATES.get(level) != state || current == null || !current.positions().contains(coordinate)) return;
                state.completed.add(coordinate);
                state.needsCheck.set(true);
            });
        }
    }

    private static void initialize(ServerLevel level) {
        if (STATES.containsKey(level)) return;
        var generator = level.getChunkSource().getGeneratorState();
        for (var set : generator.possibleStructureSets()) {
            if (!set.is(BuiltinStructureSets.STRONGHOLDS)
                    || !(set.value().placement() instanceof ConcentricRingsStructurePlacement placement)
                    || placement.count() == 0) continue;
            List<ChunkPos> originals = generator.getRingPositionsFor(placement);
            if (originals == null || originals.isEmpty()) return;
            State state = new State(level, placement, originals);
            STATES.put(level, state);
            state.publish();
            return;
        }
    }

    /** A missing stored start consumes a slot; an ungenerated candidate remains in its original slot. */
    public static void recover(ServerLevel level) {
        if (!level.getServer().isSameThread()) return;
        initialize(level);
        State state = STATES.get(level);
        if (state == null || !level.getServer().getWorldData().worldGenOptions().generateStructures()
                || !R196StructureGeneration.unlocked(level, state.stronghold)
                || level.getGameTime() < state.nextCheckAt
                || !state.needsCheck.getAndSet(false)) return;
        state.nextCheckAt = level.getGameTime() + 20L;
        List<ChunkPos> current = new ArrayList<>(state.data.positions(state.originals));
        int checks = 0;
        for (int slot = 0; slot < current.size(); slot++) {
            ChunkPos coordinate = current.get(slot);
            if (presence(level, state, coordinate) != StructureCheckResult.START_NOT_PRESENT
                    || !completedStarts(level, state, coordinate)) continue;
            boolean replaced = false;
            while (checks < CANDIDATE_CHECKS_PER_SECOND && !replaced) {
                checks++;
                ChunkPos candidate = candidate(level.getSeed(), slot, state.data.nextAttempt(slot), state.originals.get(slot));
                if (current.contains(candidate) || !level.getWorldBorder().isWithinBounds(candidate)
                        || !untouched(level, candidate)) continue;
                if (presence(level, state, candidate) != StructureCheckResult.CHUNK_LOAD_NEEDED) continue;
                state.data.replace(slot, candidate);
                state.completed.remove(coordinate);
                current.set(slot, candidate);
                state.publish();
                state.needsCheck.set(true);
                replaced = true;
            }
            if (!replaced) state.needsCheck.set(true);
        }
    }

    private static StructureCheckResult presence(ServerLevel level, State state, ChunkPos coordinate) {
        return level.structureManager().checkStructurePresence(coordinate, state.stronghold.value(), state.placement, false);
    }

    private static boolean untouched(ServerLevel level, ChunkPos coordinate) {
        var chunks = level.getChunkSource().chunkMap;
        var access = (ChunkMapAccess)chunks;
        long packed = coordinate.toLong();
        if (chunks.getVisibleChunkIfPresent(packed) != null || access.mite$updatingChunks().containsKey(packed)
                || access.mite$pendingUnloads().containsKey(packed)) return false;
        try {
            // CHUNK_LOAD_NEEDED also represents old/unknown stored NBT. Require a genuinely absent chunk.
            return chunks.read(coordinate).join().isEmpty();
        } catch (RuntimeException failure) {
            Mite.LOGGER.warn("Cannot inspect stronghold recovery candidate {}", coordinate, failure);
            return false;
        }
    }

    private static boolean completedStarts(ServerLevel level, State state, ChunkPos coordinate) {
        if (state.completed.contains(coordinate)) return true;
        var chunks = level.getChunkSource().chunkMap;
        var holder = chunks.getVisibleChunkIfPresent(coordinate.toLong());
        if (holder != null && holder.getChunkIfPresent(ChunkStatus.STRUCTURE_STARTS) != null) return true;
        try {
            return chunks.read(coordinate).join().map(tag -> {
                ChunkStatus status = ChunkStatus.byName(tag.getString("Status"));
                return status != null && status.isOrAfter(ChunkStatus.STRUCTURE_STARTS);
            }).orElse(false);
        } catch (RuntimeException failure) {
            Mite.LOGGER.warn("Cannot inspect consumed stronghold candidate {}", coordinate, failure);
            return false;
        }
    }

    /** A deterministic square spiral leaves the old candidate and its surrounding structures intact. */
    public static ChunkPos candidate(long seed, int slot, int attempt, ChunkPos original) {
        int ring = (int)Math.ceil((Math.sqrt((double)attempt + 1D) - 1D) / 2D);
        long side = 2L * ring, maximum = (2L * ring + 1L) * (2L * ring + 1L) - 1L;
        long offset = maximum - attempt;
        int x = ring, z = -ring;
        if (offset < side) {
            x -= (int)offset;
        } else if (offset < side * 2L) {
            x = -ring;
            z += (int)(offset - side);
        } else if (offset < side * 3L) {
            x = -ring + (int)(offset - side * 2L);
            z = ring;
        } else {
            z = ring - (int)(offset - side * 3L);
        }
        int rotation = (int)(seed ^ original.toLong() ^ slot) & 3;
        for (int turn = 0; turn < rotation; turn++) {
            int previousX = x;
            x = -z;
            z = previousX;
        }
        return new ChunkPos(original.x + x * 64, original.z + z * 64);
    }

    @SubscribeEvent
    public static void load(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            initialize(level);
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && Math.floorMod(level.getGameTime(), 20L) == 0L) {
            recover(level);
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            State removed = STATES.remove(level);
            if (removed != null) {
                PUBLISHED.remove(removed.generator);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STATES.forEach((level, state) -> {
            if (level.getServer() == event.getServer()) {
                PUBLISHED.remove(state.generator);
                STATES.remove(level, state);
            }
        });
    }
}
