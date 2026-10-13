package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modern campfires retain their native cooking at heat1; they cannot replace high-temperature furnaces. @author NingMeng123 */
@Mixin(CampfireBlockEntity.class)
public abstract class FurnaceCampfireHeatMixin {
    @Inject(method = "getCookableRecipe", at = @At("HEAD"), cancellable = true)
    private void mite$checkRecipeHeat(ItemStack input, CallbackInfoReturnable<Optional<RecipeHolder<CampfireCookingRecipe>>> cir) {
        if (FurnaceThermalController.requiredHeat(input) > 1) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "placeFood", at = @At("HEAD"), cancellable = true)
    private void mite$checkInsertionHeat(LivingEntity entity, ItemStack input, int time, CallbackInfoReturnable<Boolean> cir) {
        if (FurnaceThermalController.requiredHeat(input) > 1) cir.setReturnValue(false);
    }

    @WrapOperation(method = "cookTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 0))
    private static boolean mite$skipHighTemperatureSlot(ItemStack input, Operation<Boolean> original) {
        // Inspect every slot at the final server tick: native NBT/component/direct-list insertion must not bypass heat.
        // Treat only that slot as inactive, preserving its original stack while other native food slots still cook.
        return original.call(input) || FurnaceThermalController.requiredHeat(input) > 1;
    }
}
