package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.campfire.CampfireController;
import io.github.ningmeng123.mite.campfire.CampfireEnvironment;
import io.github.ningmeng123.mite.campfire.InvestigateCampfireGoal;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded fire discovery, radius-limited warmth and genuine navigation/fire placement. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CampfireEnvironmentGameTests {
    private CampfireEnvironmentGameTests() {}

    @GameTest(template = "empty")
    public static void bothCampfiresPreventFreezingWithinThreeBlocksButNeverWarmBeyondTheBoundary(GameTestHelper helper) {
        for (Block block : campfires()) try (var f = new Fixture(helper, block); var session = new PlayerSession(f.level)) {
            var player = session.player;
            player.moveTo(f.fire.getCenter().add(3D, 0D, 0D));
            player.setTicksFrozen(100);
            CampfireEnvironment.tick(f.level, f.fire);
            helper.assertValueEqual(player.getTicksFrozen(), 0, "A fueled fire clears freezing at the exact three-block boundary");
            player.moveTo(f.fire.getCenter().add(3.01D, 0D, 0D));
            player.setTicksFrozen(100);
            CampfireEnvironment.tick(f.level, f.fire);
            helper.assertValueEqual(player.getTicksFrozen(), 100, "Fire cannot warm players outside its stated radius");
            f.extinguish(f.fire);
            player.moveTo(f.fire.getCenter().add(2D, 0D, 0D));
            CampfireEnvironment.tick(f.level, f.fire);
            helper.assertValueEqual(player.getTicksFrozen(), 100, "Stored fuel in an unlit fire supplies no warmth");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theLoadedFireIndexFindsTheNearestBurningSourceAndImmediatelyRejectsExtinguishedOnes(GameTestHelper helper) {
        try (var f = new Fixture(helper, Blocks.CAMPFIRE)) {
            BlockPos second = f.fire.east(6);
            f.addFire(second, Blocks.SOUL_CAMPFIRE);
            CampfireEnvironment.tick(f.level, f.fire);
            CampfireEnvironment.tick(f.level, second);
            Vec3 origin = f.fire.getCenter().add(1D, 0D, 0D);
            helper.assertValueEqual(CampfireEnvironment.findNearestFire(f.level, origin, 16D), f.fire,
                    "The nearer of two actual burning block entities is selected");
            f.extinguish(f.fire);
            helper.assertValueEqual(CampfireEnvironment.findNearestFire(f.level, origin, 16D), second,
                    "An extinguished source is rejected before its index timeout");
            helper.assertTrue(CampfireEnvironment.findNearestFire(f.level, origin, 4D) == null,
                    "A still-burning source outside the requested radius is ignored");
            f.extinguish(second);
            helper.assertTrue(CampfireEnvironment.findNearestFire(f.level, origin, 16D) == null,
                    "No stale entry keeps attracting monsters after both fires go out");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aMonsterWithAnActualCombatTargetNeverStartsInvestigatingFirelight(GameTestHelper helper) {
        try (var f = new Fixture(helper, Blocks.CAMPFIRE)) {
            Zombie zombie = f.zombie();
            var goal = f.goal(zombie);
            var prey = helper.makeMockPlayer(GameType.SURVIVAL);
            prey.moveTo(zombie.position().add(2D, 0D, 0D));
            zombie.setTarget(prey);
            helper.assertFalse(goal.canUse(), "The installed goal never takes movement away from combat");
            helper.assertTrue(goal.targetFire() == null, "Combat does not queue a hidden campfire target");
            zombie.setTarget(null);
            zombie.tickCount += 1000;
            helper.assertTrue(goal.canUse(), "The same monster can inspect the real fire after combat ends");
            goal.start();
            zombie.setTarget(prey);
            helper.assertFalse(goal.canContinueToUse(), "A new combat target interrupts an in-progress fire investigation");
            goal.stop();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void idleMonstersUseARealSafePathStopOutsideTheFireAndCancelWhenItGoesOut(GameTestHelper helper) {
        try (var f = new Fixture(helper, Blocks.CAMPFIRE)) {
            Zombie zombie = f.zombie();
            var goal = f.goal(zombie);
            helper.assertTrue(goal.canUse(), "An idle monster discovers the visible indexed fire with a reachable standing site");
            helper.assertValueEqual(goal.targetFire(), f.fire, "The event-installed goal chooses the actual fueled source");
            goal.start();
            var path = zombie.getNavigation().getPath();
            helper.assertTrue(path != null && path.canReach(), "The investigation uses an actual native reachable path");
            BlockPos destination = path.getEndNode().asBlockPos();
            double horizontalSquared = Math.pow(destination.getX() - f.fire.getX(), 2D)
                    + Math.pow(destination.getZ() - f.fire.getZ(), 2D);
            helper.assertTrue(horizontalSquared >= 4D, "The destination is at least two blocks horizontally from the flames");
            zombie.moveTo(Vec3.atBottomCenterOf(destination));
            goal.tick();
            helper.assertTrue(zombie.getNavigation().isDone(), "A monster reaching the viewing distance stops instead of entering the fire");
            helper.assertTrue(goal.targetFire() != null && !zombie.isOnFire(), "Looking at the flames preserves a safe idle investigation");
            f.extinguish(f.fire);
            helper.assertFalse(goal.canContinueToUse(), "Extinguishing the source immediately invalidates its investigation");
            goal.tick();
            helper.assertTrue(goal.targetFire() == null, "The cancelled goal releases its target and native navigation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nearbyHayAndWoodCanIgniteOnlyWhenTheVanillaFireRuleAllowsIt(GameTestHelper helper) {
        for (Block source : new Block[]{Blocks.HAY_BLOCK, Blocks.OAK_PLANKS}) try (var f = new Fixture(helper, Blocks.CAMPFIRE)) {
            f.set(f.fire.east(), source.defaultBlockState());
            f.fireTick(false);
            helper.assertFalse(CampfireEnvironment.igniteNeighbour(f.level, f.fire), "Disabled doFireTick blocks the entire accidental ignition operation");
            helper.assertValueEqual(f.nearbyFlames(), 0L, "Disabling fire ticks creates no new flame blocks");
            f.fireTick(true);
            helper.assertTrue(CampfireEnvironment.igniteNeighbour(f.level, f.fire), "A permitted ignition attempt finds loaded air next to the real combustible neighbour");
            helper.assertValueEqual(f.nearbyFlames(), 1L, "A single successful attempt places one ordinary fire, rather than deleting its fuel");
            helper.assertTrue(f.level.getBlockState(f.fire.east()).is(source), "Starting the risk never directly destroys the hay or wooden block");
        }
        helper.succeed();
    }

    private static Block[] campfires() { return new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}; }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final BlockPos fire;
        final ServerLevelData data;
        final long gameTime, dayTime;
        final boolean oldFireTick, raining, thundering;
        final int clearTime, rainTime, thunderTime;
        final float rain, oldRain, thunder, oldThunder;
        final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        final List<BlockPos> firePositions = new ArrayList<>();
        final List<Zombie> zombies = new ArrayList<>();

        Fixture(GameTestHelper helper, Block block) {
            level = helper.getLevel(); data = (ServerLevelData) level.getLevelData();
            fire = helper.absolutePos(new BlockPos(8, 4, 4));
            gameTime = level.getGameTime(); dayTime = level.getDayTime();
            oldFireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
            clearTime = data.getClearWeatherTime(); rainTime = data.getRainTime(); thunderTime = data.getThunderTime();
            raining = data.isRaining(); thundering = data.isThundering();
            rain = level.rainLevel; oldRain = level.oRainLevel; thunder = level.thunderLevel; oldThunder = level.oThunderLevel;
            data.setGameTime(gameTime - Math.floorMod(gameTime, 20L));
            level.setDayTime(18000L);
            level.setWeatherParameters(6000, 0, false, false);
            level.setRainLevel(0F); level.setThunderLevel(0F);
            fireTick(false);
            for (BlockPos pos : BlockPos.betweenClosed(fire.offset(-7, -1, -3), fire.offset(7, 3, 3)))
                set(pos, pos.getY() == fire.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            addFire(fire, block);
        }

        void set(BlockPos pos, BlockState state) {
            originals.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, 2);
        }

        void addFire(BlockPos pos, Block block) {
            set(pos, block.defaultBlockState().setValue(CampfireBlock.LIT, false));
            set(pos.above(3), Blocks.STONE.defaultBlockState());
            firePositions.add(pos);
            CampfireController.refuel((CampfireBlockEntity) level.getBlockEntity(pos), 3, true);
            CampfireEnvironment.tick(level, pos);
        }

        void extinguish(BlockPos pos) { set(pos, level.getBlockState(pos).setValue(CampfireBlock.LIT, false)); }
        void fireTick(boolean enabled) { level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(enabled, level.getServer()); }
        long nearbyFlames() {
            return originals.keySet().stream().filter(pos -> level.getBlockState(pos).is(Blocks.FIRE)
                    || level.getBlockState(pos).is(Blocks.SOUL_FIRE)).count();
        }

        Zombie zombie() {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            zombie.moveTo(Vec3.atBottomCenterOf(fire.west(6)));
            zombie.setNoGravity(true); zombie.setOnGround(true); zombie.setPersistenceRequired();
            level.addFreshEntity(zombie); zombies.add(zombie);
            return zombie;
        }

        InvestigateCampfireGoal goal(Zombie zombie) {
            zombie.tickCount += 1000;
            return zombie.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(InvestigateCampfireGoal.class::isInstance).map(InvestigateCampfireGoal.class::cast).findFirst().orElseThrow();
        }

        @Override public void close() {
            for (Zombie zombie : zombies) zombie.discard();
            for (BlockPos pos : firePositions) CampfireEnvironment.forget(level, pos);
            originals.forEach((pos, state) -> level.setBlock(pos, state, 2));
            data.setGameTime(gameTime); level.setDayTime(dayTime); fireTick(oldFireTick);
            data.setClearWeatherTime(clearTime); data.setRainTime(rainTime); data.setThunderTime(thunderTime);
            data.setRaining(raining); data.setThundering(thundering);
            level.rainLevel = rain; level.oRainLevel = oldRain; level.thunderLevel = thunder; level.oThunderLevel = oldThunder;
            level.updateSkyBrightness();
        }
    }

    private static final class PlayerSession implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        PlayerSession(ServerLevel level) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "campfire-warmth"), false);
            player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setHealth(player.getMaxHealth());
        }
        @Override public void close() {
            try { player.server.getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
        }
    }
}
