package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.world.LunarAnnouncements;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native ServerPlayer system-chat packets, event dispatch, persistent NBT and respawn cloning. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class LunarAnnouncementGameTests {
    private LunarAnnouncementGameTests() {}
    private static long time(int day, int tick) { return (day - 1L) * 24000L + tick; }

    @GameTest(template = "empty")
    public static void fourSpecialDatesSendTheirColoredLocalizedSystemChatDuringDaylight(GameTestHelper helper) {
        try (Clock saved = new Clock(helper.getLevel()); Fixture fixture = new Fixture(helper.getLevel())) {
            int[] days = {24, 32, 128, 120};
            String[] names = {"harvest", "blood", "blue", "moon_dog"};
            ChatFormatting[] colors = {ChatFormatting.GOLD, ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.AQUA};
            for (int index = 0; index < days.length; index++) {
                helper.getLevel().setDayTime(time(days[index], 6000));
                List<ClientboundSystemChatPacket> packets = fixture.tick();
                helper.assertValueEqual(packets.size(), 1, "A special date sends exactly one native system-chat packet");
                assertPacket(helper, packets.getFirst(), days[index], names[index], colors[index]);
            }
        }
        repeatedTicksAndNightBoundariesDoNotRepeatTheSameDate(helper);
        eachPlayerFirstJoiningASpecialDateReceivesAnIndependentAnnouncement(helper);
        helper.succeed();
    }

    private static void repeatedTicksAndNightBoundariesDoNotRepeatTheSameDate(GameTestHelper helper) {
        try (Clock saved = new Clock(helper.getLevel()); Fixture fixture = new Fixture(helper.getLevel())) {
            helper.getLevel().setDayTime(time(32, 0));
            helper.assertValueEqual(fixture.tick().size(), 1, "First tick on the blood date announces it during daylight");
            for (int tick : new int[]{0, 1, 6000, 12999, 13000, 18000, 23000, 23001, 23999}) {
                helper.getLevel().setDayTime(time(32, tick));
                helper.assertTrue(fixture.tick().isEmpty(), "The same calendar date is not repeated at tick " + tick);
            }
        }
    }

    @GameTest(template = "empty")
    public static void ordinaryDatesAreSilentAndTimeJumpsAnnounceTheNewSpecialDate(GameTestHelper helper) {
        try (Clock saved = new Clock(helper.getLevel()); Fixture fixture = new Fixture(helper.getLevel())) {
            helper.getLevel().setDayTime(time(23, 23999));
            helper.assertTrue(fixture.tick().isEmpty(), "The ordinary day before harvest has no announcement");
            helper.assertFalse(fixture.player.getPersistentData().contains(Player.PERSISTED_NBT_TAG), "Ordinary ticks do not create announcement persistence");
            helper.getLevel().setDayTime(time(24, 0));
            assertPacket(helper, only(helper, fixture.tick()), 24, "harvest", ChatFormatting.GOLD);
            helper.getLevel().setDayTime(time(56, 18000));
            assertPacket(helper, only(helper, fixture.tick()), 56, "harvest", ChatFormatting.GOLD);
            helper.getLevel().setDayTime(time(64, 18000));
            assertPacket(helper, only(helper, fixture.tick()), 64, "blood", ChatFormatting.RED);
            helper.getLevel().setDayTime(time(65, 18000));
            helper.assertTrue(fixture.tick().isEmpty(), "A time jump back to an ordinary date is silent");
        }
        malformedOldAnnouncementDataIsReplacedWithoutOverwritingOtherPlayerPersistence(helper);
        helper.succeed();
    }

    private static void eachPlayerFirstJoiningASpecialDateReceivesAnIndependentAnnouncement(GameTestHelper helper) {
        try (Clock saved = new Clock(helper.getLevel()); Fixture first = new Fixture(helper.getLevel()); Fixture second = new Fixture(helper.getLevel())) {
            helper.getLevel().setDayTime(time(128, 21000));
            assertPacket(helper, only(helper, first.tick()), 128, "blue", ChatFormatting.BLUE);
            assertPacket(helper, only(helper, second.tick()), 128, "blue", ChatFormatting.BLUE);
            helper.assertTrue(first.tick().isEmpty() && second.tick().isEmpty(), "Each player's own date marker prevents repetition");
        }
    }

    @GameTest(template = "empty")
    public static void netherAndEndRemainSilentAndReturningToTheOverworldDoesNotRepeat(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(nether != null && end != null, "Vanilla dimensions are loaded for actual dimension isolation");
        try (Clock saved = new Clock(helper.getLevel()); Fixture fixture = new Fixture(nether)) {
            helper.getLevel().setDayTime(time(32, 18000));
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "lunar-offline"), false);
            var unconnected = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            helper.assertFalse(LunarAnnouncements.announce(unconnected), "An unconnected temporary server player does not send or consume announcements");
            helper.assertFalse(unconnected.getPersistentData().contains(Player.PERSISTED_NBT_TAG), "No connection means no persisted announced date");
            helper.assertTrue(fixture.tick().isEmpty(), "A server player in the nether receives no calendar message");
            helper.assertFalse(fixture.player.getPersistentData().contains(Player.PERSISTED_NBT_TAG), "Another dimension must not consume the overworld announcement");
            fixture.player.setServerLevel(end);
            helper.assertTrue(fixture.tick().isEmpty(), "A server player in the end receives no calendar message");
            fixture.player.setServerLevel(helper.getLevel());
            assertPacket(helper, only(helper, fixture.tick()), 32, "blood", ChatFormatting.RED);
            fixture.player.setServerLevel(nether); helper.assertTrue(fixture.tick().isEmpty(), "Leaving the overworld is silent");
            fixture.player.setServerLevel(helper.getLevel());
            helper.assertTrue(fixture.tick().isEmpty(), "Returning on the already announced date remains silent");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualPlayerSaveAndReloadKeepTheAnnouncedDateAcrossReconnectOrRestart(GameTestHelper helper) {
        try (Clock saved = new Clock(helper.getLevel()); Fixture original = new Fixture(helper.getLevel())) {
            helper.getLevel().setDayTime(time(120, 9000));
            assertPacket(helper, only(helper, original.tick()), 120, "moon_dog", ChatFormatting.AQUA);
            CompoundTag savedPlayer = original.player.saveWithoutId(new CompoundTag());
            CompoundTag marker = savedPlayer.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG).getCompound(LunarAnnouncements.DATA_KEY);
            helper.assertTrue(marker.getLong("last_day") == 120L && marker.getString("last_event").equals("MOON_DOG"), "The actual player save contains both date and event in PlayerPersisted");
            helper.assertValueEqual(marker.getInt("version"), 1, "The new persisted announcement format has a version");
            try (Fixture reloaded = new Fixture(helper.getLevel(), original.player.getGameProfile())) {
                reloaded.player.load(savedPlayer);
                reloaded.drain();
                helper.assertTrue(reloaded.tick().isEmpty(), "Loading the real saved player NBT prevents a reconnect or server-restart repeat");
                helper.getLevel().setDayTime(time(128, 0));
                assertPacket(helper, only(helper, reloaded.tick()), 128, "blue", ChatFormatting.BLUE);
            }
        }
        nativeDeathAndEndReturnClonesKeepPlayerPersistedWithoutDuplicateChat(helper);
        helper.succeed();
    }

    private static void nativeDeathAndEndReturnClonesKeepPlayerPersistedWithoutDuplicateChat(GameTestHelper helper) {
        try (Clock saved = new Clock(helper.getLevel()); Fixture original = new Fixture(helper.getLevel())) {
            helper.getLevel().setDayTime(time(24, 6000));
            assertPacket(helper, only(helper, original.tick()), 24, "harvest", ChatFormatting.GOLD);
            for (boolean alive : new boolean[]{false, true}) try (Fixture clone = new Fixture(helper.getLevel(), original.player.getGameProfile())) {
                clone.player.restoreFrom(original.player, alive);
                clone.drain();
                helper.assertTrue(clone.tick().isEmpty(), "Native restoreFrom retains announcement data for " + (alive ? "end return" : "death respawn"));
                helper.assertTrue(clone.player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                        .getCompound(LunarAnnouncements.DATA_KEY).getLong("last_day") == 24L, "The native persisted section reaches the actual replacement player");
            }
        }
    }

    private static void malformedOldAnnouncementDataIsReplacedWithoutOverwritingOtherPlayerPersistence(GameTestHelper helper) {
        try (Clock saved = new Clock(helper.getLevel()); Fixture fixture = new Fixture(helper.getLevel())) {
            var persisted = new CompoundTag();
            persisted.putString("other_mod_marker", "preserve");
            persisted.putString(LunarAnnouncements.DATA_KEY, "invalid old marker");
            fixture.player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            helper.getLevel().setDayTime(time(32, 6000));
            assertPacket(helper, only(helper, fixture.tick()), 32, "blood", ChatFormatting.RED);
            helper.assertTrue(fixture.player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getString("other_mod_marker").equals("preserve"), "Unrelated persisted player data is retained");
            helper.assertTrue(fixture.tick().isEmpty(), "The repaired marker prevents later duplicate ticks");
        }
    }

    private static ClientboundSystemChatPacket only(GameTestHelper helper, List<ClientboundSystemChatPacket> packets) {
        helper.assertValueEqual(packets.size(), 1, "Exactly one native system chat packet was sent");
        return packets.getFirst();
    }
    private static void assertPacket(GameTestHelper helper, ClientboundSystemChatPacket packet, long day, String event, ChatFormatting color) {
        helper.assertFalse(packet.overlay(), "Announcements appear in chat, not the action bar");
        helper.assertTrue(TextColor.fromLegacyFormat(color).equals(packet.content().getStyle().getColor()), "The whole message carries the event's requested color");
        helper.assertTrue(packet.content().getContents() instanceof TranslatableContents, "The actual packet contains a client-localized component");
        var contents = (TranslatableContents) packet.content().getContents();
        helper.assertTrue(contents.getKey().equals("lunar.mite.announcement"), "The common announcement sentence is translated by the client");
        helper.assertValueEqual(contents.getArgs().length, 2, "The sentence has day and event-name arguments");
        helper.assertTrue(contents.getArgs()[0] instanceof Number && ((Number) contents.getArgs()[0]).longValue() == day, "The actual packet carries the full calendar day");
        helper.assertTrue(contents.getArgs()[1] instanceof Component name && name.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("lunar.mite." + event), "The event name is independently translated");
    }
    private static final class Clock implements AutoCloseable {
        private final ServerLevel level; private final long dayTime;
        Clock(ServerLevel level) { this.level = level; dayTime = level.getDayTime(); }
        @Override public void close() { level.setDayTime(dayTime); }
    }
    private static final class Fixture implements AutoCloseable {
        final ServerPlayer player; final EmbeddedChannel channel;
        Fixture(ServerLevel level) { this(level, new GameProfile(UUID.randomUUID(), "lunar-notice")); }
        Fixture(ServerLevel level, GameProfile profile) {
            var cookie = CommonListenerCookie.createInitial(profile, false);
            player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie);
            drain();
        }
        List<ClientboundSystemChatPacket> tick() {
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            return drain();
        }
        List<ClientboundSystemChatPacket> drain() {
            channel.runPendingTasks(); channel.flushOutbound();
            List<ClientboundSystemChatPacket> packets = new ArrayList<>();
            Object packet;
            while ((packet = channel.readOutbound()) != null) if (packet instanceof ClientboundSystemChatPacket message) packets.add(message);
            return packets;
        }
        @Override public void close() {
            player.getTextFilter().leave(); player.discard(); channel.finishAndReleaseAll();
        }
    }
}
