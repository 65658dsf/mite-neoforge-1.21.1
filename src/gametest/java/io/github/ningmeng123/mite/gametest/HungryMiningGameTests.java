package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.survival.DualFoodData;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Hunger threshold and modern mining integration. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class HungryMiningGameTests {
    @GameTest(template = "empty")
    public static void oneFoodPointAndEatingRestoreNormalLeafMining(GameTestHelper helper) {
        Player player = miner(helper, GameType.SURVIVAL, 0);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        player.getFoodData().setFoodLevel(1);
        assertBits(helper, player.getDigSpeed(leaves, position), 0x3f800000,
                "One food point supplies normal strength without requiring a full food bar");
        assertBits(helper, leaves.getDestroyProgress(player, helper.getLevel(), position), 0x3c200000,
                "Fed level-zero leaves retain the recorded progress");

        player.getFoodData().setFoodLevel(0);
        assertBits(helper, player.getDigSpeed(leaves, position), 0x3e4ccccd,
                "An empty bar divides strength by five");
        assertBits(helper, leaves.getDestroyProgress(player, helper.getLevel(), position), 0x3b000000,
                "Hungry level-zero leaves require 512 effective progress increments");

        ItemStack food = new ItemStack(Items.DRIED_KELP);
        ItemStack remaining = food.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(remaining.isEmpty(), "The real item consumption path consumes the kelp");
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), 1, "Kelp restores exactly one food point");
        assertBits(helper, player.getDigSpeed(leaves, position), 0x3f800000,
                "Eating restores speed immediately without a tick or cached-state update");
        assertBits(helper, leaves.getDestroyProgress(player, helper.getLevel(), position), 0x3c200000,
                "Eating restores normal block progress immediately");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nutritionAloneAvoidsTheFoodEnergyMiningPenalty(GameTestHelper helper) {
        Player player = miner(helper, GameType.SURVIVAL, 0);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        DualFoodData nutrition = (DualFoodData) player.getFoodData();
        nutrition.mite$setNutrition(1);
        player.getFoodData().setFoodLevel(1);
        player.getFoodData().setFoodLevel(0);
        helper.assertValueEqual(nutrition.mite$getNutrition(), 1,
                "Setting satiation to zero does not erase the separate nutrition reserve");
        assertBits(helper, player.getDigSpeed(leaves, position), 0x3f800000,
                "One nutrition point alone supplies full level-zero mining strength");
        assertBits(helper, leaves.getDestroyProgress(player, helper.getLevel(), position), 0x3c200000,
                "Nutrition-only food energy avoids the divide-by-five progress penalty");
        nutrition.mite$setNutrition(0);
        assertBits(helper, player.getDigSpeed(leaves, position), 0x3e4ccccd,
                "Only clearing the remaining nutrition point divides mining strength by five");
        assertBits(helper, leaves.getDestroyProgress(player, helper.getLevel(), position), 0x3b000000,
                "Both reserves empty restores the independently recorded hungry progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hungerComposesOnceWithLevelsToolsAirAndSavedFood(GameTestHelper helper) {
        Player player = miner(helper, GameType.SURVIVAL, 0);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        int[] levels = {-40, 5, 35, 200};
        int[] strengthBits = {0x3d23d70d, 0x3e6147af, 0x3eae147b, 0x3f800000};
        int[] progressBits = {0x39ccccd1, 0x3b0ccccd, 0x3b599999, 0x3c200000};
        for (int i = 0; i < levels.length; i++) {
            PlayerExperience.setLevels(player, levels[i]);
            assertBits(helper, player.getDigSpeed(leaves, position), strengthBits[i], "Hungry level " + levels[i]);
            assertBits(helper, leaves.getDestroyProgress(player, helper.getLevel(), position), progressBits[i],
                    "Hunger and level are each applied once for level " + levels[i]);
        }

        PlayerExperience.setTotal(player, 6650);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertBits(helper, player.getDigSpeed(stone, position), 0x402e147b,
                "Iron tool strength is preserved before hunger and level");
        player.setOnGround(false);
        assertBits(helper, player.getDigSpeed(stone, position), 0x3f0b4396,
                "The modern airborne penalty and food penalty each run once");
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 0));
        assertBits(helper, player.getDigSpeed(stone, position), 0x3f271de7,
                "Haste remains inside the completed modern strength input");
        assertBits(helper, stone.getDestroyProgress(player, helper.getLevel(), position), 0x3a0b4396,
                "Progress does not apply the environment or hunger penalties again");

        player.removeEffect(MobEffects.DIG_SPEED);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.setOnGround(true);
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        helper.assertValueEqual(saved.getInt("foodLevel"), 0, "The entity save records an empty food bar");
        helper.assertValueEqual(saved.getInt("mite_nutrition"), 0, "The hungry fixture also saves empty independent nutrition");
        Player restored = helper.makeMockPlayer(GameType.SURVIVAL);
        restored.load(saved);
        helper.assertValueEqual(restored.experienceLevel, 35, "The entity load restores the mining level");
        helper.assertValueEqual(restored.getFoodData().getFoodLevel(), 0, "Loading does not fill the empty bar");
        helper.assertValueEqual(((DualFoodData) restored.getFoodData()).mite$getNutrition(), 0,
                "Loading does not refill the independent nutrition reserve");
        assertBits(helper, restored.getDigSpeed(leaves, position), 0x3eae147b,
                "Loaded food and experience immediately select hungry level-35 strength");
        assertBits(helper, leaves.getDestroyProgress(restored, helper.getLevel(), position), 0x3b599999,
                "The restored player keeps the correct hungry progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeAndSpectatorBypassHungerWhileSurvivalAndAdventureUseIt(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        for (GameType mode : GameType.values()) {
            Player player = miner(helper, mode, 6650);
            boolean bypass = mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
            assertBits(helper, player.getDigSpeed(leaves, position), bypass ? 0x3f800000 : 0x3eae147b,
                    "Empty-bar strength in " + mode);
            assertBits(helper, leaves.getDestroyProgress(player, helper.getLevel(), position), bypass ? 0x3e2aaaab : 0x3b599999,
                    "Creative/spectator retain modern progress and survival/adventure use hungry R196 progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void earlierSpeedChangesAndCancellationKeepTheirMeaningWhenHungry(GameTestHelper helper) {
        Player player = miner(helper, GameType.SURVIVAL, -800);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        float[] replacement = {3.0F};
        boolean[] canceled = {false};
        Consumer<PlayerEvent.BreakSpeed> listener = event -> {
            if (event.getEntity() == player) {
                event.setNewSpeed(replacement[0]);
                event.setCanceled(canceled[0]);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerEvent.BreakSpeed.class, listener);
        try {
            assertBits(helper, player.getDigSpeed(leaves, position), 0x3df5c294,
                    "The prior event's strength is divided before the negative-level factor");
            for (float blocked : new float[]{0.0F, -3.0F}) {
                replacement[0] = blocked;
                helper.assertValueEqual(player.getDigSpeed(leaves, position), blocked,
                        "Nonpositive event speeds stay unchanged");
                helper.assertValueEqual(leaves.getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                        "Nonpositive speed cannot create mining progress");
            }
            replacement[0] = Float.NaN;
            helper.assertTrue(Float.isNaN(player.getDigSpeed(leaves, position)), "A nonnumeric speed is not made positive");
            helper.assertValueEqual(leaves.getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                    "A nonnumeric speed cannot create progress");
            replacement[0] = 3.0F;
            canceled[0] = true;
            helper.assertValueEqual(player.getDigSpeed(leaves, position), -1.0F, "Cancellation retains the NeoForge sentinel");
            helper.assertValueEqual(Blocks.TORCH.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                    "Canceled zero-hardness mining stays blocked while hungry");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        assertBits(helper, player.getDigSpeed(leaves, position), 0x3d23d70d,
                "Removing the temporary listener restores ordinary hungry mining");
        helper.succeed();
    }

    private static Player miner(GameTestHelper helper, GameType mode, int totalExperience) {
        Player player = helper.makeMockPlayer(mode);
        PlayerExperience.setTotal(player, totalExperience);
        player.setOnGround(true);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.getFoodData().setFoodLevel(0);
        ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
        player.getFoodData().setSaturation(0.0F);
        return player;
    }

    private static void assertBits(GameTestHelper helper, float actual, int expectedBits, String context) {
        helper.assertValueEqual(Float.floatToRawIntBits(actual), expectedBits,
                context + ": expected bits " + Integer.toHexString(expectedBits)
                        + ", actual " + Integer.toHexString(Float.floatToRawIntBits(actual)));
    }
}
