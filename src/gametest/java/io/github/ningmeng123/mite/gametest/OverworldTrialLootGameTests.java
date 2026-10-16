package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.loot.OverworldTrialLootBalance;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exact acceptance boundaries and final native loot pipelines for overworld trial rewards. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class OverworldTrialLootGameTests {
    private static final ResourceLocation TRIAL = vanilla("chests/trial_chambers/reward");
    private static final List<Item> TARGETS = List.of(Items.EMERALD_BLOCK, Items.EMERALD, Items.DIAMOND);

    private OverworldTrialLootGameTests() {}

    @GameTest(template = "empty")
    public static void eachTargetKeepsExactlyOneOfSixIndependentAcceptanceTickets(GameTestHelper helper) {
        for (Item item : TARGETS) {
            int kept = 0;
            for (int ticket = 0; ticket < 6; ticket++) {
                ItemStack stack = new ItemStack(item, 3);
                var loot = list(stack);
                SixTickets random = new SixTickets(ticket);
                OverworldTrialLootBalance.reduce(loot, helper.getLevel(), TRIAL, random);
                helper.assertValueEqual(random.draws, 1, "One actual target type uses exactly one sixth-chance draw");
                if (!loot.isEmpty()) {
                    kept++;
                    helper.assertTrue(loot.getFirst() == stack && stack.getCount() == 3,
                            "Winning ticket retains the complete original stack");
                }
            }
            helper.assertValueEqual(kept, 1, "Exactly one of six equally likely tickets keeps each target resource");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void threeResourceTypesRollIndependentlyAndNeverReplaceMissingRewards(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 2);
        var loot = list(new ItemStack(Items.EMERALD_BLOCK), new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.DIAMOND, 2), iron);
        SixTickets random = new SixTickets(1, 0, 5);
        OverworldTrialLootBalance.reduce(loot, helper.getLevel(), TRIAL, random);
        helper.assertValueEqual(random.draws, 3, "The three resource types have independent acceptance decisions");
        helper.assertValueEqual(loot.size(), 2, "Failed resource decisions leave empty rewards rather than replacements");
        helper.assertTrue(loot.getFirst().is(Items.EMERALD) && loot.getFirst().getCount() == 5,
                "Only the resource with a winning ticket survives");
        helper.assertTrue(loot.getLast() == iron, "Unrelated reward identity and count are untouched");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyStackOfOneResourceSharesOneDrawWithoutChangingCountOrComponents(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.EMERALD, 2);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("Trial emerald alpha"));
        ItemStack second = new ItemStack(Items.EMERALD, 4);
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Trial emerald beta"));
        ItemStack referenceFirst = first.copy(), referenceSecond = second.copy();
        var loot = list(first, second);
        SixTickets winning = new SixTickets(0);
        OverworldTrialLootBalance.reduce(loot, helper.getLevel(), TRIAL, winning);
        helper.assertValueEqual(winning.draws, 1, "Split stacks cannot multiply acceptance opportunities");
        helper.assertTrue(loot.size() == 2 && loot.getFirst() == first && loot.getLast() == second,
                "A winning resource preserves every original stack object and order");
        helper.assertTrue(ItemStack.matches(first, referenceFirst) && ItemStack.matches(second, referenceSecond),
                "Original quantities and per-stack data components are preserved");
        SixTickets losing = new SixTickets(2);
        OverworldTrialLootBalance.reduce(loot, helper.getLevel(), TRIAL, losing);
        helper.assertValueEqual(losing.draws, 1, "A loss also uses only one decision for duplicate stacks");
        helper.assertTrue(loot.isEmpty(), "A losing resource removes all of its split stacks together");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unrelatedTablesDimensionsNamespacesAndNoTargetLootConsumeNoAcceptanceRandomness(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel(), underworld = underworld(helper);
        for (ServerLevel level : List.of(overworld, underworld)) {
            for (ResourceLocation table : List.of(vanilla("chests/simple_dungeon"),
                    ResourceLocation.fromNamespaceAndPath("addon", "chests/trial_chambers/reward"),
                    ResourceLocation.fromNamespaceAndPath("mite", "chests/underworld_trial_chambers/reward"),
                    vanilla("chests/trial_chambers_fake/reward"))) {
                assertUntouched(helper, level, table);
            }
        }
        assertUntouched(helper, underworld, TRIAL);
        assertUntouched(helper, overworld, null);
        assertUntouched(helper, underworld, vanilla("pots/trial_chambers/corridor"));
        var ordinary = list(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_BLOCK));
        SixTickets random = new SixTickets(5);
        OverworldTrialLootBalance.reduce(ordinary, overworld, TRIAL, random);
        helper.assertValueEqual(random.draws, 0, "A matching table without target resources does not consume RNG");
        helper.assertValueEqual(ordinary.size(), 2, "Diamond storage blocks and other non-target resources retain their rewards");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFinalLootReturnCombinesTrialReductionWithRetiredDiamondEquipmentFilter(GameTestHelper helper) {
        LootTable table = fixture();
        table.setLootTableId(vanilla("chests/trial_chambers/reduction_fixture"));
        var retained = table.getRandomItems(params(helper.getLevel()), new SixTickets(0));
        helper.assertValueEqual(retained.size(), 4, "A winning native final return retains three resources and iron");
        helper.assertTrue(retained.stream().noneMatch(stack -> stack.is(Items.DIAMOND_SWORD)),
                "The retired equipment filter remains effective alongside the probability hook");
        var rejected = table.getRandomItems(params(helper.getLevel()), new SixTickets(5));
        helper.assertTrue(rejected.size() == 1 && rejected.getFirst().is(Items.IRON_INGOT),
                "The actual LootTable return removes lost resources and forbidden equipment without replacement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativePotLootUsesTheSameOverworldOnlyAcceptanceBoundary(GameTestHelper helper) {
        LootTable table = fixture();
        table.setLootTableId(vanilla("pots/trial_chambers/reduction_fixture"));
        helper.assertTrue(OverworldTrialLootBalance.applies(helper.getLevel(), table.getLootTableId()),
                "Trial decorated-pot paths are included independently of chest paths");
        var rejected = table.getRandomItems(params(helper.getLevel()), new SixTickets(1));
        helper.assertTrue(rejected.size() == 1 && rejected.getFirst().is(Items.IRON_INGOT),
                "The actual pot final return applies the sixth-chance reduction");
        var underground = table.getRandomItems(params(underworld(helper)), new SixTickets(1));
        helper.assertValueEqual(underground.size(), 4, "The identical vanilla pot table retains resources in the underworld");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void actualLoadedNormalAndOminousVaultGraphsReduceOnlyRequestedFinalResources(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel(), underworld = underworld(helper);
        boolean[] seen = new boolean[TARGETS.size()];
        for (String suffix : List.of("reward", "reward_ominous")) {
            LootTable table = overworld.getServer().reloadableRegistries().getLootTable(ResourceKey.create(
                    Registries.LOOT_TABLE, vanilla("chests/trial_chambers/" + suffix)));
            helper.assertTrue(table != LootTable.EMPTY, "Pinned native vault graph is loaded: " + suffix);
            for (int seed = 1; seed <= 128; seed++) {
                for (int ticket : new int[]{0, 5}) {
                    var baseline = table.getRandomItems(params(underworld), new SixTickets(seed * 10_007L, new int[]{ticket}));
                    var actual = table.getRandomItems(params(overworld), new SixTickets(seed * 10_007L, new int[]{ticket}));
                    List<ItemStack> expected = new ArrayList<>();
                    for (ItemStack stack : baseline) {
                        int index = TARGETS.indexOf(stack.getItem());
                        if (index >= 0) seen[index] = true;
                        if (ticket == 0 || index < 0) expected.add(stack);
                    }
                    helper.assertValueEqual(actual.size(), expected.size(),
                            "Only final target rewards disappear from real nested vault graph: " + suffix);
                    for (int i = 0; i < actual.size(); i++) helper.assertTrue(ItemStack.matches(actual.get(i), expected.get(i)),
                            "Other vault rewards, enchantments, components and stack quantities remain identical");
                }
            }
        }
        for (int i = 0; i < seen.length; i++) helper.assertTrue(seen[i],
                "The actual normal/ominous vault graphs exercise target resource " + TARGETS.get(i));
        helper.succeed();
    }

    private static void assertUntouched(GameTestHelper helper, ServerLevel level, ResourceLocation table) {
        var loot = list(new ItemStack(Items.EMERALD_BLOCK, 2), new ItemStack(Items.EMERALD, 3), new ItemStack(Items.DIAMOND, 4));
        var original = new ArrayList<>(loot);
        SixTickets random = new SixTickets(5);
        helper.assertTrue(!OverworldTrialLootBalance.applies(level, table), "Unrelated table or dimension is outside the rule: " + table);
        OverworldTrialLootBalance.reduce(loot, level, table, random);
        helper.assertValueEqual(random.draws, 0, "Unrelated loot never consumes acceptance RNG");
        helper.assertTrue(loot.equals(original), "Unrelated loot keeps every original item stack");
    }

    private static LootTable fixture() {
        LootTable.Builder builder = LootTable.lootTable().setParamSet(LootContextParamSets.VAULT);
        for (Item item : List.of(Items.EMERALD_BLOCK, Items.EMERALD, Items.DIAMOND, Items.IRON_INGOT, Items.DIAMOND_SWORD))
            builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(item)));
        return builder.build();
    }

    private static LootParams params(ServerLevel level) {
        return new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, new Vec3(0, 200, 0))
                .create(LootContextParamSets.VAULT);
    }

    private static ResourceLocation vanilla(String path) { return ResourceLocation.withDefaultNamespace(path); }
    private static ObjectArrayList<ItemStack> list(ItemStack... stacks) { return new ObjectArrayList<>(stacks); }
    private static ServerLevel underworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The actual underworld dimension is loaded");
        return level;
    }

    /** Other loot-engine randomness remains seeded; only the new acceptance-boundary draw is scripted. */
    private static final class SixTickets extends LegacyRandomSource {
        private final int[] tickets;
        private int draws;
        private SixTickets(int... tickets) { this(196L, tickets); }
        private SixTickets(long seed, int[] tickets) { super(seed); this.tickets = tickets; }
        @Override public int nextInt(int bound) {
            if (bound != 6) return super.nextInt(bound);
            return tickets[(draws++) % tickets.length];
        }
    }
}
