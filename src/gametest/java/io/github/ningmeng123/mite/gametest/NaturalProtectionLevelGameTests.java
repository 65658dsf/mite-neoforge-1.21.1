package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.SourceVillagerTrades;
import io.github.ningmeng123.mite.rules.SourceVillagerTradeRules;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.enchantment.providers.EnchantmentsByCost;
import net.minecraft.world.item.enchantment.providers.EnchantmentsByCostWithDifficulty;
import net.minecraft.world.item.enchantment.providers.SingleEnchantment;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native and source reward generators reserve Protection V for player enchanting and merging. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NaturalProtectionLevelGameTests {
    private NaturalProtectionLevelGameTests() {}

    private static Holder<Enchantment> protection(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
    }

    private static int level(ItemStack stack, Holder<Enchantment> enchantment) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(enchantment);
    }

    @GameTest(template = "empty")
    public static void bothNativeAndSourceLibrarianBooksStopAtProtectionFour(GameTestHelper helper) {
        Villager villager = new Villager(EntityType.VILLAGER, helper.getLevel());
        var source = SourceVillagerTradeRules.listings().stream()
                .filter(listing -> listing.kind() == SourceVillagerTradeRules.Kind.BOOK).findFirst().orElseThrow();
        var nativeListing = new VillagerTrades.EnchantBookForEmeralds(1, EnchantmentTags.TRADEABLE);
        var protection = protection(helper);
        int sourceLevels = 0, nativeLevels = 0;
        for (int seed = 0; seed < 4096; seed++) {
            int sourceLevel = level(SourceVillagerTrades.create(villager, source, RandomSource.create(seed)).getResult(), protection);
            int nativeLevel = level(nativeListing.getOffer(villager, RandomSource.create(seed)).getResult(), protection);
            helper.assertTrue(sourceLevel <= 4, "Source librarians cannot sell Protection V");
            helper.assertTrue(nativeLevel <= 4, "Native librarians cannot sell Protection V");
            if (sourceLevel > 0) sourceLevels |= 1 << (sourceLevel - 1);
            if (nativeLevel > 0) nativeLevels |= 1 << (nativeLevel - 1);
        }
        helper.assertValueEqual(sourceLevels, 15, "Source librarians retain all four natural Protection levels");
        helper.assertValueEqual(nativeLevels, 15, "Native librarians retain all four natural Protection levels");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chestAndFishingLootFunctionsCannotDirectlyGenerateProtectionFive(GameTestHelper helper) {
        var protection = protection(helper);
        int observedLevels = 0;
        for (boolean fishing : new boolean[] {false, true}) {
            var context = fishing ? LootContextParamSets.FISHING : LootContextParamSets.CHEST;
            LootTable table = LootTable.lootTable().setParamSet(context)
                    .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.BOOK)
                            .apply(EnchantRandomlyFunction.randomEnchantment().withEnchantment(protection))))
                    .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.BOOK)
                            .apply(new EnchantWithLevelsFunction.Builder(ConstantValue.exactly(200))
                                    .fromOptions(HolderSet.direct(protection)))))
                    .build();
            var builder = new LootParams.Builder(helper.getLevel())
                    .withParameter(LootContextParams.ORIGIN, helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)).getCenter());
            if (fishing) builder.withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD));
            var params = builder.create(context);
            for (int seed = 0; seed < 128; seed++) {
                List<ItemStack> loot = table.getRandomItems(params, seed);
                helper.assertValueEqual(loot.size(), 2, "Random and level-based reward functions each produce a book");
                for (ItemStack book : loot) {
                    int generated = level(book, protection);
                    helper.assertTrue(book.is(Items.ENCHANTED_BOOK) && generated >= 1 && generated <= 4,
                            "Chest and fishing rewards contain Protection I through IV only");
                    observedLevels |= 1 << (generated - 1);
                }
            }
        }
        helper.assertValueEqual(observedLevels, 15, "Natural loot still reaches every Protection level through IV");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mobAndFixedProvidersCannotGenerateProtectionFiveButOtherLevelFiveRewardsRemain(GameTestHelper helper) {
        var protection = protection(helper);
        var access = helper.getLevel().registryAccess();
        var random = RandomSource.create(196L);
        for (int run = 0; run < 128; run++) {
            ItemStack armor = EnchantmentHelper.enchantItem(random, new ItemStack(Items.IRON_CHESTPLATE), 200,
                    java.util.stream.Stream.of(protection));
            helper.assertValueEqual(level(armor, protection), 4, "High-energy mob equipment rewards stop at Protection IV");
        }
        var equipment = new ItemStack(Items.IRON_CHESTPLATE);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        new SingleEnchantment(protection, ConstantInt.of(5)).enchant(equipment, enchantments, random,
                helper.getLevel().getCurrentDifficultyAt(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1))));
        helper.assertValueEqual(enchantments.getLevel(protection), 4, "A fixed provider cannot award Protection V directly");
        var unbreaking = access.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
        var other = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        new SingleEnchantment(unbreaking, ConstantInt.of(5)).enchant(equipment, other, random,
                helper.getLevel().getCurrentDifficultyAt(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1))));
        helper.assertValueEqual(other.getLevel(unbreaking), 5, "Unbreaking V remains a legal generated reward");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void directCostProviderReservesProtectionFiveButRetainsOtherLevelFiveRewards(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var pool = HolderSet.direct(protection(helper), registry.getHolderOrThrow(Enchantments.UNBREAKING));
        assertNaturalCostProvider(helper, new EnchantmentsByCost(pool, ConstantInt.of(200)));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void directDifficultyCostProviderReservesProtectionFiveButRetainsOtherLevelFiveRewards(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var pool = HolderSet.direct(protection(helper), registry.getHolderOrThrow(Enchantments.UNBREAKING));
        assertNaturalCostProvider(helper, new EnchantmentsByCostWithDifficulty(pool, 200, 0));
        helper.succeed();
    }

    private static void assertNaturalCostProvider(GameTestHelper helper, EnchantmentProvider provider) {
        var protection = protection(helper);
        var unbreaking = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
        var difficulty = helper.getLevel().getCurrentDifficultyAt(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)));
        boolean foundProtectionFour = false, foundUnbreakingFive = false;
        for (int seed = 0; seed < 128; seed++) {
            var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            provider.enchant(new ItemStack(Items.IRON_CHESTPLATE), enchantments, RandomSource.create(seed), difficulty);
            int protectionLevel = enchantments.getLevel(protection), unbreakingLevel = enchantments.getLevel(unbreaking);
            helper.assertTrue(protectionLevel >= 0 && protectionLevel <= 4, "A direct cost provider respects the natural Protection IV ceiling");
            helper.assertTrue(unbreakingLevel >= 0 && unbreakingLevel <= 5, "The direct provider respects the Unbreaking V ceiling");
            helper.assertTrue(protectionLevel > 0 || unbreakingLevel > 0, "A nonempty eligible pool provides at least one random reward");
            foundProtectionFour |= protectionLevel == 4;
            foundUnbreakingFive |= unbreakingLevel == 5;
        }
        helper.assertTrue(foundProtectionFour, "Protection IV remains reachable across a fixed sequence of source rewards");
        helper.assertTrue(foundUnbreakingFive, "Unbreaking V remains reachable across a fixed sequence of source rewards");
    }
}
