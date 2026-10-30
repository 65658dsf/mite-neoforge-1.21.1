package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.network.ServerClockStatus;
import io.github.ningmeng123.mite.rules.WorldClock;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercise native pre-login status and NeoForge's encoded JSON cache without logging in. @author NingMeng123 */
@GameTestHolder("mite_feedback")
@PrefixGameTestTemplate(false)
public final class ServerClockStatusGameTests {
    private ServerClockStatusGameTests() {}

    @GameTest(template = "empty")
    public static void clockKeepsMotdPlayerSampleIconAndProtocolMetadata(GameTestHelper h) {
        var sample = new GameProfile(UUID.randomUUID(), "NingMeng123");
        var motd = Component.literal("Original server\nSecond line").withStyle(ChatFormatting.BLUE);
        var original = new ServerStatus(motd, Optional.of(new ServerStatus.Players(128, 1, List.of(sample))),
                Optional.of(ServerStatus.Version.current()), Optional.of(new ServerStatus.Favicon(new byte[]{1, 2})), true, true);
        var updated = ServerClockStatus.withClock(original, 18000);
        h.assertTrue(updated.description().getString().startsWith("Day 2 · 00:00\n"),
                "The first visible server-list line exposes day and time, including clients without translations");
        h.assertTrue(updated.description().getSiblings().getLast().equals(motd)
                        && original.description().equals(motd) && !motd.getString().contains("Day 2"),
                "The complete styled server description is retained without mutating the source");
        h.assertTrue(updated.players().equals(original.players()) && updated.version().equals(original.version())
                        && updated.favicon().equals(original.favicon()) && updated.enforcesSecureChat() && updated.isModded(),
                "Population, player sample, protocol, favicon and security flags are preserved");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unjoinedStatusRequestCarriesClockThroughTheNativeJsonCache(GameTestHelper h) throws Exception {
        MinecraftServer server = h.getLevel().getServer();
        Method build = MinecraftServer.class.getDeclaredMethod("buildServerStatus"); build.setAccessible(true);
        Method cache = MinecraftServer.class.getDeclaredMethod("resetStatusCache", ServerStatus.class); cache.setAccessible(true);
        Field cacheField = MinecraftServer.class.getDeclaredField("cachedServerStatus"); cacheField.setAccessible(true);
        Object previousCache = cacheField.get(server);
        int online = server.getPlayerCount();
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            long dayTime = server.overworld().getDayTime();
            ServerStatus status = (ServerStatus)build.invoke(server);
            cache.invoke(server, status);
            var listener = new ServerStatusPacketListenerImpl(status, connection, server.getStatusJson());
            listener.handleStatusRequest(ServerboundStatusRequestPacket.INSTANCE);
            Object packet = channel.readOutbound();
            h.assertTrue(packet instanceof ClientboundStatusResponsePacket, "The native listener sends a public status response");
            var wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundStatusResponsePacket.STREAM_CODEC.encode(wire, (ClientboundStatusResponsePacket)packet);
                var decoded = ClientboundStatusResponsePacket.STREAM_CODEC.decode(wire).status();
                h.assertTrue(decoded.description().getString().startsWith("Day " + WorldClock.displayDay(dayTime)
                                + " · " + WorldClock.displayTime(dayTime) + "\n"),
                        "The native JSON cache and wire codec contain the actual synchronized Overworld clock");
                h.assertTrue(decoded.players().equals(status.players()) && decoded.version().equals(status.version()),
                        "The public response retains native player and compatibility information");
            } finally { wire.release(); }
            h.assertValueEqual(server.getPlayerCount(), online, "Reading server time never logs a player into the world");
        } finally {
            cacheField.set(server, previousCache);
            channel.finishAndReleaseAll();
        }
        h.succeed();
    }
}
