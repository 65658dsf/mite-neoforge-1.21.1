package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** R196 metal materials, reusing vanilla copper/iron/gold identities where present. @author NingMeng123 */
public final class MiteMaterials {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);

    public static final DeferredItem<Item> COPPER_NUGGET = nugget("copper_nugget");
    public static final DeferredItem<Item> SILVER_NUGGET = nugget("silver_nugget");
    public static final DeferredItem<Item> ANCIENT_METAL_NUGGET = nugget("ancient_metal_nugget");
    public static final DeferredItem<Item> MITHRIL_NUGGET = nugget("mithril_nugget");
    public static final DeferredItem<Item> ADAMANTIUM_NUGGET = nugget("adamantium_nugget");
    public static final DeferredItem<Item> SILVER_INGOT = ingot("silver_ingot");
    public static final DeferredItem<Item> ANCIENT_METAL_INGOT = ingot("ancient_metal_ingot");
    public static final DeferredItem<Item> MITHRIL_INGOT = ingot("mithril_ingot");
    public static final DeferredItem<Item> ADAMANTIUM_INGOT = ingot("adamantium_ingot");
    public static final DeferredItem<Item> VIBRANIUM_NUGGET = ITEMS.registerSimpleItem("vibranium_nugget", new Item.Properties().stacksTo(64).fireResistant());
    public static final DeferredItem<Item> VIBRANIUM_INGOT = ITEMS.registerSimpleItem("vibranium_ingot", new Item.Properties().stacksTo(8).fireResistant());

    public static final DeferredItem<Item> OBSIDIAN_STICK = ITEMS.registerSimpleItem("obsidian_stick", new Item.Properties().stacksTo(64));

    private MiteMaterials() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        MaterialRecipeSerializers.register(modBus);
        modBus.addListener(MiteMaterials::modifyDefaultComponents);
    }

    public static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (Item ingot : new Item[]{Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT}) {
            // Patch only the stack limit; preserve every other vanilla/default component.
            event.modify(ingot, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 8));
        }
    }

    private static DeferredItem<Item> nugget(String name) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(64));
    }

    private static DeferredItem<Item> ingot(String name) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(8));
    }
}
