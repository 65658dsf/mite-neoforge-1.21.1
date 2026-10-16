package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.curse.PlayerCurses;
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

/** Discovery request only: a client can never apply, replace, or lift a curse. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record RevealCursePayload(int id) implements CustomPacketPayload {
    public static final Type<RevealCursePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "reveal_curse"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RevealCursePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RevealCursePayload::id, RevealCursePayload::new);
    @Override public Type<RevealCursePayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && payload.id() > 0
                    && payload.id() == PlayerCurses.state(player).id()) PlayerCurses.reveal(player);
        }));
    }
    public static void send(int id) { PacketDistributor.sendToServer(new RevealCursePayload(id)); }
}
