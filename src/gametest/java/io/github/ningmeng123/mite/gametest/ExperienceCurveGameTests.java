package io.github.ningmeng123.mite.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Exercises the transformed vanilla XP APIs and their integer state in a server.
 * Expected thresholds are literal vectors from the R196 initializer bytecode;
 * the upper gameplay cap and proportional level spending are modern decisions.
 *
 * @author NingMeng123
 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ExperienceCurveGameTests {
    @GameTest(template = "empty")
    public static void pointAwardsCrossExactR196Boundaries(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperiencePoints(19);
        assertExperience(helper, player, 19, 0, 19, 20, "One point before level one");
        player.giveExperiencePoints(1);
        assertExperience(helper, player, 20, 1, 0, 30, "Level one starts at 20 XP");
        player.giveExperiencePoints(29);
        assertExperience(helper, player, 49, 1, 29, 30, "One point before level two");
        player.giveExperiencePoints(1);
        assertExperience(helper, player, 50, 2, 0, 40, "Level two starts at 50 XP");
        player.giveExperiencePoints(-1);
        assertExperience(helper, player, 49, 1, 29, 30, "Spending a point crosses back into level one");

        Player largeReward = helper.makeMockPlayer(GameType.SURVIVAL);
        largeReward.giveExperiencePoints(6650);
        assertExperience(helper, largeReward, 6650, 35, 0, 370, "One reward crosses 35 levels");
        largeReward.giveExperiencePoints(369);
        assertExperience(helper, largeReward, 7019, 35, 369, 370, "Last point of level 35");
        largeReward.giveExperiencePoints(1);
        assertExperience(helper, largeReward, 7020, 36, 0, 380, "Level 36 boundary");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void serverSettersKeepExactPointsAndProportions(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            helper.assertTrue(player.isCreative(), "The mock server player exercises the creative API path");
            player.setExperienceLevels(8);
            player.setExperiencePoints(53);
            assertExperience(helper, player, 493, 8, 53, 100, "Server point setter retains the exact integer 53");
            player.giveExperienceLevels(2);
            assertExperience(helper, player, 713, 10, 63, 120, "Adding levels carries floor(53*120/100) points");
            player.setExperienceLevels(9);
            assertExperience(helper, player, 597, 9, 57, 110, "Setting a level carries floor(63*110/120) points");
            player.setExperiencePoints(Integer.MAX_VALUE);
            assertExperience(helper, player, 649, 9, 109, 110, "Point setter stops before promotion");
            player.setExperiencePoints(Integer.MIN_VALUE);
            assertExperience(helper, player, 540, 9, 0, 110, "Negative point setter clamps to zero within the level");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantingSpendsLevelsWithoutRestoringOldTotal(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperiencePoints(493);
        player.onEnchantmentPerformed(new ItemStack(Items.DIAMOND_SWORD), 3);
        assertExperience(helper, player, 237, 5, 37, 70, "Enchanting spends three levels and retains the old proportion");
        player.giveExperienceLevels(-1);
        assertExperience(helper, player, 171, 4, 31, 60, "The level API used by anvils also spends the canonical total");
        player.giveExperiencePoints(1);
        assertExperience(helper, player, 172, 4, 32, 60, "The next point must not restore the pre-enchantment balance");

        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        PlayerExperience.setTotal(creative, -1);
        creative.onEnchantmentPerformed(new ItemStack(Items.DIAMOND_SWORD), 0);
        assertExperience(helper, creative, -1, -1, 19, 20, "Creative enchanting must not clear an existing debt");
        creative.onEnchantmentPerformed(new ItemStack(Items.DIAMOND_SWORD), 1);
        assertExperience(helper, creative, -21, -2, 19, 20, "The enchantment callback preserves signed level spending");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedExperienceMigratesOnceAndThenUsesCanonicalTotal(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("XpLevel", 8);
        legacy.putFloat("XpP", 0.5F);
        legacy.putInt("XpTotal", 123456);
        PlayerExperience.normalizeLoaded(player, legacy);
        assertExperience(helper, player, 490, 8, 50, 100, "Unversioned saves preserve their level and proportion");

        // Exercise the actual NBT-tail hook, not only the normalization helper.
        Player loaded = helper.makeMockPlayer(GameType.SURVIVAL);
        loaded.readAdditionalSaveData(legacy);
        assertExperience(helper, loaded, 490, 8, 50, 100, "Vanilla NBT loading invokes legacy migration");
        loaded.giveExperiencePoints(3);
        CompoundTag saved = new CompoundTag();
        loaded.addAdditionalSaveData(saved);
        helper.assertValueEqual(saved.getInt(PlayerExperience.VERSION_TAG), PlayerExperience.CURRENT_VERSION,
                "Saving marks the current canonical XP schema");
        helper.assertValueEqual(saved.getInt("XpTotal"), 493, "Saving preserves the exact total");

        // Conflicting derived fields must not override a versioned integer total.
        saved.putInt(PlayerExperience.VERSION_TAG, 1);
        saved.putInt("XpLevel", 35);
        saved.putFloat("XpP", 0.9F);
        Player reloaded = helper.makeMockPlayer(GameType.SURVIVAL);
        reloaded.readAdditionalSaveData(saved);
        assertExperience(helper, reloaded, 493, 8, 53, 100, "Version one restores from total rather than stale derived fields");
        PlayerExperience.normalizeLoaded(reloaded, saved);
        assertExperience(helper, reloaded, 493, 8, 53, 100, "Normalizing a canonical save is idempotent");
        saved.putInt(PlayerExperience.VERSION_TAG, PlayerExperience.CURRENT_VERSION);
        saved.putInt("XpTotal", -1);
        reloaded.readAdditionalSaveData(saved);
        assertExperience(helper, reloaded, -1, -1, 19, 20, "The signed schema preserves negative canonical experience");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledPointEventLeavesAllExperienceFieldsUnchanged(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperiencePoints(19);
        int[] calls = {0};
        Consumer<PlayerXpEvent.XpChange> cancel = event -> {
            if (event.getEntity() == player) {
                calls[0]++;
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayerXpEvent.XpChange.class, cancel);
        try {
            player.giveExperiencePoints(6631);
            helper.assertValueEqual(calls[0], 1, "Point changes post one cancellable event");
            assertExperience(helper, player, 19, 0, 19, 20, "Canceled point reward cannot partially mutate state");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        player.giveExperiencePoints(1);
        assertExperience(helper, player, 20, 1, 0, 30, "Removing the listener restores normal point awards");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledAggregateLevelEventRollsBackThePointAward(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperiencePoints(19);
        int[] calls = {0};
        int[] requestedLevels = {0};
        Consumer<PlayerXpEvent.LevelChange> cancel = event -> {
            if (event.getEntity() == player) {
                calls[0]++;
                requestedLevels[0] = event.getLevels();
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayerXpEvent.LevelChange.class, cancel);
        try {
            player.giveExperiencePoints(6631);
            helper.assertValueEqual(calls[0], 1, "A multi-level point reward posts one aggregate level event");
            helper.assertValueEqual(requestedLevels[0], 35, "The event reports the complete 35-level change");
            assertExperience(helper, player, 19, 0, 19, 20, "Canceling the level event also rejects its point reward");
            player.giveExperienceLevels(2);
            helper.assertValueEqual(calls[0], 2, "Direct level changes use the same cancellable event");
            helper.assertValueEqual(requestedLevels[0], 2, "The direct level request is preserved");
            assertExperience(helper, player, 19, 0, 19, 20, "Canceled direct level change cannot partially mutate state");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        player.giveExperiencePoints(1);
        assertExperience(helper, player, 20, 1, 0, 30, "Removing the listener restores normal level changes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gameplayCapAndExtremeDeltasNeverWrap(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperiencePoints(1);
        player.giveExperiencePoints(Integer.MAX_VALUE);
        assertExperience(helper, player, 203000, 200, 0, 1, "Large point award stops at level 200 with an empty bar");
        player.giveExperiencePoints(Integer.MAX_VALUE);
        assertExperience(helper, player, 203000, 200, 0, 1, "Repeated capped award cannot wrap the total");
        player.giveExperiencePoints(Integer.MIN_VALUE);
        assertExperience(helper, player, -800, -40, 0, 20, "Large point subtraction clamps at the debt floor");
        player.giveExperienceLevels(Integer.MAX_VALUE);
        assertExperience(helper, player, 203000, 200, 0, 1, "Large level addition stops at the gameplay cap");
        player.giveExperienceLevels(Integer.MIN_VALUE);
        assertExperience(helper, player, -800, -40, 0, 20, "Large level subtraction cannot overflow");
        PlayerExperience.setLevels(player, Integer.MAX_VALUE);
        PlayerExperience.setPoints(player, Integer.MAX_VALUE);
        assertExperience(helper, player, 203000, 200, 0, 1, "Set APIs cannot fill a nonexistent level 201 bar");
        PlayerExperience.setLevels(player, Integer.MIN_VALUE);
        assertExperience(helper, player, -800, -40, 0, 20, "Extreme negative target levels clamp all derived state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void negativeExperienceUsesTheSameModelInEveryGameMode(GameTestHelper helper) {
        for (GameType mode : GameType.values()) {
            Player player = helper.makeMockPlayer(mode);
            PlayerExperience.setTotal(player, -21);
            assertExperience(helper, player, -21, -2, 19, 20, mode + " keeps signed experience");
            player.giveExperiencePoints(1);
            assertExperience(helper, player, -20, -1, 0, 20, mode + " crosses a negative boundary");
            player.giveExperiencePoints(19);
            assertExperience(helper, player, -1, -1, 19, 20, mode + " retains the final debt point");
            player.giveExperiencePoints(1);
            assertExperience(helper, player, 0, 0, 0, 20, mode + " reaches zero without a skipped level");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void commandQueriesAndOrbPickupUseTheIntegerModel(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setExperienceLevels(0);
            player.setExperiencePoints(0);
            ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), player.getX(), player.getY(), player.getZ(), 19);
            player.takeXpDelay = 0;
            orb.playerTouch(player);
            assertExperience(helper, player, 19, 0, 19, 20, "An unmodified experience orb awards its remaining points");
            helper.assertTrue(orb.isRemoved(), "The picked-up single orb must be consumed");

            player.setExperiencePoints(0);
            CommandSourceStack source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            execute(helper, source, "experience set @s 8 levels");
            execute(helper, source, "experience set @s 53 points");
            assertExperience(helper, player, 493, 8, 53, 100, "Commands share the exact setter model");
            helper.assertValueEqual(execute(helper, source, "experience query @s points"), 53,
                    "The points query must not round 53 down through a float");
            execute(helper, source, "experience set @s 9 levels");
            execute(helper, source, "experience set @s 1 points");
            assertExperience(helper, player, 541, 9, 1, 110, "Level nine retains a single point");
            helper.assertValueEqual(execute(helper, source, "experience query @s points"), 1,
                    "The points query must not round a fractional bar down to zero");

            assertCommandRejected(helper, source, "experience set @s 201 levels");
            assertExperience(helper, player, 541, 9, 1, 110, "Rejected level 201 command leaves the player unchanged");
            execute(helper, source, "experience set @s 200 levels");
            assertCommandRejected(helper, source, "experience set @s 1 points");
            assertExperience(helper, player, 203000, 200, 0, 1, "Rejected capped point command leaves an empty bar");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static int execute(GameTestHelper helper, CommandSourceStack source, String command) throws CommandSyntaxException {
        return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
    }

    private static void assertCommandRejected(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            int result = execute(helper, source, command);
            helper.assertValueEqual(result, 0, "Rejected command must not report a successful target: " + command);
        } catch (CommandSyntaxException expected) {
            // Vanilla command failures are syntax exceptions; callers report failure.
        }
    }

    private static void assertExperience(GameTestHelper helper, Player player, int total, int level,
                                         int points, int needed, String context) {
        helper.assertValueEqual(player.totalExperience, total, context + " (total)");
        helper.assertValueEqual(player.experienceLevel, level, context + " (level)");
        helper.assertValueEqual(PlayerExperience.pointsWithinLevel(player), points, context + " (points)");
        helper.assertValueEqual(player.getXpNeededForNextLevel(), needed, context + " (bar width)");
        float expectedProgress = (float) points / needed;
        helper.assertTrue(Math.abs(player.experienceProgress - expectedProgress) < 0.000001F,
                context + " (bar progress expected " + expectedProgress + ", actual " + player.experienceProgress + ")");
    }
}
