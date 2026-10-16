package io.github.ningmeng123.mite.client;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.PlayerRest;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
/** Source 100-tick fade to black and gradual waking; native controls remain visible. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SleepConsciousnessDisplay {
    private SleepConsciousnessDisplay(){}
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent e){e.registerBelowAll(ResourceLocation.fromNamespaceAndPath("mite","sleep_consciousness"),(g,d)->{
        var p=Minecraft.getInstance().player;
        if(p==null)return;
        int alpha=Math.min(255,255*PlayerRest.counter(p)/100);
        if(alpha>0)g.fill(0,0,g.guiWidth(),g.guiHeight(),alpha<<24);
    });}
}
