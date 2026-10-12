package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.FlintHatchetItem;
import java.util.stream.Stream;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Production starter materials and tools. @author NingMeng123 */
public final class MiteItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);

    public static final DeferredItem<Item> FLINT_CHIP =
            ITEMS.registerSimpleItem("flint_chip", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> SINEW =
            ITEMS.registerSimpleItem("sinew", new Item.Properties().stacksTo(16));
    public static final DeferredItem<FlintHatchetItem> FLINT_HATCHET =
            ITEMS.registerItem("flint_hatchet", FlintHatchetItem::new);

    private MiteItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(EventPriority.LOWEST, MiteItems::addCreativeTabContents);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        // Snapshot both sets before mutating; remove all component variants from display and search.
        Stream.concat(event.getParentEntries().stream(), event.getSearchEntries().stream())
                .filter(stack -> stack.is(Items.WOODEN_AXE) || stack.is(Items.WOODEN_PICKAXE))
                .toList().forEach(stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            event.accept(FLINT_CHIP.get());
            event.accept(SINEW.get());
        } else if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(FLINT_HATCHET.get());
        }
    }
}
