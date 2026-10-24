package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression for high enchantability bypassing a 200-point offer. @author NingMeng123 */
@GameTestHolder("mite_feedback")
@PrefixGameTestTemplate(false)
public final class FeedbackEnchantingGameTests {
    private FeedbackEnchantingGameTests() {}

    @GameTest(template = "empty")
    public static void allRegisteredCopperToolsUseTheReducedTotalBaseDamage(GameTestHelper helper) {
        int checked = 0;
        for (var definition : MiteTools.definitions()) if (definition.material().equals("copper")) {
            var stack = new ItemStack(MiteTools.get(definition.id()));
            double actual = 1D + stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
                    .filter(entry -> entry.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))
                    .mapToDouble(entry -> entry.modifier().amount()).sum();
            var silver = new ItemStack(MiteTools.get(definition.id().replace("copper", "silver")));
            double silverDamage = 1D + silver.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
                    .filter(entry -> entry.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))
                    .mapToDouble(entry -> entry.modifier().amount()).sum();
            helper.assertTrue(Math.abs(actual - silverDamage * .9375D) < .00001D,
                    "Copper and silver use the reviewed0.75/0.8 material budgets for the same source family: " + definition.id());
            checked++;
        }
        helper.assertTrue(checked >= 10, "All copper tool and weapon families were inspected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highRanksRespectSourcePaidEnergyAndMaterialCeilings(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            var protection = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.PROTECTION);
            var input = new ItemStack(MiteTools.get("vibranium_chestplate"));
            boolean foundFive = false;
            for (int seed = 0; seed < 256; seed++) {
                for (int energy : new int[]{2, 10, 30, 44, 45, 100}) {
                    var entries = VanillaTableEnchanting.select(input, 30, RandomSource.create(seed), List.of(protection), energy);
                    for (var entry : entries) {
                        helper.assertTrue(R196EnchantmentCatalog.definition("minecraft:protection").minCost(entry.level) <= (int)(energy * 1.25F),
                                "Each rank fits the source budget including its bounded initial variation");
                        if (energy == 2) helper.assertValueEqual(entry.level, 1, "200 XP only funds protection I");
                        if (entry.level == 5) foundFive = true;
                    }
                }
                for (var entry : VanillaTableEnchanting.select(new ItemStack(MiteTools.get("mithril_chestplate")), 30,
                        RandomSource.create(seed), List.of(protection), 100))
                    helper.assertTrue(entry.level <= 4, "Mithril cannot acquire vibranium's Protection V exception");
            }
            helper.assertTrue(foundFive, "Protection V remains attainable with sufficient offer strength and payment");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualTwoHundredPointPreviewAndPurchaseCannotProduceProtectionFive(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            var protection = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.PROTECTION);
            BlockPos table = helper.absolutePos(new BlockPos(2, 2, 2));
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y < 4; y++)
                helper.getLevel().setBlock(table.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(table, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get().defaultBlockState(), 2);
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PaidPower"));
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(table.getCenter());
            int purchases = 0;
            for (int seed = 0; seed < 32; seed++) {
                PlayerExperience.setTotal(player, 20000);
                var menu = new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
                menu.setData(3, seed);
                menu.getSlot(0).set(new ItemStack(MiteTools.get("vibranium_chestplate")));
                menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
                // Native empty-shelf RNG sometimes offers no third option at all.
                if (menu.costs[2] == 0) continue;
                helper.assertValueEqual(((R196EnchantmentMenuAccess)menu).mite$experienceCost(2), 200,
                        "This reproduces the reported two-hundred-point offer");
                helper.assertValueEqual(menu.levelClue[2], 1, "The actual menu previews protection I");
                helper.assertTrue(menu.clickMenuButton(player, 2), "The low-cost offer can still be bought");
                helper.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem()).getLevel(protection), 1,
                        "The purchased result matches its low-rank preview");
                helper.assertValueEqual(player.totalExperience, 19800, "The actual purchase charges exactly two hundred points");
                purchases++;
            }
            helper.assertTrue(purchases > 0, "Several native seeds actually exercised the paid low-cost offer");
        }
        helper.succeed();
    }

    private static final class Settings implements AutoCloseable {
        private final List<? extends String> caps = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        private final List<? extends String> disabled = MiteConfig.DISABLED_ENCHANTMENTS.get();
        private final List<? extends String> chances = MiteConfig.ENCHANTMENT_CHANCES.get();
        private Settings(GameTestHelper helper) {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(List.of("minecraft:protection=5"));
            MiteConfig.ENCHANTMENT_CHANCES.set(List.of());
            MiteConfig.DISABLED_ENCHANTMENTS.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                    .map(holder -> holder.key().location().toString()).filter(id -> !id.equals("minecraft:protection")).toList());
            MiteConfig.refreshEnchantmentRules();
        }
        @Override public void close() {
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.ENCHANTMENT_CHANCES.set(chances);
            MiteConfig.refreshEnchantmentRules();
        }
    }
}
