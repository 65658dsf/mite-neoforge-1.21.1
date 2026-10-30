package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Acknowledges the server save before the editor closes. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record GameplayConfigEditResultPayload(int requestId, boolean success, String message) implements CustomPacketPayload {
    public static final Type<GameplayConfigEditResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "edit_gameplay_config_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplayConfigEditResultPayload> CODEC = new StreamCodec<>() {
        @Override public GameplayConfigEditResultPayload decode(RegistryFriendlyByteBuf buffer) {
            return new GameplayConfigEditResultPayload(buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(4096));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, GameplayConfigEditResultPayload payload) {
            buffer.writeVarInt(payload.requestId());
            buffer.writeBoolean(payload.success());
            buffer.writeUtf(payload.message(), 4096);
        }
    };
    @Override public Type<GameplayConfigEditResultPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() ->
                io.github.ningmeng123.mite.client.config.MiteConfigClient.receiveEditResult(payload.requestId(), payload.success(), payload.message())));
    }
}
