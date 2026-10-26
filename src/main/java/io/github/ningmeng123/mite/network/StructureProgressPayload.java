package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.advancement.StructureAdvancements;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import io.netty.handler.codec.DecoderException;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded, server-only exploration snapshot for advancement tooltips. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record StructureProgressPayload(int enabledMask, long day, int flags, Map<Metric, Long> values)
        implements CustomPacketPayload {
    public static final Type<StructureProgressPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "structure_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StructureProgressPayload> CODEC = new StreamCodec<>() {
        @Override public StructureProgressPayload decode(RegistryFriendlyByteBuf buffer) {
            int enabled = buffer.readVarInt();
            long day = buffer.readVarLong();
            int flags = buffer.readVarInt();
            if (enabled < 0 || (enabled & ~StructureAdvancements.ALL_ENABLED) != 0 || day < 1
                    || flags < 0 || (flags & ~StructureProgressData.ALL_CONDITIONS) != 0)
                throw new DecoderException("Invalid structure progress header");
            var values = new EnumMap<Metric, Long>(Metric.class);
            for (Metric metric : Metric.values()) {
                long value = buffer.readVarLong();
                if (value < 0 || value > metric.cap()) throw new DecoderException("Invalid structure metric " + metric);
                values.put(metric, value);
            }
            return new StructureProgressPayload(enabled, day, flags, values);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, StructureProgressPayload payload) {
            buffer.writeVarInt(payload.enabledMask());
            buffer.writeVarLong(payload.day());
            buffer.writeVarInt(payload.flags());
            // Fixed enum order avoids attacker-controlled collection sizes or arbitrary keys.
            for (Metric metric : Metric.values()) buffer.writeVarLong(payload.values().get(metric));
        }
    };
    private static volatile StructureProgressPayload latest;
    private static final Map<ServerPlayer, StructureProgressPayload> SENT = new WeakHashMap<>();

    public StructureProgressPayload {
        enabledMask &= StructureAdvancements.ALL_ENABLED;
        day = Math.max(1L, day);
        flags &= StructureProgressData.ALL_CONDITIONS;
        var normalized = new EnumMap<Metric, Long>(Metric.class);
        for (Metric metric : Metric.values()) normalized.put(metric, metric.clamp(values.getOrDefault(metric, 0L)));
        values = Map.copyOf(normalized);
    }

    @Override public Type<StructureProgressPayload> type() { return TYPE; }
    public static StructureProgressPayload latest() { return latest; }
    public static void clearClientState() { latest = null; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> latest = payload));
    }

    public static void send(ServerPlayer player, StructureProgressPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(TYPE) || payload.equals(SENT.get(player))) return;
        SENT.put(player, payload);
        PacketDistributor.sendToPlayer(player, payload);
    }
}
