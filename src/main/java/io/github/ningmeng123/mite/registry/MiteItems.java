package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.FlintHatchetItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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
        modBus.addListener(MiteItems::addCreativeTabContents);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            event.accept(FLINT_CHIP.get());
            event.accept(SINEW.get());
        } else if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(FLINT_HATCHET.get());
        }
    }
}
