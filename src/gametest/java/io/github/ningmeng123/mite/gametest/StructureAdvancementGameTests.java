package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.advancement.StructureAdvancements;
import io.github.ningmeng123.mite.advancement.StructureAdvancements.Entry;
import io.github.ningmeng123.mite.survival.DeathOrbQueue;
import io.github.ningmeng123.mite.network.StructureProgressPayload;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import io.github.ningmeng123.mite.worldgen.StructureUnlockAnnouncements;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered structure guides, native client visibility, and authoritative shared progress. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StructureAdvancementGameTests {
    private static final int ALL = StructureAdvancements.ALL_ENABLED;
    private static final long DAY_60 = 1_410_000L;
    private static final Map<Metric, Long> COMPLETE = java.util.Arrays.stream(Metric.values())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(metric -> metric, Metric::cap));

    private StructureAdvancementGameTests() {}

    @GameTest(template = "empty")
    public static void actualLoginShowsAllTwentyOneIncompleteStructureNodesWhenWorldStructuresAreDisabled(GameTestHelper h) {
        try (WorldState world = new WorldState(h); Session session = new Session(h)) {
            h.assertFalse(world.level.getServer().getWorldData().worldGenOptions().generateStructures(),
                    "The actual test world disables natural structures");
            ServerPlayer player = session.player();
            var manager = player.server.getAdvancements();
            var guide = holder(h, player, StructureAdvancements.GUIDE_ID);
            h.assertTrue(player.getAdvancements().getOrStartProgress(guide).isDone(),
                    "The production login subscriber completes the always-visible guide even in a disabled world");
            h.assertTrue(guide.value().criteria().containsKey("visible"), "The guide has its dedicated visibility criterion");
            assertQuietDisplay(h, guide);
            Map<ResourceLocation, Boolean> visibility = new HashMap<>();
            var guideNode = manager.tree().get(StructureAdvancements.GUIDE_ID);
            h.assertTrue(guideNode != null, "The guide belongs to the real loaded advancement tree");
            AdvancementVisibilityEvaluator.evaluateVisibility(guideNode,
                    node -> player.getAdvancements().getOrStartProgress(node.holder()).isDone(),
                    (node, visible) -> visibility.put(node.holder().id(), visible));
            for (Entry entry : Entry.values()) {
                var advancement = holder(h, player, entry.id());
                h.assertValueEqual(advancement.value().parent().orElseThrow(), StructureAdvancements.GUIDE_ID,
                        "Each structure is an immediate guide child: " + entry);
                h.assertValueEqual(advancement.value().criteria().keySet(), Set.copyOf(entry.criteria()),
                        "The registered criteria match the synchronized structure prerequisites: " + entry);
                h.assertValueEqual(advancement.value().requirements().size(), entry.criteria().size(),
                        "Every displayed prerequisite is independently required: " + entry);
                for (String criterion : entry.criteria()) {
                    h.assertFalse(advancement.value().requirements().test(name -> !name.equals(criterion)),
                            "No criterion can be skipped through OR requirements: " + entry + "/" + criterion);
                }
                assertQuietDisplay(h, advancement);
                h.assertValueEqual(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                        advancement.value().display().orElseThrow().getIcon().getItem()), ResourceLocation.parse(entry.icon()),
                        "The real node uses the icon from the shared structure definition: " + entry);
                h.assertTrue(advancement.value().display().orElseThrow().getDescription().getContents()
                        instanceof TranslatableContents description
                        && description.getKey().equals("advancement.mite.structures." + entry.path() + ".description"),
                        "The client receives the localized unlock requirements: " + entry);
                h.assertFalse(progress(h, player, entry).isDone(), "A disabled structure remains incomplete: " + entry);
                h.assertFalse(criterion(h, player, entry, "structures_enabled"),
                        "The real disabled world never marks structures as enabled: " + entry);
                h.assertValueEqual(visibility.get(entry.id()), Boolean.TRUE,
                        "Native visibility includes the uncompleted node before its unlock: " + entry);
            }
            player.getAdvancements().flushDirty(player);
            Set<ResourceLocation> sent = new java.util.HashSet<>();
            for (Object packet : session.drain()) {
                if (packet instanceof ClientboundUpdateAdvancementsPacket update)
                    update.getAdded().forEach(advancement -> sent.add(advancement.id()));
            }
            for (Entry entry : Entry.values()) h.assertTrue(sent.contains(entry.id()),
                    "Native advancement synchronization actually sends the locked node to the client: " + entry);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedPrerequisitesRespectEveryDateBoundaryAndMissingCondition(GameTestHelper h) {
        try (WorldState world = new WorldState(h); Session session = new Session(h)) {
            ServerPlayer player = session.player();
            h.assertValueEqual(Entry.values().length, 21, "All 21 non-exempt structure families have visible unlock nodes");
            for (Entry entry : Entry.values()) {
                long firstTick = (entry.minimumDay() - 1L) * 24_000L - 6_000L;
                StructureAdvancements.sync(player, ALL, firstTick - 1L, StructureProgressData.ALL_CONDITIONS, COMPLETE);
                h.assertFalse(criterion(h, player, entry, "date"), "Last tick before its date is locked: " + entry);
                h.assertFalse(progress(h, player, entry).isDone(), "Exploration cannot bypass the date: " + entry);
                for (var missing : entry.flagRequirements()) {
                    StructureAdvancements.sync(player, ALL, firstTick,
                            StructureProgressData.ALL_CONDITIONS & ~missing.bit(), COMPLETE);
                    h.assertFalse(criterion(h, player, entry, missing.criterion()), "Missing shared craft/visit stays locked: " + entry);
                    h.assertFalse(progress(h, player, entry).isDone(), "Every legacy condition is independently required: " + entry);
                }
                for (var missing : entry.definition().requirements()) {
                    var below = new java.util.EnumMap<Metric, Long>(Metric.class);
                    below.putAll(COMPLETE);
                    below.put(missing.metric(), missing.target() - 1L);
                    StructureAdvancements.sync(player, ALL, firstTick, StructureProgressData.ALL_CONDITIONS, below);
                    h.assertFalse(criterion(h, player, entry, missing.criterion()), "One centimeter/count below remains locked: " + entry);
                    h.assertFalse(progress(h, player, entry).isDone(), "Every exploration condition is independently required: " + entry);
                    below.put(missing.metric(), missing.target());
                    StructureAdvancements.sync(player, ALL, firstTick, StructureProgressData.ALL_CONDITIONS, below);
                    h.assertTrue(progress(h, player, entry).isDone(), "Exact metric target completes with all other prerequisites: " + entry);
                }
                StructureAdvancements.sync(player, ALL, firstTick, StructureProgressData.ALL_CONDITIONS, COMPLETE);
                h.assertTrue(progress(h, player, entry).isDone(), "The first allowed tick completes every exact prerequisite: " + entry);
            }
            StructureAdvancements.sync(player, 0, DAY_60, StructureProgressData.ALL_CONDITIONS, COMPLETE);
            for (Entry entry : Entry.values()) {
                h.assertFalse(progress(h, player, entry).isDone(), "Disabling structures revokes completion: " + entry);
                h.assertFalse(criterion(h, player, entry, "structures_enabled"), "The enabled criterion is revoked: " + entry);
            }
            for (Entry enabled : Entry.values()) {
                StructureAdvancements.sync(player, enabled.enabledBit(), DAY_60, StructureProgressData.ALL_CONDITIONS, COMPLETE);
                for (Entry candidate : Entry.values()) h.assertValueEqual(progress(h, player, candidate).isDone(), candidate == enabled,
                        "Only its enabled structure set can complete a node: enabled=" + enabled + ", candidate=" + candidate);
            }
            StructureAdvancements.sync(player, ALL, DAY_60, StructureProgressData.ALL_CONDITIONS, COMPLETE);
            var clock = (ServerLevelData) world.level.getLevelData();
            long gameTime = world.level.getGameTime();
            try {
                clock.setGameTime(gameTime - Math.floorMod(gameTime, 20L));
                StructureUnlockAnnouncements.tick(new LevelTickEvent.Post(() -> true, world.level));
                for (Entry entry : Entry.values()) h.assertFalse(progress(h, player, entry).isDone(),
                        "The production tick refreshes progress even when its actual world has no eligible structures: " + entry);
            } finally {
                clock.setGameTime(gameTime);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedSyncIsSilentAndSavedCompletionIsCorrectedByRealReloginAfterRollback(GameTestHelper h) {
        try (WorldState world = new WorldState(h); Session session = new Session(h)) {
            ServerPlayer original = session.player();
            var unrelated = holder(h, original, ResourceLocation.withDefaultNamespace("story/mine_stone"));
            for (String criterion : unrelated.value().criteria().keySet()) original.getAdvancements().award(unrelated, criterion);
            session.drain();
            StructureAdvancements.sync(original, ALL, DAY_60, StructureProgressData.ALL_CONDITIONS, COMPLETE);
            original.getAdvancements().flushDirty(original);
            assertNoChat(h, session.drain());
            for (Entry entry : Entry.values()) h.assertTrue(progress(h, original, entry).isDone(),
                    "The shared snapshot completes the structure for a player who did not personally fulfill it: " + entry);
            for (int repeat = 0; repeat < 3; repeat++) StructureAdvancements.sync(original, ALL, DAY_60, StructureProgressData.ALL_CONDITIONS, COMPLETE);
            original.getAdvancements().flushDirty(original);
            List<Object> repeated = session.drain();
            assertNoChat(h, repeated);
            h.assertFalse(repeated.stream().anyMatch(ClientboundUpdateAdvancementsPacket.class::isInstance),
                    "An unchanged snapshot does not dirty or resend native advancement progress");
            original.getAdvancements().save();
            var file = original.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(original.getUUID() + ".json");
            try {
                var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                h.assertTrue(saved.getAsJsonObject(StructureAdvancements.GUIDE_ID.toString()).get("done").getAsBoolean(),
                        "The real advancement file persists the visible guide");
                for (Entry entry : Entry.values()) h.assertTrue(saved.getAsJsonObject(entry.id().toString()).get("done").getAsBoolean(),
                        "The real advancement file persists completed criteria: " + entry);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot inspect the native advancement save", exception);
            }
            world.at(0L, 0);
            session.reconnect();
            ServerPlayer reloaded = session.player();
            h.assertTrue(reloaded != original && reloaded.getUUID().equals(original.getUUID()),
                    "Native PlayerList disconnect and login create a replacement from the same saved identity");
            h.assertTrue(reloaded.getAdvancements().getOrStartProgress(holder(h, reloaded, StructureAdvancements.GUIDE_ID)).isDone(),
                    "The visible guide survives native relogin");
            for (Entry entry : Entry.values()) {
                h.assertFalse(progress(h, reloaded, entry).isDone(),
                        "Production login corrects the saved completion using the disabled, rolled-back world: " + entry);
                h.assertFalse(criterion(h, reloaded, entry, "date"),
                        "A saved date milestone is revoked after calendar rollback: " + entry);
            }
            h.assertTrue(reloaded.getAdvancements().getOrStartProgress(unrelated).isDone(),
                    "Structure synchronization preserves an unrelated saved advancement");
            assertNoChat(h, session.drain());
            StructureAdvancements.sync(reloaded, ALL, DAY_60, StructureProgressData.ALL_CONDITIONS, COMPLETE);
            for (Entry entry : Entry.values()) h.assertTrue(progress(h, reloaded, entry).isDone(),
                    "Meeting the prerequisites again restores current completion: " + entry);
            assertNoChat(h, session.drain());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void progressSnapshotCodecIsBoundedAndPreservesAllCurrentTargets(GameTestHelper h) {
        var mutable = new java.util.EnumMap<Metric, Long>(Metric.class);
        mutable.putAll(COMPLETE);
        var payload = new StructureProgressPayload(ALL, 60L, StructureProgressData.ALL_CONDITIONS, mutable);
        mutable.clear();
        h.assertValueEqual(payload.values(), COMPLETE, "Snapshot owns an immutable copy of every metric");
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            StructureProgressPayload.CODEC.encode(bytes, payload);
            h.assertTrue(bytes.readableBytes() < 200, "Fixed 18 bounded counters have a compact maximum wire size");
            h.assertValueEqual(StructureProgressPayload.CODEC.decode(bytes), payload, "Every shared flag, date and counter survives actual wire encoding");
            h.assertFalse(bytes.isReadable(), "The decoder consumes exactly the fixed snapshot");
            for (long invalid : new long[]{-1L, Metric.values()[0].cap() + 1L}) {
                bytes.clear(); bytes.writeVarInt(ALL); bytes.writeVarLong(32L); bytes.writeVarInt(0); bytes.writeVarLong(invalid);
                boolean rejected = false;
                try { StructureProgressPayload.CODEC.decode(bytes); } catch (DecoderException expected) { rejected = true; }
                h.assertTrue(rejected, "Negative or over-cap counters are rejected before becoming client state");
            }
            bytes.clear(); bytes.writeVarInt(-1); bytes.writeVarLong(32L); bytes.writeVarInt(0);
            boolean rejected = false;
            try { StructureProgressPayload.CODEC.decode(bytes); } catch (DecoderException expected) { rejected = true; }
            h.assertTrue(rejected, "Unknown structure bits are rejected");
        } finally { bytes.release(); }
        h.succeed();
    }

    private static AdvancementHolder holder(GameTestHelper h, ServerPlayer player, ResourceLocation id) {
        var result = player.server.getAdvancements().get(id);
        h.assertTrue(result != null, "The real server has loaded advancement " + id);
        return result;
    }

    private static AdvancementProgress progress(GameTestHelper h, ServerPlayer player, Entry entry) {
        return player.getAdvancements().getOrStartProgress(holder(h, player, entry.id()));
    }

    private static boolean criterion(GameTestHelper h, ServerPlayer player, Entry entry, String criterion) {
        var result = progress(h, player, entry).getCriterion(criterion);
        h.assertTrue(result != null, "The displayed structure contains criterion " + entry + "/" + criterion);
        return result.isDone();
    }

    private static void assertQuietDisplay(GameTestHelper h, AdvancementHolder holder) {
        var display = holder.value().display().orElseThrow();
        h.assertFalse(display.isHidden(), "The structure guide is never hidden until completed: " + holder.id());
        h.assertFalse(display.getIcon().isEmpty(), "The registered structure has a renderable item icon: " + holder.id());
        h.assertFalse(display.getDescription().getString().isBlank(), "The structure has visible prerequisite text: " + holder.id());
        h.assertFalse(display.shouldShowToast(), "Structure tracking does not create duplicate advancement toasts: " + holder.id());
        h.assertFalse(display.shouldAnnounceChat(), "Structure tracking preserves the existing dedicated chat notices: " + holder.id());
    }

    private static void assertNoChat(GameTestHelper h, List<Object> packets) {
        h.assertFalse(packets.stream().anyMatch(packet -> packet instanceof ClientboundSystemChatPacket chat
                        && chat.content().getContents() instanceof TranslatableContents text
                        && (text.getKey().startsWith("chat.type.advancement.")
                        || text.getKey().startsWith("message.mite.structure_unlock."))),
                "Synchronizing the guide and criteria never adds native system-chat announcements");
    }

    private static final class WorldState implements AutoCloseable {
        final ServerLevel level;
        final StructureProgressData progress;
        final long time;
        final int conditions;
        WorldState(GameTestHelper h) {
            level = h.getLevel().getServer().overworld();
            progress = StructureProgressData.get(level);
            time = level.getDayTime();
            conditions = progress.conditions();
            at(0L, 0);
        }
        void at(long time, int conditions) {
            progress.restoreConditions(conditions);
            level.setDayTime(time);
            R196StructureGeneration.refresh(level);
        }
        @Override public void close() { at(time, conditions); }
    }

    /** Native player files and advancement files, with an active connection for outgoing packets. */
    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;
        final GameProfile profile = new GameProfile(UUID.randomUUID(), "adv-" + UUID.randomUUID().toString().substring(0, 8));
        EmbeddedChannel channel;
        ServerGamePacketListenerImpl listener;
        Session(GameTestHelper h) { helper = h; join(); }
        ServerPlayer player() { return listener.player; }
        private void join() {
            var cookie = CommonListenerCookie.createInitial(profile, false);
            var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            listener = player.connection;
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 24, 1));
            player.moveTo(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
        }
        void reconnect() { leave(); join(); }
        List<Object> drain() {
            channel.runPendingTasks();
            channel.flushOutbound();
            List<Object> packets = new ArrayList<>();
            Object packet;
            while ((packet = channel.readOutbound()) != null) packets.add(packet);
            return packets;
        }
        private void leave() {
            var current = helper.getLevel().getServer().getPlayerList().getPlayer(profile.getId());
            if (current != null) current.server.getPlayerList().remove(current);
            channel.finishAndReleaseAll();
        }
        @Override public void close() {
            player().getInventory().clearContent();
            for (ServerLevel level : helper.getLevel().getServer().getAllLevels()) DeathOrbQueue.get(level).removeForOwner(profile.getId());
            leave();
        }
    }
}
