package io.github.ningmeng123.mite.compat.simplyswords;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/** Main-hand weight penalties for optional Simply Swords weapons. @author NingMeng123 */
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

    @SubscribeEvent
    public static void attributes(ItemAttributeModifierEvent event) {
        if (!twoHanded(event.getItemStack())) return;
        event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED,
                -ATTACK_SPEED_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
        event.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(MOVEMENT_SPEED,
                -MOVEMENT_SPEED_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
    }

    /** Optional classes are resolved only when Better Combat is installed. */
    private static final class Loaded {
        private static Boolean twoHanded(ItemStack stack) {
            var attributes = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
            return attributes == null ? null : attributes.isTwoHanded();
        }
    }
}
