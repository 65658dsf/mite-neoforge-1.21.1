package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ArmorDyeRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual maintenance menus and spawn-equipment hooks, with independent expected values. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class EquipmentQualityMaintenanceGameTests {
    private EquipmentQualityMaintenanceGameTests() {}

    @GameTest(template = "empty")
    public static void grindstoneKeepsQualityAndNeverMultipliesDurabilityTwice(GameTestHelper helper) {
        Player player = experienced(helper);
        GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory());
        menu.getSlot(0).set(equipment(Items.IRON_PICKAXE, Quality.FINE, 10000));
        menu.getSlot(1).set(equipment(Items.IRON_PICKAXE, Quality.FINE, 10000));
        ItemStack result = menu.getSlot(2).getItem();
        helper.assertFalse(result.isEmpty(), "Qualified equal-quality combine has an output");
        helper.assertValueEqual(result.getOrDefault(DataComponents.MAX_DAMAGE, 0), 9600, "Grindstone stores unscaled iron base");
        helper.assertValueEqual(result.getMaxDamage(), 14400, "Fine multiplier is applied once");
        helper.assertValueEqual(result.getDamageValue(), 5600, "4400 plus 4400 remaining has no bonus");
        helper.assertValueEqual(EquipmentDurability.quality(result), Quality.FINE, "Quality survives maintenance");
        menu.getSlot(1).set(equipment(Items.IRON_PICKAXE, Quality.AVERAGE, 6000));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Mixed quality cannot repair in the grindstone");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilRequiresRepairAbilityButRenamingDoesNot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PlayerExperience.setTotal(player, 0);
        AnvilMenu menu = new AnvilMenu(1, player.getInventory());
        menu.getSlot(0).set(equipment(Items.IRON_PICKAXE, Quality.FINE, 10000));
        menu.setItemName("品质保留测试");
        helper.assertFalse(menu.getSlot(2).getItem().isEmpty(), "Rename preview is not gated by crafting quality ability");
        helper.assertValueEqual(EquipmentDurability.quality(menu.getSlot(2).getItem()), Quality.FINE, "Renaming preserves quality");
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 10000, "Renaming repairs no wear");
        menu.getSlot(1).set(equipment(Items.IRON_PICKAXE, Quality.FINE, 10000));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Zero-XP player cannot repair fine equipment");
        PlayerExperience.setTotal(player, 203000);
        menu.createResult();
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 5600, "Qualified anvil repair has no 12-percent bonus");
        helper.assertValueEqual(menu.getSlot(2).getItem().getMaxDamage(), 14400, "Anvil keeps fine maximum");
        menu.getSlot(1).set(equipment(Items.IRON_PICKAXE, Quality.AVERAGE, 6000));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Anvil cannot merge two different qualities");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void qualityRepairAbilityIsRecheckedAfterExperienceLoss(GameTestHelper helper) {
        Player player = experienced(helper);
        GrindstoneMenu grindstone = new GrindstoneMenu(1, player.getInventory());
        grindstone.getSlot(0).set(equipment(Items.IRON_PICKAXE, Quality.FINE, 10000));
        grindstone.getSlot(1).set(equipment(Items.IRON_PICKAXE, Quality.FINE, 10000));
        helper.assertFalse(grindstone.getSlot(2).getItem().isEmpty(), "Initial grindstone preview exists");
        PlayerExperience.setTotal(player, 0);
        helper.assertTrue(grindstone.quickMoveStack(player, 2).isEmpty(), "Direct shift transfer rechecks lost ability");
        helper.assertValueEqual(grindstone.getSlot(0).getItem().getDamageValue(), 10000, "Denied grindstone consumes no material");

        PlayerExperience.setTotal(player, 203000);
        AnvilMenu anvil = new AnvilMenu(2, player.getInventory());
        anvil.getSlot(0).set(equipment(Items.IRON_PICKAXE, Quality.FINE, 10000));
        anvil.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 1));
        helper.assertFalse(anvil.getSlot(2).getItem().isEmpty(), "Material repair initially passes");
        PlayerExperience.setTotal(player, 0);
        helper.assertTrue(anvil.quickMoveStack(player, 2).isEmpty(), "Direct anvil shift transfer rechecks lost ability");
        helper.assertValueEqual(anvil.getSlot(1).getItem().getCount(), 1, "Denied material repair preserves ingot");
        anvil.clicked(2, 0, ClickType.PICKUP, player);
        helper.assertTrue(anvil.getCarried().isEmpty(), "Ordinary click cannot retrieve a stale quality repair");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void singleItemDisenchantingPreservesQualityWithoutRepairGate(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PlayerExperience.setTotal(player, 0);
        ItemStack first = equipment(Items.IRON_PICKAXE, Quality.FINE, 10000);
        first.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.EFFICIENCY), 1);
        GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory());
        menu.getSlot(0).set(first);
        ItemStack result = menu.getSlot(2).getItem();
        helper.assertFalse(result.isEmpty(), "Single-item disenchanting needs no quality crafting ability");
        helper.assertFalse(result.isEnchanted(), "The enchantment is removed");
        helper.assertValueEqual(result.getDamageValue(), 10000, "Disenchanting does not repair wear");
        helper.assertValueEqual(result.getMaxDamage(), 14400, "Disenchanting does not double the multiplier");
        helper.assertValueEqual(EquipmentDurability.quality(result), Quality.FINE, "Disenchanting preserves fine quality");
        helper.assertValueEqual(player.totalExperience, 0, "Preview has no quality experience cost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smithingAndDyeingInheritQualityWithoutCopyingOldDurabilityBase(GameTestHelper helper) {
        Item ancientPickaxe = MiteTools.get("mite:ancient_metal_pickaxe");
        ItemStack ancient = equipment(ancientPickaxe, Quality.FINE, 1000);
        ancient.set(DataComponents.MAX_DAMAGE, 19200); // Simulates a normal combine-repair component patch.
        SmithingTransformRecipe upgrade = new SmithingTransformRecipe(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(ancientPickaxe), Ingredient.of(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_PICKAXE));
        ItemStack upgraded = upgrade.assemble(new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                ancient, new ItemStack(Items.NETHERITE_INGOT)), helper.getLevel().registryAccess());
        helper.assertValueEqual(EquipmentDurability.quality(upgraded), Quality.FINE, "Upgrade preserves fine quality");
        helper.assertValueEqual(upgraded.getOrDefault(DataComponents.MAX_DAMAGE, 0), 38400, "Upgrade adopts the netherite base");
        helper.assertValueEqual(upgraded.getMaxDamage(), 57600, "New base receives quality exactly once");
        helper.assertValueEqual(upgraded.getDamageValue(), 1000, "Modern upgrade preserves damage");

        ItemStack leather = equipment(Items.LEATHER_CHESTPLATE, Quality.FINE, 6);
        ArmorDyeRecipe dye = new ArmorDyeRecipe(CraftingBookCategory.MISC);
        ItemStack dyed = dye.assemble(CraftingInput.of(2, 1, List.of(leather, new ItemStack(Items.RED_DYE))),
                helper.getLevel().registryAccess());
        helper.assertValueEqual(EquipmentDurability.quality(dyed), Quality.FINE, "Leather dyeing preserves quality");
        helper.assertValueEqual(dyed.getDamageValue(), 6, "Dyeing preserves wear");
        helper.assertValueEqual(dyed.getMaxDamage(), 24, "Fine leather retains its correct maximum");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generatedWoodAndLeatherDowngradeOnlyOnceAfterEnchanting(GameTestHelper helper) {
        TestSkeleton mob = new TestSkeleton(helper.getLevel());
        ItemStack leather = new ItemStack(Items.LEATHER_CHESTPLATE);
        ItemStack bow = new ItemStack(Items.BOW);
        mob.setItemSlot(EquipmentSlot.CHEST, leather);
        mob.setItemSlot(EquipmentSlot.MAINHAND, bow);
        mob.finishEquipment();
        helper.assertValueEqual(EquipmentDurability.quality(leather), Quality.POOR, "Generated unenchanted leather becomes poor");
        helper.assertValueEqual(EquipmentDurability.quality(bow), Quality.POOR, "Generated unenchanted wooden bow becomes poor");
        mob.finishEquipment();
        helper.assertValueEqual(EquipmentDurability.quality(leather), Quality.POOR, "Repeated hook does not degrade quality again");
        ItemStack explicit = equipment(Items.LEATHER_CHESTPLATE, Quality.FINE, 0);
        mob.setItemSlot(EquipmentSlot.CHEST, explicit);
        ItemStack enchanted = new ItemStack(Items.BOW);
        enchanted.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.POWER), 1);
        mob.setItemSlot(EquipmentSlot.MAINHAND, enchanted);
        mob.finishEquipment();
        helper.assertValueEqual(EquipmentDurability.quality(explicit), Quality.FINE, "Explicit generated quality is preserved");
        helper.assertValueEqual(EquipmentDurability.quality(enchanted), Quality.AVERAGE, "Enchanted bow is not downgraded");
        mob.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void qualityHasNoIndependentMiningAttackOrEnchantabilityMultiplier(GameTestHelper helper) {
        ItemStack ordinary = equipment(Items.IRON_PICKAXE, Quality.AVERAGE, 0);
        ItemStack fine = equipment(Items.IRON_PICKAXE, Quality.FINE, 0);
        helper.assertValueEqual(fine.getDestroySpeed(Blocks.STONE.defaultBlockState()), ordinary.getDestroySpeed(Blocks.STONE.defaultBlockState()),
                "Quality changes durability, not mining speed");
        helper.assertValueEqual(fine.getAttributeModifiers(), ordinary.getAttributeModifiers(), "Quality does not add attack modifiers");
        helper.assertValueEqual(fine.getEnchantmentValue(), ordinary.getEnchantmentValue(), "Enchantability remains a material property");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void badlyWornPoorMobWeaponsHaveTheExtraDropPenaltyButPickedUpGearDoesNot(GameTestHelper helper) {
        TestSkeleton mob = new TestSkeleton(helper.getLevel());
        mob.captureDrops(new ArrayList<>());
        mob.setDropChance(EquipmentSlot.MAINHAND, 1F);
        int[] dropped = new int[2];
        for (boolean enchanted : new boolean[]{false, true}) for (int quarter = 0; quarter < 4; quarter++)
            for (int ticket = 0; ticket < 3; ticket++) for (int enchantmentTicket = 0; enchantmentTicket < 2; enchantmentTicket++) {
                ItemStack weapon = equipment(Items.IRON_PICKAXE, Quality.POOR, 7000);
                if (enchanted) weapon.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(Enchantments.EFFICIENCY), 1);
                mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                mob.dropTestEquipment(quarter * .25F, ticket, enchantmentTicket);
                boolean expected = quarter == 3 && ticket == 0 && (!enchanted || enchantmentTicket == 0);
                helper.assertValueEqual(mob.getMainHandItem().isEmpty(), expected,
                        "Equal quality and wear apply the same original poor-wear penalty, then both independent drop gates");
                if (expected) dropped[enchanted ? 1 : 0]++;
            }
        helper.assertValueEqual(dropped[0], 2, "Unenchanted poor worn weapons keep two of twenty-four matching ticket triples");
        helper.assertValueEqual(dropped[1], 1, "Enchanted poor worn weapons keep one of the same twenty-four ticket triples");
        helper.assertValueEqual(dropped[1] * 2, dropped[0], "Equal-quality equally worn enchanted weapons drop half as often");
        mob.setDropChance(EquipmentSlot.MAINHAND, 2F);
        for (boolean enchanted : new boolean[]{false, true}) {
            ItemStack weapon = equipment(Items.IRON_PICKAXE, Quality.POOR, 7000);
            if (enchanted) weapon.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.EFFICIENCY), 1);
            mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            mob.dropTestEquipment(0F, 2, 1);
            helper.assertTrue(mob.getMainHandItem().isEmpty(), "Picked-up equipment bypasses quality and both drop reductions, enchanted=" + enchanted);
        }
        mob.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void specialUndeadWeaponsSpawnAtPoorQuality(GameTestHelper helper) {
        TestWitherSkeleton wither = new TestWitherSkeleton(helper.getLevel());
        wither.makeEquipment();
        helper.assertTrue(wither.getMainHandItem().is(Items.STONE_SWORD), "Modern wither skeleton weapon type is retained");
        helper.assertValueEqual(EquipmentDurability.quality(wither.getMainHandItem()), Quality.POOR,
                "The original wither-skeleton poor-quality override applies");
        TestZombifiedPiglin piglin = new TestZombifiedPiglin(helper.getLevel());
        piglin.makeEquipment();
        helper.assertTrue(piglin.getMainHandItem().is(Items.GOLDEN_SWORD) || piglin.getMainHandItem().is(Items.GOLDEN_PICKAXE)
                || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(piglin.getMainHandItem().getItem()).toString().equals("mite:golden_battle_axe"),
                "R196 pigman chooses sword twice, battle axe or pickaxe, with the same poor quality");
        helper.assertValueEqual(EquipmentDurability.quality(piglin.getMainHandItem()), Quality.POOR,
                "The original zombie-pigman poor-quality override applies");
        wither.discard();
        piglin.discard();
        helper.succeed();
    }

    private static Player experienced(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PlayerExperience.setTotal(player, 203000);
        return player;
    }

    private static ItemStack equipment(Item item, Quality quality, int damage) {
        ItemStack result = new ItemStack(item);
        EquipmentDurability.setQuality(result, quality);
        result.setDamageValue(damage);
        return result;
    }

    private static final class TestSkeleton extends Skeleton {
        private RandomSource controlledRandom;
        private TestSkeleton(ServerLevel level) { super(EntityType.SKELETON, level); }
        @Override public RandomSource getRandom() { return controlledRandom == null ? super.getRandom() : controlledRandom; }
        private void finishEquipment() {
            populateDefaultEquipmentEnchantments((ServerLevel)level(), RandomSource.create(42),
                    new DifficultyInstance(Difficulty.PEACEFUL, 0L, 0L, 0F));
        }
        private void dropTestEquipment(float originalRoll, int reductionTicket) {
            dropTestEquipment(originalRoll, reductionTicket, 0);
        }
        private void dropTestEquipment(float originalRoll, int reductionTicket, int enchantmentTicket) {
            controlledRandom = new LegacyRandomSource(196L) {
                @Override public float nextFloat() { return originalRoll; }
                @Override public int nextInt(int bound) {
                    if (bound == 3) return reductionTicket;
                    if (bound == 2) return enchantmentTicket;
                    throw new AssertionError("Unexpected equipment reduction bound " + bound);
                }
            };
            try { super.dropCustomDeathLoot((ServerLevel)level(), damageSources().generic(), true); }
            finally { controlledRandom = null; }
        }
    }

    private static final class TestWitherSkeleton extends WitherSkeleton {
        private TestWitherSkeleton(ServerLevel level) { super(EntityType.WITHER_SKELETON, level); }
        private void makeEquipment() {
            populateDefaultEquipmentSlots(RandomSource.create(42), new DifficultyInstance(Difficulty.PEACEFUL, 0L, 0L, 0F));
        }
    }

    private static final class TestZombifiedPiglin extends ZombifiedPiglin {
        private TestZombifiedPiglin(ServerLevel level) { super(EntityType.ZOMBIFIED_PIGLIN, level); }
        private void makeEquipment() {
            populateDefaultEquipmentSlots(RandomSource.create(42), new DifficultyInstance(Difficulty.PEACEFUL, 0L, 0L, 0F));
        }
    }
}
