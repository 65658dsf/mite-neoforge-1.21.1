package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;

/** Modern damage, cooldown and player weight tradeoffs; archived material definitions stay intact. @author NingMeng123 */
public final class NativeWeaponBalanceRules {
    private NativeWeaponBalanceRules() {}

    public static boolean starter(String material) {
        return material.equals("wood") || material.equals("stone") || material.equals("flint") || material.equals("obsidian");
    }

    public static float damageBonus(ToolFamily family, String material, float originalBonus) {
        if (starter(material)) return originalBonus;
        float factor = switch (family) {
            case BATTLE_AXE -> .9F;
            case SCYTHE -> .7F;
            case SHEARS -> .6F;
            case AXE, HATCHET, PICKAXE, SHOVEL, MATTOCK, HOE -> .65F;
            default -> .8F;
        };
        // The family budget is relative to the ordinary metal curve; the material budget runs once.
        float familyFactor = factor / R196WeaponRules.DEFAULT_MELEE_DAMAGE_FACTOR;
        return R196WeaponRules.meleeDamageBonus(material, (originalBonus + 1F) * familyFactor - 1F);
    }

    public static double attacksPerSecond(ToolFamily family, String material) {
        if (starter(material)) return switch (family) {
            case AXE, HATCHET, BATTLE_AXE, WAR_HAMMER -> 1D;
            case PICKAXE, SHOVEL, MATTOCK -> 1.2D;
            case HOE -> 2D;
            default -> 1.6D;
        };
        return switch (family) {
            case SWORD -> 1.5D;
            case DAGGER, KNIFE -> 2D;
            case BATTLE_AXE -> .85D;
            case WAR_HAMMER -> .75D;
            case CLUB -> 1.1D;
            case CUDGEL -> 1.2D;
            case SCYTHE -> 1D;
            case AXE -> .95D;
            case HATCHET -> 1.25D;
            case PICKAXE, SHOVEL, MATTOCK -> 1.05D;
            case HOE -> 1.2D;
            case SHEARS -> 1.3D;
        };
    }

    public static double movementPenalty(ToolFamily family, String material) {
        if (starter(material)) return 0D;
        return switch (family) {
            case BATTLE_AXE -> .15D;
            case WAR_HAMMER -> .18D;
            case SCYTHE, CLUB -> .08D;
            case CUDGEL -> .04D;
            default -> 0D;
        };
    }
}
