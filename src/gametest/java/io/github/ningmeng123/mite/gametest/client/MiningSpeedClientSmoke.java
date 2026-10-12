package io.github.ningmeng123.mite.gametest.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.ExperienceRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in normal-input smoke test, available only in the development test source set. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class MiningSpeedClientSmoke {
    private static final BlockPos TARGET = new BlockPos(11005, 200, 11006);
    private static final Vec3 STAND = new Vec3(11005.5, 200.0, 11003.5);
    private static final float YAW = 0.0F;
    private static final float PITCH = 20.472F;
    // Literal progress bits and repeated float-addition counts, independent of production rules.
    private static final MiningCase FINAL_CASE = new MiningCase("dirt_35", Blocks.DIRT.defaultBlockState(),
            35, 1.7F, 0.5F, 0x3bd9999a, 151);
    private static final MiningCase[] LEVEL_CASES = {
        new MiningCase("dirt_0", Blocks.DIRT.defaultBlockState(), 0, 1.0F, 0.5F, 0x3b800000, 256),
        new MiningCase("dirt_minus_40", Blocks.DIRT.defaultBlockState(), -40, 0.2F, 0.5F, 0x3a4ccccf, 1281),
        FINAL_CASE,
        new MiningCase("dirt_200", Blocks.DIRT.defaultBlockState(), 200, 5.0F, 0.5F, 0x3ca00000, 52),
        new MiningCase("dirt_5", Blocks.DIRT.defaultBlockState(), 5, 1.1F, 0.5F, 0x3b8ccccd, 233)
    };
    private static final MiningCase[] BASE_CASES = {
        LEVEL_CASES[0],
        new MiningCase("grass_block_0", Blocks.GRASS_BLOCK.defaultBlockState(), 0, 1.0F, 0.6F, 0x3b800000, 256),
        new MiningCase("sand_0", Blocks.SAND.defaultBlockState(), 0, 1.0F, 0.5F, 0x3ba00000, 205),
        new MiningCase("gravel_0", Blocks.GRAVEL.defaultBlockState(), 0, 1.0F, 0.6F, 0x3b555555, 308),
        new MiningCase("oak_leaves_0", Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                0, 1.0F, 0.2F, 0x3c200000, 103),
        FINAL_CASE
    };
    private static Runner runner;
    private static boolean conflictingModesReported;

    private record MiningCase(String name, BlockState state, int level, float strength,
            float rawHardness, int progressBits, int accumulatedTicks) {
    }

    private MiningSpeedClientSmoke() {
    }

    @SubscribeEvent
    public static void beforeClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (runner == null || !runner.active.get() || runner.phase != Phase.MINING
                || minecraft.getSingleplayerServer() != runner.server || minecraft.player == null
                || minecraft.level == null || minecraft.gameMode == null
                || !runner.playerId.equals(minecraft.player.getUUID())
                || minecraft.screen != null || minecraft.getOverlay() != null
                || !minecraft.isWindowActive() || !minecraft.mouseHandler.isMouseGrabbed()) {
            return;
        }
        // Runs before vanilla pick(1.0F), after mouse motion from the preceding render frame.
        // Vanilla still performs the ray cast and the complete input/packet mining path.
        pointAtTarget(minecraft);
        minecraft.options.keyAttack.setDown(true);
    }

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean levelSmoke = Boolean.getBoolean("mite.miningSmoke");
        boolean baseSmoke = Boolean.getBoolean("mite.miningBaseSmoke");
        if (levelSmoke && baseSmoke) {
            if (runner != null && runner.active.get()) {
                runner.fail(minecraft, "Both mining smoke modes were enabled");
            } else if (!conflictingModesReported) {
                String message = "MITE_MINING FAIL: miningSmoke and miningBaseSmoke are mutually exclusive";
                Mite.LOGGER.error(message);
                minecraft.gui.getChat().addMessage(Component.literal(message));
            }
            conflictingModesReported = true;
            return;
        }
        if (!levelSmoke && !baseSmoke) {
            if (runner != null && runner.active.get()) {
                runner.fail(minecraft, "Opt-in property was disabled");
            }
            return;
        }
        if (runner == null) {
            if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null
                    || minecraft.screen != null || minecraft.getSingleplayerServer() == null) {
                return;
            }
            runner = new Runner(minecraft.getSingleplayerServer(), minecraft.player.getUUID(), baseSmoke);
            Mite.LOGGER.info("MITE_MINING START copy={} cases={}", runner.worldFolder,
                    Arrays.toString(Arrays.stream(runner.cases).map(MiningCase::name).toArray()));
            runner.prepare(runner.cases[0], true);
        } else if (runner.baseSmoke != baseSmoke) {
            if (runner.active.get()) {
                runner.fail(minecraft, "The selected smoke mode changed during the test");
            }
            return;
        }
        runner.tick(minecraft);
    }

    private enum Phase {
        PREPARING, SYNCING, MINING, CONFIRMING, FINAL_PREPARING, FINAL_SYNCING, DONE
    }

    private static final class Runner {
        private final IntegratedServer server;
        private final UUID playerId;
        private final boolean baseSmoke;
        private final String worldFolder;
        private final MiningCase[] cases;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final int[] miningTicks;
        private CompletableFuture<Boolean> serverWork;
        private Phase phase = Phase.PREPARING;
        private int caseIndex;
        private int elapsedTicks;
        private int stableTicks;
        private int currentMiningTicks;

        private Runner(IntegratedServer server, UUID playerId, boolean baseSmoke) {
            this.server = server;
            this.playerId = playerId;
            this.baseSmoke = baseSmoke;
            worldFolder = baseSmoke ? "MITE Mining Test-008" : "MITE Mining Test-007";
            cases = baseSmoke ? BASE_CASES : LEVEL_CASES;
            miningTicks = new int[cases.length];
        }

        private void prepare(MiningCase testCase, boolean createPlatform) {
            stableTicks = 0;
            serverWork = onServer(player -> {
                preparePlayer(player, testCase, createPlatform);
                return true;
            });
        }

        private void tick(Minecraft minecraft) {
            if (!active.get()) {
                return;
            }
            try {
                require(++elapsedTicks <= 4000, "Timed out after 4000 client ticks at " + phase);
                require(minecraft.getSingleplayerServer() == server && minecraft.player != null
                        && minecraft.level != null && minecraft.gameMode != null
                        && playerId.equals(minecraft.player.getUUID()), "The test world or player was disconnected");
                if ((phase == Phase.MINING || phase == Phase.CONFIRMING) && minecraft.screen != null) {
                    throw new IllegalStateException("Mining was canceled by opening a screen");
                }

                switch (phase) {
                    case PREPARING, FINAL_PREPARING -> {
                        stopMining(minecraft);
                        if (serverWork.isDone()) {
                            serverWork.join();
                            phase = phase == Phase.PREPARING ? Phase.SYNCING : Phase.FINAL_SYNCING;
                        }
                    }
                    case SYNCING, FINAL_SYNCING -> {
                        stopMining(minecraft);
                        pointAtTarget(minecraft);
                        MiningCase testCase = phase == Phase.SYNCING ? cases[caseIndex] : FINAL_CASE;
                        if (!readyOnClient(minecraft, testCase)) {
                            stableTicks = 0;
                            return;
                        }
                        if (++stableTicks < 3) {
                            return;
                        }
                        if (phase == Phase.FINAL_SYNCING) {
                            pass(minecraft);
                        } else {
                            BlockState targetState = minecraft.level.getBlockState(TARGET);
                            assertClose(minecraft.player.getDigSpeed(targetState, TARGET), testCase.strength(), "client dig speed");
                            assertBits(targetState.getDestroySpeed(minecraft.level, TARGET),
                                    Float.floatToRawIntBits(testCase.rawHardness()), "unchanged registered hardness");
                            assertBits(targetState.getDestroyProgress(minecraft.player, minecraft.level, TARGET),
                                    testCase.progressBits(), "client destroy progress");
                            currentMiningTicks = 0;
                            phase = Phase.MINING;
                            // Minecraft's normal handleKeybinds/continueAttack path
                            // calls startDestroyBlock and continueDestroyBlock.
                            minecraft.options.keyAttack.setDown(true);
                            Mite.LOGGER.info("MITE_MINING CASE_START case={} level={} rawHardness={} speed={} progress={} expectedAdditions={}",
                                    testCase.name(), testCase.level(), testCase.rawHardness(), testCase.strength(),
                                    Float.intBitsToFloat(testCase.progressBits()), testCase.accumulatedTicks());
                        }
                    }
                    case MINING -> {
                        currentMiningTicks++;
                        if (minecraft.level.getBlockState(TARGET).isAir()) {
                            stopMining(minecraft);
                            miningTicks[caseIndex] = currentMiningTicks;
                            phase = Phase.CONFIRMING;
                            serverWork = confirmServerBreak();
                        } else {
                            require(minecraft.isWindowActive(), "Window focus was lost during mining");
                            require(minecraft.getOverlay() == null, "An overlay interrupted mining");
                            require(minecraft.mouseHandler.isMouseGrabbed(), "Mouse capture was released during mining");
                            require(minecraft.options.keyAttack.isDown(), "Attack input was released during mining");
                            require(minecraft.player.experienceLevel == cases[caseIndex].level(), "XP changed during mining");
                            require(minecraft.player.onGround(), "Player left the test platform during mining");
                            require(minecraft.player.getMainHandItem().isEmpty(), "Mining is no longer empty-handed");
                            require(minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(TARGET),
                                    "Mining target drifted: hit=" + (minecraft.hitResult instanceof BlockHitResult reportedHit
                                            ? reportedHit.getBlockPos() : minecraft.hitResult == null ? "null" : minecraft.hitResult.getType())
                                            + " yaw=" + minecraft.player.getYRot() + " pitch=" + minecraft.player.getXRot()
                                            + " clientTicks=" + currentMiningTicks);
                            assertBits(minecraft.level.getBlockState(TARGET)
                                            .getDestroyProgress(minecraft.player, minecraft.level, TARGET),
                                    cases[caseIndex].progressBits(), "mining progress changed for " + cases[caseIndex].name());
                            pointAtTarget(minecraft);
                            minecraft.options.keyAttack.setDown(true);
                        }
                    }
                    case CONFIRMING -> {
                        stopMining(minecraft);
                        if (!serverWork.isDone()) {
                            return;
                        }
                        if (!serverWork.join()) {
                            // Packet delivery may trail the client prediction.
                            serverWork = confirmServerBreak();
                            return;
                        }
                        MiningCase testCase = cases[caseIndex];
                        int expectedTicks = testCase.accumulatedTicks();
                        require(miningTicks[caseIndex] >= expectedTicks && miningTicks[caseIndex] <= expectedTicks + 6,
                                testCase.name() + " client ticks outside [" + expectedTicks + "," + (expectedTicks + 6)
                                        + "]: " + miningTicks[caseIndex]);
                        Mite.LOGGER.info("MITE_MINING CASE_PASS case={} level={} clientTicks={} expectedAdditions={} serverAir=true",
                                testCase.name(), testCase.level(), miningTicks[caseIndex], expectedTicks);
                        if (++caseIndex < cases.length) {
                            phase = Phase.PREPARING;
                            prepare(cases[caseIndex], false);
                        } else {
                            if (!baseSmoke) {
                                require(miningTicks[1] > miningTicks[0] * 2,
                                        "Level -40 was not clearly slower than level 0: " + Arrays.toString(miningTicks));
                                require(miningTicks[3] < miningTicks[0],
                                        "Level 200 was not faster than level 0: " + Arrays.toString(miningTicks));
                            }
                            phase = Phase.FINAL_PREPARING;
                            prepare(FINAL_CASE, false);
                        }
                    }
                    case DONE -> { }
                }
            } catch (RuntimeException exception) {
                fail(minecraft, exception.toString());
            }
        }

        private CompletableFuture<Boolean> confirmServerBreak() {
            return onServer(player -> {
                require(player.serverLevel() == server.overworld(), "Server player left the overworld");
                return player.serverLevel().getBlockState(TARGET).isAir();
            });
        }

        private <T> CompletableFuture<T> onServer(Function<ServerPlayer, T> action) {
            CompletableFuture<T> result = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    require(active.get(), "Smoke test is no longer active");
                    String folder = server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
                    require(worldFolder.equals(folder), "Refusing to modify a non-test save: " + folder);
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    require(player != null, "Server player is no longer connected");
                    result.complete(action.apply(player));
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            });
            return result;
        }

        private void pass(Minecraft minecraft) {
            stopMining(minecraft);
            active.set(false);
            phase = Phase.DONE;
            String message = "MITE_MINING PASS — " + cases.length + " 案例客户端真实挖掘与服务端确认通过；当前 35 级";
            Mite.LOGGER.info("{} clientTicks={} elapsedClientTicks={}", message, Arrays.toString(miningTicks), elapsedTicks);
            minecraft.gui.getChat().addMessage(Component.literal(message));
            minecraft.gui.setOverlayMessage(Component.literal("MITE_MINING PASS — 已保留 35 级与泥土目标，可按 F2"), false);
        }

        private void fail(Minecraft minecraft, String reason) {
            stopMining(minecraft);
            active.set(false);
            phase = Phase.DONE;
            Mite.LOGGER.error("MITE_MINING FAIL {}", reason);
            minecraft.gui.getChat().addMessage(Component.literal("MITE_MINING FAIL: " + reason));
            minecraft.gui.setOverlayMessage(Component.literal("MITE_MINING FAIL — 已停止挖掘，详见日志"), false);
        }
    }

    private static void preparePlayer(ServerPlayer player, MiningCase testCase, boolean createPlatform) {
        ServerLevel world = player.server.getLevel(Level.OVERWORLD);
        require(world != null, "Overworld is unavailable");
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        // This dedicated copy keeps drops disabled so mining cannot equip picked-up dirt.
        // Drop behavior is covered separately by the server GameTests.
        world.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false, player.server);
        world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        world.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, player.server);
        world.setDayTime(6000L);
        world.setWeatherParameters(6000, 0, false, false);
        player.server.setDifficulty(Difficulty.PEACEFUL, true);
        if (createPlatform) {
            for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class,
                    new AABB(11000, 199, 11000, 11010, 204, 11010))) {
                item.discard();
            }
            // This entire fixture is inside x/z 11000..11010, y 199..203.
            for (int x = 11001; x <= 11009; x++) {
                for (int z = 11001; z <= 11009; z++) {
                    world.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState());
                    for (int y = 200; y <= 203; y++) {
                        world.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        // Only setup places blocks. The MINING/CONFIRMING phases never remove one.
        world.setBlockAndUpdate(TARGET, testCase.state());
        player.setGameMode(GameType.SURVIVAL);
        player.stopUsingItem();
        player.removeAllEffects();
        player.getInventory().clearContent();
        player.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(1.0);
        player.getAttribute(Attributes.MINING_EFFICIENCY).setBaseValue(0.0);
        PlayerExperience.setTotal(player, ExperienceRules.experienceRequiredForLevel(testCase.level()));
        player.setExperiencePoints(0); // Uses the normal dirty flag and XP packet.
        player.getFoodData().setFoodLevel(20);
        player.setHealth(player.getMaxHealth());
        player.teleportTo(world, STAND.x, STAND.y, STAND.z, YAW, PITCH);
        player.setDeltaMovement(Vec3.ZERO);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static boolean readyOnClient(Minecraft minecraft, MiningCase testCase) {
        return minecraft.screen == null && minecraft.getOverlay() == null
                && minecraft.isWindowActive() && minecraft.mouseHandler.isMouseGrabbed()
                && minecraft.level.dimension().equals(Level.OVERWORLD)
                && minecraft.player.experienceLevel == testCase.level() && minecraft.player.experienceProgress == 0.0F
                && !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                && minecraft.player.position().distanceToSqr(STAND) < 0.1
                && minecraft.player.onGround() && minecraft.player.getMainHandItem().isEmpty()
                && minecraft.player.getActiveEffects().isEmpty()
                && minecraft.level.getBlockState(TARGET).is(testCase.state().getBlock())
                && (!testCase.state().is(Blocks.OAK_LEAVES)
                        || minecraft.level.getBlockState(TARGET).getValue(LeavesBlock.PERSISTENT))
                && minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(TARGET);
    }

    private static void pointAtTarget(Minecraft minecraft) {
        minecraft.player.setYRot(YAW);
        minecraft.player.setXRot(PITCH);
    }

    private static void stopMining(Minecraft minecraft) {
        minecraft.options.keyAttack.setDown(false);
        if (minecraft.gameMode != null && minecraft.player != null && minecraft.level != null) {
            minecraft.gameMode.stopDestroyBlock();
        }
    }

    private static void assertClose(float actual, float expected, String description) {
        require(Float.isFinite(actual) && Math.abs(actual - expected) < 0.00002F,
                description + " expected=" + expected + " actual=" + actual);
    }

    private static void assertBits(float actual, int expectedBits, String description) {
        require(Float.floatToRawIntBits(actual) == expectedBits,
                description + " expectedBits=0x" + Integer.toHexString(expectedBits)
                        + " actualBits=0x" + Integer.toHexString(Float.floatToRawIntBits(actual)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
