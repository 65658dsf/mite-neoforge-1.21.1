package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Development data generation entry point. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MiteDataGenerators {
    private MiteDataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new MiteRecipeProvider(event.getGenerator().getPackOutput(), event.getLookupProvider()));
        event.getGenerator().addProvider(true, new MaterialAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new ToolAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new WorkbenchAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new FurnaceAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new ArmorAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new RangedAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new FunctionalAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new DecorativeAssetsProvider(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
        event.getGenerator().addProvider(true, new WeaponAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new UtilityAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(event.includeServer(), new StackingRecipeAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new WorldAssetsProvider(event.getGenerator().getPackOutput()));
        event.getGenerator().addProvider(true, new OreAssetsProvider(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
        event.getGenerator().addProvider(true, new OreWorldgenProvider(event.getGenerator().getPackOutput(), event.getExistingFileHelper()));
        event.getGenerator().addProvider(event.includeServer(),
                new MiteLootModifierProvider(event.getGenerator().getPackOutput(), event.getLookupProvider()));
        MiteBlockTagsProvider blockTags = new MiteBlockTagsProvider(event.getGenerator().getPackOutput(),
                event.getLookupProvider(), event.getExistingFileHelper());
        event.getGenerator().addProvider(event.includeServer(), blockTags);
        event.getGenerator().addProvider(event.includeServer(), new MiteItemTagsProvider(
                event.getGenerator().getPackOutput(), event.getLookupProvider(), blockTags.contentsGetter(), event.getExistingFileHelper()));
    }
}
