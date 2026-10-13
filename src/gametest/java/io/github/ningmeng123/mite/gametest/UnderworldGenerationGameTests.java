package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196UnderworldGenerator;
import io.github.ningmeng123.mite.worldgen.R196UnderworldSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real server dimensions and generated chunk boundaries. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UnderworldGenerationGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void underworldLoadsWithModernNegativeHeightAndNoSky(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        helper.assertTrue(level.getMinBuildHeight() == -64 && level.getHeight() == 384, "地底世界应为 Y-64..319");
        helper.assertTrue(!level.dimensionType().hasSkyLight(), "地底世界不能有天空光");
        helper.assertTrue(!level.dimensionType().ultraWarm(), "地底世界必须允许源版水层存在");
        helper.assertTrue(level.getChunkSource().getGenerator() instanceof R196UnderworldGenerator, "未加载 R196 生成器");
        helper.assertTrue(level.dimensionType().timeOfDay(0) == .5f, "地底天角应固定 .5");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void generatedUnderworldHasMantleFloorAndBedrockRoof(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        int baseX = 3200, baseZ = -3200;
        level.getChunk(baseX >> 4, baseZ >> 4);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            helper.assertTrue(level.getBlockState(new BlockPos(baseX + x, -64, baseZ + z)).is(MiteWorldBlocks.MANTLE.get()), "地底底部缺少地幔");
            helper.assertTrue(level.getBlockState(new BlockPos(baseX + x, 319, baseZ + z)).is(Blocks.BEDROCK), "地底顶部缺少基岩");
            helper.assertTrue(!level.getBlockState(new BlockPos(baseX + x, 64, baseZ + z)).isAir(), "源版下层不应生成额外洞穴");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void overworldAndNetherKeepTheirModernNoiseGenerators(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD), nether = server.getLevel(Level.NETHER);
        helper.assertTrue(overworld != null && nether != null, "原版维度未加载");
        // GameTest's selected overworld may intentionally be flat. Check its modern height separately.
        helper.assertTrue(overworld.getMinBuildHeight() == -64 && overworld.getHeight() == 384, "主世界高度被旧地底规则覆盖");
        helper.assertTrue(!(overworld.getChunkSource().getGenerator() instanceof R196UnderworldGenerator), "主世界生成器被地底替换");
        helper.assertTrue(nether.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator, "下界生成器被替换");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void underworldBatsCanSpawnAboveTheLegacyOverworldHeightLimit(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        BlockPos position = new BlockPos(3208, 180, -3208);
        var old = level.getBlockState(position);
        var oldFloor = level.getBlockState(position.below());
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);
        try {
            helper.assertTrue(!Bat.checkBatSpawnRules(EntityType.BAT, level, MobSpawnType.NATURAL, position,
                    RandomSource.create(1)), "此案例必须位于原版蝙蝠高度上限之外");
            boolean accepted = false;
            for (int i = 0; i < 64; i++) {
                if (R196UnderworldSpawns.bat(EntityType.BAT, level, MobSpawnType.NATURAL, position,
                        RandomSource.create(i * 1000000003L))) accepted = true;
            }
            helper.assertTrue(accepted, "R196 地底蝙蝠不能被主世界 Y63/现代海平面限制排除");
        } finally {
            level.setBlock(position, old, 2);
            level.setBlock(position.below(), oldFloor, 2);
        }
        helper.succeed();
    }

    private static ServerLevel underworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "mite:underworld 未在独立服务器注册");
        return level;
    }
}
