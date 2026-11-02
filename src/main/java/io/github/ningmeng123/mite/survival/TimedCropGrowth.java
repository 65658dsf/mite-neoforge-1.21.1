package io.github.ningmeng123.mite.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.rules.CropGrowthRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Chunk-persistent crop work; only native ticking chunks earn bounded growth time. @author NingMeng123 */
public final class TimedCropGrowth {
    public static final int UPDATE_INTERVAL = 20;
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mite.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Data>> CLOCKS = TYPES.register("timed_crop_growth",
            () -> AttachmentType.builder(() -> new Data()).serialize(Data.CODEC).build());
    // Empty chunks need no persistent attachment. A reloaded chunk receives a fresh migration scan.
    private static final Map<LevelChunk, Boolean> SCANNED = Collections.synchronizedMap(new WeakHashMap<>());

    public record Entry(long position, String block, long work) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("position").forGetter(Entry::position),
                Codec.STRING.fieldOf("block").forGetter(Entry::block),
                Codec.LONG.validate(work -> work >= 0L && work <= CropGrowthRules.TIMED_MATURITY_WORK
                        ? DataResult.success(work) : DataResult.error(() -> "Invalid crop growth work"))
                        .fieldOf("work").forGetter(Entry::work)
        ).apply(instance, Entry::new));
    }

    private record Timing(long accountedAt, long due) {}
    private record Due(Entry entry, long accountedAt) {}
    private record Provisional(BlockState previous, boolean restored, boolean replaced) {}

    /** Only immutable entry snapshots are serialized; scheduling and provisional placements are transient. */
    public static final class Data {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 1).fieldOf("version").forGetter(data -> 1),
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Data::entries)
        ).apply(instance, (version, entries) -> new Data(entries)));
        private final Map<Long, Entry> crops = new HashMap<>();
        private final Map<Long, Timing> timings = new HashMap<>();
        private final List<Set<Long>> wheel = new ArrayList<>(UPDATE_INTERVAL);
        private final Map<Long, Provisional> provisional = new HashMap<>();
        private long lastNativeTick = Long.MIN_VALUE;
        private boolean randomTicksEnabled = true;

        public Data() { this(List.of()); }
        public Data(List<Entry> entries) {
            for (int index = 0; index < UPDATE_INTERVAL; index++) wheel.add(new HashSet<>());
            for (Entry entry : entries) crops.put(entry.position(), new Entry(entry.position(), entry.block(),
                    Math.clamp(entry.work(), 0L, CropGrowthRules.TIMED_MATURITY_WORK)));
        }
        public synchronized List<Entry> entries() {
            return crops.values().stream().sorted(Comparator.comparingLong(Entry::position)).toList();
        }
        private synchronized Entry get(long pos) { return crops.get(pos); }
        private synchronized boolean empty() { return crops.isEmpty() && provisional.isEmpty(); }
        private synchronized void provisional(BlockPos pos, BlockState previous, BlockState current, boolean restoring) {
            Provisional old = provisional.get(pos.asLong());
            boolean replaced = managed(previous) && (!previous.is(current.getBlock())
                    || managed(current) && age(current) < age(previous));
            provisional.put(pos.asLong(), new Provisional(old == null ? previous : old.previous(),
                    restoring || old != null && old.restored(), replaced || old != null && old.replaced()));
        }
        private synchronized Map<Long, Provisional> takeProvisional() {
            Map<Long, Provisional> result = new HashMap<>(provisional);
            provisional.clear();
            return result;
        }
        private synchronized void enroll(Entry entry, long now) {
            remove(entry.position());
            crops.put(entry.position(), entry);
            schedule(entry.position(), now);
            if (lastNativeTick == Long.MIN_VALUE) lastNativeTick = now;
        }
        private synchronized boolean remove(long pos) {
            Timing old = timings.remove(pos);
            if (old != null) wheel.get(slot(old.due())).remove(pos);
            return crops.remove(pos) != null;
        }
        private synchronized void schedule(long pos, long now) {
            Timing old = timings.put(pos, new Timing(now, now + UPDATE_INTERVAL));
            if (old != null) wheel.get(slot(old.due())).remove(pos);
            wheel.get(slot(now + UPDATE_INTERVAL)).add(pos);
        }
        private synchronized void rebase(long now) {
            timings.clear();
            wheel.forEach(Set::clear);
            for (long pos : crops.keySet()) schedule(pos, now);
        }
        private synchronized boolean beginTick(long now, boolean enabled) {
            if (lastNativeTick == now) return false;
            boolean continuous = lastNativeTick != Long.MIN_VALUE && now == lastNativeTick + 1L;
            lastNativeTick = now;
            if (!continuous) {
                rebase(now);
                randomTicksEnabled = enabled;
                return false;
            }
            if (!enabled) {
                if (randomTicksEnabled) rebase(now);
                randomTicksEnabled = false;
                return false;
            }
            if (!randomTicksEnabled) rebase(now - 1L);
            randomTicksEnabled = true;
            return true;
        }
        private synchronized List<Due> due(long now) {
            Set<Long> slot = wheel.get(slot(now));
            List<Long> positions = List.copyOf(slot);
            slot.clear();
            List<Due> result = new ArrayList<>();
            for (long pos : positions) {
                Timing timing = timings.get(pos);
                Entry entry = crops.get(pos);
                if (timing == null || entry == null) continue;
                if (timing.due() > now) {
                    wheel.get(slot(timing.due())).add(pos);
                    continue;
                }
                timings.remove(pos);
                result.add(new Due(entry, timing.accountedAt()));
            }
            return result;
        }
        private synchronized Entry advance(Entry previous, long ticks, float rate) {
            if (crops.get(previous.position()) != previous) return null;
            long work = CropGrowthRules.advanceTimedWork(previous.work(), ticks, rate);
            Entry updated = work == previous.work() ? previous : new Entry(previous.position(), previous.block(), work);
            crops.put(updated.position(), updated);
            return updated;
        }
        private synchronized void reschedule(Entry expected, long now) {
            if (crops.get(expected.position()) == expected) schedule(expected.position(), now);
        }
    }

    private TimedCropGrowth() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private static int slot(long time) { return Math.floorMod(time, UPDATE_INTERVAL); }

    /** Food crops and unconnected melon/pumpkin stems, with no changes to other special plants. */
    public static boolean managed(BlockState state) {
        return CropLifecycle.managed(state) || state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM);
    }
    private static int age(BlockState state) {
        return state.getBlock() instanceof CropBlock crop ? crop.getAge(state) : state.getValue(StemBlock.AGE);
    }
    private static int maxAge(BlockState state) {
        return state.getBlock() instanceof CropBlock crop ? crop.getMaxAge() : 7;
    }
    private static boolean dead(BlockState state) {
        return CropLifecycle.managed(state) ? CubeCropBlight.dead(state) : SpecialPlantGrowth.dead(state);
    }
    private static boolean tracked(BlockState state) {
        return managed(state) && !dead(state) && age(state) < maxAge(state);
    }
    private static String blockId(BlockState state) { return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(); }
    private static long stageWork(BlockState state) {
        return CropGrowthRules.TIMED_MATURITY_WORK * age(state) / maxAge(state);
    }
    private static boolean belongs(LevelChunk chunk, BlockPos pos) {
        return !chunk.isOutsideBuildHeight(pos) && pos.getX() >> 4 == chunk.getPos().x && pos.getZ() >> 4 == chunk.getPos().z;
    }
    private static float rate(ServerLevel level, BlockPos pos, BlockState state) {
        return CropLifecycle.managed(state) ? CropLifecycle.growthRate(state, level, pos) : SpecialPlantGrowth.stemGrowth(level, pos, state);
    }
    private static boolean eligible(ServerLevel level, BlockPos pos, BlockState state) {
        return !dead(state) && !CubeCropBlight.blighted(state) && level.isAreaLoaded(pos, 2)
                && state.canSurvive(level, pos) && level.getMaxLocalRawBrightness(pos.above()) == 15;
    }

    /** Seeding/migration infers only already visible stages; an ordinary newly planted age-zero crop starts at zero. */
    private static void enroll(LevelChunk chunk, BlockPos pos, BlockState state, long now) {
        Data data = chunk.getData(CLOCKS.get());
        long work = stageWork(state);
        data.enroll(new Entry(pos.asLong(), blockId(state), work), now);
        chunk.setUnsaved(true);
    }

    /** Observe the actual result after chunk replacement, including explosions, harvesters and commands. */
    public static void changed(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState current) {
        if (!(chunk.getLevel() instanceof ServerLevel level) || !belongs(chunk, pos)
                || previous == current || !managed(previous) && !managed(current)) return;
        if (level.captureBlockSnapshots || level.restoringBlockSnapshots) {
            // Snapshot commits do not call setBlockState again. Reconcile their final result on the next native tick.
            chunk.getData(CLOCKS.get()).provisional(pos, previous, current, level.restoringBlockSnapshots);
            return;
        }
        committed(chunk, pos, previous, current, level.getGameTime());
    }

    private static void committed(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState current, long now) {
        Data data = chunk.getExistingDataOrNull(CLOCKS.get());
        Entry entry = data == null ? null : data.get(pos.asLong());
        if (!tracked(current)) {
            if (data != null && data.remove(pos.asLong())) chunk.setUnsaved(true);
            return;
        }
        boolean samePlant = managed(previous) && previous.is(current.getBlock()) && age(current) >= age(previous);
        if (!samePlant || entry == null || !entry.block().equals(blockId(current))) enroll(chunk, pos, current, now);
    }

    private static void migrate(LevelChunk chunk, long now) {
        for (int index = 0; index < chunk.getSections().length; index++) {
            LevelChunkSection section = chunk.getSections()[index];
            if (!section.maybeHas(TimedCropGrowth::tracked)) continue;
            int minY = chunk.getMinBuildHeight() + index * 16;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                BlockState state = section.getBlockState(x, y, z);
                if (!tracked(state)) continue;
                BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, minY + y, chunk.getPos().getMinBlockZ() + z);
                Data data = chunk.getExistingDataOrNull(CLOCKS.get());
                Entry entry = data == null ? null : data.get(pos.asLong());
                if (entry == null || !entry.block().equals(blockId(state))) enroll(chunk, pos, state, now);
            }
        }
    }

    /** Public for runtime regression fixtures; callers must supply each actual consecutive native chunk tick. */
    public static void tickChunk(ServerLevel level, LevelChunk chunk) {
        long now = level.getGameTime();
        Data data = chunk.getExistingDataOrNull(CLOCKS.get());
        if (data != null) {
            for (var pending : data.takeProvisional().entrySet()) {
                BlockPos pos = BlockPos.of(pending.getKey());
                if (!belongs(chunk, pos)) continue;
                BlockState current = chunk.getBlockState(pos);
                Provisional snapshot = pending.getValue();
                if (snapshot.restored() && current == snapshot.previous()) continue;
                if (current != snapshot.previous() || snapshot.replaced()) committed(chunk, pos,
                        snapshot.replaced() ? Blocks.AIR.defaultBlockState() : snapshot.previous(), current, now);
            }
        }
        if (SCANNED.putIfAbsent(chunk, Boolean.TRUE) == null) migrate(chunk, now);
        data = chunk.getExistingDataOrNull(CLOCKS.get());
        if (data == null) return;
        if (data.empty()) {
            chunk.removeData(CLOCKS.get());
            chunk.setUnsaved(true);
            return;
        }
        boolean enabled = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING) > 0;
        if (!data.beginTick(now, enabled)) return;
        for (Due due : data.due(now)) {
            BlockPos pos = BlockPos.of(due.entry().position());
            BlockState state = belongs(chunk, pos) ? chunk.getBlockState(pos) : Blocks.AIR.defaultBlockState();
            if (!tracked(state) || !due.entry().block().equals(blockId(state))) {
                if (data.remove(pos.asLong())) chunk.setUnsaved(true);
                continue;
            }
            long elapsed = Math.clamp(now - due.accountedAt(), 0L, (long)UPDATE_INTERVAL);
            float growth = eligible(level, pos, state) ? rate(level, pos, state) : 0F;
            Entry updated = data.advance(due.entry(), elapsed, growth);
            if (updated == null) continue;
            if (updated.work() != due.entry().work()) chunk.setUnsaved(true);
            int targetAge = CropGrowthRules.timedAge(updated.work(), maxAge(state));
            if (growth > 0F && targetAge > age(state) && CommonHooks.canCropGrow(level, pos, state, true)) {
                // Preserve other mods' synchronous changes made during the cancellable growth callback.
                if (chunk.getBlockState(pos) == state) {
                    BlockState grown = state.getBlock() instanceof CropBlock crop ? crop.getStateForAge(targetAge)
                            : state.setValue(StemBlock.AGE, targetAge);
                    if (level.setBlock(pos, grown, 2)) {
                        FoodAgriculture.onCropGrowth(level, pos.below(), level.random);
                        CommonHooks.fireCropGrowPost(level, pos, state);
                    }
                }
            }
            data.reschedule(updated, now);
        }
        if (data.empty()) {
            chunk.removeData(CLOCKS.get());
            chunk.setUnsaved(true);
        }
    }

    /** Raw persistent work, without creating a clock or loading a chunk. */
    public static long work(LevelChunk chunk, BlockPos pos) {
        Data data = chunk.getExistingDataOrNull(CLOCKS.get());
        Entry entry = data == null ? null : data.get(pos.asLong());
        return entry == null ? -1L : entry.work();
    }

    /** Jade reads the last committed work; the server clock, never inspection, advances plants. */
    public static long remainingTicks(ServerLevel level, BlockPos pos, BlockState state) {
        if (!managed(state) || dead(state) || level.isOutsideBuildHeight(pos)) return -1L;
        if (age(state) >= maxAge(state)) return 0L;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.getBlockState(pos) != state || !level.isAreaLoaded(pos, 2)) return -1L;
        Data data = chunk.getExistingDataOrNull(CLOCKS.get());
        Entry entry = data == null ? null : data.get(pos.asLong());
        long work = entry == null || !entry.block().equals(blockId(state)) ? stageWork(state) : entry.work();
        return CropGrowthRules.timedRemainingTicks(work, rate(level, pos, state));
    }
}
