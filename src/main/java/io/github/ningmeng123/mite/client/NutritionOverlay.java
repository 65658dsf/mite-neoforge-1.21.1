package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.NutritionPayload;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.rules.NutritionRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Source nutrients and food values; AppleSkin's compatible saturation preview represents N. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class NutritionOverlay {
    private static final int WIDTH = 81;
    private static final int NUTRIENT_WIDTH = 120;
    private static final int NUTRIENT_HEIGHT = 5;
    private NutritionOverlay() {}

    @SubscribeEvent public static void besideHotbar(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft minecraft = Minecraft.getInstance();
        var state = visibleState(minecraft);
        if (state == null) return;
        var graphics = event.getGuiGraphics();
        int center = graphics.guiWidth() / 2;
        boolean rightHanded = minecraft.player.getMainArm() == HumanoidArm.RIGHT;
        int leftOccupied = 0;
        int rightOccupied = 0;
        if (!minecraft.player.getOffhandItem().isEmpty()) {
            if (rightHanded) leftOccupied = 29;
            else rightOccupied = 29;
        }
        // Reserve this space even between attacks so the nutrient bars do not jump during cooldown.
        if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            if (rightHanded) rightOccupied = Math.max(rightOccupied, 24);
            else leftOccupied = Math.max(leftOccupied, 24);
        }
        int leftEnd = center - 91 - leftOccupied - 10;
        int rightStart = center + 91 + rightOccupied + 10;
        int leftWidth = Math.min(NUTRIENT_WIDTH, Math.max(0, leftEnd - 4));
        int rightWidth = Math.min(NUTRIENT_WIDTH, Math.max(0, graphics.guiWidth() - 4 - rightStart));
        drawNutrient(graphics, minecraft, leftEnd - leftWidth, leftWidth, state.protein(), 0xFFB68A2C);
        drawNutrient(graphics, minecraft, rightStart, rightWidth, state.plants(), 0xFF8EBC28);
    }

    private static NutritionPayload visibleState(Minecraft minecraft) {
        var state = NutritionPayload.latest();
        return minecraft.player == null || minecraft.gameMode == null || minecraft.options.hideGui
                || !minecraft.gameMode.canHurtPlayer() || minecraft.player.isSpectator()
                || state == null || state.entityId() != minecraft.player.getId() ? null : state;
    }

    private static void drawNutrient(GuiGraphics graphics, Minecraft minecraft, int x, int width, int value, int color) {
        if (width <= 2) return;
        int maximum = NutritionRules.MICRONUTRIENT_MAX;
        int current = Math.clamp(value, 0, maximum);
        int percent = (int) ((long) current * 100 / maximum);
        int y = graphics.guiHeight() - NUTRIENT_HEIGHT;
        graphics.fill(x, y, x + width, graphics.guiHeight(), 0xFF101010);
        graphics.fill(x + 1, y + 1, x + width - 1, graphics.guiHeight() - 1, 0xFF292929);
        int filled = (int) ((long) (width - 2) * current / maximum);
        if (filled > 0) graphics.fill(x + 1, y + 1, x + 1 + filled, graphics.guiHeight() - 1, color);
        String label = current + "/" + maximum + " (" + percent + "%)";
        int textWidth = minecraft.font.width(label);
        float scale = Math.min(1F, (width - 2F) / Math.max(1, textWidth));
        // GUI coordinates already include the user's scale. Only shrink when the available side space needs it.
        graphics.pose().pushPose();
        graphics.pose().translate(x + 1, y - 3 - minecraft.font.lineHeight * scale, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(minecraft.font, label, 0, 0, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    @SubscribeEvent public static void afterAir(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.AIR_LEVEL)) return;
        Minecraft minecraft = Minecraft.getInstance();
        var state = visibleState(minecraft);
        if (state == null) return;
        var graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2 + 10;
        List<Component> statuses = new ArrayList<>();
        if (minecraft.player.hasEffect(MobEffects.CONFUSION))
            statuses.add(Component.translatable("hud.mite.food_nausea"));
        if (state.protein() <= 0 || state.plants() <= 0)
            statuses.add(Component.translatable("hud.mite.malnourished"));
        for (Component status : statuses) {
            int statusY = graphics.guiHeight() - minecraft.gui.rightHeight;
            graphics.drawString(minecraft.font, status, x + WIDTH - minecraft.font.width(status), statusY, 0xE6AA77, true);
            minecraft.gui.rightHeight += 10;
        }
    }

    @SubscribeEvent public static void explainFood(ItemTooltipEvent event) {
        var profile = MiteFoodCatalog.profile(event.getItemStack());
        if (profile == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft == null ? null : minecraft.player;
        var state = NutritionPayload.latest();
        int level = player != null && state != null && state.entityId() == player.getId() ? state.resistanceLevel() : -1;
        int satiation = NutritionRules.adjustedSatiation(profile.satiation(), profile.sugar(), level);
        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.translatable("tooltip.mite.food_values", satiation, profile.nutrition())
                .withStyle(ChatFormatting.GREEN));
        var nativeFood = event.getItemStack().get(DataComponents.FOOD);
        float saturationGain = event.getItemStack().is(Items.WHEAT_SEEDS) && nativeFood != null
                ? nativeFood.saturation() : profile.saturationGain();
        if (saturationGain > 0F)
            tooltip.add(Component.translatable("tooltip.mite.food_native_saturation", saturationGain)
                    .withStyle(ChatFormatting.YELLOW));
        addGain(tooltip, "tooltip.mite.food_protein_gain", NutritionRules.addMicronutrient(0, profile.nutrition(), profile.protein()));
        addGain(tooltip, "tooltip.mite.food_fat_gain", NutritionRules.addMicronutrient(0, profile.nutrition(), profile.fat()));
        addGain(tooltip, "tooltip.mite.food_plant_nutrient_gain", profile.plantNutrientGain());
        addGain(tooltip, "tooltip.mite.food_sugar", NutritionRules.addedResistance(profile.sugar()));
    }

    private static void addGain(List<Component> tooltip, String translationKey, int amount) {
        if (amount > 0) {
            tooltip.add(Component.translatable(translationKey, amount).withStyle(ChatFormatting.YELLOW));
        }
    }

    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        NutritionPayload.clearClientState();
    }
}
