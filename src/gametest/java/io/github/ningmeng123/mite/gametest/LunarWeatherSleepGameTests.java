package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.world.LunarWeather;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Immediate real weather/bed checks always restore global clocks and weather before returning. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class LunarWeatherSleepGameTests {
    private LunarWeatherSleepGameTests() {}

    @GameTest(template = "empty")
    public static void nativeWeatherTickForcesBloodStormThenFadesOutAtTheSourceBoundary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, level.getServer());
            level.setDayTime(31L * 24000L);
            level.setRainLevel(0F); level.setThunderLevel(0F);
            advanceWeather(level);
            helper.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "The native weather tick receives the forced blood-period storm flags");
            helper.assertTrue(Math.abs(level.getRainLevel(1F) - .01F) < .0001F, "Native one-hundred-tick rain fade-in is retained");
            level.setDayTime(31L * 24000L + 12999L);
            level.setRainLevel(1F); level.setThunderLevel(1F);
            advanceWeather(level);
            helper.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "Tick12999 remains inside the storm");
            level.setDayTime(31L * 24000L + 13000L);
            advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining() || level.getLevelData().isThundering(), "Tick13000 is outside both half-open event intervals");
            helper.assertTrue(Math.abs(level.getRainLevel(1F) - .99F) < .0001F, "The last weather does not disappear instantly at the boundary");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blueMoonAndHarvestNightClearNaturalRainWhileDisabledCyclePreservesCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, level.getServer());
            for (long time : new long[]{127L * 24000L, 127L * 24000L + 18000L, 23L * 24000L + 13000L, 23L * 24000L + 23000L}) {
                level.setDayTime(time); level.setWeatherParameters(0, 6000, true, true);
                advanceWeather(level);
                helper.assertFalse(level.getLevelData().isRaining() || level.getLevelData().isThundering(), "Source protected interval clears ordinary weather at " + time);
            }
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
            level.setDayTime(31L * 24000L + 6000L); level.setWeatherParameters(1000, 0, false, false);
            advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining() || level.getLevelData().isThundering(), "An administrator may freeze clear weather even during blood day");
            level.setDayTime(127L * 24000L + 18000L); level.setWeatherParameters(0, 1000, true, true);
            advanceWeather(level);
            helper.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "An administrator may retain manual storms during blue moon when cycling is disabled");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualDesertRainRespectsTheRoofAndReachesNativeCauldronPrecipitation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos reference = helper.absolutePos(BlockPos.ZERO);
        BlockPos pos = new BlockPos((reference.getX() & ~15) + 8, 296, (reference.getZ() & ~15) + 8);
        var chunk = level.getChunkAt(pos);
        var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        var originalBiomes = section.getBiomes();
        int quartX = net.minecraft.core.QuartPos.fromBlock(chunk.getPos().getMinBlockX());
        int quartY = net.minecraft.core.QuartPos.fromBlock(pos.getY()) & ~3;
        int quartZ = net.minecraft.core.QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        var sampler = level.getChunkSource().randomState().sampler();
        var original = level.getBlockState(pos); var originalRoof = level.getBlockState(pos.above(2));
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            // Native biome lookup fuzzes the sample into adjacent quart cells. Cover and restore the
            // entire isolated section instead of relying on one cell at an arbitrary test-grid edge.
            var desert = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
            section.fillBiomesFromNoise((x,y,z,climate) -> desert, sampler, quartX, quartY, quartZ);
            helper.assertTrue(level.getBiome(pos.above()).is(Biomes.DESERT), "Every fuzzy precipitation sample is the intended dry biome");
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 2);
            level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 2);
            level.setDayTime(31L * 24000L + 6000L);
            level.setWeatherParameters(0, 6000, true, true); level.setRainLevel(1F);
            helper.assertTrue(level.isRainingAt(pos.above()), "The real Level.isRainingAt treats a blood-period desert as rainy");
            level.setBlock(pos.above(2), Blocks.STONE.defaultBlockState(), 2);
            helper.assertFalse(level.isRainingAt(pos.above()), "The native sky and height checks still protect a covered position");
            level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 2);
            long seed = 0;
            for (; seed < 10000; seed++) if (net.minecraft.util.RandomSource.create(seed).nextFloat() < .05F) break;
            helper.assertTrue(seed < 10000, "A native cauldron five-percent success draw exists");
            level.getRandom().setSeed(seed);
            level.tickPrecipitation(pos);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.WATER_CAULDRON), "Actual tickPrecipitation fills the cauldron from desert blood rain");
            level.setDayTime(30L * 24000L + 6000L);
            helper.assertFalse(level.isRainingAt(pos.above()), "The same dry biome remains rainless outside blood periods");
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        finally {
            level.setBlock(pos, original, 2); level.setBlock(pos.above(2), originalRoof, 2);
            section.fillBiomesFromNoise((x,y,z,climate) -> originalBiomes.get(x & 3,y & 3,z & 3), sampler, quartX, quartY, quartZ);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodSleepDenialUsesActualServerBedAndDoesNotAffectOtherMoonNights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(0, 1, 0)), head = foot.east();
        var oldFoot = level.getBlockState(foot); var oldHead = level.getBlockState(head);
        var oldFloor = level.getBlockState(foot.below()); var oldHeadFloor = level.getBlockState(head.below());
        var oldFootAir = level.getBlockState(foot.above()); var oldHeadAir = level.getBlockState(head.above());
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "lunar-bed-test"));
        player.setGameMode(GameType.CREATIVE);
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 2); level.setBlock(head.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(foot.above(), Blocks.AIR.defaultBlockState(), 2); level.setBlock(head.above(), Blocks.AIR.defaultBlockState(), 2);
            var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
            level.setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), 2);
            player.setPos(foot.getX() + .5, foot.getY(), foot.getZ() + .5);
            for (long offset : new long[]{0, 13000, 18000, 23000, 23999}) {
                level.setDayTime(31L * 24000L + offset); level.updateSkyBrightness();
                var result = player.startSleepInBed(head);
                helper.assertTrue(result.left().orElse(null) == Player.BedSleepingProblem.OTHER_PROBLEM && !player.isSleeping(), "Blood-period bed refuses actual sleep at " + offset);
            }
            helper.assertTrue(head.equals(player.getRespawnPosition()), "Native bed validation still sets the respawn position");
            for (long day : new long[]{23, 119, 127, 30}) {
                level.setDayTime(day * 24000L + 18000L); level.updateSkyBrightness();
                var result = player.startSleepInBed(head);
                helper.assertTrue(result.right().isPresent() && player.isSleeping(), "Harvest, moon-dog, blue and ordinary nights retain native sleep at day index " + day);
                player.stopSleepInBed(true, true);
            }
        } finally {
            if (player.isSleeping()) player.stopSleepInBed(true, true);
            level.setBlock(foot, oldFoot, 2); level.setBlock(head, oldHead, 2);
            level.setBlock(foot.below(), oldFloor, 2); level.setBlock(head.below(), oldHeadFloor, 2);
            level.setBlock(foot.above(), oldFootAir, 2); level.setBlock(head.above(), oldHeadAir, 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ongoingServerSleepIsStoppedOnBloodPeriodAndNonOverworldRainStaysNative(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "lunar-wake-test"));
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.setDayTime(31L * 24000L + 18000L);
            var denied = new CanContinueSleepingEvent(player, null); NeoForge.EVENT_BUS.post(denied);
            helper.assertFalse(denied.mayContinueSleeping(), "The real per-tick sleep event stops a pre-existing server sleeper");
            level.setDayTime(127L * 24000L + 18000L);
            var allowed = new CanContinueSleepingEvent(player, null); NeoForge.EVENT_BUS.post(allowed);
            helper.assertTrue(allowed.mayContinueSleeping(), "Blue moon does not impose blood sleep restrictions");
            var desert = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT).value();
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            long previous = nether.getDayTime();
            try {
                nether.setDayTime(31L * 24000L + 6000L);
                helper.assertTrue(LunarWeather.precipitation(nether, desert, BlockPos.ZERO, Biome.Precipitation.NONE) == Biome.Precipitation.NONE, "A non-overworld dimension cannot acquire blood precipitation");
            } finally { nether.setDayTime(previous); }
        }
        helper.succeed();
    }

    private static void advanceWeather(ServerLevel level) {
        try { Method method = ServerLevel.class.getDeclaredMethod("advanceWeatherCycle"); method.setAccessible(true); method.invoke(level); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static final class WeatherSnapshot implements AutoCloseable {
        final ServerLevel level; final ServerLevelData data;
        final long time; final int rainTime, thunderTime, clearTime; final boolean rain, thunder, cycle;
        final float rainStrength, thunderStrength;
        WeatherSnapshot(ServerLevel level) {
            this.level = level; data = (ServerLevelData)level.getLevelData(); time = level.getDayTime();
            rainTime = data.getRainTime(); thunderTime = data.getThunderTime(); clearTime = data.getClearWeatherTime();
            rain = data.isRaining(); thunder = data.isThundering(); cycle = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
            rainStrength = level.getRainLevel(1F); level.setRainLevel(1F); thunderStrength = level.getThunderLevel(1F); level.setRainLevel(rainStrength);
        }
        @Override public void close() {
            level.setDayTime(time); data.setRainTime(rainTime); data.setThunderTime(thunderTime); data.setClearWeatherTime(clearTime);
            data.setRaining(rain); data.setThundering(thunder); level.setRainLevel(rainStrength); level.setThunderLevel(thunderStrength);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(cycle, level.getServer()); level.updateSkyBrightness();
        }
    }
}
