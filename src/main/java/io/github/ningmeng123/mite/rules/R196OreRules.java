package io.github.ningmeng123.mite.rules;

import java.util.List;
import java.util.Random;

/** Source mineral sampling and connected growth, kept independent of Minecraft. @author NingMeng123 */
public final class R196OreRules {
    public enum Dimension { OVERWORLD, UNDERWORLD, NETHER }
    public enum Kind {
        DIRT, GRAVEL, COAL, COPPER, SILVER, GOLD, IRON, MITHRIL, ADAMANTIUM,
        INFESTED, REDSTONE, DIAMOND, LAPIS, EMERALD, QUARTZ, NETHER_GOLD, NETHER_INFESTED
    }
    public static final List<Kind> OVERWORLD = List.of(Kind.DIRT, Kind.GRAVEL, Kind.COAL,
            Kind.COPPER, Kind.SILVER, Kind.GOLD, Kind.IRON, Kind.MITHRIL, Kind.INFESTED,
            Kind.REDSTONE, Kind.DIAMOND, Kind.LAPIS);
    public static final List<Kind> UNDERWORLD = List.of(Kind.GRAVEL, Kind.COPPER, Kind.SILVER,
            Kind.GOLD, Kind.IRON, Kind.MITHRIL, Kind.ADAMANTIUM, Kind.REDSTONE,
            Kind.DIAMOND, Kind.LAPIS, Kind.INFESTED);
    public static final List<Kind> NETHER = List.of(Kind.QUARTZ, Kind.NETHER_GOLD, Kind.NETHER_INFESTED);

    /** Each call replaces at most one eligible block; unsuccessful steps consume no random values. */
    public interface VeinAccess {
        boolean canReplace(int x, int y, int z);
        boolean supported(int x, int y, int z);
        void replace(int x, int y, int z);
    }
    private R196OreRules() {}

