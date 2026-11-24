package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.NativeMobEquipment;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.entity.ShieldBlockGoal;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.ShieldGuardRules;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native shield damage gates, spawn decisions and AI lifecycle. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MobShieldGameTests {
    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true); mob.setNoGravity(true);
        mob.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 10, 1))));
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); mob.setHealth(200F);
        ((MobSpawnTypeAccessor)mob).mite$setSpawnType(net.minecraft.world.entity.MobSpawnType.NATURAL);
        return mob;
    }
    private static Player player(GameTestHelper h, Mob mob, double distance) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(mob.position().add(0D, 0D, distance));
        player.setNoGravity(true); player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); player.setHealth(200F);
        return player;
    }
    private static long seed(Predicate<RandomSource> predicate) {
        for (long seed = 0L; seed < 100_000L; ++seed) if (predicate.test(RandomSource.create(seed))) return seed;
        throw new AssertionError("Missing deterministic shield RNG seed");
    }
    private static void shield(Mob mob, String material) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(MiteShields.get(material)));
        mob.setYRot(0F); mob.setYHeadRot(0F); mob.yBodyRot = 0F;
    }
    private static void raise(Mob mob) {
        mob.startUsingItem(InteractionHand.OFF_HAND);
        for (int tick = 0; tick < 6; ++tick) mob.tick();
    }

    @GameTest(template = "empty")
    public static void spawnRollCreatesOneWornShieldMatchingEveryMetalWithoutReplacingWeapon(GameTestHelper h) {
        for (String material : List.of("rusted_iron", "copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium")) {
            Mob mob = mob(h, EntityType.ZOMBIE);
            String weaponId = material.equals("iron") ? "minecraft:iron_sword" : material.equals("gold")
                    ? "minecraft:golden_sword" : "mite:" + material + "_sword";
            ItemStack weapon = new ItemStack(MiteTools.get(weaponId)); weapon.setDamageValue(123);
            mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            mob.getRandom().setSeed(seed(r -> r.nextFloat() < MobShields.SPAWN_CHANCE));
            h.assertTrue(MobShields.populate(mob), "Deterministic successful spawn roll equips " + material);
            h.assertTrue(mob.getMainHandItem() == weapon && weapon.getDamageValue() == 123, "Existing actual main stack is unchanged");
            h.assertTrue(mob.getOffhandItem().is(MiteShields.get(material)) && mob.getOffhandItem().getDamageValue() > 0,
                    "Shield follows the weapon's metal and has generated equipment wear");
            ItemStack generated = mob.getOffhandItem();
            mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            h.assertFalse(MobShields.populate(mob), "Removing a shield never repeats the spawn roll");
            h.assertTrue(mob.getOffhandItem().isEmpty(), "The removed shield is not duplicated");
            h.assertTrue(!generated.isEmpty(), "Previously generated shield remains one independent real stack");
            mob.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unarmedHumanoidsCanSpawnWithBothWeaponAndShield(GameTestHelper h) {
        for (var type : List.of(EntityType.ZOMBIE, EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE, EntityType.VINDICATOR)) {
            Mob mob = mob(h, type); mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            mob.getRandom().setSeed(seed(r -> r.nextFloat() < MobShields.SPAWN_CHANCE));
            h.assertTrue(MobShields.populate(mob), "Each eligible unarmed humanoid can draw sword and shield");
            String material = MobShields.fallbackMaterial(NativeMobEquipment.day(mob.level().getDayTime()));
            h.assertTrue(MobShields.melee(mob.getMainHandItem()) && mob.getOffhandItem().is(MiteShields.get(material)),
                    "A shield roll always also leaves a real melee weapon");
            mob.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedRollRangedWeaponsAndExistingOffhandsArePreserved(GameTestHelper h) {
        for (ItemStack main : List.of(new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW), new ItemStack(Items.TRIDENT))) {
            Mob mob = mob(h, EntityType.ZOMBIE); mob.setItemSlot(EquipmentSlot.MAINHAND, main);
            h.assertFalse(MobShields.populate(mob), "Ranged/two-hand weapons are never replaced with generated sword/shield");
            h.assertTrue(mob.getMainHandItem() == main && mob.getOffhandItem().isEmpty(), "Ranged equipment stays intact"); mob.discard();
        }
        Mob skeleton = mob(h, EntityType.SKELETON);
        ItemStack sword = new ItemStack(Items.IRON_SWORD), spare = new ItemStack(Items.BOW);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, sword); skeleton.setItemSlot(EquipmentSlot.OFFHAND, spare);
        h.assertFalse(MobShields.populate(skeleton), "The skeleton bow/sword storage slot is never overwritten");
        h.assertTrue(skeleton.getOffhandItem() == spare && skeleton.getMainHandItem() == sword, "Both stored original weapons remain");
        Mob failed = mob(h, EntityType.ZOMBIE);
        failed.getRandom().setSeed(seed(r -> r.nextFloat() >= MobShields.SPAWN_CHANCE));
        h.assertFalse(MobShields.populate(failed), "The chance also produces a genuine unarmed result");
        failed.getRandom().setSeed(seed(r -> r.nextFloat() < MobShields.SPAWN_CHANCE));
        h.assertFalse(MobShields.populate(failed), "An unsuccessful spawn roll is final");
        Mob cow = mob(h, EntityType.COW);
        h.assertFalse(MobShields.populate(cow), "Animals never receive monster equipment");
        skeleton.discard(); failed.discard(); cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadOnlyInstallsOneGoalWithoutGeneratingOrOverwritingEquipment(GameTestHelper h) {
        Mob mob = mob(h, EntityType.ZOMBIE);
        MobShields.join(new EntityJoinLevelEvent(mob, h.getLevel(), true));
        MobShields.install(mob); MobShields.install(mob);
        h.assertValueEqual(mob.goalSelector.getAvailableGoals().stream().filter(g -> g.getGoal() instanceof ShieldBlockGoal).count(), 1L,
                "Loading and duplicate join paths install exactly one guard goal");
        h.assertFalse(MobShields.populate(mob), "A loaded old entity never receives a new equipment roll");
        h.assertTrue(mob.getMainHandItem().isEmpty() && mob.getOffhandItem().isEmpty(), "Old equipment is left unchanged");
        CompoundTag saved = mob.saveWithoutId(new CompoundTag());
        Mob restored = mob(h, EntityType.ZOMBIE); restored.load(saved);
        h.assertFalse(MobShields.populate(restored), "Spawn decision persists through NBT round-trip");
        mob.discard(); restored.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualBlockingHasNativeRaiseDelayAndAllowsRearDamage(GameTestHelper h) {
        Mob mob = mob(h, EntityType.ZOMBIE); shield(mob, "copper"); Player player = player(h, mob, 2D);
        var damage = h.getLevel().damageSources().playerAttack(player);
        mob.startUsingItem(InteractionHand.OFF_HAND);
        h.assertFalse(mob.isDamageSourceBlocked(damage), "Raising a shield cannot instantly block an incoming hit");
        for (int tick = 0; tick < 6; ++tick) mob.tick();
        h.assertTrue(mob.isDamageSourceBlocked(damage), "Native five-tick delay enables the front arc");
        float health = mob.getHealth(); mob.hurt(damage, 4F);
        h.assertTrue(mob.getHealth() == health, "The actual front attack is blocked through native LivingEntity.hurt");
        player.setPos(mob.position().add(0D, 0D, -2D)); mob.invulnerableTime = 0;
        h.assertFalse(mob.isDamageSourceBlocked(damage), "Walking behind the same raised shield bypasses it");
        mob.hurt(damage, 4F); h.assertTrue(mob.getHealth() < health, "A real rear attack damages the guarding monster");
        mob.discard(); player.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void axesInterruptRaisedMobIronShieldsForTheirMaterialCooldown(GameTestHelper h) {
        int cooldown = ShieldGuardRules.profile("iron").cooldownTicks();
        for (String axe : List.of("minecraft:iron_axe", "mite:copper_axe", "mite:ancient_metal_battle_axe", "mite:rusted_iron_hatchet")) {
            Mob mob = mob(h, EntityType.ZOMBIE); shield(mob, "iron"); Player player = player(h, mob, 2D);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get(axe))); raise(mob);
            h.assertTrue(mob.isBlocking(), "The fixture has completed native raise delay");
            mob.hurt(h.getLevel().damageSources().playerAttack(player), 4F);
            h.assertFalse(mob.isUsingItem(), "A real blocked axe attack interrupts " + axe);
            h.assertValueEqual(mob.getPersistentData().getLong(MobShields.DISABLED_UNTIL), h.getLevel().getGameTime() + cooldown,
                    "Interrupted mob shields use the iron shield cooldown");
            ShieldBlockGoal goal = new ShieldBlockGoal(mob); mob.setTarget(player); mob.tickCount += 20;
            h.assertFalse(goal.canUse(), "AI cannot raise a disabled shield again");
            mob.discard(); player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void guardGoalSharesPursuitAndLeavesPredictableCounterattackWindows(GameTestHelper h) {
        Mob mob = mob(h, EntityType.ZOMBIE); shield(mob, "silver"); Player player = player(h, mob, 2D); mob.setTarget(player);
        ShieldBlockGoal goal = new ShieldBlockGoal(mob);
        h.assertTrue(goal.getFlags().isEmpty(), "Shield actions do not monopolize MOVE or LOOK and stop pursuit");
        h.assertFalse(goal.canUse(), "Freshly observing a target does not produce an instant perfect block");
        mob.tickCount += 10; mob.getRandom().setSeed(seed(r -> r.nextFloat() < .55F));
        h.assertTrue(goal.canUse(), "A nearby visible player can trigger an anticipatory guard roll"); goal.start();
        h.assertTrue(MobShields.defending(mob), "The AI uses the actual offhand shield");
        int ticks = 0;
        while (goal.canContinueToUse() && ticks < 40) { goal.tick(); ++ticks; }
        h.assertTrue(ticks >= 18 && ticks <= 30, "Guard is finite and lasts the intended 18–30 ticks");
        goal.stop(); h.assertFalse(mob.isUsingItem(), "The end of guard lowers the shield");
        mob.tickCount += 23;
        h.assertFalse(goal.canUse(), "At least 24 vulnerable ticks remain before another guard attempt");
        mob.tickCount += 100; mob.getRandom().setSeed(seed(r -> r.nextFloat() < .55F));
        player.setPos(mob.position().add(0D, 0D, 7D));
        h.assertFalse(goal.canUse(), "Distant players do not keep shields raised");
        player.setPos(mob.position().add(0D, 0D, 2D)); mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        h.assertFalse(goal.canUse(), "Switching to a ranged weapon disables the shield action");
        mob.discard(); player.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void raisedShieldsPreventMonsterAttacksUntilTheGuardEnds(GameTestHelper h) {
        for (var type : List.of(EntityType.ZOMBIE, MiteMobs.type(MobKind.LONGDEAD), MiteMobs.type(MobKind.REVENANT))) {
            Mob mob = mob(h, type);
            if (mob instanceof R196Skeleton skeleton) skeleton.setSkeletonType(2);
            shield(mob, "ancient_metal"); Player player = player(h, mob, 1D); mob.setTarget(player);
            mob.startUsingItem(InteractionHand.OFF_HAND);
            float before = player.getHealth();
            h.assertFalse(mob.doHurtTarget(player), "Native and source overrides cannot attack through a raised shield");
            h.assertTrue(player.getHealth() == before, "The blocked outgoing attack does not damage the player");
            mob.stopUsingItem();
            h.assertTrue(mob.doHurtTarget(player) && player.getHealth() < before, "Lowering shield restores normal real melee attacks");
            mob.discard(); player.discard();
        }
        h.succeed();
    }
}
