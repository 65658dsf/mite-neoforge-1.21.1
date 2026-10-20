package io.github.ningmeng123.mite.enchantment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/** Vanilla 1.21.1 acquisition profiles and selection; existing enchantment effects stay unchanged. @author NingMeng123 */
public final class VanillaTableEnchanting {
    public static final String RESOURCE = "/mite/vanilla_table_enchantments.json";
    public static final int MAXIMUM_SELECTION_LEVEL = 30;
    public static final int MAXIMUM_ENCHANTABILITY = 25;
    private static final Map<String, Profile> PROFILES = loadProfiles();

    public record Profile(int maximumLevel, int weight, Enchantment.Cost minCost, Enchantment.Cost maxCost) {
        public Profile {
            if (maximumLevel < 1 || weight < 0 || minCost == null || maxCost == null)
                throw new IllegalArgumentException("Invalid table acquisition profile");
        }
    }
    private record Candidate(R196EnchantmentCatalog.Definition source, Profile profile, EnchantmentInstance enchantment) {}

    private VanillaTableEnchanting() {}

    /** Official vanilla metadata is independent of source registry overrides and runtime configuration. */
    public static Map<String, Profile> profiles() { return PROFILES; }

    public static Profile profile(Holder<Enchantment> enchantment) {
        String id = enchantment.unwrapKey().map(key -> key.location().toString()).orElse("");
        Profile vanilla = PROFILES.get(id);
        if (vanilla != null) return vanilla;
        var definition = enchantment.value().definition();
        return new Profile(definition.maxLevel(), definition.weight(), definition.minCost(), definition.maxCost());
    }

    /** Raising a configured limit never grants an extra naturally obtainable table rank. */
    public static int maximumLevel(Holder<Enchantment> enchantment) {
        int natural = profile(enchantment).maximumLevel();
        return Math.min(natural, enchantment.unwrapKey()
                .map(key -> MiteConfig.enchantmentMaxLevel(key.location().toString(), natural)).orElse(natural));
    }

    /** The four physical source layers span the same progression as vanilla's fifteen effective shelves. */
    public static int mappedBookshelves(int accessibleBookshelves) {
        return Math.clamp(accessibleBookshelves, 0, FunctionalBlockRules.MAX_ENCHANTMENT_BOOKSHELVES)
                * 15 / FunctionalBlockRules.MAX_ENCHANTMENT_BOOKSHELVES;
    }

