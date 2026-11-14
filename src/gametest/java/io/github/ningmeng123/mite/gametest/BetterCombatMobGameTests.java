package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.combat.MobWeaponReach;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional Better Combat integration through native mob attacks and source goals. @author NingMeng123 */
@GameTestHolder("mite_bettercombat")
@PrefixGameTestTemplate(false)
public final class BetterCombatMobGameTests {
    private BetterCombatMobGameTests() {}

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 10, 2))));
        mob.setYRot(0F);
        mob.setYHeadRot(0F);
        mob.yBodyRot = 0F;
        mob.tickCount = 100;
        mob.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        mob.setHealth(200F);
        return mob;
    }

    private static Mob armed(GameTestHelper h, EntityType<? extends Mob> type) {
        Mob mob = mob(h, type);
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        return mob;
    }

    private static Mob target(GameTestHelper h, Mob attacker) {
        Mob target = mob(h, EntityType.COW);
        target.setPos(attacker.position().add(0D, 0D, 1.25D));
        attacker.setTarget(target);
        return target;
    }

    private static boolean absent(GameTestHelper h) {
        if (BetterCombatBridge.enabled()) return false;
        h.succeed();
        return true;
    }

    private static BetterCombatBridge.Profile profile(GameTestHelper h, Mob attacker) {
        var profile = BetterCombatBridge.profile(attacker, 0);
        h.assertTrue(profile != null, "The real installed Better Combat weapon/tool profile is resolved");
        h.assertTrue(profile.windupTicks() > 0 && profile.cooldownTicks() >= profile.windupTicks(),
                "The profile contains a positive windup inside its full attack interval");
        return profile;
    }

    private static void advance(Mob attacker, LivingEntity target, int ticks) {
        for (int i = 0; i < ticks; ++i) {
            ++attacker.tickCount;
            target.invulnerableTime = 0;
            BetterMobCombat.tick(attacker);
        }
    }

    private static void begin(GameTestHelper h, Mob attacker, Mob target) {
        float health = target.getHealth();
        h.assertFalse(attacker.doHurtTarget(target), "Native melee schedules the impact without reporting an early hit");
        h.assertTrue(BetterMobCombat.attacking(attacker), "Native melee starts a real pending weapon swing");
        h.assertValueEqual(target.getHealth(), health, "The target takes no immediate windup damage");
    }

    private static void registryProfileAndPlayerCap(GameTestHelper h, Mob attacker, Mob target, Player player, ResourceLocation id) {
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The tested weapon/tool is a real registered item: " + id);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        attacker.setItemSlot(EquipmentSlot.MAINHAND, stack);
        var profile = BetterCombatBridge.profile(attacker, 0);
        h.assertTrue(profile != null, "Better Combat's actual weapon registry resolves " + id);
        h.assertTrue(BetterMobCombat.supported(attacker), "The registered weapon/tool enables hostile melee: " + id);
        player.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
        player.setPos(attacker.position());
        // Native equipment detection applies this real item's range modifiers to the player attributes.
        player.tick();
        player.setPos(attacker.position());
        double playerReach = SourcePlayerReach.meleeReach(player, target);
        h.assertTrue(profile.reach(attacker, target) <= Math.max(0D, playerReach - .2D) + .000001D,
                "The mob's resolved weapon range stays at least one fifth of a block beneath the same-weapon player cap: " + id);
    }

    private static void excludedToolProfile(GameTestHelper h, Mob attacker, ResourceLocation id) {
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The excluded tool is a real registered item: " + id);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BuiltInRegistries.ITEM.get(id)));
        h.assertTrue(BetterCombatBridge.profile(attacker, 0) == null,
                "The non-axe tool does not select a Better Combat melee profile: " + id);
        h.assertFalse(BetterMobCombat.supported(attacker), "The non-axe tool retains the existing mob behavior: " + id);
    }

    @GameTest(template = "empty")
    public static void disabledCompatibilityKeepsArmedGoalAndBrainMeleeImmediate(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(false)) {
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.SKELETON,
                    EntityType.WITHER_SKELETON, EntityType.VINDICATOR, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE)) {
                Mob attacker = armed(h, type);
                Mob target = target(h, attacker);
                try {
                    float health = target.getHealth();
                    h.assertFalse(BetterMobCombat.supported(attacker), "Disabled compatibility never claims an armed monster: " + type);
                    h.assertFalse(BetterMobCombat.canStrikeFrom(attacker, target, attacker.position()),
                            "Disabled compatibility does not replace native or Source range checks: " + type);
                    h.assertFalse(BetterMobCombat.request(attacker, target), "Disabled compatibility starts no weapon swing: " + type);
                    h.assertTrue(attacker.doHurtTarget(target) && target.getHealth() < health,
                            "The real native melee path applies immediate damage while disabled: " + type);
                    h.assertFalse(BetterMobCombat.attacking(attacker), "Native damage leaves no compatibility recovery: " + type);
                    if (type == EntityType.WITHER_SKELETON)
                        h.assertTrue(target.hasEffect(MobEffects.WITHER), "Native secondary effects remain immediate while disabled");
                } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void disablingDuringWindupReleasesMovementAndBrainCooldownAndAllowsFreshReenable(GameTestHelper h) {
        if (absent(h)) return;
        try (var compatibility = new BetterCombatMobSettings(true)) {
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.PIGLIN)) {
                Mob attacker = armed(h, type);
                Mob target = target(h, attacker);
                try {
                    var profile = profile(h, attacker);
                    ResourceLocation movement = ResourceLocation.fromNamespaceAndPath("mite", "mob_attack_movement");
                    begin(h, attacker, target);
                    h.assertTrue(attacker.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(movement),
                            "The pending swing installs its real movement modifier");
                    boolean brain = attacker.getBrain().checkMemory(MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED);
                    if (brain) attacker.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, 100L);
                    float health = target.getHealth();
                    try (var disabled = new BetterCombatMobSettings(false)) {
                        h.assertFalse(BetterMobCombat.attacking(attacker), "Disabling immediately releases the native attack gate");
                        BetterMobCombat.beforeTick(new EntityTickEvent.Pre(attacker));
                        h.assertFalse(attacker.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(movement),
                                "The disabled pre-tick removes the interrupted movement modifier before native AI runs");
                        if (brain) h.assertFalse(attacker.getBrain().checkMemory(MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.VALUE_PRESENT),
                                "The interrupted Better Combat recovery cannot block the native brain attack");
                        h.assertValueEqual(target.getHealth(), health, "Disabling cannot settle the queued impact early");
                        h.assertTrue(attacker.doHurtTarget(target) && target.getHealth() < health,
                                "An interrupted monster can immediately use its original native damage path");
                        if (brain) {
                            attacker.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, 20L);
                            BetterMobCombat.afterTick(new EntityTickEvent.Post(attacker));
                            h.assertTrue(attacker.getBrain().checkMemory(MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.VALUE_PRESENT),
                                    "The disabled post-tick preserves the fresh cooldown written by the native brain attack");
                        }
                    }
                    h.assertFalse(BetterMobCombat.attacking(attacker), "Reenabling does not revive the old pending attack or recovery");
                    target.invulnerableTime = 0;
                    begin(h, attacker, target);
                    health = target.getHealth();
                    advance(attacker, target, profile.windupTicks());
                    h.assertTrue(target.getHealth() < health, "Reenabling starts a fresh delayed attack before the old recovery boundary");
                } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void absentModLeavesNativeMeleeImmediateAndControllerInactive(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (BetterCombatBridge.enabled()) { h.succeed(); return; }
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.WITHER_SKELETON,
                    EntityType.VINDICATOR, EntityType.PIGLIN)) {
                Mob attacker = armed(h, type);
                Mob target = target(h, attacker);
                try {
                    float health = target.getHealth();
                    h.assertFalse(BetterMobCombat.supported(attacker), "Absent optional mod does not claim the mob");
                    h.assertFalse(BetterMobCombat.request(attacker, target), "Absent optional mod starts no custom swing");
                    h.assertTrue(attacker.doHurtTarget(target) && target.getHealth() < health,
                            "The actual native melee path still applies immediate damage without Better Combat");
                    h.assertFalse(BetterMobCombat.attacking(attacker), "Fallback melee leaves no pending attack state");
                } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void armedGoalAndBrainMonstersReceiveDelayedNativeMelee(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.SKELETON,
                    EntityType.WITHER_SKELETON, EntityType.VINDICATOR, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE)) {
                Mob attacker = armed(h, type);
                Mob target = target(h, attacker);
                try {
                    var profile = profile(h, attacker);
                    h.assertTrue(BetterMobCombat.supported(attacker), "Armed hostile goal/brain family is supported: " + type);
                    begin(h, attacker, target);
                    float before = target.getHealth();
                    advance(attacker, target, profile.windupTicks());
                    h.assertTrue(target.getHealth() < before, "The scheduled native attack reaches its actual impact: " + type);
                } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void piglinBrainTargetWorksWithoutAMobTargetField(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.PIGLIN);
            Mob target = target(h, attacker);
            try {
                attacker.setTarget(null);
                attacker.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
                var profile = profile(h, attacker);
                begin(h, attacker, target);
                float before = target.getHealth();
                advance(attacker, target, profile.windupTicks());
                h.assertTrue(target.getHealth() < before, "The native brain's real target survives until the delayed impact");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void nativeAndSourceRangeChecksUseTheSameArmedController(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.WITHER_SKELETON,
                    EntityType.VINDICATOR, EntityType.PIGLIN)) {
                Mob attacker = armed(h, type);
                Mob target = target(h, attacker);
                try {
                    h.assertTrue(BetterMobCombat.canStrike(attacker, target), "A nearby target is inside weapon reach");
                    h.assertTrue(attacker.isWithinMeleeAttackRange(target), "Native goal/brain range accepts the same nearby target");
                    if (attacker instanceof PathfinderMob pathfinder)
                        h.assertTrue(SourceMeleeGoal.canStrike(pathfinder, target), "Source goal range accepts the same nearby target");
                    target.setPos(attacker.position().add(0D, 0D, 20D));
                    h.assertFalse(BetterMobCombat.canStrike(attacker, target), "Weapon reach rejects a remote target");
                    h.assertFalse(attacker.isWithinMeleeAttackRange(target), "Native goal/brain range rejects the remote target");
                    if (attacker instanceof PathfinderMob pathfinder)
                        h.assertFalse(SourceMeleeGoal.canStrike(pathfinder, target), "Source goal range rejects the remote target");
                } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void sourceGoalStartsTheWeaponWindupBeforeDoingDamage(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                var goal = new SourceMeleeGoal((PathfinderMob)attacker, 1D, false);
                float health = target.getHealth();
                goal.tick();
                h.assertTrue(BetterMobCombat.attacking(attacker), "The actual source melee goal starts the compatibility swing");
                h.assertValueEqual(target.getHealth(), health, "The source goal cannot apply damage before the weapon impact");
                advance(attacker, target, profile.windupTicks());
                h.assertTrue(target.getHealth() < health, "The source goal's delayed swing still reaches real mob damage");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void windupAndRecoveryPreventInstantAndRepeatedHits(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                float original = target.getHealth();
                begin(h, attacker, target);
                h.assertFalse(BetterMobCombat.request(attacker, target), "Repeated requests during windup cannot start duplicate attacks");
                advance(attacker, target, profile.windupTicks() - 1);
                h.assertValueEqual(target.getHealth(), original, "The tick immediately before the upswing has no damage");
                advance(attacker, target, 1);
                float firstHit = target.getHealth();
                h.assertTrue(firstHit < original, "The configured upswing tick applies the first real hit");
                target.invulnerableTime = 0;
                h.assertFalse(attacker.doHurtTarget(target), "The recovery interval rejects an immediate second native attack");
                h.assertValueEqual(target.getHealth(), firstHit, "Repeated native requests cannot bypass the weapon cooldown");
                advance(attacker, target, profile.cooldownTicks() + 2);
                h.assertValueEqual(target.getHealth(), firstHit, "One request never creates a second unrequested impact");
                h.assertTrue(BetterMobCombat.request(attacker, target), "A new attack becomes possible after weapon recovery");
                h.assertValueEqual(target.getHealth(), firstHit, "The next attack also begins with its windup");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void witherSkeletonAppliesItsNativeEffectOnlyAtActualImpact(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.WITHER_SKELETON);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                begin(h, attacker, target);
                h.assertFalse(target.hasEffect(MobEffects.WITHER), "Scheduling a native skeleton attack cannot apply wither early");
                advance(attacker, target, profile.windupTicks());
                h.assertTrue(target.hasEffect(MobEffects.WITHER), "The real delayed native skeleton hit retains its wither effect");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void stationaryAiHeadFacingControlsTheDelayedWeaponHit(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                h.assertTrue(profile.angle() < 360D, "The sword fixture has a directional attack");
                attacker.setYRot(180F);
                attacker.yBodyRot = 180F;
                attacker.setYHeadRot(180F);
                attacker.getLookControl().setLookAt(target, 180F, 180F);
                attacker.getLookControl().tick();
                h.assertTrue(Math.abs(attacker.getYHeadRot()) < .001F && attacker.getYRot() == 180F,
                        "The real AI look controller faces the nearby target without changing the stationary entity yaw");
                float health = target.getHealth();
                begin(h, attacker, target);
                advance(attacker, target, profile.windupTicks());
                h.assertTrue(target.getHealth() < health,
                        "The delayed native weapon hit follows the AI's actual aim despite its old movement and body yaw");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void movingBehindTheUnturnedHeadStillAvoidsTheDelayedHit(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                h.assertTrue(profile.angle() < 360D, "The sword fixture has a directional attack");
                float health = target.getHealth();
                begin(h, attacker, target);
                target.setPos(attacker.position().add(0D, 0D, -1.25D));
                attacker.setYRot(180F);
                attacker.yBodyRot = 180F;
                h.assertTrue(BetterMobCombat.canStrike(attacker, target),
                        "The target remains physically reachable after moving behind the unchanged head direction");
                advance(attacker, target, profile.windupTicks());
                h.assertValueEqual(target.getHealth(), health,
                        "Entity and body rotation cannot hit a target behind the AI's head before its aim catches up");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void movingOutOfWeaponReachDuringWindupMakesTheAttackMiss(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                float health = target.getHealth();
                begin(h, attacker, target);
                target.setPos(attacker.position().add(0D, 0D, 20D));
                advance(attacker, target, profile.cooldownTicks() + profile.windupTicks() + 2);
                h.assertValueEqual(target.getHealth(), health, "A target which escaped during windup receives no remote damage");
                h.assertFalse(BetterMobCombat.attacking(attacker), "The missed swing eventually completes");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void glassPlacedDuringWindupBlocksTheActualWeaponImpact(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            BlockPos wall = attacker.blockPosition().offset(0, 0, 1);
            try {
                var profile = profile(h, attacker);
                float health = target.getHealth();
                begin(h, attacker, target);
                for (int y = 0; y < 3; ++y) for (int x = -1; x <= 1; ++x)
                    h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.GLASS.defaultBlockState(), 3);
                h.assertFalse(BetterMobCombat.canStrike(attacker, target), "Transparent glass still physically blocks weapon reach");
                advance(attacker, target, profile.cooldownTicks() + profile.windupTicks() + 2);
                h.assertValueEqual(target.getHealth(), health, "Impact rechecks a wall built after the attack started");
            } finally {
                for (int y = 0; y < 3; ++y) for (int x = -1; x <= 1; ++x)
                    h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
                BetterMobCombat.cancel(attacker); attacker.discard(); target.discard();
            }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void switchingWeaponDuringWindupCancelsTheOldImpact(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                float health = target.getHealth();
                begin(h, attacker, target);
                attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                advance(attacker, target, 1);
                h.assertFalse(BetterMobCombat.request(attacker, target), "Changing weapons cannot reset the spent attack recovery");
                advance(attacker, target, profile.cooldownTicks() + profile.windupTicks() + 2);
                h.assertValueEqual(target.getHealth(), health, "A canceled sword impact cannot occur with the replacement tool");
                h.assertFalse(BetterMobCombat.attacking(attacker), "The canceled sword swing finishes its original recovery");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void raisingARealShieldDuringWindupCancelsTheAttack(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                attacker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                float health = target.getHealth();
                begin(h, attacker, target);
                attacker.startUsingItem(InteractionHand.OFF_HAND);
                h.assertTrue(MobShields.defending(attacker), "The cancellation fixture uses an actual raised offhand shield");
                advance(attacker, target, 1);
                advance(attacker, target, profile.cooldownTicks() + profile.windupTicks() + 2);
                h.assertValueEqual(target.getHealth(), health, "A mob cannot complete its delayed attack through its own shield");
                h.assertFalse(BetterMobCombat.attacking(attacker), "The shield-canceled swing finishes its original recovery");
            } finally { attacker.stopUsingItem(); BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void emptyHandsRangedWeaponsAndAnimalsKeepTheirExistingBehavior(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            for (ItemStack weapon : List.of(ItemStack.EMPTY, new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW),
                    new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_HOE),
                    new ItemStack(MiteTools.get("mite:copper_mattock")), new ItemStack(MiteTools.get("mite:copper_shears")),
                    new ItemStack(MiteTools.get("mite:copper_scythe")), new ItemStack(MiteTools.get("mite:flint_knife")))) {
                Mob attacker = armed(h, EntityType.ZOMBIE);
                Mob target = target(h, attacker);
                try {
                    attacker.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                    float health = target.getHealth();
                    h.assertFalse(BetterMobCombat.supported(attacker), "Empty hands, ranged weapons and non-axe tools do not select weapon melee");
                    h.assertFalse(BetterMobCombat.request(attacker, target), "Excluded equipment starts no compatibility attack");
                    h.assertTrue(attacker.doHurtTarget(target) && target.getHealth() < health,
                            "The excluded equipment retains direct native melee behavior when explicitly invoked");
                    h.assertFalse(BetterMobCombat.attacking(attacker), "Excluded equipment leaves no pending attack");
                } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            }
            Mob animal = armed(h, EntityType.COW);
            Mob target = target(h, animal);
            try {
                h.assertFalse(BetterMobCombat.supported(animal), "Holding a sword does not turn an animal into an armed monster");
                h.assertFalse(BetterMobCombat.request(animal, target), "Animal behavior is not replaced by the compatibility controller");
            } finally { BetterMobCombat.cancel(animal); animal.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void armedAxesAlsoUseTheRealWeaponAttackProfile(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                var profile = profile(h, attacker);
                h.assertTrue(BetterMobCombat.supported(attacker), "An armed hostile axe receives the Better Combat weapon profile");
                float health = target.getHealth();
                begin(h, attacker, target);
                advance(attacker, target, profile.windupTicks());
                h.assertTrue(target.getHealth() < health, "An axe's delayed swing uses the actual native damage path");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void unequippingAndReequippingCannotBypassTheSpentWeaponCooldown(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                float health = target.getHealth();
                begin(h, attacker, target);
                attacker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                advance(attacker, target, 1);
                h.assertFalse(BetterMobCombat.supported(attacker), "The unequipped mob no longer selects a weapon profile");
                h.assertFalse(attacker.doHurtTarget(target), "A direct empty-hand attack cannot bypass already spent weapon recovery");
                h.assertValueEqual(target.getHealth(), health, "Unequipping during windup produces no immediate unarmed damage");
                attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                h.assertFalse(BetterMobCombat.request(attacker, target), "Reequipping the sword cannot reset its previous cooldown");
                h.assertFalse(attacker.doHurtTarget(target), "A direct reequipped sword attack also respects the previous cooldown");
                advance(attacker, target, profile.cooldownTicks() - 2);
                h.assertFalse(BetterMobCombat.request(attacker, target), "The tick immediately before the original recovery boundary remains blocked");
                h.assertValueEqual(target.getHealth(), health, "No canceled weapon impact survives the temporary empty hand");
                advance(attacker, target, 1);
                h.assertTrue(BetterMobCombat.request(attacker, target), "The reequipped sword can start again exactly at the original recovery boundary");
                h.assertValueEqual(target.getHealth(), health, "A legal new swing still begins with its own windup");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void losingTheTrackedTargetCancelsItsPendingImpact(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                float health = target.getHealth();
                begin(h, attacker, target);
                attacker.setTarget(null);
                advance(attacker, target, 1);
                attacker.setTarget(target);
                advance(attacker, target, profile.cooldownTicks() + profile.windupTicks() + 2);
                h.assertValueEqual(target.getHealth(), health,
                        "Losing the tracked target cancels the swing even if the same target returns before impact");
                h.assertFalse(BetterMobCombat.attacking(attacker), "The canceled tracked-target swing completes its recovery");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void realNativeMeleeGoalStartsAgainAtTheWeaponRecoveryBoundary(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.VINDICATOR);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                var goal = new MeleeAttackGoal((PathfinderMob)attacker, 1D, false);
                float health = target.getHealth();
                goal.tick();
                h.assertTrue(BetterMobCombat.attacking(attacker), "The real native melee goal begins a pending weapon swing");
                h.assertValueEqual(target.getHealth(), health, "The native goal does not skip the weapon windup");
                for (int i = 1; i < profile.cooldownTicks(); ++i) {
                    advance(attacker, target, 1);
                    goal.tick();
                }
                float firstHit = target.getHealth();
                h.assertTrue(firstHit < health, "The first native goal swing reaches actual weapon damage");
                advance(attacker, target, 1);
                h.assertFalse(BetterMobCombat.attacking(attacker), "The first swing has finished at its configured weapon interval");
                goal.tick();
                h.assertTrue(BetterMobCombat.attacking(attacker),
                        "The next actual native goal swing starts at weapon recovery without an extra fixed twenty-tick delay");
                h.assertValueEqual(target.getHealth(), firstHit, "The next native goal swing starts with windup instead of immediate damage");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void piglinBrainAttackMemoryTracksTheActualWeaponRecovery(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.PIGLIN);
            Mob target = target(h, attacker);
            try {
                var profile = profile(h, attacker);
                h.assertTrue(attacker.getBrain().checkMemory(MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED),
                        "The actual piglin brain registers its melee recovery memory");
                begin(h, attacker, target);
                // Native MeleeAttack writes its fixed interval after doHurtTarget returns.
                attacker.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, 20L);
                BetterMobCombat.tick(attacker);
                h.assertValueEqual(attacker.getBrain().getTimeUntilExpiry(MemoryModuleType.ATTACK_COOLING_DOWN),
                        (long)profile.cooldownTicks(), "The controller replaces the brain's fixed interval with weapon recovery");
                advance(attacker, target, 1);
                h.assertValueEqual(attacker.getBrain().getTimeUntilExpiry(MemoryModuleType.ATTACK_COOLING_DOWN),
                        (long)profile.cooldownTicks() - 1L, "Brain recovery follows the remaining weapon interval");
                advance(attacker, target, profile.cooldownTicks() - 1);
                h.assertTrue(attacker.getBrain().getMemory(MemoryModuleType.ATTACK_COOLING_DOWN).isEmpty(),
                        "The piglin brain is allowed to request another attack exactly when weapon recovery ends");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void weaponAndAxeFamiliesResolveProfilesWhileOtherToolFamiliesRemainExcluded(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            player.setNoGravity(true);
            var allowed = EnumSet.of(ToolFamily.SWORD, ToolFamily.DAGGER, ToolFamily.CLUB, ToolFamily.CUDGEL,
                    ToolFamily.BATTLE_AXE, ToolFamily.WAR_HAMMER, ToolFamily.AXE, ToolFamily.HATCHET);
            var visited = EnumSet.noneOf(ToolFamily.class);
            try {
                for (var definition : MiteTools.definitions()) {
                    visited.add(definition.family());
                    if (allowed.contains(definition.family()))
                        registryProfileAndPlayerCap(h, attacker, target, player, ResourceLocation.parse(definition.id()));
                    else excludedToolProfile(h, attacker, ResourceLocation.parse(definition.id()));
                }
                h.assertTrue(visited.equals(EnumSet.allOf(ToolFamily.class)),
                        "Every MITE tool family is checked as either a weapon/axe or an excluded utility tool");
                for (String material : List.of("wooden", "stone", "iron", "golden", "diamond", "netherite")) {
                    for (String family : List.of("sword", "axe"))
                        registryProfileAndPlayerCap(h, attacker, target, player,
                                ResourceLocation.fromNamespaceAndPath("minecraft", material + "_" + family));
                    for (String family : List.of("pickaxe", "shovel", "hoe"))
                        excludedToolProfile(h, attacker, ResourceLocation.fromNamespaceAndPath("minecraft", material + "_" + family));
                }
                excludedToolProfile(h, attacker, ResourceLocation.parse("minecraft:shears"));
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); player.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void axeAndHatchetProfilesProduceActualDelayedNativeImpacts(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            for (String id : List.of("mite:copper_axe", "mite:flint_hatchet", "minecraft:iron_axe", "mite:iron_hatchet")) {
                Mob attacker = armed(h, EntityType.ZOMBIE);
                Mob target = target(h, attacker);
                try {
                    attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get(id)));
                    var profile = profile(h, attacker);
                    float health = target.getHealth();
                    begin(h, attacker, target);
                    advance(attacker, target, profile.windupTicks() - 1);
                    h.assertValueEqual(target.getHealth(), health, "The shared tool profile retains its real windup: " + id);
                    advance(attacker, target, 1);
                    h.assertTrue(target.getHealth() < health, "The shared tool profile reaches actual native melee damage: " + id);
                } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
            }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void simplySwordsAddedAndOriginalFamiliesUseRegistryProfilesWithinPlayerReach(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
            Mob attacker = armed(h, EntityType.ZOMBIE);
            Mob target = target(h, attacker);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            player.setNoGravity(true);
            int added = 0;
            int original = 0;
            try {
                for (var id : BuiltInRegistries.ITEM.keySet()) {
                    if (!id.getNamespace().equals("mite") && !id.getNamespace().equals("simplyswords")) continue;
                    var definition = SimplySwordsCompat.definition(new ItemStack(BuiltInRegistries.ITEM.get(id)));
                    if (definition == null || !SimplySwordsCompat.families().contains(definition.family())
                            || !List.of("copper", "gold", "diamond").contains(definition.material())) continue;
                    registryProfileAndPlayerCap(h, attacker, target, player, id);
                    if (definition.added()) ++added; else ++original;
                }
                h.assertValueEqual(added, SimplySwordsCompat.families().size(),
                        "Every MITE-added copper Simply Swords family is sampled through the actual registry");
                h.assertValueEqual(original, SimplySwordsCompat.families().size() * 2,
                        "Every original gold and diamond Simply Swords family is sampled through the actual registry");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); player.discard(); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void simplySwordsLongAndShortWeaponsKeepDistinctRangesAtActualImpact(GameTestHelper h) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(h)) return;
            if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
            double multiplier = MiteConfig.WEAPON_REACH_MULTIPLIER.get();
            double reduction = MiteConfig.WEAPON_REACH_REDUCTION.get();
            try {
                MiteConfig.WEAPON_REACH_MULTIPLIER.set(1D);
                MiteConfig.WEAPON_REACH_REDUCTION.set(0D);
                MiteConfig.refreshEnchantmentRules();
                simplySwordsDistanceCase(h, "cutlass", 2.75D, 2.1D, 1.8D, 1.8D, true);
                simplySwordsDistanceCase(h, "cutlass", 2.75D, 2.1D, 2D, 2D, false);
                simplySwordsDistanceCase(h, "cutlass", 2.75D, 2.1D, 2.9D, 2.9D, false);
                simplySwordsDistanceCase(h, "glaive", 3.5D, 3.5D, 2.9D, 2.9D, true);
                simplySwordsDistanceCase(h, "glaive", 3.5D, 3.5D, 3.4D, 3.4D, false);
                simplySwordsDistanceCase(h, "glaive", 3.5D, 3.5D, 3.7D, 3.7D, false);
                simplySwordsDistanceCase(h, "cutlass", 2.75D, 2.1D, 1.8D, 2.9D, false);
                simplySwordsDistanceCase(h, "glaive", 3.5D, 3.5D, 2.9D, 3.7D, false);
                simplySwordsDistanceCase(h, "greathammer", 4D, 3.25D, 2.9D, 2.9D, true);
                simplySwordsDistanceCase(h, "greathammer", 4D, 3.25D, 3.1D, 3.1D, false);
                simplySwordsDistanceCase(h, "spear", 4D, 3.75D, 3.4D, 3.4D, true);
                simplySwordsDistanceCase(h, "spear", 4D, 3.75D, 3.65D, 3.65D, false);
            } finally {
                MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
                MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
                MiteConfig.refreshEnchantmentRules();
            }
            h.succeed();
        }
    }

    private static void simplySwordsDistanceCase(GameTestHelper h, String family, double expectedRange, double expectedPlayerReach,
            double startDistance, double impactDistance, boolean hits) {
        Mob attacker = armed(h, EntityType.ZOMBIE);
        Mob target = target(h, attacker);
        try {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mite", "simplyswords_vibranium_" + family);
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The screenshot's actual vibranium weapon is registered: " + id);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BuiltInRegistries.ITEM.get(id)));
            var profile = profile(h, attacker);
            h.assertTrue(Math.abs(profile.range() - expectedRange) < .000001D,
                    "The actual Better Combat weapon registry retains its distinct native range: " + id);
            double expectedMobReach = expectedPlayerReach - .2D;
            h.assertTrue(Math.abs(profile.reach(attacker, target) - expectedMobReach) < .000001D,
                    "The mob retains this weapon's distinct player range minus the fixed margin: " + id);
            placeAtEyeDistance(attacker, target, startDistance);
            float health = target.getHealth();
            if (startDistance > expectedMobReach) {
                h.assertFalse(BetterMobCombat.canStrike(attacker, target),
                        "A target outside the balanced " + expectedMobReach + "-block range cannot begin a swing: " + id);
                h.assertFalse(BetterMobCombat.request(attacker, target),
                        "A rejected remote request creates no pending weapon impact: " + id);
                advance(attacker, target, profile.windupTicks());
                h.assertValueEqual(target.getHealth(), health, "A rejected remote swing causes no damage: " + id);
                return;
            }
            h.assertTrue(BetterMobCombat.canStrike(attacker, target),
                    "The balanced " + expectedMobReach + "-block range admits its target at " + startDistance + ": " + id);
            begin(h, attacker, target);
            placeAtEyeDistance(attacker, target, impactDistance);
            advance(attacker, target, profile.windupTicks() - 1);
            h.assertValueEqual(target.getHealth(), health, "The longer weapon still spends its actual windup: " + id);
            advance(attacker, target, 1);
            if (hits) h.assertTrue(target.getHealth() < health,
                    "The delayed native impact damages a target inside this weapon's own range: " + id);
            else h.assertValueEqual(target.getHealth(), health,
                    "The delayed impact rechecks the weapon's own range after its target retreats: " + id);
        } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
    }

    private static void placeAtEyeDistance(Mob attacker, Mob target, double distance) {
        // Keep both feet at the same height while measuring the guard's eye-to-collision-box distance.
        double vertical = Math.max(0D, attacker.getEyeHeight() - target.getBbHeight());
        double forward = Math.sqrt(distance * distance - vertical * vertical) + target.getBbWidth() * .5D;
        target.setPos(attacker.position().add(0D, 0D, forward));
    }

    @GameTest(template = "empty")
    public static void shortComboRangeAppliesReductionBeforeTheMarginAndRechecksTheStoredMoveAtImpact(GameTestHelper helper) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(helper)) return;
            try (ReachSettings ignored = new ReachSettings(1D, .5D)) {
                Loaded.shortCombo(helper);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void scaleChangesDuringWindupUseTheCurrentPlayerEquivalentWeaponLimit(GameTestHelper helper) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(helper)) return;
            try (ReachSettings ignored = new ReachSettings(1D, 0D)) {
                Loaded.scaleChange(helper);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void oldToolReachCannotDoubleTheRangeButRealItemModifiersStillApply(GameTestHelper helper) {
        try (var compatibility = new BetterCombatMobSettings(true)) {
            if (absent(helper)) return;
            try (ReachSettings ignored = new ReachSettings(1D, 0D)) {
                Loaded.legacyRange(helper);
            }
            helper.succeed();
        }
    }

    private static void placeAtShoulderDistance(Mob attacker, Mob target, double distance) {
        double shoulderHeight = attacker.getEyeHeight() - attacker.getBbHeight() * .15D * attacker.getAgeScale();
        double vertical = Math.max(0D, shoulderHeight - target.getBbHeight());
        double forward = Math.sqrt(distance * distance - vertical * vertical) + target.getBbWidth() * .5D;
        target.setPos(attacker.position().add(0D, 0D, forward));
    }

    private static final class ReachSettings implements AutoCloseable {
        private final double multiplier = MiteConfig.WEAPON_REACH_MULTIPLIER.get();
        private final double reduction = MiteConfig.WEAPON_REACH_REDUCTION.get();

        private ReachSettings(double multiplier, double reduction) {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
            MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
            MiteConfig.refreshEnchantmentRules();
        }
        @Override public void close() {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
            MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
            MiteConfig.refreshEnchantmentRules();
        }
    }

    private static final class Loaded {
        private static net.bettercombat.api.WeaponAttributes.Attack move(float multiplier) {
            return new net.bettercombat.api.WeaponAttributes.Attack(null,
                    net.bettercombat.api.WeaponAttributes.HitBoxShape.FORWARD_BOX,
                    1D, 1F, multiplier, 180D, .5D, "bettercombat:one_handed_stab", null, null, List.of());
        }

        private static net.bettercombat.api.WeaponAttributes attributes(net.bettercombat.api.WeaponAttributes.Attack... moves) {
            return new net.bettercombat.api.WeaponAttributes(3D, 0D, null, null, false, "mite_mob_reach_test", moves, null);
        }

        private static void shortCombo(GameTestHelper helper) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(Items.IRON_SWORD);
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(Items.IRON_SWORD));
            net.bettercombat.logic.WeaponRegistry.register(id, attributes(move(.6F), move(1.25F)));
            try {
                shortCase(helper, .9D, 1.05D, true);
                shortCase(helper, 1.2D, 1.2D, false);
                shortCase(helper, .9D, 1.2D, false);
            } finally { net.bettercombat.logic.WeaponRegistry.register(id, previous); }
        }

        private static void shortCase(GameTestHelper helper, double start, double finish, boolean hits) {
            Mob attacker = armed(helper, EntityType.ZOMBIE);
            Mob target = target(helper, attacker);
            try {
                var profile = profile(helper, attacker);
                helper.assertTrue(Math.abs(profile.range() - 1.8D) < .000001D, "Native profile data retains the actual short-move multiplier");
                helper.assertTrue(Math.abs(BetterCombatBridge.playerEquivalentRange(attacker, attacker.getMainHandItem(), profile.rangeMultiplier()) - 1.3D) < .000001D,
                        "The half-block configured reduction follows the actual move multiplier before the monster margin");
                helper.assertTrue(Math.abs(MobWeaponReach.sourceLimit(attacker, target, attacker.getY()) - 1.55D) < .000001D,
                        "The source 2.25-block sword minus .5 reduction and .2 monster margin is 1.55, still wider than this short move");
                placeAtShoulderDistance(attacker, target, start);
                float health = target.getHealth();
                if (start > 1.1D) {
                    helper.assertFalse(BetterMobCombat.canStrike(attacker, target), "A position in the player-only margin cannot begin the short move");
                    helper.assertFalse(BetterMobCombat.request(attacker, target), "A remote short-move request starts no recovery or impact");
                } else {
                    begin(helper, attacker, target);
                    placeAtShoulderDistance(attacker, target, finish);
                    advance(attacker, target, profile.windupTicks() - 1);
                    helper.assertValueEqual(target.getHealth(), health, "Short moves still wait their complete native windup");
                    advance(attacker, target, 1);
                }
                if (hits) helper.assertTrue(target.getHealth() < health, "A short move inside its final 1.1-block cap damages through the real native path");
                else helper.assertValueEqual(target.getHealth(), health, "The final impact cannot use the next combo's longer multiplier or the player's final fifth of a block");
            } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); }
        }

        private static void scaleChange(GameTestHelper helper) {
            float influence = net.bettercombat.BetterCombatMod.getConfig().entity_scale_attack_range_influence;
            net.bettercombat.BetterCombatMod.getConfig().entity_scale_attack_range_influence = 1F;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(Items.IRON_SWORD);
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(Items.IRON_SWORD));
            net.bettercombat.logic.WeaponRegistry.register(id, attributes(move(1F)));
            Mob attacker = armed(helper, EntityType.ZOMBIE);
            Mob target = target(helper, attacker);
            try {
                var profile = profile(helper, attacker);
                placeAtShoulderDistance(attacker, target, 1.4D);
                float health = target.getHealth();
                begin(helper, attacker, target);
                attacker.getAttribute(Attributes.SCALE).setBaseValue(.5D);
                attacker.refreshDimensions();
                helper.assertTrue(Math.abs(BetterCombatBridge.playerEquivalentRange(attacker, attacker.getMainHandItem(), profile.rangeMultiplier()) - 1.5D) < .000001D,
                        "The current half-scale range replaces the range recorded before windup");
                advance(attacker, target, profile.windupTicks());
                helper.assertValueEqual(target.getHealth(), health, "Changing scale during windup prevents an otherwise out-of-range delayed impact");
                advance(attacker, target, profile.cooldownTicks());
                placeAtShoulderDistance(attacker, target, 1.25D);
                helper.assertTrue(BetterMobCombat.request(attacker, target), "The same scaled attacker can begin a fresh move inside its final 1.3-block cap");
                advance(attacker, target, profile.windupTicks());
                helper.assertTrue(target.getHealth() < health, "The scaled range cap still allows legitimate nearby native damage");
            } finally {
                BetterMobCombat.cancel(attacker); attacker.discard(); target.discard();
                net.bettercombat.logic.WeaponRegistry.register(id, previous);
                net.bettercombat.BetterCombatMod.getConfig().entity_scale_attack_range_influence = influence;
            }
        }

        private static void legacyRange(GameTestHelper helper) {
            for (boolean simply : new boolean[]{false, true}) {
                if (simply && !SimplySwordsCompat.enabled()) continue;
                Mob attacker = armed(helper, EntityType.ZOMBIE);
                Mob target = target(helper, attacker);
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                try {
                    ItemStack weapon = simply ? new ItemStack(SimplySwordsCompat.weapon("vibranium", "glaive")) : new ItemStack(Items.IRON_SWORD);
                    var modifiers = ItemAttributeModifiers.builder();
                    weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> modifiers.add(attribute, modifier, EquipmentSlotGroup.MAINHAND));
                    modifiers.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(ResourceLocation.parse("mite:tool_reach"),
                            .75D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                    modifiers.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(ResourceLocation.parse("mite:mob_external_reach_test"),
                            1.25D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                    weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.build());
                    attacker.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                    player.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
                    player.setPos(attacker.position());
                    player.tick();
                    player.setPos(attacker.position());
                    var profile = profile(helper, attacker);
                    helper.assertTrue(Math.abs(MobWeaponReach.itemRange(attacker) - 4.25D) < .000001D,
                            "The legacy three-quarter block is ignored while the real external five-quarter block survives");
                    helper.assertTrue(Math.abs(profile.itemRange() - 4.25D) < .000001D
                                    && Math.abs(BetterCombatBridge.playerEquivalentRange(attacker, weapon, 1D) - 4.25D) < .000001D,
                            "Explicit real item range overrides metadata without restoring the obsolete modifier");
                    helper.assertTrue(profile.reach(attacker, target) <= Math.max(0D, SourcePlayerReach.sourceMeleeReach(player, target) - .2D) + .000001D,
                            "The same legacy-bearing item cannot give the monster a longer source cap than the real equipped player");
                } finally { BetterMobCombat.cancel(attacker); attacker.discard(); target.discard(); player.discard(); }
            }
        }
    }
}
