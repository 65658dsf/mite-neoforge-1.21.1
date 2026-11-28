package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.config.GravelDropSettings;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.github.ningmeng123.mite.worldgen.OreGenerationDisplay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jeresources.api.restrictions.BiomeRestriction;
import jeresources.api.restrictions.DimensionRestriction;
import jeresources.api.restrictions.Restriction;
import jeresources.entry.WorldGenEntry;
import jeresources.jei.JEIConfig;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Called by the JEI plugin when JER is installed. @author NingMeng123 */
final class MiteJerCompat {
    private MiteJerCompat() {}

    static void register(IRecipeRegistration registration) {
        var entries = new ArrayList<WorldGenEntry>();
        boolean wholeFlint = GravelDropSettings.wholeFlint();
        for (var ore : OreGenerationDisplay.entries()) {
            if (ore.kind() == Kind.GRAVEL) {
                for (var page : GravelJerDrops.pages(false, wholeFlint))
                    entries.add(new MiteResourceEntry(ore, page));
            } else {
                entries.add(new MiteResourceEntry(ore, null));
            }
        }
        for (var page : GravelJerDrops.pages(true, wholeFlint))
            entries.add(new MiteResourceEntry(page, MiteResourceBlocks.NETHER_GRAVEL.get()));
        // JER's registerEntry merges duplicate block/dimension keys. Submit distinct native entries in the
        // same recipe registration phase JER uses, so old graphs and paginated gravel drops stay separate.
        registration.addRecipes(JEIConfig.WORLD_GEN_TYPE, entries);
    }

    static void hideReplaced(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        var entries = manager.createRecipeLookup(JEIConfig.WORLD_GEN_TYPE).get().toList();
        var coveredByDimension = new HashMap<ResourceLocation, Set<Item>>();
        for (var entry : entries) {
            if (entry instanceof MiteResourceEntry)
                coveredByDimension.computeIfAbsent(ResourceLocation.parse(entry.getDimension()), key -> new HashSet<>())
                        .add(entry.getBlock().getItem());
        }
        var replaced = entries.stream().filter(entry -> !(entry instanceof MiteResourceEntry)
                && isReplaced(entry, coveredByDimension)).toList();
        if (!replaced.isEmpty()) manager.hideRecipes(JEIConfig.WORLD_GEN_TYPE, replaced);
    }

    private static boolean isReplaced(WorldGenEntry entry, Map<ResourceLocation, Set<Item>> coveredByDimension) {
        var dimension = ResourceLocation.tryParse(entry.getDimension());
        if (dimension == null) return false;
        var covered = coveredByDimension.get(dimension);
        if (covered == null || !covered.contains(entry.getBlock().getItem())) return false;
        if (entry.hasDeepSlateVariant() && !covered.contains(entry.getDeepSlateBlock().getItem())) return false;
        var dimensionWide = new Restriction(new DimensionRestriction(ResourceKey.create(Registries.DIMENSION, dimension)));
        if (entry.getRestriction().equals(dimensionWide)) return true;

        // These two vanilla placed features are explicitly removed by MITE's Overworld biome modifier.
        return entry.getBlock().is(Items.COPPER_ORE) && entry.getRestriction().equals(
                new Restriction(BiomeRestriction.DRIPSTONE_CAVES, DimensionRestriction.OVERWORLD))
                || entry.getBlock().is(Items.GOLD_ORE) && entry.getRestriction().equals(
                new Restriction(BiomeRestriction.BADLANDS, DimensionRestriction.OVERWORLD));
    }
}
