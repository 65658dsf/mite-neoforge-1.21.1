package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Plant PNG alpha is rendered as native cutout blocks. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FoodPlantRendering {
    private FoodPlantRendering() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(MiteFoodPlants.ONIONS.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(MiteFoodPlants.BLUEBERRY_BUSH.get(), RenderType.cutout());
        });
    }
}
