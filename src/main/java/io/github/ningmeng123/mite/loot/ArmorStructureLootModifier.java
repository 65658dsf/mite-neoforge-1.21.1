package io.github.ningmeng123.mite.loot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Scoped source gear pools retain source roll counts and full denominators, without doubling modern gear loot. @author NingMeng123 */
public final class ArmorStructureLootModifier extends LootModifier {
    public static final MapCodec<ArmorStructureLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, ArmorStructureLootModifier::new));
    public record Entry(String item, int minimum, int maximum, int weight, int minimumOverworldDay) {
        public Entry(String item, int minimum, int maximum, int weight) { this(item, minimum, maximum, weight, 0); }
    }
    public record Pool(String modernTable, int minimumRolls, int maximumRolls, int sourceTotalWeight, List<Entry> entries) {
        public Pool { entries = List.copyOf(entries); }
        public Entry select(int draw) {
            if (draw < 0 || draw >= sourceTotalWeight) throw new IllegalArgumentException("Outside source loot denominator");
            for (Entry entry : entries) {
                draw -= entry.weight();
                if (draw < 0) return entry;
            }
            return null;
        }
    }
    private static final Map<String, Pool> POOLS = load();
    public ArmorStructureLootModifier(LootItemCondition[] conditions) { super(conditions); }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
    public static Map<String, Pool> pools() { return POOLS; }

    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        ItemStack artifact=io.github.ningmeng123.mite.advancement.ReferencedBooks.artifact(context);
        if(!artifact.isEmpty())loot.add(artifact);
        String table = context.getQueriedLootTableId().toString();
        Pool pool;
        if (table.equals("minecraft:chests/simple_dungeon"))
            pool = POOLS.get(context.getLevel().dimension().equals(MiteWorldDimensions.UNDERWORLD) ? "dungeon_underworld" : "dungeon_surface");
        else pool = POOLS.values().stream().filter(p -> p.modernTable().equals(table)).findFirst().orElse(null);
        if (pool == null) return loot;
        // Retained modern netherite equipment, leather horse armor and non-gear loot stay in their modern pools.
        loot.removeIf(ArmorStructureLootModifier::replacedGear);
        var random = context.getRandom();
        int rolls = pool.minimumRolls() + random.nextInt(pool.maximumRolls() - pool.minimumRolls() + 1);
        var unique = io.github.ningmeng123.mite.utility.UniqueMusicSavedData.get(context.getLevel());
        ItemStack reservedRecord = ItemStack.EMPTY;
        long sourceDay = (context.getLevel().getServer().overworld().getGameTime() + 6000L) / 24000L + 1L;
        for (int roll = 0; roll < rolls; roll++) {
            Entry entry = null;
            for (int attempt = 0; attempt < 4; attempt++) {
                entry = pool.select(random.nextInt(pool.sourceTotalWeight()));
                if (entry == null) break;
                var item = MiteArmorContent.get(entry.item());
                if (item instanceof io.github.ningmeng123.mite.item.R196RecordItem record) {
                    if (reservedRecord.isEmpty() && !unique.has(record.recordName())) reservedRecord = new ItemStack(item);
                    entry = null;
                    continue;
                }
                if (context.getLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                        && sourceDay < entry.minimumOverworldDay()) {
                    entry = null;
                    continue;
                }
                break;
            }
            if (entry == null) continue; // Full source non-equipment weight remains an empty gear outcome.
            ItemStack stack = new ItemStack(MiteArmorContent.get(entry.item()), entry.minimum() + random.nextInt(entry.maximum() - entry.minimum() + 1));
            if (stack.isDamageableItem()) {
                var definition = MiteArmorContent.definition(stack.getItem());
                boolean rusted = definition != null && definition.material().equals("rusted_iron");
                int fractionTenths = (rusted ? 3 : 0) + random.nextInt(7);
                stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getMaxDamage() * fractionTenths / 10));
            }
            loot.add(stack);
        }
        if (!reservedRecord.isEmpty() && unique.mark(((io.github.ningmeng123.mite.item.R196RecordItem)reservedRecord.getItem()).recordName()))
            loot.add(reservedRecord);
        // Modern balance bonus only fills underworld dungeon chests that did not already roll a coin.
        if (pool == POOLS.get("dungeon_underworld")) {
            var coin = MiteArmorContent.get("mite:ancient_metal_coin");
            if (loot.stream().noneMatch(stack -> stack.is(coin)) && random.nextFloat() < .03F)
                loot.add(new ItemStack(coin));
        }
        return loot;
    }
    private static boolean replacedGear(ItemStack stack) {
        if (io.github.ningmeng123.mite.item.RemovedDiamondEquipment.isRemoved(stack)) return true;
        if (stack.is(Items.NETHERITE_HELMET) || stack.is(Items.NETHERITE_CHESTPLATE) || stack.is(Items.NETHERITE_LEGGINGS) || stack.is(Items.NETHERITE_BOOTS)) return false;
        return EquipmentDurability.armorProfile(stack) != null || MiteArmorContent.definition(stack.getItem()) != null
                || stack.is(Items.IRON_HORSE_ARMOR) || stack.is(Items.GOLDEN_HORSE_ARMOR)
                || MiteArmorContent.chains().stream().anyMatch(c -> stack.is(MiteArmorContent.get(c.id())));
    }
    private static Map<String, Pool> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(ArmorStructureLootModifier.class.getResourceAsStream(
                "/mite/armor-loot-027.json")), StandardCharsets.UTF_8)) {
            Map<String, Pool> pools = new LinkedHashMap<>();
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("pools").entrySet().forEach(pair -> {
                JsonObject data = pair.getValue().getAsJsonObject();
                List<Entry> entries = new ArrayList<>();
                data.getAsJsonArray("entries").forEach(element -> {
                    var entry = element.getAsJsonObject();
                    entries.add(new Entry(entry.get("item").getAsString(), entry.get("minimum").getAsInt(),
                            entry.get("maximum").getAsInt(), entry.get("weight").getAsInt()));
                });
                pools.put(pair.getKey(), new Pool(data.get("modern_table").getAsString(), data.get("minimum_rolls").getAsInt(),
                        data.get("maximum_rolls").getAsInt(), data.get("source_total_weight").getAsInt(), entries));
            });
            try (var extraReader = new InputStreamReader(Objects.requireNonNull(ArmorStructureLootModifier.class.getResourceAsStream(
                    "/mite/chest-content-028.json")), StandardCharsets.UTF_8)) {
                JsonParser.parseReader(extraReader).getAsJsonObject().getAsJsonObject("pools").entrySet().forEach(pair -> {
                    Pool original = Objects.requireNonNull(pools.get(pair.getKey()));
                    List<Entry> entries = new ArrayList<>(original.entries());
                    pair.getValue().getAsJsonArray().forEach(value -> {
                        var entry = value.getAsJsonObject();
                        entries.add(new Entry(entry.get("item").getAsString(), entry.get("minimum").getAsInt(),
                                entry.get("maximum").getAsInt(), entry.get("weight").getAsInt(),
                                entry.has("minimum_overworld_day") ? entry.get("minimum_overworld_day").getAsInt() : 0));
                    });
                    pools.put(pair.getKey(), new Pool(original.modernTable(), original.minimumRolls(), original.maximumRolls(),
                            original.sourceTotalWeight(), entries));
                });
            }
            return java.util.Collections.unmodifiableMap(pools);
        } catch (Exception exception) { throw new IllegalStateException("Cannot load R196 armor loot source weights", exception); }
    }
}
