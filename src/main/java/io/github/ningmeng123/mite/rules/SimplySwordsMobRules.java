package io.github.ningmeng123.mite.rules;

import java.util.LinkedHashMap;
import java.util.List;

/** Spawn-only optional weapons follow the shared ordinary-day and blood-date material curves. @author NingMeng123 */
public final class SimplySwordsMobRules {
    public static final float EQUIP_CHANCE = .15F;
    public record Material(String name, double weight) {}
    private static final List<String> TIER_MATERIALS = List.of("rusted_iron", "rusted_iron", "gold", "copper", "silver",
            "iron", "ancient_metal", "mithril", "adamantium", "vibranium");

    private SimplySwordsMobRules() {}

    public static List<Material> available(long highestDay) {
        return available(highestDay, false);
    }

    /** Normalized material probabilities; leather and chainmail stages both use scavenged rusted-iron blades. */
    public static List<Material> available(long highestDay, boolean bloodDate) {
        int stage = bloodDate ? BloodMoonEquipmentRules.stage(BloodMoonEquipmentRules.bloodMoons(highestDay)) : -1;
        if (stage < 0 && !BloodMoonEquipmentRules.ordinaryActive(highestDay)) return List.of(new Material("rusted_iron", 1D));
        boolean blood = bloodDate && stage >= 0;
        double progress = blood ? stage : BloodMoonEquipmentRules.ordinaryProgress(highestDay);
        var probabilities = new LinkedHashMap<String, Double>();
        for (int tier = 0; tier < TIER_MATERIALS.size(); tier++) {
            double chance = blood ? BloodMoonEquipmentRules.tierChance(stage, tier, true)
                    : BloodMoonEquipmentRules.ordinaryTierChance(progress, tier);
            if (chance > 0D) probabilities.merge(TIER_MATERIALS.get(tier), chance, Double::sum);
        }
        return probabilities.entrySet().stream().map(entry -> new Material(entry.getKey(), entry.getValue())).toList();
    }

    /** This fallback is used only when neither a weapon nor recognized metal armor establishes a material. */
    public static String selectMaterial(long highestDay, double draw) {
        return selectMaterial(highestDay, false, draw);
    }

    public static String selectMaterial(long highestDay, boolean bloodDate, double draw) {
        if (!(draw >= 0D && draw < 1D)) throw new IllegalArgumentException("Material draw must be in [0,1)");
        if (bloodDate) {
            int stage = BloodMoonEquipmentRules.stage(BloodMoonEquipmentRules.bloodMoons(highestDay));
            if (stage >= 0) return TIER_MATERIALS.get(BloodMoonEquipmentRules.selectTier(stage, true, draw));
        }
        if (!BloodMoonEquipmentRules.ordinaryActive(highestDay)) return "rusted_iron";
        return TIER_MATERIALS.get(BloodMoonEquipmentRules.selectOrdinaryTier(BloodMoonEquipmentRules.ordinaryProgress(highestDay), draw));
    }
}
