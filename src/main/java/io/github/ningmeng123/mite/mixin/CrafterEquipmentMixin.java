package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Validate before the automated result is emitted or ingredients are consumed. @author NingMeng123 */
@Mixin(CrafterBlock.class)
public abstract class CrafterEquipmentMixin {
    @ModifyExpressionValue(method = "dispenseFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mite$equipment(ItemStack result, BlockState state, ServerLevel level, BlockPos pos) {
        return WorkbenchRules.allowsCrafter(level, pos, result) ? result : ItemStack.EMPTY;
    }
}
