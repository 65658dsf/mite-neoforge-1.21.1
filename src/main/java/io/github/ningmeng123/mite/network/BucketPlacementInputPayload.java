package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.world.BucketPlacementIntent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One use packet's bucket Ctrl state; collection, source placement and XP stay server-owned. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record BucketPlacementInputPayload(InteractionHand hand, int sequence, boolean sourceRequested)
        implements CustomPacketPayload {
    public static final Type<BucketPlacementInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "bucket_placement_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BucketPlacementInputPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public BucketPlacementInputPayload decode(RegistryFriendlyByteBuf buffer) {
            return new BucketPlacementInputPayload(buffer.readEnum(InteractionHand.class), buffer.readVarInt(), buffer.readBoolean());
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, BucketPlacementInputPayload payload) {
            buffer.writeEnum(payload.hand());
            buffer.writeVarInt(payload.sequence());
            buffer.writeBoolean(payload.sourceRequested());
        }
    };

    @Override public Type<BucketPlacementInputPayload> type() { return TYPE; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                BucketPlacementIntent.receive(player, payload.hand(), payload.sequence(), payload.sourceRequested());
        }));
    }
}
