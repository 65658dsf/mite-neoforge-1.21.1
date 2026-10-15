package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.network.CraftingProgressPayload;
import io.github.ningmeng123.mite.network.CraftingQualityPayload;
import io.github.ningmeng123.mite.rules.EquipmentQualityRules;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Server-owned manufacturing sessions; one tick and one real recipe consumption per batch. @author NingMeng123 */
public final class TimedCraftingController {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final ThreadLocal<Inventory> COMPLETING_INVENTORY = new ThreadLocal<>();
    private static final ThreadLocal<Consumption> COMPLETING_CONSUMPTION = new ThreadLocal<>();
    private static final Map<UUID, CraftingQualityPayload> QUALITY_UPDATES = new HashMap<>();
    private record Consumption(CraftingContainer grid, int amount) {}
    private record Prepared(AbstractContainerMenu menu, CraftingContainer grid,
            RecipeHolder<CraftingRecipe> recipe, TimedCraftingRecipes.Rule rule, ItemStack result,
            CraftingQuality.Result quality) {}
    private static final class Session {
        final Prepared prepared;
        final List<ItemStack> inputs;
        final Level level;
        final boolean continuous;
        int progress;
        Session(Prepared prepared, Level level, boolean continuous) {
            this.prepared = prepared;
            this.inputs = snapshot(prepared.grid());
            this.level = level;
            this.continuous = continuous;
        }
    }
    private TimedCraftingController() {}

