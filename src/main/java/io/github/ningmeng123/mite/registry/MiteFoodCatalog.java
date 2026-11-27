package io.github.ningmeng123.mite.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.item.FoodProfile;
import io.github.ningmeng123.mite.rules.NutritionRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.jetbrains.annotations.Nullable;

/** Runtime food data shared by consumption, preview and server qualification. @author NingMeng123 */
public final class MiteFoodCatalog {
    public static final FoodProfile CAKE_SLICE = new FoodProfile(2, 2, true, false, false, 166, false, false);
    // The mod constructor attaches listeners before DeferredRegister has produced the added items.
    // Resolve registry keys only during ModifyDefaultComponentsEvent or the first gameplay access.
    private static final class Catalog {
        private static final Map<Item, Entry> ENTRIES = load();
        private static final Map<Item, FoodProfile> SOURCE_PROFILES = profiles(false);
        private static final Map<Item, FoodProfile> MODERN_PROFILES = profiles(true);
    }
    private record Entry(FoodProfile profile, int stackLimit, int useTicks, float compatibilitySaturation, JsonArray effects,
                         @Nullable Item remainder, boolean modern) {}

    private MiteFoodCatalog() {}

    public static void register(IEventBus bus) {
        bus.addListener(MiteFoodCatalog::modifyDefaults);
    }

    /** Explicit original-release mappings, including the modern split of golden apple subtypes. */
    public static Map<Item, FoodProfile> sourceProfiles() { return Catalog.SOURCE_PROFILES; }
    /** Explicit modern and user-requested adaptations; these are not unchanged R196 values. */
    public static Map<Item, FoodProfile> modernProfiles() { return Catalog.MODERN_PROFILES; }

    @Nullable
    public static FoodProfile profile(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Entry entry = Catalog.ENTRIES.get(stack.getItem());
        if (entry != null) return entry.profile();
        FoodProperties food = stack.getFoodProperties(null);
        if (food == null) return null;
        // Explicit compatibility policy for external foods: native nutrition supplies both S/N.
        // No protein, fat, plant or sugar values are invented from a modern saturation multiplier.
        return new FoodProfile(food.nutrition(), food.nutrition(), false, false, false, 0,
                stack.getItem().getUseAnimation(stack) == UseAnim.DRINK, food.canAlwaysEat());
    }

    private static void modifyDefaults(ModifyDefaultComponentsEvent event) {
        Catalog.ENTRIES.forEach((item, entry) -> event.modify(item, patch -> patch
                .set(DataComponents.MAX_STACK_SIZE, entry.stackLimit())
                .set(DataComponents.FOOD, components(entry))));
        event.modify(Items.BOWL, patch -> patch.set(DataComponents.MAX_STACK_SIZE, 16));
        event.modify(Items.BUCKET, patch -> patch.set(DataComponents.MAX_STACK_SIZE, 8));
        // Cake is eaten from its placed block; a handheld cake must not receive FOOD.
        event.modify(Items.CAKE, patch -> patch.set(DataComponents.MAX_STACK_SIZE, 8));
        // Its custom finish method never applies FOOD: this native component is misleading metadata.
        event.modify(Items.OMINOUS_BOTTLE, patch -> patch.remove(DataComponents.FOOD));
    }

    private static FoodProperties components(Entry entry) {
        FoodProperties.Builder builder = new FoodProperties.Builder().nutrition(entry.profile().satiation())
                .saturationModifier(0F).alwaysEdible();
        // canAlwaysEat only opens the native interaction path; profile.alwaysEdible is authoritative
        // in R196's qualification, which also checks the independent nutrition and micronutrients.
        if (entry.remainder() != null) builder.usingConvertsTo(entry.remainder());
        for (JsonElement element : entry.effects()) {
            JsonObject effect = element.getAsJsonObject();
            Holder<MobEffect> type = effect(effect.get("effect").getAsString());
            int duration = effect.get("duration_ticks").getAsInt();
            int amplifier = effect.get("amplifier").getAsInt();
            builder.effect(() -> new MobEffectInstance(type, duration, amplifier), effect.get("probability").getAsFloat());
        }
        FoodProperties properties = builder.build();
        // AppleSkin reads absolute FOOD.saturation. Foods without an explicit gain expose R196 N
        // for display, while consuming a food still applies its actual profile values once.
        return new FoodProperties(properties.nutrition(), entry.compatibilitySaturation(), properties.canAlwaysEat(),
                entry.useTicks() / 20F, properties.usingConvertsTo(), properties.effects());
    }

