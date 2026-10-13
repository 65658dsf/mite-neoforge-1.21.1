package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.item.RunestoneItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/** wd's right-click output selection, with no material payment until manufacturing completes. @author NingMeng123 */
public final class RunestoneCrafting {
    private record Selection(AbstractContainerMenu menu, ResourceLocation recipe, List<ItemStack> inputs, int rune) {}
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private RunestoneCrafting() {}
    public static ItemStack apply(Player player, AbstractContainerMenu menu, CraftingContainer grid, ResourceLocation recipe, ItemStack raw) {
        if (!(raw.getItem() instanceof RunestoneItem)) return raw;
        Selection selected = SELECTIONS.get(player.getUUID());
        int rune = selected != null && selected.menu() == menu && selected.recipe().equals(recipe)
                && sameIngredients(selected.inputs(), grid) ? selected.rune() : 0;
        if (player instanceof ServerPlayer)
            SELECTIONS.put(player.getUUID(), new Selection(menu, recipe, TimedCraftingController.snapshot(grid), rune));
        return RunestoneItem.withRune(raw.copy(), rune);
    }
    public static boolean select(ServerPlayer player, AbstractContainerMenu menu, int direction) {
        Selection old = SELECTIONS.get(player.getUUID());
        if (old == null || old.menu() != menu || !(menu.getSlot(0).getItem().getItem() instanceof RunestoneItem)) return false;
        SELECTIONS.put(player.getUUID(), new Selection(menu, old.recipe(), old.inputs(), Math.floorMod(old.rune() + direction, 16)));
        return true;
    }
    public static void clear(Player player) { SELECTIONS.remove(player.getUUID()); }
    private static boolean sameIngredients(List<ItemStack> inputs, CraftingContainer grid) {
        if (inputs.size() != grid.getContainerSize()) return false;
        for (int i = 0; i < inputs.size(); i++)
            if (!ItemStack.isSameItemSameComponents(inputs.get(i), grid.getItem(i))) return false;
        return true;
    }
    public static void inputsChanged(AbstractContainerMenu menu) {
        SELECTIONS.entrySet().removeIf(entry -> entry.getValue().menu() == menu
                && menu.getSlot(1).container instanceof CraftingContainer grid
                && !TimedCraftingController.isCompletingGrid(grid) && !sameIngredients(entry.getValue().inputs(), grid));
    }
}
