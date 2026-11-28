package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import java.util.BitSet;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

/** Estimated ore blocks divided by the 16×16 cells of each layer of eligible terrain. @author NingMeng123 */
final class OreBlockDensity {
    private OreBlockDensity() {}

    record Shape(Dimension dimension, Kind kind, boolean deep, int minY, int maxY, OreHeightRange origins,
                 boolean customHeight, int size, double sharedFraction) {}

    static IntToDoubleFunction perAttempt(Shape shape, IntToDoubleFunction origins) {
        var blocks = new double[shape.maxY - shape.minY];
        var random = new Random(196L);
        if (shape.kind == Kind.ANCIENT_DEBRIS) {
            // Equal count of small and large passes, but each has its own size AND height distribution.
            addNative(shape, blocks, 2, true, y -> shape.customHeight ? origins.applyAsDouble(y) * .5 :
                    y >= 8 && y <= 119 ? .5 / 112 : 0);
            addNative(shape, blocks, 3, true, y -> shape.customHeight ? origins.applyAsDouble(y) * .5 :
                    Math.max(0, 9 - Math.abs(y - 16)) * .5 / 81);
        } else if (shape.kind == Kind.RED_DIAMOND || shape.kind == Kind.NETHER_ADAMANTIUM) {
            addNative(shape, blocks, shape.size, false, origins);
        } else {
            var rock = new Rock(shape, blocks);
            int samples = Math.clamp(8192 / (shape.origins.maximum() - shape.origins.minimum() + 1), 16, 256);
            // Keep even very small substrate-specific batches in the estimate.
            int shared = Math.clamp((int)Math.round(samples * shape.sharedFraction),
                    shape.sharedFraction > 0 ? 1 : 0, shape.sharedFraction < 1 ? samples - 1 : samples);
            for (int y = shape.origins.minimum(); y <= shape.origins.maximum(); y++) {
                double mass = origins.applyAsDouble(y);
                if (mass == 0) continue;
                if (shape.kind == Kind.EMERALD) {
                    blocks[y - shape.minY] += mass * hostFraction(shape, y);
                    continue;
                }
                int legacyY = switch (shape.dimension) {
                    case NETHER -> y;
                    case UNDERWORLD -> ModernUnderworldTerrain.toLegacyY(y);
                    case OVERWORLD -> y <= 64 ? Math.floorDiv(y + 64, 2) : y;
                };
                legacyY = Math.clamp(legacyY, R196OreRules.minHeight(shape.kind, shape.dimension),
                        R196OreRules.maxHeight(shape.kind, shape.dimension));
                for (int i = 0; i < samples; i++) {
                    boolean bothHosts = i < shared;
                    double weight = bothHosts ? mass * shape.sharedFraction / shared :
                            mass * (1D - shape.sharedFraction) / (samples - shared);
                    rock.reset(y, bothHosts, weight, random.nextInt());
                    R196OreRules.generateVein(shape.kind, shape.dimension, rock, 0, 0, 0, legacyY,
                            R196OreRules.depthScaled(shape.kind, shape.dimension), random);
                }
            }
        }
        return y -> y < shape.minY || y >= shape.maxY ? 0D : blocks[y - shape.minY] / 256D;
    }

