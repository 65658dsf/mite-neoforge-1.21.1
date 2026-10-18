package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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

/** Native death decisions retain their original roll and independently keep one third. @author NingMeng123 */
@GameTestHolder("mite_equipment_drop")
@PrefixGameTestTemplate(false)
public final class NativeEquipmentDropRateGameTests {
    private static final float NATURAL_CHANCE = .085F;
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private NativeEquipmentDropRateGameTests() {}

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
        for (float chance : List.of(NATURAL_CHANCE, .99F)) {
            long seed = chance == NATURAL_CHANCE ? seedBetween(NATURAL_CHANCE, modified) : seedBetween(.2F, .9F);
            if (chance == .99F) h.assertTrue(EnchantmentHelper.processEquipmentDropChance(h.getLevel(), killer, source, chance) > 1F,
                    "Looting creates a guaranteed native drop without converting the gear into picked-up equipment");
            for (int candidate = 0; candidate < 3; ++candidate) {
                TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
                try {
                    mob.equip(EquipmentSlot.MAINHAND, chance);
                    mob.rolls(seed, candidate);
                    mob.drop(source, true);
                    h.assertValueEqual(mob.drops.size(), candidate == 0 ? 1 : 0,
                            "The whole native looting result is reduced by three, including a looting guarantee");
                    h.assertValueEqual(mob.gate.calls, 1,
                            "Only the stored original chance identifies picked-up equipment");
                } finally { mob.close(); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativePickupKeepsGuaranteedDropsAndExactDurabilityForEveryEquipmentSlot(GameTestHelper h) {
        for (EquipmentSlot slot : SLOTS) {
            TestMob mob = new TestMob(EntityType.HUSK, h.getLevel());
            try {
                ItemStack incoming = equipment(item(slot));
                incoming.setDamageValue(17);
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
        for (var type : List.of(EntityType.COW, EntityType.VILLAGER)) for (EquipmentSlot slot : SLOTS) {
            TestMob mob = new TestMob(type, h.getLevel());
            try {
                mob.equip(slot, NATURAL_CHANCE);
                mob.rolls(seedBetween(.04F, .08F), 2);
                mob.drop(mob.damageSources().generic(), true);
                h.assertValueEqual(mob.drops.size(), 1, "Non-monster equipment keeps the original chance: " + type + " / " + slot);
                h.assertValueEqual(mob.gate.calls, 0, "Non-monsters consume no reduction RNG");
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

        private RandomSource nativeRandom() { return super.getRandom(); }
        @Override public RandomSource getRandom() { return gate == null ? super.getRandom() : gate; }
        private void drop(DamageSource source, boolean recent) { super.dropCustomDeathLoot((ServerLevel)level(), source, recent); }
        private void close() { captureDrops(null); drops.forEach(ItemEntity::discard); discard(); }
    }

    private static final class GateRandom extends LegacyRandomSource {
        private final int candidate;
        private int calls;

        private GateRandom(int candidate) { super(0L); this.candidate = candidate; }
        @Override public int nextInt(int bound) {
            if (bound != 3) throw new AssertionError("Unexpected reduction RNG bound " + bound);
            ++calls;
            return candidate;
        }
        @Override public float nextFloat() { throw new AssertionError("The original native float must use its original RNG"); }
    }
}
