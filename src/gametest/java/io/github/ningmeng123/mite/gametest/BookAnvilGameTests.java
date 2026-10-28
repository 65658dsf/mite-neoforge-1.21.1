package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.AnvilExperienceAccess;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

/** Real anvil transactions combine stored source and modern enchantments on books. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class BookAnvilGameTests {
    private BookAnvilGameTests() {}

    @GameTest(template = "empty")
    public static void everyAnvilUpgradesSourceBooksAndChargesNormalAndShiftPickupOnce(GameTestHelper helper) {
        List<Block> anvils = new ArrayList<>(List.of(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL));
        MiteFunctionalBlocks.ANVILS.values().forEach(anvil -> anvils.add(anvil.get()));
        for (Block anvil : anvils) for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, anvil)) {
            int initialWear = f.entity().damage();
            ItemStack first = book(helper, new Entry(Enchantments.UNBREAKING, 4));
            first.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 book"));
            ItemStack second = book(helper, new Entry(Enchantments.UNBREAKING, 4));
            ItemStack expected = book(helper, new Entry(Enchantments.UNBREAKING, 5));
            expected.set(DataComponents.CUSTOM_NAME, first.get(DataComponents.CUSTOM_NAME));
            expected.set(DataComponents.REPAIR_COST, 1);
            f.prepare(first, second);
            helper.assertTrue(ItemStack.matches(f.menu.getSlot(2).getItem(), expected),
                    "Unbreaking IV plus IV previews a named stored Unbreaking V book on " + anvil);
            assertFee(helper, f.menu, 8000);
            f.menu.createResult();
            f.menu.broadcastChanges();
            helper.assertValueEqual(f.entity().damage(), initialWear, "Repeated book previews preserve the anvil's existing damage stage and wear");
            helper.assertValueEqual(stored(helper, first, Enchantments.UNBREAKING), 4, "Preview leaves the first book unchanged");
            helper.assertValueEqual(stored(helper, second, Enchantments.UNBREAKING), 4, "Preview leaves the second book unchanged");
            int beforeXp = f.owner.totalExperience;
            f.menu.clicked(2, 0, click, f.owner);
            helper.assertTrue(ItemStack.matches(f.result(click), expected), "Actual pickup receives exactly the stored-enchantment preview");
            helper.assertValueEqual(f.owner.totalExperience, beforeXp - 8000, "Combining source books costs a fixed 8000 XP points once");
            helper.assertValueEqual(f.entity().damage(), initialWear + 500, "The final level V book consumes 500 additional anvil durability once");
            helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.getSlot(1).getItem().isEmpty(),
                    "Actual pickup consumes both source books once");
            int paidXp = f.owner.totalExperience;
            f.menu.clicked(2, 0, click, f.owner);
            helper.assertValueEqual(f.owner.totalExperience, paidXp, "A second exhausted-result click cannot charge XP again");
            helper.assertValueEqual(f.entity().damage(), initialWear + 500, "A second exhausted-result click cannot charge wear again");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compatibleSourceAndModernBooksMergeInEitherOrderWithStoredEntriesAndFees(GameTestHelper helper) {
        List<Merge> cases = List.of(
                new Merge(List.of(new Entry(Enchantments.EFFICIENCY, 2)), List.of(new Entry(Enchantments.UNBREAKING, 3)), 8000, 550),
                new Merge(List.of(new Entry(Enchantments.UNBREAKING, 4)), List.of(new Entry(Enchantments.MENDING, 1)), 8000, 550),
                new Merge(List.of(new Entry(Enchantments.MENDING, 1)), List.of(new Entry(Enchantments.UNBREAKING, 4)), 8000, 550),
                new Merge(List.of(new Entry(Enchantments.EFFICIENCY, 2)),
                        List.of(new Entry(Enchantments.UNBREAKING, 3), new Entry(Enchantments.MENDING, 1)), 8000, 720),
                new Merge(List.of(new Entry(Enchantments.MENDING, 1)), List.of(new Entry(Enchantments.QUICK_CHARGE, 2)), 8000, 330));
        for (Merge merge : cases) for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            f.prepare(book(helper, merge.first.toArray(Entry[]::new)), book(helper, merge.second.toArray(Entry[]::new)));
            ItemStack preview = f.menu.getSlot(2).getItem().copy();
            helper.assertTrue(preview.is(Items.ENCHANTED_BOOK), "Compatible source and modern entries produce a book in both input orders");
            assertFee(helper, f.menu, merge.points);
            helper.assertValueEqual(f.entity().damage(), 0, "A mixed-book preview has no wear");
            int beforeXp = f.owner.totalExperience;
            f.menu.clicked(2, 0, click, f.owner);
            ItemStack result = f.result(click);
            helper.assertTrue(ItemStack.matches(result, preview), "Actual pickup preserves the complete mixed-book preview");
            List<Entry> entries = new ArrayList<>(merge.first);
            entries.addAll(merge.second);
            for (Entry entry : entries) helper.assertValueEqual(stored(helper, result, entry.enchantment), entry.level,
                    "Book merging retains a compatible stored source or modern entry: " + entry.enchantment.location());
            helper.assertValueEqual(result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).size(),
                    entries.size(), "Each compatible entry appears exactly once in stored enchantments");
            helper.assertTrue(result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(),
                    "Merged book enchantments are stored for later application to equipment");
            helper.assertValueEqual(f.owner.totalExperience, beforeXp - merge.points, "Mixed-book pickup pays the fixed quoted XP point fee once");
            helper.assertValueEqual(f.entity().damage(), merge.wear, "Final stored levels and type surcharge determine mixed-book wear");
            helper.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.getSlot(1).getItem().isEmpty(),
                    "Mixed-book pickup consumes both ingredients");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cappedWeakerAndConflictingSourceBooksRejectWithoutConsumingInputsXpOrWear(GameTestHelper helper) {
        List<Merge> rejected = List.of(
                new Merge(List.of(new Entry(Enchantments.UNBREAKING, 5)), List.of(new Entry(Enchantments.UNBREAKING, 5)), 0, 0),
                new Merge(List.of(new Entry(Enchantments.EFFICIENCY, 4)), List.of(new Entry(Enchantments.EFFICIENCY, 2)), 0, 0),
                new Merge(List.of(new Entry(Enchantments.SHARPNESS, 2)), List.of(new Entry(Enchantments.SMITE, 2)), 0, 0),
                new Merge(List.of(new Entry(Enchantments.SILK_TOUCH, 1)), List.of(new Entry(Enchantments.FORTUNE, 3)), 0, 0),
                new Merge(List.of(new Entry(Enchantments.PROTECTION, 4)), List.of(new Entry(Enchantments.BLAST_PROTECTION, 4)), 0, 0),
                new Merge(List.of(new Entry(Enchantments.UNBREAKING, 2), new Entry(Enchantments.MENDING, 1)),
                        List.of(new Entry(Enchantments.INFINITY, 1)), 0, 0));
        for (Merge merge : rejected) for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            ItemStack first = book(helper, merge.first.toArray(Entry[]::new));
            ItemStack second = book(helper, merge.second.toArray(Entry[]::new));
            f.prepare(first, second);
            helper.assertTrue(f.menu.getSlot(2).getItem().isEmpty(), "Maximum, weaker and mutually exclusive source book merges are rejected");
            int beforeXp = f.owner.totalExperience;
            f.menu.clicked(2, 0, click, f.owner);
            helper.assertTrue(f.menu.getCarried().isEmpty() && f.owner.getInventory().isEmpty(), "Rejected book merging produces no item");
            helper.assertTrue(ItemStack.matches(f.menu.getSlot(0).getItem(), first)
                    && ItemStack.matches(f.menu.getSlot(1).getItem(), second), "Rejected book merging preserves both input components");
            helper.assertValueEqual(f.owner.totalExperience, beforeXp, "Rejected book merging consumes no experience");
            helper.assertValueEqual(f.entity().damage(), 0, "Rejected book merging consumes no anvil durability");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookMergesRequirePointsWhilePriorWorkAndRenamingDoNotAddFeesOrLevelLimits(GameTestHelper helper) {
        for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            ItemStack first = book(helper, new Entry(Enchantments.EFFICIENCY, 2));
            ItemStack second = book(helper, new Entry(Enchantments.EFFICIENCY, 2));
            first.set(DataComponents.REPAIR_COST, 3);
            second.set(DataComponents.REPAIR_COST, 7);
            PlayerExperience.setTotal(f.owner, 0);
            f.prepare(first, second);
            assertFee(helper, f.menu, 8000);
            helper.assertValueEqual(f.menu.getSlot(2).getItem().getOrDefault(DataComponents.REPAIR_COST, 0), 15,
                    "The combined book increases the greater prior-work penalty");
            f.menu.setItemName("NingMeng123 combined");
            assertFee(helper, f.menu, 8000);
            ItemStack expected = f.menu.getSlot(2).getItem().copy();
            helper.assertFalse(expected.isEmpty(), "An unaffordable book merge retains its priced preview");
            f.menu.clicked(2, 0, click, f.owner);
            helper.assertTrue(f.menu.getCarried().isEmpty() && f.owner.getInventory().isEmpty(), "Zero XP blocks actual normal and Shift book pickup");
            helper.assertTrue(ItemStack.matches(f.menu.getSlot(0).getItem(), first)
                    && ItemStack.matches(f.menu.getSlot(1).getItem(), second), "An unaffordable merge preserves both source books");
            helper.assertValueEqual(f.owner.totalExperience, 0, "An unaffordable book merge does not create XP debt");
            helper.assertValueEqual(f.entity().damage(), 0, "An unaffordable book merge does not damage the anvil");
            PlayerExperience.setTotal(f.owner, 10000);
            int beforeXp = f.owner.totalExperience;
            f.menu.clicked(2, 0, click, f.owner);
            helper.assertTrue(ItemStack.matches(f.result(click), expected), "Replenishing XP permits the same named book preview to be taken");
            helper.assertValueEqual(f.owner.totalExperience, beforeXp - 8000, "Named book merging costs 8000 points without prior-work or rename surcharges");
            helper.assertValueEqual(f.entity().damage(), 300, "Prior work and naming do not inflate final Efficiency III wear");
        }
        try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            ItemStack first = book(helper, new Entry(Enchantments.EFFICIENCY, 2));
            ItemStack second = book(helper, new Entry(Enchantments.EFFICIENCY, 2));
            first.set(DataComponents.REPAIR_COST, 31);
            second.set(DataComponents.REPAIR_COST, 6);
            f.prepare(first, second);
            assertFee(helper, f.menu, 8000);
            helper.assertFalse(f.menu.getSlot(2).getItem().isEmpty(), "Old prior-work penalties cannot hide a point-priced book result");
            f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.owner);
            helper.assertValueEqual(stored(helper, f.result(ClickType.QUICK_MOVE), Enchantments.EFFICIENCY), 3,
                    "Survival Shift-pickup obtains a book despite an old forty-level prior-work quote");
            helper.assertValueEqual(f.owner.totalExperience, 2000, "Prior-work penalties do not inflate the fixed 8000 point book charge");
            helper.assertValueEqual(f.entity().damage(), 300, "The formerly too-expensive operation retains normal final-level wear");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookMergingSpendsExactPointsAndRecalculatesTheRemainingNaturalLevel(GameTestHelper helper) {
        for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                try (Fixture f = new Fixture(helper, Blocks.ANVIL)) {
            ItemStack first = book(helper, new Entry(Enchantments.EFFICIENCY, 2));
            ItemStack second = book(helper, new Entry(Enchantments.EFFICIENCY, 2));
            first.set(DataComponents.REPAIR_COST, 1);
            second.set(DataComponents.REPAIR_COST, 7);
            PlayerExperience.setTotal(f.owner, 8000);
            f.prepare(first, second);
            assertFee(helper, f.menu, 8000);
            helper.assertTrue(f.menu.getSlot(2).mayPickup(f.owner), "An exact 8000 point balance can fund the fixed book quote");
            f.menu.clicked(2, 0, click, f.owner);
            helper.assertValueEqual(stored(helper, f.result(click), Enchantments.EFFICIENCY), 3,
                    "Normal and Shift pickup obtain the actual combined book");
            helper.assertValueEqual(f.owner.totalExperience, 0, "The complete 8000 point balance pays the fixed book fee exactly");
            helper.assertValueEqual(f.owner.experienceLevel, 0, "The remaining zero point balance recalculates the player's natural level");
            helper.assertValueEqual(f.entity().damage(), 300, "Fixed point charging leaves final-level anvil wear independent");
        }
        helper.succeed();
    }

    private static void assertFee(GameTestHelper helper, AnvilMenu menu, int points) {
        helper.assertValueEqual(menu.getCost(), points, "The native menu quote carries the complete fixed XP point price");
        AnvilExperienceAccess experience = (AnvilExperienceAccess) menu;
        helper.assertTrue(experience.mite$usesExperiencePoints(), "Actual book operations expose their experience point payment mode to the UI");
        helper.assertValueEqual(experience.mite$experienceCost(), points, "The UI accessor exposes the complete fixed XP point fee");
    }

    private static ItemStack book(GameTestHelper helper, Entry... entries) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Entry entry : entries) stored.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(entry.enchantment), entry.level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return stack;
    }

    private static int stored(GameTestHelper helper, ItemStack book, ResourceKey<Enchantment> enchantment) {
        return EnchantmentHelper.getEnchantmentsForCrafting(book).getLevel(helper.getLevel().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(enchantment));
    }

    private record Entry(ResourceKey<Enchantment> enchantment, int level) {}
    private record Merge(List<Entry> first, List<Entry> second, int points, int wear) {}

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
            PlayerExperience.setTotal(owner, 10000);
            menu = new AnvilMenu(71, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        }

        MaterialAnvilBlockEntity entity() { return (MaterialAnvilBlockEntity) helper.getLevel().getBlockEntity(pos); }

        void prepare(ItemStack first, ItemStack second) {
            menu.getSlot(1).set(ItemStack.EMPTY);
            menu.setItemName("");
            menu.getSlot(0).set(first);
            menu.setItemName(first.getHoverName().getString());
            menu.getSlot(1).set(second);
        }

        ItemStack result(ClickType click) {
            if (click == ClickType.PICKUP) return menu.getCarried();
            ItemStack result = ItemStack.EMPTY;
            int count = 0;
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                ItemStack stack = owner.getInventory().getItem(slot);
                if (stack.is(Items.ENCHANTED_BOOK)) { result = stack; count += stack.getCount(); }
            }
            helper.assertValueEqual(count, 1, "Shift-pickup transfers exactly one combined enchanted book");
            helper.assertTrue(menu.getCarried().isEmpty(), "Shift-pickup keeps the cursor empty");
            return result;
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
