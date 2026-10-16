package io.github.ningmeng123.mite.network;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.advancement.MiteAchievements;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** R196's inventory-open stat is client-originated; this payload can only award its sender that one stat. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public record OpenAchievementsPayload() implements CustomPacketPayload {
    public static final Type<OpenAchievementsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("mite","open_inventory_achievement"));
    public static final StreamCodec<RegistryFriendlyByteBuf,OpenAchievementsPayload> CODEC=StreamCodec.unit(new OpenAchievementsPayload());
    @Override public Type<OpenAchievementsPayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{
        if(c.player().isAlive())MiteAchievements.award(c.player(),"openInventory");
    }));}
}
