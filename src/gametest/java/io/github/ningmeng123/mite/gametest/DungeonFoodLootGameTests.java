package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.loot.DungeonFoodLoot;
import io.github.ningmeng123.mite.loot.LemonSeedChestLoot;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.utility.UniqueMusicSavedData;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exact food chances plus the real registered chest pipeline in both dungeon dimensions. @author NingMeng123 */
@GameTestHolder("mite_dungeon_food")
@PrefixGameTestTemplate(false)
public final class DungeonFoodLootGameTests {
    private static final ResourceLocation DUNGEON = vanilla("chests/simple_dungeon");
    private static final String[] RECORDS = {"underworld", "descent", "wanderer", "legends"};

    private DungeonFoodLootGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void twoIndependentTwentyPercentDecisionsKeepAllThreeQuantityOutcomesAndExistingLoot(GameTestHelper h) {
        for (int quantity = 1; quantity <= 3; quantity++) for (int accepted = 0; accepted < 4; accepted++) {
            ItemStack original = new ItemStack(Items.IRON_INGOT, 2);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Dungeon original reward"));
            ItemStack before = original.copy();
            var loot = new ObjectArrayList<ItemStack>(); loot.add(original);
            var random = new BoundaryRandom((accepted & 1) != 0 ? .199999F : .2F,
                    (accepted & 2) != 0 ? .199999F : .2F, quantity - 1);
            var result = DungeonFoodLoot.append(loot, context(h.getLevel(), DUNGEON, random));
            h.assertTrue(result == loot && result.getFirst() == original && ItemStack.matches(original, before),
                    "Adding dungeon food retains the existing list, stack identity, quantity and components");
            h.assertValueEqual(count(result, "orange"), (accepted & 1) != 0 ? quantity : 0,
                    "Orange accepts a draw below twenty percent and rejects the exact boundary");
            h.assertValueEqual(count(result, "onion"), (accepted & 2) != 0 ? quantity : 0,
                    "Onion has its own independent decision and the same one-to-three quantity range");
            h.assertValueEqual(random.chanceDraws, 2, "Each chest makes exactly one chance decision per food");
            h.assertValueEqual(random.quantityDraws, Integer.bitCount(accepted),
                    "A missed chance does not consume a quantity draw or create a replacement reward");
            h.assertValueEqual(result.size(), 1 + Integer.bitCount(accepted),
                    "Each accepted food is appended once, and both foods may occur together");
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void otherChestTablesForeignNamespacesAndUnknownContextsNeverChangeLootOrConsumeRandomness(GameTestHelper h) {
        for (ResourceLocation table : List.of(vanilla("chests/abandoned_mineshaft"), vanilla("chests/desert_pyramid"),
                vanilla("chests/village/village_plains_house"), vanilla("chests/trial_chambers/reward"),
                vanilla("chests/simple_dungeon_extra"), ResourceLocation.fromNamespaceAndPath("addon", "chests/simple_dungeon"))) {
            assertUntouched(h, table);
        }
        assertUntouched(h, null);
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite", timeoutTicks = 400)
    public static void registeredOverworldAndUnderworldDungeonTablesCanDeliverEitherBothOrNeitherFood(GameTestHelper h) {
        try (var records = new RecordClaims(h.getLevel())) {
            for (ServerLevel level : List.of(h.getLevel(), underworld(h))) {
                LootTable table = loadedTable(level, DUNGEON);
                h.assertTrue(table != LootTable.EMPTY, "The native spawner-dungeon table is actually registered");
                int[] outcomes = new int[4]; boolean ordinaryLoot = false;
                for (int sample = 1; sample <= 256; sample++) {
                    var loot = table.getRandomItems(params(level), 0x464f4f44L + sample * 1_000_003L);
                    int orange = count(loot, "orange"), onion = count(loot, "onion");
                    h.assertTrue(orange >= 0 && orange <= 3 && onion >= 0 && onion <= 3,
                            "The final registered pipeline emits at most one one-to-three stack of each food per dungeon chest");
                    outcomes[(orange > 0 ? 1 : 0) | (onion > 0 ? 2 : 0)]++;
                    ordinaryLoot |= loot.stream().anyMatch(stack -> !food(stack));
                }
                for (int outcome = 0; outcome < outcomes.length; outcome++) h.assertTrue(outcomes[outcome] > 0,
                        "Registered dungeon pipeline reaches independent food outcome " + outcome + " in " + level.dimension().location());
                h.assertTrue(ordinaryLoot, "Adding food does not replace all existing dungeon rewards");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite", timeoutTicks = 400)
    public static void registeredNonDungeonChestsInBothDimensionsDoNotAcquireTheNewFoods(GameTestHelper h) {
        try (var records = new RecordClaims(h.getLevel())) {
            for (ServerLevel level : List.of(h.getLevel(), underworld(h))) {
                for (String path : List.of("chests/abandoned_mineshaft", "chests/desert_pyramid", "chests/village/village_plains_house")) {
                    LootTable table = loadedTable(level, vanilla(path));
                    h.assertTrue(table != LootTable.EMPTY, "The unrelated native chest table is loaded: " + path);
                    for (int sample = 1; sample <= 32; sample++) {
                        var loot = table.getRandomItems(params(level), 0x4e4f464f4f44L + sample * 1_000_033L);
                        h.assertTrue(loot.stream().noneMatch(DungeonFoodLootGameTests::food),
                                "Food additions remain scoped to spawner dungeons rather than " + path);
                    }
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite", timeoutTicks = 400)
    public static void registeredSurfaceAndUnderworldDungeonsAndAllVillageHousesAwardLemonSeeds(GameTestHelper h) {
        try (var records = new RecordClaims(h.getLevel())) {
            assertSeedRewards(h, h.getLevel(), DUNGEON);
            assertSeedRewards(h, underworld(h), DUNGEON);
            for (String type : List.of("desert", "plains", "savanna", "snowy", "taiga")) {
                assertSeedRewards(h, h.getLevel(), vanilla("chests/village/village_" + type + "_house"));
            }
            for (String path : List.of("chests/village/village_weaponsmith", "chests/abandoned_mineshaft",
                    "chests/trial_chambers/reward")) {
                ResourceLocation id = vanilla(path);
                LootTable table = loadedTable(h.getLevel(), id);
                h.assertTrue(table != LootTable.EMPTY, "The unrelated chest table is registered: " + id);
                for (int sample = 1; sample <= 32; sample++) {
                    h.assertValueEqual(seedCount(table.getRandomItems(params(h.getLevel()), sample * 104729L)), 0,
                            "Unrelated registered chests do not generate lemon seeds: " + id);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void lemonSeedRollUsesTheChestSpecificChanceAndPreservesExistingLoot(GameTestHelper h) {
        for (ResourceLocation id : List.of(DUNGEON, vanilla("chests/village/village_plains_house"))) {
            float chance = id.equals(DUNGEON) ? 0.20F : 0.125F;
            for (float roll : new float[] {Math.nextDown(chance), chance}) {
                ItemStack original = new ItemStack(Items.IRON_INGOT, 2);
                original.set(DataComponents.CUSTOM_NAME, Component.literal("Existing chest reward"));
                ItemStack before = original.copy();
                var loot = new ObjectArrayList<ItemStack>();
                loot.add(original);
                var random = new LegacyRandomSource(196L) {
                    @Override public float nextFloat() { return roll; }
                    @Override public int nextInt(int bound) {
                        h.assertValueEqual(bound, 2, "The seed stack has two possible quantities");
                        return 1;
                    }
                };
                var result = LemonSeedChestLoot.append(loot, context(h.getLevel(), id, random));
                h.assertTrue(result == loot && result.getFirst() == original && ItemStack.matches(original, before),
                        "The additive reward preserves the original stack and its components");
                h.assertValueEqual(seedCount(result), roll < chance ? 2 : 0,
                        "A roll equal to the chest's probability yields no seeds: " + id);
            }
        }
        for (ResourceLocation id : List.of(vanilla("chests/village/village_weaponsmith"),
                ResourceLocation.fromNamespaceAndPath("addon", "chests/simple_dungeon"))) {
            var loot = new ObjectArrayList<ItemStack>();
            var result = LemonSeedChestLoot.append(loot, context(h.getLevel(), id, new ForbiddenRandom()));
            h.assertTrue(result == loot && result.isEmpty(), "Unrelated tables neither change loot nor consume RNG: " + id);
        }
        h.succeed();
    }

    private static void assertSeedRewards(GameTestHelper h, ServerLevel level, ResourceLocation id) {
        LootTable table = loadedTable(level, id);
        h.assertTrue(table != LootTable.EMPTY, "The intended chest table is registered: " + id);
        int[] outcomes = new int[3];
        boolean ordinaryLoot = false;
        for (int sample = 1; sample <= 128; sample++) {
            var loot = table.getRandomItems(params(level), 0x4c454d4f4eL + sample * 104729L);
            int seeds = seedCount(loot);
            h.assertTrue(seeds <= 2, "At most two lemon seeds generate per chest: " + id);
            outcomes[seeds]++;
            ordinaryLoot |= loot.stream().anyMatch(stack -> !stack.is(MiteFoodPlants.LEMON_SEEDS.get()) && !food(stack));
        }
        for (int outcome = 0; outcome < outcomes.length; outcome++) {
            h.assertTrue(outcomes[outcome] > 0,
                    "The registered chest can roll " + outcome + " lemon seeds: " + id + " in " + level.dimension().location());
        }
        h.assertTrue(ordinaryLoot, "The registered chest still delivers its ordinary rewards: " + id);
    }

    private static int seedCount(List<ItemStack> loot) {
        return loot.stream().filter(stack -> stack.is(MiteFoodPlants.LEMON_SEEDS.get())).mapToInt(ItemStack::getCount).sum();
    }

    private static void assertUntouched(GameTestHelper h, ResourceLocation table) {
        ItemStack existing = new ItemStack(Items.GOLD_INGOT, 3);
        var loot = new ObjectArrayList<ItemStack>(); loot.add(existing);
        var result = DungeonFoodLoot.append(loot, context(h.getLevel(), table, new ForbiddenRandom()));
        h.assertTrue(result == loot && result.size() == 1 && result.getFirst() == existing && existing.getCount() == 3,
                "An unrelated table preserves its exact existing rewards: " + table);
    }

    private static int count(List<ItemStack> loot, String item) {
        return loot.stream().filter(stack -> stack.is(MiteFoods.get(item))).mapToInt(ItemStack::getCount).sum();
    }
    private static boolean food(ItemStack stack) { return stack.is(MiteFoods.get("orange")) || stack.is(MiteFoods.get("onion")); }
    private static ResourceLocation vanilla(String path) { return ResourceLocation.withDefaultNamespace(path); }
    private static LootTable loadedTable(ServerLevel level, ResourceLocation id) {
        return level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
    }
    private static LootParams params(ServerLevel level) {
        return new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, new Vec3(0, 64, 0))
                .create(LootContextParamSets.CHEST);
    }
    private static LootContext context(ServerLevel level, ResourceLocation table, RandomSource random) {
        return new LootContext.Builder(params(level)).withOptionalRandomSource(random).withQueriedLootTableId(table).create(Optional.empty());
    }
    private static ServerLevel underworld(GameTestHelper h) {
        ServerLevel level = h.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        h.assertTrue(level != null, "The actual underworld dimension is loaded for the complete replacement-pool regression");
        return level;
    }

    private static final class BoundaryRandom extends LegacyRandomSource {
        private final float orange, onion; private final int quantity;
        int chanceDraws, quantityDraws;
        BoundaryRandom(float orange, float onion, int quantity) { super(196L); this.orange = orange; this.onion = onion; this.quantity = quantity; }
        @Override public float nextFloat() {
            if (chanceDraws >= 2) throw new AssertionError("Unexpected extra dungeon food chance draw");
            return chanceDraws++ == 0 ? orange : onion;
        }
        @Override public int nextInt(int bound) {
            if (bound != 3) throw new AssertionError("Dungeon food quantity must have exactly three outcomes");
            quantityDraws++; return quantity;
        }
    }
    private static final class ForbiddenRandom extends LegacyRandomSource {
        ForbiddenRandom() { super(196L); }
        @Override public int nextInt(int bound) { throw new AssertionError("Unrelated loot consumed dungeon food RNG"); }
        @Override public float nextFloat() { throw new AssertionError("Unrelated loot consumed dungeon food RNG"); }
        @Override public long nextLong() { throw new AssertionError("Unrelated loot consumed dungeon food RNG"); }
        @Override public double nextDouble() { throw new AssertionError("Unrelated loot consumed dungeon food RNG"); }
        @Override public boolean nextBoolean() { throw new AssertionError("Unrelated loot consumed dungeon food RNG"); }
    }
    private static final class RecordClaims implements AutoCloseable {
        final UniqueMusicSavedData claims; final Set<String> previous = new HashSet<>();
        RecordClaims(ServerLevel level) {
            claims = UniqueMusicSavedData.get(level);
            for (String record : RECORDS) { if (claims.has(record)) previous.add(record); claims.mark(record); }
        }
        @Override public void close() {
            for (String record : RECORDS) { claims.release(record); if (previous.contains(record)) claims.mark(record); }
        }
    }
}
