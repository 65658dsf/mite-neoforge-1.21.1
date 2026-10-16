package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.world.LunarWeather;
import io.github.ningmeng123.mite.world.LunarWeatherState;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
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
    public static void nativeWeatherTickTransitionsBloodDayRainToDryNightThunderThenFadesOut(GameTestHelper helper) {
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
            helper.assertFalse(level.getLevelData().isRaining(), "Blood night turns the rain flag off exactly at tick13000");
            helper.assertTrue(level.getLevelData().isThundering(), "The dry blood night retains the thunder flag");
            helper.assertTrue(Math.abs(level.getRainLevel(1F) - .99F) < .0001F, "Rain keeps its native one-hundred-tick fade-out at dusk");
            for (int tick = 0; tick < 100; tick++) advanceWeather(level);
            helper.assertFalse(level.isRaining(), "Settled blood night has no global rain");
            helper.assertTrue(level.isThundering() && level.getThunderLevel(1F) > .9F, "Thunder remains active when settled blood rain reaches zero");
            helper.assertFalse(level.isRainingAt(helper.absolutePos(new BlockPos(1, 310, 1))), "A blood night does not rain on an exposed position");
            level.setDayTime(31L * 24000L + 23000L);
            advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining(), "The last source-night tick23000 remains dry");
            helper.assertTrue(level.getLevelData().isThundering() && level.isThundering(), "The last source-night tick still thunders");
            level.setDayTime(31L * 24000L + 23001L);
            advanceWeather(level);
            helper.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "Source dawn restarts rain exactly at tick23001");
            helper.assertTrue(Math.abs(level.getRainLevel(1F) - .01F) < .0001F, "Dawn rain resumes with the native fade-in");
            level.setDayTime(31L * 24000L + 23999L);
            level.setRainLevel(1F); level.setThunderLevel(1F); advanceWeather(level);
            helper.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "The blood date's last dawn tick remains rainy");
            level.setDayTime(32L * 24000L);
            advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining() || level.getLevelData().isThundering(), "The next ordinary date resumes the independent seed0 weather schedule");
            helper.assertTrue(Math.abs(level.getRainLevel(1F) - .99F) < .0001F, "Rain fades out normally at the next date boundary");
            level.setDayTime(31L * 24000L + 18000L);
            for (int tick = 0; tick < 100; tick++) advanceWeather(level);
            level.setDayTime(31L * 24000L);
            advanceWeather(level);
            helper.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "Jumping from a dry night back to a blood morning restarts rainfall");
            helper.assertTrue(Math.abs(level.getRainLevel(1F) - .01F) < .0001F, "Returning rain starts with the same native fade-in");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blueMoonAndHarvestKeepSourceWeatherButBloodOverridesAFrozenClearCommand(GameTestHelper helper) {
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
            helper.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "Blood daytime rain also overrides frozen clear weather");
            level.setDayTime(31L * 24000L + 13000L);
            advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining(), "Frozen weather cycling still stops rain at blood nightfall");
            helper.assertTrue(level.getLevelData().isThundering(), "Frozen weather cycling still allows the blood night's dry thunder");
            level.setDayTime(127L * 24000L + 18000L); level.setWeatherParameters(0, 1000, true, true);
            advanceWeather(level);
            helper.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "An administrator may retain manual storms during blue moon when cycling is disabled");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstThreeBloodDatesRainByDayAndThunderDryAtNightButFourthDateIsBlue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, level.getServer());
            for (long day : new long[]{32, 64, 96}) for (long tick : new long[]{0, 6000, 12999, 13000, 18000, 23000, 23001, 23999}) {
                level.setDayTime((day - 1L) * 24000L + tick);
                level.setWeatherParameters(1000, 0, false, false);
                advanceWeather(level);
                helper.assertTrue(level.getLevelData().isRaining() == (tick < 13000 || tick > 23000),
                        "Blood rain follows the day/night boundary: day" + day + " tick" + tick);
                helper.assertTrue(level.getLevelData().isThundering(),
                        "Thunder spans the entire actual blood date: day" + day + " tick" + tick);
            }
            level.setDayTime(127L * 24000L + 18000L); advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining() || level.getLevelData().isThundering(),
                    "The128th date is blue, so neither blood daytime rain nor dry night thunder leaks into it");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frozenWeatherIsRestoredAfterSavedBloodStateAndForwardOrBackwardTimeJumps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
            level.setWeatherParameters(9000, 0, false, false);
            level.setDayTime(31L * 24000L + 18000L); advanceWeather(level);
            CompoundTag encoded = LunarWeatherState.get(level).save(new CompoundTag(), level.registryAccess());
            helper.assertTrue(encoded.getBoolean("blood_storm_active"), "A real SavedData payload retains the active override across restart");
            level.getDataStorage().set(LunarWeatherState.NAME, LunarWeatherState.load(encoded, level.registryAccess()));
            level.setWeatherParameters(4000, 0, false, false); advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining(), "The restored blood-night override keeps the rain flag off");
            helper.assertTrue(level.getLevelData().isThundering(),
                    "A restored override still forces dry blood-night thunder after a clear command");
            level.setDayTime(127L * 24000L); advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining() || level.getLevelData().isThundering(),
                    "Jumping out restores the frozen weather saved before blood, even after state reload");
            helper.assertValueEqual(((ServerLevelData)level.getLevelData()).getClearWeatherTime(), 9000,
                    "Frozen pre-blood command duration is restored rather than permanently forcing rain");
            level.setDayTime(31L * 24000L + 18000L); advanceWeather(level);
            level.setDayTime(30L * 24000L + 6000L); advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining() || level.getLevelData().isThundering(),
                    "A backwards time command also releases the dry-night override");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frozenWetWeatherRestoresAfterDryBloodNightButANewCommandTakesPrecedence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
            level.setWeatherParameters(0, 8100, true, false);
            level.setDayTime(31L * 24000L + 18000L); advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining(), "Entering blood night suspends an existing ordinary rainstorm");
            helper.assertTrue(level.getLevelData().isThundering(), "Entering blood night still forces thunder");
            CompoundTag encoded = LunarWeatherState.get(level).save(new CompoundTag(), level.registryAccess());
            helper.assertTrue(encoded.getBoolean("raining") && !encoded.getBoolean("thundering"), "SavedData retains the wet non-thundering weather suspended by a dry night");
            level.getDataStorage().set(LunarWeatherState.NAME, LunarWeatherState.load(encoded, level.registryAccess()));
            level.setDayTime(32L * 24000L); advanceWeather(level);
            helper.assertTrue(level.getLevelData().isRaining() && !level.getLevelData().isThundering(), "Leaving saved dry thunder restores the original frozen rainstorm");
            helper.assertValueEqual(((ServerLevelData)level.getLevelData()).getRainTime(), 8100, "The suspended rainstorm's duration survives the dry night and state reload");
            level.setDayTime(31L * 24000L + 18000L); advanceWeather(level);
            level.setDayTime(32L * 24000L);
            level.setWeatherParameters(5000, 0, false, false); advanceWeather(level);
            helper.assertFalse(level.getLevelData().isRaining() || level.getLevelData().isThundering(), "An explicit clear command issued after leaving blood overrides the suspended rainstorm");
            helper.assertValueEqual(((ServerLevelData)level.getLevelData()).getClearWeatherTime(), 5000, "A new administrator command keeps its own frozen duration");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void settledBloodDayAndDryNightDoNotBroadcastUnchangedWeatherEveryTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "blood-weather-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            for (long time : new long[]{6000, 18000}) {
                level.setDayTime(31L * 24000L + time);
                level.setRainLevel(time == 6000 ? 1F : 0F); level.setThunderLevel(1F); advanceWeather(level);
                helper.assertTrue(level.isRaining() == (time == 6000), "The packet stability check starts with settled rain matching the lunar time " + time);
                helper.assertTrue(level.isThundering(), "The packet stability check exercises active thunder at " + time);
                channel.runPendingTasks(); channel.flushOutbound();
                while (channel.readOutbound() != null) { /* Clear native login and first weather snapshot. */ }
                for (int tick = 0; tick < 20; tick++) advanceWeather(level);
                channel.runPendingTasks(); channel.flushOutbound();
                Object packet;
                while ((packet = channel.readOutbound()) != null) helper.assertTrue(!(packet instanceof ClientboundGameEventPacket),
                        "Steady blood weather does not spam redundant native weather packets at " + time);
            }
        } finally {
            level.getServer().getPlayerList().remove(player); channel.finishAndReleaseAll();
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
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 2);
            level.setDayTime(31L * 24000L + 18000L);
            level.setRainLevel(0F); level.setThunderLevel(1F); advanceWeather(level);
            helper.assertFalse(level.isRainingAt(pos.above()), "Exposed desert cauldrons receive no rain during the dry blood night");
            level.getRandom().setSeed(seed);
            level.tickPrecipitation(pos);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.CAULDRON), "Native precipitation does not fill cauldrons merely because dry thunder is active");
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
        var shelter = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "lunar-bed-test"));
        player.setGameMode(GameType.CREATIVE);
        try (WeatherSnapshot saved = new WeatherSnapshot(level)) {
            // This is a lunar restriction test; provide the shelter required by ordinary R196 sleep.
            for (BlockPos mutable : BlockPos.betweenClosed(foot.offset(-1,-1,-1),foot.offset(2,2,1))) {
                BlockPos pos=mutable.immutable(); shelter.put(pos,level.getBlockState(pos));
                boolean wall=pos.getX()==foot.getX()-1||pos.getX()==foot.getX()+2
                        ||pos.getY()==foot.getY()-1||pos.getY()==foot.getY()+2||pos.getZ()!=foot.getZ();
                level.setBlock(pos,wall?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            level.setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 2); level.setBlock(head.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(foot.above(), Blocks.AIR.defaultBlockState(), 2); level.setBlock(head.above(), Blocks.AIR.defaultBlockState(), 2);
            var state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
            level.setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), 2);
            player.setPos(foot.getX() + .5, foot.getY(), foot.getZ() + .5);
            helper.assertFalse(io.github.ningmeng123.mite.worldgen.R196OutdoorChecks.outdoors(level,head),
                    "The actual lunar test bed is sheltered under the shared source outdoor check");
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
            for(var entry:shelter.entrySet())level.setBlock(entry.getKey(),entry.getValue(),2);
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
        final CompoundTag bloodWeather;
        WeatherSnapshot(ServerLevel level) {
            this.level = level; data = (ServerLevelData)level.getLevelData(); time = level.getDayTime();
            bloodWeather = LunarWeatherState.get(level).save(new CompoundTag(), level.registryAccess());
            rainTime = data.getRainTime(); thunderTime = data.getThunderTime(); clearTime = data.getClearWeatherTime();
            rain = data.isRaining(); thunder = data.isThundering(); cycle = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
            rainStrength = level.getRainLevel(1F); level.setRainLevel(1F); thunderStrength = level.getThunderLevel(1F); level.setRainLevel(rainStrength);
        }
        @Override public void close() {
            level.getDataStorage().set(LunarWeatherState.NAME, LunarWeatherState.load(bloodWeather, level.registryAccess()));
            level.setDayTime(time); data.setRainTime(rainTime); data.setThunderTime(thunderTime); data.setClearWeatherTime(clearTime);
            data.setRaining(rain); data.setThundering(thunder); level.setRainLevel(rainStrength); level.setThunderLevel(thunderStrength);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(cycle, level.getServer()); level.updateSkyBrightness();
        }
    }
}
