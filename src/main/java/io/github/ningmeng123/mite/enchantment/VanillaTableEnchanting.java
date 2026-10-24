package io.github.ningmeng123.mite.enchantment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.item.WoodenClubBehavior;
import io.github.ningmeng123.mite.rules.WoodenClubRules;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/** Vanilla 1.21.1 acquisition profiles and selection; existing enchantment effects stay unchanged. @author NingMeng123 */
public final class VanillaTableEnchanting {
    public static final String RESOURCE = "/mite/vanilla_table_enchantments.json";
    public static final int MAXIMUM_SELECTION_LEVEL = 30;
    private static final Map<String, Profile> PROFILES = loadProfiles();

    public record Profile(int maximumLevel, int weight, Enchantment.Cost minCost, Enchantment.Cost maxCost,
            List<String> supportedItems, List<String> primaryItems, List<String> exclusiveSet) {
        public Profile {
            if (maximumLevel < 1 || weight < 0 || minCost == null || maxCost == null)
                throw new IllegalArgumentException("Invalid table acquisition profile");
            supportedItems = List.copyOf(supportedItems);
            primaryItems = List.copyOf(primaryItems);
            exclusiveSet = List.copyOf(exclusiveSet);
        }

        public Profile(int maximumLevel, int weight, Enchantment.Cost minCost, Enchantment.Cost maxCost) {
            this(maximumLevel, weight, minCost, maxCost, List.of(), List.of(), List.of());
        }
    }
    private record Candidate(Profile profile, EnchantmentInstance enchantment) {}

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

    /** The settings/registry cap controls ranks; native power and cost windows still control eligibility. */
    public static int maximumLevel(Holder<Enchantment> enchantment) {
        int registered = enchantment.value().getMaxLevel();
        return enchantment.unwrapKey()
                .map(key -> MiteConfig.enchantmentMaxLevel(key.location().toString(), registered)).orElse(registered);
    }

    /** Vanilla caps effective bookshelf power at fifteen. */
    public static int mappedBookshelves(int accessibleBookshelves) {
        return Math.clamp(accessibleBookshelves, 0, 15);
    }

