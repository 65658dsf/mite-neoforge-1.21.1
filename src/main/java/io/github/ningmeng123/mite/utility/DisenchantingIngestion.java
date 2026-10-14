package io.github.ningmeng123.mite.utility;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.R196DisenchantingBottleItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** Source uf checks ingestion throughout use, including newly gained nausea or eye submersion. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class DisenchantingIngestion {
    private DisenchantingIngestion() {}
    @SubscribeEvent public static void start(LivingEntityUseItemEvent.Start event) {
        if (event.getItem().getItem() instanceof R196DisenchantingBottleItem && !R196DisenchantingBottleItem.canDrink(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(LivingEntityUseItemEvent.Tick event) {
        if (event.getItem().getItem() instanceof R196DisenchantingBottleItem && !R196DisenchantingBottleItem.canDrink(event.getEntity())) event.setCanceled(true);
    }
}
