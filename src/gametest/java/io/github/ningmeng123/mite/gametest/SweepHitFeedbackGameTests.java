package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.survival.DualFoodData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real vanilla sweep transactions must earn their extra knockback through a successful hit. @author NingMeng123 */
@GameTestHolder("mite_damage_audit")
@PrefixGameTestTemplate(false)
public final class SweepHitFeedbackGameTests {
    private static final Vec3 EXISTING_MOTION = new Vec3(.11D, .07D, -.13D);

    private SweepHitFeedbackGameTests() {}

    @GameTest(template = "empty")
    public static void successfulSweepKeepsOneNativeAndOneExtraKnockbackAfterDamage(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Cow secondary = fixture.secondary(.4D, 1.7D);
            int[] primaryKnockbacks = {0}, secondaryKnockbacks = {0};
            Consumer<LivingKnockBackEvent> observe = event -> {
                if (event.getEntity() == fixture.primary) primaryKnockbacks[0]++;
                if (event.getEntity() == secondary) {
                    secondaryKnockbacks[0]++;
                    helper.assertTrue(secondary.getHealth() < secondary.getMaxHealth(),
                            "Both accepted sweep knockbacks follow the actual health transaction");
                }
            };
            NeoForge.EVENT_BUS.addListener(observe);
            try { fixture.attack(); }
            finally { NeoForge.EVENT_BUS.unregister(observe); }
            close(helper, secondary.getMaxHealth() - secondary.getHealth(), 1D,
                    "The real sword sweep still removes its ordinary one point of health");
            helper.assertValueEqual(secondaryKnockbacks[0], 2,
                    "Accepted sweeping invokes native hurt knockback and the captured extra knockback once each");
            helper.assertValueEqual(primaryKnockbacks[0], 1,
                    "The primary target keeps its original native hurt knockback");
            helper.assertTrue(!secondary.getDeltaMovement().equals(EXISTING_MOTION),
                    "A successful sweep adds knockback to the target's existing motion");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sweepOutsideSourceReachPreservesHealthAndExistingMotion(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Cow secondary = fixture.secondary(0D, 2.8D);
            helper.assertFalse(SourcePlayerReach.canMelee(fixture.player, secondary),
                    "The secondary target is outside the sword's MITE reach");
            helper.assertTrue(fixture.player.distanceToSqr(secondary) < 9D,
                    "The same target remains inside the native three-block sweep candidate gate");
            helper.assertTrue(fixture.player.getMainHandItem().getSweepHitBox(fixture.player, fixture.primary)
                            .intersects(secondary.getBoundingBox()),
                    "The native sweep box actually includes the rejected secondary target");
            int[] knockbacks = {0};
            Consumer<LivingKnockBackEvent> observe = event -> {
                if (event.getEntity() == secondary) knockbacks[0]++;
            };
            NeoForge.EVENT_BUS.addListener(observe);
            try { fixture.attack(); }
            finally { NeoForge.EVENT_BUS.unregister(observe); }
            close(helper, secondary.getHealth(), secondary.getMaxHealth(), "Out-of-reach sweeping removes no health");
            helper.assertValueEqual(knockbacks[0], 0, "A rejected sweep cannot invoke extra knockback");
            helper.assertTrue(secondary.getDeltaMovement().equals(EXISTING_MOTION),
                    "Rejecting a sweep preserves motion that already existed before the attack");
            helper.assertValueEqual(secondary.hurtTime, 0, "Rejected reach creates no new hurt animation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sweepRejectedByHurtImmunityDoesNotAddKnockback(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Cow secondary = fixture.secondary(.4D, 1.7D);
            helper.assertTrue(secondary.hurt(fixture.player.damageSources().playerAttack(fixture.player), 4F),
                    "An earlier real hit establishes native hurt immunity");
            secondary.setHealth(secondary.getMaxHealth());
            secondary.setDeltaMovement(EXISTING_MOTION);
            helper.assertTrue(secondary.invulnerableTime > 10,
                    "The actual secondary target is still in the strict native hurt-immunity window");
            int[] knockbacks = {0};
            Consumer<LivingKnockBackEvent> observe = event -> {
                if (event.getEntity() == secondary) knockbacks[0]++;
            };
            NeoForge.EVENT_BUS.addListener(observe);
            try { fixture.attack(); }
            finally { NeoForge.EVENT_BUS.unregister(observe); }
            close(helper, secondary.getHealth(), secondary.getMaxHealth(), "Hurt immunity rejects this weaker sweep");
            helper.assertValueEqual(knockbacks[0], 0, "An immunity-rejected sweep creates no extra knockback");
            helper.assertTrue(secondary.getDeltaMovement().equals(EXISTING_MOTION),
                    "Immunity leaves the target's pre-existing motion intact");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void absorptionOnlySweepStillKeepsTheAcceptedExtraKnockback(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Cow secondary = fixture.secondary(.4D, 1.7D);
            secondary.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(4D);
            secondary.setAbsorptionAmount(4F);
            int[] knockbacks = {0};
            Consumer<LivingKnockBackEvent> observe = event -> {
                if (event.getEntity() == secondary) knockbacks[0]++;
            };
            NeoForge.EVENT_BUS.addListener(observe);
            try { fixture.attack(); }
            finally { NeoForge.EVENT_BUS.unregister(observe); }
            close(helper, secondary.getHealth(), secondary.getMaxHealth(), "Absorption covers the entire sweep health cost");
            close(helper, secondary.getAbsorptionAmount(), 3D, "The accepted sweep actually removes absorption");
            helper.assertValueEqual(knockbacks[0], 2,
                    "Absorption loss still earns exactly the native and extra sweep knockbacks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledSweepKnockbackDoesNotUndoDamageOrReplaceExistingMotion(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Cow secondary = fixture.secondary(.4D, 1.7D);
            int[] canceled = {0};
            Consumer<LivingKnockBackEvent> cancel = event -> {
                if (event.getEntity() == secondary) {
                    canceled[0]++;
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try { fixture.attack(); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            close(helper, secondary.getMaxHealth() - secondary.getHealth(), 1D,
                    "Canceling knockback does not cancel the real sweep damage");
            helper.assertValueEqual(canceled[0], 2,
                    "Both native and deferred knockback still use the real cancellable NeoForge event");
            helper.assertTrue(secondary.getDeltaMovement().equals(EXISTING_MOTION),
                    "Canceling both knockbacks preserves the target's earlier motion");
        }
        helper.succeed();
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .0001D, message + ": " + actual + " vs " + expected);
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<Cow> targets = new ArrayList<>();
        private final Boolean betterCombatSweeping;
        private final Player player;
        private final Cow primary;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 15, 1))));
            player.setNoGravity(true);
            player.setOnGround(true);
            player.setSpeed(.1F);
            player.setSprinting(false);
            player.experienceLevel = 0;
            player.getFoodData().setFoodLevel(6);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4D);
            player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
            player.getAttribute(Attributes.SWEEPING_DAMAGE_RATIO).setBaseValue(0D);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            helper.assertTrue(sword.canPerformAction(ItemAbilities.SWORD_SWEEP),
                    "The held real iron sword retains the modern native sweeping ability");
            helper.assertTrue(player.getAttackStrengthScale(.5F) > .9F,
                    "The fixture reaches full native attack strength without depending on tick scheduling");
            primary = cow(0D, 1.2D);
            betterCombatSweeping = BetterCombatBridge.enabled() ? LoadedSweeping.enable() : null;
        }

