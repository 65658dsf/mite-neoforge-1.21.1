package io.github.ningmeng123.mite.rules;

/** ItemArrow, akc, xv and ul arithmetic from the registered R196 release. @author NingMeng123 */
public final class R196RangedRules {
    public enum Material {
        FLINT("flint", 1F, 1F, 0, .3F), OBSIDIAN("obsidian", 2F, 2F, 0, .4F),
        COPPER("copper", 4F, 3F, 30, .6F), SILVER("silver", 4F, 3F, 30, .6F),
        RUSTED_IRON("rusted_iron", 4F, 2F, 0, .5F), GOLD("gold", 4F, 2F, 50, .5F),
        IRON("iron", 8F, 4F, 30, .7F), ANCIENT_METAL("ancient_metal", 16F, 4F, 40, .8F),
        MITHRIL("mithril", 64F, 5F, 100, .8F), ADAMANTIUM("adamantium", 256F, 6F, 40, .9F);
        public final String id;
        public final float durability, materialDamage, recovery;
        public final int enchantability;
        Material(String id, float durability, float damage, int enchantability, float recovery) {
            this.id = id; this.durability = durability; this.materialDamage = damage;
            this.enchantability = enchantability; this.recovery = recovery;
        }
        public float arrowDamage() { return .5F + materialDamage * .5F; }
        public int rodDurability() { return (int)(2F * durability + (this == FLINT ? 1F : 0F)); }
        public boolean hasCraftableArrow() { return this != RUSTED_IRON; }
        public boolean hasRod() { return this != RUSTED_IRON; }
        public static Material byId(String id) {
            for (Material material : values()) if (material.id.equals(id)) return material;
            return null;
        }
    }
    private R196RangedRules() {}

    /** Use the calendar equipment stages without replacing a mob's stronger native ammunition. */
    public static Material monsterArrow(long day, Material original) {
        Material unlocked = switch (BloodMoonEquipmentRules.stageForDay(day)) {
            case 1 -> Material.RUSTED_IRON;
            case 3 -> Material.COPPER;
            case 5 -> Material.IRON;
            case 6 -> Material.ANCIENT_METAL;
            case 7 -> Material.MITHRIL;
            case 8, 9 -> Material.ADAMANTIUM;
            default -> original;
        };
        return BloodMoonEquipmentRules.materialTier(unlocked.id) > BloodMoonEquipmentRules.materialTier(original.id)
                ? unlocked : original;
    }

    /** ul.checkForBite: adjusted dawn starts at 6000, dawn/dusk minima at 5500/17500. */
    public static int biteChanceDenominator(long unadjustedTime, boolean blueMoon, boolean raining,
            int fishingFortune, boolean worm) {
        int adjusted = (int)Math.floorMod(unadjustedTime + 6000L, 24000L);
        float factor = (float)Math.min(Math.abs(adjusted - 5500), Math.abs(adjusted - 17500)) / 600F;
        int chance = blueMoon ? 600 : Math.clamp((int)(600F * factor), 600, 2400);
        if (raining) chance /= 2;
        for (int i = 0; i < Math.max(fishingFortune, 0); i++) chance = chance * 9 / 10;
        if (worm) chance = (int)((float)chance * .5F);
        return Math.max(chance, 1);
    }

    /** Fishing Fortune adds each source's probability gain over the same weather/bait baseline. */
    public static double biteChance(long time, boolean blueMoon, boolean raining, int normal, int affix, boolean worm) {
        double probability = 1D / biteChanceDenominator(time, blueMoon, raining, normal, worm);
        if (affix > 0) probability += 1D / biteChanceDenominator(time, blueMoon, raining, affix, worm)
                - 1D / biteChanceDenominator(time, blueMoon, raining, 0, worm);
        return Math.clamp(probability, 0D, 1D);
    }

    public static float impactDamage(Material material, float speed, float enchantedDamage,
            boolean silverUndead, boolean skeletonTarget, boolean skeletonShooter, boolean dispenser,
            boolean enchantedLauncher, boolean critical, float criticalRoll) {
        if (!(speed >= 1F)) return 0F;
        float damage = enchantedDamage;
        if (silverUndead && material == Material.SILVER) damage *= 1.25F;
        if (skeletonTarget) damage *= .25F;
        float minimum = material.arrowDamage() * 2F + 2F;
        if (skeletonShooter) damage = !enchantedLauncher ? minimum : Math.max(damage, minimum);
        else if (dispenser) damage = Math.max(damage, minimum);
        return critical ? damage * (1.5F + criticalRoll * .5F) : damage;
    }
}
