package io.github.ningmeng123.mite.network;

import com.google.gson.Gson;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server settings including both ore dimensions, sent on login and config reload. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record GameplayConfigPayload(MiteConfig.Snapshot snapshot) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    public static final Type<GameplayConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "gameplay_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplayConfigPayload> CODEC = new StreamCodec<>() {
        @Override public GameplayConfigPayload decode(RegistryFriendlyByteBuf buffer) {
            return new GameplayConfigPayload(GSON.fromJson(buffer.readUtf(262144), MiteConfig.Snapshot.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, GameplayConfigPayload payload) {
            buffer.writeUtf(GSON.toJson(payload.snapshot()), 262144);
        }
    };
    @Override public Type<GameplayConfigPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("4").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() ->
                io.github.ningmeng123.mite.client.config.MiteConfigClient.receive(payload.snapshot())));
    }
    public static void send(ServerPlayer player) {
        if (player.connection != null && player.connection.hasChannel(TYPE))
            PacketDistributor.sendToPlayer(player, new GameplayConfigPayload(MiteConfig.localSnapshot()));
    }
}
