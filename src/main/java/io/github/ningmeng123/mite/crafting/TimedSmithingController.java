package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.network.CraftingProgressPayload;
import io.github.ningmeng123.mite.rules.EquipmentQualityRules;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/** Server-owned upgrades commit one native smithing transaction after a complete period. @author NingMeng123 */
public final class TimedSmithingController {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final ThreadLocal<Completion> COMPLETING = new ThreadLocal<>();
    private record Reserve(int slot, int count, ItemStack before) {}
    private record Payment(int addition, List<Reserve> reserves) {
        boolean matches(Player player) {
            return reserves.stream().allMatch(reserve -> ItemStack.matches(reserve.before(), player.getInventory().getItem(reserve.slot())));
        }
        boolean same(Payment other) {
            if (addition != other.addition || reserves.size() != other.reserves.size()) return false;
            for (int index = 0; index < reserves.size(); index++) {
                Reserve first = reserves.get(index), second = other.reserves.get(index);
                if (first.slot() != second.slot() || first.count() != second.count() || !ItemStack.matches(first.before(), second.before())) return false;
            }
            return true;
        }
    }
    private record Prepared(SmithingMenu menu, RecipeHolder<SmithingRecipe> recipe, ItemStack result, int ingots,
            Payment payment, SmithingTableTier table) {}
    private static final class Session {
        final Prepared prepared;
        final List<ItemStack> inputs;
        final Level level;
        final boolean inventory;
        int progress;

        Session(Prepared prepared, Level level, boolean inventory) {
            this.prepared = prepared;
            this.inputs = TimedCraftingController.snapshot(inputs(prepared.menu()));
            this.level = level;
            this.inventory = inventory;
        }
    }
    private static final class Completion {
        final ServerPlayer player;
        final Session session;
        boolean claimed, validated, committed;

        Completion(ServerPlayer player, Session session) { this.player = player; this.session = session; }
    }

    private TimedSmithingController() {}

    private static Container inputs(SmithingMenu menu) { return menu.getSlot(SmithingMenu.TEMPLATE_SLOT).container; }

    public static boolean applies(AbstractContainerMenu menu) {
        return menu instanceof SmithingMenu && MaterialUpgradeRequirements.usesTemplate(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem());
    }

    /** Matching sees all available matching ingots; the real input slot keeps its normal stack limit. */
    public static SmithingRecipeInput includeInventory(SmithingRecipeInput input, Player player) {
        if (!MaterialUpgradeRequirements.usesTemplate(input.template()) || input.addition().isEmpty()) return input;
        return new SmithingRecipeInput(input.template(), input.base(), input.addition().copyWithCount(availableIngots(player, input.addition())));
    }

    public static int availableIngots(Player player, ItemStack addition) {
        if (addition.isEmpty()) return 0;
        long total = addition.getCount();
        for (ItemStack stack : player.getInventory().items)
            if (ItemStack.isSameItemSameComponents(stack, addition)) total += stack.getCount();
        return (int)Math.min(Integer.MAX_VALUE, total);
    }

