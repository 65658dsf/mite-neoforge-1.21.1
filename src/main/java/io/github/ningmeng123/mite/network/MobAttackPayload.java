package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.BetterCombatMobAnimations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only server-approved swings may animate a mob on tracking clients. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record MobAttackPayload(int entityId, String animation, int duration, float upswing) implements CustomPacketPayload {
    public static final Type<MobAttackPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "mob_attack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MobAttackPayload> STREAM_CODEC = new StreamCodec<>() {
        public MobAttackPayload decode(RegistryFriendlyByteBuf buffer) {
            return new MobAttackPayload(buffer.readVarInt(), buffer.readUtf(256), buffer.readVarInt(), buffer.readFloat());
        }
        public void encode(RegistryFriendlyByteBuf buffer, MobAttackPayload value) {
            buffer.writeVarInt(value.entityId()); buffer.writeUtf(value.animation(), 256);
            buffer.writeVarInt(value.duration()); buffer.writeFloat(value.upswing());
        }
    };
    @Override public Type<MobAttackPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player().level().getEntity(payload.entityId()) instanceof Mob mob)) return;
            if (payload.animation().isEmpty()) BetterCombatMobAnimations.stop(mob);
            else if (payload.duration() > 0 && payload.duration() <= 1200 && Float.isFinite(payload.upswing()))
                BetterCombatMobAnimations.start(mob, payload.animation(), payload.duration(), payload.upswing(), false);
        }));
    }
    public static void send(Mob mob, String animation, int duration, float upswing) {
        if (mob.level() instanceof ServerLevel level)
            for (var player : level.getChunkSource().chunkMap.getPlayers(mob.chunkPosition(), false))
                if (player.connection != null && player.connection.hasChannel(TYPE))
                    PacketDistributor.sendToPlayer(player, new MobAttackPayload(mob.getId(), animation, duration, upswing));
    }
}
