package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.curse.CurseState;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Private owner-only mirror, with explicit channel negotiation for non-network players. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record CurseStatePayload(int id, boolean known) implements CustomPacketPayload {
    public static final Type<CurseStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "curse_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CurseStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CurseStatePayload::id, ByteBufCodecs.BOOL, CurseStatePayload::known, CurseStatePayload::new);
    @Override public Type<CurseStatePayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (payload.id() >= 0 && payload.id() <= 16)
                context.player().setData(MiteAttachments.CURSE_STATE, new CurseState(payload.id(), payload.known()));
        }));
    }
    public static void send(ServerPlayer player, CurseState state) {
        if (player.connection != null && player.connection.hasChannel(TYPE))
            PacketDistributor.sendToPlayer(player, new CurseStatePayload(state.id(), state.known()));
    }
}
