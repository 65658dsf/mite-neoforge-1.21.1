package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.AutoRestock;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A screen can only suppress this player's automatic movement; no item or quantity is client supplied. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record AutoRestockScreenPayload(boolean open) implements CustomPacketPayload {
    public static final Type<AutoRestockScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "auto_restock_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AutoRestockScreenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AutoRestockScreenPayload::open, AutoRestockScreenPayload::new);
    @Override public Type<AutoRestockScreenPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (value, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) AutoRestock.pause(player, value.open);
        }));
    }
}
