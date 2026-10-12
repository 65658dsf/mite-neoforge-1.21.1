package io.github.ningmeng123.mite.gametest.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real client crafting, placement and workbench use in one opted-in disposable save. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class FlintWorkbenchClientSmoke {
    private static final String WORLD = "MITE Flint Workbench Test-014";
    private static final BlockPos TABLE = new BlockPos(15005, 200, 15006);
    private static final Vec3 STAND = new Vec3(15005.5, 200.0, 15003.5);
    private static final AABB AREA = new AABB(15000, 199, 15000, 15011, 205, 15011);
    private static Runner runner;

    private FlintWorkbenchClientSmoke() {}

    private enum Phase {
        SETUP, WAIT_INITIAL, INPUT, WAIT_LAYOUT, CLEAR, WAIT_CLEAR, VERIFY_CRAFT,
        WAIT_EQUIPPED, PLACE, WAIT_PLACED, OPEN, WAIT_TABLE, DONE
    }

    private record Click(int slot, int button, ClickType type) {}

    private record Counts(int logs, int planks, int flint, int string, int sticks, int tables) {}

    private static final Counts INITIAL = new Counts(2, 4, 1, 1, 1, 0);
    private static final Counts CRAFTED = new Counts(1, 4, 0, 0, 0, 1);
    private static final Counts PLACED = new Counts(1, 4, 0, 0, 0, 0);
    private static final Counts FINISHED = new Counts(0, 8, 0, 0, 0, 0);

    private record Snapshot(Counts inventory, List<String> grid, boolean cursorEmpty, boolean workbenchOpen,
            String output, int outputCount, boolean tablePlaced, int droppedItems, int selected, boolean holdsTable) {
        private boolean emptyGrid() { return grid.stream().allMatch(String::isEmpty); }
    }

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("mite.flintWorkbenchSmoke")) {
            if (runner != null && runner.active.get()) runner.fail(mc, "Opt-in was disabled");
            return;
        }
        if (runner == null) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null
                    || mc.getSingleplayerServer() == null) return;
            runner = new Runner(mc.getSingleplayerServer(), mc.player.getUUID());
            for (String property : new String[]{"mite.miningSmoke", "mite.miningBaseSmoke", "mite.miningHungerSmoke",
                    "mite.flintStartSmoke", "mite.logHarvestSmoke", "mite.woodenEquipmentSmoke", "mite.grassMiningSmoke"}) {
                if (Boolean.getBoolean(property)) {
                    runner.fail(mc, "Do not enable another smoke fixture: " + property);
                    return;
                }
            }
            Mite.LOGGER.info("MITE_FLINT_WORKBENCH START copy={} supplied=2logs,4planks,1flint,1string,1stick; real menu and interaction packets", WORLD);
            runner.work = runner.onServer(player -> {
                prepareWorld(player);
                return snapshot(player);
            });
        }
        runner.tick(mc);
    }

    private static final class Runner {
        private final IntegratedServer server;
        private final UUID playerId;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final ArrayDeque<Click> clicks = new ArrayDeque<>();
        private CompletableFuture<Snapshot> work;
        private Phase phase = Phase.SETUP;
        private int recipe;
        private int elapsed;
        private int stableTicks;
        private int clickDelay;

        private Runner(IntegratedServer server, UUID playerId) {
            this.server = server;
            this.playerId = playerId;
        }

        private void tick(Minecraft mc) {
            if (!active.get()) return;
            try {
                require(++elapsed <= 4000, "Timeout at " + phase);
                require(mc.getSingleplayerServer() == server && mc.player != null && mc.level != null && mc.gameMode != null
                        && playerId.equals(mc.player.getUUID()), "Test world/player disconnected");
                stopAttack(mc);
                switch (phase) {
                    case SETUP -> {
                        if (pollServer() != null) phase = Phase.WAIT_INITIAL;
                    }
                    case WAIT_INITIAL -> {
                        mc.player.getInventory().selected = 0;
                        if (!stable(focused(mc) && mc.player.onGround() && mc.player.position().distanceToSqr(STAND) < 0.1
                                && mc.player.experienceLevel == 0 && mc.player.getFoodData().getFoodLevel() == 6
                                && !mc.player.isCreative() && !mc.player.isSpectator() && counts(mc.player).equals(INITIAL))) return;
                        mc.setScreen(new InventoryScreen(mc.player));
                        planRecipe(mc);
                    }
                    case INPUT, CLEAR -> {
                        requireMenu(mc, recipe == 3);
                        if (clickDelay-- > 0) return;
                        if (!clicks.isEmpty()) {
                            Click next = clicks.removeFirst();
                            click(mc, next.slot(), next.button(), next.type());
                            clickDelay = 2;
                        } else {
                            stableTicks = 0;
                            work = null;
                            phase = phase == Phase.INPUT ? Phase.WAIT_LAYOUT : Phase.WAIT_CLEAR;
                        }
                    }
                    case WAIT_LAYOUT -> {
                        requireMenu(mc, recipe == 3);
                        Snapshot state = pollServer();
                        if (state == null) return;
                        List<String> expected = expectedGrid(recipe);
                        if (!stable(state.workbenchOpen() == (recipe == 3) && state.grid().equals(expected)
                                && grid(mc.player).equals(expected) && state.cursorEmpty()
                                && mc.player.containerMenu.getCarried().isEmpty())) return;
                        require(state.droppedItems() == 0, "Ingredients were dropped while filling the grid");
                        ItemStack output = mc.player.containerMenu.getSlot(0).getItem();
                        if (recipe < 2) {
                            require(output.isEmpty() && state.outputCount() == 0, "Forbidden backpack recipe has an output: " + state);
                            // Even a normal shift-click on the empty result must consume no ingredients.
                            click(mc, 0, 0, ClickType.QUICK_MOVE);
                            Mite.LOGGER.info("MITE_FLINT_WORKBENCH RECIPE_BLOCKED recipe={} clientAndServerOutput=empty grid={}",
                                    recipe == 0 ? "backpack_log_to_planks" : "four_planks_old_workbench", state.grid());
                            clicks.clear();
                            for (int slot = 1; slot <= 4; slot++) {
                                if (mc.player.containerMenu.getSlot(slot).hasItem()) clicks.add(new Click(slot, 0, ClickType.QUICK_MOVE));
                            }
                            clickDelay = 2;
                            phase = Phase.CLEAR;
                        } else {
                            Item expectedItem = recipe == 2 ? Items.CRAFTING_TABLE : Items.OAK_PLANKS;
                            int expectedCount = recipe == 2 ? 1 : 4;
                            require(output.is(expectedItem) && output.getCount() == expectedCount
                                            && state.output().equals(BuiltInRegistries.ITEM.getKey(expectedItem).toString())
                                            && state.outputCount() == expectedCount,
                                    "Crafted result did not match on both sides: " + state);
                            click(mc, 0, 0, ClickType.QUICK_MOVE);
                            work = null;
                            stableTicks = 0;
                            phase = Phase.VERIFY_CRAFT;
                        }
                    }
                    case WAIT_CLEAR -> {
                        requireMenu(mc, false);
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!stable(inventoryMatches(mc, state, INITIAL) && !state.tablePlaced())) return;
                        Mite.LOGGER.info("MITE_FLINT_WORKBENCH REFUND_PASS recipe={} unchanged={} cursorAndGrid=empty drops=0", recipe, state.inventory());
                        recipe++;
                        planRecipe(mc);
                    }
                    case VERIFY_CRAFT -> {
                        requireMenu(mc, recipe == 3);
                        Snapshot state = pollServer();
                        if (state == null) return;
                        Counts expected = recipe == 2 ? CRAFTED : FINISHED;
                        if (!stable(inventoryMatches(mc, state, expected))) return;
                        if (recipe == 3) {
                            require(state.tablePlaced() && mc.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE),
                                    "Placed workbench vanished during crafting");
                            Mite.LOGGER.info("MITE_FLINT_WORKBENCH PLANKS_PASS oneShiftClick=true output=4 final={} placedTables=1 drops=0", state.inventory());
                            pass(mc);
                        } else {
                            require(!state.tablePlaced(), "Workbench existed before the player's placement");
                            Mite.LOGGER.info("MITE_FLINT_WORKBENCH CRAFT_PASS recipe=flint+string/stick+log result={} remaining={}",
                                    new ItemStack(Items.CRAFTING_TABLE).getHoverName().getString(), state.inventory());
                            click(mc, findSlot(mc, Items.CRAFTING_TABLE), 0, ClickType.SWAP);
                            mc.player.getInventory().selected = 0;
                            work = null;
                            stableTicks = 0;
                            phase = Phase.WAIT_EQUIPPED;
                        }
                    }
                    case WAIT_EQUIPPED -> {
                        requireMenu(mc, false);
                        mc.player.getInventory().selected = 0;
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!stable(inventoryMatches(mc, state, CRAFTED) && state.selected() == 0 && state.holdsTable()
                                && mc.player.getMainHandItem().is(Items.CRAFTING_TABLE)
                                && mc.player.getMainHandItem().getCount() == 1)) return;
                        mc.player.closeContainer();
                        stableTicks = 0;
                        work = null;
                        phase = Phase.PLACE;
                    }
                    case PLACE -> {
                        mc.player.getInventory().selected = 0;
                        pointAt(mc, new Vec3(TABLE.getX() + 0.5, TABLE.getY() - 0.001, TABLE.getZ() + 0.5));
                        if (!stable(focused(mc) && mc.player.onGround() && !mc.player.isShiftKeyDown()
                                && mc.player.getMainHandItem().is(Items.CRAFTING_TABLE)
                                && mc.level.getBlockState(TABLE).isAir()
                                && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(TABLE.below())
                                && hit.getDirection() == Direction.UP)) return;
                        // Pass the actual client ray-cast result through the normal placement/packet path.
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, (BlockHitResult) mc.hitResult);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        work = null;
                        stableTicks = 0;
                        phase = Phase.WAIT_PLACED;
                    }
                    case WAIT_PLACED -> {
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!stable(focused(mc) && inventoryMatches(mc, state, PLACED)
                                && state.tablePlaced() && mc.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE))) return;
                        Mite.LOGGER.info("MITE_FLINT_WORKBENCH PLACE_PASS normalUseItemOn=true remaining={} placedTables=1", state.inventory());
                        stableTicks = 0;
                        phase = Phase.OPEN;
                    }
                    case OPEN -> {
                        pointAt(mc, Vec3.atCenterOf(TABLE));
                        if (!stable(focused(mc) && !mc.player.isShiftKeyDown()
                                && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(TABLE))) return;
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, (BlockHitResult) mc.hitResult);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        work = null;
                        stableTicks = 0;
                        phase = Phase.WAIT_TABLE;
                    }
                    case WAIT_TABLE -> {
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!stable(mc.screen instanceof CraftingScreen && mc.player.containerMenu instanceof CraftingMenu
                                && state.workbenchOpen() && inventoryMatches(mc, state, PLACED) && state.tablePlaced())) return;
                        recipe = 3;
                        planRecipe(mc);
                    }
                    case DONE -> { }
                }
            } catch (RuntimeException failure) {
                fail(mc, failure.toString());
            }
        }

        private void planRecipe(Minecraft mc) {
            requireMenu(mc, recipe == 3);
            require(grid(mc.player).stream().allMatch(String::isEmpty) && mc.player.containerMenu.getCarried().isEmpty(),
                    "Recipe planning requires empty grid and cursor");
            clicks.clear();
            Item[] ingredients = switch (recipe) {
                case 0 -> new Item[]{Items.OAK_LOG, Items.AIR, Items.AIR, Items.AIR};
                case 1 -> new Item[]{Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS};
                case 2 -> new Item[]{Items.FLINT, Items.STRING, Items.STICK, Items.OAK_LOG};
                case 3 -> new Item[]{Items.OAK_LOG, Items.AIR, Items.AIR, Items.AIR, Items.AIR,
                        Items.AIR, Items.AIR, Items.AIR, Items.AIR};
                default -> throw new IllegalStateException("Unknown recipe " + recipe);
            };
            for (int cell = 0; cell < ingredients.length; cell++) {
                if (ingredients[cell] == Items.AIR) continue;
                int source = findSlot(mc, ingredients[cell]);
                // Each operation returns the remaining stack to its source before the next ingredient.
                clicks.add(new Click(source, 0, ClickType.PICKUP));
                clicks.add(new Click(cell + 1, 1, ClickType.PICKUP));
                clicks.add(new Click(source, 0, ClickType.PICKUP));
            }
            work = null;
            clickDelay = 2;
            stableTicks = 0;
            phase = Phase.INPUT;
        }

        private Snapshot pollServer() {
            if (work == null) {
                work = onServer(FlintWorkbenchClientSmoke::snapshot);
                return null;
            }
            if (!work.isDone()) return null;
            Snapshot state = work.join();
            work = null;
            return state;
        }

        private boolean stable(boolean condition) {
            stableTicks = condition ? stableTicks + 1 : 0;
            return stableTicks >= 3;
        }

        private <T> CompletableFuture<T> onServer(Function<ServerPlayer, T> action) {
            CompletableFuture<T> result = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    require(active.get(), "Fixture no longer active");
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
            stopAttack(mc);
            active.set(false);
            phase = Phase.DONE;
            String tableName = new ItemStack(Items.CRAFTING_TABLE).getHoverName().getString();
            String message = "MITE_FLINT_WORKBENCH PASS — 背包门槛、造台、摆放及台内加工通过；" + tableName + " 1 个，木板 8 个";
            Mite.LOGGER.info("{} elapsedTicks={} cursorAndGrid=empty drops=0; screen left open; save/reload is a separate manual check", message, elapsed);
            mc.gui.getChat().addMessage(Component.literal(message));
            mc.gui.setOverlayMessage(Component.literal(message), false);
        }

        private void fail(Minecraft mc, String reason) {
            stopAttack(mc);
            active.set(false);
            Mite.LOGGER.error("MITE_FLINT_WORKBENCH FAIL phase={} recipe={} reason={}", phase, recipe, reason);
            phase = Phase.DONE;
            mc.gui.getChat().addMessage(Component.literal("MITE_FLINT_WORKBENCH FAIL: " + reason));
        }
    }

    private static void prepareWorld(ServerPlayer player) {
        var world = player.serverLevel();
        var server = player.server;
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        world.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, server);
        world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        world.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        world.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        world.setDayTime(6000L);
        world.setWeatherParameters(6000, 0, false, false);
        server.setDifficulty(Difficulty.NORMAL, true);
        for (int x = 15001; x <= 15009; x++) {
            for (int z = 15001; z <= 15009; z++) {
                world.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState());
                for (int y = 200; y <= 204; y++) world.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, AREA)) item.discard();
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.stopUsingItem();
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
        for (int slot = 1; slot <= 4; slot++) player.inventoryMenu.getSlot(slot).set(ItemStack.EMPTY);
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 2));
        player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 4));
        player.getInventory().setItem(2, new ItemStack(Items.FLINT));
        player.getInventory().setItem(3, new ItemStack(Items.STRING));
        player.getInventory().setItem(4, new ItemStack(Items.STICK));
        player.getInventory().selected = 0;
        PlayerExperience.setTotal(player, 0);
        player.getFoodData().setFoodLevel(6);
        player.getFoodData().setSaturation(0.0F);
        player.getFoodData().setExhaustion(0.0F);
        player.setHealth(player.getMaxHealth());
        player.teleportTo(world, STAND.x, STAND.y, STAND.z, 0.0F, 20.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static Snapshot snapshot(ServerPlayer player) {
        ItemStack output = player.containerMenu.getSlot(0).getItem();
        int drops = 0;
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(ItemEntity.class, AREA)) drops += item.getItem().getCount();
        return new Snapshot(counts(player), grid(player), player.containerMenu.getCarried().isEmpty(),
                player.containerMenu instanceof CraftingMenu,
                output.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), output.getCount(),
                player.serverLevel().getBlockState(TABLE).is(Blocks.CRAFTING_TABLE), drops, player.getInventory().selected,
                player.getMainHandItem().is(Items.CRAFTING_TABLE) && player.getMainHandItem().getCount() == 1);
    }

    private static Counts counts(Player player) {
        return new Counts(count(player, Items.OAK_LOG), count(player, Items.OAK_PLANKS), count(player, Items.FLINT),
                count(player, Items.STRING), count(player, Items.STICK), count(player, Items.CRAFTING_TABLE));
    }

    private static int count(Player player, Item item) {
        int result = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }

    private static List<String> grid(Player player) {
        require(player.containerMenu instanceof InventoryMenu || player.containerMenu instanceof CraftingMenu, "Unexpected menu");
        int size = player.containerMenu instanceof CraftingMenu ? 9 : 4;
        List<String> result = new ArrayList<>();
        for (int slot = 1; slot <= size; slot++) {
            ItemStack stack = player.containerMenu.getSlot(slot).getItem();
            result.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getCount());
        }
        return List.copyOf(result);
    }

    private static List<String> expectedGrid(int recipe) {
        String log = "minecraft:oak_log:1", plank = "minecraft:oak_planks:1", empty = "";
        return switch (recipe) {
            case 0 -> List.of(log, empty, empty, empty);
            case 1 -> List.of(plank, plank, plank, plank);
            case 2 -> List.of("minecraft:flint:1", "minecraft:string:1", "minecraft:stick:1", log);
            case 3 -> List.of(log, empty, empty, empty, empty, empty, empty, empty, empty);
            default -> throw new IllegalStateException("Unknown recipe " + recipe);
        };
    }

    private static boolean inventoryMatches(Minecraft mc, Snapshot state, Counts expected) {
        return state.inventory().equals(expected) && counts(mc.player).equals(expected) && state.emptyGrid()
                && grid(mc.player).stream().allMatch(String::isEmpty) && state.cursorEmpty()
                && mc.player.containerMenu.getCarried().isEmpty() && state.droppedItems() == 0;
    }

    private static int findSlot(Minecraft mc, Item item) {
        int first = mc.player.containerMenu instanceof CraftingMenu ? 10 : 9;
        int last = mc.player.containerMenu instanceof CraftingMenu ? 45 : 44;
        for (int slot = first; slot <= last; slot++) {
            if (mc.player.containerMenu.getSlot(slot).getItem().is(item)) return slot;
        }
        throw new IllegalStateException("Missing ingredient/result " + item);
    }

    private static void click(Minecraft mc, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, button, type, mc.player);
    }

    private static void requireMenu(Minecraft mc, boolean workbench) {
        require(mc.getOverlay() == null && mc.isWindowActive(), "Crafting screen lost focus");
        require(workbench ? mc.screen instanceof CraftingScreen && mc.player.containerMenu instanceof CraftingMenu
                : mc.screen instanceof InventoryScreen && mc.player.containerMenu == mc.player.inventoryMenu,
                "Expected " + (workbench ? "workbench" : "backpack") + " crafting screen");
    }

    private static boolean focused(Minecraft mc) {
        return mc.screen == null && mc.getOverlay() == null && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed();
    }

    private static void pointAt(Minecraft mc, Vec3 point) {
        Vec3 difference = point.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(difference.z, difference.x)) - 90.0));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(difference.y, Math.hypot(difference.x, difference.z))));
    }

    private static void stopAttack(Minecraft mc) {
        mc.options.keyAttack.setDown(false);
        if (mc.player != null && mc.level != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
