package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.R196Elemental;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.ranged.ArrowRecovery;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;

/** Source of/sm/tv literals verify actual health transactions and post-construction save loading. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class SharedNpcRules031GameTests {
    @GameTest(template="empty") public static void sourceHorseBardingContributesItsIntegerProtection(GameTestHelper h) {
        int[] defense={4,4,3,5,5,6,7}; String[] ids={"mite:copper_horse_armor","mite:silver_horse_armor","minecraft:golden_horse_armor","minecraft:iron_horse_armor","mite:ancient_metal_horse_armor","mite:mithril_horse_armor","mite:adamantium_horse_armor"};
        for(int i=0;i<ids.length;i++) {
            var horse=EntityType.HORSE.create(h.getLevel()); horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30); horse.setHealth(30);
            ItemStack barding=new ItemStack(io.github.ningmeng123.mite.registry.MiteArmorContent.get(ids[i])); horse.setBodyArmorItem(barding);
            double displayed=barding.getAttributeModifiers().modifiers().stream()
                    .filter(e->e.attribute().equals(Attributes.ARMOR)).mapToDouble(e->e.modifier().amount()).sum();
            h.assertValueEqual(displayed,(double)defense[i],"Source horse armor display agrees with actual defense for "+ids[i]);
            // Modern generic damage bypasses armor; use an actual ordinary mob attack for barding.
            horse.hurt(horse.damageSources().mobAttack(EntityType.COW.create(h.getLevel())),10);
            h.assertValueEqual(horse.getHealth(),20F+defense[i],"ItemHorseArmor integer source defense once for "+ids[i]);
            h.assertValueEqual(barding.getDamageValue(),0,"Horse barding remains source nondamageable");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void horseBardingCannotBlockSourceLavaOrAbsoluteDamage(GameTestHelper h) {
        var horse=EntityType.HORSE.create(h.getLevel()); horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30); horse.setHealth(30);
        horse.setBodyArmorItem(new ItemStack(io.github.ningmeng123.mite.registry.MiteArmorContent.get("mite:adamantium_horse_armor")));
        horse.hurt(horse.damageSources().lava(),10); h.assertValueEqual(horse.getHealth(),20F,"Lava bypasses source mundane barding");
        horse.invulnerableTime=0; horse.hurt(horse.damageSources().inWall(),4); h.assertValueEqual(horse.getHealth(),16F,"Absolute suffocation bypasses all barding defense"); h.succeed();
    }
    @GameTest(template="empty") public static void sourceNpcFireResistanceDoesNotGrantLavaImmunity(GameTestHelper h) {
        Cow cow=EntityType.COW.create(h.getLevel()); cow.setHealth(20); cow.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,200));
        h.assertTrue(!cow.hurt(cow.damageSources().inFire(),4),"Pure fire nb.m is stopped by source fire resistance");
        h.assertTrue(cow.hurt(cow.damageSources().lava(),4),"Lava nb.setLavaDamage is a separate damage flag");
        h.assertValueEqual(cow.getHealth(),16F,"Antifire potion does not subtract source lava damage");
        var neth=MiteMobs.type(MobKind.NETHERSPAWN).create(h.getLevel()); neth.setHealth(8); neth.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,200));
        h.assertTrue(neth.hurt(neth.damageSources().lava(),4),"Netherspawn is fire-immune, not lava-immune even with antifire");
        h.assertValueEqual(neth.getHealth(),4F,"Netherspawn source lava acceptance survives native early fire gate"); h.succeed();
    }
    @GameTest(template="empty") public static void burningArrowAspectDoesNotSuppressPhysicalHit(GameTestHelper h) {
        var attacker=h.makeMockPlayer(GameType.SURVIVAL); Arrow arrow=new Arrow(EntityType.ARROW,h.getLevel()); arrow.igniteForSeconds(10);
        Cow cow=EntityType.COW.create(h.getLevel()); cow.setHealth(20); cow.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,200));
        cow.hurt(cow.damageSources().arrow(arrow,attacker),4); h.assertValueEqual(cow.getHealth(),16F,"nc.hasFireAspect is not pure nb.m fire damage");
        var neth=MiteMobs.type(MobKind.NETHERSPAWN).create(h.getLevel()); neth.setHealth(8);
        h.assertTrue(neth.hurt(neth.damageSources().arrow(arrow,attacker),4),"Netherspawn fire-only immunity must retain ordinary burning-arrow physical hit");
        h.assertValueEqual(neth.getHealth(),4F,"Burning arrow does source physical damage to Netherspawn");
        var clay=(R196Elemental)MiteMobs.type(MobKind.CLAY_GOLEM).create(h.getLevel()); clay.setHealth(30);
        h.assertTrue(clay.hurt(clay.damageSources().arrow(arrow,attacker),4),"Normal clay is not immune to physical burning arrow");
        h.assertValueEqual(clay.getHealth(),26F,"Mud clay lacks hardened stone tool gate"); h.succeed();
    }
    private static Cow absorbingCow(GameTestHelper h,float amount) {
        Cow cow=EntityType.COW.create(h.getLevel()); cow.setHealth(20);
        cow.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100); cow.setAbsorptionAmount(amount); return cow;
    }
    @GameTest(template="empty") public static void sourceNpcAbsorptionRetainsOneHealthLossAndExtraDebit(GameTestHelper h) {
        Cow cow=absorbingCow(h,20); cow.hurt(cow.damageSources().generic(),4);
        h.assertValueEqual(cow.getHealth(),19F,"of positive hit costs minimum one health after absorption");
        h.assertValueEqual(cow.getAbsorptionAmount(),15F,"Source nonplayer decrements original four absorbed plus final one damage"); h.succeed();
    }
    @GameTest(template="empty") public static void sourceNpcPartialAbsorptionKeepsActualRemainingDamage(GameTestHelper h) {
        Cow cow=absorbingCow(h,2); cow.hurt(cow.damageSources().generic(),4);
        h.assertValueEqual(cow.getHealth(),18F,"Partial source absorption leaves two real damage");
        h.assertValueEqual(cow.getAbsorptionAmount(),0F,"Additional NPC debit cannot make stored absorption negative"); h.succeed();
    }
    @GameTest(template="empty") public static void sourceNpcAbsorptionFloorPreservesPreEventZeroDamage(GameTestHelper h) {
        Cow cow=absorbingCow(h,20); Consumer<LivingDamageEvent.Pre> listener=e->{ if(e.getEntity()==cow) e.setNewDamage(0); };
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Pre.class,listener);
        try { cow.hurt(cow.damageSources().generic(),4); } finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.assertValueEqual(cow.getHealth(),20F,"A mod's zero-damage pre-event is not resurrected by source positive-hit floor");
        h.assertValueEqual(cow.getAbsorptionAmount(),20F,"Zero transaction consumes no absorption"); h.succeed();
    }
    @GameTest(template="empty") public static void fallingAnvilWearsSourceNpcHelmetButOrdinaryHitDoesNot(GameTestHelper h) {
        Cow cow=EntityType.COW.create(h.getLevel()); ItemStack helmet=new ItemStack(Items.IRON_HELMET); cow.setItemSlot(EquipmentSlot.HEAD,helmet);
        long seed=3; int expected=(int)(8F*4F+new java.util.Random(seed).nextFloat()*8F*2F); cow.getRandom().setSeed(seed);
        cow.hurt(cow.damageSources().anvil(new Snowball(h.getLevel(),h.makeMockPlayer(GameType.SURVIVAL))),8);
        h.assertValueEqual(helmet.getDamageValue(),expected,"of falling anvil uses incoming*4 plus source one uniform draw*incoming*2");
        cow.invulnerableTime=0; cow.hurt(cow.damageSources().generic(),2);
        h.assertValueEqual(helmet.getDamageValue(),expected,"Empty of.tryDamageArmor does not wear NPC helmet on ordinary hits"); h.succeed();
    }
    @GameTest(template="empty") public static void sourceNpcHelmetWearKeepsCancelableModernArmorEvent(GameTestHelper h) {
        Cow cow=EntityType.COW.create(h.getLevel()); ItemStack helmet=new ItemStack(Items.IRON_HELMET); cow.setItemSlot(EquipmentSlot.HEAD,helmet);
        Consumer<ArmorHurtEvent> listener=e->{ if(e.getEntity()==cow) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(ArmorHurtEvent.class,listener);
        try { cow.hurt(cow.damageSources().anvil(new Snowball(h.getLevel(),h.makeMockPlayer(GameType.SURVIVAL))),8); }
        finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.assertValueEqual(helmet.getDamageValue(),0,"Canceling modern armor event prevents all source falling-object wear"); h.succeed();
    }
    @GameTest(template="empty") public static void nativeNpcDefenseNeverUsesPlayerFullBlocking(GameTestHelper h) {
        for (int seed=0;seed<32;seed++) {
            WitherBoss wither=EntityType.WITHER.create(h.getLevel()); wither.setHealth(300); wither.getRandom().setSeed(seed);
            wither.hurt(wither.damageSources().generic(), .5F);
            h.assertValueEqual(wither.getHealth(), 299F, "Source NPC minimum one has no player-only blocking draw at seed " + seed);
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void nativeWitchSmallIndirectHitStillCostsOneHealth(GameTestHelper h) {
        Witch witch=EntityType.WITCH.create(h.getLevel()); witch.setHealth(26);
        var attacker=h.makeMockPlayer(GameType.SURVIVAL); Snowball projectile=new Snowball(h.getLevel(),attacker);
        witch.hurt(witch.damageSources().indirectMagic(projectile,attacker),6);
        h.assertValueEqual(witch.getHealth(),25F,"tv ten defense still obeys nonplayer minimum one, without extra 85 percent resistance"); h.succeed();
    }
    @GameTest(template="empty") public static void recoveredContainedArrowsRequireAdultSourceMob(GameTestHelper h) {
        h.assertValueEqual(recoveredArrows(h,false),1,"Adult source cow returns one actual contained adamantium arrow");
        h.assertValueEqual(recoveredArrows(h,true),0,"Source baby NPC death suppresses contained arrows"); h.succeed();
    }
    @GameTest(template="empty") public static void disabledMobLootConsumesContainedArrowsWithoutReturningThem(GameTestHelper h) {
        boolean old=h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        try { h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false,h.getLevel().getServer());
            h.assertValueEqual(recoveredArrows(h,false),0,"Source doMobLoot false clears stored arrows without dropping them");
        } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(old,h.getLevel().getServer()); }
        h.succeed();
    }
    private static int recoveredArrows(GameTestHelper h,boolean baby) {
        Cow cow=EntityType.COW.create(h.getLevel()); cow.setBaby(baby); cow.tickCount=100;
        ItemStack arrow=new ItemStack(MiteRangedItems.arrow(Material.ADAMANTIUM)); ArrowRecovery.contain(cow,arrow);
        List<ItemStack> drops=new ArrayList<>();
        Consumer<LivingDropsEvent> listener=e->{ if(e.getEntity()==cow) for(ItemEntity item:e.getDrops()) drops.add(item.getItem().copy()); };
        NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class,listener);
        try { cow.setHealth(0); cow.die(cow.damageSources().generic()); }
        finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.assertTrue(!cow.hasData(ArrowRecovery.CONTAINED),"Death clears recovery data to prevent a second return");
        return drops.stream().filter(s->s.is(arrow.getItem())).mapToInt(ItemStack::getCount).sum();
    }
    @GameTest(template="empty") public static void nativeWitherSourceNaturalDefenseSubtractsFour(GameTestHelper h) {
        WitherBoss wither = EntityType.WITHER.create(h.getLevel());
        wither.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); wither.setHealth(100);
        h.assertTrue(wither.hurt(wither.damageSources().generic(), 6), "Real native wither hit");
        h.assertValueEqual(wither.getHealth(), 98F, "sm.getNaturalDefense supplies exactly four, without modern armor percentage"); h.succeed();
    }
    @GameTest(template="empty") public static void nativeWitchIndirectMagicSubtractsTenOnce(GameTestHelper h) {
        Witch witch = EntityType.WITCH.create(h.getLevel()); witch.setHealth(26);
        var attacker = h.makeMockPlayer(GameType.SURVIVAL); Snowball projectile = new Snowball(h.getLevel(), attacker);
        witch.hurt(witch.damageSources().indirectMagic(projectile, attacker), 20);
        h.assertValueEqual(witch.getHealth(), 16F, "tv source indirect magic defense ten replaces modern 85 percent potion resistance"); h.succeed();
    }
    @GameTest(template="empty") public static void nativeWitchDirectMagicDoesNotReceiveIndirectProtection(GameTestHelper h) {
        Witch witch = EntityType.WITCH.create(h.getLevel()); witch.setHealth(26);
        witch.hurt(witch.damageSources().magic(), 12);
        h.assertValueEqual(witch.getHealth(), 14F, "Source tv only protects indirect magic; direct magic remains twelve"); h.succeed();
    }
    @GameTest(template="empty") public static void elementalDiggerLoadsSavedProgressAfterGoalConstruction(GameTestHelper h) {
        R196Elemental elemental = (R196Elemental)MiteMobs.type(MobKind.EARTH_ELEMENTAL).create(h.getLevel());
        // Keep the fixture above the small template's marker/barrier structures; they otherwise block its eye ray.
        BlockPos origin = h.absolutePos(new BlockPos(1, 10, 1));
        elemental.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D);
        R196DiggerGoal goal = new R196DiggerGoal(elemental); // Native constructors create goals before NBT is read.
        BlockPos target = elemental.blockPosition().east(); h.getLevel().setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState());
        CompoundTag saved = new CompoundTag(); saved.putLong("position", target.asLong()); saved.putInt("progress", 5); saved.putInt("cooldown", 1);
        elemental.getPersistentData().put("mite_digging_031", saved);
        elemental.setTarget(EntityType.COW.create(h.getLevel()));
        h.assertTrue(elemental.getTarget() != null && elemental.getTarget().isAlive(), "Live prey fixture is retained");
        h.assertTrue(goal.canDestroy(target, false), "Loaded source dirt passes tool/grief/range qualification: position=" + elemental.position() + " block=" + target);
        h.assertTrue(goal.canDestroy(target, true), "The saved reachable block has clear source eye or leg access");
        h.assertTrue(goal.canUse(), "Saved reachable work resumes through normal goal entry"); goal.tick();
        h.assertValueEqual(elemental.getPersistentData().getCompound("mite_digging_031").getInt("progress"), 6, "Resume sixth stage instead of resetting to minus one"); h.succeed();
    }
}
