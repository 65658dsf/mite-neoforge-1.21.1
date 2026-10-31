package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.combat.MobWeaponReach;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.MobAttackBoundsAccess;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Weapon reach caps use the real player metric without extending historical mob reach. @author NingMeng123 */
@GameTestHolder("mite_mob_reach")
@PrefixGameTestTemplate(false)
public final class MobWeaponReachGameTests {
    private static final double EPSILON = .00001D;
    private static final ResourceLocation LEGACY_REACH = ResourceLocation.fromNamespaceAndPath("mite", "tool_reach");

    private MobWeaponReachGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite_targeting")
    public static void equippedWeaponCapsMatchTheActualSurvivalPlayerAfterConfigurationAndHeight(GameTestHelper helper) {
        for (double[] settings : new double[][]{{1D, 0D}, {.75D, .25D}, {1.5D, .25D}, {0D, 0D}, {1D, 16D}}) {
            try (ReachSettings ignored = new ReachSettings(settings[0], settings[1], true);
                    Fixture fixture = new Fixture(helper, EntityType.ZOMBIE)) {
                for (ItemStack weapon : List.of(new ItemStack(Items.IRON_SWORD),
                        new ItemStack(Items.IRON_AXE), new ItemStack(MiteTools.get("mite:iron_scythe")))) {
                    fixture.equip(weapon);
                    helper.assertTrue(MobWeaponReach.applies(fixture.attacker), "The actual held melee weapon receives the mob cap: " + weapon);
                    for (double height : new double[]{-2.5D, -1.5D, 0D, 1.5D, 2.5D}) {
                        fixture.target.setPos(fixture.attacker.position().add(0D, -height, 1D));
                        double playerReach = SourcePlayerReach.sourceMeleeReach(fixture.player, fixture.target);
                        double expected = Math.max(0D, playerReach - MobWeaponReach.MARGIN);
                        close(helper, MobWeaponReach.sourceLimit(fixture.attacker, fixture.target, fixture.attacker.getY()),
                                expected, "The equipped survival player and mob use the same configuration and height before the fixed margin");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_targeting")
    public static void eyeToHitboxAndPlannedFeetRespectAdultBabyAndSpiderBodies(GameTestHelper helper) {
        try (ReachSettings ignored = new ReachSettings(.75D, .25D, false)) {
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.SPIDER, EntityType.CAVE_SPIDER)) {
                for (boolean baby : new boolean[]{false, true}) {
                    if (baby && type != EntityType.ZOMBIE) continue;
                    try (Fixture fixture = new Fixture(helper, type)) {
                        if (fixture.attacker instanceof Zombie zombie) zombie.setBaby(baby);
                        fixture.equip(new ItemStack(Items.IRON_SWORD));
                        double limit = MobWeaponReach.sourceLimit(fixture.attacker, fixture.target, fixture.attacker.getY());
                        fixture.atDistance(limit - .001D, 0D);
                        helper.assertTrue(MobWeaponReach.canReachFrom(fixture.attacker, fixture.target, fixture.attacker.position()),
                                "The actual eye and target collision box admit the point just inside the cap: " + type + ", baby=" + baby);
                        fixture.atDistance(limit + .001D, 0D);
                        helper.assertFalse(MobWeaponReach.canReachFrom(fixture.attacker, fixture.target, fixture.attacker.position()),
                                "Body width and low eyes cannot extend the cap: " + type + ", baby=" + baby);
                        Vec3 original = fixture.attacker.position();
                        Vec3 planned = original.add(0D, .5D, .25D);
                        boolean plannedReach = MobWeaponReach.canReachFrom(fixture.attacker, fixture.target, planned);
                        fixture.attacker.setPos(planned);
                        helper.assertValueEqual(MobWeaponReach.canReachFrom(fixture.attacker, fixture.target, fixture.attacker.position()),
                                plannedReach, "A virtual route endpoint uses the same eye position as actual movement");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_targeting")
    public static void nativeArmedAttacksRecheckTheShorterCapAfterTheTargetRetreats(GameTestHelper helper) {
        // Ordinary swords now retain a three-block player base; keep this cap below native reach.
        try (ReachSettings ignored = new ReachSettings(.5D, .5D, false)) {
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.VINDICATOR,
                    EntityType.PIGLIN, EntityType.CAVE_SPIDER)) {
                try (Fixture fixture = new Fixture(helper, type)) {
                    fixture.equip(new ItemStack(Items.IRON_SWORD));
                    double limit = MobWeaponReach.sourceLimit(fixture.attacker, fixture.target, fixture.attacker.getY());
                    fixture.atDistance(limit - .3D, 0D);
                    helper.assertTrue(fixture.attacker.isWithinMeleeAttackRange(fixture.target),
                            "Native melee admits a genuinely close target before it moves: " + type);
                    fixture.atDistance(limit + .05D, 0D);
                    helper.assertTrue(((MobAttackBoundsAccess)fixture.attacker).mite$attackBoundingBox().intersects(fixture.target.getBoundingBox()),
                            "The retreat stays inside the native attack volume, isolating the shorter configured cap");
                    helper.assertTrue(SourcePlayerReach.canMelee(fixture.player, fixture.target),
                            "The escaped target remains inside the same weapon's survival player range: " + type);
                    helper.assertFalse(fixture.attacker.isWithinMeleeAttackRange(fixture.target),
                            "Native melee now rejects the player-only distance band: " + type);
                    float before = fixture.target.getHealth();
                    fixture.attacker.doHurtTarget(fixture.target);
                    fixture.finishSwing();
                    helper.assertValueEqual(fixture.target.getHealth(), before,
                            "A stale preflight or direct native attack cannot damage the escaped target: " + type);
                    helper.assertFalse(fixture.target.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
                            "A rejected cave-spider hit cannot apply its secondary poison: " + type);
                }
                try (Fixture fixture = new Fixture(helper, type)) {
                    fixture.equip(new ItemStack(Items.IRON_SWORD));
                    fixture.atDistance(MobWeaponReach.sourceLimit(fixture.attacker, fixture.target, fixture.attacker.getY()) - .3D, 0D);
                    helper.assertTrue(fixture.attacker.isWithinMeleeAttackRange(fixture.target)
                                    && MobWeaponReach.canReachFrom(fixture.attacker, fixture.target, fixture.attacker.position()),
                            "The approaching target satisfies both the original native or BC gate and the new cap: " + type);
                    float before = fixture.target.getHealth();
                    fixture.attacker.doHurtTarget(fixture.target);
                    fixture.finishSwing();
                    helper.assertTrue(fixture.target.getHealth() < before,
                            "The cap still permits a real native hit after approaching closely enough: " + type);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_targeting")
    public static void directMobAttackDamageCannotBypassTheFinalDistanceCheck(GameTestHelper helper) {
        try (ReachSettings ignored = new ReachSettings(.75D, .25D, false);
                Fixture fixture = new Fixture(helper, EntityType.ZOMBIE)) {
            fixture.equip(new ItemStack(Items.IRON_SWORD));
            Player victim = fixture.player;
            double limit = MobWeaponReach.sourceLimit(fixture.attacker, victim, fixture.attacker.getY());
            fixture.atDistance(victim, limit + .05D, 0D);
            float before = victim.getHealth();
            helper.assertFalse(victim.hurt(fixture.attacker.damageSources().mobAttack(fixture.attacker), 4F),
                    "Direct native MOB_ATTACK damage cannot bypass the held weapon's final cap");
            helper.assertValueEqual(victim.getHealth(), before, "The rejected direct damage removes no health");
            fixture.atDistance(victim, limit - .05D, 0D);
            victim.invulnerableTime = 0;
            helper.assertTrue(victim.hurt(fixture.attacker.damageSources().mobAttack(fixture.attacker), 4F)
                            && victim.getHealth() < before,
                    "The same real damage pipeline remains usable at a legal nearby distance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_targeting")
    public static void sourceGoalsKeepTheirHistoricalGateAndUseTheCapAtPlannedAttackPositions(GameTestHelper helper) {
        try (var compatibility = new BetterCombatMobSettings(false);
                ReachSettings ignored = new ReachSettings(1.5D, 0D, false);
                Fixture fixture = new Fixture(helper, EntityType.ZOMBIE)) {
            fixture.equip(new ItemStack(Items.IRON_SWORD));
            fixture.target.setPos(fixture.attacker.position().add(0D, 0D, 1.96D));
            helper.assertTrue(MobWeaponReach.canReachFrom(fixture.attacker, fixture.target, fixture.attacker.position()),
                    "The generous configured cap is not itself the historical source distance gate");
            helper.assertFalse(SourceMeleeGoal.canStrike((PathfinderMob)fixture.attacker, fixture.target),
                    "Adding the player-relative cap does not expand the old source sword reach");
            fixture.target.setPos(fixture.attacker.position().add(0D, 0D, 1.94D));
            helper.assertTrue(SourceMeleeGoal.canStrike((PathfinderMob)fixture.attacker, fixture.target),
                    "The historical source sword boundary remains usable inside the new cap");
        }
        try (ReachSettings ignored = new ReachSettings(.5D, .5D, false);
                Fixture fixture = new Fixture(helper, EntityType.ZOMBIE)) {
            fixture.equip(new ItemStack(Items.IRON_SWORD));
            fixture.atDistance(MobWeaponReach.sourceLimit(fixture.attacker, fixture.target, fixture.attacker.getY()) + .05D, 0D);
            PathfinderMob attacker = (PathfinderMob)fixture.attacker;
            helper.assertFalse(SourceMeleeGoal.canStrike(attacker, fixture.target),
                    "The old source sword feet distance cannot bypass a tighter configured player cap");
            Vec3 planned = attacker.position().add(0D, 0D, .1D);
            helper.assertTrue(planned.distanceTo(fixture.target.position()) < 1.95D,
                    "The planned endpoint stays inside the historical source sword gate");
            helper.assertTrue(SourceMeleeGoal.canStrikeFrom(attacker, fixture.target, planned),
                    "The route planner can still select a genuinely closer attack endpoint");
            float before = fixture.target.getHealth();
            SourceMeleeGoal goal = new SourceMeleeGoal(attacker, 1D, false);
            goal.tick();
            helper.assertValueEqual(fixture.target.getHealth(), before,
                    "The real installed source attack path does not deal damage before closing the gap");
            attacker.setPos(planned);
            goal.tick();
            helper.assertTrue(fixture.target.getHealth() < before,
                    "Moving to the planned endpoint lets the actual goal land its native hit");
            goal.stop();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_targeting")
    public static void legacyToolRangeIsIgnoredWhileRealRangeComponentsStillContributeExactlyOnce(GameTestHelper helper) {
        try (ReachSettings ignored = new ReachSettings(1D, 0D, false);
                Fixture fixture = new Fixture(helper, EntityType.ZOMBIE)) {
            ItemStack original = new ItemStack(Items.IRON_SWORD);
            fixture.equip(original);
            double originalRange = MobWeaponReach.itemRange(fixture.attacker);
            double originalLimit = MobWeaponReach.sourceLimit(fixture.attacker, fixture.target, fixture.attacker.getY());
            fixture.equip(withRange(original, LEGACY_REACH, 8D));
            close(helper, MobWeaponReach.itemRange(fixture.attacker), originalRange,
                    "An obsolete saved mite:tool_reach component cannot add eight blocks to mob range");
            close(helper, MobWeaponReach.sourceLimit(fixture.attacker, fixture.target, fixture.attacker.getY()), originalLimit,
                    "Legacy reach filtering happens before the player-relative cap");
            fixture.equip(withRange(original, ResourceLocation.fromNamespaceAndPath("mite", "mob_reach_component_test"), .5D));
            close(helper, MobWeaponReach.itemRange(fixture.attacker), originalRange + .5D,
                    "A real modern held-item range modifier is preserved exactly once");
            close(helper, MobWeaponReach.sourceLimit(fixture.attacker, fixture.target, fixture.attacker.getY()),
                    Math.max(0D, SourcePlayerReach.sourceMeleeReach(fixture.player, fixture.target) - MobWeaponReach.MARGIN),
                    "The same actual range component yields the same player base before the mob margin");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_targeting")
    public static void zeroConfiguredReachRejectsEvenOverlappingArmedDamageButUnarmedBehaviorIsUnchanged(GameTestHelper helper) {
        try (ReachSettings ignored = new ReachSettings(0D, 0D, false);
                Fixture fixture = new Fixture(helper, EntityType.ZOMBIE)) {
            fixture.equip(new ItemStack(Items.IRON_SWORD));
            fixture.target.setPos(fixture.attacker.position());
            close(helper, MobWeaponReach.sourceLimit(fixture.attacker, fixture.target, fixture.attacker.getY()), 0D,
                    "The configured player range and fixed margin clamp safely to zero");
            helper.assertFalse(MobWeaponReach.canReachFrom(fixture.attacker, fixture.target, fixture.attacker.position()),
                    "Zero permitted reach does not authorize an overlapping armed target");
            float before = fixture.target.getHealth();
            fixture.attacker.doHurtTarget(fixture.target);
            fixture.finishSwing();
            helper.assertValueEqual(fixture.target.getHealth(), before, "An overlapping target still takes no armed damage");
            fixture.equip(ItemStack.EMPTY);
            helper.assertFalse(MobWeaponReach.applies(fixture.attacker), "Empty hands do not opt into the held-weapon cap");
            fixture.target.setPos(fixture.attacker.position().add(0D, 0D, 1.2D));
            helper.assertTrue(SourceMeleeGoal.canStrike((PathfinderMob)fixture.attacker, fixture.target),
                    "The original nearby unarmed source attack remains available despite player weapon settings");
            helper.assertTrue(fixture.attacker.doHurtTarget(fixture.target) && fixture.target.getHealth() < before,
                    "The original actual unarmed damage path remains immediate and unchanged");
        }
        helper.succeed();
    }

    private static ItemStack withRange(ItemStack original, ResourceLocation identifier, double amount) {
        ItemStack result = original.copy();
        var modifiers = ItemAttributeModifiers.builder();
        original.forEachModifier(EquipmentSlot.MAINHAND,
                (attribute, modifier) -> modifiers.add(attribute, modifier, EquipmentSlotGroup.MAINHAND));
        modifiers.add(Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(identifier, amount, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.build());
        return result;
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON, message + ": expected=" + expected + ", actual=" + actual);
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final Mob attacker;
        private final Cow target;
        private final Player player;

        private Fixture(GameTestHelper helper, EntityType<? extends Mob> type) {
            this.helper = helper;
            attacker = type.create(helper.getLevel());
            attacker.setNoAi(true);
            attacker.setNoGravity(true);
            attacker.setOnGround(true);
            attacker.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 20, 2))));
            attacker.setYRot(0F);
            attacker.setYHeadRot(0F);
            attacker.yBodyRot = 0F;
            attacker.tickCount = 100;
            attacker.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
            target = EntityType.COW.create(helper.getLevel());
            target.setNoAi(true);
            target.setNoGravity(true);
            target.setOnGround(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
            target.setHealth(200F);
            target.setPos(attacker.position().add(0D, 0D, 1D));
            attacker.setTarget(target);
            player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setOnGround(true);
            player.setPos(attacker.position());
        }

        private void equip(ItemStack weapon) {
            Vec3 feet = attacker.position();
            attacker.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
            attacker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            attacker.tick();
            attacker.setPos(feet);
            attacker.setYRot(0F);
            attacker.setYHeadRot(0F);
            attacker.yBodyRot = 0F;
            player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.tick();
            player.setPos(feet);
            player.setDeltaMovement(Vec3.ZERO);
        }

        private void atDistance(double distance, double targetHeight) {
            atDistance(target, distance, targetHeight);
        }

        private void atDistance(Entity victim, double distance, double targetHeight) {
            victim.setPos(attacker.position().add(0D, targetHeight, 0D));
            Vec3 eye = attacker.getEyePosition();
            double verticalGap = Math.abs(eye.y - SourcePlayerReach.closestPoint(
                    victim.getBoundingBox().inflate(victim.getPickRadius()), eye).y);
            helper.assertTrue(distance >= verticalGap, "The requested hitbox distance includes the actual eye-height gap");
            double horizontalGap = Math.sqrt(Math.max(0D, distance * distance - verticalGap * verticalGap));
            victim.setPos(attacker.position().add(0D, targetHeight,
                    horizontalGap + victim.getBbWidth() * .5D + victim.getPickRadius()));
            MobWeaponReachGameTests.close(helper, eye.distanceTo(SourcePlayerReach.closestPoint(victim.getBoundingBox().inflate(victim.getPickRadius()), eye)),
                    distance, "The fixture independently measures eye-to-hitbox rather than center distance");
        }

        private void finishSwing() {
            var profile = BetterCombatBridge.profile(attacker, 0);
            if (profile == null) return;
            for (int tick = 0; tick <= profile.windupTicks(); ++tick) {
                ++attacker.tickCount;
                target.invulnerableTime = 0;
                BetterMobCombat.tick(attacker);
            }
        }

        @Override public void close() {
            BetterMobCombat.cancel(attacker);
            attacker.discard();
            target.discard();
            player.discard();
        }
    }

    private static final class ReachSettings implements AutoCloseable {
        private final double multiplier = MiteConfig.WEAPON_REACH_MULTIPLIER.get();
        private final double reduction = MiteConfig.WEAPON_REACH_REDUCTION.get();
        private final boolean heightPenalty = MiteConfig.HEIGHT_REACH_PENALTY.get();

        private ReachSettings(double multiplier, double reduction, boolean heightPenalty) {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
            MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
            MiteConfig.HEIGHT_REACH_PENALTY.set(heightPenalty);
            MiteConfig.refreshEnchantmentRules();
        }

        @Override public void close() {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
            MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
            MiteConfig.HEIGHT_REACH_PENALTY.set(heightPenalty);
            MiteConfig.refreshEnchantmentRules();
        }
    }
}
