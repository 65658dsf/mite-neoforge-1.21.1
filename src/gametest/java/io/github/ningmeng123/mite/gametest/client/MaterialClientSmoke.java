package io.github.ningmeng123.mite.gametest.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteShards;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual crafting, placement and gem-use packets in a named disposable save. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class MaterialClientSmoke {
    private static final String WORLD = "MITE Materials Test-015";
    private static final BlockPos TABLE = new BlockPos(16005, 200, 16006);
    private static final Vec3 STAND = new Vec3(16005.5, 200.0, 16003.5);
    private static final AABB AREA = new AABB(16000, 199, 16000, 16011, 205, 16011);
    private static final float START_HEALTH = 2.0F;
    private static Runner runner;

    private MaterialClientSmoke() {}

    private enum Phase {
        SETUP, WAIT_INITIAL, INPUT, WAIT_LAYOUT, CLEAR, WAIT_CLEAR, VERIFY_CRAFT,
        WAIT_EQUIPPED, PLACE, WAIT_PLACED, OPEN, WAIT_TABLE, USE_GEM, VERIFY_USE, DONE
    }

    private record Click(int slot, int button, ClickType type) {}
    private record Counts(int ingots, int nuggets, int diamonds, int shards, int tables) {}
    private record Ingredient(Item item, int count) {}
    private record Stage(String name, boolean workbench, List<Ingredient> grid,
            Item output, int outputCount, Counts after) {}
    private record Snapshot(Counts inventory, List<String> grid, boolean cursorEmpty, boolean workbench,
            String output, int outputCount, boolean tablePlaced, int droppedItems, boolean legalStacks,
            int selected, String heldItem, int heldCount, int totalXp, int level, float health) {
        private boolean emptyGrid() { return grid.stream().allMatch(String::isEmpty); }
    }

    private static final Counts INITIAL = new Counts(1, 0, 1, 0, 1);
    private static final Counts UNPACKED = new Counts(0, 9, 1, 0, 1);
    private static final Counts PLACED = new Counts(0, 9, 1, 0, 0);
    private static final Counts RESTORED = new Counts(1, 0, 1, 0, 0);
    private static final Counts FRAGMENTED = new Counts(1, 0, 0, 9, 0);
    private static final Counts FINISHED = new Counts(1, 0, 0, 0, 0);

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("mite.materialSmoke")) {
            if (runner != null && runner.active.get()) runner.fail(mc, "Opt-in was disabled");
            return;
        }
        if (runner == null) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null
                    || mc.getSingleplayerServer() == null) return;
            runner = new Runner(mc.getSingleplayerServer(), mc.player.getUUID());
            for (String property : new String[]{"mite.miningSmoke", "mite.miningBaseSmoke", "mite.miningHungerSmoke",
                    "mite.flintStartSmoke", "mite.logHarvestSmoke", "mite.woodenEquipmentSmoke", "mite.grassMiningSmoke",
                    "mite.flintWorkbenchSmoke"}) {
                if (Boolean.getBoolean(property)) {
                    runner.fail(mc, "Do not enable another smoke fixture: " + property);
                    return;
                }
            }
            Mite.LOGGER.info("MITE_MATERIAL START copy={} supplied=1copper_ingot,1diamond,1crafting_table; no gathering or display-stock claim", WORLD);
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
        private final Stage[] stages = {
                new Stage("copper_ingot_to_nine_nuggets_2x2", false,
                        single(Items.COPPER_INGOT, 1, 4), MiteMaterials.COPPER_NUGGET.get(), 9, UNPACKED),
                new Stage("nine_nuggets_in_one_2x2_slot_rejected", false,
                        single(MiteMaterials.COPPER_NUGGET.get(), 9, 4), Items.AIR, 0, UNPACKED),
                new Stage("nine_nuggets_to_ingot_3x3", true,
                        filled(MiteMaterials.COPPER_NUGGET.get()), Items.COPPER_INGOT, 1, RESTORED),
                new Stage("diamond_to_nine_shards", true,
                        single(Items.DIAMOND, 1, 9), MiteShards.DIAMOND_SHARD.get(), 9, FRAGMENTED),
                new Stage("nine_shards_to_diamond_3x3", true,
                        filled(MiteShards.DIAMOND_SHARD.get()), Items.DIAMOND, 1, RESTORED)
        };
        private CompletableFuture<Snapshot> work;
        private Phase phase = Phase.SETUP;
        private int stageIndex;
        private int elapsed;
        private int stableTicks;
        private int clickDelay;

        private Runner(IntegratedServer server, UUID playerId) {
            this.server = server;
            this.playerId = playerId;
        }

        private void tick(Minecraft mc) {
            if (!active.get()) return;
            // Desktop focus may change while the user works. Do not send menu/input packets until it returns.
            if (!mc.isWindowActive() || mc.getOverlay() != null) return;
            try {
                require(++elapsed <= 4000, "Timeout at " + phase);
                require(mc.getSingleplayerServer() == server && mc.player != null && mc.level != null && mc.gameMode != null
                        && playerId.equals(mc.player.getUUID()), "Test world/player disconnected");
                stopInput(mc);
                Stage stage = stages[stageIndex];
                switch (phase) {
                    case SETUP -> {
                        if (pollServer() != null) phase = Phase.WAIT_INITIAL;
                    }
                    case WAIT_INITIAL -> {
                        mc.player.getInventory().selected = 0;
                        if (!stable(focused(mc) && mc.player.onGround() && mc.player.position().distanceToSqr(STAND) < 0.1
                                && !mc.player.isCreative() && !mc.player.isSpectator() && mc.player.experienceLevel == 0
                                && mc.player.totalExperience == 0 && sameHealth(mc.player.getHealth())
                                && mc.player.getFoodData().getFoodLevel() == 6 && counts(mc.player).equals(INITIAL))) return;
                        require(legalStacks(mc.player), "Initial copper ingot stack limit was not four");
                        mc.setScreen(new InventoryScreen(mc.player));
                        planStage(mc);
                    }
                    case INPUT, CLEAR -> {
                        requireMenu(mc, stage.workbench());
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
                        requireMenu(mc, stage.workbench());
                        Snapshot state = pollServer();
                        if (state == null) return;
                        List<String> expected = expectedGrid(stage);
                        if (!stable(state.workbench() == stage.workbench() && state.grid().equals(expected)
                                && grid(mc.player).equals(expected) && state.cursorEmpty()
                                && mc.player.containerMenu.getCarried().isEmpty())) return;
                        assertUndisturbed(mc, state);
                        ItemStack output = mc.player.containerMenu.getSlot(0).getItem();
                        if (stage.outputCount() == 0) {
                            require(output.isEmpty() && state.outputCount() == 0, "A stacked ingredient bypassed the 3x3 requirement: " + state);
                            click(mc, 0, 0, ClickType.QUICK_MOVE);
                            clicks.clear();
                            for (int slot = 1; slot <= 4; slot++) {
                                if (mc.player.containerMenu.getSlot(slot).hasItem()) clicks.add(new Click(slot, 0, ClickType.QUICK_MOVE));
                            }
                            clickDelay = 2;
                            phase = Phase.CLEAR;
                        } else {
                            require(output.is(stage.output()) && output.getCount() == stage.outputCount()
                                            && state.output().equals(id(stage.output())) && state.outputCount() == stage.outputCount(),
                                    "Recipe output differed between client/server or from the literal expectation: " + state);
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
                        if (!stable(inventoryMatches(mc, state, UNPACKED))) return;
                        assertUndisturbed(mc, state);
                        require(!state.tablePlaced(), "Workbench was placed before normal interaction");
                        Mite.LOGGER.info("MITE_MATERIAL REJECT_PASS stage={} nineNuggetsReturned=true cursorAndGrid=empty server={}", stage.name(), state);
                        equip(mc, Items.CRAFTING_TABLE);
                    }
                    case VERIFY_CRAFT -> {
                        requireMenu(mc, stage.workbench());
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!stable(inventoryMatches(mc, state, stage.after()))) return;
                        assertUndisturbed(mc, state);
                        require(state.tablePlaced() == stage.workbench(), "Workbench state changed during crafting");
                        Mite.LOGGER.info("MITE_MATERIAL CRAFT_PASS stage={} singleShiftClick=true server={}", stage.name(), state);
                        if (stageIndex == stages.length - 1) {
                            equip(mc, Items.DIAMOND);
                        } else {
                            stageIndex++;
                            planStage(mc);
                        }
                    }
                    case WAIT_EQUIPPED -> {
                        requireMenu(mc, stage.workbench());
                        mc.player.getInventory().selected = 0;
                        Item expectedItem = stageIndex == 1 ? Items.CRAFTING_TABLE : Items.DIAMOND;
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!stable(inventoryMatches(mc, state, stage.after()) && state.selected() == 0
                                && state.heldItem().equals(id(expectedItem)) && state.heldCount() == 1
                                && mc.player.getMainHandItem().is(expectedItem) && mc.player.getMainHandItem().getCount() == 1)) return;
                        assertUndisturbed(mc, state);
                        mc.player.closeContainer();
                        work = null;
                        stableTicks = 0;
                        phase = stageIndex == 1 ? Phase.PLACE : Phase.USE_GEM;
                    }
                    case PLACE -> {
                        mc.player.getInventory().selected = 0;
                        pointAt(mc, new Vec3(TABLE.getX() + 0.5, TABLE.getY() - 0.001, TABLE.getZ() + 0.5));
                        if (!stable(focused(mc) && mc.player.onGround() && !mc.player.isShiftKeyDown()
                                && mc.player.getMainHandItem().is(Items.CRAFTING_TABLE) && mc.level.getBlockState(TABLE).isAir()
                                && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(TABLE.below())
                                && hit.getDirection() == Direction.UP)) return;
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
                        assertUndisturbed(mc, state);
                        Mite.LOGGER.info("MITE_MATERIAL PLACE_PASS normalUseItemOn=true suppliedTableConsumed=1 server={}", state);
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
                                && state.workbench() && state.tablePlaced() && inventoryMatches(mc, state, PLACED))) return;
                        assertUndisturbed(mc, state);
                        stageIndex = 2;
                        planStage(mc);
                    }
                    case USE_GEM -> {
                        mc.player.getInventory().selected = 0;
                        // Keep the entire natural pick ray inside the cleared platform volume.
                        mc.player.setYRot(0.0F);
                        mc.player.setXRot(-30.0F);
                        if (!stable(focused(mc) && mc.player.onGround() && mc.player.getMainHandItem().is(Items.DIAMOND)
                                && mc.player.getMainHandItem().getCount() == 1 && mc.player.totalExperience == 0
                                && mc.player.experienceLevel == 0 && sameHealth(mc.player.getHealth())
                                && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.MISS)) return;
                        // Exactly one normal ServerboundUseItemPacket; no direct server sacrifice call.
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        Mite.LOGGER.info("MITE_MATERIAL SACRIFICE_SENT item=diamond count=1 expectedXp=500 expectedLevel=8 healthBefore=2.0 normalUseItem=true");
                        work = null;
                        stableTicks = 0;
                        phase = Phase.VERIFY_USE;
                    }
                    case VERIFY_USE -> {
                        require(focused(mc), "Gem verification lost focus");
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (state.totalXp() == 0 && state.inventory().equals(RESTORED)) return;
                        require(state.inventory().equals(FINISHED) && state.totalXp() == 500 && state.level() == 8
                                        && sameHealth(state.health()) && state.tablePlaced() && state.emptyGrid()
                                        && state.cursorEmpty() && state.droppedItems() == 0 && state.legalStacks(),
                                "Authoritative sacrifice or conservation mismatch: " + state);
                        if (!stable(inventoryMatches(mc, state, FINISHED) && mc.player.totalExperience == 500
                                && mc.player.experienceLevel == 8 && sameHealth(mc.player.getHealth())
                                && mc.player.getMainHandItem().isEmpty() && mc.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE))) return;
                        Mite.LOGGER.info("MITE_MATERIAL SACRIFICE_PASS consumed=1 xpAdded=500 level=8 healthUnchanged=2.0 server={}", state);
                        pass(mc);
                    }
                    case DONE -> { }
                }
            } catch (RuntimeException failure) {
                fail(mc, failure.toString());
            }
        }

        private void planStage(Minecraft mc) {
            Stage stage = stages[stageIndex];
            requireMenu(mc, stage.workbench());
            require(grid(mc.player).stream().allMatch(String::isEmpty) && mc.player.containerMenu.getCarried().isEmpty(),
                    "Recipe planning requires an empty grid and cursor");
            clicks.clear();
            for (int cell = 0; cell < stage.grid().size(); cell++) {
                Ingredient ingredient = stage.grid().get(cell);
                if (ingredient.count() == 0) continue;
                int source = findSlot(mc, ingredient.item());
                clicks.add(new Click(source, 0, ClickType.PICKUP));
                for (int n = 0; n < ingredient.count(); n++) clicks.add(new Click(cell + 1, 1, ClickType.PICKUP));
                clicks.add(new Click(source, 0, ClickType.PICKUP));
            }
            work = null;
            clickDelay = 2;
            stableTicks = 0;
            phase = Phase.INPUT;
        }

        private void equip(Minecraft mc, Item item) {
            click(mc, findSlot(mc, item), 0, ClickType.SWAP);
            mc.player.getInventory().selected = 0;
            work = null;
            stableTicks = 0;
            phase = Phase.WAIT_EQUIPPED;
        }

        private Snapshot pollServer() {
            if (work == null) {
                work = onServer(MaterialClientSmoke::snapshot);
                return null;
            }
            if (!work.isDone()) return null;
            Snapshot result = work.join();
            work = null;
            return result;
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
            stopInput(mc);
            active.set(false);
            phase = Phase.DONE;
            String message = "MITE_MATERIAL PASS — 铜粒与钻石碎片拆合、2×2限制、钻石献祭500经验通过；生命仍为2";
            Mite.LOGGER.info("{} elapsedTicks={} final=1copper_ingot,0diamond,0shards,0nuggets,1placedTable; no drops/oversizedStacks", message, elapsed);
            mc.gui.getChat().addMessage(Component.literal(message));
            mc.gui.setOverlayMessage(Component.literal(message), false);
            mc.setScreen(new InventoryScreen(mc.player));
        }

        private void fail(Minecraft mc, String reason) {
            stopInput(mc);
            active.set(false);
            Mite.LOGGER.error("MITE_MATERIAL FAIL phase={} stage={} reason={}", phase, stages[stageIndex].name(), reason);
            phase = Phase.DONE;
            mc.gui.getChat().addMessage(Component.literal("MITE_MATERIAL FAIL: " + reason));
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
        for (int x = 16001; x <= 16009; x++) {
            for (int z = 16001; z <= 16009; z++) {
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
        player.getInventory().setItem(0, new ItemStack(Items.COPPER_INGOT));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND));
        player.getInventory().setItem(2, new ItemStack(Items.CRAFTING_TABLE));
        player.getInventory().selected = 0;
        PlayerExperience.setTotal(player, 0);
        player.getFoodData().setFoodLevel(6);
        player.getFoodData().setSaturation(0.0F);
        player.getFoodData().setExhaustion(0.0F);
        player.setHealth(START_HEALTH);
        player.teleportTo(world, STAND.x, STAND.y, STAND.z, 0.0F, 20.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static Snapshot snapshot(ServerPlayer player) {
        ItemStack output = player.containerMenu.getSlot(0).getItem();
        ItemStack held = player.getMainHandItem();
        int drops = 0;
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(ItemEntity.class, AREA)) drops += item.getItem().getCount();
        return new Snapshot(counts(player), grid(player), player.containerMenu.getCarried().isEmpty(),
                player.containerMenu instanceof CraftingMenu, output.isEmpty() ? "" : id(output.getItem()), output.getCount(),
                player.serverLevel().getBlockState(TABLE).is(Blocks.CRAFTING_TABLE), drops, legalStacks(player),
                player.getInventory().selected, held.isEmpty() ? "" : id(held.getItem()), held.getCount(),
                player.totalExperience, player.experienceLevel, player.getHealth());
    }

    private static Counts counts(Player player) {
        return new Counts(count(player, Items.COPPER_INGOT), count(player, MiteMaterials.COPPER_NUGGET.get()),
                count(player, Items.DIAMOND), count(player, MiteShards.DIAMOND_SHARD.get()), count(player, Items.CRAFTING_TABLE));
    }

    private static int count(Player player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static boolean legalStacks(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (!legalStack(player.getInventory().getItem(slot))) return false;
        }
        for (var slot : player.containerMenu.slots) if (!legalStack(slot.getItem())) return false;
        return legalStack(player.containerMenu.getCarried());
    }

    private static boolean legalStack(ItemStack stack) {
        return stack.isEmpty() || stack.getCount() <= stack.getMaxStackSize()
                && (!stack.is(Items.COPPER_INGOT) || stack.getMaxStackSize() == 4 && stack.getCount() <= 4);
    }

    private static List<String> grid(Player player) {
        require(player.containerMenu instanceof InventoryMenu || player.containerMenu instanceof CraftingMenu, "Unexpected menu");
        int size = player.containerMenu instanceof CraftingMenu ? 9 : 4;
        List<String> cells = new ArrayList<>();
        for (int slot = 1; slot <= size; slot++) {
            ItemStack stack = player.containerMenu.getSlot(slot).getItem();
            cells.add(stack.isEmpty() ? "" : id(stack.getItem()) + ":" + stack.getCount());
        }
        return List.copyOf(cells);
    }

    private static List<Ingredient> single(Item item, int count, int size) {
        List<Ingredient> cells = new ArrayList<>();
        cells.add(new Ingredient(item, count));
        for (int i = 1; i < size; i++) cells.add(new Ingredient(Items.AIR, 0));
        return List.copyOf(cells);
    }

    private static List<Ingredient> filled(Item item) {
        List<Ingredient> cells = new ArrayList<>();
        for (int i = 0; i < 9; i++) cells.add(new Ingredient(item, 1));
        return List.copyOf(cells);
    }

    private static List<String> expectedGrid(Stage stage) {
        return stage.grid().stream().map(ingredient -> ingredient.count() == 0 ? ""
                : id(ingredient.item()) + ":" + ingredient.count()).toList();
    }

    private static boolean inventoryMatches(Minecraft mc, Snapshot state, Counts expected) {
        return state.inventory().equals(expected) && counts(mc.player).equals(expected) && state.emptyGrid()
                && grid(mc.player).stream().allMatch(String::isEmpty) && state.cursorEmpty()
                && mc.player.containerMenu.getCarried().isEmpty() && state.droppedItems() == 0
                && state.legalStacks() && legalStacks(mc.player);
    }

    private static void assertUndisturbed(Minecraft mc, Snapshot state) {
        require(state.totalXp() == 0 && state.level() == 0 && sameHealth(state.health())
                        && mc.player.totalExperience == 0 && mc.player.experienceLevel == 0 && sameHealth(mc.player.getHealth())
                        && state.droppedItems() == 0 && state.legalStacks() && legalStacks(mc.player),
                "Crafting changed XP/health, dropped materials or made an oversized stack: " + state);
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

    private static void stopInput(Minecraft mc) {
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        if (mc.player != null && mc.level != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }

    private static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    private static boolean sameHealth(float health) { return Math.abs(health - START_HEALTH) < 0.0001F; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
