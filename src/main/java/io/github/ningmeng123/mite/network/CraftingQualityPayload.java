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

/** Authoritative quality choices and their costs; contains no rendering dependencies. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record CraftingQualityPayload(int containerId, int quality, int minQuality, int maxQuality,
        int experienceCost, int period, boolean visible, boolean crafting, boolean affordable)
        implements CustomPacketPayload {
    public static final Type<CraftingQualityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "crafting_quality"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingQualityPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public CraftingQualityPayload decode(RegistryFriendlyByteBuf buffer) {
            return new CraftingQualityPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CraftingQualityPayload value) {
            buffer.writeVarInt(value.containerId());
            buffer.writeVarInt(value.quality());
            buffer.writeVarInt(value.minQuality());
            buffer.writeVarInt(value.maxQuality());
            buffer.writeVarInt(value.experienceCost());
            buffer.writeVarInt(value.period());
            buffer.writeBoolean(value.visible());
            buffer.writeBoolean(value.crafting());
            buffer.writeBoolean(value.affordable());
        }
    };
    private static volatile CraftingQualityPayload latest;
    private static volatile long receivedAt;

    @Override public Type<CraftingQualityPayload> type() { return TYPE; }
    public static CraftingQualityPayload latest() { return latest; }
    public static long receivedAt() { return receivedAt; }
    public static void clearClientState() { latest = null; receivedAt = 0; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            latest = payload;
            receivedAt = System.nanoTime();
        }));
    }

    public static void send(ServerPlayer player, CraftingQualityPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(TYPE)) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void send(ServerPlayer player, int containerId, int quality, int minQuality, int maxQuality,
            int experienceCost, int period, boolean visible, boolean crafting, boolean affordable) {
        send(player, new CraftingQualityPayload(containerId, quality, minQuality, maxQuality,
                experienceCost, period, visible, crafting, affordable));
    }
}
