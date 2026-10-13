package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
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

/** Display-only state: the client never requests progress or declares completion. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record CraftingProgressPayload(int containerId, int progress, int period, boolean hungry, boolean active)
        implements CustomPacketPayload {
    public static final Type<CraftingProgressPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "crafting_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftingProgressPayload::containerId,
            ByteBufCodecs.VAR_INT, CraftingProgressPayload::progress,
            ByteBufCodecs.VAR_INT, CraftingProgressPayload::period,
            ByteBufCodecs.BOOL, CraftingProgressPayload::hungry,
            ByteBufCodecs.BOOL, CraftingProgressPayload::active, CraftingProgressPayload::new);
    // Plain client cache is safe to load on a dedicated server: no rendering classes are referenced here.
    private static volatile CraftingProgressPayload latest;
    private static volatile long receivedAt;
    @Override public Type<CraftingProgressPayload> type() { return TYPE; }
    public static CraftingProgressPayload latest() { return latest; }
    public static long receivedAt() { return receivedAt; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            latest = payload;
            receivedAt = System.nanoTime();
        }));
    }
    public static void send(ServerPlayer player, int containerId, int progress, int period, boolean hungry, boolean active) {
        // A display-only update must never abort a craft when a connection has no negotiated UI channel.
        if (player.connection == null || !player.connection.hasChannel(TYPE)) return;
        PacketDistributor.sendToPlayer(player, new CraftingProgressPayload(containerId, progress, period, hungry, active));
    }
}
