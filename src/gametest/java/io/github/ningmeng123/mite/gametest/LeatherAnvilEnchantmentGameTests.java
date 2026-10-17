package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical anvil transactions cover the reported leather chestplate plus Unbreaking IV failure. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class LeatherAnvilEnchantmentGameTests {
    private static final List<Item> LEATHER_ARMOR = List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
            Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);

    private LeatherAnvilEnchantmentGameTests() {}

    @GameTest(template = "empty")
    public static void everyAnvilAppliesUnbreakingFourToAllLeatherSlotsThroughBothPickupModes(GameTestHelper helper) {
        for (Block anvil : anvils()) for (Item item : LEATHER_ARMOR)
                for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE)) {
            try (Fixture f = new Fixture(helper, anvil)) {
                ItemStack input = leather(item);
                ItemStack unchanged = input.copy();
                int wear = f.entity().damage();
                BlockState state = helper.getLevel().getBlockState(f.pos);
                f.input(input);
                f.menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 4));
                ItemStack preview = f.menu.getSlot(2).getItem();
                helper.assertFalse(preview.isEmpty(), "Unbreaking IV produces leather armor on " + anvil + " via " + click + ": " + item);
                assertTransferred(helper, unchanged, preview, 4);
                helper.assertTrue(ItemStack.matches(input, unchanged), "Preview never mutates the source leather armor");
                helper.assertValueEqual(f.menu.getCost(), 0, "Source book transfer is available at zero XP");
                helper.assertTrue(f.menu.getSlot(2).mayPickup(f.owner), "A zero-XP survival player may take fine-quality enchanted leather");

                f.menu.clicked(2, 0, click, f.owner);
                ItemStack result = click == ClickType.PICKUP ? f.menu.getCarried() : f.inventoryResult(item);
                assertTransferred(helper, unchanged, result, 4);
                helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "A real pickup consumes the original armor exactly once");
                helper.assertTrue(f.menu.getSlot(1).getItem().isEmpty(), "A real pickup consumes the Unbreaking IV book");
                helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "No duplicate enchanted result remains after pickup");
                helper.assertValueEqual(f.owner.totalExperience, 0, "Applying a source book charges no XP points");
                helper.assertValueEqual(f.owner.experienceLevel, 0, "Applying a source book charges no levels");
                helper.assertValueEqual(f.entity().damage(), wear, "Enchantment does not add anvil wear");
                helper.assertValueEqual(helper.getLevel().getBlockState(f.pos), state, "Enchantment preserves the physical anvil material and damage stage");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leatherBookTransferCapsUnbreakingAtFiveWithoutChangingTheTablePool(GameTestHelper helper) {
        for (Item item : LEATHER_ARMOR) try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            ItemStack input = leather(item);
            helper.assertFalse(R196EnchantmentCatalog.supported("unbreaking", input),
                    "Leather remains outside the historical Unbreaking enchanting-table pool");
            f.input(input);
            f.menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 200));
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "An overlevel book still produces legal leather equipment");
            assertTransferred(helper, input, f.menu.getSlot(2).getItem(), 5);
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            assertTransferred(helper, input, f.menu.getCarried(), 5);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leatherExceptionRejectsWrongEnchantmentsAndAnimalArmor(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            f.input(leather(Items.LEATHER_CHESTPLATE));
            f.menu.getSlot(1).set(book(helper, Enchantments.EFFICIENCY, 4));
            helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "Efficiency cannot enchant a leather chestplate");

            for (Item animalArmor : List.of(Items.LEATHER_HORSE_ARMOR, Items.WOLF_ARMOR)) {
                f.input(new ItemStack(animalArmor));
                f.menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 4));
                helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "The leather player-armor exception does not include animal armor: " + animalArmor);
            }

            f.input(leather(Items.LEATHER_CHESTPLATE));
            f.menu.getSlot(1).set(book(helper, Enchantments.PROTECTION, 3));
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "Existing supported protection books still enchant leather");
            helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, Enchantments.PROTECTION), f.menu.getSlot(2).getItem()),
                    3, "Existing leather protection level is preserved");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blastProtectionFourAdamantiumLeggingsAcceptUnbreakingFourOnEveryAnvil(GameTestHelper helper) {
        Item leggings = MiteArmorContent.get("mite:adamantium_leggings");
        for (Block anvil : anvils()) for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, anvil)) {
            ItemStack input = new ItemStack(leggings);
            EquipmentDurability.setQuality(input, Quality.FINE);
            input.setDamageValue(17);
            input.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 adamantium"));
            input.enchant(enchantment(helper, Enchantments.BLAST_PROTECTION), 4);
            ItemStack original = input.copy();
            ItemStack expected = original.copy();
            expected.enchant(enchantment(helper, Enchantments.UNBREAKING), 4);
            int wear = f.entity().damage();
            f.input(input);
            f.menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 4));
            helper.assertTrue(ItemStack.matches(f.menu.getSlot(2).getItem(), expected),
                    "Existing Blast Protection IV adamantium leggings gain Unbreaking IV without losing any components on " + anvil);
            helper.assertTrue(ItemStack.matches(input, original), "Adding a book never mutates existing enchantments in the input preview");
            helper.assertValueEqual(f.menu.getCost(), 0, "Compatible source-book additions retain zero XP cost");
            f.menu.clicked(2, 0, click, f.owner);
            ItemStack result = click == ClickType.PICKUP ? f.menu.getCarried() : f.inventoryResult(leggings);
            helper.assertTrue(ItemStack.matches(result, expected), "Actual pickup preserves Blast Protection IV alongside Unbreaking IV");
            helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.getSlot(1).getItem().isEmpty(),
                    "The actual compatible-book transaction consumes the leggings and book once");
            helper.assertValueEqual(f.owner.totalExperience, 0, "Adding compatible source enchantments at zero XP incurs no debt");
            helper.assertValueEqual(f.entity().damage(), wear, "Adding compatible source enchantments causes no anvil wear");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compatibleSourceLevelsMergeButConflictsAndMaximumLevelNoOpsConsumeNothing(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            ItemStack input = leather(Items.LEATHER_CHESTPLATE);
            input.enchant(enchantment(helper, Enchantments.PROTECTION), 3);
            input.enchant(enchantment(helper, Enchantments.UNBREAKING), 4);
            ItemStack expected = input.copy();
            expected.enchant(enchantment(helper, Enchantments.UNBREAKING), 5);
            f.input(input);
            f.menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 4));
            helper.assertTrue(ItemStack.matches(f.menu.getSlot(2).getItem(), expected),
                    "Unbreaking IV plus IV reaches source level V and retains existing Protection III");
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            helper.assertTrue(ItemStack.matches(f.menu.getCarried(), expected), "Actual pickup obtains the upgraded source level V");
            f.menu.setCarried(ItemStack.EMPTY);

            for (int bookLevel : new int[] {1, 4, 5, 200}) {
                f.input(expected.copy());
                f.menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, bookLevel));
                helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "A capped Unbreaking V target rejects a no-change book of level " + bookLevel);
                f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
                helper.assertTrue(f.menu.getCarried().isEmpty(), "A capped source enchantment cannot produce a duplicate result");
                helper.assertTrue(ItemStack.matches(f.menu.getSlot(0).getItem(), expected), "Rejected capped transfers preserve all existing armor components");
                helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 1, "Rejected capped transfers do not consume the book");
            }

            ItemStack conflicting = new ItemStack(MiteArmorContent.get("mite:adamantium_leggings"));
            conflicting.enchant(enchantment(helper, Enchantments.BLAST_PROTECTION), 4);
            f.input(conflicting);
            f.menu.getSlot(1).set(book(helper, Enchantments.PROTECTION, 4));
            helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "Protection cannot be added over existing Blast Protection IV");
            f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.owner);
            helper.assertTrue(ItemStack.matches(f.menu.getSlot(0).getItem(), conflicting), "A conflicting shift-click retains the enchanted armor");
            helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 1, "A conflicting shift-click retains the book");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceBooksPreserveModernEnchantmentsAndModernBooksKeepNativePayment(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            ItemStack input = leather(Items.LEATHER_CHESTPLATE);
            input.enchant(enchantment(helper, Enchantments.MENDING), 1);
            ItemStack expected = input.copy();
            expected.enchant(enchantment(helper, Enchantments.UNBREAKING), 4);
            f.input(input);
            f.menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 4));
            helper.assertTrue(ItemStack.matches(f.menu.getSlot(2).getItem(), expected),
                    "Adding a source book preserves an existing modern Mending enchantment");
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            helper.assertTrue(ItemStack.matches(f.menu.getCarried(), expected), "The actual source transfer retains Mending");
            f.menu.setCarried(ItemStack.EMPTY);

            input = leather(Items.LEATHER_CHESTPLATE);
            input.enchant(enchantment(helper, Enchantments.UNBREAKING), 4);
            f.input(input);
            f.menu.getSlot(1).set(book(helper, Enchantments.MENDING, 1));
            ItemStack preview = f.menu.getSlot(2).getItem().copy();
            helper.assertFalse(preview.isEmpty(), "A modern Mending book can still enchant equipment with existing source Unbreaking");
            helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, Enchantments.MENDING), preview), 1,
                    "The modern path adds Mending I");
            helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, Enchantments.UNBREAKING), preview), 4,
                    "The modern path preserves source Unbreaking IV");
            helper.assertTrue(f.menu.getCost() > 0, "Modern-only books retain native XP payment even when the target has source enchantments");
            helper.assertFalse(f.menu.getSlot(2).mayPickup(f.owner), "A zero-XP player cannot take a modern result for free");
            PlayerExperience.setTotal(f.owner, 10000);
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            helper.assertTrue(ItemStack.matches(f.menu.getCarried(), preview), "A funded player can take the modern result through the real menu");
            helper.assertTrue(f.owner.totalExperience < 10000, "The modern transaction actually charges XP");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookAppliedUnbreakingFourActuallyReducesLeatherDurabilityConsumption(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            f.input(leather(Items.LEATHER_CHESTPLATE));
            f.menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 4));
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "The reported leather chestplate plus Unbreaking IV has an actual result");
            f.menu.clicked(2, 0, ClickType.PICKUP, f.owner);
            ItemStack enchanted = f.menu.getCarried();
            int plainWear = EnchantmentHelper.processDurabilityChange(helper.getLevel(), leather(Items.LEATHER_CHESTPLATE), 1000);
            int enchantedWear = EnchantmentHelper.processDurabilityChange(helper.getLevel(), enchanted, 1000);
            helper.assertValueEqual(plainWear, 1000, "Unenchanted leather does not discard incoming durability damage");
            // IV independently saves 60% of points. This wide interval distinguishes the source effect
            // from a missing enchantment and the native armor penalty, without reseeding shared world RNG.
            helper.assertTrue(enchantedWear >= 250 && enchantedWear <= 550,
                    "The actual applied IV enchantment saves roughly 60 percent of leather wear; consumed=" + enchantedWear);
        }
        helper.succeed();
    }

    private static List<Block> anvils() {
        List<Block> blocks = new ArrayList<>(List.of(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL));
        MiteFunctionalBlocks.ANVILS.values().forEach(anvil -> blocks.add(anvil.get()));
        return blocks;
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment(helper, key), level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }

    private static ItemStack leather(Item item) {
        ItemStack stack = new ItemStack(item);
        EquipmentDurability.setQuality(stack, Quality.FINE);
        stack.setDamageValue(17);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 leather"));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x7BCB42, false));
        return stack;
    }

    private static void assertTransferred(GameTestHelper helper, ItemStack original, ItemStack result, int level) {
        helper.assertTrue(result.is(original.getItem()), "Transfer preserves the leather equipment type");
        helper.assertValueEqual(result.getCount(), 1, "Transfer yields exactly one item");
        helper.assertValueEqual(result.getDamageValue(), original.getDamageValue(), "Transfer preserves existing leather wear");
        helper.assertValueEqual(result.getMaxDamage(), original.getMaxDamage(), "Transfer preserves quality-scaled maximum durability");
        helper.assertValueEqual(EquipmentDurability.quality(result), Quality.FINE, "Transfer preserves fine quality at zero XP");
        helper.assertValueEqual(result.get(DataComponents.CUSTOM_NAME), original.get(DataComponents.CUSTOM_NAME), "Transfer preserves the custom name");
        helper.assertValueEqual(result.get(DataComponents.DYED_COLOR), original.get(DataComponents.DYED_COLOR), "Transfer preserves dye color and tooltip preference");
        helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(enchantment(helper, Enchantments.UNBREAKING), result),
                level, "Transfer retains the expected source Unbreaking level");
        helper.assertValueEqual(result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size(),
                1, "No unrelated enchantment is added to the result");
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final Player owner;
        final BlockPos pos;
        final AnvilMenu menu;
        final BlockState original;
        final BlockState below;

        Fixture(GameTestHelper helper, Block anvil) {
            this.helper = helper;
            pos = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(pos);
            below = helper.getLevel().getBlockState(pos.below());
            helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, anvil.defaultBlockState(), 3);
            owner = helper.makeMockPlayer(GameType.SURVIVAL);
            owner.moveTo(pos.getCenter());
            PlayerExperience.setTotal(owner, 0);
            menu = new AnvilMenu(71, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        }

        MaterialAnvilBlockEntity entity() {
            return (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos);
        }

        void input(ItemStack stack) {
            menu.getSlot(0).set(stack);
            // The real anvil screen sends the populated name textbox after inserting an item.
            menu.setItemName(stack.getHoverName().getString());
        }

        ItemStack inventoryResult(Item item) {
            ItemStack found = ItemStack.EMPTY;
            int count = 0;
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                ItemStack stack = owner.getInventory().getItem(slot);
                if (stack.is(item)) { found = stack; count += stack.getCount(); }
            }
            helper.assertValueEqual(count, 1, "Shift-click inserts exactly one enchanted leather item into the inventory");
            helper.assertTrue(menu.getCarried().isEmpty(), "Shift-click leaves the cursor empty");
            return found;
        }

        @Override public void close() {
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            owner.getInventory().clearContent();
            helper.getLevel().setBlock(pos, original, 3);
            helper.getLevel().setBlock(pos.below(), below, 3);
        }
    }
}
