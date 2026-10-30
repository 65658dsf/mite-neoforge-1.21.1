package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.GameplayConfigEdits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** An untrusted editor draft; only the server may authorize and publish it. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record GameplayConfigEditPayload(int requestId, String section, String json) implements CustomPacketPayload {
    public static final Type<GameplayConfigEditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "edit_gameplay_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplayConfigEditPayload> CODEC = new StreamCodec<>() {
        @Override public GameplayConfigEditPayload decode(RegistryFriendlyByteBuf buffer) {
            return new GameplayConfigEditPayload(buffer.readVarInt(), buffer.readUtf(64), buffer.readUtf(262144));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, GameplayConfigEditPayload payload) {
            buffer.writeVarInt(payload.requestId());
            buffer.writeUtf(payload.section(), 64);
            buffer.writeUtf(payload.json(), 262144);
        }
    };
    @Override public Type<GameplayConfigEditPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            String failure = GameplayConfigEdits.apply(player, payload.section(), payload.json());
            if (failure == null) GameplayConfigPayload.send(player);
            if (player.connection != null && player.connection.hasChannel(GameplayConfigEditResultPayload.TYPE))
                PacketDistributor.sendToPlayer(player, new GameplayConfigEditResultPayload(payload.requestId(), failure == null,
                        failure == null ? "" : failure));
        }));
    }
}
