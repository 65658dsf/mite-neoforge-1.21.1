package io.github.ningmeng123.mite.combat;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.NativeWeaponBalanceRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/** Rebuild only the two canonical base modifiers, including on saved stacks with old attribute components. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class NativeWeaponAttributes {
    private record Profile(double damageBonus, double attackSpeed, double movementPenalty) {}
    private NativeWeaponAttributes() {}

    private static Profile profile(ItemStack stack) {
        if (stack.isEmpty() || SimplySwordsCompat.definition(stack) != null) return null;
        var definition = MiteTools.definition(stack);
        if (definition != null) {
            if (NativeWeaponBalanceRules.starter(definition.material())) return null;
            return new Profile(NativeWeaponBalanceRules.damageBonus(definition.family(), definition.material(), definition.damage()),
                    NativeWeaponBalanceRules.attacksPerSecond(definition.family(), definition.material()),
                    NativeWeaponBalanceRules.movementPenalty(definition.family(), definition.material()));
        }
        float rawBonus = (float)stack.getItem().getDefaultAttributeModifiers().modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                        && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID)
                        && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE)
                .mapToDouble(entry -> entry.modifier().amount()).sum();
        if (stack.is(Items.TRIDENT)) return new Profile((rawBonus + 1D) * .72D - 1D, 1D, .05D);
        if (stack.is(Items.MACE)) return new Profile((rawBonus + 1D) * .8D - 1D, .65D, .15D);
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")) return null;
        var profile = EquipmentDurability.toolProfile(stack);
        if (profile == null) return null;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        String material = path.substring(0, path.indexOf('_') < 0 ? path.length() : path.indexOf('_'));
        if (material.equals("wooden") || material.equals("stone")) return null;
        return new Profile(NativeWeaponBalanceRules.damageBonus(profile.family(), material, rawBonus),
                NativeWeaponBalanceRules.attacksPerSecond(profile.family(), material),
                NativeWeaponBalanceRules.movementPenalty(profile.family(), material));
    }

    public static double movementPenalty(ItemStack stack) {
        Profile profile = profile(stack);
        return profile == null ? 0D : profile.movementPenalty();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attributes(ItemAttributeModifierEvent event) {
        Profile profile = profile(event.getItemStack());
        if (profile == null) return;
        event.removeIf(entry -> (entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID))
                || (entry.attribute().equals(Attributes.ATTACK_SPEED)
                && entry.modifier().id().equals(Item.BASE_ATTACK_SPEED_ID)));
        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                profile.damageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                profile.attackSpeed() - 4D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
    }
}
