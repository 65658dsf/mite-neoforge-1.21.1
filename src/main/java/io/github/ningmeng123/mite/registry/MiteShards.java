package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered R196 fragments; whole gems keep their existing modern IDs. @author NingMeng123 */
public final class MiteShards {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    public static final DeferredItem<Item> OBSIDIAN_SHARD = ITEMS.registerSimpleItem("obsidian_shard");
    public static final DeferredItem<Item> EMERALD_SHARD = ITEMS.registerSimpleItem("emerald_shard");
    public static final DeferredItem<Item> DIAMOND_SHARD = ITEMS.registerSimpleItem("diamond_shard");
    public static final DeferredItem<Item> QUARTZ_SHARD = ITEMS.registerSimpleItem("quartz_shard");
    public static final DeferredItem<Item> GLASS_SHARD = ITEMS.registerSimpleItem("glass_shard");

    private MiteShards() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(MiteShards::modifyComponents);
    }

    private static void modifyComponents(ModifyDefaultComponentsEvent event) {
        for (Item item : new Item[]{Items.FLINT, Items.QUARTZ, Items.LAPIS_LAZULI}) {
            event.modify(item, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 16));
        }
        for (Item item : new Item[]{Items.EMERALD, Items.DIAMOND}) {
            event.modify(item, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 32));
        }
    }
}
