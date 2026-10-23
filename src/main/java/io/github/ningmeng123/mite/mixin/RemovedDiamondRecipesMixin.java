package io.github.ningmeng123.mite.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep server lookup, recipe-book synchronization and automation on the same filtered recipe set. @author NingMeng123 */
@Mixin(RecipeManager.class)
public abstract class RemovedDiamondRecipesMixin {
    @Shadow @Final private HolderLookup.Provider registries;
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;
    @Shadow private Multimap<RecipeType<?>, RecipeHolder<?>> byType;

    @Inject(method = {"apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            "replaceRecipes"}, at = @At("TAIL"))
    private void mite$filterDiamondResults(CallbackInfo ci) {
        var names = ImmutableMap.<ResourceLocation, RecipeHolder<?>>builder();
        var types = ImmutableMultimap.<RecipeType<?>, RecipeHolder<?>>builder();
        for (var holder : byName.values()) {
            if (RemovedDiamondEquipment.isRemoved(holder.value().getResultItem(registries))) continue;
            // This otherwise-legal material recipe cycles through retired uniques in recipe viewers.
            if (holder.id().getNamespace().equals(SimplySwordsCompat.MOD_ID)
                    && holder.id().getPath().equals("unique_melting")) continue;
            names.put(holder.id(), holder);
            types.put(holder.value().getType(), holder);
        }
        byName = names.build();
        byType = types.build();
    }
}
