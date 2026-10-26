package io.github.ningmeng123.mite.rules;

import java.util.List;

/** One persistent sword per player, with material-specific damage and per-attack cooldowns. @author NingMeng123 */
public final class PhantomBladeRules {
    public static final double TARGET_RANGE = 10D;
    public record Profile(String material, double damage, int intervalTicks, int rank, int rechargeTicks, int bladeCount) {
        public int fullMask() { return (1 << bladeCount)-1; }
    }
    private static final List<Profile> PROFILES = List.of(
            new Profile("rusted_iron", .4D, 70, 0, 70, 1), new Profile("gold", .45D, 65, 1, 65, 1),
            new Profile("copper", .5D, 60, 2, 60, 1), new Profile("silver", .5D, 60, 2, 60, 1),
            new Profile("iron", 1D, 50, 3, 50, 1), new Profile("ancient_metal", 1.25D, 45, 4, 45, 1),
            new Profile("mithril", 1.5D, 40, 5, 40, 1), new Profile("adamantium", 2D, 30, 6, 30, 1),
            new Profile("vibranium", 3D, 20, 7, 20, 1));
    private PhantomBladeRules() {}
    public static List<Profile> profiles() { return PROFILES; }
    public static int maximumBladeCount() { return PROFILES.stream().mapToInt(Profile::bladeCount).max().orElseThrow(); }
    public static Profile profile(String material) {
        return PROFILES.stream().filter(profile -> profile.material().equals(material)).findFirst().orElse(null);
    }
}
