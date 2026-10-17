package io.github.ningmeng123.mite.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Public status queries preserve the operator experience commands. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class PlayerStatusCommandGameTests {
    private PlayerStatusCommandGameTests() {}

    @GameTest(template = "empty")
    public static void publicDayUsesTheOverworldDisplayDateAcrossMidnight(GameTestHelper helper) throws CommandSyntaxException {
        var player = helper.makeMockServerPlayerInLevel();
        var overworld = player.server.overworld();
        var nether = player.server.getLevel(Level.NETHER);
        long oldTime = overworld.getDayTime();
        helper.assertTrue(nether != null, "The real nether dimension is available");
        long oldNetherTime = nether.getDayTime();
        var output = new CapturingSource();
        var source = player.createCommandSourceStack().withPermission(0).withLevel(nether).withSource(output);
        var dispatcher = player.server.getCommands().getDispatcher();
        try {
            nether.setDayTime(0L);
            long[][] dates = {{0L, 1L}, {17_999L, 1L}, {18_000L, 2L}, {42_000L, 3L}};
            for (long[] date : dates) {
                overworld.setDayTime(date[0]);
                output.messages.clear();
                dispatcher.execute("day", source);
                assertFeedback(helper, output, "gui.mite.world_day", date[1]);
                helper.assertValueEqual(overworld.getDayTime(), date[0], "Reading the date never changes world time");
            }
        } finally {
            nether.setDayTime(oldNetherTime);
            overworld.setDayTime(oldTime);
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void publicXpReportsTheSignedTotalWithoutChangingExperience(GameTestHelper helper) throws CommandSyntaxException {
        var player = helper.makeMockServerPlayerInLevel();
        var output = new CapturingSource();
        var source = player.createCommandSourceStack().withPermission(0).withSource(output);
        var dispatcher = player.server.getCommands().getDispatcher();
        try {
            for (int amount : new int[] {0, 1_234, -37, Integer.MAX_VALUE}) {
                player.totalExperience = amount;
                player.experienceLevel = 9;
                player.experienceProgress = 0.25F;
                output.messages.clear();
                helper.assertValueEqual(dispatcher.execute("xp", source), amount,
                        "The public query returns total points, including debt and the integer storage boundary");
                assertFeedback(helper, output, "commands.mite.xp", amount);
                helper.assertValueEqual(player.totalExperience, amount, "Querying does not change the total balance");
                helper.assertValueEqual(player.experienceLevel, 9, "Querying does not change experience levels");
                helper.assertValueEqual(player.experienceProgress, 0.25F, "Querying does not change level progress");
            }
        } finally {
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void publicXpDoesNotExposeAnyOperatorSubcommands(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var source = player.createCommandSourceStack().withPermission(0).withSuppressedOutput();
        var dispatcher = player.server.getCommands().getDispatcher();
        try {
            player.totalExperience = 123;
            player.experienceLevel = 3;
            player.experienceProgress = 0.5F;
            for (String root : List.of("xp", "experience")) {
                for (String suffix : List.of("add @s 5 points", "set @s 0 points", "query @s points")) {
                    assertRejected(helper, dispatcher, root + " " + suffix, source);
                    helper.assertValueEqual(player.totalExperience, 123, "Rejected commands preserve the total balance");
                    helper.assertValueEqual(player.experienceLevel, 3, "Rejected commands preserve experience levels");
                    helper.assertValueEqual(player.experienceProgress, 0.5F, "Rejected commands preserve level progress");
                }
            }
        } finally {
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void operatorsKeepBothExperienceCommandNamesAndConsoleXpNeedsAPlayer(GameTestHelper helper) throws CommandSyntaxException {
        var player = helper.makeMockServerPlayerInLevel();
        var dispatcher = player.server.getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        try {
            for (String root : List.of("xp", "experience")) {
                helper.assertValueEqual(dispatcher.execute(root + " set @s 3 levels", source), 1,
                        "The operator set command retains its selected target");
                helper.assertValueEqual(player.experienceLevel, 3, "The operator can still set experience levels");
                helper.assertValueEqual(dispatcher.execute(root + " add @s 1 levels", source), 1,
                        "The operator add command retains its selected target");
                helper.assertValueEqual(dispatcher.execute(root + " query @s levels", source), 4,
                        "The operator query still returns levels rather than total points");
                int previousTotal = player.totalExperience;
                dispatcher.execute(root + " add @s 5 points", source);
                helper.assertValueEqual(player.totalExperience, previousTotal + 5, "Operator point grants still work");
            }
            assertRejected(helper, dispatcher, "xp", player.server.createCommandSourceStack().withSuppressedOutput());
        } finally {
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher,
            String command, CommandSourceStack source) {
        try {
            dispatcher.execute(command, source);
        } catch (CommandSyntaxException expected) {
            return;
        }
        helper.assertTrue(false, "This source must not execute /" + command);
    }

    private static void assertFeedback(GameTestHelper helper, CapturingSource output, String key, long value) {
        helper.assertValueEqual(output.messages.size(), 1, "A status query sends one direct reply");
        var content = output.messages.getFirst().getContents();
        helper.assertTrue(content instanceof TranslatableContents, "The reply is localizable");
        var translated = (TranslatableContents) content;
        helper.assertValueEqual(translated.getKey(), key, "The reply uses the expected status translation");
        helper.assertValueEqual(translated.getArgs().length, 1, "The reply has one numeric status argument");
        helper.assertTrue(translated.getArgs()[0] instanceof Number, "The status argument is numeric");
        helper.assertValueEqual(((Number) translated.getArgs()[0]).longValue(), value, "The reply contains the exact status value");
    }

    private static final class CapturingSource implements CommandSource {
        private final List<Component> messages = new ArrayList<>();

        @Override public void sendSystemMessage(Component message) { messages.add(message); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
