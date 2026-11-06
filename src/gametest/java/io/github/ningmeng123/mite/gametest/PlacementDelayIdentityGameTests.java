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

/** Server players leave R196 input timing to their clients. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class PlacementDelayIdentityGameTests {
    private PlacementDelayIdentityGameTests() {}

    @GameTest(template = "empty")
    public static void equalServerPlayersCannotCreateClientInputDeadlines(GameTestHelper helper) {
        ServerPlayer first = player(helper.getLevel());
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The fixture has a separate Nether level");
        ServerPlayer other = player(nether);
        other.setId(first.getId());
        try {
            helper.assertTrue(first != other && first.equals(other), "The fixture reproduces shared network IDs");
            PlacementDelay.placed(first);
            PlacementDelay.bucketPlaced(other);
            helper.assertValueEqual(PlacementDelay.remainingMillis(first), 0L, "Server block placement leaves input timing to the client");
            helper.assertValueEqual(PlacementDelay.remainingMillis(other), 0L, "Server bucket placement leaves input timing to the client");
            PlacementDelay.clear(other);
            helper.assertFalse(PlacementDelay.blocked(first), "Clearing another instance leaves server requests available");
            helper.succeed();
        } finally {
            PlacementDelay.clear(first);
            PlacementDelay.clear(other);
        }
    }

    @GameTest(template = "empty")
    public static void changingAServerEntityIdCannotCreateAnInputLock(GameTestHelper helper) {
        ServerPlayer player = player(helper.getLevel());
        int originalId = player.getId();
        try {
            PlacementDelay.blockPlaced(player, helper.getLevel());
            player.setId(originalId ^ 0x40000000);
            PlacementDelay.bucketPlaced(player);
            helper.assertValueEqual(PlacementDelay.remainingTicks(player), 0, "Server identity changes leave input timing on the client");
            helper.assertFalse(PlacementDelay.placementBlocked(player), "Valid server requests can arrive in the same tick");
            helper.succeed();
        } finally {
            player.setId(originalId);
            PlacementDelay.clear(player);
        }
    }

    @GameTest(template = "empty")
    public static void serverHungerValidationIsIndependentOfClientInputState(GameTestHelper helper) {
        ServerPlayer player = player(helper.getLevel());
        try {
            player.getFoodData().setFoodLevel(0);
            PlacementDelay.placed(player);
            PlacementDelay.clear(player);
            helper.assertTrue(PlacementDelay.placementBlocked(player), "A hungry survival player still fails server validation");
            player.getFoodData().setFoodLevel(1);
            helper.assertFalse(PlacementDelay.placementBlocked(player), "Restored hunger permits the next valid request");
            player.getAbilities().instabuild = true;
            player.getFoodData().setFoodLevel(0);
            helper.assertFalse(PlacementDelay.placementBlocked(player), "Creative server use retains its hunger exemption");
            helper.succeed();
        } finally {
            PlacementDelay.clear(player);
        }
    }

    private static ServerPlayer player(ServerLevel level) {
        return new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "mite-delay-test"), ClientInformation.createDefault()) {
            @Override public BlockPos adjustSpawnLocation(ServerLevel spawnLevel, BlockPos spawnPos) {
                return spawnPos;
            }
        };
    }
}
