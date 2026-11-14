package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;

/** Modern damage, cooldown and player weight tradeoffs; archived material definitions stay intact. @author NingMeng123 */
public final class NativeWeaponBalanceRules {
    private NativeWeaponBalanceRules() {}

    public static boolean starter(String material) {
        return material.equals("wood") || material.equals("stone") || material.equals("flint") || material.equals("obsidian");
    }

    public static float damageBonus(ToolFamily family, String material, float originalBonus) {
        return originalBonus;
    }

    public static double attacksPerSecond(ToolFamily family, String material) {
        return switch (family) {
            case SWORD -> 1.5D;
            case DAGGER -> 1.8D;
            case KNIFE -> 2D;
            case BATTLE_AXE -> .85D;
            case WAR_HAMMER -> .75D;
            case CLUB -> 1.2D;
            case CUDGEL -> 1.6D;
            case SCYTHE -> .8D;
            case AXE -> .95D;
            case HATCHET -> 1.25D;
            case PICKAXE, SHOVEL, MATTOCK -> 1D;
            case HOE -> 1.1D;
            case SHEARS -> 1.3D;
        };
    }

    /** Better Combat same-height melee range before target adjustments and player reach settings. */
    public static double meleeReach(ToolFamily family) {
        return switch (family) {
            case SWORD -> 2.25D;
            case DAGGER, WAR_HAMMER, CLUB, HATCHET, SHEARS -> 2D;
            case KNIFE, CUDGEL -> 1.75D;
            case SCYTHE -> 3.25D;
            case BATTLE_AXE, AXE, PICKAXE, SHOVEL, HOE, MATTOCK -> 2.25D;
        };
    }

    /** Selects the melee range for the active attack system. */
    public static double meleeReach(ToolFamily family, boolean betterCombat) {
        if (betterCombat) return meleeReach(family);
        return switch (family) {
            case BATTLE_AXE -> 2D;
            case SCYTHE -> 2.5D;
            default -> meleeReach(family);
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
