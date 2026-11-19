package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

/** Birth and pickup armor floor for the two native piglin types. @author NingMeng123 */
public final class PiglinArmor {
    static final int MINIMUM_TIER = 2;
    static final String MINIMUM_PREFIX = BloodMoonEquipmentRules.PREFIXES.get(MINIMUM_TIER);

    private PiglinArmor() {}

    public static boolean handles(Mob mob) {
        return mob.getType() == EntityType.PIGLIN || mob.getType() == EntityType.PIGLIN_BRUTE;
    }

    public static boolean rejects(Mob mob, ItemStack stack) {
        if (!handles(mob) || !(stack.getItem() instanceof ArmorItem armor)) return false;
        if (armor.getEquipmentSlot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return true;
        var material = armor.getMaterial();
        if (material.equals(ArmorMaterials.LEATHER) || material.equals(ArmorMaterials.CHAIN)
                || material.equals(ArmorMaterials.TURTLE)) return true;
        var definition = MiteArmorContent.definition(armor);
        if (definition != null) return definition.material().equals("rusted_iron") || definition.material().equals("leather");
        return armor.getDefense() < ArmorMaterials.GOLD.value().getDefense(armor.getType());
    }
}
