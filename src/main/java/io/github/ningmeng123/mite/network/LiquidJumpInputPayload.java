package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.LiquidJumpEffort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A boolean is an input request, never a client-provided food amount or target player. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public record LiquidJumpInputPayload(boolean held) implements CustomPacketPayload {
    public static final Type<LiquidJumpInputPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,"liquid_jump_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,LiquidJumpInputPayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.BOOL,LiquidJumpInputPayload::held,LiquidJumpInputPayload::new);
    @Override public Type<LiquidJumpInputPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE,STREAM_CODEC,(payload,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player) LiquidJumpEffort.setInput(player,payload.held());
        }));
    }
}
