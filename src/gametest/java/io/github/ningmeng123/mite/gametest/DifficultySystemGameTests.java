package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import io.github.ningmeng123.mite.world.DifficultySystem;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.DifficultyChangeEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Saved level policy, official server/command/network entry points and modern-height regional tension. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DifficultySystemGameTests {
    private static PrimaryLevelData data(GameType gameType, boolean hardcore, Difficulty difficulty) {
        LevelSettings settings = new LevelSettings("mite-difficulty-test", gameType, hardcore, difficulty,
                true, new GameRules(), WorldDataConfiguration.DEFAULT);
        return new PrimaryLevelData(settings, new WorldOptions(196L, true, false),
                PrimaryLevelData.SpecialWorldProperty.NONE, Lifecycle.stable());
    }

    private static void fixed(GameTestHelper helper, WorldData data, String message) {
        helper.assertValueEqual(data.getDifficulty(), Difficulty.HARD, message + ": fixed hard difficulty");
        helper.assertTrue(data.isDifficultyLocked(), message + ": fixed lock");
    }

    private static void close(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .00001F, message + ": " + actual + " vs " + expected);
    }

    @GameTest(template = "empty")
    public static void normalizeMigratesEveryOldDifficultyWithoutChangingGameModeOrHardcore(GameTestHelper helper) {
        for (GameType gameType : GameType.values()) for (boolean hardcore : new boolean[]{false, true})
            for (Difficulty difficulty : Difficulty.values()) {
                PrimaryLevelData data = data(gameType, hardcore, difficulty);
                data.setDifficultyLocked(false);
                DifficultySystem.normalize(data);
                DifficultySystem.normalize(data);
                fixed(helper, data, "Normalization is idempotent for an independent old world");
                helper.assertValueEqual(data.getGameType(), gameType, "Difficulty does not change creative/survival/spectator/adventure policy");
                helper.assertTrue(data.isHardcore() == hardcore, "Difficulty locking never enables or disables hardcore");
                helper.assertTrue(data.isAllowCommands(), "The existing command permission setting is retained");
                helper.assertValueEqual(data.worldGenOptions().seed(), 196L, "Difficulty migration does not alter the seed");
            }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fixedPolicySurvivesRealLevelDatSerializationAndParsing(GameTestHelper helper) throws Exception {
        for (boolean hardcore : new boolean[]{false, true}) {
            PrimaryLevelData original = data(GameType.CREATIVE, hardcore, Difficulty.EASY);
            DifficultySystem.normalize(original);
            CompoundTag saved = original.createTag(helper.getLevel().registryAccess(), null);
            helper.assertValueEqual(saved.getByte("Difficulty"), (byte)3, "Actual level.dat serializer writes R196 hard id");
            helper.assertTrue(saved.getBoolean("DifficultyLocked"), "Actual level.dat serializer persists the lock");
            helper.assertValueEqual(saved.getInt("GameType"), GameType.CREATIVE.getId(), "Creative mode remains a separate saved setting");
            helper.assertTrue(saved.getBoolean("hardcore") == hardcore, "Hardcore remains a separate saved setting");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(saved, bytes);
            CompoundTag decoded = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
            Dynamic<net.minecraft.nbt.Tag> dynamic = new Dynamic<>(NbtOps.INSTANCE, decoded);
            LevelSettings settings = LevelSettings.parse(dynamic, WorldDataConfiguration.DEFAULT);
            PrimaryLevelData restored = PrimaryLevelData.parse(dynamic, settings, PrimaryLevelData.SpecialWorldProperty.NONE,
                    original.worldGenOptions(), Lifecycle.stable());
            fixed(helper, restored, "Fresh parsed world data retains saved hard+lock without being normalized a second time");
            helper.assertValueEqual(restored.getGameType(), GameType.CREATIVE, "Parsed mode remains creative");
            helper.assertTrue(restored.isHardcore() == hardcore, "Parsed hardcore setting is unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void officialServerSettersCannotLowerOrUnlockDifficulty(GameTestHelper helper) {
        try (ServerState saved = new ServerState(helper.getLevel())) {
            MinecraftServer server = helper.getLevel().getServer();
            fixed(helper, server.getWorldData(), "Server level creation normalized difficulty before tests began");
            GameType mode = server.getWorldData().getGameType();
            boolean hardcore = server.getWorldData().isHardcore();
            for (Difficulty requested : Difficulty.values()) for (boolean force : new boolean[]{false, true}) {
                server.setDifficulty(requested, force);
                fixed(helper, server.getWorldData(), "Official setDifficulty(" + requested + ", " + force + ")");
                for (ServerLevel level : server.getAllLevels()) {
                    helper.assertValueEqual(level.getDifficulty(), Difficulty.HARD, "All dimensions expose the same fixed difficulty");
                    helper.assertTrue(level.getLevelData().isDifficultyLocked(), "All dimensions expose the saved lock");
                }
            }
            server.setDifficultyLocked(false);
            fixed(helper, server.getWorldData(), "Official unlock request cannot remove the R196 lock");
            helper.assertValueEqual(server.getWorldData().getGameType(), mode, "Server requests do not change the world game mode");
            helper.assertTrue(server.getWorldData().isHardcore() == hardcore, "Server requests do not change hardcore");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void officialSetterPublishesExactlyOneDifficultyChangeAndNormalizationIsIdempotent(GameTestHelper helper) {
        try (ServerState saved = new ServerState(helper.getLevel())) {
            MinecraftServer server = helper.getLevel().getServer();
            WorldData data = server.getWorldData();
            List<DifficultyChangeEvent> observed = new ArrayList<>();
            Consumer<DifficultyChangeEvent> listener = observed::add;
            NeoForge.EVENT_BUS.addListener(DifficultyChangeEvent.class, listener);
            try {
                fixed(helper, data, "The live world begins normalized");
                DifficultySystem.normalize(data);
                DifficultySystem.normalize(data);
                helper.assertTrue(observed.isEmpty(), "Repeated normalization of hard+locked data publishes no redundant difficulty events");
                for (Difficulty requested : Difficulty.values()) for (boolean force : new boolean[]{false, true}) {
                    // Independent legacy-save fixture; the official API itself must remain unable to lower difficulty.
                    data.setDifficulty(Difficulty.EASY);
                    data.setDifficultyLocked(false);
                    observed.clear();
                    server.setDifficulty(requested, force);
                    helper.assertValueEqual(observed.size(), 1,
                            "One official setter request produces one native event, including forced/unforced requests");
                    DifficultyChangeEvent event = observed.getFirst();
                    helper.assertValueEqual(event.getDifficulty(), Difficulty.HARD, "Observers only receive the final source difficulty");
                    helper.assertValueEqual(event.getOldDifficulty(), Difficulty.EASY, "The event preserves the actual previous saved difficulty");
                    fixed(helper, data, "The request atomically restores hard+locked policy");
                    observed.clear();
                    DifficultySystem.normalize(data);
                    DifficultySystem.normalize(data);
                    helper.assertTrue(observed.isEmpty(), "Following normalization cannot duplicate the setter's event");
                }
                server.setDifficultyLocked(false);
                helper.assertTrue(observed.isEmpty(), "An unlock request on normalized data does not fabricate a difficulty change");
                fixed(helper, data, "Rejected unlock keeps the source policy");
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void officialDifficultyCommandQueriesHardAndRejectsEverySetter(GameTestHelper helper) throws CommandSyntaxException {
        try (ServerState saved = new ServerState(helper.getLevel())) {
            MinecraftServer server = helper.getLevel().getServer();
            CommandSourceStack source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            helper.assertValueEqual(server.getCommands().getDispatcher().execute("difficulty", source), 3,
                    "The registered native query remains available and reports hard id3");
            for (Difficulty requested : Difficulty.values()) {
                try {
                    server.getCommands().getDispatcher().execute("difficulty " + requested.getKey(), source);
                    throw new AssertionError("R196 difficulty setter unexpectedly succeeded: " + requested);
                } catch (CommandSyntaxException expected) {
                    helper.assertTrue(expected.getRawMessage() instanceof Component component
                                    && component.getContents() instanceof TranslatableContents translated
                                    && translated.getKey().equals("commands.mite.difficulty.locked"),
                            "Every setter, including hard, reports the source-mode lock explanation");
                }
                fixed(helper, server.getWorldData(), "Rejected command leaves authoritative data unchanged");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothConnectedPlayersReceiveOnlyHardLockedDifficultySnapshots(GameTestHelper helper) {
        try (ServerState saved = new ServerState(helper.getLevel());
                PlayerConnection first = new PlayerConnection(helper.getLevel(), "difficulty-one");
                PlayerConnection second = new PlayerConnection(helper.getLevel(), "difficulty-two")) {
            first.drain(); second.drain();
            helper.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
            assertPackets(helper, first.drain(), "First player difficulty change");
            assertPackets(helper, second.drain(), "Second player difficulty change");
            helper.getLevel().getServer().setDifficultyLocked(false);
            assertPackets(helper, first.drain(), "First player unlock request");
            assertPackets(helper, second.drain(), "Second player unlock request");
        }
        helper.succeed();
    }

    private static void assertPackets(GameTestHelper helper, List<ClientboundChangeDifficultyPacket> packets, String message) {
        helper.assertTrue(!packets.isEmpty(), message + " emits an actual native difficulty packet");
        for (var packet : packets) {
            helper.assertValueEqual(packet.getDifficulty(), Difficulty.HARD, message + " never advertises a transient lower value");
            helper.assertTrue(packet.isLocked(), message + " never advertises an unlocked state");
        }
    }

    @GameTest(template = "empty")
    public static void regionalTensionSupportsNegativeAndAboveOldHeightPositions(GameTestHelper helper) {
        try (RegionState saved = new RegionState(helper)) {
            helper.assertTrue(saved.level.getMinBuildHeight() < 0 && saved.level.getMaxBuildHeight() > 256,
                    "The real modern test dimension extends both old world-height boundaries");
            saved.chunk.setInhabitedTime(3_600_000L);
            saved.level.setDayTime(168_000L); // Source day8, phase0/full moon.
            for (int y : new int[]{saved.level.getMinBuildHeight(), -1, 64, 256, saved.level.getMaxBuildHeight() - 1})
                close(helper, DifficultySystem.tension(saved.level, saved.position.atY(y)), 1.25F,
                        "Loaded positions share source tension at modern height " + y);
            close(helper, DifficultySystem.tension(saved.level, saved.position.atY(saved.level.getMinBuildHeight() - 1)), 0F,
                    "Below the real dimension floor has no regional tension");
            close(helper, DifficultySystem.tension(saved.level, saved.position.atY(saved.level.getMaxBuildHeight())), 0F,
                    "Above the real dimension ceiling has no regional tension");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void queryingUnloadedRegionalTensionDoesNotLoadOrGenerateChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos distant = new BlockPos(20_000_008, level.getMinBuildHeight() + 8, 20_000_008);
        int chunkX = distant.getX() >> 4, chunkZ = distant.getZ() >> 4;
        helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null, "The remote control chunk starts absent");
        int loaded = level.getChunkSource().getLoadedChunksCount();
        close(helper, DifficultySystem.tension(level, distant), 0F, "An absent source chunk returns zero");
        close(helper, DifficultySystem.tension(level, distant.atY(64)), 0F, "A second height does not accidentally generate the chunk");
        helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null, "The chunk remains absent after both queries");
        helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(), loaded, "No unrelated chunk was loaded by a tension query");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void regionalTensionUsesSourcePhaseAndResidenceWithoutModernGlobalAgeGrowth(GameTestHelper helper) {
        try (RegionState saved = new RegionState(helper)) {
            float[] fractions = {1F, .75F, .5F, .25F, 0F, .25F, .5F, .75F};
            saved.chunk.setInhabitedTime(0L);
            for (int phase = 0; phase < fractions.length; phase++) {
                saved.level.setDayTime(Math.floorMod(phase - 1, 8) * 24_000L);
                close(helper, DifficultySystem.tension(saved.level, saved.position), fractions[phase] * .25F,
                        "The eight raw R196 phases contribute independently of visual/weather brightness");
            }
            saved.level.setDayTime(168_000L);
            saved.chunk.setInhabitedTime(1_800_000L);
            close(helper, DifficultySystem.tension(saved.level, saved.position), .75F, "Half residence contributes .5 and a full moon .25");
            saved.chunk.setInhabitedTime(3_600_000L);
            close(helper, DifficultySystem.tension(saved.level, saved.position), 1.25F, "Maximum real source inputs reach1.25, not the loose1.5 clamp");
            saved.chunk.setInhabitedTime(36_000_000L);
            saved.level.setDayTime(168_000L + 8L * 100_000L * 24_000L);
            ((ServerLevelData)saved.level.getLevelData()).setGameTime(99_000_000L);
            close(helper, DifficultySystem.tension(saved.level, saved.position), 1.25F,
                    "Further residence and world/global days cannot introduce the modern global-age difficulty term");
        }
        helper.succeed();
    }

    private static final class ServerState implements AutoCloseable {
        private final WorldData data;
        private final Difficulty difficulty;
        private final boolean locked;
        ServerState(ServerLevel level) {
            data = level.getServer().getWorldData(); difficulty = data.getDifficulty(); locked = data.isDifficultyLocked();
        }
        @Override public void close() {
            // Restore the precise pre-test data without calling the policy-enforcing entry points under test.
            data.setDifficulty(difficulty); data.setDifficultyLocked(locked);
        }
    }

    private static final class RegionState implements AutoCloseable {
        final ServerLevel level;
        final BlockPos position;
        final LevelChunk chunk;
        private final long time;
        private final long gameTime;
        private final long inhabited;
        RegionState(GameTestHelper helper) {
            level = helper.getLevel(); position = helper.absolutePos(new BlockPos(2, 1, 2));
            chunk = level.getChunkAt(position); time = level.getDayTime(); gameTime = level.getGameTime(); inhabited = chunk.getInhabitedTime();
        }
        @Override public void close() {
            chunk.setInhabitedTime(inhabited); level.setDayTime(time); ((ServerLevelData)level.getLevelData()).setGameTime(gameTime);
        }
    }

    private static final class PlayerConnection implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        PlayerConnection(ServerLevel level, String name) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
            player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        }
        List<ClientboundChangeDifficultyPacket> drain() {
            channel.runPendingTasks(); channel.flushOutbound();
            List<ClientboundChangeDifficultyPacket> packets = new ArrayList<>();
            Object packet;
            while ((packet = channel.readOutbound()) != null)
                if (packet instanceof ClientboundChangeDifficultyPacket difficulty) packets.add(difficulty);
            return packets;
        }
        @Override public void close() {
            try { player.server.getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
        }
    }
}
