package io.github.ningmeng123.mite.client;

import com.mojang.datafixers.util.Pair;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/** Effective reach projections for the optional L2Tabs attribute screen. @author NingMeng123 */
public final class L2AttributePanel {
    public static final Holder<Attribute> MELEE_REACH = Holder.direct(
            new RangedAttribute("attribute.mite.effective_melee_reach", 0D, 0D, 1024D));

    private L2AttributePanel() {}

    public static boolean handles(LivingEntity entity, Holder<Attribute> attribute) {
        return entity instanceof Player && (attribute.equals(Attributes.BLOCK_INTERACTION_RANGE)
                || attribute.equals(Attributes.ENTITY_INTERACTION_RANGE) || attribute.equals(MELEE_REACH));
    }

    @Nullable
    public static Double value(LivingEntity entity, Holder<Attribute> attribute) {
        if (!(entity instanceof Player player)) return null;
        if (attribute.equals(Attributes.BLOCK_INTERACTION_RANGE))
            return SourcePlayerReach.blockReach(player, Blocks.AIR.defaultBlockState(), InteractionHand.MAIN_HAND);
        if (attribute.equals(Attributes.ENTITY_INTERACTION_RANGE)) return SourcePlayerReach.interactionReach(player, player);
        if (attribute.equals(MELEE_REACH)) return SourcePlayerReach.meleeReach(player, player);
        return null;
    }

    /** The extra holder belongs to the screen list and is never installed on the player. */
    public static List<?> entries(LivingEntity entity, List<?> original) {
        if (!(entity instanceof Player)) return original;
        int position = original.size();
        for (int index = 0; index < original.size(); index++) {
            if (!(original.get(index) instanceof Pair<?, ?> entry)) continue;
            if (MELEE_REACH.equals(entry.getFirst())) return original;
            if (Attributes.ENTITY_INTERACTION_RANGE.equals(entry.getFirst())) position = index + 1;
        }
        var result = new ArrayList<Object>(original);
        result.add(position, Pair.of(MELEE_REACH, Loaded.ENTRY));
        return result;
    }

    public static List<Component> details(LivingEntity entity, Holder<Attribute> attribute, boolean shift) {
        var result = new ArrayList<Component>();
        if (!(entity instanceof Player player) || !handles(entity, attribute)) return result;
        boolean melee = attribute.equals(MELEE_REACH);
        boolean block = attribute.equals(Attributes.BLOCK_INTERACTION_RANGE);
        result.add(Component.translatable(attribute.value().getDescriptionId()).withStyle(ChatFormatting.GOLD));
        result.add(line("value", value(entity, attribute)).withStyle(ChatFormatting.GREEN));
        result.add(Component.translatable("tooltip.mite.l2_range." + (melee ? "melee" : block ? "block" : "entity"))
                .withStyle(ChatFormatting.GRAY));
        if (shift) {
            if (melee) {
                result.add(line("level_bonus", ProgressionRules.meleeReachBonusForLevel(player.experienceLevel)));
                result.add(line("source_limit", SourcePlayerReach.sourceMeleeReach(player, player)));
                Double attackLimit = BetterCombatPlayerReach.reach(player);
                if (attackLimit != null) result.add(line("attack_limit", attackLimit));
            } else {
                result.add(line("raw_attribute", player.getAttributeValue(attribute)));
                if (block) result.add(line("tool_bonus", player.getAbilities().instabuild ? 0D
                        : SourcePlayerReach.toolBonus(player.getMainHandItem())));
            }
        } else result.add(Component.translatable("tooltip.mite.l2_range.shift").withStyle(ChatFormatting.DARK_GRAY));
        return result;
    }

    private static net.minecraft.network.chat.MutableComponent line(String key, double value) {
        return Component.translatable("tooltip.mite.l2_range." + key,
                ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(value)).withStyle(ChatFormatting.BLUE);
    }

    /** Resolve the optional screen's row metadata only when that screen requests its list. */
    private static final class Loaded {
        private static final Object ENTRY = entry();

        private static Object entry() {
            try {
                return Class.forName("dev.xkmc.l2tabs.init.data.AttrDispEntry")
                        .getConstructor(boolean.class, int.class, double.class).newInstance(false, 0, 0D);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot create the L2Tabs melee reach display row", exception);
            }
        }
    }
}
