package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.NutritionPayload;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.rules.NutritionRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Shows R196 nutrition separately from vanilla/AppleSkin saturation. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class NutritionOverlay {
    private NutritionOverlay() {}

    @SubscribeEvent public static void explainFood(ItemTooltipEvent event) {
        var profile = MiteFoodCatalog.profile(event.getItemStack());
        if (profile == null) return;
        var player=Minecraft.getInstance().player;
        var state=NutritionPayload.latest();
        int level=player!=null && state!=null && state.entityId()==player.getId() ? state.resistanceLevel() : -1;
        int satiation=NutritionRules.adjustedSatiation(profile.satiation(),profile.sugar(),level);
        event.getToolTip().add(Component.translatable("tooltip.mite.food_values", satiation, profile.nutrition())
                .withStyle(ChatFormatting.GREEN));
        var nativeFood = event.getItemStack().get(DataComponents.FOOD);
        if (event.getItemStack().is(Items.WHEAT_SEEDS) && nativeFood != null)
            event.getToolTip().add(Component.translatable("tooltip.mite.food_native_saturation", nativeFood.saturation())
                    .withStyle(ChatFormatting.YELLOW));
        List<Component> sources = new ArrayList<>();
        if (profile.protein()) sources.add(Component.translatable("tooltip.mite.food_protein"));
        if (profile.fat()) sources.add(Component.translatable("tooltip.mite.food_fat"));
        if (profile.plants()) sources.add(Component.translatable("tooltip.mite.food_plants"));
        if (!sources.isEmpty()) {
            var joined = Component.empty();
            for (int i = 0; i < sources.size(); i++) {
                if (i > 0) joined.append(Component.translatable("tooltip.mite.food_separator"));
                joined.append(sources.get(i));
            }
            event.getToolTip().add(Component.translatable("tooltip.mite.food_sources", joined).withStyle(ChatFormatting.GRAY));
        }
        if (profile.sugar() > 0)
            event.getToolTip().add(Component.translatable("tooltip.mite.food_sugar", NutritionRules.addedResistance(profile.sugar()))
                    .withStyle(ChatFormatting.YELLOW));
    }

    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        NutritionPayload.clearClientState();
    }
}
