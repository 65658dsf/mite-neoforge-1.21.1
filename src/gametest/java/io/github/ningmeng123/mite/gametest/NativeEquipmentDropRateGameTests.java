package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.item.EquipmentQuality;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native death decisions retain their original roll and independent natural/enchantment reductions. @author NingMeng123 */
@GameTestHolder("mite_equipment_drop")
@PrefixGameTestTemplate(false)
public final class NativeEquipmentDropRateGameTests {
    private static final float NATURAL_CHANCE = .085F;
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private NativeEquipmentDropRateGameTests() {}

    @GameTest(template = "empty")
    public static void naturalHighTierEquipmentNeverDropsFromAnyNativeSlotOrConsumesReductionRng(GameTestHelper h) {
        long seed = seedBetween(.04F, .08F);
        for (Item item : HighTierEquipmentFixtures.items()) for (EquipmentSlot slot : SLOTS)
            for (boolean enchanted : List.of(false, true)) {
                TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
                try {
                    mob.equip(slot, 1F);
                    ItemStack stack = equipment(item);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("iron equipment"));
                    if (enchanted) enchant(h, stack);
                    mob.setItemSlot(slot, stack);
                    mob.rolls(seed, 0, 0);
                    RandomSource expected = RandomSource.create(seed);
                    expected.nextFloat();
                    mob.drop(mob.damageSources().generic(), true);
                    h.assertTrue(mob.drops.isEmpty() && mob.getItemBySlot(slot) == stack,
                            "Even a winning native roll cannot drop renamed natural high-tier equipment: " + item + " / " + slot);
                    h.assertValueEqual(mob.gate.calls, 0, "The permanent material ban consumes neither reduction draw");
                    h.assertValueEqual(mob.nativeRandom().nextFloat(), expected.nextFloat(),
                            "High-tier rejection preserves the existing native floating-point roll");
                } finally { mob.close(); }
            }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeLootingGuaranteesCannotOvercomeTheNaturalHighTierBan(GameTestHelper h) {
        Player killer = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = equipment(Items.IRON_SWORD);
        sword.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.LOOTING), 3);
        killer.setItemSlot(EquipmentSlot.MAINHAND, sword);
        DamageSource source = killer.damageSources().playerAttack(killer);
        h.assertTrue(EnchantmentHelper.processEquipmentDropChance(h.getLevel(), killer, source, .99F) > 1F,
                "Real looting crosses the guaranteed-drop threshold before the material restriction");
        long seed = seedBetween(.2F, .9F);
        for (Item item : HighTierEquipmentFixtures.items()) for (EquipmentSlot slot : SLOTS) {
            TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
            try {
                mob.equip(slot, .99F);
                ItemStack stack = equipment(item);
                enchant(h, stack);
                mob.setItemSlot(slot, stack);
                mob.rolls(seed, 0, 0);
                mob.drop(source, true);
                h.assertTrue(mob.drops.isEmpty(), "Looting never converts naturally equipped high-tier gear into a pickup: " + item + " / " + slot);
                h.assertValueEqual(mob.gate.calls, 0, "High-tier gear skips both reduction draws even after looting guarantees the native chance");
            } finally { mob.close(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pickedUpHighTierEquipmentKeepsEveryNativeSlotAndItsExactComponents(GameTestHelper h) {
        long seed = seedBetween(.2F, .9F);
        for (Item item : HighTierEquipmentFixtures.items()) for (EquipmentSlot slot : SLOTS)
            for (boolean enchanted : List.of(false, true)) {
                TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
                try {
                    mob.equip(slot, 2F);
                    ItemStack incoming = equipment(item);
                    incoming.setDamageValue(37);
                    if (enchanted) enchant(h, incoming);
                    ItemStack expected = incoming.copy();
                    mob.setItemSlot(slot, incoming);
                    mob.rolls(seed, 2, 1);
                    mob.drop(mob.damageSources().generic(), false);
                    h.assertTrue(mob.drops.size() == 1 && ItemStack.matches(mob.drops.getFirst().getItem(), expected),
                            "The actual picked-up marker preserves every high-tier slot on environmental death: " + item + " / " + slot);
                    h.assertTrue(mob.getItemBySlot(slot).isEmpty(), "Returned picked-up high-tier equipment clears its native slot");
                    h.assertValueEqual(mob.gate.calls, 0, "Picked-up high-tier equipment consumes no reduction RNG");
                } finally { mob.close(); }
            }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualNativePickupMarksHighTierWeaponsShieldsAndArmorAsReturnable(GameTestHelper h) {
        long seed = seedBetween(.2F, .9F);
        for (String material : List.of("mithril", "adamantium", "vibranium")) for (EquipmentSlot slot : SLOTS)
            for (boolean enchanted : List.of(false, true)) {
                TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
                try {
                    String suffix = switch (slot) {
                        case MAINHAND -> "sword";
                        case OFFHAND -> "shield";
                        case HEAD -> "helmet";
                        case CHEST -> "chestplate";
                        case LEGS -> "leggings";
                        case FEET -> "boots";
                        default -> throw new AssertionError("Unsupported native pickup fixture slot " + slot);
                    };
                    ItemStack incoming = equipment(R196MobEquipment.item("mite:" + material + "_" + suffix));
                    incoming.setDamageValue(37);
                    if (enchanted) enchant(h, incoming);
                    ItemStack expected = incoming.copy();
                    h.assertFalse(mob.equipItemIfPossible(incoming).isEmpty(), "The real native pickup route accepts high-tier equipment");
                    h.assertTrue(((SourceMobDropChanceAccess)(Object)mob).mite$equipmentDropChance(slot) > 1F,
                            "Native pickup records the high-tier item's original guaranteed-drop marker");
                    mob.rolls(seed, 2, 1);
                    mob.drop(mob.damageSources().generic(), false);
                    h.assertTrue(mob.drops.size() == 1 && ItemStack.matches(mob.drops.getFirst().getItem(), expected),
                            "High-tier gear picked up through Mob.equipItemIfPossible returns unchanged: " + material + " / " + slot);
                    h.assertValueEqual(mob.gate.calls, 0, "Actual native high-tier pickups skip all new random gates");
                } finally { mob.close(); }
            }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nonMonstersKeepHighTierEquipmentDropsWithoutAnyNewRandomDraw(GameTestHelper h) {
        long seed = seedBetween(.04F, .08F);
        for (var type : List.of(EntityType.COW, EntityType.VILLAGER)) for (Item item : HighTierEquipmentFixtures.items())
            for (EquipmentSlot slot : SLOTS) for (boolean enchanted : List.of(false, true)) {
                TestMob mob = new TestMob(type, h.getLevel());
                try {
                    mob.equip(slot, NATURAL_CHANCE);
                    ItemStack stack = equipment(item);
                    if (enchanted) enchant(h, stack);
                    mob.setItemSlot(slot, stack);
                    mob.rolls(seed, 2, 1);
                    mob.drop(mob.damageSources().generic(), true);
                    h.assertValueEqual(mob.drops.size(), 1,
                            "The permanent high-tier equipment ban applies only to monster category entities: " + type + " / " + item + " / " + slot);
                    h.assertValueEqual(mob.gate.calls, 0, "Non-monsters never consume new material, natural or enchantment gate draws");
                } finally { mob.close(); }
            }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeNaturalWeaponsShieldsAndArmorKeepExactlyOneOfThreeWinningRolls(GameTestHelper h) {
        // This roll wins at the old chance but would lose if the native threshold were simply divided by three.
        long seed = seedBetween(.04F, .08F);
        for (EquipmentSlot slot : SLOTS) for (int candidate = 0; candidate < 3; ++candidate) {
            TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
            try {
                mob.equip(slot, NATURAL_CHANCE);
                mob.rolls(seed, candidate);
                mob.drop(mob.damageSources().generic(), true);
                h.assertValueEqual(mob.drops.size(), candidate == 0 ? 1 : 0,
                        "One of three candidates keeps the unchanged successful native roll: " + slot + " / " + candidate);
                h.assertValueEqual(mob.getItemBySlot(slot).isEmpty(), candidate == 0,
                        "Native equipment is cleared only when its item actually drops");
                h.assertValueEqual(mob.gate.calls, 1, "Each eligible slot gets one independent three-way decision");
            } finally { mob.close(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeEnchantedNaturalEquipmentKeepsExactlyOneOfSixWinningTicketPairs(GameTestHelper h) {
        long seed = seedBetween(.04F, .08F);
        for (EquipmentSlot slot : SLOTS) {
            int retained = 0;
            for (int candidate = 0; candidate < 3; ++candidate) for (int enchantmentCandidate = 0; enchantmentCandidate < 2; ++enchantmentCandidate) {
                TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
                try {
                    mob.equip(slot, NATURAL_CHANCE);
                    enchant(h, mob.getItemBySlot(slot));
                    mob.rolls(seed, candidate, enchantmentCandidate);
                    mob.drop(mob.damageSources().generic(), true);
                    boolean expected = candidate == 0 && enchantmentCandidate == 0;
                    h.assertValueEqual(mob.drops.size(), expected ? 1 : 0,
                            "One of six ticket pairs retains an enchanted native drop: " + slot);
                    h.assertValueEqual(mob.getItemBySlot(slot).isEmpty(), expected,
                            "Both independent gates must pass before native equipment clears");
                    h.assertValueEqual(mob.gate.calls, candidate == 0 ? 2 : 1,
                            "The enchantment draw occurs only after the one-third gate passes");
                    if (expected) {
                        retained++;
                        h.assertTrue(mob.drops.getFirst().getItem().isEnchanted(), "The retained native item keeps its enchantments");
                        h.assertValueEqual(EquipmentDurability.quality(mob.drops.getFirst().getItem()), Quality.AVERAGE,
                                "The additional drop gate preserves native equipment quality");
                    }
                } finally { mob.close(); }
            }
            h.assertValueEqual(retained, 1, "Enchanted native equipment keeps exactly one of six candidate pairs: " + slot);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantedPoorWornNativeWeaponsDropHalfAsOftenAtTheSameQualityAndWear(GameTestHelper h) {
        int[] kept = new int[2];
        long seed = seedBetween(.04F, .08F);
        for (boolean enchanted : List.of(false, true)) for (int quarter = 0; quarter < 4; quarter++)
            for (int candidate = 0; candidate < 3; candidate++) for (int enchantmentCandidate = 0; enchantmentCandidate < 2; enchantmentCandidate++) {
                TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
                try {
                    mob.equip(EquipmentSlot.MAINHAND, 1F);
                    ItemStack weapon = equipment(Items.IRON_PICKAXE);
                    EquipmentDurability.setQuality(weapon, Quality.POOR);
                    weapon.setDamageValue(7000);
                    if (enchanted) enchant(h, weapon);
                    mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                    if (enchanted) mob.rolls(seed, candidate, enchantmentCandidate);
                    else mob.rolls(seed, candidate);
                    mob.gate.qualityRoll = quarter * .25F;
                    mob.drop(mob.damageSources().generic(), true);
                    boolean expected = quarter == 3 && candidate == 0 && (!enchanted || enchantmentCandidate == 0);
                    h.assertValueEqual(mob.drops.size(), expected ? 1 : 0,
                            "Equally worn poor native weapons retain the same quality penalty before natural and enchanted drop gates");
                    h.assertValueEqual(mob.gate.qualityCalls, 1, "Both enchanted and unenchanted poor native weapons make the same wear roll");
                    if (expected) kept[enchanted ? 1 : 0]++;
                } finally { mob.close(); }
            }
        h.assertValueEqual(kept[0], 2, "Unenchanted poor worn native weapons keep two of twenty-four matching ticket triples");
        h.assertValueEqual(kept[1], 1, "Enchanted poor worn native weapons keep one of the same twenty-four ticket triples");
        h.assertValueEqual(kept[1] * 2, kept[0], "At equal poor quality and wear, native enchantments halve the drop chance");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reductionCannotTurnAFailedNativeRollIntoADropOrSkipTheOriginalRoll(GameTestHelper h) {
        long seed = seedBetween(.2F, .9F);
        TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
        try {
            mob.equip(EquipmentSlot.MAINHAND, NATURAL_CHANCE);
            mob.rolls(seed, 0);
            RandomSource expected = RandomSource.create(seed);
            expected.nextFloat();
            mob.drop(mob.damageSources().generic(), true);
            h.assertTrue(mob.drops.isEmpty() && !mob.getMainHandItem().isEmpty(),
                    "A winning reduction candidate still has to pass the original native chance");
            h.assertValueEqual(mob.nativeRandom().nextFloat(), expected.nextFloat(),
                    "The original native floating-point draw is preserved");
        } finally { mob.close(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lootingBonusAndLootingGuaranteesAreAlsoReducedToOneThird(GameTestHelper h) {
        Player killer = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = equipment(Items.IRON_SWORD);
        sword.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.LOOTING), 3);
        killer.setItemSlot(EquipmentSlot.MAINHAND, sword);
        DamageSource source = killer.damageSources().playerAttack(killer);
        float modified = EnchantmentHelper.processEquipmentDropChance(h.getLevel(), killer, source, NATURAL_CHANCE);
        h.assertTrue(modified > NATURAL_CHANCE, "The real killer's looting raises the native chance");
        for (float chance : List.of(NATURAL_CHANCE, .99F)) for (boolean enchanted : List.of(false, true)) {
            long seed = chance == NATURAL_CHANCE ? seedBetween(NATURAL_CHANCE, modified) : seedBetween(.2F, .9F);
            if (chance == .99F) h.assertTrue(EnchantmentHelper.processEquipmentDropChance(h.getLevel(), killer, source, chance) > 1F,
                    "Looting creates a guaranteed native drop without converting the gear into picked-up equipment");
            for (int candidate = 0; candidate < 3; ++candidate)
                for (int enchantmentCandidate = 0; enchantmentCandidate < (enchanted ? 2 : 1); ++enchantmentCandidate) {
                TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
                try {
                    mob.equip(EquipmentSlot.MAINHAND, chance);
                    if (enchanted) {
                        enchant(h, mob.getMainHandItem());
                        mob.rolls(seed, candidate, enchantmentCandidate);
                    } else mob.rolls(seed, candidate);
                    mob.drop(source, true);
                    boolean expected = candidate == 0 && (!enchanted || enchantmentCandidate == 0);
                    h.assertValueEqual(mob.drops.size(), expected ? 1 : 0,
                            "Looting and looting guarantees retain both natural and enchanted equipment reductions");
                    h.assertValueEqual(mob.gate.calls, enchanted && candidate == 0 ? 2 : 1,
                            "Only the stored original chance identifies picked-up equipment");
                } finally { mob.close(); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativePickupKeepsGuaranteedDropsAndExactDurabilityForEveryEquipmentSlot(GameTestHelper h) {
        for (EquipmentSlot slot : SLOTS) for (boolean enchanted : List.of(false, true)) {
            TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
            try {
                ItemStack incoming = equipment(item(slot));
                incoming.setDamageValue(17);
                if (enchanted) enchant(h, incoming);
                ItemStack expected = incoming.copy();
                h.assertFalse(mob.equipItemIfPossible(incoming).isEmpty(), "The native pickup route accepts this equipment: " + slot);
                h.assertTrue(((SourceMobDropChanceAccess)(Object)mob).mite$equipmentDropChance(slot) > 1F,
                        "Native pickup marks the actual destination as a guaranteed drop: " + slot);
                mob.rolls(seedBetween(.2F, .9F), 2);
                mob.drop(mob.damageSources().generic(), false);
                h.assertTrue(mob.drops.size() == 1 && ItemStack.matches(mob.drops.getFirst().getItem(), expected),
                        "Picked-up equipment keeps its guaranteed environmental death drop and exact components: " + slot);
                h.assertValueEqual(mob.gate.calls, 0, "Picked-up equipment never consumes a reduction roll");
            } finally { mob.close(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nonMonsterEquipmentRetainsItsOriginalChanceWithoutAnExtraRandomDraw(GameTestHelper h) {
        for (var type : List.of(EntityType.COW, EntityType.VILLAGER)) for (EquipmentSlot slot : SLOTS)
            for (boolean enchanted : List.of(false, true)) {
            TestMob mob = new TestMob(type, h.getLevel());
            try {
                mob.equip(slot, NATURAL_CHANCE);
                if (enchanted) enchant(h, mob.getItemBySlot(slot));
                mob.rolls(seedBetween(.04F, .08F), 2);
                mob.drop(mob.damageSources().generic(), true);
                h.assertValueEqual(mob.drops.size(), 1, "Non-monster equipment keeps the original chance: " + type + " / " + slot);
                h.assertValueEqual(mob.gate.calls, 0, "Non-monsters consume neither natural nor enchantment reduction RNG");
            } finally { mob.close(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nonMonsterEnchantedPoorWornWeaponsKeepTheirLegacyQualityExemption(GameTestHelper h) {
        for (var type : List.of(EntityType.COW, EntityType.VILLAGER)) {
            TestMob mob = new TestMob(type, h.getLevel());
            try {
                mob.equip(EquipmentSlot.MAINHAND, 1F);
                ItemStack weapon = equipment(Items.IRON_PICKAXE);
                EquipmentDurability.setQuality(weapon, Quality.POOR);
                weapon.setDamageValue(7000);
                enchant(h, weapon);
                h.assertFalse(EquipmentQuality.hasPoorHeldDropPenalty(weapon, EquipmentSlot.MAINHAND, 1F),
                        "The original three-argument predicate keeps its R196 enchanted-equipment exemption");
                h.assertTrue(EquipmentQuality.hasPoorHeldDropPenalty(weapon, EquipmentSlot.MAINHAND, 1F, true),
                        "The monster-only predicate applies the same poor-wear penalty to enchanted equipment");
                mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                mob.rolls(seedBetween(.04F, .08F), 2);
                mob.drop(mob.damageSources().generic(), true);
                h.assertValueEqual(mob.drops.size(), 1, "Non-monsters retain the original enchanted poor-wear exemption");
                h.assertValueEqual(mob.gate.calls, 0, "Non-monsters consume no natural or enchanted drop gate draws");
                h.assertValueEqual(mob.gate.qualityCalls, 0, "Non-monster enchanted poor weapons consume no new wear draw");
            } finally { mob.close(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vanishingAndRecentPlayerRequirementsStillPreventEquipmentDrops(GameTestHelper h) {
        for (boolean cursed : List.of(false, true)) {
            TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
            try {
                mob.equip(EquipmentSlot.MAINHAND, cursed ? 2F : 1F);
                if (cursed) mob.getMainHandItem().enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(Enchantments.VANISHING_CURSE), 1);
                mob.rolls(seedBetween(.04F, .08F), 0);
                mob.drop(mob.damageSources().generic(), false);
                h.assertTrue(mob.drops.isEmpty() && !mob.getMainHandItem().isEmpty(),
                        "Vanishing and nonplayer death restrictions still reject equipment before the random decision");
                h.assertValueEqual(mob.gate.calls, 0, "Native early rejections do not consume reduction RNG");
            } finally { mob.close(); }
        }
        h.succeed();
    }

    private static Item item(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> Items.IRON_SWORD;
            case OFFHAND -> Items.SHIELD;
            case HEAD -> Items.IRON_HELMET;
            case CHEST -> Items.IRON_CHESTPLATE;
            case LEGS -> Items.IRON_LEGGINGS;
            case FEET -> Items.IRON_BOOTS;
            default -> throw new IllegalArgumentException("Unsupported fixture slot: " + slot);
        };
    }

    private static ItemStack equipment(Item item) {
        ItemStack result = new ItemStack(item);
        EquipmentDurability.setQuality(result, Quality.AVERAGE);
        return result;
    }

    private static void enchant(GameTestHelper h, ItemStack stack) {
        stack.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.UNBREAKING), 1);
    }

    private static long seedBetween(float low, float high) {
        for (long seed = 0; seed < 1_000_000; ++seed) {
            float roll = RandomSource.create(seed).nextFloat();
            if (roll > low && roll < high) return seed;
        }
        throw new AssertionError("No native equipment seed in " + low + " .. " + high);
    }

    /** Native Mob uses its field RNG; only the new independent gate uses getRandom(). */
    private static final class TestMob extends Mob {
        private final List<ItemEntity> drops = new ArrayList<>();
        private GateRandom gate;

        private TestMob(EntityType<? extends Mob> type, ServerLevel level) {
            super(type, level);
            captureDrops(drops);
            setNoAi(true);
            setNoGravity(true);
        }

        private void equip(EquipmentSlot slot, float chance) {
            setItemSlot(slot, equipment(item(slot)));
            setDropChance(slot, chance);
        }

        private void rolls(long seed, int candidate) {
            super.getRandom().setSeed(seed);
            gate = new GateRandom(candidate);
        }

        private void rolls(long seed, int candidate, int enchantmentCandidate) {
            rolls(seed, candidate);
            if (candidate == 0) gate.enchantmentTicket(enchantmentCandidate);
        }

        private RandomSource nativeRandom() { return super.getRandom(); }
        @Override public RandomSource getRandom() { return gate == null ? super.getRandom() : gate; }
        private void drop(DamageSource source, boolean recent) { super.dropCustomDeathLoot((ServerLevel)level(), source, recent); }
        private void close() { captureDrops(null); drops.forEach(ItemEntity::discard); discard(); }
    }

    private static final class GateRandom extends LegacyRandomSource {
        private final ArrayDeque<Draw> tickets = new ArrayDeque<>();
        private int calls;
        private Float qualityRoll;
        private int qualityCalls;

        private GateRandom(int candidate) { super(0L); tickets.addLast(new Draw(3, candidate)); }
        private void enchantmentTicket(int value) { tickets.addLast(new Draw(2, value)); }
        @Override public int nextInt(int bound) {
            if (tickets.isEmpty() || tickets.getFirst().bound() != bound)
                throw new AssertionError("Unexpected reduction RNG bound " + bound);
            ++calls;
            return tickets.removeFirst().value();
        }
        @Override public float nextFloat() {
            if (qualityRoll == null) throw new AssertionError("The original native float must use its original RNG");
            qualityCalls++;
            return qualityRoll;
        }
        private record Draw(int bound, int value) {}
    }
}