    private static Prepared prepare(Player player, AbstractContainerMenu menu) {
        if (!(menu instanceof CraftingMenu || menu instanceof InventoryMenu)
                || !(menu.getSlot(1).container instanceof CraftingContainer grid)) return null;
        if (grid.isEmpty()) return null;
        var recipe = player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), player.level());
        if (recipe.isEmpty()) return null;
        ItemStack raw = recipe.get().value().assemble(grid.asCraftInput(), player.level().registryAccess());
        CraftingQuality.Result quality = CraftingQuality.resolve(player, menu, grid, recipe.get(), raw);
        if (quality == null) return null;
        if (player instanceof ServerPlayer && (quality.quality() != null || raw.getItem() instanceof io.github.ningmeng123.mite.item.RunestoneItem)) {
            ItemStack display = WorkbenchRules.allows(menu, quality.stack())
                    && CraftingAccessRules.canCraft(grid, quality.stack()) ? quality.stack() : ItemStack.EMPTY;
            if (!ItemStack.matches(menu.getSlot(0).getItem(), display)) {
                menu.getSlot(0).set(display.copy());
                menu.broadcastChanges();
            }
        }
        return new Prepared(menu, grid, recipe.get(), quality.rule(), quality.stack(), quality);
    }

    public static boolean isTimed(Player player, AbstractContainerMenu menu) { return prepare(player, menu) != null; }

    public static boolean start(ServerPlayer player, AbstractContainerMenu menu) {
        return start(player, menu, false);
    }

    public static boolean start(ServerPlayer player, AbstractContainerMenu menu, boolean continuous) {
        Prepared prepared = prepare(player, menu);
        if (prepared == null || player.containerMenu != menu || !allowed(player, prepared)) return false;
        Session old = SESSIONS.get(player.getUUID());
        // Repeated clicks neither queue batches nor change an already-running session's mode.
        if (old != null && old.prepared.menu() == menu && old.prepared.recipe().id().equals(prepared.recipe().id())
                && matches(old.inputs, prepared.grid())) return true;
        SESSIONS.put(player.getUUID(), new Session(prepared, player.level(), continuous));
        show(player, 0, period(player, prepared), false);
        publishQuality(player, prepared);
        return true;
    }

    public static boolean isActive(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }

    public static void selectQuality(ServerPlayer player, AbstractContainerMenu menu, int direction) {
        if (player.containerMenu != menu || direction != 1 && direction != -1 || !menu.stillValid(player)) return;
        Prepared prepared = prepare(player, menu);
        if (prepared != null && RunestoneCrafting.select(player, menu, direction)) {
            cancel(player);
            prepare(player, menu);
            return;
        }
        if (prepared == null || prepared.quality().quality() == null || prepared.quality().fixed()) return;
        cancel(player);
        CraftingQuality.select(player, menu, prepared.quality(), direction);
        publishQuality(player, prepare(player, menu));
    }

    public static void forget(ServerPlayer player) {
        cancel(player);
        CraftingQuality.clear(player);
        RunestoneCrafting.clear(player);
        CraftingQualityPayload old = QUALITY_UPDATES.remove(player.getUUID());
        if (old != null) CraftingQualityPayload.send(player, old.containerId(), 0, 0, 0, 0, 0, false, false, false);
    }

    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) CraftingProgressPayload.send(player, session.prepared.menu().containerId, 0, 0, false, false);
    }
    public static void inputsChanged(AbstractContainerMenu menu) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return;
        CraftingQuality.inputsChanged(menu);
        RunestoneCrafting.inputsChanged(menu);
        SESSIONS.entrySet().removeIf(entry -> {
            Session session = entry.getValue();
            if (session.prepared.menu() != menu || matches(session.inputs, session.prepared.grid())) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) CraftingProgressPayload.send(player, menu.containerId, 0, 0, false, false);
            return true;
        });
    }
    public static int progress(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? 0 : session.progress;
    }
    public static int period(ServerPlayer player, AbstractContainerMenu menu) {
        Prepared prepared = prepare(player, menu);
        return prepared == null ? 0 : period(player, prepared);
    }
    private static int period(ServerPlayer player, Prepared prepared) {
        float capacity = prepared.menu() instanceof WorkbenchAccess access ? access.mite$workbenchCapacity() : 0F;
        return TimedCraftingRules.period(EquipmentQualityRules.adjustedDifficulty(prepared.rule().difficulty(),
                prepared.quality().quality()), player.experienceLevel,
                TimedCraftingRules.benchModifier(capacity, prepared.rule().requiredBench() != null), false);
    }

    private static boolean allowed(ServerPlayer player, Prepared prepared) {
        float capacity = prepared.menu() instanceof WorkbenchAccess access ? access.mite$workbenchCapacity() : 0F;
        if (!player.isAlive() || player.isSpectator() || !prepared.menu().stillValid(player)
                || upperBodyInWeb(player)
                || prepared.result().isEmpty() || !ItemStack.matches(prepared.result(), prepared.menu().getSlot(0).getItem())
                || capacity < prepared.rule().requiredCapacity()
                || !WorkbenchRules.allows(prepared.menu(), prepared.result())
                || !CraftingAccessRules.canCraft(prepared.grid(), prepared.result())
                || !CraftingQuality.canManufacture(player, prepared.quality())
                || prepared.quality().cost() > 0 && player.totalExperience < prepared.quality().cost()
                || prepared.quality().repair() && !CraftingQuality.canRepair(player, prepared.result())) return false;
        for (int i = 0; i < prepared.grid().getContainerSize(); i++) {
            if (prepared.grid().getItem(i).isDamaged() && !prepared.quality().repair()) return false;
        }
        return true;
    }

    private static boolean upperBodyInWeb(ServerPlayer player) {
        AABB body = player.getBoundingBox();
        double minimumY = player.getY() + 1.0;
        if (body.maxY <= minimumY) return false;
        AABB upper = new AABB(body.minX, minimumY, body.minZ, body.maxX, body.maxY, body.maxZ);
        return BlockPos.betweenClosedStream(upper).anyMatch(pos -> player.level().getBlockState(pos).is(Blocks.COBWEB));
    }

    /** The production player-tick entry; GameTests invoke this same method N times, without bypasses. */
    public static void tick(ServerPlayer player) {
        Prepared prepared = prepare(player, player.containerMenu);
        publishQuality(player, prepared);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (player.containerMenu != session.prepared.menu() || player.level() != session.level || prepared == null
                || !session.prepared.recipe().id().equals(prepared.recipe().id())
                || !matches(session.inputs, prepared.grid()) || !allowed(player, prepared)) {
            cancel(player);
            return;
        }
        int period = period(player, prepared);
        if (!FoodConsumption.hasEnergy(player)) {
            if (player.tickCount % 20 == 0) show(player, session.progress, period, true);
            return;
        }
        if (++session.progress < period) {
            if (session.progress % 5 == 0) show(player, session.progress, period, false);
            return;
        }
        int refund = CoinCrafting.refund(prepared.grid(), prepared.quality().consumption());
        CraftingQuality.Payment payment = CraftingQuality.approveExperienceTransaction(player, prepared.quality().cost(), refund);
        // Event listeners may change XP, inputs, menus or quality. Recheck after approval before consuming anything.
        Prepared checked = prepare(player, player.containerMenu);
        if (payment == null || checked == null || SESSIONS.get(player.getUUID()) != session
                || player.level() != session.level
                || checked.menu() != prepared.menu() || !checked.recipe().id().equals(prepared.recipe().id())
                || !matches(session.inputs, checked.grid()) || !ItemStack.matches(checked.result(), prepared.result())
                || checked.quality().cost() != prepared.quality().cost()
                || CoinCrafting.refund(checked.grid(), checked.quality().consumption()) != refund || !allowed(player, checked)) {
            cancel(player);
            publishQuality(player, checked);
            return;
        }
        if (!FoodConsumption.hasEnergy(player)) {
            session.progress = Math.min(session.progress, Math.max(period(player, checked) - 1, 0));
            show(player, session.progress, period(player, checked), true);
            return;
        }
        if (!CraftingQuality.pay(player, payment, refund > 0 || prepared.quality().quality() == null && prepared.quality().cost() > 0)) {
            cancel(player);
            return;
        }
        cancel(player);
        var slot = prepared.menu().getSlot(0);
        ItemStack crafted = slot.remove(prepared.result().getCount());
        // Scope the creative-overflow correction to this transaction's own inventory.
        Inventory previous = COMPLETING_INVENTORY.get();
        Consumption previousConsumption = COMPLETING_CONSUMPTION.get();
        COMPLETING_INVENTORY.set(player.getInventory());
        COMPLETING_CONSUMPTION.set(new Consumption(prepared.grid(), prepared.quality().consumption()));
        try {
            // onTake uses the actual matched recipe and preserves its container-item remainders.
            slot.onTake(player, crafted);
        } finally {
            if (previous == null) COMPLETING_INVENTORY.remove();
            else COMPLETING_INVENTORY.set(previous);
            if (previousConsumption == null) COMPLETING_CONSUMPTION.remove();
            else COMPLETING_CONSUMPTION.set(previousConsumption);
        }
        io.github.ningmeng123.mite.worldgen.R196StructureGeneration.crafted(player, crafted);
        giveOrDrop(player, crafted);
        prepared.menu().broadcastChanges();
        Prepared next = prepare(player, prepared.menu());
        if (session.continuous && !EquipmentQualityRules.stopAfterCompletion(prepared.quality().quality() == null ? 0 : prepared.quality().cost())
                && next != null && next.recipe().id().equals(prepared.recipe().id())) {
            start(player, prepared.menu(), true);
        } else show(player, period, period, false);
        publishQuality(player, next);
    }

    public static int consumptionFor(CraftingContainer container, int vanillaCount) {
        Consumption consumption = COMPLETING_CONSUMPTION.get();
        return consumption != null && consumption.grid() == container ? consumption.amount() : vanillaCount;
    }
    public static boolean isCompletingGrid(CraftingContainer grid) {
        Consumption consumption = COMPLETING_CONSUMPTION.get();
        return consumption != null && consumption.grid() == grid;
    }

    private static void publishQuality(ServerPlayer player, Prepared prepared) {
        boolean visible = prepared != null && prepared.quality().quality() != null;
        CraftingQualityPayload update = visible
                ? new CraftingQualityPayload(prepared.menu().containerId, prepared.quality().quality().ordinal(),
                        prepared.quality().range().minimum().ordinal(), prepared.quality().range().maximum().ordinal(),
                        prepared.quality().cost(), period(player, prepared), true, isActive(player), allowed(player, prepared))
                : new CraftingQualityPayload(player.containerMenu.containerId, 0, 0, 0, 0, 0, false, false, false);
        if (visible || QUALITY_UPDATES.containsKey(player.getUUID())) {
            CraftingQualityPayload previous = QUALITY_UPDATES.put(player.getUUID(), update);
            if (!update.equals(previous) || player.tickCount % 20 == 0) CraftingQualityPayload.send(player, update);
        }
    }

    public static void damaged(ServerPlayer player, float damage) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) session.progress = Math.max(0, session.progress - TimedCraftingRules.damageSetback(damage));
    }

    public static List<ItemStack> snapshot(Container container) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) items.add(container.getItem(i).copy());
        return items;
    }
    public static boolean matches(List<ItemStack> items, Container container) {
        if (items.size() != container.getContainerSize()) return false;
        for (int i = 0; i < items.size(); i++) if (!ItemStack.matches(items.get(i), container.getItem(i))) return false;
        return true;
    }
    public static boolean isCompletingIn(Inventory inventory) {
        return COMPLETING_INVENTORY.get() == inventory;
    }

    /** Mutates only the amount actually stored; false leaves a nonempty remainder for the caller to drop. */
    public static boolean insertWithoutDiscard(Inventory inventory, ItemStack stack) {
        // Inventory.add intentionally discards overflow in creative; exact manufacturing output must not.
        for (int i = 0; i < inventory.items.size() && !stack.isEmpty(); i++) {
            ItemStack existing = inventory.items.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int transfer = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (transfer > 0) { existing.grow(transfer); stack.shrink(transfer); }
            }
        }
        for (int i = 0; i < inventory.items.size() && !stack.isEmpty(); i++) {
            if (inventory.items.get(i).isEmpty()) inventory.setItem(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
        }
        inventory.setChanged();
        return stack.isEmpty();
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        insertWithoutDiscard(player.getInventory(), stack);
        while (!stack.isEmpty()) player.drop(stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())), false);
    }
    private static void show(ServerPlayer player, int progress, int period, boolean hungry) {
        CraftingProgressPayload.send(player, player.containerMenu.containerId, progress, period, hungry, true);
        player.displayClientMessage(Component.literal(hungry ? "制造暂停：需要食物" : "制造 "
                + Math.min(100, progress * 100 / Math.max(1, period)) + "% · 右键结果取消"), true);
    }
}
