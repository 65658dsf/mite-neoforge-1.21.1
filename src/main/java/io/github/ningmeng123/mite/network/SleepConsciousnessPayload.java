package io.github.ningmeng123.mite.network;
import io.github.ningmeng123.mite.Mite;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Read-only source consciousness snapshot. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public record SleepConsciousnessPayload(int entityId,int counter) implements CustomPacketPayload {
    public static final Type<SleepConsciousnessPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("mite","sleep_consciousness"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SleepConsciousnessPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.entityId);b.writeVarInt(p.counter);},b->new SleepConsciousnessPayload(b.readVarInt(),b.readVarInt()));
    @Override public Type<SleepConsciousnessPayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->{
        if(c.player().getId()==p.entityId)c.player().getPersistentData().putInt("mite_sleep_consciousness",Math.clamp(p.counter,0,110));
    }));}
    public static void send(ServerPlayer p,int counter){if(p.connection!=null&&p.connection.hasChannel(TYPE))PacketDistributor.sendToPlayer(p,new SleepConsciousnessPayload(p.getId(),counter));}
}
