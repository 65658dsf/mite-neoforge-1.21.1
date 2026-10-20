package io.github.ningmeng123.mite.progression;

/** Equipment progression arithmetic; one experience point is 1000 stored units. @author NingMeng123 */
public final class GearProgressionRules {
    public static final long SCALE = 1000L;
    public static final long TILL_EXPERIENCE_MILLIS = SCALE;
    private GearProgressionRules() {}

    public static int maximumLevel(String material) {
        if (material == null) return 0;
        return switch (material) {
            case "copper", "silver", "rusted_iron", "gold", "leather", "wood", "stone", "flint", "obsidian",
                    "iron", "ancient_metal", "mithril", "diamond", "adamantium", "netherite", "runic", "special",
                    "vibranium", "unique" -> 25;
            default -> 0;
        };
    }

    public static long nextLevelExperienceMillis(int currentLevel) {
        return nextLevelExperienceMillis(null, currentLevel, false, -1);
    }

    /** armorSlot: -1 for tools, otherwise helmet/chest/legs/boots = 0/1/2/3. */
    public static long nextLevelExperienceMillis(String material, int currentLevel, boolean weapon, int armorSlot) {
        if (currentLevel < 0 || currentLevel > 27) throw new IllegalArgumentException("Invalid gear level");
        String type = material == null ? "" : material;
        if (armorSlot >= 0) {
            int base = switch (type) {
                case "copper", "silver" -> 16;
                case "gold" -> 18;
                case "iron", "ancient_metal" -> 20;
                case "mithril" -> 24;
                case "adamantium" -> 28;
                case "vibranium" -> 32;
                default -> 150;
            };
            int factor = switch (armorSlot) {
                case 0 -> 2; case 1 -> 4; case 2 -> 3; case 3 -> 1;
                default -> throw new IllegalArgumentException("Invalid armor slot");
            };
            return (base + (long)currentLevel * (base / 2)) * factor * SCALE;
        }
        int base = switch (type) {
            case "copper", "silver" -> 100;
            case "gold" -> 125;
            case "mithril", "adamantium", "vibranium" -> 200;
            default -> 150;
        };
        int increase = switch (type) {
            case "copper", "silver" -> 50;
            case "gold" -> 60;
            case "mithril" -> 100;
            case "adamantium" -> 120;
            case "vibranium" -> 150;
            default -> 75;
        };
        return (base + (long)currentLevel * increase * (weapon ? 2 : 1)) * SCALE;
    }

    public static long killExperienceMillis(double maximumHealth) {
        return 0;
    }

    public static long miningExperienceMillis(float hardness, int harvestTier) {
        if (!(hardness > 0) || !Float.isFinite(hardness)) return 0;
        return Math.max(0, harvestTier) * SCALE;
    }

    public static long damageExperienceMillis(float damage) {
        return Float.isFinite(damage) ? (int)Math.clamp(damage, 1F, 4F) * 2L * SCALE : 0;
    }

    public static long shieldExperienceMillis(float blockedDamage) {
        return 0;
    }

    public static long saturatedAdd(long first, long second) {
        if (first < 0 || second < 0) throw new IllegalArgumentException("Negative gear experience");
        return second > Long.MAX_VALUE - first ? Long.MAX_VALUE : first + second;
    }
}
