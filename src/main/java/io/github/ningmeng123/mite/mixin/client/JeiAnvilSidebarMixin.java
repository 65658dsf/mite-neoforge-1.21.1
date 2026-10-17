package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.integration.jei.AnvilJeiCategory;
import io.github.ningmeng123.mite.integration.jei.AnvilJeiSidebar;
import java.util.function.Consumer;
import java.util.stream.Stream;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.ILookupState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional JEI integration, leaving every other category's native sidebar unchanged. @author NingMeng123 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLogic", remap = false)
public abstract class JeiAnvilSidebarMixin {
    @Shadow private ILookupState state;
    @Shadow private IRecipeCategory<?> cachedRecipeCategory;
    @Shadow private IRecipeLayoutList cachedRecipeLayoutsWithButtons;
    @Shadow public abstract IRecipeCategory<?> getSelectedRecipeCategory();

    @Inject(method = "getRecipeCatalystGroups()Ljava/util/stream/Stream;", at = @At("HEAD"), cancellable = true, remap = false)
    private void mite$repairSidebar(CallbackInfoReturnable<Stream<Consumer<IIngredientAcceptor<?>>>> cir) {
        if (getSelectedRecipeCategory() instanceof AnvilJeiCategory category)
            cir.setReturnValue(AnvilJeiSidebar.groups(state, category, cachedRecipeCategory, cachedRecipeLayoutsWithButtons));
    }
}
