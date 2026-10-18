package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
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
    public static void naturalMithrilAndHigherEquipmentNeverDropsFromAnySourceSlot(GameTestHelper helper) {
        for (Item item : HighTierEquipmentFixtures.items()) for (EquipmentSlot slot : SLOTS)
            for (boolean enchanted : new boolean[]{false, true}) try (Fixture f = new Fixture(helper, 0F)) {
                ItemStack stack = equipment(item, Quality.FINE, 37);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("iron equipment"));
                if (enchanted) enchant(helper, stack);
                f.mob.setItemSlot(slot, stack);
                f.drop(1F, true, false, false);
                helper.assertTrue(f.drops.isEmpty(), "Renaming cannot bypass the permanent natural high-tier ban: " + item + " / " + slot);
                helper.assertTrue(f.mob.getItemBySlot(slot) == stack, "A rejected high-tier drop retains the exact slot stack");
                f.random.done(helper, 0);
            }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickedUpHighTierEquipmentReturnsUnchangedFromEverySourceSlot(GameTestHelper helper) {
        for (Item item : HighTierEquipmentFixtures.items()) for (EquipmentSlot slot : SLOTS)
            for (boolean enchanted : new boolean[]{false, true}) for (boolean recent : new boolean[]{false, true})
                try (Fixture f = new Fixture(helper, .99F)) {
                    ItemStack stack = equipment(item, Quality.POOR, 37);
                    if (enchanted) enchant(helper, stack);
                    ItemStack expected = stack.copy();
                    f.mob.setItemSlot(slot, stack);
                    f.drop(2F, recent, true, false);
                    helper.assertTrue(f.drops.size() == 1 && ItemStack.matches(f.drops.getFirst().getItem(), expected),
                            "Picked-up high-tier equipment preserves the original guaranteed drop: " + item + " / " + slot);
                    helper.assertTrue(f.mob.getItemBySlot(slot).isEmpty(), "Returned picked-up high-tier equipment clears its source slot");
                    f.random.done(helper, 0);
                }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceLootingCannotOvercomeTheNaturalHighTierBan(GameTestHelper helper) {
        Player killer = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.LOOTING), 3);
        killer.setItemSlot(EquipmentSlot.MAINHAND, sword);
        DamageSource source = killer.damageSources().playerAttack(killer);
        for (Item item : HighTierEquipmentFixtures.items()) for (EquipmentSlot slot : SLOTS)
            try (Fixture f = new Fixture(helper, .99F)) {
                ItemStack stack = equipment(item, Quality.FINE, 37);
                f.mob.setItemSlot(slot, stack);
                f.drop(source, .99F, true, false, false);
                helper.assertTrue(f.drops.isEmpty(), "Looting cannot turn natural high-tier gear into a guaranteed pickup: " + item + " / " + slot);
                f.random.done(helper, 0);
            }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ancientMetalEquipmentAndHighTierRawMaterialsRetainBothProbabilityGates(GameTestHelper helper) {
        for (String id : List.of("mite:ancient_metal_sword", "mite:ancient_metal_pickaxe", "mite:ancient_metal_bow",
                "mite:ancient_metal_shield", "mite:ancient_metal_chainmail_chestplate", "mite:mithril_ingot", "mite:mithril_chain"))
            for (boolean enchanted : new boolean[]{false, true}) for (int ticket = 0; ticket < 3; ticket++)
                for (int enchantmentTicket = 0; enchantmentTicket < (enchanted ? 2 : 1); enchantmentTicket++)
                    try (Fixture f = new Fixture(helper, 0F, ticket)) {
                        if (enchanted && ticket == 0) f.random.enchantmentTicket(enchantmentTicket);
                        ItemStack stack = equipment(R196MobEquipment.item(id), Quality.FINE, 37);
                        stack.set(DataComponents.CUSTOM_NAME, Component.literal("mithril equipment"));
                        if (enchanted) enchant(helper, stack);
                        f.mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
                        f.drop(1F, true, false, false);
                        boolean expected = ticket == 0 && (!enchanted || enchantmentTicket == 0);
                        helper.assertValueEqual(f.drops.size(), expected ? 1 : 0,
                                "The material boundary does not alter lower-tier equipment or high-tier raw materials: " + id);
                        f.random.done(helper, enchanted && ticket == 0 ? 2 : 1);
                    }
        helper.succeed();
    }

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
            for (boolean enchanted : new boolean[]{false, true})
            try (Fixture f = new Fixture(helper, 0F)) {
                List<ItemStack> expected = new ArrayList<>();
                for (int i = 0; i < SLOTS.length; i++) {
                    ItemStack stack = equipment(ITEMS[i], Quality.POOR, 0);
                    stack.setDamageValue(stack.getMaxDamage() - 2);
                    if (enchanted) stack.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                            .getHolderOrThrow(Enchantments.UNBREAKING), 1);
                    expected.add(stack.copy());
                    f.mob.setItemSlot(SLOTS[i], stack);
                }
                f.drop(2F, recent, massiveFall, false);
                helper.assertValueEqual(f.drops.size(), SLOTS.length,
                        "Picked-up worn poor equipment retains every slot for recent=" + recent + " fall=" + massiveFall
                                + " enchanted=" + enchanted);
                for (EquipmentSlot slot : SLOTS) helper.assertTrue(f.mob.getItemBySlot(slot).isEmpty(),
                        "The picked-up stack returns to the player from " + slot);
                for (ItemStack stack : expected) helper.assertValueEqual(
                        f.drops.stream().filter(e -> ItemStack.matches(e.getItem(), stack)).count(), 1L,
                        "Picked-up equipment retains its exact enchantments, quality and wear");
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
    public static void enchantedSourceEquipmentKeepsExactlyOneOfSixWinningTicketPairs(GameTestHelper helper) {
        int kept = 0;
        for (int ticket = 0; ticket < 3; ticket++) for (int enchantmentTicket = 0; enchantmentTicket < 2; enchantmentTicket++)
            try (Fixture f = new Fixture(helper, 0F, ticket)) {
            if (ticket == 0) f.random.enchantmentTicket(enchantmentTicket);
            ItemStack enchanted = equipment(Items.IRON_PICKAXE, Quality.FINE, 37);
            enchanted.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.EFFICIENCY), 1);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, enchanted);
            f.drop(1F, true, false, false);
            boolean expected = ticket == 0 && enchantmentTicket == 0;
            helper.assertValueEqual(f.drops.size(), expected ? 1 : 0,
                    "Enchanted natural equipment survives one of the six independent drop-ticket pairs");
            if (expected) {
                kept++;
                helper.assertTrue(ItemStack.matches(f.drops.getFirst().getItem(), enchanted),
                        "The retained enchanted source stack preserves enchantments, quality and exact wear");
            }
            f.random.done(helper, ticket == 0 ? 2 : 1);
        }
        helper.assertValueEqual(kept, 1, "Enchanted natural equipment keeps exactly one of six ticket pairs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantedPoorWornSourceWeaponsDropHalfAsOftenAtTheSameQualityAndWear(GameTestHelper helper) {
        int[] kept = new int[2];
        for (boolean enchanted : new boolean[]{false, true}) for (int quarter = 0; quarter < 4; quarter++)
            for (int ticket = 0; ticket < 3; ticket++) for (int enchantmentTicket = 0; enchantmentTicket < 2; enchantmentTicket++)
                try (Fixture f = new Fixture(helper, quarter * .25F, quarter == 3 ? new int[]{ticket} : new int[0])) {
                    if (enchanted && quarter == 3 && ticket == 0) f.random.enchantmentTicket(enchantmentTicket);
                    ItemStack weapon = equipment(Items.IRON_PICKAXE, Quality.POOR, 7000);
                    if (enchanted) weapon.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                            .getHolderOrThrow(Enchantments.EFFICIENCY), 1);
                    f.mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                    f.drop(1F, true, false, false);
                    boolean expected = quarter == 3 && ticket == 0 && (!enchanted || enchantmentTicket == 0);
                    helper.assertValueEqual(f.drops.size(), expected ? 1 : 0,
                            "Equal quality and wear retain the same seventy-five-percent poor-wear penalty before both drop gates");
                    if (expected) {
                        kept[enchanted ? 1 : 0]++;
                        helper.assertTrue(ItemStack.matches(f.drops.getFirst().getItem(), weapon),
                                "A retained poor worn source stack keeps its exact enchantments, quality and damage");
                    }
                    int draws = quarter == 3 ? 1 + (enchanted && ticket == 0 ? 1 : 0) : 0;
                    f.random.done(helper, draws);
                }
        helper.assertValueEqual(kept[0], 2, "Unenchanted poor worn source weapons keep two of twenty-four equally likely ticket triples");
        helper.assertValueEqual(kept[1], 1, "Enchanted poor worn source weapons keep one of the same twenty-four ticket triples");
        helper.assertValueEqual(kept[1] * 2, kept[0], "At equal poor quality and severe wear, enchantments halve the source drop chance");
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

    private static void enchant(GameTestHelper helper, ItemStack stack) {
        stack.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.UNBREAKING), 1);
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
            drop(mob.damageSources().generic(), chance, recent, massiveFall, longdead);
        }
        private void drop(DamageSource source, float chance, boolean recent, boolean massiveFall, boolean longdead) {
            R196MobEquipment.dropEquipment(mob, source, recent, massiveFall, longdead, slot -> chance);
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
        private final ArrayDeque<Draw> tickets = new ArrayDeque<>();
        private int draws;

        private TicketRandom(float oldRoll, int... values) {
            super(196L);
            this.oldRoll = oldRoll;
            for (int value : values) tickets.addLast(new Draw(3, value));
        }
        private void enchantmentTicket(int value) { tickets.addLast(new Draw(2, value)); }
        @Override public float nextFloat() { return oldRoll; }
        @Override public int nextInt(int bound) {
            if (tickets.isEmpty() || tickets.getFirst().bound() != bound)
                throw new AssertionError("Unexpected source equipment draw nextInt(" + bound + ")");
            draws++;
            return tickets.removeFirst().value();
        }
        private record Draw(int bound, int value) {}
        private void done(GameTestHelper helper, int expectedDraws) {
            helper.assertTrue(tickets.isEmpty(), "Every independently scripted source equipment ticket was consumed");
            helper.assertValueEqual(draws, expectedDraws, "New reduction draws occur only after the original gates pass");
        }
    }
}
