package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import io.github.ningmeng123.mite.enchantment.R196BookAnvil;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source and native selection respect admission chances without changing deterministic transfers or effects. @author NingMeng123 */
@GameTestHolder("mite_enchanting_chance")
@PrefixGameTestTemplate(false)
public final class EnchantmentChanceSelectionGameTests {
    private EnchantmentChanceSelectionGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void zeroChanceRemovesTheOnlyEligibleSourceAndTableCandidate(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var sharpness = registry.getHolderOrThrow(Enchantments.SHARPNESS);
            var others = registry.holders().map(entry -> entry.key().location().toString())
                    .filter(id -> !id.equals("minecraft:sharpness")).collect(Collectors.toSet());
            settings.set(Map.of(), others);
            for (int seed = 0; seed < 32; seed++) {
                helper.assertFalse(select(sharpness, new ItemStack(Items.IRON_SWORD), 30, RandomSource.create(seed)).isEmpty(),
                        "The single candidate is level-eligible before probability filtering: " + seed);
                helper.assertFalse(R196EnchantmentTable.select(helper.getLevel().registryAccess(), new ItemStack(Items.IRON_SWORD),
                        30, RandomSource.create(seed)).isEmpty(), "The registered table pool contains eligible Sharpness: " + seed);
            }
            settings.set(Map.of("minecraft:sharpness", 0D), others);
            for (int seed = 0; seed < 32; seed++) {
                helper.assertTrue(select(sharpness, new ItemStack(Items.IRON_SWORD), 30, RandomSource.create(seed)).isEmpty(),
                        "A zero-percent candidate cannot enter source weighted selection: " + seed);
                helper.assertTrue(R196EnchantmentTable.select(helper.getLevel().registryAccess(), new ItemStack(Items.IRON_SWORD),
                        30, RandomSource.create(seed)).isEmpty(), "The actual table selector uses the same probability filter: " + seed);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void fractionalChancesSampleCandidateAdmissionOncePerSelection(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var candidates = List.of(registry.getHolderOrThrow(Enchantments.SHARPNESS), registry.getHolderOrThrow(Enchantments.QUICK_CHARGE));
            int samples = 4096;
            for (int route = 0; route < candidates.size(); route++) {
                var holder = candidates.get(route);
                var item = route == 0 ? Items.IRON_SWORD : Items.CROSSBOW;
                String id = holder.unwrapKey().orElseThrow().location().toString();
                for (double percent : new double[]{50D, 37.5D}) {
                    settings.set(Map.of(id, percent));
                    var random = RandomSource.create(196L);
                    int admitted = 0;
                    for (int trial = 0; trial < samples; trial++) {
                        var selected = select(holder, new ItemStack(item), 30, random);
                        helper.assertTrue(selected.size() <= 1, "A single-candidate pool can select at most one enchantment");
                        if (!selected.isEmpty()) admitted++;
                    }
                    double fraction = admitted / (double)samples;
                    helper.assertTrue(Math.abs(fraction - percent / 100D) < .06D,
                            "Each source/native selection admits a candidate only once at " + percent + "%; " + id + " selected=" + admitted + "/" + samples);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void defaultAndExplicitFullChancePreserveEachRouteAndRandomState(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var pool = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getTag(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow().stream().toList();
            settings.set(Map.of());
            var untouched = RandomSource.create(196L);
            var filtered = RandomSource.create(196L);
            for (var holder : pool)
                helper.assertTrue(ConfiguredEnchantments.availableForRandomSelection(holder, filtered),
                        "Default probability admits each enabled candidate");
            helper.assertValueEqual(filtered.nextLong(), untouched.nextLong(), "Default candidate admission consumes no random values");

            var expected = new ArrayList<RouteDraws>();
            for (int seed = 0; seed < 128; seed++) expected.add(drawRoutes(helper, pool, seed));
            var explicit = pool.stream().collect(Collectors.toMap(holder -> holder.unwrapKey().orElseThrow().location().toString(), holder -> 100D));
            settings.set(explicit);
            for (int seed = 0; seed < expected.size(); seed++) {
                helper.assertValueEqual(drawRoutes(helper, pool, seed), expected.get(seed),
                        "Explicit 100% preserves table/source/native output ordering, levels and subsequent RNG state: " + seed);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void disabledCandidatesTakePriorityWithoutConsumingAnAdmissionRoll(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var sharpness = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
            for (double percent : new double[]{100D, 50D, 0D}) {
                settings.set(Map.of("minecraft:sharpness", percent), Set.of("minecraft:sharpness"));
                var random = RandomSource.create(196L);
                var untouched = RandomSource.create(196L);
                helper.assertFalse(ConfiguredEnchantments.availableForRandomSelection(sharpness, random),
                        "Disabled entries stay excluded even when their probability is " + percent + "%");
                helper.assertValueEqual(random.nextLong(), untouched.nextLong(), "Disabled entries do not consume an additional probability roll");
                helper.assertTrue(select(sharpness, new ItemStack(Items.IRON_SWORD), 30, RandomSource.create(196L)).isEmpty(),
                        "The actual routed selector also respects disablement before admission");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void zeroProbabilityPreservesExistingDirectAndAnvilEnchantmentsAndDamage(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var sharpness = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
            settings.set(Map.of());
            var existing = new ItemStack(Items.IRON_SWORD);
            existing.enchant(sharpness, 3);
            var cow = EntityType.COW.create(helper.getLevel());
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var source = helper.getLevel().damageSources().playerAttack(player);
            float before = EnchantmentHelper.modifyDamage(helper.getLevel(), existing, cow, source, 5F);
            helper.assertTrue(before > 5F, "The initial enchantment has a real numerical damage effect");

            settings.set(Map.of("minecraft:sharpness", 0D));
            helper.assertTrue(ConfiguredEnchantments.enabled(sharpness), "Zero random participation leaves enchantment effects enabled");
            helper.assertValueEqual(existing.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharpness), 3,
                    "The saved enchantment component is preserved");
            helper.assertValueEqual(existing.getEnchantmentLevel(sharpness), 3, "The existing enchantment still contributes its level");
            helper.assertValueEqual(EnchantmentHelper.modifyDamage(helper.getLevel(), existing, cow, source, 5F), before,
                    "An existing enchantment keeps its real numerical effect at zero participation");

            var direct = new ItemStack(Items.IRON_SWORD);
            direct.enchant(sharpness, 3);
            helper.assertValueEqual(direct.getEnchantmentLevel(sharpness), 3, "Direct and command-style application remains available");
            var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            stored.set(sharpness, 3);
            var book = new ItemStack(Items.ENCHANTED_BOOK);
            book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
            var merge = R196BookAnvil.plan(new ItemStack(Items.IRON_SWORD), book);
            helper.assertTrue(merge != null && !merge.output().isEmpty(), "A zero-probability enchantment can still transfer from an existing book");
            helper.assertValueEqual(merge.output().getEnchantmentLevel(sharpness), 3, "The anvil transfers the original level");
            helper.assertValueEqual(EnchantmentHelper.modifyDamage(helper.getLevel(), merge.output(), cow, source, 5F), before,
                    "Transferred enchantments retain the same numerical effect");
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void modernCrossbowAndModEnchantmentCandidatesUseTheSameProbabilitySetting(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var quickCharge = registry.getHolderOrThrow(Enchantments.QUICK_CHARGE);
            var stun = registry.getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("mite:stun")));
            var candidates = List.of(quickCharge, stun);
            for (int candidate = 0; candidate < candidates.size(); candidate++) {
                var holder = candidates.get(candidate);
                var item = candidate == 0 ? Items.CROSSBOW : Items.BOOK;
                String id = holder.unwrapKey().orElseThrow().location().toString();
                settings.set(Map.of(id, 100D));
                for (int seed = 0; seed < 32; seed++)
                    helper.assertFalse(select(holder, new ItemStack(item), 20, RandomSource.create(seed)).isEmpty(),
                            "The modern/modded candidate is normally eligible: " + id + "/" + seed);
                settings.set(Map.of(id, 0D));
                for (int seed = 0; seed < 32; seed++)
                    helper.assertTrue(select(holder, new ItemStack(item), 20, RandomSource.create(seed)).isEmpty(),
                            "Registry-wide probability settings also filter modern/modded candidates: " + id + "/" + seed);
            }
        }
        helper.succeed();
    }

    private static List<EnchantmentInstance> select(Holder<Enchantment> holder, ItemStack item, int level, RandomSource random) {
        return EnchantmentHelper.selectEnchantment(random, item, level, Stream.of(holder));
    }

    private static Draw drawTable(GameTestHelper helper, long seed) {
        var random = RandomSource.create(seed);
        var result = R196EnchantmentTable.select(helper.getLevel().registryAccess(), new ItemStack(Items.BOOK), 30, random);
        return new Draw(result.stream().map(entry -> entry.enchantment.unwrapKey().orElseThrow().location() + "=" + entry.level).toList(), random.nextLong());
    }

    private static Draw drawHelper(List<Holder<Enchantment>> pool, ItemStack item, long seed) {
        var random = RandomSource.create(seed);
        var result = EnchantmentHelper.selectEnchantment(random, item, 30, pool.stream());
        return new Draw(result.stream().map(entry -> entry.enchantment.unwrapKey().orElseThrow().location() + "=" + entry.level).toList(), random.nextLong());
    }

    private static RouteDraws drawRoutes(GameTestHelper helper, List<Holder<Enchantment>> pool, long seed) {
        return new RouteDraws(drawTable(helper, seed), drawHelper(pool, new ItemStack(Items.IRON_SWORD), seed),
                drawHelper(pool, new ItemStack(Items.CROSSBOW), seed));
    }

    private record Draw(List<String> enchantments, long nextRandomValue) {}
    private record RouteDraws(Draw table, Draw sourceHelper, Draw nativeHelper) {}

    /** Authoritative snapshots isolate the test rules from asynchronous file reloads and never edit local settings. */
    private static final class Settings implements AutoCloseable {
        private final MiteConfig.Snapshot base = MiteConfig.localSnapshot();
        private final MiteConfig.Snapshot original = MiteConfig.effectiveSnapshot() == base ? null : MiteConfig.effectiveSnapshot();

        private Settings() { set(Map.of()); }

        private void set(Map<String, Double> chances) { set(chances, Set.of()); }

        private void set(Map<String, Double> chances, Set<String> disabled) {
            MiteConfig.receiveServerSnapshot(new MiteConfig.Snapshot(base.ores(), base.growth(), base.healthGrowth(), base.damageGrowth(),
                    base.reachMultiplier(), base.reachReduction(), base.heightPenalty(), base.underworldTrials(), base.overworldTrials(),
                    Map.of(), disabled, base.monsterSettings(), base.oreCandidates(), base.oreChances(), base.overworldOreCandidates(),
                    base.overworldOreChances(), base.overworldOres(), chances));
        }

        @Override public void close() {
            MiteConfig.clearServerSnapshot();
            if (original != null) MiteConfig.receiveServerSnapshot(original);
        }
    }
}