        private Cow secondary(double x, double z) {
            Cow target = cow(x, z);
            target.setDeltaMovement(EXISTING_MOTION);
            return target;
        }

        private Cow cow(double x, double z) {
            Cow target = EntityType.COW.create(helper.getLevel());
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            target.setHealth(100F);
            target.setPos(player.position().add(x, 0D, z));
            helper.getLevel().addFreshEntity(target);
            target.setOnGround(true);
            targets.add(target);
            return target;
        }

        private void attack() {
            helper.assertTrue(SourcePlayerReach.canMelee(player, primary), "The primary target is genuinely in reach");
            player.attack(primary);
            helper.assertTrue(primary.getHealth() < primary.getMaxHealth(), "The actual primary attack succeeds");
            helper.assertTrue(primary.getDeltaMovement().lengthSqr() > 0D,
                    "The primary attack preserves ordinary native knockback");
        }

        @Override public void close() {
            targets.forEach(Cow::discard);
            player.discard();
            if (betterCombatSweeping != null) LoadedSweeping.restore(betterCombatSweeping);
        }
    }

    /** Optional Better Combat config types are loaded only when that mod is actually installed. */
    private static final class LoadedSweeping {
        private static boolean enable() {
            boolean before = net.bettercombat.BetterCombatMod.getConfig().allow_vanilla_sweeping;
            net.bettercombat.BetterCombatMod.getConfig().allow_vanilla_sweeping = true;
            return before;
        }

        private static void restore(boolean enabled) {
            net.bettercombat.BetterCombatMod.getConfig().allow_vanilla_sweeping = enabled;
        }
    }
}
