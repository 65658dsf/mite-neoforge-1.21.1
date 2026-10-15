package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.worldgen.VillageBlacksmithLoot;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded source table, real registered stacks and independent selection boundary evidence. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class VillageLootGameTests {
    private VillageLootGameTests() {}

    @GameTest(template = "empty")
    public static void everySourceRewardResolvesAgainstTheActualItemRegistryBeforeAnyRandomDraw(GameTestHelper helper) {
        var missing = VillageBlacksmithLoot.entries().stream().map(VillageBlacksmithLoot.Entry::item)
                .filter(id -> !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR).toList();
        helper.assertTrue(missing.isEmpty(), "All 56 source rewards must use registered item IDs: " + missing);
        VillageBlacksmithLoot.validateRegisteredItems();
        for (String slot : new String[]{"helmet", "chestplate", "leggings", "boots"}) helper.assertTrue(
                VillageBlacksmithLoot.entries().stream().anyMatch(row -> row.item().toString().equals("mite:copper_chain_" + slot)),
                "Copper chain armor uses its registry identity, not its old chainmail texture path");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sourceBlacksmithTableReachesAllRewardsWithLegalCountsWearAndDefaultQuality(GameTestHelper helper) {
        helper.assertValueEqual(VillageBlacksmithLoot.entries().size(), 56, "Source aji declares 56 weighted rows");
        helper.assertValueEqual(VillageBlacksmithLoot.totalWeight(), 302, "Independent sum of source aji weights is 302");
        Set<ResourceLocation> seen = new HashSet<>();
        for (int sample = 0; sample < 4096; sample++) {
            SimpleContainer inventory = new SimpleContainer(27);
            VillageBlacksmithLoot.fill(inventory, 64, Level.OVERWORLD, 59L * 24000, RandomSource.create(sample * 1_000_003L + 196));
            int occupied = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) continue;
                occupied++;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                seen.add(id);
                var row = VillageBlacksmithLoot.entries().stream().filter(entry -> entry.item().equals(id)).findFirst().orElseThrow();
                helper.assertTrue(stack.getCount() >= row.minimum() && stack.getCount() <= row.maximum()
                        && stack.getCount() <= stack.getMaxStackSize(), "Source quantities remain legal: " + id);
                helper.assertFalse(stack.isEnchanted(), "The source blacksmith table does not invent enchantments");
                if (stack.isDamageableItem()) {
                    boolean sourceFraction = false;
                    for (int tenth = 0; tenth <= 6; tenth++) sourceFraction |= stack.getDamageValue() == (int) ((long) stack.getMaxDamage() * tenth / 10);
                    helper.assertTrue(sourceFraction, "Chest wear is a discrete source tenth, not continuous vanilla random damage: " + id);
                    helper.assertTrue(EquipmentDurability.quality(stack) == Quality.AVERAGE, "Chest equipment retains source average quality: " + id);
                }
            }
            helper.assertTrue(occupied >= 1 && occupied <= 8, "Three through eight random-slot writes cannot expose more than eight occupied slots");
        }
        for (var row : VillageBlacksmithLoot.entries()) helper.assertTrue(seen.contains(row.item()), "Actual loaded registry reaches reward " + row.item());
        helper.assertFalse(seen.contains(ResourceLocation.withDefaultNamespace("diamond"))
                || seen.stream().anyMatch(id -> id.getPath().startsWith("diamond_") && !id.getPath().equals("diamond_shard")),
                "Village smiths cannot bypass MITE progress with whole diamonds or diamond equipment");
        helper.assertFalse(seen.contains(ResourceLocation.withDefaultNamespace("iron_pickaxe")),
                "Source deliberately omits the iron pickaxe from this table");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSourceSelectionBoundariesChooseTheSpecifiedRowAndCount(GameTestHelper helper) {
        int cursor = 0;
        for (var row : VillageBlacksmithLoot.entries()) {
            for (int selected : new int[]{cursor, cursor + row.weight() - 1}) {
                SimpleContainer inventory = new SimpleContainer(27);
                VillageBlacksmithLoot.fill(inventory, 64, Level.OVERWORLD, 59L * 24000, new BoundaryRandom(selected));
                ItemStack item = inventory.getItem(0);
                helper.assertTrue(BuiltInRegistries.ITEM.getKey(item.getItem()).equals(row.item()),
                        "Both ends of the independently accumulated weighted interval select " + row.item());
                helper.assertValueEqual(item.getCount(), row.minimum(), "Zero count draw selects the declared source minimum");
                helper.assertTrue(item.getDamageValue() == 0, "Zero source wear draw creates undamaged equipment");
                for (int slot = 1; slot < 27; slot++) helper.assertTrue(inventory.getItem(slot).isEmpty(),
                        "Repeated writes to the same random slot overwrite instead of accumulating or rerouting");
            }
            cursor += row.weight();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceChestWearUsesEveryTenthWithoutMobLeatherQualityDowngrade(GameTestHelper helper) {
        for (int fraction = 0; fraction <= 6; fraction++) {
            ItemStack leather = new ItemStack(Items.LEATHER_CHESTPLATE);
            final int draw = fraction;
            VillageBlacksmithLoot.applyChestWear(leather, new net.minecraft.world.level.levelgen.LegacyRandomSource(196) {
                @Override public int nextInt(int bound) { return draw; }
            });
            helper.assertTrue(EquipmentDurability.quality(leather) == Quality.AVERAGE,
                    "Source chest damage does not apply the distinct mob-generated leather downgrade");
            helper.assertValueEqual(leather.getDamageValue(), leather.getMaxDamage() * fraction / 10,
                    "Each independent source wear boundary scales the actual registered durability");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceLowAltitudeAndEarlyDayChestFiltersRetainFourAttemptLimit(GameTestHelper helper) {
        int hoeCursor = 0;
        for (var row : VillageBlacksmithLoot.entries()) {
            if (row.item().getPath().equals("copper_hoe")) break;
            hoeCursor += row.weight();
        }
        SimpleContainer low = new SimpleContainer(27);
        BoundaryRandom random = new BoundaryRandom(hoeCursor);
        VillageBlacksmithLoot.fill(low, 47, Level.OVERWORLD, 59L * 24000, random);
        helper.assertTrue(low.isEmpty(), "Source mk rejects hoes below y48");
        helper.assertValueEqual(random.weightDraws, 12, "Three source rolls each make at most four rejected attempts");
        SimpleContainer early = new SimpleContainer(27);
        VillageBlacksmithLoot.fill(early, 64, Level.OVERWORLD, 0, new BoundaryRandom(hoeCursor));
        helper.assertTrue(early.isEmpty(), "Source early-day filtering remains valid for direct structure placement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceDisplayDayBoundaryUnlocksRealHoeAndCoinRewardsSixThousandTicksBeforeMidnight(GameTestHelper helper) {
        for (String id : new String[]{"mite:copper_hoe", "mite:copper_coin"}) {
            int cursor = 0;
            var selected = VillageBlacksmithLoot.entries().stream().filter(row -> row.item().toString().equals(id)).findFirst().orElseThrow();
            for (var row : VillageBlacksmithLoot.entries()) { if (row == selected) break; cursor += row.weight(); }
            long boundary = (selected.minimumDay() - 1L) * 24000L - 6000L;
            SimpleContainer before = new SimpleContainer(27), unlocked = new SimpleContainer(27);
            VillageBlacksmithLoot.fill(before, 64, Level.OVERWORLD, boundary - 1, new BoundaryRandom(cursor));
            VillageBlacksmithLoot.fill(unlocked, 64, Level.OVERWORLD, boundary, new BoundaryRandom(cursor));
            helper.assertTrue(before.isEmpty(), "Actual source reward remains gated immediately before its display-day boundary: " + id);
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(unlocked.getItem(0).getItem()).equals(selected.item()),
                    "Actual source reward unlocks at the +6000 display-day boundary: " + id);
        }
        helper.succeed();
    }

    private static final class BoundaryRandom extends net.minecraft.world.level.levelgen.LegacyRandomSource {
        private final int selected;
        int weightDraws;
        BoundaryRandom(int selected) { super(196); this.selected = selected; }
        @Override public int nextInt(int bound) {
            if (bound == 302) { weightDraws++; return selected; }
            return 0;
        }
    }
}
