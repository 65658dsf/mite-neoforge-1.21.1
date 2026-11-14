package io.github.ningmeng123.mite.rules;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Bounded optional-weapon stats share the source material ladder and one material budget. @author NingMeng123 */
public final class SimplySwordsBalanceRules {
    public static final double MIN_CONFIGURED_DAMAGE_ADJUSTMENT = -4D, MAX_CONFIGURED_DAMAGE_ADJUSTMENT = 8D;
    public static final double TWO_HANDED_ATTACK_SPEED_FACTOR = .8D;
    public record Profile(String family, double damageFactor, double meleeReach, double standaloneMeleeReach,
            double attacksPerSecond, double minimumAttacksPerSecond,
            double maximumAttacksPerSecond, double twoHandedMovementPenalty, double singleHandedMovementPenalty,
            double upstreamAttacksPerSecond, float upstreamDamageModifier, boolean defaultTwoHanded) {}
    private static final List<Profile> PROFILES = List.of(
            new Profile("longsword", 1D, 2.5D, 2.5D, 1.3D, 1.17D, 1.65D, .15D, 0D, 1.6D, 0F, false),
            new Profile("twinblade", .7D, 3.5D, 2.75D, 1.05D / TWO_HANDED_ATTACK_SPEED_FACTOR, 1.15D, 1.4D, .15D, 0D, 2D, 0F, true),
            new Profile("rapier", .75D, 2.1D, 2D, 1.85D, 1.65D, 2.05D, .15D, 0D, 2.2D, -1F, false),
            new Profile("katana", .85D, 2.15D, 2.1D, 1.7D / TWO_HANDED_ATTACK_SPEED_FACTOR, 2D, 2.25D, .15D, 0D, 2D, 0F, true),
            new Profile("sai", .6D, 2D, 2D, 2D, 1.8D, 2.2D, .15D, 0D, 2.5D, -3F, false),
            new Profile("spear", .65D, 3.75D, 3D, .9D, .75D, 1.05D, .15D, .05D, 1.3D, 0F, false),
            new Profile("glaive", .65D, 3.5D, 2.75D, 1D, .85D, 1.15D, .15D, .05D, 1.4D, 0F, true),
            new Profile("warglaive", .8D, 2.1D, 2D, 1.65D, 1.55D, 1.8D, .15D, .05D, 1.8D, 0F, false),
            new Profile("cutlass", .8D, 2.1D, 2D, 1.75D, 1.6D, 1.95D, .15D, 0D, 2D, 0F, false),
            new Profile("claymore", 1.15D, 3.25D, 3.25D, .95D, .8D, 1.1D, .2D, 0D, 1.2D, 2F, true),
            new Profile("greataxe", 1.2D, 3.25D, 3.25D, .8D, .65D, .95D, .22D, 0D, .9D, 3F, true),
            new Profile("greathammer", 1.25D, 3.25D, 3.25D, .7D, .6D, .85D, .25D, 0D, .8D, 4F, true),
            new Profile("chakram", .55D, 2.8D, 2.8D, .9D, .75D, 1.05D, .15D, 0D, 1D, -1F, false),
            new Profile("scythe", .7D, 3.5D, 2.75D, 1D, .85D, 1.15D, .15D, .05D, 1.3D, 1F, true),
            new Profile("halberd", .55D, 4D, 3.25D, .65D / TWO_HANDED_ATTACK_SPEED_FACTOR, .7D, .95D, .2D, .05D, 1.2D, 3F, true));
    private static final Map<String, Profile> BY_FAMILY = PROFILES.stream().collect(Collectors.toUnmodifiableMap(Profile::family, Function.identity()));
    private SimplySwordsBalanceRules() {}
    public static List<Profile> profiles() { return PROFILES; }
    public static Profile profile(String family) { return BY_FAMILY.getOrDefault(family, PROFILES.getFirst()); }
    public static double meleeReach(String family) { return profile(family).meleeReach(); }
    public static double meleeReach(String family, boolean betterCombat) {
        Profile profile = profile(family);
        return betterCombat ? profile.meleeReach() : profile.standaloneMeleeReach();
    }
    public static String category(String category, String fallback) {
        if (category == null || category.isBlank()) return fallback;
        String normalized = switch (category) {
            case "sword", "swords" -> "longsword";
            case "dagger" -> "sai";
            case "trident" -> "spear";
            case "axe", "heavy_axe", "double_axe" -> "greataxe";
            case "mace", "hammer" -> "greathammer";
            default -> category;
        };
        return BY_FAMILY.containsKey(normalized) ? normalized : fallback;
    }
    public static double damageAdjustment(double adjustment) {
        return Double.isFinite(adjustment) ? Math.clamp(adjustment, MIN_CONFIGURED_DAMAGE_ADJUSTMENT, MAX_CONFIGURED_DAMAGE_ADJUSTMENT) : 0D;
    }
    public static double damageBonus(String material, String family, double materialDamageTotal, double configuredAdjustment) {
        return damageBonus(material, family, materialDamageTotal, configuredAdjustment, false);
    }
    public static double damageBonus(String material, String family, double materialDamageTotal, double configuredAdjustment, boolean unique) {
        double raw = Double.isFinite(materialDamageTotal) ? Math.clamp(materialDamageTotal, 1D, 64D) : 1D;
        // Native SS iron/gold otherwise sit below added copper despite belonging to a later tier.
        // Unique weapons retain their independent source basis rather than inheriting their vanilla tier.
        if (!unique) raw = switch (material) {
            case "gold" -> 7D;
            case "iron", "diamond" -> 9D;
            case "netherite", "runic" -> 10D;
            default -> raw;
        };
        double total = Math.max(.5D, raw + damageAdjustment(configuredAdjustment)) * profile(family).damageFactor();
        return total - 1D;
    }
    /** Configuration is relative to the upstream family speed; limits apply before haste and equipment affixes. */
    public static double attackRate(String family, double configuredRate) {
        Profile profile = profile(family);
        double rate = Double.isFinite(configuredRate) && configuredRate > 0D ? configuredRate : profile.upstreamAttacksPerSecond();
        return Math.clamp(profile.attacksPerSecond() * rate / profile.upstreamAttacksPerSecond(),
                profile.minimumAttacksPerSecond(), profile.maximumAttacksPerSecond());
    }
    public static double baseAttackSpeedBonus(String family, double configuredRate, boolean twoHanded) {
        // Handedness applies once in the live handling modifier; base stats must not cancel it in advance.
        return attackRate(family, configuredRate) - 4D;
    }
    public static double movementPenalty(String family, boolean twoHanded) {
        Profile profile = profile(family);
        return twoHanded ? profile.twoHandedMovementPenalty() : profile.singleHandedMovementPenalty();
    }
}
