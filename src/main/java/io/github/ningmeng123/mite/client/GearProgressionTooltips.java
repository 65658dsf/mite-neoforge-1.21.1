package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.progression.GearModifier;
import java.math.BigDecimal;
import java.util.Comparator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

/** Client presentation of independently stored gear growth. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class GearProgressionTooltips {
    private GearProgressionTooltips() {}
    public static String experience(long millis) {
        return BigDecimal.valueOf(millis, 3).stripTrailingZeros().toPlainString();
    }
    @SubscribeEvent
    public static void append(ItemTooltipEvent event) {
        int cap = GearProgression.maxLevel(event.getItemStack());
        if (cap == 0) return;
        var state = GearProgression.state(event.getItemStack());
        event.getToolTip().add(Component.translatable("tooltip.mite.gear_level", state.level(), cap).withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(state.level() >= cap
                ? Component.translatable("tooltip.mite.gear_max_level").withStyle(ChatFormatting.GOLD)
                : Component.translatable("tooltip.mite.gear_experience", experience(state.experienceMillis()),
                        experience(GearProgression.nextLevelExperienceMillis(event.getItemStack(), state.level()))).withStyle(ChatFormatting.GRAY));
        if (state.affixes().isEmpty()) return;
        boolean showDescriptions = event.getFlags().hasShiftDown();
        var registries = event.getContext().registries();
        if (registries == null && event.getEntity() != null) registries = event.getEntity().registryAccess();
        var enchantments = registries == null ? null : registries.lookup(Registries.ENCHANTMENT).orElse(null);
        event.getToolTip().add(Component.translatable("tooltip.mite.gear_affixes").withStyle(ChatFormatting.DARK_AQUA));
        state.affixes().entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString())).forEach(entry -> {
            GearModifier modifier = GearModifier.fromId(entry.getKey());
            Component name = modifier != null ? modifier.displayName()
                    : enchantments == null ? Component.literal(entry.getKey().toString())
                    : enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()))
                            .map(holder -> holder.value().description()).orElse(Component.literal(entry.getKey().toString()));
            event.getToolTip().add(Component.translatable("tooltip.mite.gear_affix", name, entry.getValue()).withStyle(ChatFormatting.AQUA));
            if (showDescriptions) {
                Component description = description(entry.getKey(), name, entry.getValue());
                if (description != null) event.getToolTip().add(Component.literal("  ").append(description).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
        if (!showDescriptions) {
            String key = Language.getInstance().has("enchdesc.activate.message")
                    ? "enchdesc.activate.message" : "tooltip.mite.enchantment_descriptions_shift";
            Component hint = Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY);
            if (event.getToolTip().stream().noneMatch(line -> line.getString().equals(hint.getString()))) event.getToolTip().add(hint);
        }
    }

    @Nullable
    private static Component description(ResourceLocation id, Component name, int level) {
        String registryKey = id.toLanguageKey("enchantment");
        String nameKey = name.getContents() instanceof TranslatableContents text ? text.getKey() : registryKey;
        for (String base : new String[]{registryKey, nameKey}) {
            for (String suffix : new String[]{"desc", "description", "info"}) {
                String key = base + "." + suffix;
                if (Language.getInstance().has(key)) return Component.translatable(key);
                String levelKey = key + "." + level;
                if (Language.getInstance().has(levelKey)) return Component.translatable(levelKey);
            }
        }
        return null;
    }
}
