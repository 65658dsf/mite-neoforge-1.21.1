package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Reports a blocked visual pack to the server for a chat notification. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record VisualPackBlockedPayload(Reason reason) implements CustomPacketPayload {
    public static final Type<VisualPackBlockedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "visual_pack_blocked"));
    private static final StreamCodec<RegistryFriendlyByteBuf, VisualPackBlockedPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeEnum(payload.reason()),
            buffer -> new VisualPackBlockedPayload(buffer.readEnum(Reason.class)));
    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;
    private static final Map<ServerGamePacketListenerImpl, EnumMap<Reason, Long>> LAST_REPORT = new WeakHashMap<>();

    public enum Reason {
        NIGHT_VISION_SHADER("message.mite.night_vision_shader.blocked", "%s: Night-vision shader use was blocked."),
        NIGHT_VISION_RESOURCE_PACK("message.mite.night_vision_resource_pack.blocked", "%s: Night-vision resource pack use was blocked."),
        XRAY_RESOURCE_PACK("message.mite.xray_resource_pack.blocked", "%s: X-ray resource pack use was blocked.");

        private final String messageKey;
        private final String fallback;

        Reason(String messageKey, String fallback) {
            this.messageKey = messageKey;
            this.fallback = fallback;
        }

        public Component blockedMessage(String playerId) {
            return Component.translatableWithFallback(messageKey, fallback, playerId);
        }

        private boolean blockedByServer() {
            return switch (this) {
                case NIGHT_VISION_SHADER -> MiteConfig.blockNightVisionShaders();
                case NIGHT_VISION_RESOURCE_PACK -> MiteConfig.blockNightVisionResourcePacks();
                case XRAY_RESOURCE_PACK -> MiteConfig.blockXrayResourcePacks();
            };
        }
    }

    @Override public Type<VisualPackBlockedPayload> type() { return TYPE; }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(TYPE, CODEC, (payload, context) -> {
            Reason reason = payload.reason();
            if (!(context.player() instanceof ServerPlayer player) || !reason.blockedByServer()) return;
            long now = System.nanoTime();
            EnumMap<Reason, Long> recent = LAST_REPORT.computeIfAbsent(player.connection, key -> new EnumMap<>(Reason.class));
            Long lastReport = recent.get(reason);
            if (lastReport != null && now - lastReport < REPORT_INTERVAL_NANOS) return;
            recent.put(reason, now);

            var players = player.serverLevel().getServer().getPlayerList();
            players.broadcastSystemMessage(reason.blockedMessage(player.getGameProfile().getName()), false);
            players.broadcastSystemMessage(settingsHint(), false);
        });
    }

    public static Component settingsHint() {
        return Component.translatableWithFallback("message.mite.visual_pack.settings_hint",
                "In singleplayer or as the server owner, you can turn off the restriction in MITE settings under "
                        + "Performance & Administration → Shader and resource pack restrictions.");
    }
}
