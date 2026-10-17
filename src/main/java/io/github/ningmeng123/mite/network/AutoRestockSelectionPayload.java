package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.Util;
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

/** Display-only acknowledgement lets same-item AUM survive an authoritative auto switch. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record AutoRestockSelectionPayload(int fromSlot, int toSlot) implements CustomPacketPayload {
    public static final Type<AutoRestockSelectionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "auto_restock_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AutoRestockSelectionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AutoRestockSelectionPayload::fromSlot, ByteBufCodecs.VAR_INT,
            AutoRestockSelectionPayload::toSlot, AutoRestockSelectionPayload::new);
    private static volatile AutoRestockSelectionPayload latest;
    private static volatile long receivedAt;
    @Override public Type<AutoRestockSelectionPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (value, context) -> context.enqueueWork(() -> {
            latest = value; receivedAt = Util.getMillis();
        }));
    }
    public static boolean accept(int from, int to) {
        var value = latest;
        latest = null;
        if (value == null || value.fromSlot != from || value.toSlot != to || Util.getMillis() - receivedAt > 3_000L) return false;
        return true;
    }
    public static void clear() { latest = null; receivedAt = 0L; }
    public static boolean failed(int slot) {
        var value = latest;
        if (value == null || value.fromSlot != slot || value.toSlot != -1 || Util.getMillis() - receivedAt > 3_000L) return false;
        latest = null; return true;
    }
    public static void send(ServerPlayer player, int from, int to) {
        if (player.connection != null && player.connection.hasChannel(TYPE))
            PacketDistributor.sendToPlayer(player, new AutoRestockSelectionPayload(from, to));
    }
}
