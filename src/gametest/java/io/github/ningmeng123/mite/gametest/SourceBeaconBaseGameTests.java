package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteMetalBlocks;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native beacon base evaluation reads placed source blocks and the loaded additive tag. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SourceBeaconBaseGameTests {
    private SourceBeaconBaseGameTests() {}
    private static int levels(GameTestHelper h, BlockPos site) throws Exception {
        Method update = BeaconBlockEntity.class.getDeclaredMethod("updateBase", Level.class, int.class, int.class, int.class);
        update.setAccessible(true);
        return (int)update.invoke(null, h.getLevel(), site.getX(), site.getY(), site.getZ());
    }
    private static void layer(GameTestHelper h, BlockPos site, int tier, Block block) {
        for (int x = -tier; x <= tier; x++) for (int z = -tier; z <= tier; z++)
            h.getLevel().setBlockAndUpdate(site.offset(x, -tier, z), block.defaultBlockState());
    }
    @GameTest(template = "fireball_r196") public static void allFourSourceMetalsBuildNativeFourTierBeaconBases(GameTestHelper h) throws Exception {
        BlockPos site = h.absolutePos(new BlockPos(7, 7, 7)); h.getLevel().setBlockAndUpdate(site, Blocks.BEACON.defaultBlockState());
        h.assertTrue(h.getLevel().getBlockEntity(site) instanceof BeaconBlockEntity, "Real native beacon block entity exists");
        for (Block metal : new Block[]{Blocks.COPPER_BLOCK, MiteMetalBlocks.STORAGE.get("silver").get(),
                MiteMetalBlocks.STORAGE.get("mithril").get(), MiteMetalBlocks.STORAGE.get("adamantium").get()}) {
            for (int tier = 1; tier <= 4; tier++) layer(h, site, tier, Blocks.AIR);
            for (int tier = 1; tier <= 4; tier++) {
                layer(h, site, tier, metal);
                h.assertTrue(levels(h, site) == tier, "Native beacon recognizes source metal at every completed tier: " + metal + " tier" + tier);
            }
        }
        h.succeed();
    }
    @GameTest(template = "fireball_r196") public static void mixedBasesPreserveModernMetalsButRejectHolesAndAncientMetal(GameTestHelper h) throws Exception {
        BlockPos site = h.absolutePos(new BlockPos(7, 7, 7));
        layer(h, site, 1, Blocks.IRON_BLOCK); layer(h, site, 2, Blocks.COPPER_BLOCK);
        layer(h, site, 3, MiteMetalBlocks.STORAGE.get("mithril").get()); layer(h, site, 4, Blocks.NETHERITE_BLOCK);
        h.assertTrue(levels(h, site) == 4, "Source and modern base materials mix through native validation");
        h.getLevel().setBlockAndUpdate(site.offset(2, -2, 2), Blocks.AIR.defaultBlockState());
        h.assertTrue(levels(h, site) == 1, "Missing block truncates native pyramid at first incomplete tier");
        layer(h, site, 1, MiteMetalBlocks.STORAGE.get("ancient_metal").get());
        h.assertTrue(levels(h, site) == 0, "Ancient metal is outside R196 beacon whitelist");
        for (Block modern : new Block[]{Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.EMERALD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.NETHERITE_BLOCK})
            h.assertTrue(modern.defaultBlockState().is(BlockTags.BEACON_BASE_BLOCKS), "Additive source tag preserves native base membership");
        h.assertTrue(!Blocks.CUT_COPPER.defaultBlockState().is(BlockTags.BEACON_BASE_BLOCKS)
                && !Blocks.OXIDIZED_COPPER.defaultBlockState().is(BlockTags.BEACON_BASE_BLOCKS), "Modern copper derivatives are not silently added to source whitelist");
        h.succeed();
    }
}
