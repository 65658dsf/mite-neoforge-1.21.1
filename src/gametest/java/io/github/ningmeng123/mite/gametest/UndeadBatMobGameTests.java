package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.BoneLordFrenzy;
import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.R196Bat;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.entity.R196Zombie;
import io.github.ningmeng123.mite.entity.VisionDimming;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent oracle constants and real equipment/contact/death paths for the eight source undead and bats. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UndeadBatMobGameTests {
    private UndeadBatMobGameTests() {}
    private static Mob mob(GameTestHelper helper, MobKind kind) {
        Mob mob = MiteMobs.type(kind).create(helper.getLevel());
        helper.assertTrue(mob != null, "Registered source entity creates " + kind);
        mob.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        mob.setNoAi(true); return mob;
    }
    private static void finalizeMob(GameTestHelper helper, Mob mob) {
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, helper.getLevel(),
                helper.getLevel().getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWN_EGG, null);
    }
    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter()); player.getInventory().clearContent(); return player;
    }
    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .00001, message + " expected=" + expected + " actual=" + actual);
    }
    @GameTest(template = "ranged_027")
    public static void EightConstructedMobsHaveSourceHealthAttackSpeedSizeAndExactExperience(GameTestHelper helper) {
        MobKind[] kinds = {MobKind.REVENANT, MobKind.LONGDEAD, MobKind.LONGDEAD_GUARDIAN, MobKind.BONE_LORD,
                MobKind.ANCIENT_BONE_LORD, MobKind.VAMPIRE_BAT, MobKind.GIANT_VAMPIRE_BAT, MobKind.NIGHTWING};
        double[][] expected = {{30,7,.26,.6,1.8,15},{12,6,.29,.6,1.8,15},{24,8,.29,.6,1.8,25},{20,5,.26,.6,1.8,15},
                {24,8,.27,.6,1.8,30},{3,1,.7,.5,.9,5},{6,2,.7,1,1.8,10},{3,1,.7,.5,.9,10}};
        for (int i = 0; i < kinds.length; ++i) {
            Mob entity = mob(helper, kinds[i]); double[] values = expected[i];
            close(helper, entity.getMaxHealth(), values[0], kinds[i] + " source health");
            close(helper, entity.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue(), values[1], kinds[i] + " source attack");
            close(helper, entity.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue(), values[2], kinds[i] + " source speed");
            close(helper, entity.getBbWidth(), values[3], kinds[i] + " source width");
            close(helper, entity.getBbHeight(), values[4], kinds[i] + " source height");
            helper.assertValueEqual(entity.getExperienceReward(helper.getLevel(), null), (int)values[5], "Equipment adds no invented XP");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void RevenantAlwaysSpawnsAdultWithAllFiveWornRustedIronItems(GameTestHelper helper) {
        for (long seed = 0; seed < 16; ++seed) {
            R196Zombie zombie = (R196Zombie)mob(helper, MobKind.REVENANT); zombie.getRandom().setSeed(seed); finalizeMob(helper, zombie);
            helper.assertTrue(!zombie.isBaby(), "Source revenant never becomes a baby or jockey");
            zombie.setBaby(true); helper.assertTrue(!zombie.isBaby(), "Source setter refuses baby state");
            helper.assertTrue(zombie.canPickUpLoot(), "Source pickup is always enabled");
            helper.assertTrue(zombie.getSpawnType() == MobSpawnType.SPAWN_EGG, "Source finalization retains modern spawn metadata");
            for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)) {
                ItemStack stack = zombie.getItemBySlot(slot);
                helper.assertTrue(!stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("rusted_iron_"),
                        "Source rusted gear " + slot);
                helper.assertTrue(stack.isDamaged() && stack.getDamageValue() < stack.getMaxDamage(), "Worn equipment remains usable");
                helper.assertTrue(EquipmentDurability.quality(stack) == Quality.POOR, "Rusted material caps source quality");
            }
            helper.assertValueEqual(zombie.getExperienceReward(helper.getLevel(), null), 15, "Five equipment pieces do not inflate source XP");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void LongdeadGetsAncientChainAndGuardianSwapsAtStrictFiveSixDistanceThresholds(GameTestHelper helper) {
        R196Skeleton guardian = (R196Skeleton)mob(helper, MobKind.LONGDEAD_GUARDIAN);
        guardian.forceSkeletonType(0); finalizeMob(helper, guardian);
        helper.assertTrue(guardian.getSpawnType() == MobSpawnType.SPAWN_EGG, "Custom skeleton finalization retains modern spawn metadata");
        helper.assertTrue(guardian.getMainHandItem().getItem() instanceof BowItem && !guardian.stowedItem().isEmpty(), "Ranged guardian stores its ancient dagger");
        for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD))
            helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(guardian.getItemBySlot(slot).getItem()).getPath().startsWith("ancient_metal_chainmail_"),
                    "Source full ancient chain " + slot);
        Cow target = new Cow(EntityType.COW, helper.getLevel()); target.setNoAi(true); guardian.setTarget(target);
        ItemStack originalBow = guardian.getMainHandItem(), originalDagger = guardian.stowedItem();
        target.setPos(guardian.getX() + 5, guardian.getY(), guardian.getZ());
        helper.assertTrue(!guardian.updateGuardianWeapon(), "Exactly five blocks keeps bow");
        target.setPos(guardian.getX() + 4.9, guardian.getY(), guardian.getZ());
        helper.assertTrue(guardian.updateGuardianWeapon() && guardian.getMainHandItem() == originalDagger, "Below five swaps the same full dagger stack");
        target.setPos(guardian.getX() + 6, guardian.getY(), guardian.getZ());
        helper.assertTrue(!guardian.updateGuardianWeapon(), "Exactly six blocks keeps dagger");
        target.setPos(guardian.getX() + 6.1, guardian.getY(), guardian.getZ());
        helper.assertTrue(guardian.updateGuardianWeapon() && guardian.getMainHandItem() == originalBow, "Above six restores the same worn bow");
        helper.assertTrue(!guardian.canHoldItem(new ItemStack(Items.DIAMOND_SWORD)), "Ranged guardian refuses unrelated weapons");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void GuardianStowedDamageQualityAndSkeletonTypeSurviveSaving(GameTestHelper helper) {
        R196Skeleton guardian = (R196Skeleton)mob(helper, MobKind.LONGDEAD_GUARDIAN); guardian.forceSkeletonType(0); finalizeMob(helper, guardian);
        EquipmentDurability.setQuality(guardian.stowedItem(), Quality.FINE); guardian.stowedItem().setDamageValue(17);
        CompoundTag saved = new CompoundTag(); guardian.addAdditionalSaveData(saved);
        R196Skeleton restored = (R196Skeleton)mob(helper, MobKind.LONGDEAD_GUARDIAN); restored.readAdditionalSaveData(saved);
        helper.assertValueEqual(restored.skeletonType(), 0, "Source skeleton subtype persists");
        helper.assertTrue(ItemStack.matches(restored.stowedItem(), guardian.stowedItem()), "Stowed stack preserves components without regeneration");
        restored.swapHeldItemWithStowed();
        helper.assertValueEqual(restored.getMainHandItem().getDamageValue(), 17, "Swapping preserves the stored wear");
        helper.assertTrue(EquipmentDurability.quality(restored.getMainHandItem()) == Quality.FINE, "Swapping preserves stored quality");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void LongdeadRangedAttackEmitsAncientArrowAndNeverHarmsOtherSkeletons(GameTestHelper helper) {
        R196Skeleton longdead = (R196Skeleton)mob(helper, MobKind.LONGDEAD); longdead.forceSkeletonType(0); finalizeMob(helper, longdead);
        Cow target = new Cow(EntityType.COW, helper.getLevel()); target.moveTo(longdead.getX() + 5, longdead.getY(), longdead.getZ());
        longdead.performRangedAttack(target, 1F);
        var arrows = helper.getLevel().getEntitiesOfClass(Arrow.class, longdead.getBoundingBox().inflate(6), a -> a.getOwner() == longdead);
        helper.assertValueEqual(arrows.size(), 1, "Real ranged goal creates one projectile");
        Arrow arrow = arrows.getFirst();
        helper.assertTrue(arrow.getPickupItemStackOrigin().is(MiteRangedItems.arrow(Material.ANCIENT_METAL)), "Ancient source ammunition survives modern arrow serialization");
        R196Skeleton guardian = (R196Skeleton)mob(helper, MobKind.LONGDEAD_GUARDIAN);
        helper.assertTrue(!guardian.hurt(helper.getLevel().damageSources().arrow(arrow, longdead), 4F), "Source skeleton friendly arrows are immune");
        close(helper, longdead.naturalDefense(helper.getLevel().damageSources().generic()), 1, "Longdead has source flat natural defense one");
        close(helper, guardian.naturalDefense(helper.getLevel().damageSources().generic()), 2, "Guardian has source flat natural defense two");
        arrows.forEach(Arrow::discard); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void SourceWitherSubtypeUsesPoorIronSwordAttackFourAndWitherHit(GameTestHelper helper) {
        R196Skeleton ancient = (R196Skeleton)mob(helper, MobKind.ANCIENT_BONE_LORD); ancient.forceSkeletonType(1); finalizeMob(helper, ancient);
        helper.assertTrue(ancient.getMainHandItem().is(Items.IRON_SWORD), "Nether subtype bypasses the ancient spawn weapon override");
        helper.assertTrue(EquipmentDurability.quality(ancient.getMainHandItem()) == Quality.POOR, "Wither sword is explicitly poor");
        close(helper, ancient.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue(), 4, "Source wither finalization overrides the leaf attack");
        close(helper, ancient.getBbWidth(), .72, "Wither subtype width"); close(helper, ancient.getBbHeight(), 2.34, "Wither subtype height");
        helper.assertTrue(!ancient.hurt(helper.getLevel().damageSources().lava(), 4), "Wither subtype rejects lava");
        Cow target = new Cow(EntityType.COW, helper.getLevel());
        helper.assertTrue(ancient.doHurtTarget(target), "Wither melee damages a living target");
        helper.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.WITHER), "Source 200 tick wither is applied");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void BoneLordRepairsSelfAndTroopsAndFrenziesOrdinaryAndAncientSkeletonsForTwentyTicks(GameTestHelper helper) {
        R196Skeleton lord = (R196Skeleton)mob(helper, MobKind.BONE_LORD); lord.setHealth(18F); helper.getLevel().addFreshEntity(lord);
        R196Skeleton troop = (R196Skeleton)mob(helper, MobKind.LONGDEAD); troop.setHealth(10F); helper.getLevel().addFreshEntity(troop);
        Skeleton ordinary = new Skeleton(EntityType.SKELETON, helper.getLevel()); ordinary.moveTo(lord.position()); ordinary.setNoAi(true);
        ordinary.setHealth(ordinary.getMaxHealth() - 2); helper.getLevel().addFreshEntity(ordinary);
        double before = troop.getAttributeValue(Attributes.ATTACK_DAMAGE), normalBefore = ordinary.getAttributeValue(Attributes.ATTACK_DAMAGE);
        lord.commandTroops();
        close(helper, lord.getHealth(), 19, "Bone lord repairs itself by one"); close(helper, troop.getHealth(), 11, "Bone lord repairs damaged ancient troop by one");
        close(helper, troop.getAttributeValue(Attributes.ATTACK_DAMAGE), before + 3, "Ancient frenzy adds half its base six attack");
        close(helper, ordinary.getAttributeValue(Attributes.ATTACK_DAMAGE), normalBefore + ordinary.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * .5,
                "Ordinary vanilla skeleton also receives source frenzy");
        helper.assertValueEqual(((BoneLordFrenzy)(Object)troop).mite$boneLordFrenzyTicks(), 20, "Source frenzy count is twenty");
        for (int i = 0; i < 20; ++i) { troop.aiStep(); ordinary.aiStep(); }
        close(helper, troop.getAttributeValue(Attributes.ATTACK_DAMAGE), before, "Expired frenzy restores exact old damage");
        close(helper, ordinary.getAttributeValue(Attributes.ATTACK_DAMAGE), normalBefore, "Ordinary frenzy also expires");
        lord.discard(); troop.discard(); ordinary.discard(); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void BoneLordSavedTroopCounterRetainsSixCapAndAncientPlateIdentity(GameTestHelper helper) {
        R196Skeleton lord = (R196Skeleton)mob(helper, MobKind.ANCIENT_BONE_LORD); lord.forceSkeletonType(2); finalizeMob(helper, lord);
        for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD))
            helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(lord.getItemBySlot(slot).getItem()).getPath().startsWith("ancient_metal_"),
                    "Ancient bone lord wears source ancient plate");
        CompoundTag sourceSave = new CompoundTag(); lord.addAdditionalSaveData(sourceSave); sourceSave.putByte("num_troops_summoned", (byte)6);
        lord.readAdditionalSaveData(sourceSave); lord.setTarget(player(helper));
        for (int i = 0; i < 32; ++i) lord.commandTroops();
        helper.assertValueEqual(lord.troopsSummoned(), 6, "Loaded full source troop counter never spawns a seventh troop");
        CompoundTag saved = new CompoundTag(); lord.addAdditionalSaveData(saved);
        helper.assertValueEqual(saved.getByte("num_troops_summoned"), (byte)6, "Source byte count persists"); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void VampireBatContactHealsOnlyRealHealthLossAndBegins1200TickFeedingCooldown(GameTestHelper helper) {
        R196Bat bat = (R196Bat)mob(helper, MobKind.VAMPIRE_BAT); bat.setResting(false); bat.setHealth(2F);
        Cow target = new Cow(EntityType.COW, helper.getLevel()); target.moveTo(bat.position()); bat.setTarget(target);
        float before = target.getHealth();
        helper.assertTrue(bat.tryContactAttack(target), "Source narrow contact box attacks its selected prey");
        close(helper, bat.getHealth(), 2 + before - target.getHealth(), "Bat heals precisely target health lost");
        helper.assertValueEqual(bat.feedCooldown(), 1200, "Full small vampire feeds for 1200 ticks");
        helper.assertValueEqual(bat.attackCooldown(), 20, "Contact attack cooldown is twenty");
        helper.assertTrue(!bat.tryContactAttack(target) && !bat.preysUpon(target), "Fed small bat pauses animal prey and contact spam");
        helper.assertTrue(bat.preysUpon(player(helper)), "Fed small bat still preys on survival players");
        CompoundTag saved = new CompoundTag(); bat.addAdditionalSaveData(saved);
        R196Bat restored = (R196Bat)mob(helper, MobKind.VAMPIRE_BAT); restored.readAdditionalSaveData(saved);
        helper.assertValueEqual(restored.feedCooldown(), 1200, "Source feed cooldown survives saving");
        restored.setHealth(2F); restored.tick();
        helper.assertValueEqual(restored.feedCooldown(), 0, "Damage ends the feeding cooldown on the next tick"); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void GiantVampireBatKeepsAnimalPreyWhenFullAndDoesNotHealBlockedDamage(GameTestHelper helper) {
        R196Bat bat = (R196Bat)mob(helper, MobKind.GIANT_VAMPIRE_BAT); bat.setResting(false); bat.setHealth(5F);
        Cow target = new Cow(EntityType.COW, helper.getLevel()); target.moveTo(bat.position()); bat.setTarget(target);
        float before = target.getHealth(); helper.assertTrue(bat.tryContactAttack(target), "Giant bat contact hit");
        close(helper, bat.getHealth(), Math.min(6, 5 + before - target.getHealth()), "Giant source health gain is clamped");
        helper.assertValueEqual(bat.feedCooldown(), 0, "Giant bat never pauses feeding"); helper.assertTrue(bat.preysUpon(target), "Full giant still targets animals");
        R196Bat blocked = (R196Bat)mob(helper, MobKind.VAMPIRE_BAT); blocked.setResting(false); blocked.setHealth(2F);
        Cow invulnerable = new Cow(EntityType.COW, helper.getLevel()); invulnerable.moveTo(blocked.position()); invulnerable.setInvulnerable(true); blocked.setTarget(invulnerable);
        helper.assertTrue(!blocked.tryContactAttack(invulnerable), "Blocked damage is not vampiric gain"); close(helper, blocked.getHealth(), 2, "Blocked hit cannot heal bat"); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void NightwingRejectsMundaneAndFireDamageButAcceptsSilverMagicAndSunlight(GameTestHelper helper) {
        Player player = player(helper);
        for (int kind = 0; kind < 3; ++kind) {
            R196Bat bat = (R196Bat)mob(helper, MobKind.NIGHTWING);
            helper.assertTrue(!bat.hurt(helper.getLevel().damageSources().generic(), 1), "Nightwing mundane immunity");
            helper.assertTrue(!bat.hurt(helper.getLevel().damageSources().lava(), 1), "Nightwing lava immunity");
            if (kind == 0) {
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(R196MobEquipment.item("mite:silver_dagger")));
                helper.assertTrue(bat.hurt(helper.getLevel().damageSources().playerAttack(player), 1), "Silver weapon defeats nightwing immunity");
            } else if (kind == 1) helper.assertTrue(bat.hurt(helper.getLevel().damageSources().magic(), 1), "Magic defeats nightwing immunity");
            else helper.assertTrue(bat.hurt(MobCombat.source(bat, "sunlight"), 1000) && !bat.isAlive(), "Source sunlight destroys nightwing");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void NightwingContactAddsSourceVisionPendingOnlyAfterSuccessfulPlayerAttack(GameTestHelper helper) {
        R196Bat bat = (R196Bat)mob(helper, MobKind.NIGHTWING); bat.setResting(false);
        Player player = player(helper); player.moveTo(bat.position()); bat.setTarget(player);
        helper.assertTrue(bat.tryContactAttack(player), "Nightwing contact hits player");
        close(helper, player.getPersistentData().getFloat(VisionDimming.PENDING), 1.25, "Source nightwing vision dimming amount");
        R196Bat blocked = (R196Bat)mob(helper, MobKind.NIGHTWING); blocked.setResting(false);
        Player immune = helper.makeMockPlayer(GameType.CREATIVE);
        // makeMockPlayer only overrides isCreative; it does not set vanilla Abilities.invulnerable.
        GameType.CREATIVE.updatePlayerAbilities(immune.getAbilities());
        immune.moveTo(blocked.position()); blocked.setTarget(immune);
        helper.assertTrue(!blocked.tryContactAttack(immune), "Creative immunity blocks the attack");
        close(helper, immune.getPersistentData().getFloat(VisionDimming.PENDING), 0, "Blocked hit adds no dimming"); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void GiantBatHangingRequiresTwoClearBlocksAndBothKindsSupportNegativeHeightCeilings(GameTestHelper helper) {
        R196Bat bat = (R196Bat)mob(helper, MobKind.VAMPIRE_BAT), giant = (R196Bat)mob(helper, MobKind.GIANT_VAMPIRE_BAT);
        BlockPos ceiling = helper.absolutePos(new BlockPos(2,5,2));
        helper.getLevel().setBlock(ceiling, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(ceiling.below(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(ceiling.below(2), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(bat.canHangFrom(ceiling), "Small source bat needs one clear block");
        helper.assertTrue(!giant.canHangFrom(ceiling), "Giant bat cannot hang inside the second block");
        helper.getLevel().setBlock(ceiling.below(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(giant.canHangFrom(ceiling), "Giant requires two clear blocks");
        helper.getLevel().setBlock(ceiling, Blocks.GLASS.defaultBlockState(), 3);
        helper.assertTrue(!bat.canHangFrom(ceiling), "Source bat excludes glass ceiling");
        BlockPos negative = new BlockPos(ceiling.getX(), -8, ceiling.getZ());
        var first = helper.getLevel().getBlockState(negative);
        var second = helper.getLevel().getBlockState(negative.below());
        var third = helper.getLevel().getBlockState(negative.below(2));
        try {
            helper.getLevel().setBlock(negative, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(negative.below(), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(negative.below(2), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(bat.canHangFrom(negative) && giant.canHangFrom(negative), "Modern adaptation permits source bats below Y zero");
        } finally {
            helper.getLevel().setBlock(negative, first, 3);
            helper.getLevel().setBlock(negative.below(), second, 3);
            helper.getLevel().setBlock(negative.below(2), third, 3);
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void ActualLongdeadAndBoneLordDeathsUseIndependentSourceArrowAndBoneDraws(GameTestHelper helper) {
        Player player = player(helper);
        for (MobKind kind : List.of(MobKind.LONGDEAD, MobKind.LONGDEAD_GUARDIAN, MobKind.BONE_LORD, MobKind.ANCIENT_BONE_LORD))
            for (int subtype : new int[]{0, 2}) for (long seed = 0; seed < 16; ++seed) {
                R196Skeleton skeleton = (R196Skeleton)mob(helper, kind); skeleton.setSkeletonType(subtype);
                for (EquipmentSlot slot : EquipmentSlot.values()) skeleton.setDropChance(slot, 0F);
                RandomSource oracle = RandomSource.create(seed);
                int arrows = 0;
                if (subtype == 0) {
                    arrows = oracle.nextInt(2);
                    if ((kind == MobKind.LONGDEAD || kind == MobKind.LONGDEAD_GUARDIAN) && arrows > 0)
                        arrows = oracle.nextInt(3) == 0 ? 1 : 0;
                }
                int bones = oracle.nextInt(3), expectedArrows = arrows;
                var item = MiteRangedItems.arrow(kind == MobKind.LONGDEAD || kind == MobKind.LONGDEAD_GUARDIAN ? Material.ANCIENT_METAL : Material.RUSTED_IRON);
                long actualSeed = seed;
                Consumer<LivingDeathEvent> seedAtDeath = event -> { if (event.getEntity() == skeleton) skeleton.getRandom().setSeed(actualSeed); };
                boolean[] observed = {false};
                Consumer<LivingDropsEvent> inspect = event -> {
                    if (event.getEntity() != skeleton) return;
                    observed[0] = true;
                    helper.assertValueEqual(event.getDrops().stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum(),
                            expectedArrows, "Independent source arrows " + kind + " subtype=" + subtype + " seed=" + actualSeed);
                    helper.assertValueEqual(event.getDrops().stream().filter(e -> e.getItem().is(Items.BONE)).mapToInt(e -> e.getItem().getCount()).sum(),
                            bones, "Independent source bones " + kind + " seed=" + actualSeed);
                    helper.assertValueEqual(event.getDrops().stream().filter(e -> e.getItem().is(Items.ARROW)).mapToInt(e -> e.getItem().getCount()).sum(),
                            0, "No modern flint-arrow duplicate");
                    event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(seedAtDeath); NeoForge.EVENT_BUS.addListener(inspect);
                try { skeleton.hurt(helper.getLevel().damageSources().playerAttack(player), 1000F); }
                finally { NeoForge.EVENT_BUS.unregister(seedAtDeath); NeoForge.EVENT_BUS.unregister(inspect); }
                helper.assertTrue(observed[0], "Source material loot participates in the native cancellable death event");
            }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void ActualRevenantDeathUsesFiftyDenominatorAndNeverDropsRustedArmorAsExtraMaterial(GameTestHelper helper) {
        Player player = player(helper);
        for (long seed = 0; seed < 40; ++seed) {
            R196Zombie zombie = (R196Zombie)mob(helper, MobKind.REVENANT);
            for (EquipmentSlot slot : EquipmentSlot.values()) zombie.setDropChance(slot, 0F);
            RandomSource oracle = RandomSource.create(seed);
            int flesh = oracle.nextFloat() < .5F ? 1 : 0;
            String nugget = oracle.nextInt(50) < 5 ? switch (oracle.nextInt(4)) {
                case 0 -> "mite:copper_nugget"; case 1 -> "mite:silver_nugget"; case 2 -> "minecraft:gold_nugget"; default -> "minecraft:iron_nugget";
            } : null;
            long actualSeed = seed;
            Consumer<LivingDeathEvent> seedAtDeath = event -> { if (event.getEntity() == zombie) zombie.getRandom().setSeed(actualSeed); };
            boolean[] observed = {false};
            Consumer<LivingDropsEvent> inspect = event -> {
                if (event.getEntity() != zombie) return;
                observed[0] = true;
                helper.assertValueEqual(event.getDrops().stream().filter(e -> e.getItem().is(Items.ROTTEN_FLESH)).mapToInt(e -> e.getItem().getCount()).sum(),
                        flesh, "Source revenant flesh " + actualSeed);
                helper.assertValueEqual(event.getDrops().stream().filter(e -> !e.getItem().is(Items.ROTTEN_FLESH)).mapToInt(e -> e.getItem().getCount()).sum(),
                        nugget == null ? 0 : 1, "Source revenant rare item is one nugget " + actualSeed);
                if (nugget != null) helper.assertTrue(event.getDrops().stream().anyMatch(e -> e.getItem().is(R196MobEquipment.item(nugget))),
                        "Source chooses one of four rare nuggets with its own RNG");
                event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(seedAtDeath); NeoForge.EVENT_BUS.addListener(inspect);
            try { zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1000F); }
            finally { NeoForge.EVENT_BUS.unregister(seedAtDeath); NeoForge.EVENT_BUS.unregister(inspect); }
            helper.assertTrue(observed[0], "Revenant loot uses native death cancellation");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void ActualAncientLordDeathReturnsTheHeldWeaponWithItsExistingQualityAndWear(GameTestHelper helper) {
        Player player = player(helper);
        R196Skeleton lord = (R196Skeleton)mob(helper, MobKind.ANCIENT_BONE_LORD); lord.setSkeletonType(2);
        ItemStack weapon = new ItemStack(R196MobEquipment.item("mite:ancient_metal_sword"));
        EquipmentDurability.setQuality(weapon, Quality.FINE); weapon.setDamageValue(37);
        lord.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        lord.setDropChance(EquipmentSlot.MAINHAND, .085F);
        for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)) lord.setDropChance(slot, 0F);
        boolean[] observed = {false};
        Consumer<LivingDropsEvent> inspect = event -> {
            if (event.getEntity() != lord) return;
            observed[0] = true;
            var equipment = event.getDrops().stream().filter(e -> e.getItem().is(weapon.getItem())).toList();
            helper.assertValueEqual(equipment.size(), 1, "Recently player-killed source lord always returns its non-poor held weapon");
            helper.assertValueEqual(equipment.getFirst().getItem().getDamageValue(), 37, "Death does not reroll generated equipment wear");
            helper.assertTrue(EquipmentDurability.quality(equipment.getFirst().getItem()) == Quality.FINE, "Actual death preserves the held quality");
            event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(inspect);
        try { lord.hurt(helper.getLevel().damageSources().playerAttack(player), 1000F); }
        finally { NeoForge.EVENT_BUS.unregister(inspect); }
        helper.assertTrue(observed[0], "Actual death uses native cancellable equipment drops"); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void OrdinarySkeletonSpawnMatchesSourceQuarterMeleeDrawAndRetainsSubtypeAndWeapon(GameTestHelper helper) {
        int melee = 0, ranged = 0;
        for (long seed = 0; seed < 32; ++seed) {
            Skeleton skeleton = new Skeleton(EntityType.SKELETON,helper.getLevel());
            skeleton.moveTo(helper.absolutePos(new BlockPos(2,3,2)).getCenter()); skeleton.getRandom().setSeed(seed * 1024);
            RandomSource oracle = RandomSource.create(seed * 1024); oracle.nextGaussian();
            int expected = oracle.nextFloat() < .25F ? 2 : 0;
            finalizeMob(helper,skeleton);
            helper.assertValueEqual(((OrdinarySkeletonState)skeleton).mite$sourceSkeletonType(),expected,"Source subtype follows og gaussian then tr quarter draw");
            helper.assertTrue(skeleton.getMainHandItem().getItem() instanceof BowItem == (expected == 0),"Stored source subtype and live weapon goal agree");
            if (expected == 2) melee++; else ranged++;
            // The lord chooses its forced troop type before finalizeSpawn; its helper must synchronize that choice too.
            int forced = expected == 0 ? 2 : 0;
            R196Skeleton.initializeOrdinaryTroop(skeleton,forced);
            helper.assertValueEqual(((OrdinarySkeletonState)skeleton).mite$sourceSkeletonType(),forced,"Summoned troop metadata follows the preselected subtype");
            helper.assertTrue(skeleton.getMainHandItem().getItem() instanceof BowItem == (forced == 0),"Forced troop weapon and metadata agree after finalization");
            CompoundTag saved = new CompoundTag(); skeleton.addAdditionalSaveData(saved);
            Skeleton restored = new Skeleton(EntityType.SKELETON,helper.getLevel()); restored.readAdditionalSaveData(saved);
            helper.assertValueEqual(((OrdinarySkeletonState)restored).mite$sourceSkeletonType(),forced,"Forced native skeleton subtype survives saving");
            helper.assertTrue(ItemStack.matches(restored.getMainHandItem(),skeleton.getMainHandItem()),"Restored weapon keeps quality and wear");
        }
        helper.assertTrue(melee>0 && ranged>0,"Independent source seeds exercise both ordinary variants"); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void OrdinarySkeletonWitherMetadataHasSourceShapeImmunityAndAttackWithoutChangingModernIdentity(GameTestHelper helper) {
        Skeleton skeleton = new Skeleton(EntityType.SKELETON,helper.getLevel());
        R196Skeleton.initializeOrdinaryTroop(skeleton,1);
        helper.assertValueEqual(((OrdinarySkeletonState)skeleton).mite$sourceSkeletonType(),1,"Source wither helper synchronizes native subtype");
        close(helper,skeleton.getBbWidth(),.72,"Source ordinary wither width"); close(helper,skeleton.getBbHeight(),2.34,"Source ordinary wither height");
        helper.assertTrue(skeleton.fireImmune(),"Persisted source type1 makes native skeleton fire immune");
        helper.assertTrue(skeleton.getMainHandItem().is(Items.IRON_SWORD) && EquipmentDurability.quality(skeleton.getMainHandItem())==Quality.POOR,"Source native type1 holds poor iron sword");
        Cow target = new Cow(EntityType.COW,helper.getLevel());
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.setHealth(100);
        helper.assertTrue(skeleton.doHurtTarget(target),"Ordinary source wither hit succeeds");
        helper.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.WITHER),"Native source type1 applies 200tick wither");
        CompoundTag saved = new CompoundTag(); skeleton.addAdditionalSaveData(saved);
        Skeleton restored = new Skeleton(EntityType.SKELETON,helper.getLevel()); restored.readAdditionalSaveData(saved);
        helper.assertTrue(restored.fireImmune(),"Source type1 survives reconnect/storage without becoming ordinary again");
        close(helper,restored.getBbHeight(),2.34,"Restored source shape"); helper.succeed();
    }
}
