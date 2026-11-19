package io.github.ningmeng123.mite.rules;

import net.minecraft.resources.ResourceLocation;

/** MITE balance for Simply Swords' built-in weapon implicits. @author NingMeng123 */
public final class SimplySwordsImplicitRules {
    public static final ResourceLocation GREATHAMMER_SUNDER = id("greathammer_sunder");
    public static final ResourceLocation SCYTHE_EXECUTE = id("scythe_execute");
    public static final int GREATHAMMER_SUNDER_CAP = 10;

    private static final Range CHAKRAM = new Range(5, 10);
    private static final Range LONGSWORD = new Range(5, 8);
    private static final Range TWINBLADE = new Range(5, 15);
    private static final Range RAPIER = new Range(5, 12);
    private static final Range SPEAR = new Range(5, 15);
    private static final Range BLEED = new Range(10, 20);
    private static final Range GREATHAMMER = new Range(1, 1);
    private static final Range SCYTHE = new Range(3, 3);

    private SimplySwordsImplicitRules() {}

    public static Range range(ResourceLocation implicitId) {
        if (!implicitId.getNamespace().equals("simplyswords")) return null;
        return switch (implicitId.getPath()) {
            case "chakram_haste" -> CHAKRAM;
            case "longsword_deflect" -> LONGSWORD;
            case "twinblade_haste" -> TWINBLADE;
            case "armor_pierce" -> RAPIER;
            case "spear_armor_pierce" -> SPEAR;
            case "glaive_bleed", "greataxe_bleed", "halberd_bleed" -> BLEED;
            case "greathammer_sunder" -> GREATHAMMER;
            case "scythe_execute" -> SCYTHE;
            default -> null;
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("simplyswords", path);
    }

    public record Range(int min, int max) {}

    public interface ComponentAccess {
        ResourceLocation implicitId();
    }
}
