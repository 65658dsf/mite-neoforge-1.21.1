package io.github.ningmeng123.mite.rules;

import java.util.List;

/** Spawn-only optional weapon choices; rare metals follow the existing blood-moon calendar. @author NingMeng123 */
public final class SimplySwordsMobRules {
    public static final float EQUIP_CHANCE = .15F;
    public record Material(String name, int weight, long bloodMoons) {}
    public static final List<Material> MATERIALS = List.of(
            new Material("rusted_iron", 100, 0), new Material("gold", 50, 4),
            new Material("copper", 100, 5), new Material("silver", 100, 5),
            new Material("ancient_metal", 25, 6), new Material("mithril", 5, 7),
            new Material("adamantium", 1, 8));

    private SimplySwordsMobRules() {}

    public static List<Material> available(long highestDay) {
        long moons = BloodMoonEquipmentRules.bloodMoons(highestDay);
        return MATERIALS.stream().filter(material -> moons >= material.bloodMoons()).toList();
    }

    /** This fallback is used only when neither a weapon nor recognized metal armor establishes a material. */
    public static String selectMaterial(long highestDay, double draw) {
        if (!(draw >= 0D && draw < 1D)) throw new IllegalArgumentException("Material draw must be in [0,1)");
        var materials = available(highestDay);
        double remaining = draw * materials.stream().mapToInt(Material::weight).sum();
        for (Material material : materials) {
            remaining -= material.weight();
            if (remaining < 0D) return material.name();
        }
        return materials.getLast().name();
    }
}
