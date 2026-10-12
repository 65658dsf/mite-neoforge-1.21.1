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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real crafting and client attack packets in one isolated test copy. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class WoodenEquipmentClientSmoke {
    private static final String WORLD_FOLDER = "MITE Wooden Equipment Test-012";
    private static final BlockPos TABLE = new BlockPos(13005, 200, 13007);
    private static final Vec3 TABLE_STAND = new Vec3(13005.5, 200.0, 13004.5);
    private static final Vec3[] TARGETS = {new Vec3(13003.5, 200.0, 13010.5), new Vec3(13008.5, 200.0, 13010.5)};
    private static final AABB AREA = new AABB(13000, 199, 13000, 13013, 205, 13013);
    private static Runner runner;

    private WoodenEquipmentClientSmoke() {}

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("mite.woodenEquipmentSmoke")) {
            if (runner != null && runner.active.get()) runner.fail(mc, "Opt-in property was disabled");
            return;
        }
        if (runner == null) {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null
                    || mc.getSingleplayerServer() == null) return;
            runner = new Runner(mc.getSingleplayerServer(), mc.player.getUUID());
            for (String property : new String[]{"mite.miningSmoke", "mite.miningBaseSmoke", "mite.miningHungerSmoke",
                    "mite.flintStartSmoke", "mite.logHarvestSmoke"}) {
                if (Boolean.getBoolean(property)) {
                    runner.fail(mc, "Do not enable another smoke mode alongside woodenEquipmentSmoke: " + property);
                    return;
                }
            }
            Mite.LOGGER.info("MITE_WOODEN_EQUIPMENT START copy={} supplied=3planks,2sticks; targets=cow,skeleton", WORLD_FOLDER);
            runner.work = runner.onServer(player -> {
                runner.prepareWorld(player);
                return runner.snapshot(player);
            });
        }
        runner.tick(mc);
    }

    private enum Phase {
        SETUP, WAIT_INITIAL, WAIT_TABLE, INPUT, WAIT_LAYOUT, CLEAR, WAIT_CLEAR, VERIFY_CRAFT,
        VERIFY_EQUIP, WAIT_CLOSED, PREPARE_TARGET, WAIT_TARGET, VERIFY_HIT, DONE
    }

    private record Click(int slot, int button, ClickType type) {}

    private record Snapshot(int planks, int sticks, int clubs, int heldDamage, int selectedSlot, boolean cursorEmpty,
            boolean workbenchOpen, List<String> grid, String output, int outputCount,
            int cowId, int skeletonId, float cowHealth, float skeletonHealth) {
        private boolean gridEmpty() { return grid.stream().allMatch(String::isEmpty); }
        private int targetId(int target) { return target == 0 ? cowId : skeletonId; }
        private float targetHealth(int target) { return target == 0 ? cowHealth : skeletonHealth; }
        private boolean craftedClub() {
            return planks == 1 && sticks == 1 && clubs == 1 && cursorEmpty && gridEmpty();
        }
    }

    private static final class Runner {
        private final IntegratedServer server;
        private final UUID playerId;
        private final UUID cowId = UUID.randomUUID();
        private final UUID skeletonId = UUID.randomUUID();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final ArrayDeque<Click> clicks = new ArrayDeque<>();
        private CompletableFuture<Snapshot> work;
        private Snapshot latest;
        private Phase phase = Phase.SETUP;
        private int elapsedTicks;
        private int stableTicks;
        private int clickDelay;
        private int recipeIndex;
        private int targetIndex;
        private int attackWaitTicks;

        private Runner(IntegratedServer server, UUID playerId) {
            this.server = server;
            this.playerId = playerId;
        }

        private void tick(Minecraft mc) {
            if (!active.get()) return;
            try {
                require(++elapsedTicks <= 4000, "Timed out after 4000 client ticks at " + phase);
                require(mc.getSingleplayerServer() == server && mc.player != null && mc.level != null && mc.gameMode != null
                        && playerId.equals(mc.player.getUUID()), "The test player or world disconnected");
                releaseAttack(mc);
                switch (phase) {
                    case SETUP -> {
                        if (pollServer() != null) phase = Phase.WAIT_INITIAL;
                    }
                    case WAIT_INITIAL -> {
                        if (!stable(focused(mc) && mc.player.onGround() && mc.player.position().distanceToSqr(TABLE_STAND) < 0.1
                                && mc.player.experienceLevel == 0 && mc.player.getFoodData().getFoodLevel() == 6
                                && !mc.player.isCreative() && !mc.player.isSpectator()
                                && count(mc.player, Items.OAK_PLANKS) == 3 && count(mc.player, Items.STICK) == 2)) return;
                        pointAt(mc, Vec3.atCenterOf(TABLE));
                        // Use the normal client interaction API; the server opens the actual CraftingMenu.
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                                new BlockHitResult(Vec3.atCenterOf(TABLE), Direction.NORTH, TABLE, false));
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        stableTicks = 0;
                        phase = Phase.WAIT_TABLE;
                    }
                    case WAIT_TABLE -> {
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!stable(mc.screen instanceof CraftingScreen && mc.player.containerMenu instanceof CraftingMenu
                                && state.workbenchOpen() && state.gridEmpty() && state.cursorEmpty())) return;
                        planRecipe(mc);
                    }
                    case INPUT, CLEAR -> {
                        requireWorkbench(mc);
                        if (clickDelay-- > 0) return;
                        if (!clicks.isEmpty()) {
                            Click click = clicks.removeFirst();
                            click(mc, click.slot(), click.button(), click.type());
                            clickDelay = 2;
                        } else {
                            stableTicks = 0;
                            work = null;
                            phase = phase == Phase.INPUT ? Phase.WAIT_LAYOUT : Phase.WAIT_CLEAR;
                        }
                    }
                    case WAIT_LAYOUT -> {
                        requireWorkbench(mc);
                        Snapshot state = pollServer();
                        if (state == null) return;
                        List<String> expected = layout(recipeIndex);
                        if (!stable(state.workbenchOpen() && state.grid().equals(expected) && grid(mc.player).equals(expected)
                                && state.cursorEmpty() && mc.player.containerMenu.getCarried().isEmpty())) return;
                        ItemStack result = mc.player.containerMenu.getSlot(0).getItem();
                        if (recipeIndex < 2) {
                            require(state.outputCount() == 0 && result.isEmpty(), "Removed wooden tool still has a crafting output: " + state);
                            Mite.LOGGER.info("MITE_WOODEN_EQUIPMENT RECIPE_BLOCKED item={} serverGrid={} clientOutput=empty serverOutput=empty",
                                    recipeIndex == 0 ? "wooden_pickaxe" : "wooden_axe", state.grid());
                            clicks.clear();
                            for (int slot = 1; slot <= 9; slot++) {
                                if (mc.player.containerMenu.getSlot(slot).hasItem()) clicks.add(new Click(slot, 0, ClickType.QUICK_MOVE));
                            }
                            clickDelay = 2;
                            phase = Phase.CLEAR;
                        } else {
                            require(state.output().equals("minecraft:wooden_sword") && state.outputCount() == 1
                                    && result.is(Items.WOODEN_SWORD) && result.getCount() == 1, "Club recipe result mismatch: " + state);
                            click(mc, 0, 0, ClickType.QUICK_MOVE);
                            work = null;
                            phase = Phase.VERIFY_CRAFT;
                        }
                    }
                    case WAIT_CLEAR -> {
                        requireWorkbench(mc);
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!stable(state.gridEmpty() && state.cursorEmpty() && state.planks() == 3 && state.sticks() == 2
                                && grid(mc.player).stream().allMatch(String::isEmpty) && mc.player.containerMenu.getCarried().isEmpty()
                                && count(mc.player, Items.OAK_PLANKS) == 3 && count(mc.player, Items.STICK) == 2)) return;
                        recipeIndex++;
                        planRecipe(mc);
                    }
                    case VERIFY_CRAFT -> {
                        requireWorkbench(mc);
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!state.craftedClub() || !clientCraftedClub(mc)) return;
                        Mite.LOGGER.info("MITE_WOODEN_EQUIPMENT CRAFT_PASS recipe=2planks+stick result=minecraft:wooden_sword server={}", state);
                        click(mc, findSlot(mc, Items.WOODEN_SWORD), 0, ClickType.SWAP);
                        mc.player.getInventory().selected = 0;
                        stableTicks = 0;
                        work = null;
                        phase = Phase.VERIFY_EQUIP;
                    }
                    case VERIFY_EQUIP -> {
                        requireWorkbench(mc);
                        Snapshot state = pollServer();
                        if (state == null) return;
                        if (!stable(state.craftedClub() && state.heldDamage() == 0 && state.selectedSlot() == 0
                                && mc.player.getInventory().selected == 0 && heldClub(mc.player, 0))) return;
                        mc.player.closeContainer();
                        work = null;
                        phase = Phase.WAIT_CLOSED;
                    }
                    case WAIT_CLOSED -> {
                        Snapshot state = pollServer();
                        if (state != null && !state.workbenchOpen() && focused(mc)) prepareTarget();
                    }
                    case PREPARE_TARGET -> {
                        if (pollServer() == null) return;
                        attackWaitTicks = 0;
                        stableTicks = 0;
                        phase = Phase.WAIT_TARGET;
                    }
                    case WAIT_TARGET -> {
                        attackWaitTicks++;
                        Entity entity = mc.level.getEntity(latest.targetId(targetIndex));
                        if (!(entity instanceof LivingEntity target)) return;
                        pointAt(mc, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
                        boolean ready = focused(mc) && mc.player.onGround() && !mc.player.isSprinting()
                                && mc.player.fallDistance == 0.0F && mc.player.experienceLevel == 0
                                && mc.player.getFoodData().getFoodLevel() == 6 && mc.player.getInventory().selected == 0
                                && heldClub(mc.player, targetIndex * 25)
                                && mc.player.position().distanceToSqr(attackStand(targetIndex)) < 0.1
                                && mc.player.getAttackStrengthScale(0.5F) >= 1.0F && attackWaitTicks >= 30
                                && mc.hitResult instanceof EntityHitResult hit && hit.getEntity().getId() == target.getId();
                        if (!stable(ready)) return;
                        require(close(target.getHealth(), targetIndex == 0 ? 10.0F : 20.0F), "Target was damaged before the intended hit");
                        // This sends a normal ServerboundInteractPacket and performs normal local attack prediction.
                        mc.gameMode.attack(mc.player, target);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        Mite.LOGGER.info("MITE_WOODEN_EQUIPMENT ATTACK_SENT target={} fullCooldown=true grounded=true expectedDamage={}",
                                targetIndex == 0 ? "cow" : "skeleton", targetIndex == 0 ? 3 : 6);
                        work = null;
                        phase = Phase.VERIFY_HIT;
                    }
                    case VERIFY_HIT -> {
                        require(focused(mc), "Attack verification lost focus or gained a screen");
                        Snapshot state = pollServer();
                        if (state == null) return;
                        float before = targetIndex == 0 ? 10.0F : 20.0F;
                        float expected = targetIndex == 0 ? 7.0F : 14.0F;
                        if (close(state.targetHealth(targetIndex), before) && state.heldDamage() == targetIndex * 25) return;
                        require(close(state.targetHealth(targetIndex), expected) && state.heldDamage() == (targetIndex + 1) * 25,
                                "Authoritative attack damage or durability mismatch: " + state);
                        Entity entity = mc.level.getEntity(state.targetId(targetIndex));
                        if (!(entity instanceof LivingEntity target) || !close(target.getHealth(), expected)
                                || !heldClub(mc.player, (targetIndex + 1) * 25)) return;
                        Mite.LOGGER.info("MITE_WOODEN_EQUIPMENT HIT_PASS target={} damage={} wear=25 totalDamage={} clientAndServerHealth={}",
                                targetIndex == 0 ? "cow" : "skeleton", before - expected, state.heldDamage(), expected);
                        if (++targetIndex == 2) pass(mc, state);
                        else prepareTarget();
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

        private Snapshot pollServer() {
            if (work == null) {
                work = onServer(this::snapshot);
                return null;
            }
            if (!work.isDone()) return null;
            latest = work.join();
            work = null;
            return latest;
        }

        private void planRecipe(Minecraft mc) {
            requireWorkbench(mc);
            require(grid(mc.player).stream().allMatch(String::isEmpty) && mc.player.containerMenu.getCarried().isEmpty(),
                    "Recipe planning requires an empty grid and cursor");
            clicks.clear();
            List<String> expected = layout(recipeIndex);
            for (int slot = 1; slot <= 9; slot++) {
                String ingredient = expected.get(slot - 1);
                if (ingredient.isEmpty()) continue;
                Item item = ingredient.equals("minecraft:oak_planks:1") ? Items.OAK_PLANKS : Items.STICK;
                int source = findSlot(mc, item);
                clicks.add(new Click(source, 0, ClickType.PICKUP));
                clicks.add(new Click(slot, 1, ClickType.PICKUP));
                clicks.add(new Click(source, 0, ClickType.PICKUP));
            }
            clickDelay = 2;
            stableTicks = 0;
            work = null;
            phase = Phase.INPUT;
        }

        private void prepareTarget() {
            int index = targetIndex;
            work = onServer(player -> {
                require(player.containerMenu == player.inventoryMenu, "Workbench was not closed before combat");
                require(player.experienceLevel == 0 && player.getInventory().selected == 0
                        && player.getMainHandItem().is(Items.WOODEN_SWORD), "Combat setup lost the crafted club in hotbar slot zero");
                Vec3 stand = attackStand(index);
                player.teleportTo(server.overworld(), stand.x, stand.y, stand.z, 0.0F, 0.0F);
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0.0F;
                player.setSprinting(false);
                player.getFoodData().setFoodLevel(6);
                player.getFoodData().setExhaustion(0.0F);
                return snapshot(player);
            });
            phase = Phase.PREPARE_TARGET;
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

        private void prepareWorld(ServerPlayer player) {
            ServerLevel world = server.overworld();
            world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            world.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            world.setDayTime(18000L);
            world.setWeatherParameters(6000, 0, false, false);
            server.setDifficulty(Difficulty.NORMAL, true);
            for (Entity entity : world.getEntities((Entity) null, AREA, entity -> !(entity instanceof Player))) entity.discard();
            for (int x = 13001; x <= 13011; x++) {
                for (int z = 13001; z <= 13011; z++) {
                    world.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState());
                    for (int y = 200; y <= 204; y++) world.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
            world.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
            player.setGameMode(GameType.SURVIVAL);
            player.stopUsingItem();
            player.removeAllEffects();
            player.setSprinting(false);
            player.getInventory().clearContent();
            player.inventoryMenu.clearCraftingContent();
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
            player.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
            player.getInventory().selected = 0;
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
            player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4.0);
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.getFoodData().setSaturation(0.0F);
            player.getFoodData().setExhaustion(0.0F);
            player.setHealth(6.0F);
            player.teleportTo(world, TABLE_STAND.x, TABLE_STAND.y, TABLE_STAND.z, 0.0F, 20.0F);
            player.fallDistance = 0.0F;
            player.setDeltaMovement(Vec3.ZERO);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            createTarget(world, EntityType.COW, cowId, TARGETS[0], 10.0F);
            createTarget(world, EntityType.SKELETON, skeletonId, TARGETS[1], 20.0F);
        }

        private Snapshot snapshot(ServerPlayer player) {
            boolean crafting = player.containerMenu instanceof CraftingMenu;
            ItemStack output = crafting ? player.containerMenu.getSlot(0).getItem() : ItemStack.EMPTY;
            Entity cow = server.overworld().getEntity(cowId);
            Entity skeleton = server.overworld().getEntity(skeletonId);
            return new Snapshot(count(player, Items.OAK_PLANKS), count(player, Items.STICK), count(player, Items.WOODEN_SWORD),
                    player.getMainHandItem().is(Items.WOODEN_SWORD) ? player.getMainHandItem().getDamageValue() : -1,
                    player.getInventory().selected,
                    player.containerMenu.getCarried().isEmpty(), crafting, crafting ? grid(player) : List.of(),
                    output.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), output.getCount(),
                    cow == null ? -1 : cow.getId(), skeleton == null ? -1 : skeleton.getId(),
                    cow instanceof LivingEntity living ? living.getHealth() : -1.0F,
                    skeleton instanceof LivingEntity living ? living.getHealth() : -1.0F);
        }

        private void pass(Minecraft mc, Snapshot state) {
            require(state.craftedClub() && state.heldDamage() == 50 && close(state.cowHealth(), 7.0F) && close(state.skeletonHealth(), 14.0F),
                    "Final quantities or target health mismatch");
            releaseAttack(mc);
            active.set(false);
            phase = Phase.DONE;
            mc.setScreen(new InventoryScreen(mc.player));
            String message = "MITE_WOODEN_EQUIPMENT PASS — 木镐/木斧配方已禁用；真实合成木棒，牛伤害3、骷髅伤害6，每击磨损25";
            Mite.LOGGER.info("{} elapsedTicks={} server={}", message, elapsedTicks, state);
            mc.gui.getChat().addMessage(Component.literal(message));
            mc.gui.setOverlayMessage(Component.literal("MITE_WOODEN_EQUIPMENT PASS — 木棒已消耗50耐久；原料由测试提供"), false);
        }

        private void fail(Minecraft mc, String reason) {
            releaseAttack(mc);
            active.set(false);
            Phase failed = phase;
            phase = Phase.DONE;
            Mite.LOGGER.error("MITE_WOODEN_EQUIPMENT FAIL phase={} reason={}", failed, reason);
            mc.gui.getChat().addMessage(Component.literal("MITE_WOODEN_EQUIPMENT FAIL: " + reason));
        }
    }

    private static void createTarget(ServerLevel world, EntityType<? extends Mob> type, UUID id, Vec3 position, float health) {
        Mob target = type.create(world);
        require(target != null, "Cannot create combat target");
        target.setUUID(id);
        target.moveTo(position);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPersistenceRequired();
        target.clearFire();
        target.removeAllEffects();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) target.setItemSlot(slot, ItemStack.EMPTY);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.0);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        require(close(target.getMaxHealth(), health), "Unexpected vanilla target maximum health");
        target.setHealth(health);
        require(world.addFreshEntity(target), "Cannot add combat target to the test level");
    }

    private static List<String> layout(int recipe) {
        String p = "minecraft:oak_planks:1", s = "minecraft:stick:1", e = "";
        return switch (recipe) {
            case 0 -> List.of(p, p, p, e, s, e, e, s, e);
            case 1 -> List.of(p, p, e, p, s, e, e, s, e);
            case 2 -> List.of(e, p, e, e, p, e, e, s, e);
            default -> throw new IllegalStateException("Unknown recipe " + recipe);
        };
    }

    private static List<String> grid(Player player) {
        require(player.containerMenu instanceof CraftingMenu, "Expected the real workbench menu");
        List<String> cells = new ArrayList<>();
        for (int slot = 1; slot <= 9; slot++) {
            ItemStack stack = player.containerMenu.getSlot(slot).getItem();
            cells.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getCount());
        }
        return List.copyOf(cells);
    }

    private static int findSlot(Minecraft mc, Item item) {
        for (int slot = 10; slot <= 45; slot++) {
            if (mc.player.containerMenu.getSlot(slot).getItem().is(item)) return slot;
        }
        throw new IllegalStateException("Missing inventory ingredient or club: " + item);
    }

    private static void click(Minecraft mc, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, button, type, mc.player);
    }

    private static int count(Player player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static boolean clientCraftedClub(Minecraft mc) {
        return count(mc.player, Items.OAK_PLANKS) == 1 && count(mc.player, Items.STICK) == 1 && count(mc.player, Items.WOODEN_SWORD) == 1
                && grid(mc.player).stream().allMatch(String::isEmpty) && mc.player.containerMenu.getCarried().isEmpty();
    }

    private static boolean heldClub(Player player, int damage) {
        return player.getMainHandItem().is(Items.WOODEN_SWORD) && player.getMainHandItem().getCount() == 1
                && player.getMainHandItem().getDamageValue() == damage;
    }

    private static boolean focused(Minecraft mc) {
        return mc.screen == null && mc.getOverlay() == null && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed();
    }

    private static void requireWorkbench(Minecraft mc) {
        require(mc.screen instanceof CraftingScreen && mc.player.containerMenu instanceof CraftingMenu
                && mc.getOverlay() == null && mc.isWindowActive(), "Workbench screen or focus changed");
    }

    private static Vec3 attackStand(int index) {
        return TARGETS[index].add(0.0, 0.0, -2.5);
    }

    private static void pointAt(Minecraft mc, Vec3 point) {
        Vec3 difference = point.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(difference.z, difference.x)) - 90.0));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(difference.y, Math.hypot(difference.x, difference.z))));
    }

    private static void releaseAttack(Minecraft mc) {
        mc.options.keyAttack.setDown(false);
        if (mc.player != null && mc.level != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }

    private static boolean close(float actual, float expected) {
        return Float.isFinite(actual) && Math.abs(actual - expected) < 0.0001F;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
