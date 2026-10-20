package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.progression.GearProgressionRules;
import io.github.ningmeng123.mite.progression.GearModifier;
import java.math.BigDecimal;
import java.util.Comparator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

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
        event.getToolTip().add(Component.translatable("tooltip.mite.gear_affixes").withStyle(ChatFormatting.DARK_AQUA));
        state.affixes().entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString())).forEach(entry -> {
            GearModifier modifier = GearModifier.fromId(entry.getKey());
            Component name = modifier != null ? modifier.displayName()
                    : event.getEntity() == null ? Component.literal(entry.getKey().toString())
                    : event.getEntity().level().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                            .getHolder(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()))
                            .map(holder -> holder.value().description()).orElse(Component.literal(entry.getKey().toString()));
            event.getToolTip().add(Component.translatable("tooltip.mite.gear_affix", name, entry.getValue()).withStyle(ChatFormatting.AQUA));
        });
    }
}
