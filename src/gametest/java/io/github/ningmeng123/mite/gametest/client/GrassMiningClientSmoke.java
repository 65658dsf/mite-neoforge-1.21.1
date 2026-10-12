package io.github.ningmeng123.mite.gametest.client;

import io.github.ningmeng123.mite.Mite;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real attack-input acceptance, restricted to one opted-in disposable save. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class GrassMiningClientSmoke {
    private static final String WORLD = "MITE Grass Mining Test-013";
    private static final BlockPos BASE = new BlockPos(14005, 200, 14006);
    private static final Vec3 STAND = new Vec3(14005.5, 200.0, 14003.5);
    private static final AABB AREA = new AABB(14000, 199, 14000, 14011, 205, 14011);
    private static Runner runner;

    private GrassMiningClientSmoke() {}

    private record Case(String name, BlockState plant, boolean upperTarget, Item tool, int food,
            int progressBits, int ticks, int finalDamage, boolean grassDrop) {
        private BlockPos target() {
            return upperTarget ? BASE.above() : BASE;
        }

        private BlockState targetState() {
            return upperTarget ? plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER) : plant;
        }
    }

    private record Result(boolean original, boolean lowerAir, boolean upperAir, int grassDrops,
            boolean toolMatches, int damage, int food, int level, int selected, boolean grounded, int progressBits) {
        private boolean cleared() {
            return lowerAir && upperAir;
        }
    }

    private enum Phase { PREPARE, SYNC, MINING, CONFIRM, DISPLAY, DONE }

    @SubscribeEvent
    public static void beforeTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (runner != null && runner.active.get() && runner.phase == Phase.MINING
                && runner.samePlayer(mc) && focused(mc)) {
            // The vanilla picker still performs the ray cast after this pre-tick aim adjustment.
            aim(mc, runner.cases[runner.index]);
            mc.options.keyAttack.setDown(true);
        }
    }

    @SubscribeEvent
    public static void afterTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("mite.grassMiningSmoke")) {
            if (runner != null && runner.active.get()) runner.fail(mc, "Opt-in was disabled");
            return;
        }
        if (runner == null) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null
                    || mc.getSingleplayerServer() == null) return;
            runner = new Runner(mc.getSingleplayerServer(), mc.player.getUUID());
            if (Boolean.getBoolean("mite.miningSmoke") || Boolean.getBoolean("mite.miningBaseSmoke")
                    || Boolean.getBoolean("mite.miningHungerSmoke") || Boolean.getBoolean("mite.flintStartSmoke")
                    || Boolean.getBoolean("mite.logHarvestSmoke") || Boolean.getBoolean("mite.woodenEquipmentSmoke")) {
                runner.fail(mc, "Only grassMiningSmoke may be enabled");
                return;
            }
            Mite.LOGGER.info("MITE_GRASS_MINING START copy={} test-supplied tools; normal attack input and natural ray casts", WORLD);
            runner.prepare(true);
        }
        runner.tick(mc);
    }

    private static final class Runner {
        private final IntegratedServer server;
        private final UUID playerId;
        private final AtomicBoolean active = new AtomicBoolean(true);
        // Independent IEEE-754 literals and counts from repeated float addition, not production helpers.
        private final Case[] cases = {
                new Case("short_grass_empty", Blocks.SHORT_GRASS.defaultBlockState(), false,
                        Items.AIR, 6, 0x3dc80000, 11, 0, false),
                new Case("short_grass_wooden_club", Blocks.SHORT_GRASS.defaultBlockState(), false,
                        Items.WOODEN_SWORD, 6, 0x3dc80000, 11, 0, false),
                new Case("fern_empty", Blocks.FERN.defaultBlockState(), false,
                        Items.AIR, 6, 0x3dc80000, 11, 0, false),
                new Case("tall_grass_upper_empty", Blocks.TALL_GRASS.defaultBlockState(), true,
                        Items.AIR, 6, 0x3dc80000, 11, 0, false),
                new Case("short_grass_empty_food_zero", Blocks.SHORT_GRASS.defaultBlockState(), false,
                        Items.AIR, 0, 0x3ca00001, 52, 0, false),
                new Case("short_grass_shears", Blocks.SHORT_GRASS.defaultBlockState(), false,
                        Items.SHEARS, 6, 0x3f800000, 1, 1, true)
        };
        private final int[] counts = new int[cases.length];
        private CompletableFuture<Result> work;
        private CompletableFuture<Void> displayWork;
        private Phase phase = Phase.PREPARE;
        private int index;
        private int elapsed;
        private int stable;
        private int miningTicks;

        private Runner(IntegratedServer server, UUID playerId) {
            this.server = server;
            this.playerId = playerId;
        }

        private boolean samePlayer(Minecraft mc) {
            return mc.getSingleplayerServer() == server && mc.player != null && mc.level != null && mc.gameMode != null
                    && playerId.equals(mc.player.getUUID());
        }

        private void prepare(boolean platform) {
            Case test = cases[index];
            stable = 0;
            phase = Phase.PREPARE;
            work = onServer(player -> {
                var world = server.overworld();
                if (platform) {
                    world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                    world.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, server);
                    world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    world.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                    world.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
                    world.setDayTime(6000L);
                    world.setWeatherParameters(6000, 0, false, false);
                    // Peaceful would refill the food-zero case during the measurement.
                    server.setDifficulty(Difficulty.NORMAL, true);
                    for (int x = 14001; x <= 14009; x++) {
                        for (int z = 14001; z <= 14009; z++) {
                            world.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.GRASS_BLOCK.defaultBlockState());
                            for (int y = 200; y <= 204; y++) {
                                world.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                }
                // Case setup is the only phase that writes plant blocks; measurement uses real input.
                for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, AREA)) item.discard();
                world.setBlockAndUpdate(BASE.above(), Blocks.AIR.defaultBlockState());
                world.setBlockAndUpdate(BASE, Blocks.AIR.defaultBlockState());
                world.setBlockAndUpdate(BASE.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                if (test.upperTarget()) DoublePlantBlock.placeAt(world, test.plant(), BASE, 3);
                else world.setBlockAndUpdate(BASE, test.plant());
                player.setGameMode(GameType.SURVIVAL);
                player.removeAllEffects();
                player.stopUsingItem();
                player.setSprinting(false);
                player.getInventory().clearContent();
                player.getInventory().selected = 0;
                player.getInventory().setItem(0, test.tool() == Items.AIR ? ItemStack.EMPTY : new ItemStack(test.tool()));
                player.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(1.0);
                player.getAttribute(Attributes.MINING_EFFICIENCY).setBaseValue(0.0);
                PlayerExperience.setTotal(player, 0);
                player.getFoodData().setFoodLevel(test.food());
                player.getFoodData().setSaturation(0.0F);
                player.getFoodData().setExhaustion(0.0F);
                player.setHealth(player.getMaxHealth());
                player.teleportTo(world, STAND.x, STAND.y, STAND.z, 0.0F, 20.0F);
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0.0F;
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                return snapshot(player, test);
            });
        }

        private void tick(Minecraft mc) {
            if (!active.get()) return;
            try {
                require(++elapsed < 4000, "Timeout at " + phase);
                require(samePlayer(mc), "Test world/player disconnected");
                Case test = cases[index];
                switch (phase) {
                    case PREPARE -> {
                        stop(mc);
                        if (work.isDone()) {
                            work.join();
                            work = null;
                            phase = Phase.SYNC;
                        }
                    }
                    case SYNC -> {
                        stop(mc);
                        // Server inventory updates do not choose the client's selected hotbar slot.
                        mc.player.getInventory().selected = 0;
                        aim(mc, test);
                        if (!ready(mc, test)) {
                            stable = 0;
                            return;
                        }
                        if (++stable < 3) return;
                        if (work == null) work = onServer(player -> snapshot(player, test));
                        if (!work.isDone()) return;
                        Result authoritative = work.join();
                        work = null;
                        if (!authoritative.grounded() || authoritative.selected() != 0) return;
                        require(authoritative.original() && authoritative.toolMatches() && authoritative.damage() == 0
                                        && authoritative.food() == test.food() && authoritative.level() == 0,
                                "Server preparation did not match the client: " + authoritative);
                        require(authoritative.progressBits() == test.progressBits(), "Wrong server progress: " + authoritative);
                        assertProgress(mc, test);
                        miningTicks = 0;
                        phase = Phase.MINING;
                        mc.options.keyAttack.setDown(true);
                        Mite.LOGGER.info("MITE_GRASS_MINING CASE_START case={} food={} progressBits={} expectedTicks={} target={}",
                                test.name(), test.food(), Integer.toHexString(test.progressBits()), test.ticks(), test.target());
                    }
                    case MINING -> {
                        miningTicks++;
                        if (mc.level.getBlockState(test.target()).isAir()) {
                            stop(mc);
                            counts[index] = miningTicks;
                            work = onServer(player -> snapshot(player, test));
                            phase = Phase.CONFIRM;
                        } else {
                            require(ready(mc, test), "Mining state/focus/tool/target changed at " + miningTicks);
                            require(mc.options.keyAttack.isDown(), "Attack key was released");
                            assertProgress(mc, test);
                            require(miningTicks <= test.ticks() + 6, "Plant did not clear within the independent timing range");
                            mc.options.keyAttack.setDown(true);
                        }
                    }
                    case CONFIRM -> {
                        stop(mc);
                        if (!work.isDone()) return;
                        Result authoritative = work.join();
                        if (!authoritative.cleared() || !mc.level.getBlockState(BASE).isAir()
                                || !mc.level.getBlockState(BASE.above()).isAir()) {
                            work = onServer(player -> snapshot(player, test));
                            return;
                        }
                        require(authoritative.toolMatches() && authoritative.damage() == test.finalDamage()
                                        && authoritative.food() == test.food() && authoritative.level() == 0,
                                "Unexpected authoritative result: " + authoritative);
                        if (test.grassDrop()) require(authoritative.grassDrops() == 1,
                                "Shears did not produce exactly one short-grass item: " + authoritative);
                        int maximumTicks = test.grassDrop() ? 6 : test.ticks() + 6;
                        require(counts[index] >= test.ticks() && counts[index] <= maximumTicks,
                                "Completion outside independent timing range: " + counts[index]);
                        Mite.LOGGER.info("MITE_GRASS_MINING CASE_PASS case={} clientTicks={} server={}",
                                test.name(), counts[index], authoritative);
                        if (index + 1 == cases.length) {
                            displayWork = onServer(player -> {
                                // Only after all measured outcomes are recorded, leave a fresh club for visual inspection.
                                player.getInventory().setItem(0, new ItemStack(Items.WOODEN_SWORD));
                                player.getInventory().selected = 0;
                                player.getFoodData().setFoodLevel(6);
                                player.getInventory().setChanged();
                                player.inventoryMenu.broadcastChanges();
                                return null;
                            });
                            phase = Phase.DISPLAY;
                        } else {
                            index++;
                            prepare(false);
                        }
                    }
                    case DISPLAY -> {
                        stop(mc);
                        mc.player.getInventory().selected = 0;
                        if (!displayWork.isDone()) return;
                        displayWork.join();
                        ItemStack club = mc.player.getMainHandItem();
                        if (club.is(Items.WOODEN_SWORD) && club.getCount() == 1 && club.getDamageValue() == 0) pass(mc);
                    }
                    case DONE -> { }
                }
            } catch (RuntimeException failure) {
                fail(mc, failure.toString());
            }
        }

        private boolean ready(Minecraft mc, Case test) {
            return focused(mc) && mc.player.onGround() && mc.player.position().distanceToSqr(STAND) < 0.1
                    && !mc.player.isCreative() && !mc.player.isSpectator() && !mc.player.isSprinting()
                    && mc.player.getActiveEffects().isEmpty() && mc.player.experienceLevel == 0
                    && mc.player.getFoodData().getFoodLevel() == test.food() && mc.player.getInventory().selected == 0
                    && matchesTool(mc.player.getMainHandItem(), test) && mc.player.getMainHandItem().getDamageValue() == 0
                    && mc.level.getBlockState(test.target()).equals(test.targetState())
                    && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(test.target());
        }

        private <T> CompletableFuture<T> onServer(Function<ServerPlayer, T> action) {
            CompletableFuture<T> result = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    require(active.get(), "Test no longer active");
                    require(WORLD.equals(server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString()),
                            "Refusing to modify a non-test save");
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    require(player != null && player.serverLevel() == server.overworld(), "Overworld player unavailable");
                    result.complete(action.apply(player));
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
            return result;
        }

        private void pass(Minecraft mc) {
            stop(mc);
            active.set(false);
            phase = Phase.DONE;
            String message = "MITE_GRASS_MINING PASS — 6 项草类采集实测通过，主手木棒供模型检查";
            Mite.LOGGER.info("{} counts={} elapsedTicks={}; tools and test plants supplied by fixture",
                    message, Arrays.toString(counts), elapsed);
            mc.gui.getChat().addMessage(Component.literal(message));
            mc.gui.setOverlayMessage(Component.literal(message), false);
        }

        private void fail(Minecraft mc, String reason) {
            stop(mc);
            active.set(false);
            Mite.LOGGER.error("MITE_GRASS_MINING FAIL phase={} case={} reason={}", phase, cases[index].name(), reason);
            phase = Phase.DONE;
            mc.gui.getChat().addMessage(Component.literal("MITE_GRASS_MINING FAIL: " + reason));
        }
    }

    private static Result snapshot(ServerPlayer player, Case test) {
        var world = player.serverLevel();
        BlockState state = world.getBlockState(test.target());
        int grassDrops = 0;
        for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, AREA)) {
            if (item.getItem().is(Items.SHORT_GRASS)) grassDrops += item.getItem().getCount();
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item.is(Items.SHORT_GRASS)) grassDrops += item.getCount();
        }
        return new Result(state.equals(test.targetState()), world.getBlockState(BASE).isAir(),
                world.getBlockState(BASE.above()).isAir(), grassDrops, matchesTool(player.getMainHandItem(), test),
                player.getMainHandItem().getDamageValue(), player.getFoodData().getFoodLevel(), player.experienceLevel,
                player.getInventory().selected, player.onGround(),
                state.isAir() ? 0 : Float.floatToRawIntBits(state.getDestroyProgress(player, world, test.target())));
    }

    private static boolean matchesTool(ItemStack stack, Case test) {
        return test.tool() == Items.AIR ? stack.isEmpty() : stack.is(test.tool()) && stack.getCount() == 1;
    }

    private static void assertProgress(Minecraft mc, Case test) {
        int bits = Float.floatToRawIntBits(mc.level.getBlockState(test.target())
                .getDestroyProgress(mc.player, mc.level, test.target()));
        require(bits == test.progressBits(), "Wrong client progress in " + test.name() + ": " + Integer.toHexString(bits));
    }

    private static void aim(Minecraft mc, Case test) {
        var shape = mc.level.getBlockState(test.target()).getShape(mc.level, test.target());
        if (shape.isEmpty()) return;
        // Use the actual selection shape, including any block-provided positional offset.
        // Do not override hitResult or send a fabricated target packet.
        Vec3 point = shape.bounds().getCenter().add(test.target().getX(), test.target().getY(), test.target().getZ());
        Vec3 difference = point.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(difference.z, difference.x)) - 90.0));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(difference.y, Math.hypot(difference.x, difference.z))));
    }

    private static boolean focused(Minecraft mc) {
        return mc.screen == null && mc.getOverlay() == null && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed();
    }

    private static void stop(Minecraft mc) {
        mc.options.keyAttack.setDown(false);
        if (mc.player != null && mc.level != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
