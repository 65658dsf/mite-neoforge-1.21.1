package io.github.ningmeng123.mite.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.github.ningmeng123.mite.worldgen.NaturalTrialFallingBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Active R196 undermining, with persistent two-tick marks instead of legacy dirt metadata. @author NingMeng123 */
public final class CollapsePhysics {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mite.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Pending>> PENDING = TYPES.register("pending_collapses",
            () -> AttachmentType.builder(() -> new Pending()).serialize(Pending.CODEC).build());

    public record Check(long position, long due, long nativeDue, int priority) {
        public static final Codec<Check> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("position").forGetter(Check::position),
                Codec.LONG.fieldOf("due").forGetter(Check::due),
                Codec.LONG.optionalFieldOf("native_due", Long.MIN_VALUE).forGetter(Check::nativeDue),
                Codec.INT.optionalFieldOf("native_priority", 0).forGetter(Check::priority)
        ).apply(i, Check::new));
    }

    public static final class Pending {
        public static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1).fieldOf("version").forGetter(p -> 1),
                Check.CODEC.listOf().fieldOf("checks").forGetter(Pending::checks)
        ).apply(i, (version, checks) -> new Pending(checks)));
        private final Long2ObjectOpenHashMap<Check> checks = new Long2ObjectOpenHashMap<>();
        public Pending() {}
        private Pending(List<Check> entries) { entries.forEach(c -> checks.put(c.position(), c)); }
        public synchronized List<Check> checks() { return checks.values().stream().sorted(Comparator.comparingLong(Check::position)).toList(); }
        public synchronized Check get(BlockPos pos) { return checks.get(pos.asLong()); }
        private synchronized void put(Check check) { checks.put(check.position(), check); }
        private synchronized Check remove(BlockPos pos) { return checks.remove(pos.asLong()); }
    }

    private CollapsePhysics() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public static boolean managed(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SOUL_SAND);
    }

    private static LevelChunk loaded(ServerLevel level, BlockPos pos) {
        return level.isOutsideBuildHeight(pos) ? null : level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean glass(BlockState state) {
        return state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(Blocks.BEACON);
    }

    public static boolean solid(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.LILY_PAD)) return true;
        if (state.is(Blocks.SNOW)) return state.getValue(SnowLayerBlock.LAYERS) > 1;
        if (state.getBlock() instanceof net.minecraft.world.level.block.CocoaBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock
                || state.is(Blocks.MOVING_PISTON)) return true;
        if (state.getBlock() instanceof BaseRailBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.BasePressurePlateBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.LeverBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock
                || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)
            return false;
        // Legacy solidity includes fences, glass and slabs, rather than requiring a full upper face.
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean canDislodgeOrCrush(ServerLevel level, BlockPos pos, BlockState falling) {
        if (loaded(level, pos) == null) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BaseRailBlock || state.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock
                || state.is(Blocks.MOVING_PISTON)) return false;
        if (state.is(Blocks.LILY_PAD) || state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) > 1) return false;
        return !solid(level, pos, state) || state.getBlock() != falling.getBlock()
                && (io.github.ningmeng123.mite.mining.MiningProgress.effectiveHardness(state, state.getDestroySpeed(level, pos)) == 0F
                    || state.getBlock() instanceof net.minecraft.world.level.block.AbstractSkullBlock);
    }

    public static boolean canFallInto(ServerLevel level, BlockPos pos, BlockState falling) {
        // The lowest buildable layer can fall into the native void; never impose the old Y=0 floor.
        if (pos.getY() < level.getMinBuildHeight()) return true;
        if (loaded(level, pos) == null) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !solid(level, pos, state) || canDislodgeOrCrush(level, pos, falling);
    }

    public static boolean tryFall(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loaded(level, pos);
        if (chunk == null) return false;
        BlockState state = chunk.getBlockState(pos);
        if (!managed(state) || !canFallInto(level, pos.below(), state)) return false;
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
        if (!falling.isAddedToLevel()) {
            // Native entity join is cancellable. Restore only our own emptied source cell.
            if (level.getBlockState(pos).is(state.getFluidState().createLegacyBlock().getBlock())) {
                level.setBlock(pos, state, 3);
                if (((NaturalTrialFallingBlock)falling).mite$isNaturalTrialBlock()) NaturalTrialBlocks.record(chunk, pos);
            }
            return false;
        }
        schedule(level, pos.above());
        return true;
    }

    public static void undermine(ServerLevel level, BlockPos pos, Player player) {
        if (player != null && player.isCreative() || !tryFall(level, pos)) return;
        for (Direction direction : Direction.Plane.HORIZONTAL) schedule(level, pos.relative(direction));
    }

    public static void playerPlaced(ServerLevel level, BlockPos pos, Player player) { tryFall(level, pos); }

    public static void schedule(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loaded(level, pos);
        if (chunk == null) return;
        BlockState state = chunk.getBlockState(pos);
        if (!managed(state)) return;
        Pending data = chunk.getData(PENDING.get());
        if (data.get(pos) != null) return;
        var ticks = (net.minecraft.world.ticks.LevelChunkTicks<net.minecraft.world.level.block.Block>) chunk.getBlockTicks();
        ScheduledTick<net.minecraft.world.level.block.Block> previous = ticks.getAll()
                .filter(t -> t.pos().equals(pos) && t.type() == state.getBlock()).findFirst().orElse(null);
        // Soul sand can already own a later bubble-column tick. Do not let native de-duplication
        // postpone a two-tick collapse, and keep that original tick if the block stays supported.
        long due = level.getGameTime() + 2L;
        boolean preserveLater = previous != null && previous.triggerTick() >= due;
        if (preserveLater) ticks.removeIf(t -> t.pos().equals(pos) && t.type() == state.getBlock());
        data.put(new Check(pos.asLong(), due,
                preserveLater ? previous.triggerTick() : Long.MIN_VALUE, preserveLater ? previous.priority().getValue() : 0));
        chunk.setUnsaved(true);
        level.scheduleTick(pos, state.getBlock(), 2);
    }

    /** Return true when this is solely our scheduled check, rather than a native block callback. */
    public static boolean tick(ServerLevel level, BlockPos pos, BlockState state) {
        LevelChunk chunk = loaded(level, pos);
        if (chunk == null) return false;
        Pending data = chunk.getExistingDataOrNull(PENDING.get());
        Check check = data == null ? null : data.get(pos);
        if (check == null) return false;
        if (check.due() > level.getGameTime()) {
            level.scheduleTick(pos, state.getBlock(), (int)Math.min(check.due() - level.getGameTime(), Integer.MAX_VALUE));
            return false;
        }
        data.remove(pos);
        chunk.setUnsaved(true);
        undermine(level, pos, null);
        if (check.nativeDue() > level.getGameTime() && level.getBlockState(pos).is(state.getBlock()))
            level.scheduleTick(pos, state.getBlock(), (int)Math.clamp(check.nativeDue() - level.getGameTime(), 0L, Integer.MAX_VALUE),
                    TickPriority.byValue(check.priority()));
        return check.nativeDue() == Long.MIN_VALUE || check.nativeDue() > level.getGameTime();
    }

    public static void removed(Level level, BlockPos pos, BlockState previous, BlockState replacement) {
        if (!(level instanceof ServerLevel server) || !managed(previous) && !previous.is(Blocks.GRASS_BLOCK)
                || replacement.is(previous.getBlock())) return;
        LevelChunk chunk = loaded(server, pos);
        if (chunk == null) return;
        Pending data = chunk.getExistingDataOrNull(PENDING.get());
        if (data != null && data.remove(pos) != null) chunk.setUnsaved(true);
    }
}
