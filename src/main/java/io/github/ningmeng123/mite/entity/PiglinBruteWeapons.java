package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import net.minecraft.world.item.ItemStack;

/** Material progression for piglin brute melee weapon pickups. @author NingMeng123 */
public final class PiglinBruteWeapons {
    private PiglinBruteWeapons() {}

    public static boolean canEquip(ItemStack stack) {
        return grade(stack) >= BloodMoonEquipmentRules.materialGrade(PiglinArmor.MINIMUM_TIER);
    }

    static int grade(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        var tool = MiteTools.definition(stack);
        String material = tool == null ? SimplySwordsCompat.material(stack) : switch (tool.family()) {
            case AXE, BATTLE_AXE, HATCHET, SWORD, DAGGER, KNIFE, SCYTHE, CLUB, CUDGEL, WAR_HAMMER -> tool.material();
            default -> null;
        };
        if (material == null) return -1;
        int tier = BloodMoonEquipmentRules.materialTier(material);
        return tier < 0 ? -1 : BloodMoonEquipmentRules.materialGrade(tier);
    }
}
