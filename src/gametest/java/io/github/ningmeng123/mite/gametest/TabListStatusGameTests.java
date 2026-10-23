package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.survival.DeathOrbQueue;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.survival.TabListStatus;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

/** Native login and display-name packets verify public Tab status synchronization. @author NingMeng123 */
@GameTestHolder("mite_tab_list")
@PrefixGameTestTemplate(false)
public final class TabListStatusGameTests {
    private static final String LEVEL = "gui.mite.tab.level";
    private static final String LATENCY = "gui.mite.tab.latency";

    private TabListStatusGameTests() {}

    @GameTest(template = "empty")
    public static void joiningPlayerStatusIsIncludedInNativeInitialization(GameTestHelper helper) {
        try (Observer viewer = new Observer(helper, 0, 0)) {
            viewer.drain();
            try (Observer joining = new Observer(helper, 35, 93)) {
                var packets = viewer.drain();
                var packet = packets.stream()
                        .filter(p -> p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER))
                        .filter(p -> p.entries().stream().anyMatch(e -> e.profileId().equals(joining.player.getUUID())))
                        .findFirst().orElseThrow();
                helper.assertTrue(packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                        "The native login packet includes a public display name");
                var entry = packet.entries().stream().filter(e -> e.profileId().equals(joining.player.getUUID()))
                        .findFirst().orElseThrow();
                assertStatus(helper, entry.displayName(), joining.player, 35, "93", ChatFormatting.GREEN);
                var ownEntry = joining.drain().stream().flatMap(p -> p.entries().stream())
                        .filter(e -> e.profileId().equals(joining.player.getUUID())).findFirst().orElseThrow();
                assertStatus(helper, ownEntry.displayName(), joining.player, 35, "93", ChatFormatting.GREEN);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void signedAndExtendedLevelsBroadcastAcrossDimensionsOnlyWhenChanged(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (Observer subject = new Observer(helper, 0, 50); Observer viewer = new Observer(helper, 0, 70)) {
            var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "The real Nether is available for the remote observer");
            viewer.player.teleportTo(nether, 0.5D, 120D, 0.5D, 0F, 0F);
            helper.assertFalse(viewer.player.level() == subject.player.level(), "Observers occupy different dimensions");
            subject.drain();
            viewer.drain();
            for (int level : new int[] {-40, 312}) {
                PlayerExperience.setLevels(subject.player, level);
                setLatency(subject.player, level < 0 ? 420 : 123);
                TabListStatus.refresh(subject.player.server);
                var subjectUpdates = statusUpdates(subject.drain(), subject.player);
                var viewerUpdates = statusUpdates(viewer.drain(), subject.player);
                helper.assertValueEqual(subjectUpdates.size(), 1, "The player receives one changed status update");
                helper.assertValueEqual(viewerUpdates.size(), 1, "A different dimension receives the same changed status");
                var name = viewerUpdates.getFirst().entries().getFirst().displayName();
                assertStatus(helper, name, subject.player, level, level < 0 ? "420" : "123",
                        level < 0 ? ChatFormatting.RED : ChatFormatting.GREEN);
                helper.assertTrue(subjectUpdates.getFirst().entries().getFirst().displayName().equals(name),
                        "All dimensions receive the same authoritative component");
                TabListStatus.refresh(subject.player.server);
                helper.assertTrue(statusUpdates(subject.drain(), subject.player).isEmpty(),
                        "Unchanged status does not resend a packet to the player");
                helper.assertTrue(statusUpdates(viewer.drain(), subject.player).isEmpty(),
                        "Unchanged status does not resend a packet across dimensions");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void latencyChangesRefreshTheNumberAndBoundaryColors(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (Observer subject = new Observer(helper, 5, 0)) {
            subject.drain();
            int[] values = {149, 150, 299, 300, -1};
            ChatFormatting[] colors = {ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.YELLOW,
                    ChatFormatting.RED, ChatFormatting.GRAY};
            for (int i = 0; i < values.length; i++) {
                setLatency(subject.player, values[i]);
                TabListStatus.refresh(subject.player.server);
                var updates = statusUpdates(subject.drain(), subject.player);
                helper.assertValueEqual(updates.size(), 1, "A latency change is published through the native status packet");
                assertStatus(helper, updates.getFirst().entries().getFirst().displayName(), subject.player, 5,
                        values[i] < 0 ? "--" : Integer.toString(values[i]), colors[i]);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void existingDisplayNamesAreCopiedWithoutRepeatingTheStatus(GameTestHelper helper) {
        try (Observer subject = new Observer(helper, -5, 15)) {
            var sharedName = Component.literal("[Rank] Custom Name").withStyle(ChatFormatting.BLUE);
            var original = sharedName.copy();
            for (int i = 0; i < 2; i++) {
                var event = new PlayerEvent.TabListNameFormat(subject.player);
                event.setDisplayName(sharedName);
                NeoForge.EVENT_BUS.post(event);
                var result = event.getDisplayName();
                helper.assertTrue(result != null && result.getString().startsWith(sharedName.getString()),
                        "An earlier mod's display name is retained");
                helper.assertTrue(sharedName.equals(original), "Appending status does not mutate a shared display name");
                helper.assertValueEqual(translations(result, LEVEL).size(), 1, "The level suffix occurs exactly once");
                helper.assertValueEqual(translations(result, LATENCY).size(), 1, "The latency suffix occurs exactly once");
                helper.assertValueEqual(translations(result, LEVEL).getFirst().getStyle().getColor(),
                        net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.RED),
                        "Negative levels retain their own color despite the earlier name's blue style");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void teamPrefixesAndSuffixesRemainVisibleAfterTeamEdits(GameTestHelper helper) {
        var scoreboard = helper.getLevel().getServer().getScoreboard();
        var team = scoreboard.addPlayerTeam("tab-" + UUID.randomUUID().toString().substring(0, 8));
        try (Observer subject = new Observer(helper, 8, 12)) {
            team.setPlayerPrefix(Component.literal("[Crew] "));
            team.setPlayerSuffix(Component.literal("!"));
            team.setColor(ChatFormatting.BLUE);
            scoreboard.addPlayerToTeam(subject.player.getScoreboardName(), team);
            subject.drain();
            TabListStatus.refresh(subject.player.server);
            var first = statusUpdates(subject.drain(), subject.player);
            helper.assertValueEqual(first.size(), 1, "Joining a team refreshes the public name");
            helper.assertTrue(first.getFirst().entries().getFirst().displayName().getString()
                    .startsWith("[Crew] " + subject.player.getName().getString() + "!"),
                    "The vanilla team prefix and suffix remain before the status");
            team.setPlayerPrefix(Component.literal("[New] "));
            subject.drain();
            TabListStatus.refresh(subject.player.server);
            var updated = statusUpdates(subject.drain(), subject.player);
            helper.assertValueEqual(updated.size(), 1, "Editing a team prefix refreshes an otherwise unchanged status");
            var name = updated.getFirst().entries().getFirst().displayName();
            helper.assertTrue(name.getString().startsWith("[New] " + subject.player.getName().getString() + "!"),
                    "The current team formatting replaces the old formatting");
            helper.assertFalse(name.getString().contains("[Crew]"), "Stale team formatting is not retained");
            assertStatus(helper, name, subject.player, 8, "12", ChatFormatting.GREEN);
        } finally {
            scoreboard.removePlayerTeam(team);
        }
        helper.succeed();
    }

    private static List<ClientboundPlayerInfoUpdatePacket> statusUpdates(List<ClientboundPlayerInfoUpdatePacket> packets,
            ServerPlayer subject) {
        return packets.stream().filter(p -> p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)
                && !p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
                && p.entries().stream().anyMatch(e -> e.profileId().equals(subject.getUUID()))).toList();
    }

    private static void assertStatus(GameTestHelper helper, Component name, ServerPlayer subject, int level,
            String latency, ChatFormatting latencyColor) {
        helper.assertTrue(name != null, "Tab status has a display component");
        helper.assertTrue(name.getString().contains(subject.getName().getString()), "Tab status retains the player's name");
        var levels = translations(name, LEVEL);
        var latencies = translations(name, LATENCY);
        helper.assertValueEqual(levels.size(), 1, "Exactly one level is displayed");
        helper.assertValueEqual(latencies.size(), 1, "Exactly one numeric latency is displayed");
        helper.assertValueEqual(((TranslatableContents)levels.getFirst().getContents()).getArgs()[0], level,
                "The public level is the full signed experience level");
        helper.assertValueEqual(((TranslatableContents)latencies.getFirst().getContents()).getArgs()[0], latency,
                "The public latency comes from the server connection");
        helper.assertValueEqual(levels.getFirst().getStyle().getColor(), net.minecraft.network.chat.TextColor.fromLegacyFormat(
                level < 0 ? ChatFormatting.RED : ChatFormatting.GREEN), "Level sign determines its color");
        helper.assertValueEqual(latencies.getFirst().getStyle().getColor(),
                net.minecraft.network.chat.TextColor.fromLegacyFormat(latencyColor), "Latency quality determines its color");
    }

    private static List<Component> translations(Component component, String key) {
        var result = new ArrayList<Component>();
        if (component.getContents() instanceof TranslatableContents text && text.getKey().equals(key)) result.add(component);
        for (var sibling : component.getSiblings()) result.addAll(translations(sibling, key));
        return result;
    }

    private static void setLatency(ServerPlayer player, int value) throws ReflectiveOperationException {
        Field field = ServerCommonPacketListenerImpl.class.getDeclaredField("latency");
        field.setAccessible(true);
        field.setInt(player.connection, value);
    }

    private static final class Observer implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;

        Observer(GameTestHelper helper, int level, int latency) {
            var profile = new GameProfile(UUID.randomUUID(), "tab-" + UUID.randomUUID().toString().substring(0, 8));
            var cookie = new CommonListenerCookie(profile, latency, ClientInformation.createDefault(), false, ConnectionType.OTHER);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
            PlayerExperience.setLevels(player, level);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setNoGravity(true);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 24, 1))));
        }

        List<ClientboundPlayerInfoUpdatePacket> drain() {
            channel.runPendingTasks();
            channel.flushOutbound();
            var result = new ArrayList<ClientboundPlayerInfoUpdatePacket>();
            Object packet;
            while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof ClientboundPlayerInfoUpdatePacket update) result.add(update);
            }
            return result;
        }

        @Override public void close() {
            player.getInventory().clearContent();
            for (ServerLevel level : player.server.getAllLevels()) DeathOrbQueue.get(level).removeForOwner(player.getUUID());
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
