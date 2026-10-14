package io.github.ningmeng123.mite.crafting;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/** One native merchant transaction distributes its complete result into source-limited stacks. @author NingMeng123 */
public final class MerchantBatchTransfers {
    private MerchantBatchTransfers() {}

    public static boolean handles(AbstractContainerMenu menu, Player player, int slot, ClickType type) {
        if (!(menu instanceof MerchantMenu) || slot != 2
                || type != ClickType.PICKUP && type != ClickType.QUICK_MOVE && type != ClickType.SWAP && type != ClickType.THROW
                || !(menu.getSlot(2).container instanceof MerchantContainer container)) return false;
        ItemStack preview = menu.getSlot(2).getItem();
        MerchantOffer offer = container.getActiveOffer();
        return !preview.isEmpty() && preview.getCount() > preview.getMaxStackSize() && offer != null && !offer.isOutOfStock()
                && ItemStack.matches(preview, offer.assemble())
                && (offer.satisfiedBy(container.getItem(0), container.getItem(1))
                    || offer.satisfiedBy(container.getItem(1), container.getItem(0)));
    }

    /** Empty means no transaction occurred; nonempty is the complete original batch for the native Shift loop. */
    public static ItemStack take(ServerPlayer player, MerchantMenu menu, int button, ClickType type) {
        if (player.containerMenu != menu || !menu.stillValid(player) || !handles(menu, player, 2, type)) return ItemStack.EMPTY;
        ItemStack preview = menu.getSlot(2).getItem();
        ItemStack carried = menu.getCarried();
        int cursorRoom = carried.isEmpty() ? preview.getMaxStackSize()
                : ItemStack.isSameItemSameComponents(carried, preview) ? Math.max(0, carried.getMaxStackSize() - carried.getCount()) : 0;
        if (type == ClickType.PICKUP && (button != 0 && button != 1 || cursorRoom == 0)) return ItemStack.EMPTY;
        if (type == ClickType.THROW && (button != 0 && button != 1 || !carried.isEmpty())) return ItemStack.EMPTY;
        if (type == ClickType.SWAP && (button < 0 || button > 8 && button != 40 || !player.getInventory().getItem(button).isEmpty()))
            return ItemStack.EMPTY;
        if (type == ClickType.QUICK_MOVE && !hasInventoryRoom(player, preview)) return ItemStack.EMPTY;

        // MerchantResultSlot.remove records the full amount. Its one onTake charges the exact offer,
        // increments uses/statistics, retains native merchant callbacks and awards merchant experience.
        ItemStack batch = menu.getSlot(2).safeTake(preview.getCount(), Integer.MAX_VALUE, player);
        if (batch.isEmpty()) return ItemStack.EMPTY;
        ItemStack complete = batch.copy();
        if (type == ClickType.PICKUP) {
            int first = Math.min(batch.getCount(), button == 1 ? Math.min(1, cursorRoom) : cursorRoom);
            if (carried.isEmpty()) menu.setCarried(batch.split(first));
            else { carried.grow(first); batch.shrink(first); }
        } else if (type == ClickType.SWAP) {
            player.getInventory().setItem(button, batch.split(Math.min(batch.getCount(), batch.getMaxStackSize())));
        }
        if (type != ClickType.THROW) TimedCraftingController.insertWithoutDiscard(player.getInventory(), batch);
        while (!batch.isEmpty()) player.drop(batch.split(Math.min(batch.getCount(), batch.getMaxStackSize())), type == ClickType.THROW);
        menu.broadcastChanges();
        return complete;
    }

    private static boolean hasInventoryRoom(Player player, ItemStack result) {
        for (ItemStack stored : player.getInventory().items)
            if (stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, result) && stored.getCount() < stored.getMaxStackSize()) return true;
        return false;
    }
}
