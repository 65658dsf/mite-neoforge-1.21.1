package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.UtilityShapelessRecipe;
import io.github.ningmeng123.mite.item.R196CoinItem;
import io.github.ningmeng123.mite.item.R196DisenchantingBottleItem;
import io.github.ningmeng123.mite.item.R196StoneBucketItem;
import io.github.ningmeng123.mite.item.R196RecordItem;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The remaining six coins, seven solidified vessels, four playable discs and disenchanting bottle. @author NingMeng123 */
public final class MiteUtilityContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mite.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Mite.MOD_ID);
    private static final Map<String, DeferredItem<? extends Item>> ENTRIES = new LinkedHashMap<>();
    public static final String[] COIN_MATERIALS = {"copper", "silver", "gold", "ancient_metal", "mithril", "adamantium"};
    public static final String[] BUCKET_MATERIALS = {"copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium"};
    public static final String[] RECORD_NAMES = {"underworld", "descent", "wanderer", "legends"};
    public static final DeferredItem<R196DisenchantingBottleItem> BOTTLE_OF_DISENCHANTING = ITEMS.register("bottle_of_disenchanting", R196DisenchantingBottleItem::new);
    public static final java.util.function.Supplier<RecipeSerializer<UtilityShapelessRecipe>> UTILITY_SHAPELESS = RECIPES.register("utility_shapeless", UtilityShapelessRecipe.Serializer::new);
    static {
        ENTRIES.put("bottle_of_disenchanting", BOTTLE_OF_DISENCHANTING);
        for (String material : COIN_MATERIALS) ENTRIES.put(material + "_coin", ITEMS.register(material + "_coin", () -> new R196CoinItem(material)));
        for (String material : BUCKET_MATERIALS) ENTRIES.put(material + "_stone_bucket", ITEMS.register(material + "_stone_bucket", () -> new R196StoneBucketItem(material)));
        for (String name : RECORD_NAMES) {
            ResourceLocation sound = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "record." + name);
            SOUNDS.register(sound.getPath(), () -> SoundEvent.createVariableRangeEvent(sound));
            ENTRIES.put("music_disc_" + name, ITEMS.register("music_disc_" + name, () -> new R196RecordItem(name)));
        }
    }
    private MiteUtilityContent() {}
    public static void register(IEventBus bus) {
        ITEMS.register(bus); RECIPES.register(bus); SOUNDS.register(bus);
        bus.addListener(MiteUtilityContent::creative);
    }
    public static Map<String, DeferredItem<? extends Item>> entries() { return Collections.unmodifiableMap(ENTRIES); }
    public static Item get(String path) {
        DeferredItem<? extends Item> entry = ENTRIES.get(path);
        if (entry == null) throw new IllegalArgumentException("Unknown R196 utility " + path);
        return entry.get();
    }
    public static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) ENTRIES.values().forEach(item -> event.accept(item.get()));
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) for (String metal : COIN_MATERIALS) event.accept(get(metal + "_coin"));
    }
}