    /** Native items retain native material bonuses; added materials use their actual enchantability. */
    public static int selectionEnchantability(ItemStack stack) {
        // This retained native ID intentionally represents the source wooden club, not a wooden sword.
        if (stack.is(Items.WOODEN_SWORD)) return WoodenClubRules.ENCHANTABILITY;
        int enchantability = stack.getEnchantmentValue();
        var item = stack.getItem();
        if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
            // Retained native tools and rods have source overrides for payment; recover their original table bonus.
            if (item instanceof TieredItem tiered) enchantability = tiered.getTier().getEnchantmentValue();
            else if (item instanceof ArmorItem armor) enchantability = armor.getMaterial().value().enchantmentValue();
            else if (item == Items.FISHING_ROD) enchantability = 1;
            else enchantability = item.getEnchantmentValue(stack);
        }
        return Math.max(0, enchantability);
    }

    /** Reproduce native offer RNG independently of the experience-point price. */
    public static int offerStrength(ItemStack stack, int accessibleBookshelves, int slot, long enchantmentSeed) {
        if (slot < 0 || slot > 2) throw new IllegalArgumentException("Invalid enchanting slot");
        RandomSource random = RandomSource.create(enchantmentSeed);
        int strength = 0;
        for (int current = 0; current <= slot; current++)
            strength = EnchantmentHelper.getEnchantmentCost(random, current, mappedBookshelves(accessibleBookshelves), stack);
        return strength < slot + 1 ? 0 : strength;
    }

    /** Official two integer bonuses and triangular fifteen-percent noise, applied once to the whole candidate pool. */
    public static int modifiedPower(ItemStack stack, int selectionLevel, RandomSource random) {
        if (selectionLevel < 1) return 0;
        int enchantability = selectionEnchantability(stack);
        if (enchantability < 1) return 0;
        int power = selectionLevel + 1
                + random.nextInt(enchantability / 4 + 1) + random.nextInt(enchantability / 4 + 1);
        float variation = (random.nextFloat() + random.nextFloat() - 1F) * .15F;
        return Math.max(1, Math.round(power + power * variation));
    }

    public static List<EnchantmentInstance> select(RegistryAccess access, ItemStack stack, int selectionLevel, RandomSource random) {
        return select(access, stack, selectionLevel, random, Integer.MAX_VALUE);
    }

    /** The quoted energy also bounds acquisition power; material bonuses cannot bypass payment. */
    public static List<EnchantmentInstance> select(RegistryAccess access, ItemStack stack, int selectionLevel,
            RandomSource random, int paidEnergy) {
        return access.registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(allowed -> select(stack, selectionLevel, random, allowed.stream().toList(), paidEnergy)).orElseGet(List::of);
    }

    /** Native candidate windows, primary item sets, weights and additional-enchantment trials. */
    public static List<EnchantmentInstance> select(ItemStack stack, int selectionLevel, RandomSource random,
            List<Holder<Enchantment>> allowed) {
        return select(stack, selectionLevel, random, allowed, Integer.MAX_VALUE);
    }

    public static List<EnchantmentInstance> select(ItemStack stack, int selectionLevel, RandomSource random,
            List<Holder<Enchantment>> allowed, int paidEnergy) {
        if (stack.isEmpty() || selectionLevel < 1 || paidEnergy < 1 || selectionEnchantability(stack) < 1) return List.of();
        int power = Math.min(modifiedPower(stack, selectionLevel, random), paidEnergy);
        List<Candidate> candidates = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (Holder<Enchantment> holder : allowed) {
            String id = holder.unwrapKey().map(key -> key.location().toString()).orElse(null);
            if (id == null || !visited.add(id)) continue;
            if (!primaryItem(stack, holder) || !ConfiguredEnchantments.availableForRandomSelection(holder, random)) continue;
            Profile profile = profile(holder);
            if (profile.weight() <= 0) continue;
            // Above-vanilla ranks require a full-strength offer as well as enough paid energy.
            int maximum = selectionLevel >= MAXIMUM_SELECTION_LEVEL ? maximumLevel(holder)
                    : Math.min(maximumLevel(holder), profile.maximumLevel());
            for (int level = maximum; level >= holder.value().getMinLevel(); level--) {
                if (power >= profile.minCost().calculate(level) && power <= profile.maxCost().calculate(level)) {
                    candidates.add(new Candidate(profile, new EnchantmentInstance(holder, level)));
                    break;
                }
            }
        }
        if (candidates.isEmpty()) return List.of();
        List<Candidate> selected = new ArrayList<>();
        selected.add(weighted(candidates, random));
        while (random.nextInt(50) <= power) {
            Candidate previous = selected.getLast();
            candidates.removeIf(candidate -> !compatible(previous.enchantment().enchantment, candidate.enchantment().enchantment));
            if (candidates.isEmpty()) break;
            selected.add(weighted(candidates, random));
            power /= 2;
        }
        return selected.stream().map(Candidate::enchantment).toList();
    }

    /** Source registry overrides must not remove a native item's ordinary table enchantments. */
    public static boolean primaryItem(ItemStack stack, Holder<Enchantment> enchantment) {
        if (stack.is(Items.BOOK)) return true;
        if (stack.is(Items.WOODEN_SWORD)) return WoodenClubBehavior.allowsEnchantment(enchantment);
        String id = enchantment.unwrapKey().map(key -> key.location().toString()).orElse("");
        Profile vanilla = PROFILES.get(id);
        if (vanilla == null) {
            var source = R196EnchantmentCatalog.definition(id);
            return stack.isPrimaryItemFor(enchantment) || source != null && source.supported(stack);
        }
        if (matchesItemSet(stack, vanilla.supportedItems()) && matchesItemSet(stack, vanilla.primaryItems())) return true;
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")) return false;
        ItemStack equivalent = nativeEquipmentCategory(stack);
        return !equivalent.isEmpty() && matchesItemSet(equivalent, vanilla.supportedItems())
                && matchesItemSet(equivalent, vanilla.primaryItems());
    }

    /** Added materials inherit their native equipment category, including native primary-item restrictions. */
    private static ItemStack nativeEquipmentCategory(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof ArmorItem armor) return switch (armor.getType()) {
            case HELMET -> new ItemStack(Items.IRON_HELMET);
            case CHESTPLATE -> new ItemStack(Items.IRON_CHESTPLATE);
            case LEGGINGS -> new ItemStack(Items.IRON_LEGGINGS);
            case BOOTS -> new ItemStack(Items.IRON_BOOTS);
            default -> ItemStack.EMPTY;
        };
        if (item instanceof BowItem) return new ItemStack(Items.BOW);
        if (item instanceof FishingRodItem) return new ItemStack(Items.FISHING_ROD);
        if (item instanceof ShieldItem) return new ItemStack(Items.SHIELD);
        var family = R196EnchantmentCatalog.family(stack);
        if (family == null) return ItemStack.EMPTY;
        return new ItemStack(switch (family) {
            case SWORD, DAGGER, KNIFE, CLUB, CUDGEL, WAR_HAMMER -> Items.IRON_SWORD;
            case AXE, HATCHET, BATTLE_AXE -> Items.IRON_AXE;
            case PICKAXE, MATTOCK -> Items.IRON_PICKAXE;
            case SHOVEL -> Items.IRON_SHOVEL;
            case HOE, SCYTHE -> Items.IRON_HOE;
            case SHEARS -> Items.SHEARS;
        });
    }

    private static boolean matchesItemSet(ItemStack stack, List<String> entries) {
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                if (stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.substring(1))))) return true;
            } else if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ResourceLocation.parse(entry))) return true;
        }
        return false;
    }

    /** Check both native exclusive sets; custom enchantments retain their data-driven exclusions. */
    public static boolean compatible(Holder<Enchantment> first, Holder<Enchantment> second) {
        return !first.equals(second) && !excludes(first, second) && !excludes(second, first);
    }

    private static boolean excludes(Holder<Enchantment> owner, Holder<Enchantment> other) {
        Profile vanilla = owner.unwrapKey().map(key -> PROFILES.get(key.location().toString())).orElse(null);
        if (vanilla == null) return owner.value().exclusiveSet().contains(other);
        for (String entry : vanilla.exclusiveSet()) {
            if (entry.startsWith("#")) {
                if (other.is(TagKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(entry.substring(1))))) return true;
            } else if (other.unwrapKey().map(key -> key.location().equals(ResourceLocation.parse(entry))).orElse(false)) return true;
        }
        return false;
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
            if (root.get("schema").getAsInt() != 2) throw new IllegalStateException("Unsupported vanilla table profile schema");
            Map<String, Profile> profiles = new HashMap<>();
            for (var element : root.getAsJsonArray("enchantments")) {
                JsonObject entry = element.getAsJsonObject();
                String id = entry.get("id").getAsString();
                if (!id.startsWith("minecraft:") || profiles.put(id, new Profile(entry.get("max_level").getAsInt(),
                        entry.get("weight").getAsInt(), cost(entry.getAsJsonObject("min_cost")),
                        cost(entry.getAsJsonObject("max_cost")), strings(entry.getAsJsonArray("supported_items")),
                        strings(entry.getAsJsonArray("primary_items")), strings(entry.getAsJsonArray("exclusive_set")))) != null)
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

    private static List<String> strings(JsonArray values) {
        List<String> entries = new ArrayList<>();
        for (var value : values) entries.add(value.getAsString());
        return List.copyOf(entries);
    }
}
