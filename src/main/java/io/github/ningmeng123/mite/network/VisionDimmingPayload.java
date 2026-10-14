package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.VisionDimming;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only the server may set persistent shadow intensity; the client interpolates its display. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record VisionDimmingPayload(float value) implements CustomPacketPayload {
    public static final Type<VisionDimmingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mite", "vision_dimming"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VisionDimmingPayload> STREAM_CODEC = new StreamCodec<>() {
        public VisionDimmingPayload decode(RegistryFriendlyByteBuf buffer) { return new VisionDimmingPayload(buffer.readFloat()); }
        public void encode(RegistryFriendlyByteBuf buffer, VisionDimmingPayload value) { buffer.writeFloat(value.value()); }
    };
    @Override public Type<VisionDimmingPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() ->
                context.player().getPersistentData().putFloat(VisionDimming.VALUE, Float.isFinite(payload.value()) ? Math.max(payload.value(), 0F) : 0F)));
    }
    public static void send(ServerPlayer player, float value) {
        if (player.connection != null && player.connection.hasChannel(TYPE)) PacketDistributor.sendToPlayer(player, new VisionDimmingPayload(value));
    }
}
