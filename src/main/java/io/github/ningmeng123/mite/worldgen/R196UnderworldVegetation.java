package io.github.ningmeng123.mite.worldgen;

import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Source ChunkPostField/BiomeGenUnderworld mycelium patches and default brown mushrooms. @author NingMeng123 */
public final class R196UnderworldVegetation {
    private R196UnderworldVegetation() {}

    public static void decorate(WorldGenLevel level, int chunkX, int chunkZ, long hashedSeed) {
        List<R196MyceliumPostField.Post> posts = R196MyceliumPostField.nearbyPosts(chunkX, chunkZ, hashedSeed);
        Random random = new Random();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
            for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                for (R196MyceliumPostField.Post post : posts) {
                    long dx = post.x() - x, dz = post.z() - z;
                    if (dx * dx + dz * dz > 580) continue;
                    random.setSeed(post.seed());
                    random.nextInt();
                    int legacyY = random.nextInt(random.nextBoolean() ? 16 : 72) + 24 + 120;
                    int searchHeight = random.nextInt(5) + 1;
                    int startY = ModernUnderworldTerrain.fromLegacyY(legacyY);
                    int endY = ModernUnderworldTerrain.fromLegacyY(legacyY + searchHeight);
                    for (int y = startY; y < endY; y++) {
                        position.set(x, y, z);
                        if (!level.getBlockState(position.above()).isAir()) continue;
                        BlockState target = level.getBlockState(position), floor = level.getBlockState(position.below());
                        if (!target.isSolidRender(level, position) || !floor.isSolidRender(level, position.below())
                                || target.getBlock() instanceof FallingBlock) break;
                        level.setBlock(position, Blocks.MYCELIUM.defaultBlockState(), 2);
                        random.setSeed(post.seed() + R196UnderworldTerrain.chunkHash(x, z));
                        random.nextInt();
                        if (random.nextInt(16) == 0 && !largeBrownMushroom(level, random, position.above())) {
                            level.setBlock(position.above(), Blocks.BROWN_MUSHROOM.defaultBlockState(), 2);
                        }
                        break;
                    }
                }
            }
        }
    }

    private static boolean largeBrownMushroom(WorldGenLevel level, Random random, BlockPos base) {
        random.nextInt(2); // afl default constructor fixes the result to brown after consuming the type draw.
        int height = random.nextInt(3) + 4;
        if (base.getY() < level.getMinBuildHeight() + 1 || base.getY() + height + 1 >= level.getMaxBuildHeight()) return false;
        for (int y = 0; y <= height + 1; y++) {
            int radius = y <= 3 ? 0 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
                    BlockState current = level.getBlockState(base.offset(dx, y, dz));
                    if (!current.isAir() && !current.is(net.minecraft.tags.BlockTags.LEAVES)) return false;
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                BlockState cap = Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState()
                        .setValue(HugeMushroomBlock.UP, true).setValue(HugeMushroomBlock.DOWN, false)
                        .setValue(HugeMushroomBlock.WEST, dx == -3 || dx == -2 && Math.abs(dz) == 3)
                        .setValue(HugeMushroomBlock.EAST, dx == 3 || dx == 2 && Math.abs(dz) == 3)
                        .setValue(HugeMushroomBlock.NORTH, dz == -3 || dz == -2 && Math.abs(dx) == 3)
                        .setValue(HugeMushroomBlock.SOUTH, dz == 3 || dz == 2 && Math.abs(dx) == 3);
                level.setBlock(base.offset(dx, height, dz), cap, 2);
            }
        }
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState().setValue(HugeMushroomBlock.UP, false)
                .setValue(HugeMushroomBlock.DOWN, false);
        for (int y = 0; y < height; y++) level.setBlock(base.above(y), stem, 2);
        return true;
    }
}
