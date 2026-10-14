package io.github.ningmeng123.mite.rules;

import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/** R196 durability arithmetic without Minecraft dependencies. @author NingMeng123 */
public final class EquipmentDurabilityRules {
    public enum Quality {
        WRETCHED(0.5F), POOR(0.75F), AVERAGE(1F), FINE(1.5F), EXCELLENT(2F),
        SUPERB(2.5F), MASTERWORK(3F), LEGENDARY(3.5F);
        private final float multiplier;
        Quality(float multiplier) { this.multiplier = multiplier; }
        public float multiplier() { return multiplier; }
        public static Quality fromOrdinal(int ordinal) {
            return values()[Math.clamp(ordinal, 0, values().length - 1)];
        }
    }

    public enum ToolFamily {
        PICKAXE(3), AXE(3), BATTLE_AXE(4), HATCHET(1), SHOVEL(1), MATTOCK(4),
        HOE(2), SWORD(2), DAGGER(1), KNIFE(1), SCYTHE(2), SHEARS(2),
        CLUB(2), CUDGEL(1), WAR_HAMMER(5);
        private final int components;
        ToolFamily(int components) { this.components = components; }
        public int components() { return components; }
    }

    public record ToolProfile(ToolFamily family, float materialDurability, Quality maxQuality) {
        public ToolProfile {
            Objects.requireNonNull(family);
            Objects.requireNonNull(maxQuality);
            requirePositiveFinite(materialDurability);
        }
        public ToolProfile(ToolFamily family, float materialDurability) {
            this(family, materialDurability, Quality.LEGENDARY);
        }
    }

    public record ArmorProfile(int components, float materialDurability, int materialProtection,
                               boolean chainmail, Quality maxQuality) {
        public ArmorProfile {
            if (components < 1 || components > 8 || materialProtection < 0)
                throw new IllegalArgumentException("Invalid armor components/protection");
            requirePositiveFinite(materialDurability);
            Objects.requireNonNull(maxQuality);
        }
    }

    private EquipmentDurabilityRules() {}

    public static int baseToolDurability(ToolProfile profile) {
        return Math.max((int)(4F * (float)profile.family().components()
                * profile.materialDurability() * 100F), 1);
    }

    public static int baseArmorDurability(ArmorProfile profile) {
        float durability = (float)profile.components() * profile.materialDurability();
        if (!profile.chainmail()) durability *= 2F;
        return Math.max((int)durability, 1);
    }

    public static int maxDurability(int base, Quality quality) {
        if (base < 1) throw new IllegalArgumentException("Durability must be positive");
        return Math.max(Math.round((float)base * Objects.requireNonNull(quality).multiplier()), 1);
    }

    /** specialPlant means grass/wheat for scythes, or cloth/plants/vines for knives. */
    public static int blockWear(float hardness, ToolProfile profile, boolean sandstone, boolean specialPlant) {
        if (!(hardness > 0F) || !Float.isFinite(hardness)) return 0;
        float rate = switch (profile.family()) {
            case AXE -> sandstone ? 1.875F : 1F;
            case HATCHET -> (sandstone ? 1.875F : 1F) * 4F / 3F;
            case BATTLE_AXE -> (sandstone ? 1.875F : 1F) * 1.25F;
            case SHOVEL -> 0.5F;
            case MATTOCK -> 0.5F * 0.8F;
            case HOE, SWORD, DAGGER -> 2F;
            case KNIFE -> specialPlant ? 2F / 4F : 2F / 2F;
            case SCYTHE -> specialPlant ? 0.5F : 2F;
            case CLUB, CUDGEL -> 0.25F;
            case WAR_HAMMER -> 1F * 2F / 3F;
            default -> 1F;
        };
        float decay = 100F * rate;
        return Math.max(Math.max((int)(hardness * decay), (int)(decay / 20F)), 1);
    }

