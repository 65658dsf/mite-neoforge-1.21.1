package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.rules.LavaBucketRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Contextual material hints; thermal processing details are shown in JEI. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class FurnaceThermalDisplay {
    private FurnaceThermalDisplay() {}

    @SubscribeEvent
    public static void explainFuel(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.getItem() instanceof BlockItem block) {
            var profile = FurnaceThermalController.profile(block.getBlock().defaultBlockState());
            if (profile != null) event.getToolTip().add(Component.translatable("tooltip.mite.furnace_max_heat", profile.maxHeat()).withStyle(ChatFormatting.GRAY));
        }
        String metal = stack.is(Items.BUCKET) ? "iron" : stack.getItem() instanceof MiteBucketItem bucket
                && bucket.contents() == MiteBucketItem.Contents.EMPTY ? bucket.metal() : null;
        if (metal != null) event.getToolTip().add(Component.translatable("tooltip.mite.bucket_melting", Math.round(LavaBucketRules.meltingChance(metal) * 100F))
                .withStyle(ChatFormatting.GRAY));
        if (stack.is(Items.SAND) || stack.is(Items.RED_SAND)) event.getToolTip().add(
                Component.translatable("tooltip.mite.furnace_sand_batch").withStyle(ChatFormatting.GRAY));
        if (stack.is(Items.CLAY_BALL)) event.getToolTip().add(
                Component.translatable("tooltip.mite.furnace_clay_batch").withStyle(ChatFormatting.GRAY));
    }
}
