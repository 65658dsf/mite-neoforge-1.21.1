package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Development data generation entry point. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MiteDataGenerators {
    private MiteDataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new MiteRecipeProvider(event.getGenerator().getPackOutput(), event.getLookupProvider()));
    }
}
