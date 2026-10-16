package io.github.ningmeng123.mite.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.curse.WitchCurseData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Witch;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real operator commands make the manual curse test procedure reproducible. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WitchCurseCommandGameTests {
    @GameTest(template = "empty")
    public static void operatorCommandsApplyInspectAndClearWithoutOverwritingExistingCurses(GameTestHelper helper) throws CommandSyntaxException {
        var player = helper.makeMockServerPlayerInLevel();
        Witch witch = EntityType.WITCH.create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(2, 5, 2));
        player.moveTo(pos.getX(), pos.getY(), pos.getZ());
        witch.moveTo(pos.getX() + 2, pos.getY(), pos.getZ());
        witch.setNoAi(true);
        helper.getLevel().addFreshEntity(witch);
        var dispatcher = player.server.getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        try {
            try {
                dispatcher.execute("mite curse apply @s cannot_run", source.withPermission(0));
                throw new AssertionError("A normal player must not apply administrator curses");
            } catch (CommandSyntaxException expected) { /* permission rejected before scheduling */ }
            helper.assertValueEqual(dispatcher.execute("mite curse apply @s cannot_run", source), 1, "The documented no-delay syntax schedules a curse");
            helper.assertTrue(PlayerCurses.has(player, R196Curse.CANNOT_RUN), "The manual diagnostic command realizes the selected effect");
            helper.assertValueEqual(dispatcher.execute("mite curse status @s", source), R196Curse.CANNOT_RUN.id(), "Status returns the authoritative curse identity");
            helper.assertFalse(PlayerCurses.state(player).known(), "Inspecting administrator diagnostics does not mark the effect discovered");
            try {
                dispatcher.execute("mite curse apply @s cannot_drink", source);
                throw new AssertionError("An existing curse must not be silently replaced");
            } catch (CommandSyntaxException expected) { /* existing entry preserved */ }
            helper.assertTrue(PlayerCurses.has(player, R196Curse.CANNOT_RUN), "A rejected second command preserves the original curse");
            dispatcher.execute("mite curse clear @s", source);
            helper.assertFalse(PlayerCurses.state(player).active(), "Clear removes the realized state");
            dispatcher.execute("mite curse apply @s cannot_drink 6000", source);
            helper.assertFalse(PlayerCurses.state(player).active(), "The delayed command does not expose the pending curse");
            helper.assertValueEqual(WitchCurseData.get(player.serverLevel()).entry(player.getUUID()).realizationTime()
                    - player.server.overworld().getGameTime(), 6000L, "The optional delay is an exact number of game ticks");
            dispatcher.execute("mite curse clear @s", source);
            helper.assertTrue(WitchCurseData.get(player.serverLevel()).entry(player.getUUID()) == null, "Clear also removes a pending curse");
            var mite = dispatcher.getRoot().getChild("mite");
            helper.assertTrue(mite.getChild("food") != null && mite.getChild("clearblocks") != null,
                    "Registering curse diagnostics preserves earlier command subtrees");
        } finally {
            PlayerCurses.cure(player);
            witch.discard();
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
