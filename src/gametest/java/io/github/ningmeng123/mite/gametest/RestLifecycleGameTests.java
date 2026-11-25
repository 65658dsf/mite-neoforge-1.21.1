package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.advancement.MiteAchievements;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.github.ningmeng123.mite.survival.PlayerRest;
import io.github.ningmeng123.mite.survival.RespawnCountdown;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native beds, FoodData, player saves and packet-driven respawning. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class RestLifecycleGameTests {
    private RestLifecycleGameTests() {}

    @GameTest(template = "empty")
    public static void sleepingWithoutSeedBufferHealsAtTheReducedRate(GameTestHelper h) {
        try (var bed = new BedChamber(h); var session = new Session(h)) {
            ServerPlayer player = session.player();
            feed(player, 6, 6);
            player.setHealth(player.getMaxHealth() - 2F);
            float before = player.getHealth();
            player.startSleeping(bed.head);
            h.assertValueEqual(PlayerRest.simulate(h.getLevel(), 480, List.of(player), tick -> false), 480,
                    "A fed sleeper forwards every requested food-update tick");
            h.assertValueEqual(player.getHealth(), before,
                    "Nutrition six without seed buffer remains below one health after 480 sleeping updates");
            h.assertValueEqual(PlayerRest.simulate(h.getLevel(), 1, List.of(player), tick -> false), 1,
                    "The next sleeping update also executes actual food metabolism");
            h.assertValueEqual(player.getHealth(), before + 1F,
                    "Nutrition six without seed buffer heals one health after 481 sleeping updates");
            h.assertTrue(player.getFoodData().getExhaustionLevel() > 1.15F,
                    "Accelerated recovery spends the increased 1.15-exhaustion healing cost");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void spendingTheLastSeedBufferSlowsRecoveryOnThatTick(GameTestHelper h) {
        try (var bed = new BedChamber(h); var session = new Session(h)) {
            ServerPlayer player = session.player();
            feed(player, 6, 6);
            player.getFoodData().setSaturation(1F);
            player.setHealth(player.getMaxHealth() - 2F);
            player.startSleeping(bed.head);
            PlayerRest.simulate(h.getLevel(), 1, List.of(player), tick -> false);
            float progress = player.saveWithoutId(new CompoundTag()).getFloat("mite_heal_progress");
            h.assertTrue(Math.abs(progress - 0.00432F) < 0.000000001F,
                    "One bed update with seed buffer accumulates the eightfold recovery increment");

            player.getFoodData().setExhaustion(4F);
            PlayerRest.simulate(h.getLevel(), 1, List.of(player), tick -> false);
            h.assertValueEqual(player.getFoodData().getSaturationLevel(), 0F, "The update spends the final seed buffer point");
            h.assertValueEqual(player.getFoodData().getFoodLevel(), 6, "Spending seed buffer preserves satiation");
            h.assertValueEqual(((DualFoodData) player.getFoodData()).mite$getNutrition(), 6, "Spending seed buffer preserves nutrition");
            h.assertTrue(player.isSleeping(), "Spending seed buffer alone does not end sleep");
            float depletedProgress = player.saveWithoutId(new CompoundTag()).getFloat("mite_heal_progress");
            h.assertTrue(Math.abs(depletedProgress - progress - 0.00208F) < 0.000000001F,
                    "The same update applies the reduced bed rate to the existing recovery progress");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void satiationAloneLetsPlayersRestAndHealInDaylight(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        long previousTime = level.getDayTime();
        GameRules.BooleanValue naturalRegeneration = level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);
        boolean previousRegeneration = naturalRegeneration.get();
        try (var bed = new BedChamber(h); var session = new Session(h)) {
            level.setDayTime(6000L);
            level.updateSkyBrightness();
            naturalRegeneration.set(true, level.getServer());
            ServerPlayer player = session.player();
            feed(player, 6, 0);
            player.setHealth(player.getMaxHealth() - 2F);
            float initialHealth = player.getHealth();

            var result = player.startSleepInBed(bed.head);
            h.assertTrue(result.right().isPresent() && player.isSleeping(),
                    "Players with only satiation can enter a sheltered bed during the day");
            var rest = NeoForge.EVENT_BUS.post(new CanContinueSleepingEvent(player, Player.BedSleepingProblem.NOT_POSSIBLE_NOW));
            h.assertTrue(rest.mayContinueSleeping(), "Daylight rest continues with satiation alone");
            for (int tick = 0; tick < 624; tick++) player.getFoodData().tick(player);
            h.assertValueEqual(player.getHealth(), initialHealth,
                    "Satiation alone remains below one health of recovery after 624 sleeping updates");
            player.getFoodData().tick(player);
            h.assertValueEqual(player.getHealth(), initialHealth + 1F,
                    "The 625th sleeping update heals one health using the reduced fallback rate");

            player.getFoodData().setFoodLevel(0);
            var depleted = NeoForge.EVENT_BUS.post(new CanContinueSleepingEvent(player, null));
            h.assertFalse(depleted.mayContinueSleeping(), "Depleting both food reserves ends ongoing rest");
        } finally {
            level.setDayTime(previousTime);
            level.updateSkyBrightness();
            naturalRegeneration.set(previousRegeneration, level.getServer());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sleepingSimulationAdvancesActualFoodAndMicronutrients(GameTestHelper h) {
        try (var bed = new BedChamber(h); var session = new Session(h)) {
            ServerPlayer player = session.player();
            feed(player, 6, 6);
            player.startSleeping(bed.head);
            h.assertTrue(player.isSleeping(), "A native bed establishes the sleeping entity state");
            int elapsed = PlayerRest.simulate(h.getLevel(), 10, List.of(player));
            h.assertValueEqual(elapsed, 10, "Ten forwarded ticks perform ten real metabolism updates");
            h.assertTrue(player.isSleeping(), "A fed sheltered sleeper remains in bed");
            h.assertTrue(Math.abs(player.getFoodData().getExhaustionLevel() - .023F) < .000001F,
                    "Dry, nourished sleep accumulates 0.0023 exhaustion per forwarded tick");
            h.assertValueEqual(NutritionState.get(player).protein(), 159990, "Protein decays during forwarded time");
            h.assertValueEqual(NutritionState.get(player).plants(), 159990, "Plant nutrition decays during forwarded time");
            h.assertValueEqual(player.getFoodData().getFoodLevel(), 6, "Sub-threshold metabolism keeps satiation");
            h.assertValueEqual(((DualFoodData) player.getFoodData()).mite$getNutrition(), 6,
                    "Sub-threshold metabolism keeps the nutrition reserve");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lastFoodReserveInterruptsSleepBeforeFurtherForwarding(GameTestHelper h) {
        try (var bed = new BedChamber(h); var session = new Session(h)) {
            ServerPlayer player = session.player();
            feed(player, 0, 1);
            player.getFoodData().setExhaustion(3.999F);
            player.startSleeping(bed.head);
            int elapsed = PlayerRest.simulate(h.getLevel(), 100, List.of(player));
            h.assertValueEqual(elapsed, 1, "The exhausting tick counts once and prevents all subsequent forwarding");
            h.assertValueEqual(((DualFoodData) player.getFoodData()).mite$getNutrition(), 0,
                    "Actual FoodData consumes the last nutrition unit");
            h.assertValueEqual(player.getFoodData().getFoodLevel(), 0, "No satiation remains as a fallback");
            h.assertFalse(player.isSleeping(), "Energy exhaustion exits the native sleeping pose");
            h.assertFalse(h.getLevel().getBlockState(bed.head).getValue(BedBlock.OCCUPIED),
                    "Waking releases the real bed's occupied state");
            h.assertValueEqual(NutritionState.get(player).protein(), 159999,
                    "Interruption prevents all remaining simulated food ticks");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void consciousnessControlsNativeDeepSleepAndFadesAfterWaking(GameTestHelper h) {
        try (var bed = new BedChamber(h); var session = new Session(h)) {
            ServerPlayer player = session.player();
            feed(player, 6, 6);
            player.startSleeping(bed.head);
            for (int tick = 0; tick < 99; tick++) PlayerRest.advanceConsciousness(player, true);
            h.assertValueEqual(PlayerRest.counter(player), 99, "Consciousness advances gradually for 99 ticks");
            h.assertFalse(player.isSleepingLongEnough(), "An occupied bed is not deep sleep before the threshold");
            PlayerRest.advanceConsciousness(player, true);
            h.assertValueEqual(PlayerRest.counter(player), 110, "The hundredth tick enters the source full darkness plateau");
            h.assertTrue(player.isSleepingLongEnough(), "Native sleep readiness reads the source consciousness state");
            player.stopSleepInBed(true, true);
            h.assertFalse(player.isSleepingLongEnough(), "Leaving the bed immediately ends native sleep readiness");
            PlayerRest.advanceConsciousness(player, false);
            h.assertValueEqual(PlayerRest.counter(player), 109, "The overlay fades instead of disappearing in one tick");
            for (int tick = 0; tick < 109; tick++) PlayerRest.advanceConsciousness(player, false);
            h.assertValueEqual(PlayerRest.counter(player), 0, "Waking eventually clears the saved consciousness state");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realDisconnectReconnectPreservesSleepFadeAndDeathCountdown(GameTestHelper h) {
        try (var bed = new BedChamber(h); var session = new Session(h)) {
            ServerPlayer before = session.player();
            feed(before, 3, 2);
            before.getFoodData().setExhaustion(.375F);
            before.startSleeping(bed.head);
            before.getPersistentData().putInt(PlayerRest.COUNTER, 74);
            session.reconnect();
            ServerPlayer restored = session.player();
            h.assertTrue(restored != before, "Reconnect creates a real replacement ServerPlayer with the same UUID");
            h.assertFalse(restored.isSleeping(), "Native loading leaves the bed rather than restoring an occupied sleep pose");
            h.assertValueEqual(PlayerRest.counter(restored), 74, "The source fade survives actual player-file saving and loading");
            h.assertValueEqual(restored.getFoodData().getFoodLevel(), 3, "Satiation survives reconnect");
            h.assertValueEqual(((DualFoodData) restored.getFoodData()).mite$getNutrition(), 2,
                    "The independent nutrition reserve survives reconnect");
            h.assertTrue(Math.abs(restored.getFoodData().getExhaustionLevel() - .375F) < .000001F,
                    "Pending metabolic work survives reconnect");
            PlayerRest.advanceConsciousness(restored, false);
            h.assertValueEqual(PlayerRest.counter(restored), 73, "The restored fade continues through the production update");
            restored.setHealth(0F);
            RespawnCountdown.died(restored);
            for (int seconds = 1; seconds <= 83; seconds++) RespawnCountdown.process(restored, seconds * 20L, false);
            h.assertValueEqual(RespawnCountdown.remaining(restored), 37, "A death countdown has a non-default saved remainder");
            session.reconnect();
            ServerPlayer dead = session.player();
            h.assertFalse(dead.isAlive(), "A dead player reconnects to the death screen state");
            h.assertValueEqual(RespawnCountdown.remaining(dead), 37, "Login does not restart a saved countdown at 120");
            RespawnCountdown.process(dead, 20L, false);
            h.assertValueEqual(RespawnCountdown.remaining(dead), 36, "The loaded countdown continues instead of being cosmetic data");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void automaticCountdownReplacesRealPlayerAfter120UnpausedSeconds(GameTestHelper h) {
        try (var session = new Session(h)) {
            ServerPlayer dead = session.player();
            dead.setHealth(0F);
            RespawnCountdown.died(dead);
            RespawnCountdown.process(dead, 0L, true);
            h.assertValueEqual(RespawnCountdown.remaining(dead), 120, "All-asleep pause preserves the initial source countdown");
            for (int tick = 1; tick <= 2399; tick++) RespawnCountdown.process(dead, tick, false);
            h.assertValueEqual(RespawnCountdown.remaining(dead), 1, "2399 actual game-clock inputs leave one second");
            h.assertTrue(session.player() == dead, "No player replacement occurs before the deadline");
            RespawnCountdown.process(dead, 2400L, false);
            ServerPlayer alive = session.player();
            h.assertTrue(alive != dead, "The 120th source second executes native PlayerList respawning");
            h.assertTrue(alive.isAlive(), "Automatic respawning produces a living player");
            h.assertValueEqual(alive.getUUID(), dead.getUUID(), "Native respawning preserves player identity");
            h.assertTrue(alive.server.getPlayerList().getPlayer(alive.getUUID()) == alive,
                    "The live player registry and network listener point to the same replacement");
            h.assertFalse(alive.getPersistentData().contains(RespawnCountdown.KEY),
                    "The respawn lifecycle removes the completed countdown");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeRespawnPacketWorksBeforeTheAutomaticCountdownExpires(GameTestHelper h) {
        try (var session = new Session(h)) {
            ServerPlayer dead = session.player();
            dead.setHealth(0F);
            RespawnCountdown.died(dead);
            RespawnCountdown.process(dead, 20L, false);
            h.assertValueEqual(RespawnCountdown.remaining(dead), 119, "Automatic respawn remains far in the future");
            dead.connection.handleClientCommand(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            ServerPlayer alive = session.player();
            h.assertTrue(alive != dead && alive.isAlive(), "The actual vanilla client packet still respawns immediately");
            h.assertFalse(alive.getPersistentData().contains(RespawnCountdown.KEY),
                    "Manual respawning clears the pending automatic countdown");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void transformedServerSleepGatesIgnoreDeadAndSpectatorPlayers(GameTestHelper h) throws ReflectiveOperationException {
        try (var bed = new BedChamber(h); var livingSession = new Session(h);
             var deadSession = new Session(h); var spectatorSession = new Session(h)) {
            ServerLevel level = h.getLevel();
            ServerPlayer living = livingSession.player(), dead = deadSession.player(), spectator = spectatorSession.player();
            feed(living, 6, 6);
            living.startSleeping(bed.head);
            living.getPersistentData().putInt(PlayerRest.COUNTER, 110);
            dead.setHealth(0F);
            spectator.setGameMode(GameType.SPECTATOR);
            List<ServerPlayer> candidates = List.of(living, dead, spectator);
            var nativeStatus = new SleepStatus();
            nativeStatus.update(candidates);
            var sleepStatusField = ServerLevel.class.getDeclaredField("sleepStatus");
            sleepStatusField.setAccessible(true);
            var noticeStatus = (SleepStatus) sleepStatusField.get(level);
            h.assertFalse(nativeStatus.areEnoughSleeping(100), "Native active-player accounting includes the dead player");
            h.assertFalse(nativeStatus.areEnoughDeepSleeping(100, candidates), "Native deep-sleep denominator also includes the dead player");

            // Invoke the actual merged ServerLevel call-site handlers, not level.tick recursively.
            // Isolate candidates and dayTime for this synchronous call only; never yield or tick.
            List<ServerPlayer> liveList = level.players(), original = new ArrayList<>(liveList);
            long oldDayTime = level.getDayTime(), oldGameTime = level.getGameTime();
            var sleepingPercentage = level.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
            int oldPercentage = sleepingPercentage.get();
            try {
                liveList.clear(); liveList.addAll(candidates);
                level.setDayTime(15000L);
                sleepingPercentage.set(100, level.getServer());
                level.updateSleepingPlayerList();
                h.assertValueEqual(noticeStatus.amountSleeping(), 1, "The server notice counts the occupied bed");
                h.assertValueEqual(noticeStatus.sleepersNeeded(100), 1, "The server notice excludes the dead player and spectator");
                h.assertTrue(serverGate(level, nativeStatus, 100, liveList, false), "Transformed outer gate excludes dead and spectator players");
                h.assertTrue(serverGate(level, nativeStatus, 100, liveList, true), "Transformed inner gate allows the sole living deep sleeper");
                living.getPersistentData().putInt(PlayerRest.COUNTER, 99);
                h.assertFalse(serverGate(level, nativeStatus, 100, liveList, true), "An occupied bed below deep-sleep consciousness cannot skip time");
                living.getPersistentData().putInt(PlayerRest.COUNTER, 110);
                sleepingPercentage.set(101, level.getServer());
                h.assertFalse(PlayerRest.canForward(level), "A percentage above 100 requires more than every eligible player");
                h.assertFalse(serverGate(level, nativeStatus, 101, liveList, false), "Even every eligible player does not meet 101 percent");
                h.assertFalse(serverGate(level, nativeStatus, 101, liveList, true), "Deep sleep cannot bypass the disabled percentage");

                dead.setHealth(dead.getMaxHealth());
                nativeStatus.update(liveList);
                level.updateSleepingPlayerList();
                h.assertValueEqual(noticeStatus.sleepersNeeded(100), 2, "The revived awake player joins the notice denominator");
                h.assertTrue(noticeStatus.areEnoughSleeping(50), "The notice accepts one of two at 50 percent");
                h.assertFalse(noticeStatus.areEnoughSleeping(51), "The notice still needs a second sleeper at 51 percent");
                for (int percentage : new int[] {0, 50, 51, 100}) {
                    sleepingPercentage.set(percentage, level.getServer());
                    boolean enough = percentage <= 50;
                    h.assertValueEqual(PlayerRest.canForward(level), enough, "One of two eligible sleepers at " + percentage + " percent");
                    h.assertValueEqual(serverGate(level, nativeStatus, percentage, liveList, false), enough,
                            "Transformed occupied-bed threshold with one of two players at " + percentage + " percent");
                    h.assertValueEqual(serverGate(level, nativeStatus, percentage, liveList, true), enough,
                            "Transformed deep-sleep threshold with one of two players at " + percentage + " percent");
                }
                dead.setHealth(0F);
                level.setDayTime(31L * 24000L + 15000L);
                h.assertFalse(serverGate(level, nativeStatus, 100, liveList, true), "A blood date cannot skip time through either native denominator");
                level.setDayTime(6000L);
                h.assertFalse(serverGate(level, nativeStatus, 100, liveList, true), "A daylight bed cannot skip time");
                level.setDayTime(15000L);
                liveList.remove(living);
                h.assertFalse(serverGate(level, nativeStatus, 100, liveList, false), "Dead and spectator players alone do not count as sleepers");
                h.assertFalse(serverGate(level, nativeStatus, 100, liveList, true), "Deep sleep requires at least one living participant");
                dead.setHealth(dead.getMaxHealth());
                nativeStatus.update(liveList);
                sleepingPercentage.set(0, level.getServer());
                h.assertFalse(PlayerRest.canForward(level), "Zero percent still requires a sleeper when a player is awake");
                h.assertFalse(serverGate(level, nativeStatus, 0, liveList, false), "Zero percent cannot skip an empty bed");
                h.assertFalse(serverGate(level, nativeStatus, 0, liveList, true), "Zero percent cannot skip without a deep sleeper");
                h.assertValueEqual(level.getGameTime(), oldGameTime, "Call-site gate verification never advances the shared game clock");
            } finally {
                sleepingPercentage.set(oldPercentage, level.getServer());
                level.setDayTime(oldDayTime);
                liveList.clear(); liveList.addAll(original);
                level.updateSleepingPlayerList();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sleepFinishSimulatesOnlyPlayersInBedAtTheConfiguredThreshold(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        long previousTime = level.getDayTime();
        long nightTime = 21999L;
        long sunrise = 23000L;
        var sleepingPercentage = level.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        int previousPercentage = sleepingPercentage.get();
        try (var bed = new BedChamber(h); var secondBed = new BedChamber(h, new BlockPos(5, 24, 1));
             var sleeperSession = new Session(h); var awakeSession = new Session(h); var newSleeperSession = new Session(h)) {
            ServerPlayer sleeper = sleeperSession.player(), awake = awakeSession.player(), newSleeper = newSleeperSession.player();
            feed(sleeper, 6, 6);
            feed(awake, 6, 6);
            feed(newSleeper, 6, 6);
            sleeper.startSleeping(bed.head);
            sleeper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10000));
            awake.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10000));
            sleeper.getPersistentData().putInt(PlayerRest.COUNTER, 99);

            // Keep other tests' sessions out of the shared level list during the synchronous event.
            List<ServerPlayer> players = level.players(), previousPlayers = new ArrayList<>(players);
            try {
                players.clear();
                players.addAll(List.of(sleeper, awake));
                level.setDayTime(nightTime);
                sleepingPercentage.set(51, level.getServer());
                PlayerRest.tick(new PlayerTickEvent.Post(sleeper));
                h.assertValueEqual(PlayerRest.counter(sleeper), 98, "Below quorum the sleeper's consciousness does not advance");
                sleeper.getPersistentData().putInt(PlayerRest.COUNTER, 99);
                sleepingPercentage.set(50, level.getServer());
                PlayerRest.tick(new PlayerTickEvent.Post(sleeper));
                h.assertTrue(sleeper.isSleepingLongEnough(), "One of two players reaches deep sleep at 50 percent");

                long finishTime = EventHooks.onSleepFinished(level, 24000L, nightTime);
                h.assertValueEqual(finishTime, sunrise, "Actual NeoForge sleep finish returns the source sunrise");
                int simulatedTicks = (int) (sunrise - nightTime);
                h.assertValueEqual(NutritionState.get(sleeper).protein(), NutritionState.INITIAL.protein() - simulatedTicks,
                        "The sleeper's food metabolism runs for every simulated tick");
                h.assertValueEqual(NutritionState.get(awake), NutritionState.INITIAL, "The awake player's nutrition does not advance");
                h.assertValueEqual(awake.getFoodData().getExhaustionLevel(), 0F, "The awake player pays no resting exhaustion");
                h.assertFalse(sleeper.hasEffect(MobEffects.DAMAGE_RESISTANCE), "After 1000 simulated ticks a sleeper clears status effects");
                h.assertTrue(awake.hasEffect(MobEffects.DAMAGE_RESISTANCE), "The awake player retains status effects");

                players.add(newSleeper);
                newSleeper.startSleeping(secondBed.head);
                h.assertTrue(newSleeper.isSleeping(), "The third player has entered a second bed");
                h.assertFalse(newSleeper.isSleepingLongEnough(), "The third player has yet to reach deep sleep");
                sleepingPercentage.set(33, level.getServer());
                long finishWithNewSleeper = EventHooks.onSleepFinished(level, 24000L, nightTime);
                h.assertValueEqual(finishWithNewSleeper, sunrise, "One of three deep sleepers meets the new percentage");
                h.assertValueEqual(NutritionState.get(newSleeper), NutritionState.INITIAL,
                        "A player who has only just entered a bed does not receive simulated resting time");
            } finally {
                players.clear();
                players.addAll(previousPlayers);
                level.updateSleepingPlayerList();
            }
        } finally {
            sleepingPercentage.set(previousPercentage, level.getServer());
            level.setDayTime(previousTime);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void effectClearingCountsTheExecutedInterruptionTick(GameTestHelper h) {
        for (int lastTick : new int[] {999, 1000}) {
            try (var firstBed = new BedChamber(h); var secondBed = new BedChamber(h, new BlockPos(5, 24, 1));
                 var wakingSession = new Session(h); var sleepingSession = new Session(h)) {
                ServerPlayer waking = wakingSession.player(), sleeping = sleepingSession.player();
                feed(waking, 6, 6); feed(sleeping, 6, 6);
                waking.startSleeping(firstBed.head); sleeping.startSleeping(secondBed.head);
                waking.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10000));
                sleeping.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10000));
                int elapsed = PlayerRest.simulate(h.getLevel(), 2000, List.of(waking, sleeping), tick -> {
                    if (tick != lastTick) return false;
                    waking.stopSleepInBed(true, true);
                    return true;
                });
                h.assertValueEqual(elapsed, lastTick, "An interruption includes the food update that already ran");
                h.assertTrue(waking.hasEffect(MobEffects.DAMAGE_RESISTANCE), "A player already out of bed is not granted resting effect removal");
                h.assertValueEqual(sleeping.hasEffect(MobEffects.DAMAGE_RESISTANCE), lastTick < 1000,
                        "The other sleeper clears effects at precisely 1000 executed ticks, including the interruption tick");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void restAchievementRequiresAnUninterrupted6000TickSleep(GameTestHelper h) {
        for (int interruptedAt : new int[] {5999, 6000, -1}) {
            try (var bed = new BedChamber(h); var session = new Session(h)) {
                ServerPlayer player = session.player();
                feed(player, 6, 6);
                player.startSleeping(bed.head);
                var achievement = player.server.getAdvancements().get(MiteAchievements.catalog().get("wellRested").id());
                h.assertFalse(player.getAdvancements().getOrStartProgress(achievement).isDone(), "A fresh player has no resting reward");
                int elapsed = PlayerRest.simulate(h.getLevel(), 6000, List.of(player), tick -> {
                    if (tick != interruptedAt) return false;
                    player.stopSleepInBed(true, true);
                    return true;
                });
                h.assertValueEqual(elapsed, interruptedAt < 0 ? 6000 : interruptedAt, "Simulated elapsed time includes its final processed tick");
                h.assertValueEqual(player.getAdvancements().getOrStartProgress(achievement).isDone(), interruptedAt < 0,
                        "Only all 6000 uninterrupted ticks while still in bed earn the native advancement, including a last-tick interruption boundary");
            }
        }
        h.succeed();
    }

    @SuppressWarnings("unchecked")
    private static boolean serverGate(ServerLevel level, SleepStatus status, int percentage,
                                      List<ServerPlayer> players, boolean deep) throws ReflectiveOperationException {
        String handler = deep ? "mite$bloodMoonCannotSkip" : "mite$sourceSleepers";
        Method method = Arrays.stream(ServerLevel.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().contains(handler))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing actual transformed ServerLevel handler " + handler));
        method.setAccessible(true);
        Operation<Boolean> nativeCall = args -> deep
                ? ((SleepStatus) args[0]).areEnoughDeepSleeping((Integer) args[1], (List<ServerPlayer>) args[2])
                : ((SleepStatus) args[0]).areEnoughSleeping((Integer) args[1]);
        return (Boolean) (deep ? method.invoke(level, status, percentage, players, nativeCall)
                : method.invoke(level, status, percentage, nativeCall));
    }

    private static void feed(ServerPlayer player, int satiation, int nutrition) {
        player.getFoodData().setFoodLevel(satiation);
        player.getFoodData().setSaturation(0F);
        player.getFoodData().setExhaustion(0F);
        ((DualFoodData) player.getFoodData()).mite$setNutrition(nutrition);
        NutritionState.set(player, NutritionState.INITIAL);
        player.setHealth(player.getMaxHealth());
    }

    /** A solid enclosure makes random source interruption checks unable to path into this bed. */
    private static final class BedChamber implements AutoCloseable {
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        final BlockPos head;

        BedChamber(GameTestHelper h) { this(h, new BlockPos(1, 24, 1)); }

        BedChamber(GameTestHelper h, BlockPos relativeFoot) {
            level = h.getLevel();
            BlockPos foot = h.absolutePos(relativeFoot);
            head = foot.north();
            for (BlockPos mutable : BlockPos.betweenClosed(foot.offset(-1, -1, -2), foot.offset(1, 2, 1))) {
                BlockPos pos = mutable.immutable();
                original.put(pos, level.getBlockState(pos));
                boolean wall = pos.getX() != foot.getX() || pos.getY() == foot.getY() - 1
                        || pos.getY() == foot.getY() + 2 || pos.getZ() == foot.getZ() - 2
                        || pos.getZ() == foot.getZ() + 1;
                level.setBlock(pos, wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            BlockState state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            level.setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), 2);
        }

        @Override public void close() {
            for (var entry : original.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
    }

    /** Real login/save/respawn lifecycle; follows the listener after PlayerList replaces a player. */
    private static final class Session implements AutoCloseable {
        private final GameTestHelper helper;
        private final GameProfile profile = new GameProfile(UUID.randomUUID(),
                "sleep-" + UUID.randomUUID().toString().substring(0, 8));
        private EmbeddedChannel channel;
        private ServerGamePacketListenerImpl listener;

        Session(GameTestHelper h) { helper = h; join(); }
        ServerPlayer player() { return listener.player; }

        private void join() {
            var cookie = CommonListenerCookie.createInitial(profile, false);
            ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                    cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            listener = player.connection;
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 24, 1));
            player.moveTo(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
        }

        void reconnect() {
            remove();
            join();
        }

        private void remove() {
            ServerPlayer current = player().server.getPlayerList().getPlayer(profile.getId());
            if (current != null) current.server.getPlayerList().remove(current);
            channel.finishAndReleaseAll();
        }

        @Override public void close() {
            ServerPlayer current = player();
            if (current.isSleeping()) current.stopSleepInBed(true, true);
            current.getInventory().clearContent();
            remove();
        }
    }
}
