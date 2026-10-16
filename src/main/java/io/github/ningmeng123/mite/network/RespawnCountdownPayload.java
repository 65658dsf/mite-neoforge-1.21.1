package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-owned countdown; client cannot speed it up or request another player's timer. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record RespawnCountdownPayload(int entityId, int seconds) implements CustomPacketPayload {
    public static final Type<RespawnCountdownPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "respawn_countdown"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RespawnCountdownPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.entityId); buf.writeVarInt(value.seconds); },
            buf -> new RespawnCountdownPayload(buf.readVarInt(), buf.readVarInt()));
    private static volatile RespawnCountdownPayload latest;
    @Override public Type<RespawnCountdownPayload> type() { return TYPE; }
    public static RespawnCountdownPayload latest() { return latest; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (value, context) -> context.enqueueWork(() -> {
            if (context.player().getId() == value.entityId) latest = value;
        }));
    }
    public static void send(ServerPlayer player, int seconds) {
        if (player.connection != null && player.connection.hasChannel(TYPE))
            PacketDistributor.sendToPlayer(player, new RespawnCountdownPayload(player.getId(), seconds));
    }
}