    public static int candidateCount(Kind kind, Dimension dimension) {
        if (dimension == Dimension.NETHER) return switch (kind) {
            case QUARTZ -> 16;
            case NETHER_GOLD -> 2;
            case NETHER_INFESTED -> 2; // Lower bound; the source adds nextInt(7).
            default -> 0;
        };
        if (kind == Kind.EMERALD) return dimension == Dimension.OVERWORLD ? 3 : 0;
        if (!(dimension == Dimension.OVERWORLD ? OVERWORLD : UNDERWORLD).contains(kind)) return 0;
        int base = switch (kind) {
            case DIRT -> 200;
            case GRAVEL -> dimension == Dimension.UNDERWORLD ? 300 : 200;
            case COAL -> 50;
            case COPPER -> 40;
            case SILVER, MITHRIL, REDSTONE -> 10;
            case GOLD -> 20;
            case IRON -> 60;
            case INFESTED -> dimension == Dimension.UNDERWORLD ? 50 : 5;
            case ADAMANTIUM, DIAMOND, LAPIS -> 5;
            default -> 0;
        };
        if (dimension == Dimension.UNDERWORLD && kind != Kind.GRAVEL) {
            base *= 8;
            if (kind == Kind.ADAMANTIUM) base *= 2;
        }
        return base;
    }
    public static int candidateCount(Kind kind, Dimension dimension, Random random) {
        if (dimension == Dimension.NETHER && kind == Kind.NETHER_INFESTED) return 2 + random.nextInt(7);
        if (dimension == Dimension.OVERWORLD && kind == Kind.EMERALD) return 3 + random.nextInt(6);
        return candidateCount(kind, dimension);
    }
    public static int baseSize(Kind kind) {
        return switch (kind) {
            case DIRT, GRAVEL -> 32;
            case COAL -> 16;
            case COPPER, SILVER, IRON -> 6;
            case GOLD -> 4;
            case REDSTONE -> 5;
            case QUARTZ -> 13;
            case NETHER_GOLD, NETHER_INFESTED -> 8;
            case EMERALD -> 1;
            default -> 3;
        };
    }
    public static int minHeight(Kind kind, Dimension dimension) {
        if (dimension == Dimension.UNDERWORLD) return 0;
        if (dimension == Dimension.NETHER) return 10;
        return switch (kind) {
            case DIRT -> 32;
            case GRAVEL -> 24;
            case COAL -> 16;
            case LAPIS -> 8;
            case EMERALD -> 4;
            default -> 0;
        };
    }
    public static int maxHeight(Kind kind, Dimension dimension) {
        if (dimension == Dimension.UNDERWORLD) return 255;
        if (dimension == Dimension.NETHER) return 117;
        return switch (kind) {
            case DIRT, GRAVEL, COPPER -> 128;
            case COAL, SILVER -> 96;
            case GOLD -> 48;
            case IRON -> 64;
            case MITHRIL, DIAMOND -> 32;
            case ADAMANTIUM, INFESTED, REDSTONE -> 24;
            case LAPIS -> 40;
            case EMERALD -> 31;
            default -> throw new IllegalArgumentException("No overworld source height: " + kind);
        };
    }
    public static boolean depthScaled(Kind kind, Dimension dimension) {
        if (dimension == Dimension.NETHER) return false;
        return switch (kind) {
            case COPPER, SILVER, GOLD, IRON, MITHRIL, ADAMANTIUM -> true;
            case INFESTED -> dimension == Dimension.OVERWORLD;
            default -> false;
        };
    }
    /** Source BlockOre subclasses get the low-depth bias; redstone has its own non-BlockOre class. */
    private static boolean ore(Kind kind) {
        return kind != Kind.DIRT && kind != Kind.GRAVEL && kind != Kind.INFESTED
                && kind != Kind.NETHER_INFESTED && kind != Kind.REDSTONE;
    }
    public static int randomHeight(Kind kind, Dimension dimension, Random random) {
        if (dimension == Dimension.NETHER) return 10 + random.nextInt(108);
        if (dimension == Dimension.UNDERWORLD) {
            if (kind == Kind.ADAMANTIUM) return random.nextInt(136);
            if (ore(kind) && random.nextFloat() < 0.75F) return random.nextInt(136);
            return random.nextInt(256);
        }
        if (kind == Kind.EMERALD) return 4 + random.nextInt(28);
        float relative;
        if (kind == Kind.LAPIS) {
            relative = (random.nextFloat() + random.nextFloat()) / 2.0F;
        } else if (kind == Kind.COPPER && random.nextInt(2) == 0) {
            relative = random.nextFloat() * 0.6F + 0.4F;
        } else {
            boolean higher = kind == Kind.DIRT || kind == Kind.GRAVEL || kind == Kind.COAL;
            do { relative = random.nextFloat(); }
            while (higher ? !(relative > random.nextFloat()) : !(relative < random.nextFloat()));
        }
        int min = minHeight(kind, dimension);
        return min + (int)(relative * (float)(maxHeight(kind, dimension) - min + 1));
    }
    public static int veinSize(Kind kind, Dimension dimension, int legacyY, Random random) {
        return veinSize(kind, dimension, legacyY, random, depthScaled(kind, dimension));
    }
    public static int veinSize(Kind kind, Dimension dimension, int legacyY, Random random, boolean depthScale) {
        return veinSize(kind, dimension, legacyY, random, depthScale, baseSize(kind));
    }
    public static int veinSize(Kind kind, Dimension dimension, int legacyY, Random random, boolean depthScale, int base) {
        float scale = 1.0F;
        while (random.nextInt(2) == 0) {
            scale = (float)((double)scale * ((double)random.nextFloat() * 0.6 + (double)0.7F));
        }
        scale = Math.min(scale, 4.0F);
        int range = maxHeight(kind, dimension) - minHeight(kind, dimension);
        if (depthScale && range > 16) {
            float relative = ((float)legacyY - (float)minHeight(kind, dimension)) / (float)range;
            scale *= 1.0F - relative + 0.5F;
        }
        if ((float)base * scale <= 3.0F && random.nextInt(2) == 0) {
            if (random.nextInt(2) != 0) return 0;
            scale *= 2.0F;
        }
        int size = (int)((float)base * scale);
        if (size < 1) return 0;
        if (size == 1) return random.nextInt(3) == 0 ? 3 : 0;
        if (size == 2) return random.nextInt(3) == 0 ? 0 : 3;
        return Math.min(size, 32);
    }
    public static int generateVein(Kind kind, Dimension dimension, VeinAccess access,
            int x, int y, int z, int legacyY, boolean depthScale, Random random) {
        if (!access.canReplace(x, y, z)) return 0;
        int size = veinSize(kind, dimension, legacyY, random, depthScale);
        return growVein(access, size, x, y, z, dimension == Dimension.UNDERWORLD && kind == Kind.GRAVEL, random);
    }
    public static int growVein(VeinAccess access, int budget, int x, int y, int z,
            boolean mustBeSupported, Random random) {
        if (budget < 1 || !access.canReplace(x, y, z) || mustBeSupported && !access.supported(x, y, z)) return 0;
        access.replace(x, y, z);
        int grown = 1;
        for (int attempt = 0; attempt < 16; attempt++) {
            int axis = random.nextInt(3);
            int sign = random.nextInt(2) == 0 ? -1 : 1;
            grown += growVein(access, budget - grown, x + (axis == 0 ? sign : 0),
                    y + (axis == 1 ? sign : 0), z + (axis == 2 ? sign : 0), mustBeSupported, random);
            if (grown == budget) break;
        }
        return grown;
    }
    /** Preserve the modern sea-level band while extending source deep deposits into negative Y. */
    public static int modernY(Dimension dimension, int legacyY) {
        return switch (dimension) {
            case OVERWORLD -> legacyY <= 64 ? -64 + legacyY * 2 : legacyY;
            case UNDERWORLD -> -64 - Math.floorDiv(-legacyY * 384, 256);
            case NETHER -> legacyY;
        };
    }
}
