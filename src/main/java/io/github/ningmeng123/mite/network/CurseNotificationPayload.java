package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import net.minecraft.nbt.CompoundTag;
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

/** Realization and lifting have a 100-tick notice; merely reconnecting does not replay it. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record CurseNotificationPayload(boolean active) implements CustomPacketPayload {
    public static final Type<CurseNotificationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "curse_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CurseNotificationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CurseNotificationPayload::active, CurseNotificationPayload::new);
    @Override public Type<CurseNotificationPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            var tag = new CompoundTag();
            tag.putInt("until", context.player().tickCount + 100);
            tag.putBoolean("active", payload.active());
            context.player().getPersistentData().put(PlayerCurses.NOTIFICATION, tag);
        }));
    }
    public static void send(ServerPlayer player, boolean active) {
        if (player.connection != null && player.connection.hasChannel(TYPE)) PacketDistributor.sendToPlayer(player, new CurseNotificationPayload(active));
    }
}
