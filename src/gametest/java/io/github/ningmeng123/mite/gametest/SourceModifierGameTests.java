package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.mite.progression.GearModifier;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source modifier acquisition and historical enchantment data remain independent. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class SourceModifierGameTests {
    private record Expected(String path, int weight, int maximumLevel, float addition) {}
    private static final List<Expected> CATALOG = List.of(
            new Expected("tool/efficiency", 20, 10, .25F),
            new Expected("tool/aquadynamic", 10, 5, 1.25F),
            new Expected("tool/durability", 40, 5, .1F),
            new Expected("tool/damage", 20, 10, 1F),
            new Expected("tool/slowdown", 10, 5, 1F),
            new Expected("tool/unnatural", 6, 5, .1F),
            new Expected("tool/demon", 4, 1, .25F),
            new Expected("tool/geology", 2, 1, .5F),
            new Expected("armor/durability", 20, 5, .1F),
            new Expected("armor/projectile_protection", 7, 5, .5F),
            new Expected("armor/explosion_protection", 7, 5, .5F),
            new Expected("armor/fire_protection", 7, 5, .5F),
            new Expected("armor/protection", 5, 5, .25F),
            new Expected("armor/steady", 8, 5, .15F),
            new Expected("armor/blessed", 8, 5, 1F),
            new Expected("armor/indomitable", 5, 4, .25F),
            new Expected("armor/immunity", 3, 4, .15F));
    private static final List<String> TOOL = List.of("tool/efficiency", "tool/aquadynamic", "tool/durability", "tool/unnatural");
    private static final List<String> WEAPON = List.of("tool/durability", "tool/damage", "tool/slowdown", "tool/demon");
    private static final List<String> ARMOR = List.of("armor/durability", "armor/projectile_protection", "armor/explosion_protection",
            "armor/fire_protection", "armor/protection", "armor/steady", "armor/blessed");
    private static final List<String> PROTECTIONS = List.of("armor/projectile_protection", "armor/explosion_protection", "armor/fire_protection", "armor/protection");

    private SourceModifierGameTests() {}

    @GameTest(template = "empty")
    public static void exactSourceCatalogHasEightToolAndNineArmorDefinitions(GameTestHelper h) {
        h.assertTrue(GearModifier.values().length == 17, "The acquisition catalog contains exactly the source's seventeen modifiers");
        for (Expected expected : CATALOG) {
            GearModifier modifier = GearModifier.fromId(id(expected.path()));
            h.assertTrue(modifier != null && modifier.weight() == expected.weight()
                    && modifier.maximumLevel() == expected.maximumLevel(), "Exact source definition for " + expected.path());
            ItemStack stack = new ItemStack(expected.path().startsWith("armor/") ? Items.IRON_CHESTPLATE : Items.IRON_PICKAXE);
            stack.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 1, 0, Map.of(id(expected.path()), 1), 1));
            h.assertTrue(modifier.level(stack) == 1 && Math.abs(modifier.value(stack) - expected.addition()) < .000001F,
                    "The per-rank source parameter is retained for " + expected.path());
        }
        h.assertTrue(GearModifier.fromId(Enchantments.EFFICIENCY.location()) == null,
                "A historical enchantment ID is never reinterpreted as a custom modifier");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceItemFamiliesAndArmorSlotsHaveTheirExactCandidatePools(GameTestHelper h) {
        pool(h, new ItemStack(Items.IRON_SWORD), WEAPON, 74);
        pool(h, new ItemStack(Items.IRON_PICKAXE), append(TOOL, "tool/geology"), 78);
        pool(h, new ItemStack(Items.IRON_AXE), TOOL, 76);
        pool(h, new ItemStack(Items.IRON_SHOVEL), TOOL, 76);
        pool(h, new ItemStack(Items.IRON_CHESTPLATE), append(ARMOR, "armor/indomitable"), 67);
        pool(h, new ItemStack(Items.IRON_HELMET), append(ARMOR, "armor/immunity"), 65);
        pool(h, new ItemStack(Items.IRON_LEGGINGS), ARMOR, 62);
        pool(h, new ItemStack(Items.IRON_BOOTS), ARMOR, 62);
        pool(h, new ItemStack(MiteTools.get("mite:flint_knife")), WEAPON, 74);
        pool(h, new ItemStack(MiteTools.get("mite:copper_dagger")), WEAPON, 74);
        pool(h, new ItemStack(MiteTools.get("mite:wooden_cudgel")), TOOL, 76);
        for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.SHIELD))
            pool(h, new ItemStack(item), List.of("tool/durability"), 40);
        for (Item item : List.of(Items.TRIDENT, Items.MACE)) pool(h, new ItemStack(item), WEAPON, 74);
        for (Item item : List.of(Items.FISHING_ROD, Items.ELYTRA, Items.IRON_HORSE_ARMOR, Items.DIAMOND_SWORD))
            pool(h, new ItemStack(item), List.of(), 0);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyWeightedIntegerIntervalProducesItsExactCustomRewardWithoutAChanceDraw(GameTestHelper h) {
        try (Session session = new Session(h)) {
            weightedPool(h, session, Items.IRON_SWORD, WEAPON, 74);
            weightedPool(h, session, Items.IRON_PICKAXE, append(TOOL, "tool/geology"), 78);
            weightedPool(h, session, Items.IRON_CHESTPLATE, append(ARMOR, "armor/indomitable"), 67);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void customProtectionExcludesOnlyOtherCustomProtectionKindsOnTheSameItem(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
        chestplate.enchant(registry.getHolderOrThrow(Enchantments.FIRE_PROTECTION), 4);
        h.assertTrue(GearProgression.modifierCandidates(chestplate).stream().filter(modifier -> PROTECTIONS.contains(path(modifier))).count() == 4,
                "An ordinary protection enchantment does not choose the custom protection kind");
        for (String selected : PROTECTIONS) {
            chestplate.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 1, 0,
                    Map.of(id(selected), 1, Enchantments.PROTECTION.location(), 5), 1));
            List<String> protections = GearProgression.modifierCandidates(chestplate).stream().map(SourceModifierGameTests::path)
                    .filter(PROTECTIONS::contains).toList();
            h.assertTrue(protections.equals(List.of(selected)), "Only the existing " + selected + " kind remains eligible");
            chestplate.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 5, 0, Map.of(id(selected), 5), 1));
            h.assertTrue(GearProgression.modifierCandidates(chestplate).stream().noneMatch(modifier -> PROTECTIONS.contains(path(modifier))),
                    "A saturated protection does not unlock an incompatible kind");
        }
        h.assertTrue(GearModifier.candidates(chestplate, Map.of(id("armor/fire_protection"), 0)).stream()
                        .filter(modifier -> PROTECTIONS.contains(path(modifier))).map(SourceModifierGameTests::path).toList()
                        .equals(List.of("armor/fire_protection")),
                "The source's key-existence exclusion also holds for a zero-rank candidate-map entry");
        pool(h, new ItemStack(Items.IRON_CHESTPLATE), append(ARMOR, "armor/indomitable"), 67);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oneMultiLevelAwardRebuildsProtectionExclusionsAndPreservesItsRemainder(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ScriptedRandom random = session.random(0, 6, 0);
            ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
            Map<ResourceLocation, Integer> ranks = new HashMap<>();
            ranks.put(id("armor/durability"), 5);
            ranks.put(id("armor/steady"), 5);
            ranks.put(id("armor/blessed"), 5);
            ranks.put(id("armor/indomitable"), 4);
            chestplate.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 0, 0, ranks, 1));
            GearProgression.award(chestplate, 360_123, session.player);
            GearProgress state = GearProgression.state(chestplate);
            h.assertTrue(state.level() == 3 && state.experienceMillis() == 123 && state.affixes().get(id("armor/projectile_protection")) == 3,
                    "One award reaches three levels, grants all three modifier ranks and keeps its fractional remainder");
            for (String other : List.of("armor/explosion_protection", "armor/fire_protection", "armor/protection"))
                h.assertFalse(state.affixes().containsKey(id(other)), "Later levels in the same award cannot select incompatible " + other);
            h.assertTrue(random.finished() && random.bounds.equals(List.of(26, 7, 7)),
                    "The first choice removes all three incompatible kinds before the next selection");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void newAndHistoricalIdsRoundTripTogetherWithoutChangingAnyHistoricalEffect(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var efficiency = registry.getHolderOrThrow(Enchantments.EFFICIENCY);
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.enchant(efficiency, 3);
        Map<ResourceLocation, Integer> ranks = Map.of(Enchantments.EFFICIENCY.location(), 17,
                Enchantments.MENDING.location(), 2, ResourceLocation.parse("missing:temporarily_removed"), 231,
                id("tool/efficiency"), 3, id("tool/geology"), 1);
        GearProgress progress = new GearProgress(UUID.randomUUID(), 9, 123_456, ranks, 1);
        stack.set(GearProgression.PROGRESS.get(), progress);
        ItemEnchantments ordinary = stack.get(DataComponents.ENCHANTMENTS);
        h.assertTrue(EnchantmentSources.levels(stack, efficiency).normal() == 3 && EnchantmentSources.levels(stack, efficiency).affix() == 17,
                "Custom efficiency does not add ranks to the historical enchantment source");
        h.assertTrue(GearModifier.fromId(id("tool/efficiency")).level(stack) == 3
                        && Math.abs(GearModifier.fromId(id("tool/efficiency")).value(stack) - .75F) < .000001F,
                "Custom efficiency reads only its own three ranks");
        ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
        exactSources(h, restored, progress, ordinary);
        RegistryFriendlyByteBuf bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(bytes, stack);
            exactSources(h, ItemStack.STREAM_CODEC.decode(bytes), progress, ordinary);
        } finally { bytes.release(); }
        ItemStack repaired = new ItemStack(Items.IRON_PICKAXE);
        GearProgression.preserve(stack, repaired);
        h.assertTrue(GearProgression.state(repaired).equals(progress), "Maintenance copies all old, new and unknown ranks as one unchanged component");
        stack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemStack sacrificial = new ItemStack(Items.IRON_PICKAXE);
        sacrificial.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 25, 0,
                Map.of(id("tool/durability"), 5, Enchantments.UNBREAKING.location(), 17,
                        ResourceLocation.parse("missing:sacrificial_history"), 231), 1));
        stack.setDamageValue(5000); sacrificial.setDamageValue(5000);
        var recipe = new RepairItemRecipe(CraftingBookCategory.EQUIPMENT);
        var input = CraftingInput.of(2, 1, List.of(stack, sacrificial));
        h.assertTrue(!stack.isEnchanted() && !sacrificial.isEnchanted() && recipe.matches(input, h.getLevel()),
                "Both damaged repair inputs satisfy the existing ordinary-enchantment and quality restrictions");
        ItemStack output = recipe.assemble(input, h.getLevel().registryAccess());
        h.assertTrue(!output.isEmpty() && GearProgression.state(output).equals(progress),
                "Actual crafted repair retains the first item's combined modifier history without merging the sacrifice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void historicalEnchantmentRanksNeverSaturateOrLockTheNewCustomPool(GameTestHelper h) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 5, 0,
                Map.of(Enchantments.EFFICIENCY.location(), 255, Enchantments.UNBREAKING.location(), 255,
                        ResourceLocation.parse("missing:temporarily_removed"), 231), 1));
        pool(h, pickaxe, append(TOOL, "tool/geology"), 78);
        Map<ResourceLocation, Integer> ranks = new HashMap<>(GearProgression.state(pickaxe).affixes());
        ranks.put(id("tool/efficiency"), 17);
        h.assertFalse(GearModifier.candidates(pickaxe, ranks).stream().anyMatch(modifier -> modifier.id().equals(id("tool/efficiency"))),
                "Only the corresponding custom key determines whether its new acquisition cap has been reached");
        h.assertTrue(ranks.get(id("tool/efficiency")) == 17, "Candidate filtering never clamps saved high ranks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allRecognizedStandaloneEquipmentUsesTheTwentyFiveLevelCap(GameTestHelper h) {
        for (Item item : List.of(Items.WOODEN_SWORD, Items.STONE_PICKAXE, Items.GOLDEN_SWORD, Items.IRON_PICKAXE,
                Items.LEATHER_CHESTPLATE, Items.IRON_CHESTPLATE, Items.NETHERITE_SWORD, Items.BOW, Items.CROSSBOW,
                Items.TRIDENT, Items.MACE, Items.SHIELD, MiteTools.get("mite:flint_knife"), MiteTools.get("mite:copper_sword"),
                MiteTools.get("mite:mithril_pickaxe"), MiteTools.get("mite:vibranium_pickaxe")))
            h.assertTrue(GearProgression.maxLevel(new ItemStack(item)) == 25, "Standalone cap is twenty-five for " + item);
        h.succeed();
    }

    private static void exactSources(GameTestHelper h, ItemStack stack, GearProgress progress, ItemEnchantments ordinary) {
        h.assertTrue(GearProgression.state(stack).equals(progress) && ordinary.equals(stack.get(DataComponents.ENCHANTMENTS)),
                "Transport preserves UUID, level, exact XP, legacy ranks, new ranks, unknown IDs and ordinary enchantments");
        var efficiency = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.EFFICIENCY);
        h.assertTrue(EnchantmentSources.levels(stack, efficiency).affix() == 17,
                "Transport preserves the historical enchantment's full source level");
    }

    private static void weightedPool(GameTestHelper h, Session session, Item item, List<String> expected, int total) {
        int start = 0;
        for (String path : expected) {
            int weight = CATALOG.stream().filter(entry -> entry.path().equals(path)).findFirst().orElseThrow().weight();
            for (int offset = 0; offset < weight; offset++) {
                ScriptedRandom random = session.random(start + offset);
                ItemStack stack = new ItemStack(item);
                long threshold = GearProgression.nextLevelExperienceMillis(stack, 0);
                GearProgression.award(stack, threshold, session.player);
                h.assertTrue(GearProgression.state(stack).level() == 1
                                && GearProgression.state(stack).affixes().equals(Map.of(id(path), 1)),
                        "Weighted roll " + (start + offset) + " selects the source interval for " + path);
                h.assertTrue(random.finished() && random.bounds.equals(List.of(total)),
                        "A first-level reward consumes one draw with the exact current denominator");
            }
            start += weight;
        }
        h.assertTrue(start == total, "Every legal integer in the expected pool was exercised");
    }

    private static void pool(GameTestHelper h, ItemStack stack, List<String> expected, int total) {
        List<GearModifier> candidates = GearProgression.modifierCandidates(stack);
        h.assertTrue(candidates.stream().map(SourceModifierGameTests::path).toList().equals(expected),
                "Exact ordered candidate pool for " + stack + ": " + candidates);
        h.assertTrue(candidates.stream().mapToInt(GearModifier::weight).sum() == total, "Exact weight denominator for " + stack);
        h.assertTrue(GearProgression.candidates(stack, h.getLevel()).equals(candidates), "The public candidate API exposes custom modifiers");
    }
    private static List<String> append(List<String> values, String value) {
        List<String> result = new ArrayList<>(values); result.add(value); return result;
    }
    private static String path(GearModifier modifier) { return modifier.id().getPath().substring("modifier/".length()); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("mite", "modifier/" + path); }

    private static final class Session implements AutoCloseable {
        final ControlledPlayer player;
        final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "source-modifier"), false);
            player = new ControlledPlayer(h, cookie);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            PlayerExperience.setTotal(player, 0);
        }
        ScriptedRandom random(int... rolls) { ScriptedRandom source = new ScriptedRandom(rolls); player.controlled = source; return source; }
        @Override public void close() {
            player.controlled = null; player.getInventory().clearContent(); player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
        }
    }
    private static final class ControlledPlayer extends ServerPlayer {
        private RandomSource controlled;
        private ControlledPlayer(GameTestHelper h, CommonListenerCookie cookie) {
            super(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        }
        @Override public RandomSource getRandom() { return controlled == null ? super.getRandom() : controlled; }
    }
    private static final class ScriptedRandom extends LegacyRandomSource {
        private final ArrayDeque<Integer> rolls = new ArrayDeque<>();
        private final List<Integer> bounds = new ArrayList<>();
        private ScriptedRandom(int... values) { super(0L); for (int value : values) rolls.addLast(value); }
        @Override public int nextInt(int bound) {
            bounds.add(bound);
            if (rolls.isEmpty()) throw new AssertionError("Unexpected modifier draw with bound " + bound);
            int value = rolls.removeFirst();
            if (value < 0 || value >= bound) throw new AssertionError("Modifier roll " + value + " outside bound " + bound);
            return value;
        }
        private boolean finished() { return rolls.isEmpty(); }
    }
}
