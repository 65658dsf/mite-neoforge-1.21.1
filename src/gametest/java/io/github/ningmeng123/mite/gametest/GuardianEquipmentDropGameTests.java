package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
import io.github.ningmeng123.mite.registry.MiteMobs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A guardian's hidden weapon retains its own natural or picked-up equipment drop policy. @author NingMeng123 */
@GameTestHolder("mite_equipment_drop")
@PrefixGameTestTemplate(false)
public final class GuardianEquipmentDropGameTests {
    private GuardianEquipmentDropGameTests() {}

    @GameTest(template = "empty")
    public static void guardianWeaponSwapsKeepThePickedUpGuaranteeOnThePickedUpStack(GameTestHelper helper) {
        R196Skeleton guardian = guardian(helper);
        float naturalChance = chance(guardian);
        ItemStack naturalSpare = guardian.stowedItem().copy();
        markMainHandPickedUp(guardian);
        ItemStack pickedUp = guardian.getMainHandItem().copy();

        guardian.swapHeldItemWithStowed();
        helper.assertTrue(ItemStack.matches(guardian.getMainHandItem(), naturalSpare), "The generated spare is equipped without recreating it");
        helper.assertValueEqual(chance(guardian), naturalChance, "The generated spare keeps its natural drop chance");
        helper.assertTrue(chance(guardian) <= 1F, "The generated spare does not inherit the picked-up guarantee");
        helper.assertTrue(ItemStack.matches(guardian.stowedItem(), pickedUp), "The picked-up stack is retained in storage");

        guardian.swapHeldItemWithStowed();
        helper.assertTrue(ItemStack.matches(guardian.getMainHandItem(), pickedUp), "Switching back retrieves the same picked-up stack");
        helper.assertValueEqual(chance(guardian), 2F, "The picked-up stack retrieves its guaranteed drop chance");
        guardian.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothGuardianWeaponDropPoliciesSurviveNativeSaveAndLoad(GameTestHelper helper) {
        R196Skeleton guardian = guardian(helper);
        float naturalChance = chance(guardian);
        markMainHandPickedUp(guardian);
        for (boolean pickedUpStowed : new boolean[]{false, true}) {
            if (pickedUpStowed) guardian.swapHeldItemWithStowed();
            CompoundTag saved = guardian.saveWithoutId(new CompoundTag());
            R196Skeleton restored = create(helper);
            restored.load(saved);
            helper.assertTrue(ItemStack.matches(restored.getMainHandItem(), guardian.getMainHandItem())
                            && ItemStack.matches(restored.stowedItem(), guardian.stowedItem()),
                    "Both exact weapon stacks survive native save/load");
            helper.assertValueEqual(chance(restored), pickedUpStowed ? naturalChance : 2F,
                    "The equipped weapon retains its own drop chance after load");
            restored.swapHeldItemWithStowed();
            helper.assertValueEqual(chance(restored), pickedUpStowed ? 2F : naturalChance,
                    "The stored weapon retrieves its own persisted drop chance after load");
            restored.swapHeldItemWithStowed();
            helper.assertValueEqual(chance(restored), pickedUpStowed ? naturalChance : 2F,
                    "A second switch restores the other weapon's persisted drop chance");
            restored.discard();
        }
        guardian.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyGuardianSavesPreserveTheExistingGuaranteeForAnUnmarkedSpare(GameTestHelper helper) {
        R196Skeleton guardian = guardian(helper);
        ItemStack naturalSpare = guardian.stowedItem().copy();
        markMainHandPickedUp(guardian);
        CompoundTag legacy = guardian.saveWithoutId(new CompoundTag());
        legacy.remove("stowed_item_drop_chance");
        R196Skeleton restored = create(helper);
        restored.load(legacy);
        helper.assertValueEqual(chance(restored), 2F, "The legacy main-hand picked-up guarantee remains intact");
        restored.swapHeldItemWithStowed();
        helper.assertTrue(ItemStack.matches(restored.getMainHandItem(), naturalSpare), "The legacy generated spare stack is restored");
        helper.assertValueEqual(chance(restored), 2F,
                "An unmarked legacy spare preserves the existing guarantee because its pickup origin cannot be recovered");
        restored.swapHeldItemWithStowed();
        helper.assertValueEqual(chance(restored), 2F, "Returning to the legacy picked-up main hand restores its guarantee");
        restored.discard();
        guardian.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyGuardianSavesWithoutAGuaranteeUseTheNaturalSpareChance(GameTestHelper helper) {
        R196Skeleton guardian = guardian(helper);
        float naturalChance = chance(guardian);
        CompoundTag legacy = guardian.saveWithoutId(new CompoundTag());
        legacy.remove("stowed_item_drop_chance");
        R196Skeleton restored = create(helper);
        restored.load(legacy);
        restored.swapHeldItemWithStowed();
        helper.assertValueEqual(chance(restored), naturalChance,
                "An unmarked legacy spare without a previous guarantee falls back to its natural drop chance");
        helper.assertTrue(chance(restored) <= 1F, "An ordinary legacy save does not acquire a picked-up guarantee");
        restored.discard();
        guardian.discard();
        helper.succeed();
    }

    private static R196Skeleton create(GameTestHelper helper) {
        R196Skeleton guardian = (R196Skeleton)MiteMobs.type(MobKind.LONGDEAD_GUARDIAN).create(helper.getLevel());
        if (guardian == null) throw new AssertionError("The registered longdead guardian can be created");
        guardian.setNoAi(true);
        guardian.setNoGravity(true);
        return guardian;
    }

    private static R196Skeleton guardian(GameTestHelper helper) {
        R196Skeleton guardian = create(helper);
        guardian.forceSkeletonType(0);
        guardian.getRandom().setSeed(196L);
        EventHooks.finalizeMobSpawn(guardian, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guardian.blockPosition()),
                MobSpawnType.SPAWN_EGG, null);
        helper.assertFalse(guardian.stowedItem().isEmpty(), "Actual ranged guardian spawning generates its hidden dagger");
        return guardian;
    }

    private static float chance(R196Skeleton guardian) {
        return ((SourceMobDropChanceAccess)(Object)guardian).mite$equipmentDropChance(EquipmentSlot.MAINHAND);
    }

    private static void markMainHandPickedUp(R196Skeleton guardian) {
        guardian.setItemSlot(EquipmentSlot.MAINHAND, guardian.getMainHandItem().copy());
        guardian.setGuaranteedDrop(EquipmentSlot.MAINHAND);
    }
}
