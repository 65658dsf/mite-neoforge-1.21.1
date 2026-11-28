package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.MossyDungeonEyeRecipe;
import io.github.ningmeng123.mite.item.FlintHatchetItem;
import io.github.ningmeng123.mite.item.MossyDungeonEyeItem;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Starter materials and standalone tools. @author NingMeng123 */
public final class MiteItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mite.MOD_ID);

    public static final DeferredItem<Item> FLINT_CHIP =
            ITEMS.registerSimpleItem("flint_chip", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> SINEW =
            ITEMS.registerSimpleItem("sinew", new Item.Properties().stacksTo(16));
    public static final DeferredItem<FlintHatchetItem> FLINT_HATCHET =
            ITEMS.registerItem("flint_hatchet", FlintHatchetItem::new);
    public static final DeferredItem<MossyDungeonEyeItem> MOSSY_DUNGEON_EYE =
            ITEMS.registerItem("mossy_dungeon_eye", MossyDungeonEyeItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<MossyDungeonEyeRecipe>> MOSSY_DUNGEON_EYE_RECIPE =
            RECIPES.register("mossy_dungeon_eye", () -> new SimpleCraftingRecipeSerializer<>(MossyDungeonEyeRecipe::new));

    private MiteItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        RECIPES.register(modBus);
        modBus.addListener(EventPriority.LOWEST, MiteItems::addCreativeTabContents);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        // Snapshot both sets before mutating; remove all component variants from display and search.
        Stream.concat(event.getParentEntries().stream(), event.getSearchEntries().stream())
                .filter(stack -> stack.is(Items.WOODEN_AXE) || stack.is(Items.WOODEN_PICKAXE)
                        || RemovedDiamondEquipment.isRemoved(stack))
                .toList().forEach(stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }
}
