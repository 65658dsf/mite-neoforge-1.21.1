package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.registry.MiteTools;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Shared pursuit and native-pickup policy for quality-aware armed monsters. @author NingMeng123 */
public final class MobEquipmentUpgrades {
    private enum Kind { NONE, ARMOR, SHIELD, BOW, CROSSBOW, MELEE }

    private MobEquipmentUpgrades() {}

    public static boolean handles(Mob mob) { return MobShields.handles(mob); }

    public static boolean equipment(ItemStack stack) { return kind(stack) != Kind.NONE; }

    /** Identifies the actual destination without temporarily replacing either hand. */
    @Nullable public static EquipmentSlot targetSlot(Mob mob, ItemStack incoming) {
        if (!handles(mob) || incoming.isEmpty() || RemovedDiamondEquipment.isRemoved(incoming)) return null;
        Kind incomingKind = kind(incoming);
        if (incomingKind == Kind.NONE) return null;
        EquipmentSlot slot;
        if (incoming.getItem() instanceof ArmorItem armor) slot = armor.getEquipmentSlot();
        else if (incomingKind == Kind.SHIELD) slot = EquipmentSlot.OFFHAND;
        else {
            slot = mob instanceof AbstractSkeleton skeleton ? SkeletonWeapons.pickupSlot(skeleton, incoming) : null;
            if (slot == null) slot = EquipmentSlot.MAINHAND;
            if (incomingKind == Kind.BOW && !(mob instanceof AbstractSkeleton)
                    && kind(mob.getItemBySlot(slot)) != Kind.BOW) return null;
            if (incomingKind == Kind.CROSSBOW && !(mob instanceof Piglin)
                    && kind(mob.getItemBySlot(slot)) != Kind.CROSSBOW) return null;
        }
        if (!mob.canUseSlot(slot)) return null;
        ItemStack current = mob.getItemBySlot(slot);
        return current.isEmpty() || kind(current) == Kind.NONE || comparable(incoming, current) ? slot : null;
    }

    /** Pursuit is deliberately strict: equal quality alone never attracts a monster. */
    public static boolean isUpgrade(Mob mob, ItemStack incoming) {
        EquipmentSlot slot = targetSlot(mob, incoming);
        if (slot == null || SourceEnchantmentCombat.disarmed(mob) || !mob.canHoldItem(incoming)
                || incoming.isDamageableItem() && incoming.getDamageValue() >= incoming.getMaxDamage()) return false;
        ItemStack current = mob.getItemBySlot(slot);
        if (locked(current)) return false;
        return current.isEmpty() || kind(current) == Kind.NONE
                || EquipmentDurability.quality(incoming).ordinal() > EquipmentDurability.quality(current).ordinal();
    }

    /** Null retains native damage/defense/wear comparisons for equal quality and unrelated items. */
    @Nullable public static Boolean replacementDecision(Mob mob, ItemStack incoming, ItemStack current) {
        if (!handles(mob)) return null;
        if (RemovedDiamondEquipment.isRemoved(incoming)) return false;
        if (!equipment(incoming)) return null;
        if (SourceEnchantmentCombat.disarmed(mob) || locked(current)) return false;
        if (current.isEmpty() || kind(current) == Kind.NONE) return true;
        // Pursuit only compares like roles; collision pickup must still retain native role changes,
        // including a dedicated melee skeleton replacing an unsuitable bow with a sword.
        if (!comparable(incoming, current)) return null;
        int comparison = Integer.compare(EquipmentDurability.quality(incoming).ordinal(), EquipmentDurability.quality(current).ordinal());
        return comparison == 0 ? null : comparison > 0;
    }

    private static boolean locked(ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
    }

    private static boolean comparable(ItemStack incoming, ItemStack current) {
        Kind category = kind(incoming);
        if (category == Kind.NONE || category != kind(current)) return false;
        return category != Kind.ARMOR || ((ArmorItem)incoming.getItem()).getEquipmentSlot()
                == ((ArmorItem)current.getItem()).getEquipmentSlot();
    }

    private static Kind kind(ItemStack stack) {
        if (stack.isEmpty()) return Kind.NONE;
        if (stack.getItem() instanceof ArmorItem) return Kind.ARMOR;
        if (MobShields.shield(stack)) return Kind.SHIELD;
        if (stack.getItem() instanceof BowItem) return Kind.BOW;
        if (stack.getItem() instanceof CrossbowItem) return Kind.CROSSBOW;
        if (MiteTools.definition(stack) != null || stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof DiggerItem || stack.getItem() instanceof TridentItem) return Kind.MELEE;
        return Kind.NONE;
    }
}
