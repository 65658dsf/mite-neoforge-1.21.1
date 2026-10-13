package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.GemSacrifice;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Explain why wood cannot be processed in the inventory grid. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class CraftingTooltips {
    private CraftingTooltips() {}

    @SubscribeEvent
    public static void explainEquipment(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (EquipmentDurability.isManaged(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.mite.quality",
                    Component.translatable("quality.mite." + EquipmentDurability.quality(stack).name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable("tooltip.mite.durability",
                    Math.max(stack.getMaxDamage() - stack.getDamageValue(), 0), stack.getMaxDamage())
                    .withStyle(ChatFormatting.GRAY));
        }
        float required = WorkbenchRules.required(stack);
        if (required > 0) {
            String material = required >= 256 ? "adamantium" : required >= 64 ? "mithril"
                    : required >= 16 ? "ancient_metal" : required >= 8 ? "iron" : required >= 4 ? "copper" : "flint";
            event.getToolTip().add(Component.translatable("tooltip.mite.workbench_required",
                    Component.translatable("block.mite." + material + "_crafting_table")).withStyle(ChatFormatting.DARK_AQUA));
        }
        int xp = GemSacrifice.experienceValue(event.getItemStack());
        if (xp > 0) event.getToolTip().add(Component.translatable("tooltip.mite.gem_sacrifice", xp).withStyle(ChatFormatting.AQUA));
        if (event.getItemStack().is(ItemTags.LOGS) || event.getItemStack().is(ItemTags.BAMBOO_BLOCKS)) {
            event.getToolTip().add(Component.translatable("tooltip.mite.planks_require_workbench")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
