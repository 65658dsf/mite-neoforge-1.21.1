package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.world.PlacementDelay;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Exercises the transformed client timer with constructor-free fixtures. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PlacementDelayClientAudit implements DataProvider {
    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (event.includeClient() && Boolean.getBoolean("mite.bucketPlacementClientAudit")) {
            event.getGenerator().addProvider(true, new PlacementDelayClientAudit());
        }
    }

    @Override public String getName() { return "R196 client placement deadlines and player identity"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        try {
            ClientLevel firstLevel = levelFixture();
            ClientLevel otherLevel = levelFixture();
            FixturePlayer first = playerFixture(firstLevel);
            FixturePlayer other = playerFixture(otherLevel);
            first.setId(41);
            other.setId(41);
            try {
                check(first != other && first.equals(other), "Fixture players share a network ID");
                check(PlacementDelay.remainingMillis(first) == 0L, "First placement is immediately available");
                PlacementDelay.placed(first);
                checkBlockDeadline(first);
                check(PlacementDelay.remainingMillis(other) == 0L, "Another instance has its own input state");
                checkBlockDeadline(first);
                PlacementDelay.bucketPlaced(other);
                long bucketRemaining = PlacementDelay.remainingMillis(other);
                check(bucketRemaining > 250L && bucketRemaining <= 500L, "Buckets use the 500ms deadline");
                checkBlockDeadline(first);
                PlacementDelay.clear(other);
                checkBlockDeadline(first);
                first.setId(42);
                checkBlockDeadline(first);
                first.fixtureLevel = otherLevel;
                check(PlacementDelay.remainingMillis(first) == 0L, "Changing level clears the old input lock");

                first.creative = true;
                first.food.setFoodLevel(0);
                PlacementDelay.placed(first);
                checkBlockDeadline(first);
                check(PlacementDelay.placementBlocked(first), "Creative input also waits after placement");
                PlacementDelay.clear(first);
                check(!PlacementDelay.placementBlocked(first), "Creative placement is exempt from hunger");
                first.creative = false;
                check(PlacementDelay.placementBlocked(first), "Survival placement still requires hunger");
                first.food.setFoodLevel(1);
                check(!PlacementDelay.placementBlocked(first), "Restored hunger allows a first placement");
                first.spectator = true;
                PlacementDelay.placed(first);
                check(!PlacementDelay.blocked(first), "Spectator state does not create a placement lock");
            } finally {
                PlacementDelay.clear(first);
                PlacementDelay.clear(other);
            }
            JsonObject report = new JsonObject();
            report.addProperty("author", "NingMeng123");
            report.addProperty("status", "passed");
            report.addProperty("block_millis", 250);
            report.addProperty("bucket_millis", 500);
            report.addProperty("client_identity_and_level_isolation", true);
            report.addProperty("creative_input_and_survival_hunger", true);
            report.addProperty("game_window_created", false);
            Path output = Path.of(System.getProperty("mite.bucketPlacementClientAuditOutput",
                    "build/verification/bucket-client-audit.json")).toAbsolutePath()
                    .resolveSibling("placement-delay-client-audit.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            return CompletableFuture.completedFuture(null);
        } catch (Exception failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void checkBlockDeadline(Player player) {
        long remaining = PlacementDelay.remainingMillis(player);
        check(remaining > 0L && remaining <= 250L && PlacementDelay.blocked(player),
                "The original 250ms block deadline remains active");
    }

    private static ClientLevel levelFixture() throws Exception {
        ClientLevel level = allocate(ClientLevel.class);
        Field clientSide = Level.class.getDeclaredField("isClientSide");
        clientSide.setAccessible(true);
        clientSide.setBoolean(level, true);
        return level;
    }

    private static FixturePlayer playerFixture(Level level) throws Exception {
        FixturePlayer player = allocate(FixturePlayer.class);
        player.fixtureLevel = level;
        player.food = new FoodData();
        return player;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class FixturePlayer extends Player {
        private Level fixtureLevel;
        private FoodData food;
        private boolean creative;
        private boolean spectator;

        // Allocated without this constructor, so no world, connection or renderer is created.
        private FixturePlayer(Level level, GameProfile profile) { super(level, BlockPos.ZERO, 0F, profile); }
        @Override public Level level() { return fixtureLevel; }
        @Override public boolean isCreative() { return creative; }
        @Override public boolean hasInfiniteMaterials() { return creative; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public FoodData getFoodData() { return food; }
    }
}
