package io.github.ningmeng123.mite.rules;

/** Modern armor points; source components identify slots, not flat damage reduction. @author NingMeng123 */
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
        if (chainmail) return switch (material) {
            case "rusted_iron" -> new Stats(1, 3, 2, 1, .28125F);
            case "gold" -> new Stats(1, 3, 2, 1, .375F);
            case "copper", "silver" -> new Stats(1, 4, 3, 1, .5625F);
            case "iron" -> new Stats(2, 4, 4, 1, .75F);
            case "ancient_metal" -> new Stats(2, 5, 4, 2, .9375F);
            case "mithril" -> new Stats(2, 6, 5, 2, 1.5F);
            case "adamantium" -> new Stats(3, 6, 5, 3, 1.875F);
            default -> throw new IllegalArgumentException("Unknown chain armor material: " + material);
        };
        return switch (material) {
            case "leather" -> new Stats(1, 3, 2, 1, .25F);
            case "rusted_iron" -> new Stats(1, 4, 3, 1, .375F);
            case "gold" -> new Stats(2, 4, 3, 1, .5F);
            case "copper", "silver" -> new Stats(2, 5, 3, 2, .75F);
            case "iron" -> new Stats(2, 5, 5, 2, 1F);
            case "ancient_metal" -> new Stats(2, 6, 5, 3, 1.25F);
            case "diamond", "mithril" -> new Stats(3, 7, 5, 3, 2F);
            case "netherite", "adamantium" -> new Stats(3, 8, 6, 3, 2.5F);
            case "vibranium" -> new Stats(4, 8, 6, 4, 3F, 3F, 3F, 3F, .12F, .2F, .18F, .1F);
            case "turtle", "turtle_scute" -> new Stats(2, 0, 0, 0, .5F);
            default -> throw new IllegalArgumentException("Unknown armor material: " + material);
        };
    }
}
