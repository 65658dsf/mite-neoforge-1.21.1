package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.EggThrowControl;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Explicit Ctrl-throw intent; the server still requires an egg in the requested hand. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record ThrowEggPayload(InteractionHand hand, float yRot, float xRot) implements CustomPacketPayload {
    public static final Type<ThrowEggPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "throw_egg"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThrowEggPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public ThrowEggPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ThrowEggPayload(buffer.readEnum(InteractionHand.class), buffer.readFloat(), buffer.readFloat());
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, ThrowEggPayload value) {
            buffer.writeEnum(value.hand());
            buffer.writeFloat(value.yRot());
            buffer.writeFloat(value.xRot());
        }
    };

    @Override public Type<ThrowEggPayload> type() { return TYPE; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                EggThrowControl.throwEgg(player, payload.hand(), payload.yRot(), payload.xRot());
        }));
    }
}
