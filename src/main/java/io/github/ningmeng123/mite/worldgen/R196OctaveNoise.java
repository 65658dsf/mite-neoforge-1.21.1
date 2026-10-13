package io.github.ningmeng123.mite.worldgen;

import java.util.Random;

/** R196 ajt/ajs lattice sampling, including the old one-row and repeated-Y optimizations. @author NingMeng123 */
final class R196OctaveNoise {
    private final Improved[] octaves;

    R196OctaveNoise(Random random, int count) {
        octaves = new Improved[count];
        for (int i = 0; i < count; i++) octaves[i] = new Improved(random);
    }

    double[] sample(int x, int y, int z, int width, int height, int depth,
                    double xScale, double yScale, double zScale) {
        double[] values = new double[width * height * depth];
        double frequency = 1;
        for (Improved octave : octaves) {
            double sx = x * frequency * xScale;
            double sz = z * frequency * zScale;
            long ix = (long) Math.floor(sx);
            long iz = (long) Math.floor(sz);
            sx = sx - ix + ix % 16777216L;
            sz = sz - iz + iz % 16777216L;
            octave.add(values, sx, y * frequency * yScale, sz, width, height, depth,
                    xScale * frequency, yScale * frequency, zScale * frequency, 1 / frequency);
            frequency /= 2;
        }
        return values;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6 - 15) + 10);
    }

    private static double lerp(double weight, double a, double b) { return a + weight * (b - a); }

    private static double gradient(int hash, double x, double y, double z) {
        int h = hash & 15;
        double a = h < 8 ? x : y;
        double b = h < 4 ? y : h != 12 && h != 14 ? z : x;
        return ((h & 1) == 0 ? a : -a) + ((h & 2) == 0 ? b : -b);
    }

    private static double gradient2(int hash, double x, double z) {
        int h = hash & 15;
        double a = (1 - ((h & 8) >> 3)) * x;
        double b = h < 4 ? 0 : h != 12 && h != 14 ? z : x;
        return ((h & 1) == 0 ? a : -a) + ((h & 2) == 0 ? b : -b);
    }

    private static final class Improved {
        private final int[] permutations = new int[512];
        private final double xo;
        private final double yo;
        private final double zo;

        private Improved(Random random) {
            xo = random.nextDouble() * 256;
            yo = random.nextDouble() * 256;
            zo = random.nextDouble() * 256;
            for (int i = 0; i < 256; i++) permutations[i] = i;
            for (int i = 0; i < 256; i++) {
                int swap = random.nextInt(256 - i) + i;
                int value = permutations[i];
                permutations[i] = permutations[swap];
                permutations[swap] = value;
                permutations[i + 256] = permutations[i];
            }
        }

        private void add(double[] values, double ox, double oy, double oz, int width, int height, int depth,
                         double sx, double sy, double sz, double amplitude) {
            int index = 0;
            int lastY = -1;
            double lowNear = 0, highNear = 0, lowFar = 0, highFar = 0;
            for (int x = 0; x < width; x++) {
                double px = ox + x * sx + xo;
                int ix = (int) Math.floor(px);
                px -= ix;
                int hx = ix & 255;
                double fx = fade(px);
                for (int z = 0; z < depth; z++) {
                    double pz = oz + z * sz + zo;
                    int iz = (int) Math.floor(pz);
                    pz -= iz;
                    int hz = iz & 255;
                    double fz = fade(pz);
                    if (height == 1) {
                        int a = permutations[permutations[hx]] + hz;
                        int b = permutations[permutations[hx + 1]] + hz;
                        double near = lerp(fx, gradient2(permutations[a], px, pz),
                                gradient(permutations[b], px - 1, 0, pz));
                        double far = lerp(fx, gradient(permutations[a + 1], px, 0, pz - 1),
                                gradient(permutations[b + 1], px - 1, 0, pz - 1));
                        values[index++] += lerp(fz, near, far) * amplitude;
                        continue;
                    }
                    for (int y = 0; y < height; y++) {
                        double py = oy + y * sy + yo;
                        int iy = (int) Math.floor(py);
                        py -= iy;
                        int hy = iy & 255;
                        if (y == 0 || hy != lastY) {
                            lastY = hy;
                            int a = permutations[hx] + hy;
                            int b = permutations[hx + 1] + hy;
                            int aa = permutations[a] + hz, ab = permutations[a + 1] + hz;
                            int ba = permutations[b] + hz, bb = permutations[b + 1] + hz;
                            lowNear = lerp(fx, gradient(permutations[aa], px, py, pz),
                                    gradient(permutations[ba], px - 1, py, pz));
                            highNear = lerp(fx, gradient(permutations[ab], px, py - 1, pz),
                                    gradient(permutations[bb], px - 1, py - 1, pz));
                            lowFar = lerp(fx, gradient(permutations[aa + 1], px, py, pz - 1),
                                    gradient(permutations[ba + 1], px - 1, py, pz - 1));
                            highFar = lerp(fx, gradient(permutations[ab + 1], px, py - 1, pz - 1),
                                    gradient(permutations[bb + 1], px - 1, py - 1, pz - 1));
                        }
                        values[index++] += lerp(fz, lerp(fade(py), lowNear, highNear),
                                lerp(fade(py), lowFar, highFar)) * amplitude;
                    }
                }
            }
        }
    }
}