    /** Native ITE veins use OreFeature ellipsoids; ancient debris uses ScatteredOreFeature offsets. */
    private static void addNative(Shape shape, double[] blocks, int size, boolean scattered, IntToDoubleFunction origins) {
        if (size <= 0) return;
        final int trials = 1024;
        var offsets = new double[64];
        var used = new BitSet();
        var random = new Random(196L);
        for (int trial = 0; trial < trials; trial++) {
            used.clear();
            if (scattered) {
                int count = random.nextInt(size + 1);
                for (int step = 0; step < count; step++) {
                    int x = scatteredOffset(random, step), y = scatteredOffset(random, step), z = scatteredOffset(random, step);
                    used.set(index(x, y, z));
                }
            } else {
                double angle = random.nextFloat() * Math.PI;
                double dx = Math.sin(angle) * size / 8D, dz = Math.cos(angle) * size / 8D;
                int y0 = random.nextInt(3) - 2, y1 = random.nextInt(3) - 2;
                for (int step = 0; step < size; step++) {
                    double t = (double)step / size;
                    double radius = ((Math.sin(Math.PI * t) + 1) * random.nextDouble() * size / 16D + 1) / 2D;
                    double cx = dx * (1 - 2 * t), cy = y0 + (y1 - y0) * t, cz = dz * (1 - 2 * t);
                    for (int x = (int)Math.floor(cx-radius); x <= cx+radius; x++)
                        for (int y = (int)Math.floor(cy-radius); y <= cy+radius; y++)
                            for (int z = (int)Math.floor(cz-radius); z <= cz+radius; z++) {
                                double rx = x+.5-cx, ry = y+.5-cy, rz = z+.5-cz;
                                if (rx*rx + ry*ry + rz*rz < radius*radius) used.set(index(x, y, z));
                            }
                }
            }
            for (int cell = used.nextSetBit(0); cell >= 0; cell = used.nextSetBit(cell + 1))
                offsets[(cell >> 6) & 63] += 1D / trials;
        }
        for (int origin = shape.origins.minimum(); origin <= shape.origins.maximum(); origin++) {
            double mass = origins.applyAsDouble(origin);
            for (int i = 0; i < offsets.length; i++) {
                int y = origin + i - 32;
                if (offsets[i] > 0 && y >= shape.minY && y < shape.maxY && (!shape.customHeight || shape.origins.contains(y)))
                    blocks[y - shape.minY] += mass * offsets[i] * hostFraction(shape, y);
            }
        }
    }

    private static int scatteredOffset(Random random, int step) {
        return Math.round((random.nextFloat() - random.nextFloat()) * Math.min(step, 7));
    }

    // R196's budget caps growth at 32 blocks. Native vanilla spheres of size <=64 also fit.
    private static int index(int x, int y, int z) { return (x + 32) << 12 | (y + 32) << 6 | (z + 32); }

    /** Vanilla deepslate uses the 0..8 vertical gradient; the MITE underworld converts stone below zero. */
    private static double deepFraction(Dimension dimension, int y) {
        return dimension == Dimension.UNDERWORLD ? (y < 0 ? 1D : 0D) : Math.clamp(1D - y / 8D, 0D, 1D);
    }

    static double hostFraction(Shape shape, int y) {
        if (!rockLayer(shape, y)) return 0;
        if (shape.dimension == Dimension.NETHER || shape.kind == Kind.GRAVEL) return 1;
        double deep = deepFraction(shape.dimension, y);
        return shape.deep ? deep : 1D - deep;
    }

    private static boolean rockLayer(Shape shape, int y) {
        return switch (shape.dimension) {
            case NETHER -> y > 0 && y < 127;
            case OVERWORLD -> y > shape.minY;
            case UNDERWORLD -> ModernUnderworldTerrain.toLegacyY(y) > 0 && ModernUnderworldTerrain.toLegacyY(y) < 255;
        };
    }

    private static final class Rock implements R196OreRules.VeinAccess {
        private final Shape shape;
        private final double[] blocks;
        private final int[] occupied = new int[1 << 18];
        private int sample, origin, salt;
        private boolean both;
        private double weight;

        Rock(Shape shape, double[] blocks) { this.shape = shape; this.blocks = blocks; }
        void reset(int origin, boolean both, double weight, int salt) {
            sample++;
            this.origin = origin;
            this.both = both;
            this.weight = weight;
            this.salt = salt;
        }
        private boolean target(int x, int dy, int z) {
            double share = hostFraction(shape, origin + dy);
            if (share == 0 || share == 1) return share == 1;
            // Assign the same transition-band cell to exactly one host throughout growth.
            int draw = (index(x, dy, z) * 0x9e3779b9 + salt) >>> 24;
            double deep = deepFraction(shape.dimension, origin + dy);
            return (draw < 256 * deep) == shape.deep;
        }
        @Override public boolean canReplace(int x, int dy, int z) {
            int y = origin + dy;
            if (y < shape.minY || y >= shape.maxY || shape.customHeight && !shape.origins.contains(y)) return false;
            if (!rockLayer(shape, y)) return false;
            return occupied[index(x, dy, z)] != sample && (both || target(x, dy, z));
        }
        @Override public boolean supported(int x, int dy, int z) { return true; }
        @Override public void replace(int x, int dy, int z) {
            occupied[index(x, dy, z)] = sample;
            if (target(x, dy, z)) blocks[origin + dy - shape.minY] += weight;
        }
    }
}
