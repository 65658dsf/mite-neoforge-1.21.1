package io.github.ningmeng123.mite.compat.simplyswords;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.rules.SimplySwordsBalanceRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/** Normalize optional base stats before forge affixes; player movement is owned by WeaponEncumbrance. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class SimplySwordsHandling {
    public static final double ATTACK_SPEED_PENALTY = .2D;
    public static final double MOVEMENT_SPEED_PENALTY = .15D;
    public static final TagKey<Item> TWO_HANDED = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "simplyswords/two_handed"));
    private static final ResourceLocation ATTACK_SPEED = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,
            "simplyswords_two_handed_attack_speed");
    private static final ResourceLocation MOVEMENT_SPEED = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,
            "simplyswords_two_handed_movement_speed");

    private SimplySwordsHandling() {}

    public static boolean twoHanded(ItemStack stack) {
        if (SimplySwordsCompat.definition(stack) == null) return false;
        if (BetterCombatBridge.enabled()) {
            Boolean resolved = Loaded.twoHanded(stack);
            if (resolved != null) return resolved;
        }
        return stack.is(TWO_HANDED);
    }

    public static double movementPenalty(ItemStack stack) {
        var definition = SimplySwordsCompat.definition(stack);
        return definition == null ? 0D : SimplySwordsBalanceRules.movementPenalty(definition.family(), twoHanded(stack));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (SimplySwordsCompat.definition(stack) == null) return;
        // A saved component list may repeat one ID in MAINHAND and ANY; removing a single keyed entry leaves an orphan.
        event.removeIf(entry -> entry.attribute().equals(Attributes.MOVEMENT_SPEED) && entry.modifier().id().equals(MOVEMENT_SPEED)
                || entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID)
                || entry.attribute().equals(Attributes.ATTACK_SPEED) && (entry.modifier().id().equals(Item.BASE_ATTACK_SPEED_ID)
                        || entry.modifier().id().equals(ATTACK_SPEED)));
        boolean twoHanded = twoHanded(stack);
        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                SimplySwordsCompat.damageBonus(stack), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        // The base family rate stays unchanged; this is the sole two-handed20% speed multiplier.
        event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                SimplySwordsCompat.baseAttackSpeedBonus(stack, twoHanded), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (twoHanded) event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED,
                -ATTACK_SPEED_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
    }

    /** Optional classes are resolved only when Better Combat is installed. */
    private static final class Loaded {
        private static Boolean twoHanded(ItemStack stack) {
            var attributes = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
            return attributes == null ? null : attributes.isTwoHanded();
        }
    }
}
