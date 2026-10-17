package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.entity.SkeletonWeaponDisplay;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real equipment drives the single-weapon presentation state without a renderer. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SkeletonWeaponDisplayGameTests {
    private static List<EntityType<? extends AbstractSkeleton>> archers() {
        return List.of(EntityType.SKELETON,
                (EntityType<? extends AbstractSkeleton>)(EntityType<?>)MiteMobs.type(MobKind.LONGDEAD));
    }

    private static AbstractSkeleton create(GameTestHelper h, EntityType<? extends AbstractSkeleton> type) {
        AbstractSkeleton skeleton = type.create(h.getLevel());
        skeleton.setNoAi(true);
        skeleton.setNoGravity(true);
        if (skeleton instanceof R196Skeleton old) old.setSkeletonType(0);
        else ((OrdinarySkeletonState)skeleton).mite$setSourceSkeletonType(0);
        return skeleton;
    }

    private static void pair(AbstractSkeleton skeleton, ItemStack main, ItemStack spare) {
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, main);
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, spare);
    }

    private static void swap(AbstractSkeleton skeleton) {
        ItemStack main = skeleton.getMainHandItem();
        pair(skeleton, skeleton.getOffhandItem(), main);
    }

    private static void cleanup(AbstractSkeleton skeleton) {
        SkeletonWeaponDisplay.clear(skeleton);
        skeleton.discard();
    }

    private static void shown(GameTestHelper h, SkeletonWeaponDisplay.Frame frame, ItemStack expected,
            float lowering, boolean switching, String context) {
        h.assertTrue(ItemStack.matches(frame.stack(), expected), context + ": exactly the expected weapon is presented");
        h.assertTrue(Math.abs(frame.lowering() - lowering) < .0001F, context + ": expected lowering fraction");
        h.assertTrue(frame.switching() == switching, context + ": expected switch lifecycle");
    }

    @GameTest(template = "empty")
    public static void firstTrackingShowsOnlyMainWeaponForEitherHandAndArcherType(GameTestHelper h) {
        for (var type : archers()) for (boolean leftHanded : new boolean[]{false, true}) {
            var skeleton = create(h, type);
            try {
                skeleton.setLeftHanded(leftHanded);
                ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
                pair(skeleton, bow, sword);
                h.assertTrue(SkeletonWeaponDisplay.hidesOffhand(skeleton), "The stored sword is hidden for either dominant hand");
                h.assertTrue(skeleton.getMainArm() == (leftHanded ? HumanoidArm.LEFT : HumanoidArm.RIGHT),
                        "The presentation still follows native skeleton handedness");
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 100D), bow, 0F, false, "First tracking");
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 101.5D), bow, 0F, false, "Unchanged tracking");
                h.assertTrue(skeleton.getMainHandItem() == bow && skeleton.getOffhandItem() == sword,
                        "Presentation does not change the real equipment slots");
            } finally { cleanup(skeleton); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void switchingLowersOldWeaponThenHasEmptyWindowBeforeRaisingNewWeapon(GameTestHelper h) {
        for (var type : archers()) {
            var skeleton = create(h, type);
            try {
                ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
                pair(skeleton, bow, sword);
                SkeletonWeaponDisplay.frame(skeleton, 0D);
                swap(skeleton);
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 1D), bow, 0F, true, "Switch begins with old bow");
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 3D), bow, .5F, true, "Old bow lowers");
                h.assertTrue(SkeletonWeaponDisplay.frame(skeleton, 5D).stack().isEmpty(), "Old bow is fully stowed before the sword appears");
                h.assertTrue(SkeletonWeaponDisplay.frame(skeleton, 5.75D).stack().isEmpty(), "A real empty interval separates the two weapons");
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 6D), sword, 1F, true, "New sword starts lowered");
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 8D), sword, .5F, true, "New sword rises");
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 10D), sword, 0F, false, "Switch finishes");
                h.assertTrue(SkeletonWeaponDisplay.hidesOffhand(skeleton) && skeleton.getOffhandItem() == bow,
                        "The original bow remains stored throughout the animation");
            } finally { cleanup(skeleton); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reverseSwitchSupportsMaterialBowAndMiteDaggerWithoutASecondDisplayedWeapon(GameTestHelper h) {
        for (var type : archers()) {
            var skeleton = create(h, type);
            try {
                ItemStack bow = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mite", "ancient_metal_bow")));
                ItemStack dagger = new ItemStack(MiteTools.get("mite:copper_dagger"));
                pair(skeleton, dagger, bow);
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 40D), dagger, 0F, false, "First tracking in melee mode");
                h.assertTrue(SkeletonWeaponDisplay.hidesOffhand(skeleton), "The material bow is stored while the dagger is held");
                swap(skeleton);
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 41D), dagger, 0F, true, "Old MITE dagger stows");
                h.assertTrue(SkeletonWeaponDisplay.frame(skeleton, 45.5D).stack().isEmpty(), "Reverse switch also has an empty interval");
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 48D), bow, .5F, true, "Material bow rises after the dagger is stowed");
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 50D), bow, 0F, false, "Reverse switch finishes");
            } finally { cleanup(skeleton); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void durabilityAndComponentUpdatesDoNotStartAWeaponSwitch(GameTestHelper h) {
        var skeleton = create(h, EntityType.SKELETON);
        try {
            ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
            pair(skeleton, bow, sword);
            SkeletonWeaponDisplay.frame(skeleton, 1D);
            bow.setDamageValue(13);
            bow.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123"));
            ItemStack updated = bow.copy();
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, updated);
            shown(h, SkeletonWeaponDisplay.frame(skeleton, 2D), updated, 0F, false, "Updated bow stack");
            sword.setDamageValue(9);
            skeleton.setItemSlot(EquipmentSlot.OFFHAND, sword.copy());
            shown(h, SkeletonWeaponDisplay.frame(skeleton, 3D), updated, 0F, false, "Updated stored sword");
            swap(skeleton);
            shown(h, SkeletonWeaponDisplay.frame(skeleton, 4D), updated, 0F, true, "Only the actual weapon class swap starts animation");
        } finally { cleanup(skeleton); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void disarmingDuringStowingImmediatelyClearsTheOldWeapon(GameTestHelper h) {
        var skeleton = create(h, EntityType.SKELETON);
        try {
            ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
            pair(skeleton, bow, sword);
            SkeletonWeaponDisplay.frame(skeleton, 0D);
            swap(skeleton);
            SkeletonWeaponDisplay.frame(skeleton, 1D);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            var disarmed = SkeletonWeaponDisplay.frame(skeleton, 2D);
            h.assertTrue(disarmed.stack().isEmpty() && !disarmed.switching(), "A disarmed skeleton cannot keep showing the previous bow");
            h.assertTrue(SkeletonWeaponDisplay.hidesOffhand(skeleton), "The stored bow stays hidden during disarming cooldown");
            h.assertTrue(SkeletonWeaponDisplay.frame(skeleton, 7D).stack().isEmpty(), "The abandoned switch cannot later resurrect a weapon");
            pair(skeleton, bow, ItemStack.EMPTY);
            shown(h, SkeletonWeaponDisplay.frame(skeleton, 8D), bow, 0F, false, "Single recovered weapon has no obsolete transition");
        } finally { cleanup(skeleton); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void losingTheSpareOrEquippingAShieldCancelsAnimationAndRestoresNativeDisplay(GameTestHelper h) {
        var skeleton = create(h, EntityType.SKELETON);
        try {
            ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
            pair(skeleton, bow, sword);
            SkeletonWeaponDisplay.frame(skeleton, 0D);
            swap(skeleton);
            SkeletonWeaponDisplay.frame(skeleton, 1D);
            skeleton.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            h.assertFalse(SkeletonWeaponDisplay.hidesOffhand(skeleton), "An absent spare does not activate weapon-specific offhand filtering");
            shown(h, SkeletonWeaponDisplay.frame(skeleton, 2D), sword, 0F, false, "Losing the spare cancels the old bow presentation");
            pair(skeleton, bow, sword);
            SkeletonWeaponDisplay.frame(skeleton, 10D);
            swap(skeleton);
            SkeletonWeaponDisplay.frame(skeleton, 11D);
            skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            h.assertFalse(SkeletonWeaponDisplay.hidesOffhand(skeleton), "A real shield remains visible");
            shown(h, SkeletonWeaponDisplay.frame(skeleton, 12D), sword, 0F, false, "Shield equipment immediately restores native main hand");
        } finally { cleanup(skeleton); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void guardianAndPureMeleeVariantsKeepTheirOriginalWeaponPresentation(GameTestHelper h) {
        var guardian = create(h, (EntityType<? extends AbstractSkeleton>)(EntityType<?>)MiteMobs.type(MobKind.LONGDEAD_GUARDIAN));
        try {
            ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
            pair(guardian, bow, sword);
            h.assertFalse(SkeletonWeaponDisplay.hidesOffhand(guardian), "Guardian equipment is excluded from ordinary archer storage filtering");
            SkeletonWeaponDisplay.frame(guardian, 0D);
            swap(guardian);
            shown(h, SkeletonWeaponDisplay.frame(guardian, 1D), sword, 0F, false, "Guardian retains native presentation");
        } finally { cleanup(guardian); }
        for (var type : archers()) {
            var skeleton = create(h, type);
            try {
                ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
                pair(skeleton, bow, sword);
                SkeletonWeaponDisplay.frame(skeleton, 0D);
                swap(skeleton);
                SkeletonWeaponDisplay.frame(skeleton, 1D);
                if (skeleton instanceof R196Skeleton old) old.setSkeletonType(2);
                else ((OrdinarySkeletonState)skeleton).mite$setSourceSkeletonType(2);
                h.assertFalse(SkeletonWeaponDisplay.hidesOffhand(skeleton), "Pure melee metadata removes ordinary archer filtering");
                shown(h, SkeletonWeaponDisplay.frame(skeleton, 2D), sword, 0F, false, "Eligibility change cancels any obsolete switch");
            } finally { cleanup(skeleton); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void separateSkeletonsDoNotShareTheirDisplayedWeaponOrAnimationClock(GameTestHelper h) {
        var first = create(h, EntityType.SKELETON);
        var second = create(h, EntityType.SKELETON);
        try {
            ItemStack firstBow = new ItemStack(Items.BOW), firstSword = new ItemStack(Items.IRON_SWORD);
            ItemStack secondBow = new ItemStack(Items.BOW), secondSword = new ItemStack(Items.STONE_SWORD);
            firstBow.setDamageValue(17);
            secondBow.setDamageValue(21);
            pair(first, firstBow, firstSword);
            pair(second, secondSword, secondBow);
            SkeletonWeaponDisplay.frame(first, 0D);
            SkeletonWeaponDisplay.frame(second, 0D);
            swap(first);
            SkeletonWeaponDisplay.frame(first, 1D);
            shown(h, SkeletonWeaponDisplay.frame(second, 2D), secondSword, 0F, false, "Second entity remains idle");
            swap(second);
            shown(h, SkeletonWeaponDisplay.frame(second, 3D), secondSword, 0F, true, "Second entity starts its independent switch");
            shown(h, SkeletonWeaponDisplay.frame(first, 3D), firstBow, .5F, true, "First entity keeps its earlier clock and exact bow");
            shown(h, SkeletonWeaponDisplay.frame(second, 5D), secondSword, .5F, true, "Second entity is only halfway through stowing");
            h.assertTrue(SkeletonWeaponDisplay.frame(first, 5D).stack().isEmpty(), "First entity has already reached its separate empty interval");
        } finally { cleanup(first); cleanup(second); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void clearingTrackingStateNeverReplaysAnOldWeaponWhenTheEntityIsObservedAgain(GameTestHelper h) {
        var skeleton = create(h, EntityType.SKELETON);
        try {
            ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
            pair(skeleton, bow, sword);
            SkeletonWeaponDisplay.frame(skeleton, 0D);
            swap(skeleton);
            SkeletonWeaponDisplay.frame(skeleton, 1D);
            SkeletonWeaponDisplay.clear(skeleton);
            shown(h, SkeletonWeaponDisplay.frame(skeleton, 3D), sword, 0F, false, "Fresh tracking presents only the actual current sword");
            h.assertTrue(SkeletonWeaponDisplay.hidesOffhand(skeleton), "Retracking still hides the real stored bow");
        } finally { cleanup(skeleton); }
        h.succeed();
    }
}
