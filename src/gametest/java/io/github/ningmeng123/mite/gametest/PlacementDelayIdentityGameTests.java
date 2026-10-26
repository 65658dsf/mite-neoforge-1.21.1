package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.world.PlacementDelay;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Player instances sharing an entity ID must retain independent placement deadlines. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class PlacementDelayIdentityGameTests {
    private PlacementDelayIdentityGameTests() {}

    @GameTest(template = "empty")
    public static void queryingAnEqualPlayerInAnotherLevelCannotErasePlacementCooldown(GameTestHelper helper) {
        ServerPlayer first = player(helper.getLevel());
        ServerPlayer other = player(otherLevel(helper));
        other.setId(first.getId());
        try {
            helper.assertTrue(first != other && first.equals(other),
                    "Separate player instances reproduce the shared entity ID of an integrated client and server");
            PlacementDelay.placed(first);
            helper.assertValueEqual(PlacementDelay.remainingTicks(first), PlacementDelay.DELAY_TICKS,
                    "The successful placement starts its deadline");
            helper.assertValueEqual(PlacementDelay.remainingTicks(other), 0,
                    "An equal player in a different level has no placement deadline of its own");
            helper.assertValueEqual(PlacementDelay.remainingTicks(first), PlacementDelay.DELAY_TICKS,
                    "Reading the other instance cannot clear the original player's deadline");
            helper.assertTrue(PlacementDelay.blocked(first),
                    "The original player remains unable to place again in the same tick");
            helper.succeed();
        } finally {
            PlacementDelay.clear(first);
            PlacementDelay.clear(other);
        }
    }

    @GameTest(template = "empty")
    public static void placingAndClearingAnEqualPlayerCannotChangeAnotherPlacementDeadline(GameTestHelper helper) {
        ServerPlayer first = player(helper.getLevel());
        ServerPlayer other = player(otherLevel(helper));
        other.setId(first.getId());
        PlacementDelay.placed(first);
        helper.runAfterDelay(2, () -> {
            try {
                PlacementDelay.placed(other);
                helper.assertValueEqual(PlacementDelay.remainingTicks(first), PlacementDelay.DELAY_TICKS - 2,
                        "A second instance's placement cannot replace or restart the original deadline");
                helper.assertValueEqual(PlacementDelay.remainingTicks(other), PlacementDelay.DELAY_TICKS,
                        "The second instance starts its own independent deadline");
                PlacementDelay.clear(other);
                helper.assertValueEqual(PlacementDelay.remainingTicks(other), 0,
                        "Clearing the second instance removes only its own deadline");
                helper.assertValueEqual(PlacementDelay.remainingTicks(first), PlacementDelay.DELAY_TICKS - 2,
                        "Clearing an equal instance cannot remove the original deadline");
                helper.succeed();
            } finally {
                PlacementDelay.clear(first);
                PlacementDelay.clear(other);
            }
        });
    }

    @GameTest(template = "empty")
    public static void changingTheEntityIdCannotBypassAnExistingPlacementCooldown(GameTestHelper helper) {
        ServerPlayer player = player(helper.getLevel());
        int originalId = player.getId();
        try {
            PlacementDelay.placed(player);
            player.setId(originalId ^ 0x40000000);
            helper.assertValueEqual(PlacementDelay.remainingTicks(player), PlacementDelay.DELAY_TICKS,
                    "A player instance retains its placement deadline when its entity ID changes");
            helper.assertTrue(PlacementDelay.blocked(player),
                    "Changing an entity ID cannot enable another placement");
            PlacementDelay.clear(player);
            helper.assertValueEqual(PlacementDelay.remainingTicks(player), 0,
                    "The same instance can still clear its deadline after an entity ID change");
            helper.succeed();
        } finally {
            player.setId(originalId);
            PlacementDelay.clear(player);
        }
    }

    private static ServerLevel otherLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(level != null && level != helper.getLevel(), "The fixture has a separate Nether level");
        return level;
    }

    private static ServerPlayer player(ServerLevel level) {
        // These players never enter the level or player list, so sharing an ID cannot
        // disturb real entity lookup tables and no network connection is needed.
        return new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "mite-delay-test"), ClientInformation.createDefault()) {
            @Override public BlockPos adjustSpawnLocation(ServerLevel spawnLevel, BlockPos spawnPos) {
                return spawnPos;
            }
        };
    }
}
