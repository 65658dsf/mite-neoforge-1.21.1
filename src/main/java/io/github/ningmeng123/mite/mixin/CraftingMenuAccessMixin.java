package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.crafting.CraftingAccessRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Filter before vanilla publishes and synchronizes the result. @author NingMeng123 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuAccessMixin {
    @ModifyExpressionValue(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"),
            require = 1, allow = 1)
    private static ItemStack mite$checkEquipment(ItemStack output, AbstractContainerMenu menu, Level level,
            Player player, CraftingContainer grid, ResultContainer result, RecipeHolder<CraftingRecipe> hint) {
        return CraftingAccessRules.canCraft(grid, output) ? output : ItemStack.EMPTY;
    }
}
