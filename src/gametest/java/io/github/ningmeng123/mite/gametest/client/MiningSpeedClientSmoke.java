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
    private static final String WORLD_FOLDER = "MITE Mining Test-007";
    private static final BlockPos TARGET = new BlockPos(11005, 200, 11006);
    private static final Vec3 STAND = new Vec3(11005.5, 200.0, 11003.5);
    private static final float YAW = 0.0F;
    private static final float PITCH = 20.472F;
    private static final int[] LEVELS = {0, -40, 35, 200, 5};
    private static final float[] SPEEDS = {1.0F, 0.2F, 1.7F, 5.0F, 1.1F};
    private static final float[] PROGRESS = {0.06666667F, 0.01333333F, 0.11333333F, 0.33333333F, 0.07333333F};
    private static Runner runner;

    private MiningSpeedClientSmoke() {
    }

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Boolean.getBoolean("mite.miningSmoke")) {
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
            runner = new Runner(minecraft.getSingleplayerServer(), minecraft.player.getUUID());
            Mite.LOGGER.info("MITE_MINING START copy={} cases={}", WORLD_FOLDER, Arrays.toString(LEVELS));
            runner.prepare(LEVELS[0], true);
        }
        runner.tick(minecraft);
    }

    private enum Phase {
        PREPARING, SYNCING, MINING, CONFIRMING, FINAL_PREPARING, FINAL_SYNCING, DONE
    }

    private static final class Runner {
        private final IntegratedServer server;
        private final UUID playerId;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final int[] miningTicks = new int[LEVELS.length];
        private CompletableFuture<Boolean> serverWork;
        private Phase phase = Phase.PREPARING;
        private int caseIndex;
        private int elapsedTicks;
        private int stableTicks;
        private int currentMiningTicks;

        private Runner(IntegratedServer server, UUID playerId) {
            this.server = server;
            this.playerId = playerId;
        }

        private void prepare(int level, boolean createPlatform) {
            stableTicks = 0;
            serverWork = onServer(player -> {
                preparePlayer(player, level, createPlatform);
                return true;
            });
        }

        private void tick(Minecraft minecraft) {
            if (!active.get()) {
                return;
            }
            try {
                require(++elapsedTicks <= 1200, "Timed out after 1200 client ticks at " + phase);
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
                        int expectedLevel = phase == Phase.SYNCING ? LEVELS[caseIndex] : 35;
                        if (!readyOnClient(minecraft, expectedLevel)) {
                            stableTicks = 0;
                            return;
                        }
                        if (++stableTicks < 3) {
                            return;
                        }
                        if (phase == Phase.FINAL_SYNCING) {
                            pass(minecraft);
                        } else {
                            BlockState dirt = minecraft.level.getBlockState(TARGET);
                            assertClose(minecraft.player.getDigSpeed(dirt, TARGET), SPEEDS[caseIndex], "client dig speed");
                            assertClose(dirt.getDestroyProgress(minecraft.player, minecraft.level, TARGET),
                                    PROGRESS[caseIndex], "client destroy progress");
                            currentMiningTicks = 0;
                            phase = Phase.MINING;
                            // Minecraft's normal handleKeybinds/continueAttack path
                            // calls startDestroyBlock and continueDestroyBlock.
                            minecraft.options.keyAttack.setDown(true);
                            Mite.LOGGER.info("MITE_MINING CASE_START level={} speed={} progress={}",
                                    expectedLevel, SPEEDS[caseIndex], PROGRESS[caseIndex]);
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
                            require(minecraft.mouseHandler.isMouseGrabbed(), "Mouse capture was released during mining");
                            require(minecraft.player.experienceLevel == LEVELS[caseIndex], "XP changed during mining");
                            require(minecraft.player.onGround(), "Player left the test platform during mining");
                            require(minecraft.player.getMainHandItem().isEmpty(), "Mining is no longer empty-handed");
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
                        Mite.LOGGER.info("MITE_MINING CASE_PASS level={} clientTicks={} serverAir=true",
                                LEVELS[caseIndex], miningTicks[caseIndex]);
                        if (++caseIndex < LEVELS.length) {
                            phase = Phase.PREPARING;
                            prepare(LEVELS[caseIndex], false);
                        } else {
                            require(miningTicks[1] > miningTicks[0] * 2,
                                    "Level -40 was not clearly slower than level 0: " + Arrays.toString(miningTicks));
                            require(miningTicks[3] < miningTicks[0],
                                    "Level 200 was not faster than level 0: " + Arrays.toString(miningTicks));
                            phase = Phase.FINAL_PREPARING;
                            prepare(35, false);
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
                    require(WORLD_FOLDER.equals(folder), "Refusing to modify a non-test save: " + folder);
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
            String message = "MITE_MINING PASS — 5 等级客户端真实挖掘与服务端确认通过；当前 35 级";
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

    private static void preparePlayer(ServerPlayer player, int level, boolean createPlatform) {
        ServerLevel world = player.server.getLevel(Level.OVERWORLD);
        require(world != null, "Overworld is unavailable");
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        // This dedicated copy keeps drops disabled so mining cannot equip picked-up dirt.
        // Drop behavior is covered separately by the server GameTests.
        world.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false, player.server);
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
        world.setBlockAndUpdate(TARGET, Blocks.DIRT.defaultBlockState());
        player.setGameMode(GameType.SURVIVAL);
        player.stopUsingItem();
        player.removeAllEffects();
        player.getInventory().clearContent();
        player.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(1.0);
        player.getAttribute(Attributes.MINING_EFFICIENCY).setBaseValue(0.0);
        PlayerExperience.setTotal(player, ExperienceRules.experienceRequiredForLevel(level));
        player.setExperiencePoints(0); // Uses the normal dirty flag and XP packet.
        player.getFoodData().setFoodLevel(20);
        player.setHealth(player.getMaxHealth());
        player.teleportTo(world, STAND.x, STAND.y, STAND.z, YAW, PITCH);
        player.setDeltaMovement(Vec3.ZERO);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static boolean readyOnClient(Minecraft minecraft, int level) {
        return minecraft.screen == null && minecraft.mouseHandler.isMouseGrabbed()
                && minecraft.level.dimension().equals(Level.OVERWORLD)
                && minecraft.player.experienceLevel == level && minecraft.player.experienceProgress == 0.0F
                && !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                && minecraft.player.position().distanceToSqr(STAND) < 0.1
                && minecraft.player.onGround() && minecraft.player.getMainHandItem().isEmpty()
                && minecraft.player.getActiveEffects().isEmpty()
                && minecraft.level.getBlockState(TARGET).is(Blocks.DIRT)
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
