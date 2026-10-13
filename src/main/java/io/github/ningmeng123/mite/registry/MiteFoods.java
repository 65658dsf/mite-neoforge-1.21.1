package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.MiteBowlItem;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered R196 foods, processing materials and matching metal vessels. @author NingMeng123 */
public final class MiteFoods {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mite.MOD_ID);
    private static final Map<String, DeferredItem<? extends Item>> ENTRIES = new LinkedHashMap<>();
    public static final String[] METALS = {"copper", "silver", "gold", "mithril", "adamantium", "ancient_metal"};

    public static final DeferredItem<Item> FLOUR = simple("flour");
    public static final DeferredItem<Item> DOUGH = simple("dough");
    public static final DeferredItem<Item> CHEESE = simple("cheese");
    public static final DeferredItem<Item> CHOCOLATE = simple("chocolate");
    public static final DeferredItem<Item> MANURE = simple("manure");
    public static final DeferredItem<ItemNameBlockItem> ONION = add("onion", () ->
            new ItemNameBlockItem(MiteFoodPlants.ONIONS.get(), new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> ORANGE = simple("orange");
    public static final DeferredItem<Item> BANANA = simple("banana");
    public static final DeferredItem<Item> BLUEBERRIES = simple("blueberries");
    public static final DeferredItem<Item> RAW_LARGE_FISH = simple("raw_large_fish");
    public static final DeferredItem<Item> COOKED_LARGE_FISH = simple("cooked_large_fish");
    public static final DeferredItem<Item> RAW_WORM = simple("raw_worm");
    public static final DeferredItem<Item> COOKED_WORM = simple("cooked_worm");

    public static final DeferredItem<MiteBowlItem> MILK_BOWL = bowl("milk_bowl", MiteBowlItem.Contents.MILK);
    public static final DeferredItem<MiteBowlItem> WATER_BOWL = bowl("water_bowl", MiteBowlItem.Contents.WATER);
    public static final DeferredItem<MiteBowlItem> BEEF_STEW = bowl("beef_stew", MiteBowlItem.Contents.FOOD);
    public static final DeferredItem<MiteBowlItem> CHICKEN_SOUP = bowl("chicken_soup", MiteBowlItem.Contents.SOUP);
    public static final DeferredItem<MiteBowlItem> VEGETABLE_SOUP = bowl("vegetable_soup", MiteBowlItem.Contents.SOUP);
    public static final DeferredItem<MiteBowlItem> ICE_CREAM = bowl("ice_cream", MiteBowlItem.Contents.FOOD);
    public static final DeferredItem<MiteBowlItem> SALAD = bowl("salad", MiteBowlItem.Contents.FOOD);
    public static final DeferredItem<MiteBowlItem> CREAM_OF_MUSHROOM_SOUP = bowl("cream_of_mushroom_soup", MiteBowlItem.Contents.SOUP);
    public static final DeferredItem<MiteBowlItem> CREAM_OF_VEGETABLE_SOUP = bowl("cream_of_vegetable_soup", MiteBowlItem.Contents.SOUP);
    public static final DeferredItem<MiteBowlItem> PUMPKIN_SOUP = bowl("pumpkin_soup", MiteBowlItem.Contents.SOUP);
    public static final DeferredItem<MiteBowlItem> MASHED_POTATO = bowl("mashed_potato", MiteBowlItem.Contents.FOOD);
    public static final DeferredItem<MiteBowlItem> SORBET = bowl("sorbet", MiteBowlItem.Contents.FOOD);
    public static final DeferredItem<MiteBowlItem> PORRIDGE = bowl("porridge", MiteBowlItem.Contents.FOOD);
    public static final DeferredItem<MiteBowlItem> CEREAL = bowl("cereal", MiteBowlItem.Contents.FOOD);

    static {
        // Register each empty peer before its filled peers; the suppliers resolve only at item registration.
        for (String metal : METALS) {
            add(metal + "_bucket", () -> new MiteBucketItem(new Item.Properties().stacksTo(8), metal, MiteBucketItem.Contents.EMPTY));
            add(metal + "_water_bucket", () -> new MiteBucketItem(new Item.Properties().stacksTo(1)
                    .craftRemainder(get(metal + "_bucket")), metal, MiteBucketItem.Contents.WATER));
            add(metal + "_milk_bucket", () -> new MiteBucketItem(new Item.Properties().stacksTo(1)
                    .craftRemainder(get(metal + "_bucket")), metal, MiteBucketItem.Contents.MILK));
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FOOD_TAB = TABS.register("foods", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.mite.foods"))
                    .icon(() -> new ItemStack(CHEESE.get())).displayItems((parameters, output) -> {
                        Set<Item> items = new LinkedHashSet<>();
                        items.addAll(MiteFoodCatalog.sourceProfiles().keySet());
                        items.addAll(MiteFoodCatalog.modernProfiles().keySet());
                        items.add(Items.CAKE);
                        items.add(Items.BOWL);
                        items.add(Items.BUCKET);
                        items.add(Items.WATER_BUCKET);
                        items.add(MiteFoodPlants.BLUEBERRY_BUSH_ITEM.get());
                        ENTRIES.values().forEach(entry -> items.add(entry.get()));
                        items.forEach(output::accept);
                    }).build());

    private MiteFoods() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
        bus.addListener(MiteFoods::creativeContents);
    }

    /** Used at runtime by vessel peers, recipes and natural acquisition. */
    public static Item get(String path) {
        DeferredItem<? extends Item> entry = ENTRIES.get(path);
        if (entry == null) throw new IllegalArgumentException("Unknown MITE food or vessel: " + path);
        return entry.get();
    }

    public static Map<String, DeferredItem<? extends Item>> entries() {
        return Collections.unmodifiableMap(ENTRIES);
    }

    private static DeferredItem<Item> simple(String name) {
        return add(name, () -> new Item(new Item.Properties().stacksTo(16)));
    }

    private static DeferredItem<MiteBowlItem> bowl(String name, MiteBowlItem.Contents contents) {
        return add(name, () -> new MiteBowlItem(new Item.Properties().stacksTo(4).craftRemainder(Items.BOWL), contents));
    }

    private static <T extends Item> DeferredItem<T> add(String name, Supplier<T> factory) {
        DeferredItem<T> entry = ITEMS.register(name, factory);
        if (ENTRIES.put(name, entry) != null) throw new IllegalArgumentException("Duplicate MITE food: " + name);
        return entry;
    }

    private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FOOD_AND_DRINKS)) {
            ENTRIES.values().stream().map(DeferredItem::get)
                    .filter(item -> MiteFoodCatalog.profile(new ItemStack(item)) != null).forEach(event::accept);
        } else if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            event.accept(FLOUR.get());
            event.accept(MANURE.get());
        }
        else if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            ENTRIES.forEach((path, item) -> { if (path.endsWith("_bucket")) event.accept(item.get()); });
        }
    }
}