    private static Payment payment(Player player, ItemStack addition, int required) {
        int fromAddition = Math.min(addition.getCount(), required);
        int remaining = required - fromAddition;
        List<Reserve> reserves = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().items.size() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stack, addition)) continue;
            int count = Math.min(remaining, stack.getCount());
            reserves.add(new Reserve(slot, count, stack.copy()));
            remaining -= count;
        }
        return remaining == 0 ? new Payment(fromAddition, List.copyOf(reserves)) : null;
    }

    public static boolean previewAllowed(SmithingMenu menu, Player player) {
        if (!applies(menu)) return true;
        ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        if (!allowedAtTable(SmithingTableTier.current(menu), result)) return false;
        int required = MaterialUpgradeRequirements.ingots(result);
        return required > 0 && availableIngots(player, menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem()) >= required;
    }

    private static Prepared prepare(ServerPlayer player, AbstractContainerMenu menu) {
        if (!(menu instanceof SmithingMenu smithing) || !applies(menu)) return null;
        var input = includeInventory(new SmithingRecipeInput(menu.getSlot(0).getItem(), menu.getSlot(1).getItem(), menu.getSlot(2).getItem()), player);
        var recipe = player.level().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, player.level());
        if (recipe.isEmpty()) return null;
        ItemStack result = recipe.get().value().assemble(input, player.level().registryAccess());
        SmithingTableTier table = SmithingTableTier.current(smithing);
        if (!allowedAtTable(table, result)) return null;
        int ingots = MaterialUpgradeRequirements.ingots(result);
        Payment payment = payment(player, menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem(), ingots);
        if (ingots <= 0 || input.addition().getCount() < ingots
                || payment == null
                || !result.isItemEnabled(player.level().enabledFeatures())
                || !ItemStack.matches(result, menu.getSlot(SmithingMenu.RESULT_SLOT).getItem())) return null;
        return new Prepared(smithing, recipe.get(), result, ingots, payment, table == null ? SmithingTableTier.VANILLA : table);
    }

    private static boolean allowedAtTable(@Nullable SmithingTableTier table, ItemStack result) {
        return !result.isEmpty() && (table == null || table.supports(SmithingTableTier.requiredFor(result)));
    }

    private static boolean allowed(ServerPlayer player, Prepared prepared, boolean inventory) {
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != prepared.menu()
                || !prepared.menu().stillValid(player)) return false;
        if (!inventory) {
            ItemStack carried = prepared.menu().getCarried();
            return carried.isEmpty() || ItemStack.isSameItemSameComponents(carried, prepared.result())
                    && carried.getCount() + prepared.result().getCount() <= carried.getMaxStackSize();
        }
        int remaining = prepared.result().getCount();
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            boolean paidEmpty = false;
            for (Reserve reserve : prepared.payment().reserves())
                if (reserve.slot() == slot && reserve.count() >= existing.getCount()) { paidEmpty = true; break; }
            if (existing.isEmpty() || paidEmpty) remaining -= prepared.result().getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(existing, prepared.result())) remaining -= existing.getMaxStackSize() - existing.getCount();
            if (remaining <= 0) return true;
        }
        return false;
    }

    public static boolean start(ServerPlayer player, AbstractContainerMenu menu, boolean inventory) {
        Prepared prepared = prepare(player, menu);
        if (prepared == null || !allowed(player, prepared, inventory)) return false;
        PlayerCurses.has(player, R196Curse.CLUMSINESS, true);
        Session previous = SESSIONS.get(player.getUUID());
        if (previous != null && previous.prepared.menu() == menu
                && previous.prepared.recipe().id().equals(prepared.recipe().id())
                && previous.prepared.payment().same(prepared.payment())
                && TimedCraftingController.matches(previous.inputs, inputs(prepared.menu()))) return true;
        SESSIONS.put(player.getUUID(), new Session(prepared, player.level(), inventory));
        show(player, 0, period(player, prepared), false, true);
        return true;
    }

    public static boolean isActive(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }
    public static int progress(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? 0 : session.progress;
    }
    public static int period(ServerPlayer player, AbstractContainerMenu menu) {
        Prepared prepared = prepare(player, menu);
        return prepared == null ? 0 : period(player, prepared);
    }
    private static int period(ServerPlayer player, Prepared prepared) {
        int basePeriod = TimedCraftingRules.period(EquipmentQualityRules.adjustedDifficulty(
                MaterialUpgradeRequirements.difficulty(prepared.result()), EquipmentDurability.quality(prepared.result())),
                player.experienceLevel, 0F, PlayerCurses.has(player, R196Curse.CLUMSINESS));
        return prepared.table().duration(basePeriod);
    }

    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) CraftingProgressPayload.send(player, session.prepared.menu().containerId, 0, 0, false, false);
    }

    public static void inputsChanged(AbstractContainerMenu menu) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || !(menu instanceof SmithingMenu smithing)) return;
        SESSIONS.entrySet().removeIf(entry -> {
            Session session = entry.getValue();
            if (session.prepared.menu() != menu || TimedCraftingController.matches(session.inputs, inputs(smithing))) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) CraftingProgressPayload.send(player, menu.containerId, 0, 0, false, false);
            return true;
        });
    }

    public static void tick(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        Prepared prepared = prepare(player, player.containerMenu);
        if (prepared == null || player.level() != session.level || prepared.menu() != session.prepared.menu()
                || !prepared.recipe().id().equals(session.prepared.recipe().id())
                || prepared.ingots() != session.prepared.ingots()
                || !prepared.payment().same(session.prepared.payment())
                || !ItemStack.matches(prepared.result(), session.prepared.result())
                || !TimedCraftingController.matches(session.inputs, inputs(prepared.menu()))
                || !allowed(player, prepared, session.inventory)) {
            cancel(player);
            return;
        }
        int period = period(player, prepared);
        if (!FoodConsumption.hasEnergy(player)) {
            if (player.tickCount % 20 == 0) show(player, session.progress, period, true, true);
            return;
        }
        if (++session.progress < period) {
            if (session.progress % 5 == 0) show(player, session.progress, period, false, true);
            return;
        }
        SESSIONS.remove(player.getUUID());
        Completion completion = new Completion(player, session);
        Completion previous = COMPLETING.get();
        COMPLETING.set(completion);
        ItemStack crafted;
        try {
            var slot = prepared.menu().getSlot(SmithingMenu.RESULT_SLOT);
            crafted = slot.remove(prepared.result().getCount());
            if (!ItemStack.matches(crafted, prepared.result())) {
                prepared.menu().createResult();
                show(player, 0, 0, false, false);
                return;
            }
            slot.onTake(player, crafted);
        } finally {
            if (previous == null) COMPLETING.remove();
            else COMPLETING.set(previous);
        }
        if (completion.committed) {
            if (session.inventory) {
                TimedCraftingController.insertWithoutDiscard(player.getInventory(), crafted);
                if (!crafted.isEmpty()) player.drop(crafted, false);
            } else {
                ItemStack carried = prepared.menu().getCarried();
                if (carried.isEmpty()) prepared.menu().setCarried(crafted);
                else carried.grow(crafted.getCount());
            }
            show(player, period, period, false, true);
        } else {
            prepared.menu().createResult();
            show(player, 0, 0, false, false);
        }
        prepared.menu().broadcastChanges();
    }

    public static boolean completing(AbstractContainerMenu menu) {
        Completion completion = COMPLETING.get();
        return completion != null && completion.session.prepared.menu() == menu;
    }

    public static boolean claim(SmithingMenu menu, Player player, ItemStack result) {
        Completion completion = COMPLETING.get();
        if (completion == null || completion.claimed || completion.player != player
                || completion.session.prepared.menu() != menu
                || !ItemStack.matches(result, completion.session.prepared.result())
                || !completion.session.prepared.payment().matches(player)
                || !TimedCraftingController.matches(completion.session.inputs, inputs(menu))) return false;
        completion.claimed = true;
        return true;
    }

    public static boolean consume(SmithingMenu menu, int slot) {
        Completion completion = COMPLETING.get();
        if (completion == null || !completion.claimed || completion.session.prepared.menu() != menu) return false;
        if (slot == SmithingMenu.TEMPLATE_SLOT) completion.validated = TimedCraftingController.matches(completion.session.inputs, inputs(menu))
                && completion.session.prepared.payment().matches(completion.player);
        if (!completion.validated) return false;
        ItemStack ingredient = menu.getSlot(slot).getItem();
        Payment payment = completion.session.prepared.payment();
        ingredient.shrink(slot == SmithingMenu.ADDITIONAL_SLOT ? payment.addition() : 1);
        if (slot == SmithingMenu.ADDITIONAL_SLOT) {
            for (Reserve reserve : payment.reserves()) {
                ItemStack stack = completion.player.getInventory().getItem(reserve.slot());
                stack.shrink(reserve.count());
                completion.player.getInventory().setItem(reserve.slot(), stack);
            }
            completion.player.getInventory().setChanged();
        }
        menu.getSlot(slot).set(ingredient);
        return true;
    }

    public static void finish(SmithingMenu menu) {
        Completion completion = COMPLETING.get();
        if (completion == null || !completion.claimed || !completion.validated || completion.session.prepared.menu() != menu) return;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack expected = completion.session.inputs.get(slot).copy();
            expected.shrink(slot == SmithingMenu.ADDITIONAL_SLOT ? completion.session.prepared.payment().addition() : 1);
            if (!ItemStack.matches(expected, menu.getSlot(slot).getItem())) return;
        }
        for (Reserve reserve : completion.session.prepared.payment().reserves()) {
            ItemStack expected = reserve.before().copy();
            expected.shrink(reserve.count());
            if (!ItemStack.matches(expected, completion.player.getInventory().getItem(reserve.slot()))) return;
        }
        completion.committed = true;
    }

    public static void damaged(ServerPlayer player, float damage) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) session.progress = Math.max(0, session.progress - TimedCraftingRules.damageSetback(damage));
    }

    private static void show(ServerPlayer player, int progress, int period, boolean hungry, boolean active) {
        CraftingProgressPayload.send(player, player.containerMenu.containerId, progress, period, hungry, active);
    }
}
