package io.github.ningmeng123.mite.rules;

import java.util.LinkedHashMap;
import java.util.List;

/** Spawn-only optional weapons share the same highest-date material stage on ordinary and blood dates. @author NingMeng123 */
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
        int stage = BloodMoonEquipmentRules.stageForDay(highestDay);
        if (stage < 0) return List.of(new Material("rusted_iron", 1D));
        var probabilities = new LinkedHashMap<String, Double>();
        for (int tier = 0; tier < TIER_MATERIALS.size(); tier++) {
            double chance = bloodDate ? BloodMoonEquipmentRules.tierChance(stage, tier, true)
                    : BloodMoonEquipmentRules.ordinaryTierChance(stage, tier);
            if (chance > 0D) probabilities.merge(TIER_MATERIALS.get(tier), chance, Double::sum);
        }
        return probabilities.entrySet().stream().map(entry -> new Material(entry.getKey(), entry.getValue())).toList();
    }

    /** This fallback is used only when neither a weapon nor recognized metal armor establishes a material. */
    public static String selectMaterial(long highestDay, double draw) {
        return selectMaterial(highestDay, false, draw);
    }

    public static String selectMaterial(long highestDay, boolean bloodDate, double draw) {
        return selectMaterial(highestDay, bloodDate, draw, MonsterArmorRules.DEFAULTS);
    }

    public static String selectMaterial(long highestDay, boolean bloodDate, double draw, MonsterArmorRules.Settings settings) {
        if (!(draw >= 0D && draw < 1D)) throw new IllegalArgumentException("Material draw must be in [0,1)");
        int stage = settings.stageForDay(highestDay);
        if (stage < 0) return "rusted_iron";
        int tier = bloodDate ? BloodMoonEquipmentRules.selectTier(stage, true, draw)
                : BloodMoonEquipmentRules.selectOrdinaryTier(stage, draw);
        return TIER_MATERIALS.get(tier);
    }
}
