package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.network.NutritionPayload;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import io.github.ningmeng123.mite.survival.LeveledFoodData;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real food entry points honor extension capacity above twenty. @author NingMeng123 */
@GameTestHolder("mite_food_capacity")
@PrefixGameTestTemplate(false)
public final class FoodCapacityExtensionGameTests {
    private FoodCapacityExtensionGameTests() {}

    @GameTest(template = "empty")
    public static void extraCapacityAllowsActualEatingToFortyWithoutAnImmediateRefill(GameTestHelper helper) throws Exception {
        try (CapacityPolicy policy = new CapacityPolicy()) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.experienceLevel = 35;
            FoodData food = player.getFoodData();
            DualFoodData dual = (DualFoodData) food;
            food.setFoodLevel(20);
            dual.mite$setNutrition(20);
            policy.bonus = 20;
            food.tick(player);
            helper.assertValueEqual(MiteExtensions.foodCapacity(player), 40, "The extension adds twenty to the base capacity");
            assertReserves(helper, food, 20, 20);
            helper.assertTrue(food.needsFood() && FoodConsumption.canIngest(player, new ItemStack(Items.COOKED_BEEF)),
                    "The native hunger gate and real item gate allow eating beyond twenty");
            new ItemStack(Items.COOKED_BEEF).finishUsingItem(helper.getLevel(), player);
            assertReserves(helper, food, 30, 30);
            new ItemStack(Items.COOKED_BEEF).finishUsingItem(helper.getLevel(), player);
            assertReserves(helper, food, 40, 40);
            helper.assertFalse(food.needsFood() || FoodConsumption.canIngest(player, new ItemStack(Items.COOKED_BEEF)),
                    "Both normal eating gates stop at the expanded capacity");
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oddCapacityAndNativeFoodAdditionUseTheExactLimit(GameTestHelper helper) throws Exception {
        try (CapacityPolicy policy = new CapacityPolicy()) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            policy.bonus = 1;
            player.getFoodData().eat(2, 0F);
            helper.assertValueEqual(MiteExtensions.foodCapacity(player), 7, "A single rank adds one food point");
            assertReserves(helper, player.getFoodData(), 7, 7);
            helper.assertFalse(player.getFoodData().needsFood(), "An odd capacity is fully usable");
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void expandedReservesSurviveFoodDataSavingAndClampWhenTheBonusIsRemoved(GameTestHelper helper) throws Exception {
        try (CapacityPolicy policy = new CapacityPolicy()) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.experienceLevel = 35;
            policy.bonus = 20;
            player.getFoodData().setFoodLevel(40);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(39);
            CompoundTag saved = new CompoundTag();
            player.getFoodData().addAdditionalSaveData(saved);
            FoodData restored = new FoodData();
            restored.readAdditionalSaveData(saved);
            ((LeveledFoodData) restored).mite$bindPlayer(player);
            ((LeveledFoodData) restored).mite$clampToLimit();
            assertReserves(helper, restored, 40, 39);
            policy.bonus = 0;
            ((LeveledFoodData) restored).mite$clampToLimit();
            assertReserves(helper, restored, 20, 20);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nutritionSnapshotRoundTripsExpandedAndOddCapacities(GameTestHelper helper) {
        for (int limit : new int[]{7, 26, 39, 40}) {
            NutritionPayload payload = new NutritionPayload(123, limit, limit - 1, limit,
                    160000, 160000, 160000, 0, -1, 0F);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                NutritionPayload.STREAM_CODEC.encode(buffer, payload);
                helper.assertValueEqual(NutritionPayload.STREAM_CODEC.decode(buffer), payload,
                        "The real network codec preserves expanded capacities and resource values");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void baseProgressionAndMinimumCapacityContinueThroughTheSamePolicyEntry(GameTestHelper helper) throws Exception {
        try (CapacityPolicy policy = new CapacityPolicy()) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertValueEqual(MiteExtensions.foodCapacity(player), 6, "Level zero retains the base capacity");
            player.experienceLevel = 35;
            helper.assertValueEqual(MiteExtensions.foodCapacity(player), 20, "The base curve reaches twenty");
            policy.bonus = -100;
            helper.assertValueEqual(MiteExtensions.foodCapacity(player), 1, "The extension entry still enforces a positive capacity");
            player.discard();
        }
        helper.succeed();
    }

    private static void assertReserves(GameTestHelper helper, FoodData food, int satiation, int nutrition) {
        helper.assertValueEqual(food.getFoodLevel(), satiation, "Actual satiation matches the expected reserve");
        helper.assertValueEqual(((DualFoodData) food).mite$getNutrition(), nutrition, "Actual nutrition matches the expected reserve");
    }

    private static final class CapacityPolicy implements AutoCloseable {
        private final Field field = MiteExtensions.class.getDeclaredField("policy");
        private final MiteExtensions.Policy previous = MiteExtensions.policy();
        private int bonus;

        CapacityPolicy() throws ReflectiveOperationException {
            field.setAccessible(true);
            field.set(null, new MiteExtensions.Policy() {
                @Override public int foodCapacity(Player player, int original) { return original + bonus; }
            });
        }

        @Override public void close() throws IllegalAccessException { field.set(null, previous); }
    }
}
