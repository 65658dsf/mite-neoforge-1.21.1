package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.BlueberriesItem;
import io.github.ningmeng123.mite.item.MiteBowlItem;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered R196 foods, processing materials and matching metal vessels. @author NingMeng123 */
public final class MiteFoods {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
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
    public static final DeferredItem<BlueberriesItem> BLUEBERRIES = add("blueberries", () ->
            new BlueberriesItem(new Item.Properties().stacksTo(16)));
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
            add(metal + "_lava_bucket", () -> new MiteBucketItem(new Item.Properties().stacksTo(1)
                    .craftRemainder(get(metal + "_bucket")), metal, MiteBucketItem.Contents.LAVA));
        }
    }

    private MiteFoods() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
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

}