    /** Source material values above vanilla's strongest material affect payment, not result amplification. */
    public static int selectionEnchantability(ItemStack stack) {
        int enchantability = stack.getEnchantmentValue();
        var item = stack.getItem();
        if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
            // Retained native tools and rods have source overrides for payment; recover their original table bonus.
            if (item instanceof TieredItem tiered) enchantability = tiered.getTier().getEnchantmentValue();
            else if (item instanceof ArmorItem armor) enchantability = armor.getMaterial().value().enchantmentValue();
            else if (item == Items.FISHING_ROD) enchantability = 1;
            else enchantability = item.getEnchantmentValue(stack);
        }
        return Math.clamp(enchantability, 1, MAXIMUM_ENCHANTABILITY);
    }

    /** Separate seeded offer RNG leaves source energy/payment rolls untouched. Slot two reaches thirty at 48 shelves. */
    public static int offerStrength(ItemStack stack, int accessibleBookshelves, int slot, long enchantmentSeed) {
        if (slot < 0 || slot > 2) throw new IllegalArgumentException("Invalid enchanting slot");
        RandomSource random = RandomSource.create(enchantmentSeed);
        int strength = 0;
        for (int current = 0; current <= slot; current++)
            strength = EnchantmentHelper.getEnchantmentCost(random, current, mappedBookshelves(accessibleBookshelves), stack);
        return Math.clamp(strength, 0, MAXIMUM_SELECTION_LEVEL);
    }

    /** Official two integer bonuses and triangular fifteen-percent noise, applied once to the whole candidate pool. */
    public static int modifiedPower(ItemStack stack, int selectionLevel, RandomSource random) {
        if (selectionLevel < 1) return 0;
        int enchantability = selectionEnchantability(stack);
        int power = Math.min(selectionLevel, MAXIMUM_SELECTION_LEVEL) + 1
                + random.nextInt(enchantability / 4 + 1) + random.nextInt(enchantability / 4 + 1);
        float variation = (random.nextFloat() + random.nextFloat() - 1F) * .15F;
        return Math.max(1, Math.round(power + power * variation));
    }

    public static List<EnchantmentInstance> select(RegistryAccess access, ItemStack stack, int selectionLevel, RandomSource random) {
        return select(stack, selectionLevel, random, access.registryOrThrow(Registries.ENCHANTMENT).holders()
                .map(holder -> (Holder<Enchantment>)holder).toList());
    }

    /** Keep source applicability/compatibility while using vanilla levels, weights and additional-enchantment trials. */
    public static List<EnchantmentInstance> select(ItemStack stack, int selectionLevel, RandomSource random,
            List<Holder<Enchantment>> allowed) {
        if (stack.isEmpty() || selectionLevel < 1 || stack.getEnchantmentValue() < 1) return List.of();
        int power = modifiedPower(stack, selectionLevel, random);
        List<Candidate> candidates = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (Holder<Enchantment> holder : allowed) {
            String id = holder.unwrapKey().map(key -> key.location().toString()).orElse(null);
            if (id == null || !visited.add(id)) continue;
            var source = R196EnchantmentCatalog.definition(id);
            if (source == null || !stack.is(Items.BOOK) && !source.supported(stack)
                    || !ConfiguredEnchantments.availableForRandomSelection(holder, random)) continue;
            Profile profile = profile(holder);
            if (profile.weight() <= 0) continue;
            for (int level = maximumLevel(holder); level >= holder.value().getMinLevel(); level--) {
                if (power >= profile.minCost().calculate(level) && power <= profile.maxCost().calculate(level)) {
                    candidates.add(new Candidate(source, profile, new EnchantmentInstance(holder, level)));
                    break;
                }
            }
        }
        if (candidates.isEmpty()) return List.of();
        List<Candidate> selected = new ArrayList<>();
        selected.add(weighted(candidates, random));
        while (random.nextInt(50) <= power) {
            Candidate previous = selected.getLast();
            candidates.removeIf(candidate -> !R196EnchantmentCatalog.compatible(previous.source().id(), candidate.source().id()));
            if (candidates.isEmpty()) break;
            selected.add(weighted(candidates, random));
            power /= 2;
        }
        return selected.stream().map(Candidate::enchantment).toList();
    }

    private static Candidate weighted(List<Candidate> candidates, RandomSource random) {
        int weight = 0;
        for (Candidate candidate : candidates) weight = Math.addExact(weight, candidate.profile().weight());
        int roll = random.nextInt(weight);
        for (Candidate candidate : candidates) {
            roll -= candidate.profile().weight();
            if (roll < 0) return candidate;
        }
        throw new IllegalStateException("Missing table weighted candidate");
    }

    private static Map<String, Profile> loadProfiles() {
        try (var stream = VanillaTableEnchanting.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing vanilla table acquisition profiles: " + RESOURCE);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalStateException("Unsupported vanilla table profile schema");
            Map<String, Profile> profiles = new HashMap<>();
            for (var element : root.getAsJsonArray("enchantments")) {
                JsonObject entry = element.getAsJsonObject();
                String id = entry.get("id").getAsString();
                if (!id.startsWith("minecraft:") || profiles.put(id, new Profile(entry.get("max_level").getAsInt(),
                        entry.get("weight").getAsInt(), cost(entry.getAsJsonObject("min_cost")),
                        cost(entry.getAsJsonObject("max_cost")))) != null)
                    throw new IllegalStateException("Invalid or duplicate vanilla table profile " + id);
            }
            return Map.copyOf(profiles);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load vanilla table acquisition profiles", exception);
        }
    }

    private static Enchantment.Cost cost(JsonObject value) {
        return new Enchantment.Cost(value.get("base").getAsInt(), value.get("per_level_above_first").getAsInt());
    }
}
