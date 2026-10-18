package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobEquipmentEnchantments;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Newborn source/native equipment, one-roll ownership and the shared persistent calendar. @author NingMeng123 */
@GameTestHolder("mite_equipment_progression")
@PrefixGameTestTemplate(false)
public final class MobEnchantmentProgressionGameTests {
    private MobEnchantmentProgressionGameTests() {}

    private static Mob mob(ServerLevel level) {
        Mob mob = EntityType.ZOMBIE.create(level);
        if (mob == null) throw new AssertionError("Zombie type must exist");
        mob.setPos(0D, level.getMaxBuildHeight() + 10D, 0D); // Fixed source tension zero, independent of test-chunk age.
        mob.setNoAi(true); mob.setNoGravity(true);
        ((MobSpawnTypeAccessor)mob).mite$setSpawnType(MobSpawnType.NATURAL);
        return mob;
    }

    private static void date(GameTestHelper helper, long highestDay) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        overworld.setDayTime(6_000L);
        BloodMoonInvasionData.get(overworld).restoreProgress(highestDay);
    }

    private static long seed(Predicate<RandomSource> predicate) {
        for (long seed = 0L; seed < 100_000L; seed++) if (predicate.test(RandomSource.create(seed))) return seed;
        throw new AssertionError("No deterministic enchantment fixture seed");
    }

    @GameTest(template = "empty")
    public static void sourceFalseKeepsItsFirstDayRngButCanEnchantWeaponsArmorAndShieldsLater(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            long successful = seed(random -> random.nextFloat() < .75F);
            for (ItemStack base : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_HELMET),
                    new ItemStack(MiteShields.get("iron")))) {
                date(helper, 1L); Mob early = mob(helper.getLevel()); early.getRandom().setSeed(successful);
                ItemStack unenchanted = R196MobEquipment.generated(early, base.copy(), false);
                RandomSource expected = RandomSource.create(successful); expected.nextFloat();
                helper.assertTrue(!unenchanted.isEnchanted() && unenchanted.getDamageValue() > 0,
                        "enchant=false keeps original first-day unenchanted worn gear");
                helper.assertValueEqual(early.getRandom().nextLong(), expected.nextLong(), "Only the old wear roll was consumed");
                date(helper, 401L); Mob late = mob(helper.getLevel()); late.getRandom().setSeed(successful);
                ItemStack enchanted = R196MobEquipment.generated(late, base.copy(), false);
                helper.assertTrue(enchanted.isEnchanted() && enchanted.getDamageValue() > 0,
                        "The same generated weapon, armor and shield gain date enchantments before source wear");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceFailedRollNeverGetsASecondChanceFromNativeSpawnSweep(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 401L); Mob mob = mob(helper.getLevel());
            mob.getRandom().setSeed(seed(random -> random.nextFloat() >= .75F));
            ItemStack stack = R196MobEquipment.generated(mob, new ItemStack(Items.IRON_SWORD), true);
            helper.assertFalse(stack.isEnchanted(), "Independent source enchantment draw failed");
            mob.setItemSlot(EquipmentSlot.MAINHAND, stack); ItemStack before = stack.copy();
            long successful = seed(random -> random.nextFloat() < .75F); mob.getRandom().setSeed(successful);
            helper.assertFalse(MobEquipmentEnchantments.populate(mob), "Spawn sweep recognizes the exact returned generated stack");
            helper.assertTrue(mob.getMainHandItem() == stack && ItemStack.matches(before, stack),
                    "The source failure, quality, damage and all components remain exact");
            helper.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(successful).nextLong(),
                    "Skipping an already generated source stack consumes no second roll");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeSpawnSweepCanEnchantAnUnenchantedWeaponArmorAndShieldWithoutChangingWear(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 401L);
            for (var fixture : List.of(new Gear(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)),
                    new Gear(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)),
                    new Gear(EquipmentSlot.OFFHAND, new ItemStack(MiteShields.get("iron"))))) {
                Mob mob = mob(helper.getLevel()); ItemStack stack = fixture.stack();
                stack.setDamageValue(Math.max(1, stack.getMaxDamage() / 3));
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("Native birth equipment"));
                ItemStack original = stack.copy();
                mob.setItemSlot(fixture.slot(), stack);
                mob.getRandom().setSeed(seed(random -> random.nextFloat() < .75F));
                MobEquipmentEnchantments.join(new EntityJoinLevelEvent(mob, helper.getLevel(), false));
                ItemStack result = mob.getItemBySlot(fixture.slot());
                helper.assertTrue(result.isEnchanted(), "A fresh native join receives its date supplement");
                ItemStack withoutNewEnchantments = result.copy();
                if (original.has(DataComponents.ENCHANTMENTS))
                    withoutNewEnchantments.set(DataComponents.ENCHANTMENTS, original.get(DataComponents.ENCHANTMENTS));
                else withoutNewEnchantments.remove(DataComponents.ENCHANTMENTS);
                helper.assertTrue(ItemStack.matches(original, withoutNewEnchantments),
                        "The supplement retains every component except enchantments for " + fixture.slot()
                                + " (expected wear " + original.getDamageValue() + ", actual " + result.getDamageValue()
                                + ", maximum " + result.getMaxDamage() + ")");
                ItemStack once = result.copy();
                helper.assertFalse(MobEquipmentEnchantments.populate(mob), "A second join or dimension entry cannot reroll");
                helper.assertTrue(ItemStack.matches(once, mob.getItemBySlot(fixture.slot())), "The once-enchanted native item remains exact");
                ItemStack replacement = fixture.stack().copy(); replacement.remove(DataComponents.ENCHANTMENTS);
                mob.setItemSlot(fixture.slot(), replacement);
                MobEquipmentEnchantments.join(new EntityJoinLevelEvent(mob, helper.getLevel(), false));
                helper.assertTrue(mob.getItemBySlot(fixture.slot()) == replacement && !replacement.isEnchanted(),
                        "Rejoining with different equipment cannot reuse the completed birth enchantment roll");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickedAndExplicitlyEnchantedEquipmentKeepsEveryComponentAndConsumesNoRoll(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 401L); Mob mob = mob(helper.getLevel());
            ItemStack picked = new ItemStack(Items.IRON_SWORD); picked.setDamageValue(77);
            picked.set(DataComponents.CUSTOM_NAME, Component.literal("Player pickup"));
            ItemStack explicit = new ItemStack(Items.IRON_HELMET); explicit.setDamageValue(42);
            explicit.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.UNBREAKING), 1);
            mob.setItemSlot(EquipmentSlot.MAINHAND, picked); mob.setDropChance(EquipmentSlot.MAINHAND, 2F);
            mob.setItemSlot(EquipmentSlot.HEAD, explicit);
            ItemStack pickedBefore = picked.copy(), explicitBefore = explicit.copy(); mob.getRandom().setSeed(196L);
            helper.assertFalse(MobEquipmentEnchantments.populate(mob), "Picked-up and explicitly enchanted items are excluded");
            helper.assertTrue(mob.getMainHandItem() == picked && mob.getItemBySlot(EquipmentSlot.HEAD) == explicit
                            && ItemStack.matches(pickedBefore, picked) && ItemStack.matches(explicitBefore, explicit),
                    "Both exact actual stacks and every component are preserved");
            helper.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "Exclusions consume no enchantment RNG");
            mob.getRandom().setSeed(197L);
            ItemStack generated = MobEquipmentEnchantments.generated(mob, explicit, true);
            helper.assertTrue(generated == explicit && ItemStack.matches(explicitBefore, generated),
                    "The shared source enchantment helper also preserves explicitly enchanted components");
            helper.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(197L).nextLong(),
                    "The source helper does not consume a hidden roll for explicitly enchanted gear");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loadedOldMobAndItsNbtRoundTripNeverReceiveDateEnchantments(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 401L); Mob old = mob(helper.getLevel()); ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.setDamageValue(83); old.setItemSlot(EquipmentSlot.MAINHAND, sword);
            MobEquipmentEnchantments.join(new EntityJoinLevelEvent(old, helper.getLevel(), true));
            helper.assertFalse(MobEquipmentEnchantments.populate(old), "Disk-load marker blocks a late-date retrofit");
            helper.assertTrue(old.getMainHandItem() == sword && !sword.isEnchanted(), "Old equipment is unchanged");
            CompoundTag tag = old.saveWithoutId(new CompoundTag()); Mob restored = mob(helper.getLevel()); restored.load(tag);
            helper.assertFalse(MobEquipmentEnchantments.populate(restored), "Saved once-decision survives NBT restoration and travel");
            helper.assertTrue(ItemStack.matches(sword, restored.getMainHandItem()), "Saved native gear remains exact");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandEquipmentAndFirstDayNativeItemsAreLeftUnchanged(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long day : new long[]{1L, 401L}) {
                date(helper, day); Mob mob = mob(helper.getLevel());
                if (day > 1L) ((MobSpawnTypeAccessor)mob).mite$setSpawnType(MobSpawnType.COMMAND);
                ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.setDamageValue(19);
                mob.setItemSlot(EquipmentSlot.MAINHAND, sword); mob.getRandom().setSeed(196L);
                helper.assertFalse(MobEquipmentEnchantments.populate(mob), "Native day-one and explicitly commanded equipment bypass the supplement");
                helper.assertTrue(mob.getMainHandItem() == sword && !sword.isEnchanted() && sword.getDamageValue() == 19,
                        "No component or wear change is introduced");
                helper.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(), "No new RNG for either bypass");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highestOverworldDaySurvivesClockRollbackAndIsSharedByNetherEquipment(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            ServerLevel overworld = helper.getLevel().getServer().overworld();
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "Native nether dimension is available");
            date(helper, 1L); overworld.setDayTime(400L * 24_000L + 6_000L);
            Mob first = mob(overworld);
            helper.assertValueEqual(MobEquipmentEnchantments.highestDay(first), 401L, "Calendar observes a new maximum on a generation query");
            overworld.setDayTime(6_000L); Mob second = mob(nether);
            helper.assertValueEqual(MobEquipmentEnchantments.highestDay(second), 401L, "Other dimensions use that maximum after the clock rolls back");
            long successful = seed(random -> random.nextFloat() < .75F);
            first.getRandom().setSeed(successful); second.getRandom().setSeed(successful);
            ItemStack one = R196MobEquipment.generated(first, new ItemStack(Items.IRON_SWORD), false);
            ItemStack two = R196MobEquipment.generated(second, new ItemStack(Items.IRON_SWORD), false);
            helper.assertTrue(one.isEnchanted() && ItemStack.matches(one, two),
                    "Matching owner RNG produces identical date enchantments and source wear across dimensions after rollback");
        }
        helper.succeed();
    }

    private record Gear(EquipmentSlot slot, ItemStack stack) {}

    private static final class Progress implements AutoCloseable {
        private final ServerLevel level;
        private final long time, highest;
        private Progress(GameTestHelper helper) {
            level = helper.getLevel().getServer().overworld(); time = level.getDayTime();
            highest = BloodMoonInvasionData.get(level).highestDay();
        }
        @Override public void close() {
            level.setDayTime(time); BloodMoonInvasionData.get(level).restoreProgress(highest);
        }
    }
}
