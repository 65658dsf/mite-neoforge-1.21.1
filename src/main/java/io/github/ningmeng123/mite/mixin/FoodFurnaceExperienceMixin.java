package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.SourceCookingExperience;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Existing source food banks, including loaded ones, cannot award hopper-produced XP later. @author NingMeng123 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FoodFurnaceExperienceMixin {
    /** Recipe reloads may remove a cooking recipe or reuse its old key for another recipe type. */
    @Inject(method = "lambda$getRecipesToAwardAndPopExperience$5", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private static void mite$skipHistoricalNonCookingRecipes(List<RecipeHolder<?>> recipes, ServerLevel level,
            Vec3 position, Object2IntMap.Entry<ResourceLocation> entry, RecipeHolder<?> holder, CallbackInfo ci) {
        if (!(holder.value() instanceof AbstractCookingRecipe)) ci.cancel();
    }

    // javap -p -c of neoforge-21.1.259-merged.jar confirms the exact static lambda name and
    // its sole AbstractCookingRecipe.getExperience invocation at offset25. Keep require1 strict.
    @Redirect(method = "lambda$getRecipesToAwardAndPopExperience$5", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;getExperience()F"), require = 1, allow = 1)
    private static float mite$excludeSourceFoodFromStoredRecipeExperience(AbstractCookingRecipe recipe,
            List<RecipeHolder<?>> recipes, ServerLevel level, Vec3 position,
            Object2IntMap.Entry<ResourceLocation> entry, RecipeHolder<?> holder) {
        // Recipe-history entries still serve native recipe unlocking; only these nine outputs
        // have their XP paid by the actual player withdrawal. Other foods and minerals retain vanilla.
        return SourceCookingExperience.isSourceRecipe(recipe, level.registryAccess()) ? 0F : recipe.getExperience();
    }
}
