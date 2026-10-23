package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.IngestionDelay;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.UseAnim;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** Client-only source ingestion cadence; it does not cancel an ingestion already in progress. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class ClientIngestionDelay {
    private static final IngestionDelay DELAY = new IngestionDelay();
    private static Object player;
    private ClientIngestionDelay() {}

    public static boolean ingestion(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof ThrowablePotionItem) return false;
        UseAnim action = stack.getUseAnimation();
        return action == UseAnim.EAT || action == UseAnim.DRINK;
    }

    public static boolean ready(Minecraft minecraft, ItemStack stack) {
        if (player != minecraft.player) { player = minecraft.player; DELAY.clear(); }
        return !ingestion(stack) || DELAY.ready(Util.getMillis());
    }

    @SubscribeEvent public static void finished(LivingEntityUseItemEvent.Finish event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() == minecraft.player && ingestion(event.getItem())) {
            player = minecraft.player;
            DELAY.finished(Util.getMillis());
            AutoHarvestInput.finishedIngestion(event.getItem(), event.getResultStack());
        }
    }
}