    public static int attackWear(ToolProfile profile, boolean vampiric, boolean sharpness) {
        float rate = switch (profile.family()) {
            case HATCHET -> 1F * 4F / 3F;
            case BATTLE_AXE -> 1F * 0.75F;
            case SWORD, DAGGER, KNIFE -> 0.5F;
            case HOE, SHEARS -> 2F;
            case SCYTHE -> vampiric ? 1F : sharpness ? 2F : 4F;
            case CLUB, CUDGEL -> 0.25F;
            case WAR_HAMMER -> 1F * 2F / 3F;
            default -> 1F;
        };
        return Math.max((int)(100F * rate), 1);
    }

    public static float unbreakingNegationChance(int level) {
        return ((float)Math.clamp(level, 0, 5) / 5F) * 0.75F;
    }

    /** One independent float draw per requested point; no draws at level zero. */
    public static int afterUnbreaking(int requested, int level, Supplier<Float> nextFloat) {
        if (requested <= 0) return 0;
        float chance = unbreakingNegationChance(level);
        if (chance <= 0F) return requested;
        int remaining = requested;
        for (int i = 0; i < requested; i++) {
            if (nextFloat.get() < chance) remaining--;
        }
        return remaining;
    }

    public static int preventDestruction(int requested, int damage, int maximum) {
        return Math.min(Math.max(requested, 0), Math.max(maximum - Math.max(damage, 0) - 1, 0));
    }

    /** wh's player factor. Non-player R196 armor always used 0.5, independently of wear. */
    public static float armorProtectionFactor(int damage, int maximum, boolean player) {
        if (!player) return 0.5F;
        if (maximum < 1 || maximum > 1 && damage >= maximum - 1) return 0F;
        return Math.clamp(2F - (float)damage / (float)maximum * 2F, 0F, 1F);
    }

    public static float armorProtection(ArmorProfile profile, int damage, int maximum) {
        return armorProtection(profile, damage, maximum, 0);
    }

    public static float armorProtection(ArmorProfile profile, int damage, int maximum, int protectionLevel) {
        int material = profile.materialProtection() - (profile.chainmail() ? 2 : 0);
        float protection = (float)(profile.components() * Math.max(material, 0)) / 24F;
        if (protectionLevel > 0) protection += protection * ((float)protectionLevel / 4F) * 0.5F;
        return protection * armorProtectionFactor(damage, maximum, true);
    }

    /** aaf combines remaining durability with no modern five-percent bonus. */
    public static int combinedRepairDamage(int firstDamage, int secondDamage, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Invalid repair maximum");
        long remaining = (long)maximum - Math.clamp(firstDamage, 0, maximum)
                + maximum - Math.clamp(secondDamage, 0, maximum);
        return (int)Math.max((long)maximum - remaining, 0L);
    }

    /** wh.getTotalArmorProtection calls ls.tryFitToNearestInteger before damage is truncated. */
    public static float roundedArmorProtection(float total) {
        float floor = total < 0F ? (float)((int)total - 1) : (float)(int)total;
        if (total - floor < 0.5F) return total - floor <= 0.0001F ? floor : total;
        return floor + 1F - total <= 0.0001F ? floor + 1F : total;
    }

    /** Empty slots consume a random portion too; the four-slot order is boots, legs, chest, head. */
    public static int[] distributeArmorWear(float amount, boolean[] occupied, IntUnaryOperator nextInt) {
        if (occupied.length != 4) throw new IllegalArgumentException("Four armor slots required");
        int[] result = new int[4];
        int count = 0;
        for (boolean present : occupied) if (present) count++;
        if (count == 0 || !Float.isFinite(amount)) return result;
        int remaining = Math.max((int)amount, 1);
        while (remaining > 0) {
            int slot = nextInt.applyAsInt(4);
            if (slot < 0 || slot >= 4) throw new IllegalArgumentException("RNG slot outside bound");
            int portion = occupied[slot] && count == 1 ? remaining : nextInt.applyAsInt(remaining) + 1;
            if (portion < 1 || portion > remaining) throw new IllegalArgumentException("RNG portion outside bound");
            if (occupied[slot]) result[slot] += portion;
            remaining -= portion;
        }
        return result;
    }

    private static void requirePositiveFinite(float value) {
        if (!(value > 0F) || !Float.isFinite(value)) throw new IllegalArgumentException("Invalid material durability");
    }
}
