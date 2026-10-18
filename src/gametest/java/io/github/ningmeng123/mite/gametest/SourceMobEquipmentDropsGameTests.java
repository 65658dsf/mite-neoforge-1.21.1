package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent draws after the R196 equipment gates, preserving player equipment. @author NingMeng123 */
@GameTestHolder("mite_equipment_drop")
@PrefixGameTestTemplate(false)
public final class SourceMobEquipmentDropsGameTests {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS,
            EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND, EquipmentSlot.BODY};
    private static final Item[] ITEMS = {Items.IRON_SWORD, Items.IRON_BOOTS, Items.IRON_LEGGINGS,
            Items.IRON_CHESTPLATE, Items.IRON_HELMET, Items.SHIELD, Items.WOLF_ARMOR};

    private SourceMobEquipmentDropsGameTests() {}

    @GameTest(template = "empty")
    public static void recentlyKilledSourceHeldWeaponKeepsExactlyOneOfThreeIndependentTickets(GameTestHelper helper) {
        int kept = 0;
        for (int ticket = 0; ticket < 3; ticket++) try (Fixture f = new Fixture(helper, 0F, ticket)) {
            ItemStack weapon = equipment(Items.IRON_SWORD, Quality.FINE, 37);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            f.drop(.085F, true, false, false);
            boolean expected = ticket == 0;
            helper.assertValueEqual(f.drops.size(), expected ? 1 : 0,
                    "The formerly guaranteed recently killed R196 held item keeps one of three tickets");
            helper.assertValueEqual(f.mob.getMainHandItem().isEmpty(), expected, "Only a successful drop clears the held slot");
            if (expected) {
                kept++;
                helper.assertTrue(ItemStack.matches(f.drops.getFirst().getItem(), weapon),
                        "The actual captured item retains its fine quality, damage and components");
                helper.assertValueEqual(f.drops.getFirst().getItem().getDamageValue(), 37, "No drop-time wear reroll");
            }
            f.random.done(helper, 1);
        }
        helper.assertValueEqual(kept, 1, "One retained ticket out of three is exactly the requested multiplier");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceArmorOffhandAndBodyEachHaveTheirOwnIndependentReductionDraw(GameTestHelper helper) {
        int[] tickets = {0, 1, 2, 0, 1, 2, 0};
        try (Fixture f = new Fixture(helper, 0F, tickets)) {
            List<ItemStack> original = new ArrayList<>();
            for (int i = 0; i < SLOTS.length; i++) {
                ItemStack stack = equipment(ITEMS[i], Quality.FINE, i + 10);
                original.add(stack);
                f.mob.setItemSlot(SLOTS[i], stack);
            }
            f.drop(.085F, true, false, false);
            helper.assertValueEqual(f.drops.size(), 3, "Each eligible slot consumes its own one-in-three draw");
            for (int i = 0; i < SLOTS.length; i++) {
                boolean expected = tickets[i] == 0;
                helper.assertValueEqual(f.mob.getItemBySlot(SLOTS[i]).isEmpty(), expected,
                        "The result belongs to the correct independent equipment slot " + SLOTS[i]);
                ItemStack stack = original.get(i);
                helper.assertValueEqual(f.drops.stream().filter(e -> ItemStack.matches(e.getItem(), stack)).count(),
                        expected ? 1L : 0L, "Successful per-slot drops retain the original stack " + SLOTS[i]);
            }
            f.random.done(helper, SLOTS.length);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickedUpEquipmentInEverySourceSlotBypassesReductionForAllDeathModes(GameTestHelper helper) {
        for (boolean recent : new boolean[]{false, true}) for (boolean massiveFall : new boolean[]{false, true})
            try (Fixture f = new Fixture(helper, 0F)) {
                for (int i = 0; i < SLOTS.length; i++) {
                    ItemStack stack = equipment(ITEMS[i], Quality.POOR, 0);
                    stack.setDamageValue(stack.getMaxDamage() - 2);
                    f.mob.setItemSlot(SLOTS[i], stack);
                }
                f.drop(2F, recent, massiveFall, false);
                helper.assertValueEqual(f.drops.size(), SLOTS.length,
                        "Picked-up worn poor equipment retains every slot for recent=" + recent + " fall=" + massiveFall);
                for (EquipmentSlot slot : SLOTS) helper.assertTrue(f.mob.getItemBySlot(slot).isEmpty(),
                        "The picked-up stack returns to the player from " + slot);
                f.random.done(helper, 0);
            }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectedOriginalArmorEnvironmentalAndFallRollsNeverDrawTheNewGate(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, .9F)) {
            f.mob.setItemSlot(EquipmentSlot.HEAD, equipment(Items.IRON_HELMET, Quality.FINE, 1));
            f.drop(.085F, true, false, false);
            helper.assertTrue(f.drops.isEmpty(), "A failed original armor chance remains a failed drop");
            f.random.done(helper, 0);
        }
        try (Fixture f = new Fixture(helper, .9F)) {
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, equipment(Items.IRON_SWORD, Quality.FINE, 1));
            f.drop(.085F, false, false, false);
            helper.assertTrue(f.drops.isEmpty(), "The original environmental held-equipment gate remains effective");
            f.random.done(helper, 0);
        }
        try (Fixture f = new Fixture(helper, 0F)) {
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, equipment(Items.IRON_SWORD, Quality.FINE, 1));
            f.drop(.085F, true, true, false);
            helper.assertTrue(f.drops.isEmpty(), "The original massive-fall equipment penalty remains effective");
            f.random.done(helper, 0);
        }
        try (Fixture f = new Fixture(helper, .9F)) {
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, equipment(Items.IRON_SWORD, Quality.FINE, 1));
            f.drop(.085F, true, false, true);
            helper.assertTrue(f.drops.isEmpty(), "Longdead non-poor held items retain their original armor-like gate");
            f.random.done(helper, 0);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poorWearAndEnchantmentExemptionStillPrecedeTheOneThirdDraw(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, .5F)) {
            ItemStack poor = equipment(Items.IRON_PICKAXE, Quality.POOR, 7000);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, poor);
            f.drop(1F, true, false, false);
            helper.assertTrue(f.drops.isEmpty() && f.mob.getMainHandItem() == poor,
                    "Badly worn poor weapons still fail the original seventy-five-percent penalty first");
            f.random.done(helper, 0);
        }
        try (Fixture f = new Fixture(helper, .9F, 0)) {
            ItemStack poor = equipment(Items.IRON_PICKAXE, Quality.POOR, 7000);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, poor);
            f.drop(1F, true, false, false);
            helper.assertTrue(f.drops.size() == 1 && ItemStack.matches(f.drops.getFirst().getItem(), poor),
                    "Passing both original quality and new reduction gates preserves the original poor worn stack");
            f.random.done(helper, 1);
        }
        for (int ticket = 0; ticket < 3; ticket++) try (Fixture f = new Fixture(helper, 0F, ticket)) {
            ItemStack enchanted = equipment(Items.IRON_PICKAXE, Quality.POOR, 7000);
            enchanted.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.EFFICIENCY), 1);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, enchanted);
            f.drop(1F, true, false, false);
            helper.assertValueEqual(f.drops.size(), ticket == 0 ? 1 : 0,
                    "Enchantment exempts the original poor-wear penalty while retaining the new one-third draw");
            f.random.done(helper, 1);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanishingCurseStillPreventsSourceAndPickedUpEquipmentDrops(GameTestHelper helper) {
        for (float chance : new float[]{.085F, 2F}) try (Fixture f = new Fixture(helper, 0F)) {
            ItemStack cursed = equipment(Items.IRON_SWORD, Quality.FINE, 37);
            cursed.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.VANISHING_CURSE), 1);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, cursed);
            f.drop(chance, true, false, false);
            helper.assertTrue(f.drops.isEmpty(), "The original curse excludes both source and picked-up equipment");
            f.random.done(helper, 0);
        }
        helper.succeed();
    }

    private static ItemStack equipment(Item item, Quality quality, int damage) {
        ItemStack stack = new ItemStack(item);
        EquipmentDurability.setQuality(stack, quality);
        stack.setDamageValue(damage);
        return stack;
    }

    private static final class Fixture implements AutoCloseable {
        private final ControlledSkeleton mob;
        private final List<ItemEntity> drops = new ArrayList<>();
        private final TicketRandom random;

        private Fixture(GameTestHelper helper, float oldRoll, int... tickets) {
            mob = new ControlledSkeleton(helper.getLevel());
            random = new TicketRandom(oldRoll, tickets);
            mob.controlledRandom = random;
            mob.captureDrops(drops);
        }
        private void drop(float chance, boolean recent, boolean massiveFall, boolean longdead) {
            R196MobEquipment.dropEquipment(mob, mob.damageSources().generic(), recent, massiveFall, longdead, slot -> chance);
        }
        @Override public void close() {
            mob.controlledRandom = null;
            mob.captureDrops(null);
            drops.forEach(ItemEntity::discard);
            mob.discard();
        }
    }

    private static final class ControlledSkeleton extends Skeleton {
        private RandomSource controlledRandom;
        private ControlledSkeleton(ServerLevel level) { super(EntityType.SKELETON, level); }
        @Override public RandomSource getRandom() { return controlledRandom == null ? super.getRandom() : controlledRandom; }
    }

    private static final class TicketRandom extends LegacyRandomSource {
        private final float oldRoll;
        private final ArrayDeque<Integer> tickets = new ArrayDeque<>();
        private int draws;

        private TicketRandom(float oldRoll, int... values) {
            super(196L);
            this.oldRoll = oldRoll;
            for (int value : values) tickets.addLast(value);
        }
        @Override public float nextFloat() { return oldRoll; }
        @Override public int nextInt(int bound) {
            if (bound != 3 || tickets.isEmpty()) throw new AssertionError("Unexpected source equipment draw nextInt(" + bound + ")");
            draws++;
            return tickets.removeFirst();
        }
        private void done(GameTestHelper helper, int expectedDraws) {
            helper.assertTrue(tickets.isEmpty(), "Every independently scripted source equipment ticket was consumed");
            helper.assertValueEqual(draws, expectedDraws, "New reduction draws occur only after the original gates pass");
        }
    }
}
