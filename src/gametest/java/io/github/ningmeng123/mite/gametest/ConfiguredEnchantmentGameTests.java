package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.crafting.SourceVillagerTrades;
import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import io.github.ningmeng123.mite.enchantment.R196BookAnvil;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.rules.SourceVillagerTradeRules;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.SingleEnchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Configuration is tested through real generation, merging and effect paths. @author NingMeng123 */
@GameTestHolder("mite_config")
@PrefixGameTestTemplate(false)
public final class ConfiguredEnchantmentGameTests {
    private ConfiguredEnchantmentGameTests() {}

    private static Holder<Enchantment> holder(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static ItemStack book(Holder<Enchantment> enchantment, int level) {
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(enchantment, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    private static final class Settings implements AutoCloseable {
        private final List<? extends String> disabled = MiteConfig.DISABLED_ENCHANTMENTS.get();
        private final List<? extends String> caps = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        private Settings() { set(List.of(), List.of()); }
        private void set(List<String> disabled, List<String> caps) {
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
            MiteConfig.refreshEnchantmentRules();
        }
        @Override public void close() {
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
            MiteConfig.refreshEnchantmentRules();
        }
    }

    @GameTest(template = "empty")
    public static void capsReachNativeSourceAndExistingEquipmentWithoutRewritingSavedLevels(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var sharpness = holder(helper, Enchantments.SHARPNESS);
            var riptide = holder(helper, Enchantments.RIPTIDE);
            var flame = R196EnchantmentCatalog.definition("minecraft:flame");
            int defaultFlameCost = flame.minCost(1);
            var sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(sharpness, 5);
            settings.set(List.of(), List.of("minecraft:sharpness=2", "minecraft:riptide=7", "mite:stun=3", "minecraft:flame=3"));
            helper.assertValueEqual(sharpness.value().getMaxLevel(), 2, "Native metadata exposes a lower configured cap");
            helper.assertValueEqual(riptide.value().getMaxLevel(), 7, "Modern-only metadata supports raising a cap");
            helper.assertValueEqual(R196EnchantmentCatalog.definition("mite:stun").maxLevel(), 3, "MITE source metadata exposes its configured cap");
            helper.assertValueEqual(flame.minCost(1), defaultFlameCost, "Raising a formerly single-rank cap preserves its original first-rank cost");
            helper.assertValueEqual(flame.minCost(2), defaultFlameCost + flame.difficulty(), "Extra configured ranks require proportionally more enchanting power");
            helper.assertValueEqual(sword.get(DataComponents.ENCHANTMENTS).getLevel(sharpness), 5, "Saved ordinary levels are retained");
            helper.assertValueEqual(sword.getEnchantmentLevel(sharpness), 2, "Existing ordinary enchantment effects obey the cap");
            var selected = R196EnchantmentTable.randomEnchantments(new ItemStack(Items.IRON_SWORD), 1000,
                    RandomSource.create(196L), List.of(sharpness));
            helper.assertValueEqual(selected.getFirst().level, 2, "Source random equipment selection obeys the cap");
            var added = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            added.upgrade(sharpness, 10);
            helper.assertValueEqual(added.getLevel(sharpness), 2, "Direct new upgrade paths obey explicit caps");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disablingAnEnchantmentSuppressesNormalAndGrowthEffectsWhileRetainingBothSources(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var power = holder(helper, Enchantments.POWER);
            var bow = new ItemStack(Items.BOW);
            bow.enchant(power, 2);
            bow.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 1, 0L,
                    Map.of(Enchantments.POWER.location(), 1), 1));
            var cow = EntityType.COW.create(helper.getLevel());
            var arrow = EntityType.ARROW.create(helper.getLevel());
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            float before = EnchantmentHelper.modifyDamage(helper.getLevel(), bow, cow,
                    helper.getLevel().damageSources().arrow(arrow, player), 5F);
            helper.assertTrue(Math.abs(before - 7.5F) < .0001F, "Both independent Power sources initially contribute");
            settings.set(List.of(), List.of("minecraft:power=1"));
            float capped = EnchantmentHelper.modifyDamage(helper.getLevel(), bow, cow,
                    helper.getLevel().damageSources().arrow(arrow, player), 5F);
            helper.assertTrue(Math.abs(capped - 7F) < .0001F, "Explicit caps apply to ordinary and growth sources independently");
            settings.set(List.of("minecraft:power"), List.of());
            float disabled = EnchantmentHelper.modifyDamage(helper.getLevel(), bow, cow,
                    helper.getLevel().damageSources().arrow(arrow, player), 5F);
            helper.assertTrue(Math.abs(disabled - 5F) < .0001F, "Disabled native numerical effects contribute no damage");
            helper.assertValueEqual(EnchantmentSources.levels(bow, power).totalLevel(), 0, "MITE reads suppress both sources");
            helper.assertTrue(!GearProgression.supportsAffix(bow, power), "Disabled enchantments leave the growth candidate pool");
            helper.assertValueEqual(bow.get(DataComponents.ENCHANTMENTS).getLevel(power), 2, "Saved ordinary source is retained");
            helper.assertValueEqual(GearProgression.state(bow).affixes().get(Enchantments.POWER.location()), 1, "Saved growth source is retained");
            settings.set(List.of(), List.of());
            float restored = EnchantmentHelper.modifyDamage(helper.getLevel(), bow, cow,
                    helper.getLevel().damageSources().arrow(arrow, player), 5F);
            helper.assertTrue(Math.abs(restored - before) < .0001F, "Re-enabling and removing caps restores both saved sources");
            cow.discard();
            arrow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disabledEnchantmentsLeaveNaturalLootProvidersAndBothLibrarianPools(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var sharpness = holder(helper, Enchantments.SHARPNESS);
            settings.set(registry.holders().map(entry -> entry.key().location().toString()).toList(), List.of());
            var table = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST)
                    .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.BOOK)
                            .apply(EnchantRandomlyFunction.randomEnchantment().withEnchantment(sharpness)))).build();
            var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN,
                    helper.absolutePos(new BlockPos(1, 1, 1)).getCenter()).create(LootContextParamSets.CHEST);
            helper.assertTrue(table.getRandomItems(params, 196L).getFirst().is(Items.BOOK), "A disabled-only random loot pool leaves its ordinary book");
            var mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            new SingleEnchantment(sharpness, ConstantInt.of(5)).enchant(new ItemStack(Items.IRON_SWORD), mutable,
                    RandomSource.create(196L), helper.getLevel().getCurrentDifficultyAt(helper.absolutePos(new BlockPos(1, 1, 1))));
            helper.assertValueEqual(mutable.getLevel(sharpness), 0, "Fixed providers cannot add disabled enchantments");
            var villager = new Villager(EntityType.VILLAGER, helper.getLevel());
            var listing = SourceVillagerTradeRules.listings().stream()
                    .filter(entry -> entry.kind() == SourceVillagerTradeRules.Kind.BOOK).findFirst().orElseThrow();
            helper.assertTrue(SourceVillagerTrades.create(villager, listing, RandomSource.create(196L)) == null,
                    "Empty source librarian pools produce no enchantment offer");
            var nativeOffer = new VillagerTrades.EnchantBookForEmeralds(1, EnchantmentTags.TRADEABLE)
                    .getOffer(villager, RandomSource.create(196L));
            helper.assertTrue(nativeOffer.getResult().is(Items.BOOK), "Empty native librarian pools fall back to an ordinary book");
            helper.assertTrue(EnchantmentHelper.getAvailableEnchantmentResults(20, new ItemStack(Items.CROSSBOW),
                    java.util.stream.Stream.of(holder(helper, Enchantments.QUICK_CHARGE))).isEmpty(),
                    "Native modern equipment draws omit disabled entries");
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceAnvilUpgradesRespectRaisedAndLoweredCapsAndDisabledBooks(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var sharpness = holder(helper, Enchantments.SHARPNESS);
            var sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(sharpness, 7);
            var levelSevenBook = book(sharpness, 7);
            settings.set(List.of(), List.of("minecraft:sharpness=8"));
            var raised = R196BookAnvil.plan(sword, levelSevenBook);
            helper.assertTrue(raised != null && !raised.output().isEmpty(), "Raising the source cap enables another merge");
            helper.assertValueEqual(raised.output().get(DataComponents.ENCHANTMENTS).getLevel(sharpness), 8, "The source anvil reaches the configured eighth rank");
            settings.set(List.of(), List.of("minecraft:sharpness=2"));
            var bounded = R196BookAnvil.plan(new ItemStack(Items.IRON_SWORD), levelSevenBook);
            helper.assertValueEqual(bounded.output().get(DataComponents.ENCHANTMENTS).getLevel(sharpness), 2, "Existing higher books transfer only up to the configured cap");
            settings.set(List.of("minecraft:sharpness"), List.of());
            var disabled = R196BookAnvil.plan(new ItemStack(Items.IRON_SWORD), levelSevenBook);
            helper.assertTrue(disabled != null && disabled.output().isEmpty(), "A disabled source book cannot transfer through an anvil");
            helper.assertValueEqual(levelSevenBook.get(DataComponents.STORED_ENCHANTMENTS).getLevel(sharpness), 7, "The rejected transfer leaves its book data intact");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refreshingConfigurationRemovesAnAlreadyEquippedEnchantmentAttribute(GameTestHelper helper) {
        try (var settings = new Settings()) {
            var efficiency = holder(helper, Enchantments.EFFICIENCY);
            var pickaxe = new ItemStack(Items.IRON_PICKAXE);
            pickaxe.enchant(efficiency, 5);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
            ConfiguredEnchantments.refreshEffects(player);
            double before = player.getAttributeValue(Attributes.MINING_EFFICIENCY);
            helper.assertTrue(before > 0D, "Equipped efficiency initially installs its attribute");
            settings.set(List.of("minecraft:efficiency"), List.of());
            ConfiguredEnchantments.refreshEffects(player);
            helper.assertTrue(player.getAttributeValue(Attributes.MINING_EFFICIENCY) < before,
                    "Configuration refresh removes previously installed enchantment attributes");
            helper.assertValueEqual(pickaxe.get(DataComponents.ENCHANTMENTS).getLevel(efficiency), 5, "Refreshing attributes preserves item data");
            settings.set(List.of(), List.of());
            ConfiguredEnchantments.refreshEffects(player);
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MINING_EFFICIENCY) - before) < .0001D,
                    "Re-enabling restores the original equipped attribute without changing the item");
        }
        helper.succeed();
    }
}
