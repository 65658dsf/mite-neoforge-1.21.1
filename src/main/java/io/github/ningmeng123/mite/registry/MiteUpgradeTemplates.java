package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.SmithingTemplateItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Native smithing templates for one-step metal equipment upgrades. @author NingMeng123 */
public final class MiteUpgradeTemplates {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    public static final DeferredItem<SmithingTemplateItem> UPGRADE_TEMPLATE = ITEMS.register(
            "equipment_upgrade_smithing_template", () -> new SmithingTemplateItem(
                    description("applies_to").withStyle(ChatFormatting.BLUE),
                    description("ingredients").withStyle(ChatFormatting.BLUE),
                    Component.translatable("upgrade.mite.equipment_upgrade").withStyle(ChatFormatting.GRAY),
                    description("base_slot_description"), description("additions_slot_description"),
                    List.of(icon("empty_armor_slot_helmet"), icon("empty_slot_sword"),
                            icon("empty_armor_slot_chestplate"), icon("empty_slot_pickaxe"),
                            icon("empty_armor_slot_leggings"), icon("empty_slot_axe"),
                            icon("empty_armor_slot_boots"), icon("empty_slot_hoe"), icon("empty_slot_shovel")),
                    List.of(icon("empty_slot_ingot"))));

    private MiteUpgradeTemplates() {}

    private static net.minecraft.network.chat.MutableComponent description(String suffix) {
        return Component.translatable("item.mite.smithing_template.equipment_upgrade." + suffix);
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.withDefaultNamespace("item/" + name);
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
