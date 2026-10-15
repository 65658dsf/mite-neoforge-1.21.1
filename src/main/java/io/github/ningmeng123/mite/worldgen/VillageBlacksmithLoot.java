package io.github.ningmeng123.mite.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** R196 aji table and mk random-slot chest algorithm, including discrete source wear. @author NingMeng123 */
public final class VillageBlacksmithLoot {
    public record Entry(ResourceLocation item, int minimum, int maximum, int weight, int minimumDay, boolean surfaceOnly) {}
    private static final List<Entry> ENTRIES = readEntries();
    private static final int WEIGHT = ENTRIES.stream().mapToInt(Entry::weight).sum();

    private VillageBlacksmithLoot() {}

    public static List<Entry> entries() { return ENTRIES; }

    public static int totalWeight() { return WEIGHT; }

    /** Invoked after registration, before any chest writes; resource parsing alone is bootstrap-safe. */
    public static void validateRegisteredItems() {
        var missing = ENTRIES.stream().map(Entry::item).filter(id -> !BuiltInRegistries.ITEM.containsKey(id)
                || BuiltInRegistries.ITEM.get(id) == Items.AIR).toList();
        if (!missing.isEmpty()) throw new IllegalStateException("Unregistered R196 village rewards: " + missing);
    }

    private static List<Entry> readEntries() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(VillageBlacksmithLoot.class
                .getResourceAsStream("/mite/village-blacksmith-loot.json")), StandardCharsets.UTF_8)) {
            JsonObject table = JsonParser.parseReader(reader).getAsJsonObject();
            List<Entry> result = new ArrayList<>();
            for (var row : table.getAsJsonArray("entries")) {
                JsonObject entry = row.getAsJsonObject();
                Entry definition = new Entry(ResourceLocation.parse(entry.get("item").getAsString()),
                        entry.get("min").getAsInt(), entry.get("max").getAsInt(), entry.get("weight").getAsInt(),
                        entry.get("minimum_day").getAsInt(), entry.get("requires_y_at_least_48").getAsBoolean());
                if (definition.minimum() < 1 || definition.maximum() < definition.minimum() || definition.weight() < 1)
                    throw new IllegalStateException("Invalid village blacksmith row: " + entry);
                result.add(definition);
            }
            if (result.size() != 56 || result.stream().mapToInt(Entry::weight).sum() != 302)
                throw new IllegalStateException("R196 village table must contain 56 rows, total weight 302");
            return List.copyOf(result);
        } catch (Exception exception) { throw new IllegalStateException("Cannot read the R196 village chest table", exception); }
    }

    public static void fill(Container inventory, int y, ResourceKey<Level> dimension, long dayTime, RandomSource random) {
        validateRegisteredItems();
        inventory.clearContent();
        int rolls = 3 + random.nextInt(6);
        long day = R196StructureGeneration.displayDay(dayTime);
        for (int roll = 0; roll < rolls; roll++) {
            Entry chosen = null;
            for (int attempt = 0; attempt < 4; attempt++) {
                Entry entry = choose(random);
                if (entry.surfaceOnly() && y < 48 || dimension.equals(Level.OVERWORLD) && day < entry.minimumDay()) continue;
                chosen = entry;
                break;
            }
            if (chosen == null) continue;
            var item = BuiltInRegistries.ITEM.get(chosen.item());
            if (item == Items.AIR) throw new IllegalStateException("Unregistered R196 village reward " + chosen.item());
            int count = chosen.minimum() + random.nextInt(chosen.maximum() - chosen.minimum() + 1);
            ItemStack stack = new ItemStack(item);
            if (stack.getMaxStackSize() >= count) {
                stack.setCount(count);
                applyChestWear(stack, random);
                inventory.setItem(random.nextInt(inventory.getContainerSize()), stack);
            } else {
                for (int index = 0; index < count; index++) {
                    ItemStack single = new ItemStack(item);
                    applyChestWear(single, random);
                    inventory.setItem(random.nextInt(inventory.getContainerSize()), single);
                }
            }
        }
    }

    private static Entry choose(RandomSource random) {
        int cursor = random.nextInt(WEIGHT);
        for (Entry entry : ENTRIES) {
            cursor -= entry.weight();
            if (cursor < 0) return entry;
        }
        throw new IllegalStateException("Village blacksmith weight cursor exhausted");
    }

    /** Chest generation never calls the mob-quality downgrade for wooden or leather equipment. */
    public static void applyChestWear(ItemStack stack, RandomSource random) {
        if (!stack.isDamageableItem()) return;
        if (EquipmentDurability.isManaged(stack)) EquipmentDurability.setQuality(stack, Quality.AVERAGE);
        int fraction = random.nextInt(7);
        stack.setDamageValue((int) ((long) stack.getMaxDamage() * fraction / 10L));
    }
}
