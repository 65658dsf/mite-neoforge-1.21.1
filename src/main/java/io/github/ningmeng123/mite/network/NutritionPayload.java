package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.DualFoodData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server snapshot for the HUD and client food eligibility; no client write-back. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record NutritionPayload(int entityId, int satiation, int nutrition, int limit,
        int protein, int fat, int plants, int resistance, int resistanceLevel, float saturation) implements CustomPacketPayload {
    public static final Type<NutritionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "nutrition"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NutritionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public NutritionPayload decode(RegistryFriendlyByteBuf buffer) {
            return new NutritionPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat());
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, NutritionPayload value) {
            buffer.writeVarInt(value.entityId());
            buffer.writeVarInt(value.satiation());
            buffer.writeVarInt(value.nutrition());
            buffer.writeVarInt(value.limit());
            buffer.writeVarInt(value.protein());
            buffer.writeVarInt(value.fat());
            buffer.writeVarInt(value.plants());
            buffer.writeVarInt(value.resistance());
            buffer.writeVarInt(value.resistanceLevel());
            buffer.writeFloat(value.saturation());
        }
    };
    private static volatile NutritionPayload latest;
    private static volatile long receivedAt;

    @Override public Type<NutritionPayload> type() { return TYPE; }
    public static NutritionPayload latest() { return latest; }
    public static long receivedAt() { return receivedAt; }
    public static void clearClientState() { latest = null; receivedAt = 0; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            latest = payload;
            receivedAt = System.nanoTime();
            var player = context.player();
            if (player.getId() == payload.entityId() && player.getFoodData() instanceof DualFoodData food) {
                food.mite$setNutrition(payload.nutrition());
                player.getFoodData().setSaturation(payload.saturation());
            }
        }));
    }

    public static void send(ServerPlayer player, NutritionPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(TYPE)) return;
        PacketDistributor.sendToPlayer(player, payload);
    }
}
