package io.github.ningmeng123.mite.rules;

/** Modern armor calibrated against R196 full-set protection at 20 incoming damage. @author NingMeng123 */
public final class ArmorBalanceRules {
    public record Stats(int helmet, int chestplate, int leggings, int boots, float helmetToughness,
            float chestplateToughness, float leggingsToughness, float bootsToughness,
            float helmetKnockbackResistance, float chestplateKnockbackResistance,
            float leggingsKnockbackResistance, float bootsKnockbackResistance) {
        public Stats(int helmet, int chestplate, int leggings, int boots, float toughness) {
            this(helmet, chestplate, leggings, boots, toughness, toughness, toughness, toughness);
        }

        public Stats(int helmet, int chestplate, int leggings, int boots, float helmetToughness,
                float chestplateToughness, float leggingsToughness, float bootsToughness) {
            this(helmet, chestplate, leggings, boots, helmetToughness, chestplateToughness,
                    leggingsToughness, bootsToughness, 0F, 0F, 0F, 0F);
        }

        public float knockbackResistance(int components) {
            return switch (components) {
                case 5 -> helmetKnockbackResistance;
                case 8 -> chestplateKnockbackResistance;
                case 7 -> leggingsKnockbackResistance;
                case 4 -> bootsKnockbackResistance;
                default -> throw new IllegalArgumentException("Not a humanoid armor slot: " + components);
            };
        }

        /** Shared material baseline; equipped item attributes use the actual slot below. */
        public float toughness() {
            return (helmetToughness + chestplateToughness + leggingsToughness + bootsToughness) / 4F;
        }

        public float toughness(int components) {
            return switch (components) {
                case 5 -> helmetToughness;
                case 8 -> chestplateToughness;
                case 7 -> leggingsToughness;
                case 4 -> bootsToughness;
                default -> throw new IllegalArgumentException("Not a humanoid armor slot: " + components);
            };
        }

        public int protection(int components) {
            return switch (components) {
                case 5 -> helmet;
                case 8 -> chestplate;
                case 7 -> leggings;
                case 4 -> boots;
                default -> throw new IllegalArgumentException("Not a humanoid armor slot: " + components);
            };
        }
    }

    private ArmorBalanceRules() {}

    /** Extra mitigation attributable to toughness alone; legacy barding keeps its separate flat protection. */
    public static float bardingToughnessBuffer(float incoming, float armor, float toughness) {
        if (!(incoming > 0F) || !(armor > 0F) || !(toughness > 0F)) return 0F;
        float baseline = Math.clamp(armor - incoming / 2F, armor * .2F, 20F);
        float strengthened = Math.clamp(armor - incoming / (2F + toughness / 4F), armor * .2F, 20F);
        return incoming * Math.max(0F, strengthened - baseline) / 25F;
    }

    public static Stats stats(String material, boolean chainmail) {
        // Armor is apportioned by the source slot weights 5/8/7/4, rounding by largest remainder.
        // Equal source protection gets equal modern stats. Toughness is a per-piece value.
        if (chainmail) return switch (material) {
            case "rusted_iron", "gold" -> new Stats(3, 5, 4, 2, .25F);
            case "copper", "silver" -> new Stats(3, 5, 4, 3, .25F);
            case "iron", "ancient_metal" -> new Stats(3, 5, 5, 3, .375F);
            case "mithril" -> new Stats(3, 6, 5, 3, .5F);
            case "adamantium" -> new Stats(4, 6, 5, 3, .5F);
            default -> throw new IllegalArgumentException("Unknown chain armor material: " + material);
        };
        return switch (material) {
            case "leather" -> new Stats(3, 4, 3, 2, .125F);
            case "rusted_iron", "gold" -> new Stats(3, 5, 5, 3, .375F);
            case "copper", "silver" -> new Stats(3, 6, 5, 3, .5F);
            case "iron", "ancient_metal" -> new Stats(4, 6, 5, 3, .5F);
            case "diamond", "mithril" -> new Stats(4, 6, 6, 3, .625F);
            case "netherite", "adamantium" -> new Stats(4, 7, 6, 3, .75F);
            case "vibranium" -> new Stats(5, 7, 6, 4, 4F, 4F, 4F, 4F, .12F, .2F, .18F, .1F);
            case "turtle", "turtle_scute" -> new Stats(2, 0, 0, 0, .5F);
            default -> throw new IllegalArgumentException("Unknown armor material: " + material);
        };
    }
}
