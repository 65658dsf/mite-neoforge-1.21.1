package io.github.ningmeng123.mite.gametest.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteItems;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Normal-input acceptance in one explicitly opted-in disposable world. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class LogHarvestClientSmoke {
    private static final String WORLD = "MITE Log Harvest Test-011";
    private static final BlockPos TARGET = new BlockPos(11005, 200, 11006);
    private static final Vec3 STAND = new Vec3(11005.5, 200.0, 11003.5);
    private static final AABB AREA = new AABB(11000, 199, 11000, 11010, 204, 11010);
    private static Runner runner;

    private LogHarvestClientSmoke() {}

    private record Case(String name, BlockState state, Item tool, int level, int progressBits,
            int ticks, boolean blocked, int finalDamage) {}
    private record Result(boolean original, boolean air, int drops, int damage, int progressBits) {}
    private enum Phase { PREPARE, SYNC, MINING, CONFIRM, DONE }

    @SubscribeEvent
    public static void beforeTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (runner != null && runner.active.get() && runner.phase == Phase.MINING && runner.samePlayer(mc)
                && mc.screen == null && mc.getOverlay() == null && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed()) {
            aim(mc);
            mc.options.keyAttack.setDown(true);
        }
    }

    @SubscribeEvent
    public static void afterTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("mite.logHarvestSmoke")) {
            if (runner != null && runner.active.get()) runner.fail(mc, "Opt-in was disabled");
            return;
        }
        if (runner == null) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null
                    || mc.getSingleplayerServer() == null) return;
            runner = new Runner(mc.getSingleplayerServer(), mc.player.getUUID());
            if (Boolean.getBoolean("mite.miningSmoke") || Boolean.getBoolean("mite.miningBaseSmoke")
                    || Boolean.getBoolean("mite.miningHungerSmoke") || Boolean.getBoolean("mite.flintStartSmoke")) {
                runner.fail(mc, "Only logHarvestSmoke may be enabled");
                return;
            }
            Mite.LOGGER.info("MITE_LOG_HARVEST START copy={} test-supplied tools; normal attack input, drops enabled", WORLD);
            runner.prepare(true);
        }
        runner.tick(mc);
    }

    private static final class Runner {
        private final IntegratedServer server;
        private final UUID playerId;
        private final AtomicBoolean active = new AtomicBoolean(true);
        // Independent literal IEEE-754 vectors; blocked cases are observation durations, not completion times.
        private final Case[] cases = {
                new Case("oak_empty", Blocks.OAK_LOG.defaultBlockState(), Items.AIR, 0, 0, 80, true, 0),
                new Case("oak_stick", Blocks.OAK_LOG.defaultBlockState(), Items.STICK, 0, 0, 60, true, 0),
                new Case("oak_wooden_axe_level_200", Blocks.OAK_LOG.defaultBlockState(), Items.WOODEN_AXE, 200, 0, 80, true, 0),
                new Case("oak_diamond_pickaxe", Blocks.OAK_LOG.defaultBlockState(), Items.DIAMOND_PICKAXE, 0, 0, 80, true, 0),
                new Case("oak_flint_hatchet", Blocks.OAK_LOG.defaultBlockState(), MiteItems.FLINT_HATCHET.get(), 0, 0x3b855555, 246, false, 160),
                new Case("stripped_crimson_stem_flint", Blocks.STRIPPED_CRIMSON_STEM.defaultBlockState(), MiteItems.FLINT_HATCHET.get(), 0, 0x3b855555, 246, false, 160),
                new Case("oak_stone_axe", Blocks.OAK_LOG.defaultBlockState(), Items.STONE_AXE, 0, 0x3bd55555, 154, false, 1),
                new Case("oak_planks_empty_level_200", Blocks.OAK_PLANKS.defaultBlockState(), Items.AIR, 200, 0x3ba00000, 205, false, 0)
        };
        private final int[] counts = new int[cases.length];
        private CompletableFuture<Result> work;
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
                    world.setDayTime(6000L);
                    world.setWeatherParameters(6000, 0, false, false);
                    server.setDifficulty(Difficulty.PEACEFUL, true);
                    for (int x = 11001; x <= 11009; x++) {
                        for (int z = 11001; z <= 11009; z++) {
                            world.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState());
                            for (int y = 200; y <= 203; y++) world.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                // Clear only the dedicated fixture between cases. MINING/CONFIRM never replace the target.
                for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, AREA)) item.discard();
                world.setBlockAndUpdate(TARGET, test.state());
                player.setGameMode(GameType.SURVIVAL);
                player.removeAllEffects();
                player.stopUsingItem();
                player.getInventory().clearContent();
                player.getInventory().selected = 0;
                player.getInventory().setItem(0, test.tool() == Items.AIR ? ItemStack.EMPTY : new ItemStack(test.tool()));
                player.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(1.0);
                player.getAttribute(Attributes.MINING_EFFICIENCY).setBaseValue(0.0);
                PlayerExperience.setLevels(player, test.level());
                player.setExperiencePoints(0);
                player.getFoodData().setFoodLevel(test.level() == 0 ? 6 : 20);
                player.getFoodData().setSaturation(0.0F);
                player.getFoodData().setExhaustion(0.0F);
                player.setHealth(player.getMaxHealth());
                player.teleportTo(world, STAND.x, STAND.y, STAND.z, 0.0F, 20.472F);
                player.setDeltaMovement(Vec3.ZERO);
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
                            phase = Phase.SYNC;
                        }
                    }
                    case SYNC -> {
                        stop(mc);
                        // Selecting the hotbar on the server does not synchronize the selected slot.
                        // The normal client game-mode tick sends this selection back to the server.
                        mc.player.getInventory().selected = 0;
                        aim(mc);
                        if (!ready(mc, test)) {
                            stable = 0;
                            return;
                        }
                        if (++stable < 3) return;
                        assertProgress(mc, test);
                        miningTicks = 0;
                        phase = Phase.MINING;
                        mc.options.keyAttack.setDown(true);
                        Mite.LOGGER.info("MITE_LOG_HARVEST CASE_START case={} blocked={} progressBits={} expectedTicks={}",
                                test.name(), test.blocked(), Integer.toHexString(test.progressBits()), test.ticks());
                    }
                    case MINING -> {
                        miningTicks++;
                        if (!test.blocked() && mc.level.getBlockState(TARGET).isAir()) {
                            finishInput(mc, test);
                        } else {
                            require(ready(mc, test), "Mining state/focus/tool/target changed at " + miningTicks);
                            require(mc.options.keyAttack.isDown(), "Attack key was released");
                            assertProgress(mc, test);
                            if (test.blocked() && miningTicks == test.ticks()) {
                                finishInput(mc, test);
                            } else {
                                mc.options.keyAttack.setDown(true);
                            }
                        }
                    }
                    case CONFIRM -> {
                        stop(mc);
                        if (!work.isDone()) return;
                        Result state = work.join();
                        if (!test.blocked() && !state.air()) {
                            work = onServer(player -> snapshot(player, test));
                            return;
                        }
                        if (test.blocked()) {
                            require(state.original() && !state.air() && state.drops() == 0 && state.damage() == 0
                                    && state.progressBits() == 0, "Rejected mining changed authoritative state: " + state);
                        } else {
                            require(state.air() && state.drops() == 1 && state.damage() == test.finalDamage(),
                                    "Allowed mining has incorrect drop/wear: " + state);
                            require(counts[index] >= test.ticks() && counts[index] <= test.ticks() + 6,
                                    "Completion outside independent timing range: " + counts[index]);
                        }
                        Mite.LOGGER.info("MITE_LOG_HARVEST CASE_PASS case={} clientTicks={} server={}", test.name(), counts[index], state);
                        if (index + 1 == cases.length) {
                            pass(mc);
                        } else {
                            index++;
                            prepare(false);
                        }
                    }
                    case DONE -> { }
                }
            } catch (RuntimeException failure) {
                fail(mc, failure.toString());
            }
        }

        private void finishInput(Minecraft mc, Case test) {
            stop(mc);
            counts[index] = miningTicks;
            work = onServer(player -> snapshot(player, test));
            phase = Phase.CONFIRM;
        }

        private boolean ready(Minecraft mc, Case test) {
            ItemStack held = mc.player.getMainHandItem();
            return mc.screen == null && mc.getOverlay() == null && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed()
                    && mc.player.onGround() && mc.player.position().distanceToSqr(STAND) < 0.1
                    && !mc.player.isCreative() && !mc.player.isSpectator() && mc.player.getActiveEffects().isEmpty()
                    && mc.player.experienceLevel == test.level() && mc.player.getFoodData().getFoodLevel() > 0
                    && (test.tool() == Items.AIR ? held.isEmpty() : held.is(test.tool()) && held.getCount() == 1 && held.getDamageValue() == 0)
                    && mc.level.getBlockState(TARGET).equals(test.state())
                    && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(TARGET);
        }

        private <T> CompletableFuture<T> onServer(Function<ServerPlayer, T> action) {
            CompletableFuture<T> result = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    require(active.get(), "Test no longer active");
                    require(WORLD.equals(server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString()),
                            "Refusing to modify a non-test save");
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    require(player != null, "Server player unavailable");
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
            String message = "MITE_LOG_HARVEST PASS — 8 项原木工具门槛与木板回归实测通过";
            Mite.LOGGER.info("{} counts={} elapsedTicks={}; blocked counts are observation windows", message, Arrays.toString(counts), elapsed);
            mc.gui.getChat().addMessage(Component.literal(message));
            mc.gui.setOverlayMessage(Component.literal(message), false);
        }

        private void fail(Minecraft mc, String reason) {
            stop(mc);
            active.set(false);
            Mite.LOGGER.error("MITE_LOG_HARVEST FAIL phase={} case={} reason={}", phase, cases[index].name(), reason);
            phase = Phase.DONE;
            mc.gui.getChat().addMessage(Component.literal("MITE_LOG_HARVEST FAIL: " + reason));
        }
    }

    private static Result snapshot(ServerPlayer player, Case test) {
        var world = player.serverLevel();
        require(world == player.server.overworld(), "Player left the overworld");
        BlockState state = world.getBlockState(TARGET);
        int drops = 0;
        Item expectedDrop = test.state().getBlock().asItem();
        for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, AREA)) {
            if (item.getItem().is(expectedDrop)) drops += item.getItem().getCount();
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(expectedDrop)) drops += stack.getCount();
        }
        return new Result(state.equals(test.state()), state.isAir(), drops, player.getMainHandItem().getDamageValue(),
                Float.floatToRawIntBits(state.getDestroyProgress(player, world, TARGET)));
    }

    private static void assertProgress(Minecraft mc, Case test) {
        require(Float.floatToRawIntBits(mc.level.getBlockState(TARGET).getDestroyProgress(mc.player, mc.level, TARGET)) == test.progressBits(),
                "Unexpected progress in " + test.name());
    }

    private static void aim(Minecraft mc) {
        mc.player.setYRot(0.0F);
        mc.player.setXRot(20.472F);
    }

    private static void stop(Minecraft mc) {
        mc.options.keyAttack.setDown(false);
        if (mc.player != null && mc.level != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
