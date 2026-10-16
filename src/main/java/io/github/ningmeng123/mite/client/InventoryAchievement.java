package io.github.ningmeng123.mite.client;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.OpenAchievementsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
/** Native inventory-open trigger, including recipe-book reinitialization without duplicate rewards. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,value=Dist.CLIENT)
public final class InventoryAchievement {
    private InventoryAchievement(){}
    @SubscribeEvent public static void open(ScreenEvent.Init.Post event){
        var connection=Minecraft.getInstance().getConnection();
        if(event.getScreen() instanceof InventoryScreen&&connection!=null&&connection.hasChannel(OpenAchievementsPayload.TYPE))PacketDistributor.sendToServer(new OpenAchievementsPayload());
    }
}
