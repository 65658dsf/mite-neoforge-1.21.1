package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.campfire.CampfireController;
import io.github.ningmeng123.mite.campfire.CampfireFuelAccess;
import io.github.ningmeng123.mite.campfire.CampfireRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native placement, lighting, loot and smoke remain consistent with the campfire fuel rules. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CampfireBlockGameTests {
    private CampfireBlockGameTests() {}

    @GameTest(template = "empty")
    public static void ordinaryAndSoulCampfireItemsPlaceColdWithoutFuel(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            fixture.set(pos.below(), Blocks.STONE.defaultBlockState());
            for (Block block : new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}) {
                fixture.set(pos, Blocks.AIR.defaultBlockState());
                ItemStack stack = new ItemStack(block);
                var context = new DirectionalPlaceContext(helper.getLevel(), pos, Direction.NORTH, stack, Direction.UP);
                helper.assertTrue(((BlockItem)block.asItem()).place(context).consumesAction(), "The actual block item successfully places the campfire");
                BlockState placed = helper.getLevel().getBlockState(pos);
                helper.assertTrue(placed.is(block) && !placed.getValue(CampfireBlock.LIT), "Both native campfire block items place an extinguished block");
                CampfireBlockEntity campfire = (CampfireBlockEntity)helper.getLevel().getBlockEntity(pos);
                helper.assertValueEqual(((CampfireFuelAccess)campfire).mite$fuelPoints(), 0, "Freshly placed campfires contain no free fuel");
                helper.assertValueEqual(placed.getLightEmission(helper.getLevel(), pos), 0, "A freshly placed cold campfire emits no block light");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("deprecation")
    public static void bothNativeCampfiresCacheTenLightOnlyInTheirLitState(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (Block block : new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}) {
            for (boolean lit : new boolean[]{false, true}) {
                BlockState state = block.defaultBlockState().setValue(CampfireBlock.LIT, lit);
                int expected = lit ? 10 : 0;
                helper.assertValueEqual(state.getLightEmission(), expected, "The native cached light value is updated before block-state construction");
                helper.assertValueEqual(state.getLightEmission(helper.getLevel(), pos), expected, "NeoForge light queries use the identical ten-or-zero value");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void normalSilkAndBothExplosionLootPathsCannotRecoverAWholeCampfire(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            ItemStack silk = new ItemStack(Items.IRON_AXE);
            silk.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
            for (Block block : new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}) {
                BlockState state = block.defaultBlockState().setValue(CampfireBlock.LIT, false);
                Item expected = block == Blocks.CAMPFIRE ? Items.CHARCOAL : Items.SOUL_SOIL;
                int count = block == Blocks.CAMPFIRE ? 2 : 1;
                for (ItemStack tool : List.of(new ItemStack(Items.IRON_AXE), silk)) {
                    fixture.set(pos, state);
                    List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), pos, helper.getLevel().getBlockEntity(pos), null, tool);
                    assertOnlyRemains(helper, drops, expected, count, "Normal and silk-touch loot");
                }
                for (Explosion.BlockInteraction behavior : new Explosion.BlockInteraction[]{Explosion.BlockInteraction.DESTROY,
                        Explosion.BlockInteraction.DESTROY_WITH_DECAY}) {
                    fixture.set(pos, state);
                    List<ItemStack> drops = new ArrayList<>();
                    Explosion explosion = new Explosion(helper.getLevel(), null, pos.getX(), pos.getY(), pos.getZ(),
                            1F, false, behavior);
                    state.onExplosionHit(helper.getLevel(), pos, explosion, (stack, at) -> drops.add(stack));
                    helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "The actual explosion callback removes the campfire");
                    assertOnlyRemains(helper, drops, expected, count, "Native explosion loot including explosion-decay context");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aLitCampfireStillCalmsBeesThroughTheNativeSmokeQuery(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            for (int height = 1; height <= 5; height++) fixture.set(pos.above(height), Blocks.AIR.defaultBlockState());
            for (Block block : new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}) {
                fixture.set(pos, block.defaultBlockState().setValue(CampfireBlock.LIT, true));
                helper.assertTrue(CampfireBlock.isSmokeyPos(helper.getLevel(), pos.above(3)), "Lit ordinary and soul campfires remain native smoke sources for beehives");
                fixture.set(pos, helper.getLevel().getBlockState(pos).setValue(CampfireBlock.LIT, false));
                helper.assertTrue(!CampfireBlock.isSmokeyPos(helper.getLevel(), pos.above(3)), "An extinguished campfire cannot continue calming bees");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void tenLightCampfiresDoNotMeltAdjacentOrdinaryIce(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        List<BlockPos> ice = new ArrayList<>();
        try {
            for (BlockPos relative : BlockPos.betweenClosed(new BlockPos(0, 4, 0), new BlockPos(4, 4, 3)))
                fixture.set(helper.absolutePos(relative), Blocks.STONE.defaultBlockState());
            int x = 1;
            for (Block block : new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}) {
                BlockPos fire = helper.absolutePos(new BlockPos(x, 2, 1));
                BlockPos frozen = fire.south();
                fixture.set(fire, block.defaultBlockState().setValue(CampfireBlock.LIT, false));
                fixture.set(frozen, Blocks.ICE.defaultBlockState());
                fixture.set(fire.below(), Blocks.STONE.defaultBlockState());
                fixture.set(frozen.below(), Blocks.STONE.defaultBlockState());
                CampfireBlockEntity entity = (CampfireBlockEntity)helper.getLevel().getBlockEntity(fire);
                helper.assertValueEqual(CampfireController.refuel(entity, CampfireRules.MAX_FUEL, true), 3,
                        "The light test burns paid fuel rather than an unsupported lit block state");
                ice.add(frozen);
                x += 2;
            }
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
        helper.runAfterDelay(30, () -> {
            try (fixture) {
                for (BlockPos pos : ice) {
                    int brightness = helper.getLevel().getBrightness(LightLayer.BLOCK, pos);
                    helper.assertTrue(brightness > 0 && brightness <= 10, "The native light engine has propagated the limited campfire light to the ice");
                    helper.assertTrue(!helper.getLevel().canSeeSky(pos), "The roof keeps sunlight and rain out of this ice control");
                    for (int i = 0; i < 256; i++) Blocks.ICE.defaultBlockState().randomTick(helper.getLevel(), pos, RandomSource.create(i));
                    helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.ICE), "Repeated actual ice random ticks cannot melt ice from the ten-light campfire");
                }
            }
            helper.succeed();
        });
    }

    private static void assertOnlyRemains(GameTestHelper helper, List<ItemStack> drops, Item expected, int count, String context) {
        helper.assertTrue(!drops.isEmpty() && drops.stream().allMatch(stack -> stack.is(expected)), context + " yields only the non-portable remains");
        helper.assertValueEqual(drops.stream().mapToInt(ItemStack::getCount).sum(), count, context + " keeps the configured remainder quantity");
        helper.assertTrue(drops.stream().noneMatch(stack -> stack.is(Items.CAMPFIRE) || stack.is(Items.SOUL_CAMPFIRE)), context + " cannot move the intact campfire");
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final Map<BlockPos, BlockState> old = new LinkedHashMap<>();
        Fixture(GameTestHelper helper) { level = helper.getLevel(); }
        void set(BlockPos pos, BlockState state) {
            old.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlockAndUpdate(pos, state);
        }
        @Override public void close() {
            old.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
        }
    }
}
