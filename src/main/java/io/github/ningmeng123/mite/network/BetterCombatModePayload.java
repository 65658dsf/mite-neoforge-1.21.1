package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Toggle the sending player's attack mode on the server thread. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record BetterCombatModePayload() implements CustomPacketPayload {
    public static final Type<BetterCombatModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "better_combat_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BetterCombatModePayload> STREAM_CODEC =
            StreamCodec.unit(new BetterCombatModePayload());

    @Override public Type<BetterCombatModePayload> type() { return TYPE; }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !BetterCombatBridge.enabled()) return;
            boolean vanilla = !BetterCombatPlayerMode.isVanilla(player);
            BetterCombatPlayerMode.setVanilla(player, vanilla);
            String message = vanilla ? "message.mite.better_combat.off"
                    : BetterCombatPlayerMode.attacksDisabled(player) ? "message.mite.better_combat.server_disabled"
                    : "message.mite.better_combat.on";
            player.displayClientMessage(Component.translatable(message), true);
        });
    }
}
