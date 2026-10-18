package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.furnace.EquipmentRecycling;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;

/** Limits natural monster equipment drops while preserving picked-up equipment. @author NingMeng123 */
public final class MobEquipmentDrops {
    public static final int NATURAL_DROP_DIVISOR = 3;
    public static final int ENCHANTED_DROP_DIVISOR = 2;

    private MobEquipmentDrops() {}

    /** Use the original slot chance: values above one mark guaranteed, including picked-up, gear. */
    public static boolean keep(Mob mob, float originalDropChance) {
        return keep(mob, originalDropChance, ItemStack.EMPTY);
    }

    /** Mithril and higher natural gear never drops; enchantments further reduce other natural gear. */
    public static boolean keep(Mob mob, float originalDropChance, ItemStack stack) {
        if (!canDrop(mob, originalDropChance, stack)) return false;
        if (mob.getType().getCategory() != MobCategory.MONSTER || originalDropChance > 1F) return true;
        return mob.getRandom().nextInt(NATURAL_DROP_DIVISOR) == 0
                && (!stack.isEnchanted() || mob.getRandom().nextInt(ENCHANTED_DROP_DIVISOR) == 0);
    }

    /** Pure material restriction shared by death, equipment replacement and disarming. */
    public static boolean canDrop(Mob mob, float originalDropChance, ItemStack stack) {
        return mob.getType().getCategory() != MobCategory.MONSTER || originalDropChance > 1F
                || !blocksNaturalDrop(stack);
    }

    /** Resolve real equipment definitions, so material resources and similarly named items are unaffected. */
    public static boolean blocksNaturalDrop(ItemStack stack) {
        String material = EquipmentRecycling.material(stack);
        return "mithril".equals(material) || "adamantium".equals(material) || "vibranium".equals(material);
    }
}
