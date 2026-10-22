package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.BlueberryBushBlock;
import io.github.ningmeng123.mite.item.FoodProfile;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.github.ningmeng123.mite.world.PlacementDelay;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native fruit placement, rejected sites and the independent edible/sheared items. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class BlueberryPlantingGameTests {
    private BlueberryPlantingGameTests() {}

    @GameTest(template = "empty")
    public static void actualFruitUsePlantsOnForestSoilWithSurvivalAndCreativeConsumption(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 32)) {
            for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
                Player player = f.player(mode);
                for (var soil : new net.minecraft.world.level.block.Block[]{Blocks.DIRT, Blocks.GRASS_BLOCK}) {
                    f.prepare(soil.defaultBlockState());
                    PlacementDelay.clear(player);
                    ItemStack berries = new ItemStack(MiteFoods.BLUEBERRIES.get(), 3);
                    player.setItemInHand(InteractionHand.MAIN_HAND, berries);
                    helper.assertTrue(berries.useOn(f.context(player)).consumesAction(),
                            "The actual edible fruit interaction accepts exposed forest dirt and grass");
                    BlockState placed = helper.getLevel().getBlockState(f.pos);
                    helper.assertTrue(placed.is(MiteFoodPlants.BLUEBERRY_BUSH.get()),
                            "Right-clicking soil with blueberries actually places the registered bush");
                    helper.assertValueEqual(placed.getValue(SweetBerryBushBlock.AGE), mode == GameType.CREATIVE ? 3 : 0,
                            "Fruit placement retains the existing survival age zero and creative mature age");
                    helper.assertValueEqual(berries.getCount(), mode == GameType.CREATIVE ? 3 : 2,
                            "The native transaction consumes exactly one fruit in survival and none in creative");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fruitCannotBypassForbiddenSoilsOrTheForestHabitat(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 48)) {
            Player player = f.player(GameType.SURVIVAL);
            for (var soil : new net.minecraft.world.level.block.Block[]{Blocks.FARMLAND, Blocks.STONE}) {
                f.prepare(soil.defaultBlockState());
                assertRejected(helper, f, player, "Farmland and stone remain forbidden blueberry substrates");
            }
            f.prepare(Blocks.DIRT.defaultBlockState());
            f.fillBiome.accept(Biomes.PLAINS);
            helper.assertFalse(helper.getLevel().getBiome(f.pos).is(BlueberryBushBlock.HABITAT),
                    "The biome negative control is outside the actual blueberry habitat tag");
            assertRejected(helper, f, player, "A valid dirt substrate cannot bypass the forest-only habitat rule");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void fruitCannotPlantUnderAnOpaqueRoof(GameTestHelper helper) {
        Fixture f = new Fixture(helper, 64);
        f.prepare(Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlock(f.pos.above(), Blocks.STONE.defaultBlockState(), 3);
        // Let the native server light engine apply the roof before checking actual placement.
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertFalse(helper.getLevel().canSeeSky(f.pos), "The roof control really blocks native sky visibility");
                assertRejected(helper, f, f.player(GameType.SURVIVAL), "Blueberry fruit cannot plant beneath opaque cover");
            } finally {
                f.close();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void fruitStillEatsAndTheShearedBushKeepsItsIndependentPlacementAndCloneIdentity(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, 80)) {
            Player player = f.player(GameType.SURVIVAL);
            ItemStack berries = new ItemStack(MiteFoods.BLUEBERRIES.get(), 3);
            helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(berries.getItem()),
                    ResourceLocation.fromNamespaceAndPath("mite", "blueberries"), "Existing saves keep the fruit registry identity");
            helper.assertValueEqual(berries.getItem().getDescriptionId(), "item.mite.blueberries", "The fruit keeps its item translation key");
            helper.assertValueEqual(berries.getMaxStackSize(), 16, "Edible fruit retains its stack limit of sixteen");
            helper.assertTrue(berries.has(DataComponents.FOOD), "The plantable fruit retains the actual native food component");
            helper.assertValueEqual(MiteFoodCatalog.profile(berries), new FoodProfile(1, 1, false, false, true, 1000, false, false),
                    "Planting does not change blueberry satiation, nutrition, plants or sugar");
            player.getFoodData().setFoodLevel(0);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
            NutritionState.set(player, new NutritionState(1, 0, 0, 0, 0, -1));
            player.setItemInHand(InteractionHand.MAIN_HAND, berries);
            helper.assertTrue(berries.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                    "An actual use in the air can still begin eating the fruit");
            helper.assertTrue(player.isUsingItem(), "The real food interaction starts the use animation");
            berries.finishUsingItem(helper.getLevel(), player);
            player.stopUsingItem();
            helper.assertValueEqual(berries.getCount(), 2, "Finishing food consumption eats one fruit");
            helper.assertValueEqual(player.getFoodData().getFoodLevel(), 1, "The fruit still adds its original one satiation");
            helper.assertValueEqual(((DualFoodData) player.getFoodData()).mite$getNutrition(), 1, "The fruit still adds its original one nutrition");
            helper.assertTrue(NutritionState.get(player).plants() > 0, "The actual eating path still applies plant micronutrients");

            helper.assertTrue(MiteFoodPlants.BLUEBERRY_BUSH.get().asItem() == MiteFoodPlants.BLUEBERRY_BUSH_ITEM.get(),
                    "The new fruit placement registration cannot replace the independently sheared bush's block item");
            f.prepare(Blocks.GRASS_BLOCK.defaultBlockState());
            ItemStack bush = new ItemStack(MiteFoodPlants.BLUEBERRY_BUSH_ITEM.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, bush);
            helper.assertTrue(bush.useOn(f.context(player)).consumesAction(), "A sheared bush still uses native placement on legal forest grass");
            BlockState placed = helper.getLevel().getBlockState(f.pos);
            helper.assertTrue(placed.is(MiteFoodPlants.BLUEBERRY_BUSH.get()), "The original bush item actually places its bush");
            helper.assertValueEqual(bush.getCount(), 1, "Sheared-bush survival placement consumes exactly one bush item");
            helper.assertTrue(placed.getBlock().getCloneItemStack(helper.getLevel(), f.pos, placed).is(MiteFoodPlants.BLUEBERRY_BUSH_ITEM.get()),
                    "Picking the placed bush still gives the sheared bush rather than edible blueberries");
        }
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, Fixture f, Player player, String reason) {
        ItemStack berries = new ItemStack(MiteFoods.BLUEBERRIES.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, berries);
        berries.useOn(f.context(player));
        helper.assertTrue(helper.getLevel().getBlockState(f.pos).isAir(), reason);
        helper.assertValueEqual(berries.getCount(), 3, "Rejected placement consumes no fruit; a possible food fallback only starts eating");
        player.stopUsingItem();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final BlockPos pos;
        private final BlockState[] previous;
        private final Runnable restoreBiomes;
        private final Consumer<ResourceKey<Biome>> fillBiome;

        private Fixture(GameTestHelper helper, int belowBuildLimit) {
            this.helper = helper;
            var level = helper.getLevel();
            var reference = helper.absolutePos(new BlockPos(2, 2, 2));
            // A separate high section per test keeps fuzzy biome samples within the restored palette.
            int y = ((level.getMaxBuildHeight() - belowBuildLimit) & ~15) + 8;
            pos = new BlockPos((reference.getX() & ~15) + 8, y, (reference.getZ() & ~15) + 8);
            previous = new BlockState[]{level.getBlockState(pos.below()), level.getBlockState(pos), level.getBlockState(pos.above())};
            var chunk = level.getChunkAt(pos);
            var section = chunk.getSection(chunk.getSectionIndex(y));
            var old = section.getBiomes();
            var snapshots = new ArrayList<Holder<Biome>>(64);
            for (int x = 0; x < 4; x++) for (int qy = 0; qy < 4; qy++) for (int z = 0; z < 4; z++) snapshots.add(old.get(x, qy, z));
            int quartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
            int quartY = QuartPos.fromBlock(y) & ~3;
            int quartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            var sampler = level.getChunkSource().randomState().sampler();
            fillBiome = biome -> {
                var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
                section.fillBiomesFromNoise((x, qy, z, climate) -> holder, sampler, quartX, quartY, quartZ);
            };
            restoreBiomes = () -> section.fillBiomesFromNoise((x, qy, z, climate) ->
                    snapshots.get(((x & 3) << 4) | ((qy & 3) << 2) | (z & 3)), sampler, quartX, quartY, quartZ);
            fillBiome.accept(Biomes.FOREST);
            helper.assertTrue(level.getBiome(pos).is(BlueberryBushBlock.HABITAT), "The positive fixture uses the actual registered forest habitat");
            helper.assertTrue(level.canSeeSky(pos), "The positive placement fixture is outdoors");
        }

        private void prepare(BlockState soil) {
            var level = helper.getLevel();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos.below(), soil, 2);
        }

        private Player player(GameType mode) {
            Player player = helper.makeMockPlayer(mode);
            // The helper overrides isCreative only; native item consumption checks instabuild.
            mode.updatePlayerAbilities(player.getAbilities());
            player.setPos(pos.getX() + .5D, pos.getY(), pos.getZ() + 2.5D);
            return player;
        }

        private UseOnContext context(Player player) {
            BlockPos soil = pos.below();
            return new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(soil).add(0D, .5D, 0D), Direction.UP, soil, false));
        }

        @Override public void close() {
            var level = helper.getLevel();
            level.setBlock(pos, previous[1], 2);
            level.setBlock(pos.above(), previous[2], 2);
            level.setBlock(pos.below(), previous[0], 2);
            restoreBiomes.run();
        }
    }
}