    private static Holder<MobEffect> effect(String name) {
        return switch (name) {
            case "poison" -> MobEffects.POISON;
            case "nausea" -> MobEffects.CONFUSION;
            case "hunger" -> MobEffects.HUNGER;
            case "regeneration" -> MobEffects.REGENERATION;
            case "resistance" -> MobEffects.DAMAGE_RESISTANCE;
            case "fire_resistance" -> MobEffects.FIRE_RESISTANCE;
            default -> throw new IllegalArgumentException("Unknown food effect: " + name);
        };
    }

    private static Map<Item, FoodProfile> profiles(boolean modern) {
        Map<Item, FoodProfile> result = new LinkedHashMap<>();
        Catalog.ENTRIES.forEach((item, entry) -> { if (entry.modern() == modern) result.put(item, entry.profile()); });
        return Collections.unmodifiableMap(result);
    }

    private static Map<Item, Entry> load() {
        var stream = MiteFoodCatalog.class.getResourceAsStream("/mite/foods-018.json");
        if (stream == null) throw new IllegalStateException("Missing food catalog foods-018.json");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<Item, Entry> entries = new LinkedHashMap<>();
            JsonArray foods = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("foods");
            for (JsonElement element : foods) {
                JsonObject food = element.getAsJsonObject();
                Item item = item(food.get("id").getAsString());
                int nutrition = food.get("nutrition").getAsInt();
                boolean plants = food.get("plants").getAsBoolean();
                float saturationGain = food.has("saturation_gain") ? food.get("saturation_gain").getAsFloat() : 0F;
                int plantNutrientGain = food.has("plant_nutrient_gain") ? food.get("plant_nutrient_gain").getAsInt()
                        : NutritionRules.addMicronutrient(0, nutrition, plants);
                FoodProfile profile = new FoodProfile(food.get("satiation").getAsInt(), nutrition,
                        food.get("protein").getAsBoolean(), food.get("fat").getAsBoolean(), plants,
                        food.get("sugar").getAsInt(), food.get("drink").getAsBoolean(), food.get("always_edible").getAsBoolean(),
                        saturationGain, plantNutrientGain);
                Item remainder = food.has("remainder") ? item(food.get("remainder").getAsString()) : null;
                float compatibilitySaturation = saturationGain > 0F ? saturationGain : nutrition;
                if (food.has("native_saturation")) compatibilitySaturation = food.get("native_saturation").getAsFloat();
                String sourceKind = food.get("source_kind").getAsString();
                if (!("r196".equals(sourceKind) || "modern_adaptation".equals(sourceKind) || "user_adaptation".equals(sourceKind)))
                    throw new IllegalArgumentException("Unknown food source kind: " + sourceKind);
                Entry entry = new Entry(profile, food.get("stack_limit").getAsInt(), food.get("use_ticks").getAsInt(),
                        compatibilitySaturation, food.getAsJsonArray("effects"), remainder, !"r196".equals(sourceKind));
                if (entries.put(item, entry) != null) throw new IllegalArgumentException("Duplicate food: " + food.get("id"));
            }
            return Collections.unmodifiableMap(entries);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load R196 food mappings", exception);
        }
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalArgumentException("Unregistered food item: " + id);
        return BuiltInRegistries.ITEM.get(key);
    }
}
