package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Player enchanting and real anvil transactions can produce Protection V. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ProtectionFiveGameTests {
    private ProtectionFiveGameTests() {}

    @GameTest(template = "empty")
    public static void fullPowerEnchantingProducesProtectionFiveBooks(GameTestHelper helper) {
        var protection = protection(helper);
        helper.assertValueEqual(protection.value().getMaxLevel(), 5, "Registered Protection supports level V");
        for (var key : java.util.List.of(Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION))
            helper.assertValueEqual(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(key).value().getMaxLevel(), 4, "Other protection enchantments retain level IV");
        int energy = R196EnchantmentTable.energy(new ItemStack(Items.BOOK), 24, false, 2, .5F);
        var random = RandomSource.create(196L);
        boolean found = false;
        for (int draw = 0; draw < 512; draw++) {
            ItemStack output = R196EnchantmentTable.output(helper.getLevel().registryAccess(), new ItemStack(Items.BOOK), energy, random);
            helper.assertTrue(output.is(Items.ENCHANTED_BOOK), "Player enchanting produces an enchanted book");
            var stored = output.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            helper.assertTrue(stored.size() >= 1, "Native table book trimming retains at least one enchantment");
            helper.assertTrue(stored.getLevel(protection) <= 5, "Player enchanting never exceeds Protection V");
            found |= stored.getLevel(protection) == 5;
        }
        helper.assertTrue(found, "A fixed sequence at reachable table power produces Protection V books");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionFourBooksMergeIntoFiveWithNormalAndShiftPickup(GameTestHelper helper) {
        for (boolean shift : new boolean[] {false, true}) {
            var owner = owner(helper, 0);
            var menu = anvil(helper, owner);
            menu.getSlot(0).set(book(helper, 4));
            menu.getSlot(1).set(book(helper, 4));
            var preview = menu.getSlot(2).getItem().copy();
            helper.assertTrue(preview.is(Items.ENCHANTED_BOOK), "Two Protection IV books preview an enchanted book");
            helper.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(preview).getLevel(protection(helper)), 5,
                    "Protection IV plus IV creates a stored Protection V enchantment");
            helper.assertValueEqual(menu.getCost(), 5, "A fresh Protection V merge costs five player levels");
            menu.clicked(2, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP, owner);
            helper.assertTrue(menu.getCarried().isEmpty() && !menu.getSlot(0).getItem().isEmpty()
                    && !menu.getSlot(1).getItem().isEmpty(), "Insufficient XP cannot consume either book or take the result");
            helper.assertFalse(owner.getInventory().items.stream().anyMatch(stack -> ItemStack.matches(stack, preview)),
                    "Insufficient XP cannot insert the result into the inventory through shift pickup");
            PlayerExperience.setTotal(owner, 10000);
            int beforeLevel = owner.experienceLevel;
            menu.clicked(2, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP, owner);
            boolean obtained = shift ? owner.getInventory().items.stream().anyMatch(stack -> ItemStack.matches(stack, preview))
                    : ItemStack.matches(menu.getCarried(), preview);
            helper.assertTrue(obtained, "Funded normal and shift pickup receive the Protection V book");
            helper.assertValueEqual(owner.experienceLevel, beforeLevel - 5, "The completed merge charges five levels once");
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "The merge consumes both books");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionBookMergingRetainsOtherEnchantmentsOnTheLeftBook(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        var menu = anvil(helper, owner);
        var unbreaking = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
        ItemStack first = book(helper, 4);
        var stored = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(first));
        stored.set(unbreaking, 4);
        first.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        menu.getSlot(0).set(first);
        menu.getSlot(1).set(book(helper, 4));
        helper.assertValueEqual(menu.getCost(), 5, "Preserved entries do not add a second transfer charge");
        menu.clicked(2, 0, ClickType.PICKUP, owner);
        var result = EnchantmentHelper.getEnchantmentsForCrafting(menu.getCarried());
        helper.assertValueEqual(result.getLevel(protection(helper)), 5, "The protection entry upgrades to V");
        helper.assertValueEqual(result.getLevel(unbreaking), 4, "The left book retains Unbreaking IV");
        helper.assertValueEqual(result.size(), 2, "Both stored enchantments survive actual pickup");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionFiveBooksApplyToArmorAndAllPlayerUpgradesStopAtFive(GameTestHelper helper) {
        var owner = owner(helper, 10000);
        var menu = anvil(helper, owner);
        for (boolean upgrade : new boolean[] {false, true}) {
            ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
            if (upgrade) armor.enchant(protection(helper), 4);
            menu.getSlot(0).set(armor);
            menu.getSlot(1).set(book(helper, upgrade ? 4 : 5));
            helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(protection(helper), menu.getSlot(2).getItem()), 5,
                    "A V book transfer and IV armor plus IV book both reach Protection V");
            menu.clicked(2, 0, ClickType.PICKUP, owner);
            ItemStack result = menu.getCarried().copy();
            menu.setCarried(ItemStack.EMPTY);
            helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(protection(helper), result), 5, "Actual pickup retains Protection V");
            menu.getSlot(0).set(result);
            menu.getSlot(1).set(book(helper, 5));
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "V armor plus a V book cannot produce level VI");
        }
        menu.getSlot(0).set(book(helper, 5));
        menu.getSlot(1).set(book(helper, 5));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Two Protection V books cannot produce level VI");
        helper.succeed();
    }

    private static Holder<Enchantment> protection(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
    }

    private static ItemStack book(GameTestHelper helper, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(protection(helper), level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return stack;
    }

    private static ServerPlayer owner(GameTestHelper helper, int points) {
        var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ProtectionFive"));
        owner.setGameMode(GameType.SURVIVAL);
        owner.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)).getCenter());
        PlayerExperience.setTotal(owner, points);
        return owner;
    }

    private static AnvilMenu anvil(GameTestHelper helper, ServerPlayer owner) {
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(pos, Blocks.ANVIL.defaultBlockState(), 2);
        return new AnvilMenu(1, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
    }
}
