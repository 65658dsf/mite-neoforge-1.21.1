package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.survival.SourceCookingExperience;
import java.util.ArrayList;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Recipe-book placement validates before removing ingredients and rewards only returned results. @author NingMeng123 */
@Mixin(ServerPlaceRecipe.class)
public abstract class FurnaceRecipeBookMixin {
    @Shadow protected Inventory inventory;
    @Shadow protected RecipeBookMenu<?, ?> menu;
    @Inject(method = "moveItemToGrid", at = @At("HEAD"), cancellable = true)
    private void mite$validate(Slot slot, ItemStack stack, int count, CallbackInfoReturnable<Integer> cir) {
        if (menu instanceof AbstractFurnaceMenu && !slot.mayPlace(stack)) cir.setReturnValue(-1);
    }
    @Inject(method = "recipeClicked", at = @At("HEAD"), cancellable = true)
    private void mite$preserveFullOutput(ServerPlayer player, RecipeHolder<?> recipe, boolean all, CallbackInfo ci) {
        if (!(menu instanceof AbstractFurnaceMenu)) return;
        var room = new ArrayList<ItemStack>();
        player.getInventory().items.forEach(stack -> room.add(stack.copy()));
        for (int slot : new int[] {0, 2}) {
            ItemStack returned = menu.getSlot(slot).getItem().copy();
            for (ItemStack existing : room) {
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, returned)) {
                    int moved = Math.min(returned.getCount(), Math.max(0, Math.min(existing.getMaxStackSize(), 64) - existing.getCount()));
                    existing.grow(moved); returned.shrink(moved);
                }
            }
            for (int index = 0; index < room.size() && !returned.isEmpty(); index++) {
                if (room.get(index).isEmpty()) {
                    int moved = Math.min(returned.getCount(), Math.min(64, returned.getMaxStackSize()));
                    room.set(index, returned.copyWithCount(moved)); returned.shrink(moved);
                }
            }
            if (!returned.isEmpty()) { ci.cancel(); return; }
        }
    }
    @WrapMethod(method = "clearGrid")
    private void mite$returnResults(Operation<Void> original) {
        if (!(menu instanceof AbstractFurnaceMenu)) { original.call(); return; }
        for (int index : new int[] {0, 2}) {
            Slot slot = menu.getSlot(index);
            ItemStack before = slot.getItem().copy(), remaining = before.copy();
            // add mutates only the inserted quantity; placeItemBackInInventory drops leftovers without clearing them.
            inventory.add(remaining);
            slot.set(remaining);
            if (index == 2) SourceCookingExperience.awardTaken(inventory.player, before, before.getCount() - remaining.getCount());
        }
        // The native clearCraftingContent unconditionally deletes leftovers if the inventory filled up.
    }
}
