package io.github.ningmeng123.mite.gametest.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
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

/** Supplies test materials, then exercises real menu clicks and mining in one opt-in copy. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class FlintStartClientSmoke {
    private static final String WORLD_FOLDER = "MITE Flint Start Test-010";
    private static final BlockPos TARGET = new BlockPos(11005, 200, 11006);
    private static final Vec3 STAND = new Vec3(11005.5, 200.0, 11003.5);
    private static final AABB AREA = new AABB(11000, 199, 11000, 11010, 204, 11010);
    // Independent float32 calculation: (1 / (1.2F / 2.5F)) / 512F, accumulated 246 times.
    private static final int PROGRESS_BITS = 0x3b855555;
    private static final int MINING_ADDITIONS = 246;
    private static Runner runner;

    private FlintStartClientSmoke() {
    }

    @SubscribeEvent
    public static void beforeClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (runner != null && runner.active.get() && runner.phase == Phase.MINING && runner.samePlayer(mc)
                && mc.screen == null && mc.getOverlay() == null && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed()) {
            pointAtTarget(mc);
            mc.options.keyAttack.setDown(true);
        }
    }

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("mite.flintStartSmoke")) {
            if (runner != null && runner.active.get()) {
                runner.fail(mc, "Opt-in property was disabled");
            }
            return;
        }
        if (runner == null) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null
                    || mc.getSingleplayerServer() == null) {
                return;
            }
            runner = new Runner(mc.getSingleplayerServer(), mc.player.getUUID());
            if (Boolean.getBoolean("mite.miningSmoke") || Boolean.getBoolean("mite.miningBaseSmoke")
                    || Boolean.getBoolean("mite.miningHungerSmoke")) {
                runner.fail(mc, "Do not enable other mining smoke modes with flintStartSmoke");
                return;
            }
            Mite.LOGGER.info("MITE_FLINT_START START copy={} supplied=4chips,1leather,2sticks; no random gathering claim", WORLD_FOLDER);
            runner.work = runner.onServer(player -> {
                prepareWorld(player);
                return snapshot(player);
            });
        }
        runner.tick(mc);
    }

    private enum Phase {
        SETUP, WAIT_INITIAL, CLICK_INPUT, WAIT_RESULT, VERIFY_CRAFT, VERIFY_EQUIP,
        PREPARE_LOG, WAIT_LOG, MINING, VERIFY_LOG, MOVE_PICKUP, WAIT_PICKUP, DONE
    }

    private record Click(int slot, int button, ClickType type) {
    }

    private record Snapshot(int chips, int flint, int leather, int sinew, int sticks, int hatchets,
            int hatchetDamage, int heldDamage, int logs, int totalLogs, boolean gridEmpty,
            boolean cursorEmpty, boolean targetAir) {
        private boolean crafted(int recipe) {
            if (!gridEmpty || !cursorEmpty || chips != 0) {
                return false;
            }
            return switch (recipe) {
                case 0 -> flint == 1 && leather == 1 && sinew == 0 && sticks == 2 && hatchets == 0;
                case 1 -> flint == 1 && leather == 0 && sinew == 4 && sticks == 2 && hatchets == 0;
                case 2 -> flint == 0 && leather == 0 && sinew == 3 && sticks == 0 && hatchets == 1 && hatchetDamage == 0;
                default -> false;
            };
        }
    }

    private static final class Runner {
        private final IntegratedServer server;
        private final UUID playerId;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final ArrayDeque<Click> clicks = new ArrayDeque<>();
        private final int[] miningTicks = new int[3];
        private CompletableFuture<Snapshot> work;
        private Phase phase = Phase.SETUP;
        private int recipeIndex;
        private int logIndex;
        private int elapsedTicks;
        private int stableTicks;
        private int clickDelay;
        private int currentMiningTicks;
        private int nextPickupSeekTick;

        private Runner(IntegratedServer server, UUID playerId) {
            this.server = server;
            this.playerId = playerId;
        }

        private boolean samePlayer(Minecraft mc) {
            return mc.getSingleplayerServer() == server && mc.player != null && mc.level != null
                    && mc.gameMode != null && playerId.equals(mc.player.getUUID());
        }

        private void tick(Minecraft mc) {
            if (!active.get()) {
                return;
            }
            try {
                require(++elapsedTicks <= 4000, "Timed out after 4000 client ticks at " + phase);
                require(samePlayer(mc), "The test player or world was disconnected");
                switch (phase) {
                    case SETUP -> {
                        stopMining(mc);
                        if (work.isDone()) {
                            work.join();
                            phase = Phase.WAIT_INITIAL;
                        }
                    }
                    case WAIT_INITIAL -> {
                        stopMining(mc);
                        boolean ready = mc.screen == null && mc.getOverlay() == null && mc.isWindowActive()
                                && mc.mouseHandler.isMouseGrabbed() && mc.player.onGround()
                                && mc.player.position().distanceToSqr(STAND) < 0.1
                                && mc.player.experienceLevel == 0 && mc.player.getFoodData().getFoodLevel() == 6
                                && !mc.player.isCreative() && !mc.player.isSpectator()
                                && count(mc.player, MiteItems.FLINT_CHIP.get()) == 4
                                && count(mc.player, Items.LEATHER) == 1 && count(mc.player, Items.STICK) == 2;
                        if (!stable(ready)) {
                            return;
                        }
                        mc.setScreen(new InventoryScreen(mc.player));
                        planRecipe(mc);
                    }
                    case CLICK_INPUT -> {
                        requireInventoryScreen(mc);
                        if (clickDelay-- > 0) {
                            return;
                        }
                        if (clicks.isEmpty()) {
                            stableTicks = 0;
                            phase = Phase.WAIT_RESULT;
                        } else {
                            Click click = clicks.removeFirst();
                            click(mc, click.slot(), click.button(), click.type());
                            clickDelay = 2;
                        }
                    }
                    case WAIT_RESULT -> {
                        requireInventoryScreen(mc);
                        Item expected = recipeIndex == 0 ? Items.FLINT
                                : recipeIndex == 1 ? MiteItems.SINEW.get() : MiteItems.FLINT_HATCHET.get();
                        int expectedCount = recipeIndex == 1 ? 4 : 1;
                        ItemStack output = mc.player.inventoryMenu.getSlot(0).getItem();
                        if (!stable(output.is(expected) && output.getCount() == expectedCount
                                && mc.player.inventoryMenu.getCarried().isEmpty())) {
                            return;
                        }
                        // A single normal shift-click consumes inputs and moves the actual server-crafted result.
                        click(mc, 0, 0, ClickType.QUICK_MOVE);
                        work = readServer();
                        phase = Phase.VERIFY_CRAFT;
                    }
                    case VERIFY_CRAFT -> {
                        requireInventoryScreen(mc);
                        if (!work.isDone()) {
                            return;
                        }
                        Snapshot state = work.join();
                        if (!state.crafted(recipeIndex) || !clientCrafted(mc, recipeIndex)) {
                            work = readServer();
                            return;
                        }
                        Mite.LOGGER.info("MITE_FLINT_START CRAFT_PASS stage={} server={}", recipeIndex + 1, state);
                        if (++recipeIndex < 3) {
                            planRecipe(mc);
                        } else {
                            int slot = findSlot(mc, MiteItems.FLINT_HATCHET.get());
                            click(mc, slot, 0, ClickType.SWAP);
                            mc.player.getInventory().selected = 0;
                            work = readServer();
                            stableTicks = 0;
                            phase = Phase.VERIFY_EQUIP;
                        }
                    }
                    case VERIFY_EQUIP -> {
                        requireInventoryScreen(mc);
                        if (!work.isDone()) {
                            return;
                        }
                        Snapshot state = work.join();
                        if (!state.crafted(2) || state.heldDamage() != 0 || !heldAtDamage(mc.player, 0)) {
                            work = readServer();
                            stableTicks = 0;
                            return;
                        }
                        if (++stableTicks < 3) {
                            return;
                        }
                        mc.player.closeContainer();
                        prepareLog();
                    }
                    case PREPARE_LOG -> {
                        stopMining(mc);
                        if (work.isDone()) {
                            work.join();
                            stableTicks = 0;
                            phase = Phase.WAIT_LOG;
                        }
                    }
                    case WAIT_LOG -> {
                        stopMining(mc);
                        pointAtTarget(mc);
                        if (!stable(mc.screen == null && mc.getOverlay() == null && mc.isWindowActive()
                                && mc.mouseHandler.isMouseGrabbed() && mc.player.onGround()
                                && mc.player.position().distanceToSqr(STAND) < 0.1
                                && mc.player.experienceLevel == 0 && mc.player.getFoodData().getFoodLevel() > 0
                                && heldAtDamage(mc.player, logIndex * 160)
                                && mc.level.getBlockState(TARGET).is(Blocks.OAK_LOG) && hitsTarget(mc))) {
                            return;
                        }
                        assertProgress(mc);
                        currentMiningTicks = 0;
                        phase = Phase.MINING;
                        mc.options.keyAttack.setDown(true);
                        Mite.LOGGER.info("MITE_FLINT_START LOG_START index={} damage={} progressBits=0x3b855555 additions=246",
                                logIndex + 1, logIndex * 160);
                    }
                    case MINING -> {
                        currentMiningTicks++;
                        if (mc.level.getBlockState(TARGET).isAir()) {
                            stopMining(mc);
                            miningTicks[logIndex] = currentMiningTicks;
                            work = readServer();
                            phase = Phase.VERIFY_LOG;
                        } else {
                            require(mc.screen == null && mc.getOverlay() == null && mc.isWindowActive()
                                    && mc.mouseHandler.isMouseGrabbed(), "Mining focus or screen changed");
                            require(mc.options.keyAttack.isDown(), "Attack input was released");
                            require(hitsTarget(mc), "Mining target drifted at client tick " + currentMiningTicks);
                            require(mc.player.onGround() && mc.player.experienceLevel == 0
                                    && mc.player.getFoodData().getFoodLevel() > 0, "Mining player state changed");
                            require(heldAtDamage(mc.player, logIndex * 160), "Crafted hatchet changed before block destruction");
                            assertProgress(mc);
                            pointAtTarget(mc);
                            mc.options.keyAttack.setDown(true);
                        }
                    }
                    case VERIFY_LOG -> {
                        stopMining(mc);
                        if (!work.isDone()) {
                            return;
                        }
                        Snapshot state = work.join();
                        if (!state.targetAir()) {
                            work = readServer();
                            return;
                        }
                        int remainingHatchets = logIndex == 2 ? 0 : 1;
                        int damage = logIndex == 2 ? -1 : (logIndex + 1) * 160;
                        require(state.hatchets() == remainingHatchets && state.hatchetDamage() == damage,
                                "Unexpected authoritative durability after log " + (logIndex + 1) + ": " + state);
                        require(state.totalLogs() == logIndex + 1 && state.sinew() == 3,
                                "Unexpected real drops or remaining binding: " + state);
                        require(miningTicks[logIndex] >= MINING_ADDITIONS && miningTicks[logIndex] <= MINING_ADDITIONS + 6,
                                "Mining ticks outside [246,252]: " + miningTicks[logIndex]);
                        Mite.LOGGER.info("MITE_FLINT_START LOG_PASS index={} clientTicks={} serverAir=true server={}",
                                logIndex + 1, miningTicks[logIndex], state);
                        if (++logIndex < 3) {
                            prepareLog();
                        } else {
                            work = onServer(player -> {
                                // Move to the actual drops. ItemEntity/playerTouch performs pickup naturally.
                                player.teleportTo(server.overworld(), TARGET.getX() + 0.5, TARGET.getY(), TARGET.getZ() + 0.5, 0.0F, 20.472F);
                                return snapshot(player);
                            });
                            phase = Phase.MOVE_PICKUP;
                        }
                    }
                    case MOVE_PICKUP -> {
                        stopMining(mc);
                        if (work.isDone()) {
                            work.join();
                            work = readServer();
                            nextPickupSeekTick = elapsedTicks + 10;
                            phase = Phase.WAIT_PICKUP;
                        }
                    }
                    case WAIT_PICKUP -> {
                        stopMining(mc);
                        if (!work.isDone()) {
                            return;
                        }
                        Snapshot state = work.join();
                        if (state.logs() != 3 || state.hatchets() != 0 || state.sinew() != 3
                                || count(mc.player, Items.OAK_LOG) != 3 || count(mc.player, MiteItems.SINEW.get()) != 3
                                || count(mc.player, MiteItems.FLINT_HATCHET.get()) != 0 || !mc.isWindowActive()) {
                            if (state.logs() < 3 && elapsedTicks >= nextPickupSeekTick) {
                                nextPickupSeekTick = elapsedTicks + 10;
                                work = seekRemainingLogs();
                            } else {
                                work = readServer();
                            }
                            return;
                        }
                        pass(mc, state);
                    }
                    case DONE -> { }
                }
            } catch (RuntimeException exception) {
                fail(mc, exception.toString());
            }
        }

        private boolean stable(boolean condition) {
            stableTicks = condition ? stableTicks + 1 : 0;
            return stableTicks >= 3;
        }

        private void planRecipe(Minecraft mc) {
            requireInventoryScreen(mc);
            require(gridEmpty(mc.player) && mc.player.inventoryMenu.getCarried().isEmpty(), "Crafting grid or cursor was not empty");
            clicks.clear();
            if (recipeIndex == 0) {
                int source = findSlot(mc, MiteItems.FLINT_CHIP.get());
                clicks.add(new Click(source, 0, ClickType.PICKUP));
                for (int slot = 1; slot <= 4; slot++) {
                    clicks.add(new Click(slot, 1, ClickType.PICKUP));
                }
            } else if (recipeIndex == 1) {
                moveOne(mc, Items.LEATHER, 1);
            } else {
                // Original 2x2 skeleton: flint/stick on top, binding/stick beneath.
                moveOne(mc, Items.FLINT, 1);
                moveOne(mc, Items.STICK, 2);
                moveOne(mc, MiteItems.SINEW.get(), 3);
                moveOne(mc, Items.STICK, 4);
            }
            clickDelay = 2;
            stableTicks = 0;
            phase = Phase.CLICK_INPUT;
        }

        private void moveOne(Minecraft mc, Item item, int destination) {
            int source = findSlot(mc, item);
            clicks.add(new Click(source, 0, ClickType.PICKUP));
            clicks.add(new Click(destination, 1, ClickType.PICKUP));
            clicks.add(new Click(source, 0, ClickType.PICKUP));
        }

        private void prepareLog() {
            work = onServer(player -> {
                require(player.serverLevel().getBlockState(TARGET).isAir(), "Refusing to overwrite a surviving target");
                player.serverLevel().setBlockAndUpdate(TARGET, Blocks.OAK_LOG.defaultBlockState());
                return snapshot(player);
            });
            phase = Phase.PREPARE_LOG;
        }

        private CompletableFuture<Snapshot> readServer() {
            return onServer(FlintStartClientSmoke::snapshot);
        }

        private CompletableFuture<Snapshot> seekRemainingLogs() {
            return onServer(player -> {
                ServerLevel world = server.overworld();
                require(player.serverLevel() == world, "Player left the overworld during drop pickup");
                if (count(player, Items.OAK_LOG) < 3) {
                    for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, AREA)) {
                        if (!item.isRemoved() && item.getItem().is(Items.OAK_LOG)) {
                            // Follow a scattered real drop; the next world tick performs normal pickup.
                            player.teleportTo(world, item.getX(), 200.0, item.getZ(), 0.0F, 20.472F);
                            player.setDeltaMovement(Vec3.ZERO);
                            break;
                        }
                    }
                }
                return snapshot(player);
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
                    require(player != null, "Server player disconnected");
                    result.complete(action.apply(player));
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            });
            return result;
        }

        private void pass(Minecraft mc, Snapshot state) {
            stopMining(mc);
            active.set(false);
            phase = Phase.DONE;
            mc.setScreen(new InventoryScreen(mc.player));
            String message = "MITE_FLINT_START PASS — 真实合成、3 次砍木及短斧损坏通过；剩余 3 筋和拾取的 3 原木";
            Mite.LOGGER.info("{} clientTicks={} elapsedTicks={} server={}", message, Arrays.toString(miningTicks), elapsedTicks, state);
            mc.gui.getChat().addMessage(Component.literal(message));
            mc.gui.setOverlayMessage(Component.literal("MITE_FLINT_START PASS — 原料由测试提供，可按 F2"), false);
        }

        private void fail(Minecraft mc, String reason) {
            stopMining(mc);
            active.set(false);
            Phase failedPhase = phase;
            phase = Phase.DONE;
            Mite.LOGGER.error("MITE_FLINT_START FAIL phase={} reason={}", failedPhase, reason);
            mc.gui.getChat().addMessage(Component.literal("MITE_FLINT_START FAIL: " + reason));
        }
    }

    private static void prepareWorld(ServerPlayer player) {
        ServerLevel world = player.server.overworld();
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
        world.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, player.server);
        world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        world.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, player.server);
        world.setDayTime(6000L);
        world.setWeatherParameters(6000, 0, false, false);
        player.server.setDifficulty(Difficulty.PEACEFUL, true);
        for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, AREA)) {
            item.discard();
        }
        for (int x = 11001; x <= 11009; x++) {
            for (int z = 11001; z <= 11009; z++) {
                world.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState());
                for (int y = 200; y <= 203; y++) {
                    world.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        player.setGameMode(GameType.SURVIVAL);
        player.stopUsingItem();
        player.removeAllEffects();
        player.getInventory().clearContent();
        player.inventoryMenu.clearCraftingContent();
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(MiteItems.FLINT_CHIP.get(), 4));
        player.getInventory().setItem(1, new ItemStack(Items.LEATHER));
        player.getInventory().setItem(2, new ItemStack(Items.STICK, 2));
        player.getInventory().selected = 0;
        player.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(1.0);
        player.getAttribute(Attributes.MINING_EFFICIENCY).setBaseValue(0.0);
        PlayerExperience.setTotal(player, 0);
        player.setExperiencePoints(0);
        player.getFoodData().setFoodLevel(6);
        player.getFoodData().setSaturation(0.0F);
        player.getFoodData().setExhaustion(0.0F);
        player.setHealth(6.0F);
        player.teleportTo(world, STAND.x, STAND.y, STAND.z, 0.0F, 20.472F);
        player.setDeltaMovement(Vec3.ZERO);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static Snapshot snapshot(ServerPlayer player) {
        int hatchetDamage = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item.is(MiteItems.FLINT_HATCHET.get())) {
                hatchetDamage = item.getDamageValue();
            }
        }
        int logs = count(player, Items.OAK_LOG);
        int totalLogs = logs;
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(ItemEntity.class, AREA)) {
            if (item.getItem().is(Items.OAK_LOG)) {
                totalLogs += item.getItem().getCount();
            }
        }
        return new Snapshot(count(player, MiteItems.FLINT_CHIP.get()), count(player, Items.FLINT), count(player, Items.LEATHER),
                count(player, MiteItems.SINEW.get()), count(player, Items.STICK), count(player, MiteItems.FLINT_HATCHET.get()),
                hatchetDamage, player.getMainHandItem().is(MiteItems.FLINT_HATCHET.get()) ? player.getMainHandItem().getDamageValue() : -1,
                logs, totalLogs, gridEmpty(player), player.inventoryMenu.getCarried().isEmpty(), player.serverLevel().getBlockState(TARGET).isAir());
    }

    private static boolean clientCrafted(Minecraft mc, int recipe) {
        if (!gridEmpty(mc.player) || !mc.player.inventoryMenu.getCarried().isEmpty() || count(mc.player, MiteItems.FLINT_CHIP.get()) != 0) {
            return false;
        }
        return switch (recipe) {
            case 0 -> count(mc.player, Items.FLINT) == 1 && count(mc.player, Items.LEATHER) == 1 && count(mc.player, Items.STICK) == 2;
            case 1 -> count(mc.player, Items.FLINT) == 1 && count(mc.player, MiteItems.SINEW.get()) == 4 && count(mc.player, Items.STICK) == 2;
            case 2 -> count(mc.player, MiteItems.FLINT_HATCHET.get()) == 1 && count(mc.player, MiteItems.SINEW.get()) == 3
                    && count(mc.player, Items.FLINT) == 0 && count(mc.player, Items.STICK) == 0;
            default -> false;
        };
    }

    private static int count(Player player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean gridEmpty(Player player) {
        for (int slot = 1; slot <= 4; slot++) {
            if (player.inventoryMenu.getSlot(slot).hasItem()) {
                return false;
            }
        }
        return true;
    }

    private static int findSlot(Minecraft mc, Item item) {
        for (int slot = 9; slot <= 44; slot++) {
            if (mc.player.inventoryMenu.getSlot(slot).getItem().is(item)) {
                return slot;
            }
        }
        throw new IllegalStateException("Missing recipe ingredient or crafted tool: " + item);
    }

    private static void requireInventoryScreen(Minecraft mc) {
        stopMining(mc);
        require(mc.screen instanceof InventoryScreen && mc.getOverlay() == null && mc.isWindowActive()
                && mc.player.containerMenu == mc.player.inventoryMenu, "Inventory screen or focus changed during crafting");
    }

    private static void click(Minecraft mc, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, button, type, mc.player);
    }

    private static boolean heldAtDamage(Player player, int damage) {
        return player.getMainHandItem().is(MiteItems.FLINT_HATCHET.get()) && player.getMainHandItem().getCount() == 1
                && player.getMainHandItem().getDamageValue() == damage;
    }

    private static boolean hitsTarget(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(TARGET);
    }

    private static void assertProgress(Minecraft mc) {
        BlockState state = mc.level.getBlockState(TARGET);
        require(state.is(Blocks.OAK_LOG), "Target is no longer the oak log");
        require(Float.floatToRawIntBits(mc.player.getDigSpeed(state, TARGET)) == 0x40200000, "Hatchet dig strength is not 2.5");
        require(Float.floatToRawIntBits(state.getDestroyProgress(mc.player, mc.level, TARGET)) == PROGRESS_BITS,
                "Unexpected oak log mining progress");
    }

    private static void pointAtTarget(Minecraft mc) {
        mc.player.setYRot(0.0F);
        mc.player.setXRot(20.472F);
    }

    private static void stopMining(Minecraft mc) {
        mc.options.keyAttack.setDown(false);
        if (mc.player != null && mc.level != null && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
