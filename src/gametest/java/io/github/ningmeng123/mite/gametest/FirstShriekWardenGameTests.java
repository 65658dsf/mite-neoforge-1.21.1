package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.worldgen.NaturalShriekerOres;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native warning admission, shared trackers and real90-tick scheduled responses. @author NingMeng123 */
@GameTestHolder("mite_shrieker_ores")
@PrefixGameTestTemplate(false)
public final class FirstShriekWardenGameTests {
    private static final int TIMEOUT = 240;

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void firstEffectiveShriekSharesMaximumWarningAndSpawnsThroughTheNativeNinetyTickResponse(GameTestHelper h) {
        Fixture f = new Fixture(h, 0, false, 2);
        h.runAtTickTime(TIMEOUT-1, f::close);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(f.level.isPositionEntityTicking(f.pos), "The real shrieker chunk is ticking"))
                .thenExecute(() -> {
                    h.assertTrue(tracker(f.players.getFirst()).getWarningLevel() == 0 && tracker(f.players.getLast()).getWarningLevel() == 0,
                            "Both nearby players begin with their actual first-warning trackers");
                    f.shrieker().tryShriek(f.level, f.players.getFirst());
                    h.assertValueEqual(f.warning(), 4, "The first admitted native warning reaches the summoning threshold");
                    for (var player : f.players) {
                        h.assertValueEqual(tracker(player).getWarningLevel(), 4, "Native nearby-player copying shares the full warning");
                        h.assertValueEqual(trackerTag(player).getInt("cooldown_ticks"), 200, "The native shared cooldown is preserved");
                    }
                    h.assertTrue(f.level.getBlockState(f.pos).getValue(SculkShriekerBlock.SHRIEKING), "The first shriek enters the native block state");
                    h.assertTrue(f.wardens.isEmpty(), "Reaching warning4 does not directly inject a warden");
                }).thenIdle(89).thenExecute(() -> h.assertTrue(f.wardens.isEmpty(), "A warden cannot appear before the original90-tick response"))
                .thenIdle(2).thenExecute(() -> {
                    h.assertValueEqual(f.wardens.size(), 1, "The native scheduled block tick adds exactly one actual warden after the first shriek");
                    Warden warden = f.wardens.getFirst();
                    h.assertTrue(warden.isAddedToLevel() && !warden.isRemoved(), "The summoned warden really entered the server level");
                    h.assertValueEqual(warden.getSpawnType(), MobSpawnType.TRIGGERED, "Native SpawnUtil preserves the triggered spawn reason");
                    h.assertValueEqual(warden.getPose(), Pose.EMERGING, "The warden retains its native emergence state");
                    h.assertFalse(f.level.getBlockState(f.pos).getValue(SculkShriekerBlock.SHRIEKING), "The native scheduled response ends the shrieking state");
                    h.assertValueEqual(f.rewarded(), f.originalRewards, "Runtime shrieks never create additional natural-shrieker ore rewards");
                    f.close();
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void firstWarningStillCannotSummonWithoutAValidNativeSpawnPosition(GameTestHelper h) {
        Fixture f = new Fixture(h, 1, true, 1);
        h.runAtTickTime(TIMEOUT-1, f::close);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(f.level.isPositionEntityTicking(f.pos), "The obstructed shrieker chunk is ticking"))
                .thenExecute(() -> {
                    f.shrieker().tryShriek(f.level, f.players.getFirst());
                    h.assertValueEqual(f.warning(), 4, "An admitted first warning still reaches4 inside an obstructed room");
                }).thenIdle(91).thenExecute(() -> {
                    h.assertTrue(f.wardens.isEmpty(), "Native SpawnUtil rejects every obstructed candidate instead of forcing a warden into blocks");
                    h.assertFalse(f.level.getBlockState(f.pos).getValue(SculkShriekerBlock.SHRIEKING), "The real scheduled response still completes");
                    f.close();
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void sharedPlayerCooldownBlocksAnImmediateSecondShriekerWithoutChangingItsWarningState(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2, false, 2)) {
            f.shrieker().tryShriek(f.level, f.players.getFirst());
            BlockPos second = f.pos.east(2);
            f.level.setBlock(second, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true), 3);
            var other = (SculkShriekerBlockEntity)f.level.getBlockEntity(second);
            other.tryShriek(f.level, f.players.getLast());
            h.assertFalse(f.level.getBlockState(second).getValue(SculkShriekerBlock.SHRIEKING), "A nearby player's existing200-tick cooldown blocks a second native warning");
            h.assertValueEqual(other.saveWithoutMetadata(f.level.registryAccess()).getInt("warning_level"), 0, "A rejected warning never arms another shrieker");
            h.assertTrue(f.wardens.isEmpty(), "A rejected second warning cannot directly create a warden");
            for (var player : f.players) h.assertValueEqual(trackerTag(player).getInt("cooldown_ticks"), 200,
                    "The rejected warning does not refresh or bypass either player's shared cooldown");
            for (int tick = 0; tick < 200; tick++) for (var player : f.players) tracker(player).tick();
            other.tryShriek(f.level, f.players.getLast());
            h.assertTrue(f.level.getBlockState(second).getValue(SculkShriekerBlock.SHRIEKING), "The next first effective warning is admitted when the original cooldown actually expires");
            h.assertValueEqual(other.saveWithoutMetadata(f.level.registryAccess()).getInt("warning_level"), 4, "A later admitted warning uses the same threshold");
            // Disable both pending responses before restoring the fixture's original blocks.
            f.level.setBlock(second, f.level.getBlockState(second).setValue(SculkShriekerBlock.SHRIEKING, false), 3);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void placedShriekersAndDisabledWardenSpawningStayHarmlessWhileFixedHardRemainsAuthoritative(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3, false, 1)) {
            var player = f.players.getFirst();
            var rule = f.level.getGameRules().getRule(GameRules.RULE_DO_WARDEN_SPAWNING);
            boolean savedRule = rule.get();
            Difficulty savedDifficulty = f.level.getDifficulty();
            try {
                for (int guard = 0; guard < 2; guard++) {
                    tracker(player).reset();
                    rule.set(guard == 0, f.level.getServer());
                    f.level.setBlock(f.pos, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, guard != 0), 3);
                    f.shrieker().tryShriek(f.level, player);
                    h.assertTrue(f.level.getBlockState(f.pos).getValue(SculkShriekerBlock.SHRIEKING), "An ineligible shrieker keeps its native harmless shriek");
                    h.assertValueEqual(f.warning(), 0, "CAN_SUMMON=false or doWardenSpawning=false cannot arm a response");
                    h.assertValueEqual(tracker(player).getWarningLevel(), 0, "An ineligible source does not spend a native player warning");
                    h.assertValueEqual(trackerTag(player).getInt("cooldown_ticks"), 0, "An ineligible source does not start a cooldown");
                    f.level.setBlock(f.pos, f.level.getBlockState(f.pos).setValue(SculkShriekerBlock.SHRIEKING, false), 3);
                }
                h.assertTrue(f.wardens.isEmpty(), "Neither actual source guard failure creates a warden");
                // This mod intentionally locks server/world difficulty to HARD, including calls requesting Peaceful.
                // Verify that real authority instead of pretending setDifficulty produced a Peaceful fixture.
                f.level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                h.assertValueEqual(f.level.getDifficulty(), Difficulty.HARD, "The existing fixed-Hard policy remains authoritative");
                rule.set(true, f.level.getServer()); tracker(player).reset();
                f.level.setBlock(f.pos, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true), 3);
                f.shrieker().tryShriek(f.level, player);
                h.assertValueEqual(f.warning(), 4, "A real Hard-world first warning remains eligible after a rejected difficulty change");
            } finally {
                rule.set(savedRule, f.level.getServer());
                f.level.getServer().setDifficulty(savedDifficulty, true);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void aNearbyExistingWardenStillRejectsTheFirstWarning(GameTestHelper h) {
        Fixture f = new Fixture(h, 4, false, 1);
        h.runAtTickTime(TIMEOUT-1, f::close);
        Warden existing = EntityType.WARDEN.create(f.level);
        existing.setPos(f.pos.east(4).getCenter()); existing.setNoAi(true); existing.setNoGravity(true);
        h.assertTrue(f.level.addFreshEntity(existing), "The existing warden is accepted into the actual level");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(f.level.getEntitiesOfClass(Warden.class,
                new net.minecraft.world.phys.AABB(f.pos).inflate(20)).contains(existing), "The existing warden is visible to native warning queries"))
                .thenExecute(() -> {
                    f.shrieker().tryShriek(f.level, f.players.getFirst());
                    h.assertFalse(f.level.getBlockState(f.pos).getValue(SculkShriekerBlock.SHRIEKING), "Native nearby-warden exclusion prevents another shriek from arming");
                    h.assertValueEqual(tracker(f.players.getFirst()).getWarningLevel(), 0, "A nearby warden prevents even the first warning increase");
                    h.assertValueEqual(f.wardens.size(), 1, "Only the already existing warden remains");
                    f.close();
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void nullPlayerAndMissingTrackerCannotBypassNativeWarningAdmission(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 5, false, 0)) {
            f.shrieker().tryShriek(f.level, null);
            h.assertFalse(f.level.getBlockState(f.pos).getValue(SculkShriekerBlock.SHRIEKING), "A null responsible player cannot start a native shriek");
            ServerPlayer noTracker = new ServerPlayer(f.level.getServer(), f.level,
                    new GameProfile(UUID.randomUUID(), "mite-no-tracker"), ClientInformation.createDefault()) {
                @Override public Optional<WardenSpawnTracker> getWardenSpawnTracker() { return Optional.empty(); }
            };
            try {
                noTracker.setPos(f.pos.getCenter());
                f.shrieker().tryShriek(f.level, noTracker);
                h.assertFalse(f.level.getBlockState(f.pos).getValue(SculkShriekerBlock.SHRIEKING), "An absent native tracker leaves tryWarn empty");
                h.assertValueEqual(f.warning(), 0, "No tracker means no armed warning state");
                h.assertTrue(f.wardens.isEmpty(), "Missing warning context never directly injects a warden");
            } finally { noTracker.discard(); }
        }
        h.succeed();
    }

    private static WardenSpawnTracker tracker(ServerPlayer player) { return player.getWardenSpawnTracker().orElseThrow(); }
    private static CompoundTag trackerTag(ServerPlayer player) {
        return (CompoundTag)WardenSpawnTracker.CODEC.encodeStart(NbtOps.INSTANCE, tracker(player)).getOrThrow();
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<Warden> wardens = new ArrayList<>();
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final Set<Long> tickets = new HashSet<>();
        final Consumer<EntityJoinLevelEvent> listener;
        final int originalRewards;
        boolean closed;
        Fixture(GameTestHelper h, int index, boolean obstructed, int count) {
            level = h.getLevel();
            ChunkPos center = new ChunkPos(1910 + index * 4, -1910);
            pos = center.getMiddleBlockPosition(81);
            if (!level.getForcedChunks().contains(center.toLong())) { level.setChunkForced(center.x, center.z, true); tickets.add(center.toLong()); }
            for (int x = center.x-2; x <= center.x+2; x++) for (int z = center.z-2; z <= center.z+2; z++)
                level.getChunk(x, z, ChunkStatus.FULL, true);
            for (BlockPos at : BlockPos.betweenClosed(pos.offset(-7,-6,-7), pos.offset(7,7,7))) {
                previous.put(at.immutable(), level.getBlockState(at));
                level.setBlock(at, (obstructed || at.getY() <= pos.getY()-1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            }
            level.setBlock(pos, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true), 3);
            originalRewards = rewarded();
            listener = event -> {
                if (event.getLevel() == level && event.getEntity() instanceof Warden warden
                        && warden.blockPosition().distSqr(pos) < 2500) {
                    warden.setNoAi(true); warden.setNoGravity(true); wardens.add(warden);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, listener);
            for (int i = 0; i < count; i++) {
                ServerPlayer player = h.makeMockServerPlayerInLevel();
                player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
                player.setPos(pos.getX()+2+i*2, pos.getY()+4, pos.getZ());
                tracker(player).reset(); players.add(player);
            }
        }
        SculkShriekerBlockEntity shrieker() { return (SculkShriekerBlockEntity)level.getBlockEntity(pos); }
        int warning() { return shrieker().saveWithoutMetadata(level.registryAccess()).getInt("warning_level"); }
        int rewarded() { return level.getChunkAt(pos).getData(NaturalShriekerOres.REWARDED).size(); }
        @Override public void close() {
            if (closed) return; closed = true;
            // Removing an actively shrieking block normally invokes its response; cleanup must first stop that state.
            for (var entry : previous.entrySet()) {
                var state = level.getBlockState(entry.getKey());
                if (state.is(Blocks.SCULK_SHRIEKER) && state.getValue(SculkShriekerBlock.SHRIEKING))
                    level.setBlock(entry.getKey(), state.setValue(SculkShriekerBlock.SHRIEKING, false), 3);
            }
            NeoForge.EVENT_BUS.unregister(listener);
            wardens.forEach(Entity::discard);
            for (var player : players) level.getServer().getPlayerList().remove(player);
            previous.forEach((at, state) -> level.setBlock(at, state, 2));
            for (long ticket : tickets) { ChunkPos chunk = new ChunkPos(ticket); level.setChunkForced(chunk.x, chunk.z, false); }
        }
    }
}
