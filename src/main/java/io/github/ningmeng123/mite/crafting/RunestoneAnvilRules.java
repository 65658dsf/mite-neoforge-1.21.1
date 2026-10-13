package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.RunestoneItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/** ItemRunestone.canBeRenamed is false; the symbol remains its identity. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class RunestoneAnvilRules {
    private RunestoneAnvilRules() {}
    @SubscribeEvent public static void update(AnvilUpdateEvent event) {
        if (event.getLeft().getItem() instanceof RunestoneItem) event.setCanceled(true);
    }
}
