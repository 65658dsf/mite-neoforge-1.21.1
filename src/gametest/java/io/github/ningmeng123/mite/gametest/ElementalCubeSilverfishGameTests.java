package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.entity.CubeState;
import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.MobCorrosion;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Cube;
import io.github.ningmeng123.mite.entity.R196Elemental;
import io.github.ningmeng123.mite.entity.R196FireElemental;
import io.github.ningmeng123.mite.entity.R196Silverfish;
import io.github.ningmeng123.mite.mixin.ItemCorrosionAccess;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent source values exercise material transitions, chemistry, saved progress and live subtype behavior. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ElementalCubeSilverfishGameTests {
    private static Mob mob(GameTestHelper helper,MobKind kind) {
        Mob result=MiteMobs.type(kind).create(helper.getLevel());
        BlockPos pos=helper.absolutePos(new BlockPos(2,3,2));
        result.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); result.setNoAi(true); result.setNoGravity(true);
        helper.getLevel().addFreshEntity(result); return result;
    }
    private static R196Elemental earth(GameTestHelper helper) { return (R196Elemental)mob(helper,MobKind.EARTH_ELEMENTAL); }
    private static R196Cube cube(GameTestHelper helper,MobKind kind,int size) { R196Cube cube=(R196Cube)mob(helper,kind); cube.setSize(size,true); return cube; }
    private static Cow cow(GameTestHelper helper) {
        Cow target=EntityType.COW.create(helper.getLevel()); target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.setHealth(100); target.moveTo(helper.absolutePos(new BlockPos(3,3,2)).getCenter());
        helper.getLevel().addFreshEntity(target); return target;
    }
    private static Player player(GameTestHelper helper) { Player p=helper.makeMockPlayer(GameType.SURVIVAL); p.moveTo(helper.absolutePos(new BlockPos(2,3,2)).getCenter()); return p; }
    private static void near(GameTestHelper helper,double actual,double expected,String message) { helper.assertTrue(Math.abs(actual-expected)<.00001,message+" expected "+expected+" got "+actual); }
    @GameTest(template="empty") public static void earthSourceAttributes(GameTestHelper h) { R196Elemental e=earth(h); near(h,e.getMaxHealth(),30,"Earth health"); near(h,e.getAttributeValue(Attributes.ATTACK_DAMAGE),12,"Earth attack"); near(h,e.naturalDefense(e.damageSources().generic()),4,"Earth defense"); near(h,e.getAttributeValue(Attributes.FOLLOW_RANGE),20,"Earth range"); h.succeed(); }
    @GameTest(template="empty") public static void claySourceAttributes(GameTestHelper h) { R196Elemental e=(R196Elemental)mob(h,MobKind.CLAY_GOLEM); near(h,e.getMaxHealth(),30,"Clay health"); near(h,e.getAttributeValue(Attributes.ATTACK_DAMAGE),6,"Clay attack"); near(h,e.naturalDefense(e.damageSources().generic()),0,"Wet clay defense"); h.succeed(); }
    @GameTest(template="empty") public static void earthMaterialSelection(GameTestHelper h) { R196Elemental e=earth(h); for (var block:List.of(Blocks.STONE,Blocks.OBSIDIAN,Blocks.NETHERRACK,Blocks.END_STONE)) { e.setMaterial(block,false); h.assertTrue(e.materialBlock()==block,"Source material must survive mapping"); } h.succeed(); }
    @GameTest(template="empty") public static void earthHeatQuenches(GameTestHelper h) { R196Elemental e=earth(h); e.setMaterial(Blocks.OBSIDIAN,false); e.convertToMagma(); h.assertTrue(e.isMagma() && e.heat()==1000,"Source conversion sets heat1000"); e.quench(); h.assertTrue(!e.isMagma() && e.heat()==0 && e.materialBlock()==Blocks.OBSIDIAN,"Quench removes bit, preserves body"); h.succeed(); }
    @GameTest(template="empty") public static void clayHardeningIsPermanent(GameTestHelper h) { R196Elemental e=(R196Elemental)mob(h,MobKind.CLAY_GOLEM); e.convertToMagma(); e.quench(); h.assertTrue(e.isHardenedClay() && !e.isMagma() && e.materialBlock()==Blocks.TERRACOTTA,"Water cannot revert fired clay"); near(h,e.naturalDefense(e.damageSources().generic()),2,"Hardened defense"); h.succeed(); }
    @GameTest(template="empty") public static void earthMaterialHeatPersist(GameTestHelper h) { R196Elemental e=earth(h); e.setMaterial(Blocks.END_STONE,false); e.convertToMagma(); CompoundTag tag=new CompoundTag(); e.addAdditionalSaveData(tag); R196Elemental restored=earth(h); restored.readAdditionalSaveData(tag); h.assertTrue(restored.isMagma() && restored.materialBlock()==Blocks.END_STONE && restored.heat()==1000,"Source material+heat save independently"); h.succeed(); }
    @GameTest(template="empty") public static void earthDamageGate(GameTestHelper h) { R196Elemental e=earth(h); Player p=player(h); h.assertTrue(e.isInvulnerableTo(e.damageSources().playerAttack(p)),"Unarmed cannot damage rock"); p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE)); h.assertTrue(!e.isInvulnerableTo(e.damageSources().playerAttack(p)),"Effective rock tool is admitted"); h.assertTrue(!e.isInvulnerableTo(e.damageSources().fall()),"Source explicitly admits fall"); h.succeed(); }
    @GameTest(template="empty") public static void clayDoesNotUseRockGate(GameTestHelper h) { R196Elemental e=(R196Elemental)mob(h,MobKind.CLAY_GOLEM); h.assertTrue(!e.isInvulnerableTo(e.damageSources().generic()),"Normal clay takes ordinary damage"); e.convertToMagma(); h.assertTrue(e.isInvulnerableTo(e.damageSources().generic()),"Fired clay gets material gate"); h.succeed(); }
    @GameTest(template="empty") public static void earthIgnoresFireLavaDrowning(GameTestHelper h) { R196Elemental e=earth(h); h.assertTrue(e.isInvulnerableTo(e.damageSources().inFire()) && e.isInvulnerableTo(e.damageSources().lava()) && !e.canDrownInFluidType(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value()),"Mineral body cannot burn, melt or drown"); h.succeed(); }
    @GameTest(template="empty") public static void snowballQuenchesMagmaEarth(GameTestHelper h) throws Exception { R196Elemental e=earth(h); e.convertToMagma(); Snowball ball=new Snowball(h.getLevel(),player(h)); var impact=Snowball.class.getDeclaredMethod("onHitEntity",net.minecraft.world.phys.EntityHitResult.class); impact.setAccessible(true); impact.invoke(ball,new net.minecraft.world.phys.EntityHitResult(e)); h.assertTrue(!e.isMagma(),"Native impact quench runs even if mineral gate rejects hit"); h.succeed(); }
    @GameTest(template="empty") public static void fireSourceAttributes(GameTestHelper h) { R196FireElemental e=(R196FireElemental)mob(h,MobKind.FIRE_ELEMENTAL); near(h,e.getMaxHealth(),20,"40 in source is range, not health"); near(h,e.getAttributeValue(Attributes.FOLLOW_RANGE),40,"Fire range"); near(h,e.getAttributeValue(Attributes.ATTACK_DAMAGE),5,"Fire attack"); h.assertTrue(!e.healsWithTime() && !e.biologicallyAlive(),"Source fire biological flags"); h.succeed(); }
    @GameTest(template="empty") public static void dryFireReceivesSourceWaterPulse(GameTestHelper h) { R196FireElemental e=(R196FireElemental)mob(h,MobKind.FIRE_ELEMENTAL); e.tickCount=Math.floorMod(-e.getId()*47,40); float before=e.getHealth(); h.assertTrue(!e.isInWaterOrRain(),"Dry test fixture"); e.aiStep(); near(h,e.getHealth(),before-1,"Confirmed R196 unconditional water pulse"); h.succeed(); }
    @GameTest(template = "empty")
    public static void fireRejectsPlainAndAdmitsWater(GameTestHelper h) {
        R196FireElemental elemental = (R196FireElemental) mob(h, MobKind.FIRE_ELEMENTAL);
        h.assertTrue(elemental.isInvulnerableTo(elemental.damageSources().generic()), "Ordinary physical damage is rejected");
        h.assertTrue(!elemental.isInvulnerableTo(MobCombat.source(elemental, "water")), "Water damage is admitted");
        h.assertTrue(!elemental.isInvulnerableTo(elemental.damageSources().magic()), "Magic damage without a weapon is admitted");
        Snowball ball = new Snowball(h.getLevel(), player(h));
        h.assertTrue(!elemental.isInvulnerableTo(elemental.damageSources().thrown(ball, ball.getOwner())), "Snowballs are admitted");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fireMeleeChecksForSoleFireAspect(GameTestHelper h) {
        Player attacker = player(h);
        DamageSource melee = attacker.damageSources().playerAttack(attacker);
        var enchantments = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var sharpness = enchantments.getHolderOrThrow(Enchantments.SHARPNESS);

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
        assertFireHealthLoss(h, melee, false, "An unenchanted pickaxe cannot hurt the fire elemental");
        pickaxe.enchant(enchantments.getHolderOrThrow(Enchantments.EFFICIENCY), 1);
        assertFireHealthLoss(h, melee, true, "An Efficiency pickaxe can hurt the fire elemental");

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, sword);
        assertFireHealthLoss(h, melee, false, "An unenchanted sword cannot hurt the fire elemental");
        sword.enchant(sharpness, 1);
        assertFireHealthLoss(h, melee, true, "A sword with Sharpness can hurt the fire elemental");

        ItemStack fireSword = new ItemStack(Items.IRON_SWORD);
        fireSword.enchant(enchantments.getHolderOrThrow(Enchantments.FIRE_ASPECT), 1);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, fireSword);
        assertFireHealthLoss(h, melee, false, "A sword with Fire Aspect alone cannot hurt the fire elemental");
        fireSword.enchant(sharpness, 1);
        assertFireHealthLoss(h, melee, true, "A sword with Fire Aspect and Sharpness can hurt the fire elemental");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fireArrowsCheckForSoleFlame(GameTestHelper h) {
        Player shooter = player(h);
        var enchantments = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var power = enchantments.getHolderOrThrow(Enchantments.POWER);

        ItemStack bow = new ItemStack(Items.BOW);
        assertFireArrowHealthLoss(h, shooter, bow, false, "An unenchanted bow cannot hurt the fire elemental");
        bow.enchant(power, 1);
        assertFireArrowHealthLoss(h, shooter, bow, true, "A bow with Power can hurt the fire elemental");

        ItemStack flameBow = new ItemStack(Items.BOW);
        flameBow.enchant(enchantments.getHolderOrThrow(Enchantments.FLAME), 1);
        assertFireArrowHealthLoss(h, shooter, flameBow, false, "A bow with Flame alone cannot hurt the fire elemental");
        flameBow.enchant(power, 1);
        assertFireArrowHealthLoss(h, shooter, flameBow, true, "A bow with Flame and Power can hurt the fire elemental");
        h.succeed();
    }

    private static void assertFireArrowHealthLoss(GameTestHelper h, Player shooter, ItemStack bow, boolean expected, String message) {
        Arrow arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), bow);
        assertFireHealthLoss(h, shooter.damageSources().arrow(arrow, shooter), expected, message);
        arrow.discard();
    }

    private static void assertFireHealthLoss(GameTestHelper h, DamageSource source, boolean expected, String message) {
        R196FireElemental elemental = (R196FireElemental) mob(h, MobKind.FIRE_ELEMENTAL);
        float before = elemental.getHealth();
        elemental.hurt(source, 2F);
        h.assertValueEqual(elemental.getHealth() < before, expected, message);
        elemental.discard();
    }

    @GameTest(template="empty") public static void fireMeleeIgnitesSixSeconds(GameTestHelper h) { R196FireElemental e=(R196FireElemental)mob(h,MobKind.FIRE_ELEMENTAL); Cow target=cow(h); h.assertTrue(e.doHurtTarget(target),"Fire melee must damage target"); h.assertTrue(target.getRemainingFireTicks()==120,"Source six seconds at20TPS"); h.succeed(); }
    @GameTest(template="empty") public static void cubeSizeAttributes(GameTestHelper h) { for (MobKind kind:List.of(MobKind.JELLY,MobKind.BLOB,MobKind.PUDDING)) { R196Cube e=cube(h,kind,4); near(h,e.getMaxHealth(),16,"Cube size square"); near(h,e.getBbWidth(),2,"Source width .5size"); near(h,e.getBbHeight(),2,"Source height .5size"); near(h,e.getAttributeValue(Attributes.ATTACK_DAMAGE),kind==MobKind.JELLY ? 8 : kind==MobKind.BLOB ? 12 : 16,"Independent type multipliers"); } h.succeed(); }
    @GameTest(template="empty") public static void oozeMaximumSize(GameTestHelper h) { R196Cube e=cube(h,MobKind.OOZE,4); h.assertTrue(e.getSize()==2,"Virtual setSize caps at2"); near(h,e.getMaxHealth(),4,"Ooze max health4"); near(h,e.getAttributeValue(Attributes.ATTACK_DAMAGE),6,"Ooze damage6"); h.succeed(); }
    @GameTest(template="empty") public static void cubeExperienceFormula(GameTestHelper h) { for (MobKind kind:List.of(MobKind.JELLY,MobKind.BLOB,MobKind.OOZE,MobKind.PUDDING)) { R196Cube e=cube(h,kind,2); int xp=kind==MobKind.JELLY ? 4 : kind==MobKind.BLOB ? 6 : kind==MobKind.OOZE ? 8 : 10; h.assertTrue(e.getExperienceReward(h.getLevel(),null)==xp,"Source XP size*(mult+acid)"); } h.succeed(); }
    @GameTest(template="empty") public static void cubeImmunityBranches(GameTestHelper h) { R196Cube jelly=cube(h,MobKind.JELLY,1),ooze=cube(h,MobKind.OOZE,1),pudding=cube(h,MobKind.PUDDING,1); h.assertTrue(jelly.isInvulnerableTo(MobCombat.source(jelly,"pepsin")),"All cubes immune pepsin"); h.assertTrue(ooze.isInvulnerableTo(ooze.damageSources().generic()) && !ooze.isInvulnerableTo(ooze.damageSources().lava()),"Ooze physical/lava branch"); h.assertTrue(pudding.isInvulnerableTo(MobCombat.source(pudding,"acid")) && !pudding.isInvulnerableTo(pudding.damageSources().inFire()),"Pudding acid/fire branch"); h.succeed(); }
    @GameTest(template="empty") public static void cubePlayerSnowballFlag(GameTestHelper h) { R196Cube e=cube(h,MobKind.PUDDING,1); Snowball ball=new Snowball(h.getLevel(),player(h)); h.assertTrue(e.isInvulnerableTo(e.damageSources().thrown(ball,ball.getOwner())),"Immune predicate allows snowball but player capability flag rejects it"); h.succeed(); }
    @GameTest(template="empty") public static void cubeDurabilityAndFoodSusceptibility(GameTestHelper h) { h.assertTrue(MobCorrosion.susceptible(new ItemStack(Items.IRON_PICKAXE),true),"Iron+wood tool acid susceptible"); h.assertTrue(!MobCorrosion.susceptible(new ItemStack(Items.IRON_PICKAXE),false),"Tools do not contain pepsin-susceptible material"); h.assertTrue(MobCorrosion.susceptible(new ItemStack(Items.BEEF),false) && !MobCorrosion.susceptible(new ItemStack(Items.BREAD),false),"Actual oracle meat/bread distinction"); h.assertTrue(!MobCorrosion.susceptible(new ItemStack(Items.LAPIS_LAZULI),true),"Source dyePowder subtype4 is acid immune"); h.assertTrue(MobCorrosion.susceptible(new ItemStack(MiteWeaponContent.BLACK_PUDDING.get()),true),"Black sphere is aO, not the dyePowder aY override"); h.succeed(); }
    @GameTest(template="empty") public static void droppedToolUsesDurabilityNotFiveHitDeath(GameTestHelper h) { R196Cube e=cube(h,MobKind.PUDDING,1); ItemEntity drop=new ItemEntity(h.getLevel(),e.getX(),e.getY(),e.getZ(),new ItemStack(Items.IRON_PICKAXE)); for (int i=0;i<6;i++) MobCorrosion.dropped(drop,true,e); h.assertTrue(drop.isAlive() && drop.getItem().getDamageValue()==600,"Six source pulses consume600 durability and cannot kill full iron pickaxe"); h.assertTrue(((ItemCorrosionAccess)(Object)drop).mite$pickupDelay()>=60,"Source pickup protection"); h.succeed(); }
    @GameTest(template="empty") public static void acidCanDestroyWornArmorLastPoint(GameTestHelper h) { Player p=player(h); ItemStack armor=new ItemStack(Items.IRON_CHESTPLATE); armor.setDamageValue(armor.getMaxDamage()-1); p.setItemSlot(EquipmentSlot.CHEST,armor); h.assertTrue(MobCorrosion.equipment(armor,true,1,p,EquipmentSlot.CHEST),"Acid admitted"); h.assertTrue(armor.isEmpty(),"Chemical wear can break a chestplate with one durability"); h.succeed(); }
    @GameTest(template="empty") public static void cubeBucketsConvertToCorrectPeers(GameTestHelper h) { R196Cube e=cube(h,MobKind.JELLY,4); ItemEntity water=new ItemEntity(h.getLevel(),e.getX(),e.getY(),e.getZ(),new ItemStack(Items.WATER_BUCKET)); MobCorrosion.dropped(water,false,e); h.assertTrue(water.getItem().is(Items.BUCKET),"Water becomes empty vessel"); ItemEntity lava=new ItemEntity(h.getLevel(),e.getX(),e.getY(),e.getZ(),new ItemStack(Items.LAVA_BUCKET)); MobCorrosion.dropped(lava,false,e); h.assertTrue(lava.getItem().is(MiteUtilityContent.get("iron_stone_bucket")),"Lava becomes matching stone-filled vessel"); h.succeed(); }
    @GameTest(template="empty") public static void cubeInstantCorrosionHasNoDrops(GameTestHelper h) { R196Cube e=cube(h,MobKind.PUDDING,1); BlockPos pos=e.blockPosition(); h.getLevel().setBlockAndUpdate(pos,Blocks.DANDELION.defaultBlockState()); e.corrosion().contact(e); h.assertTrue(h.getLevel().isEmptyBlock(pos),"Source period0 dissolves flower"); h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,e.getBoundingBox().inflate(1)).isEmpty(),"Chemical destruction cannot drop flower"); h.succeed(); }
    @GameTest(template="empty") public static void cubeTimedCorrosionPersists(GameTestHelper h) { R196Cube e=cube(h,MobKind.PUDDING,1); BlockPos pos=e.blockPosition(); h.getLevel().setBlockAndUpdate(pos,Blocks.IRON_BLOCK.defaultBlockState()); e.tickCount=Math.floorMod(-e.getId()*47,20); e.corrosion().contact(e); h.assertTrue(e.corrosion().progress(pos)==1,"Size1 increments source seconds1"); CompoundTag tag=new CompoundTag(); e.addAdditionalSaveData(tag); R196Cube restored=cube(h,MobKind.PUDDING,1); restored.readAdditionalSaveData(tag); h.assertTrue(restored.corrosion().progress(pos)==1,"Dissolution progress survives NBT"); h.succeed(); }
    @GameTest(template="empty") public static void burningCubeDeathDoesNotSplit(GameTestHelper h) { R196Cube e=cube(h,MobKind.JELLY,4); e.setRemainingFireTicks(40); e.setHealth(0); e.remove(Entity.RemovalReason.KILLED); h.assertTrue(h.getLevel().getEntitiesOfClass(R196Cube.class,e.getBoundingBox().inflate(3),x->x!=e).isEmpty(),"Source burning removal bypasses split"); h.succeed(); }
    @GameTest(template="empty") public static void deadCubeSplitsSameKind(GameTestHelper h) { R196Cube e=cube(h,MobKind.BLOB,4); e.setHealth(0); e.remove(Entity.RemovalReason.KILLED); var children=h.getLevel().getEntitiesOfClass(R196Cube.class,e.getBoundingBox().inflate(3),x->x!=e); h.assertTrue(children.size()>=2 && children.size()<=4,"Source2..4split count"); h.assertTrue(children.stream().allMatch(c->c.miteKind()==MobKind.BLOB && c.getSize()==2),"Children keep class and half size"); h.succeed(); }
    @GameTest(template="empty") public static void cropBlightStateCuresAndReplants(GameTestHelper h) { BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); h.getLevel().setBlockAndUpdate(pos,Blocks.WHEAT.defaultBlockState()); h.assertTrue(CubeCropBlight.infect(h.getLevel(),pos),"Crop becomes blighted"); var state=h.getLevel().getBlockState(pos); h.assertTrue(CubeCropBlight.blighted(state),"Native saved/synced state holds disease"); CubeCropBlight.cure(h.getLevel(),pos,state); h.assertTrue(!CubeCropBlight.blighted(h.getLevel().getBlockState(pos)),"Bone-meal disease cure doesn't grow"); h.getLevel().setBlockAndUpdate(pos,state); h.getLevel().removeBlock(pos,false); h.getLevel().setBlockAndUpdate(pos,Blocks.WHEAT.defaultBlockState()); h.assertTrue(!CubeCropBlight.blighted(h.getLevel().getBlockState(pos)),"Fresh replant never inherits old disease"); h.succeed(); }
    @GameTest(template="empty") public static void blightedCropHasNoDrops(GameTestHelper h) {
        var level=h.getLevel(); BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); var area=new AABB(pos).inflate(2);
        var original=level.getBlockState(pos); var originalSoil=level.getBlockState(pos.below());
        var originalItems=level.getEntitiesOfClass(ItemEntity.class,area).stream().map(ItemEntity::getId).collect(java.util.stream.Collectors.toSet());
        boolean originalDrops=level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS); var player=player(h);
        try {
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,level.getServer());
            level.setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),2);
            var healthy=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7);
            level.setBlock(pos,healthy,2); Block.dropResources(healthy,level,pos,null,player,ItemStack.EMPTY);
            h.assertValueEqual(collectNewCropDrops(h,area,originalItems,Items.WHEAT),1,"A real healthy mature player drop is the positive source control");
            var blighted=healthy.setValue(CubeCropBlight.BLIGHTED,true);
            level.setBlock(pos,blighted,2); Block.dropResources(blighted,level,pos,null,player,ItemStack.EMPTY);
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,area).stream().allMatch(drop->originalItems.contains(drop.getId())),
                    "Blighted wheat produces no items through the real loot and event path");
            h.assertValueEqual(collectNewCropDrops(h,area,originalItems,Items.WHEAT),0,"Blighted wheat is excluded by the actual managed getDrops and native event pipeline");
            var potato=Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE,7).setValue(CubeCropBlight.BLIGHTED,true);
            level.setBlock(pos,potato,2); Block.dropResources(potato,level,pos,null,player,ItemStack.EMPTY);
            h.assertValueEqual(collectNewCropDrops(h,area,originalItems,Items.POISONOUS_POTATO),1,"The actual source potato override is retained exactly once rather than revived by the old disease event");
        } finally {
            collectNewCropDrops(h,area,originalItems,Items.WHEAT);
            level.setBlock(pos,original,2); level.setBlock(pos.below(),originalSoil,2);
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(originalDrops,level.getServer());
        }
        h.succeed();
    }
    private static int collectNewCropDrops(GameTestHelper h,AABB area,java.util.Set<Integer> original,net.minecraft.world.item.Item item) {
        int count=0;
        for(var drop:h.getLevel().getEntitiesOfClass(ItemEntity.class,area)) if(!original.contains(drop.getId())) {
            if(drop.getItem().is(item))count+=drop.getItem().getCount(); drop.discard();
        }
        return count;
    }
    @GameTest(template="empty") public static void silverfishSubtypeEffects(GameTestHelper h) { for (MobKind kind:List.of(MobKind.COPPERSPINE,MobKind.HOARY_SILVERFISH)) { R196Silverfish fish=(R196Silverfish)mob(h,kind); Cow target=cow(h); fish.doHurtTarget(target); var effect=target.getEffect(kind==MobKind.COPPERSPINE ? MobEffects.POISON : MobEffects.MOVEMENT_SLOWDOWN); h.assertTrue(effect!=null && effect.getDuration()==(kind==MobKind.COPPERSPINE ? 480 : 50) && effect.getAmplifier()==(kind==MobKind.COPPERSPINE ? 0 : 5),"Source effects applied only after health loss"); } h.succeed(); }
    @GameTest(template="empty") public static void netherspawnFireLavaSeparation(GameTestHelper h) { R196Silverfish fish=(R196Silverfish)mob(h,MobKind.NETHERSPAWN); h.assertTrue(fish.isInvulnerableTo(fish.damageSources().inFire()),"Source fire immune"); h.assertTrue(!fish.isInvulnerableTo(fish.damageSources().lava()),"Source did not override lava susceptibility"); h.succeed(); }
    @GameTest(template="empty") public static void silverfishRecruitRestoresHostWithoutGrief(GameTestHelper h) {
        R196Silverfish fish=(R196Silverfish)mob(h,MobKind.COPPERSPINE);
        var level=h.getLevel(); BlockPos pos=fish.blockPosition().offset(1,0,0);
        var oldBlock=level.getBlockState(pos);
        var existingIds=level.getEntitiesOfClass(Silverfish.class,fish.getBoundingBox().inflate(2)).stream()
                .map(Entity::getId).collect(java.util.stream.Collectors.toSet());
        var released=new java.util.ArrayList<Silverfish>();
        java.util.function.Consumer<EntityJoinLevelEvent> capture=event -> {
            if(event.getLevel()==level && event.getEntity() instanceof Silverfish child && child!=fish
                    && !existingIds.contains(child.getId()) && child.blockPosition().equals(pos)) released.add(child);
        };
        boolean previous=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        level.setBlockAndUpdate(pos,Blocks.INFESTED_STONE.defaultBlockState());
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,level.getServer());
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,capture);
        try {
            fish.recruit();
            h.assertTrue(level.getBlockState(pos).is(Blocks.STONE),"mobGriefing=false restores stone");
            // A same-tick spatial query may not see a freshly promoted chunk's entity section yet.
            h.assertValueEqual(released.size(),1,"Recruit releases exactly one new inhabitant at the infested block");
            h.assertTrue(level.getEntity(released.getFirst().getUUID())==released.getFirst(),
                    "The captured new inhabitant is actually accepted by the world's entity manager");
        } finally {
            NeoForge.EVENT_BUS.unregister(capture); released.forEach(Silverfish::discard); fish.discard();
            level.setBlockAndUpdate(pos,oldBlock);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previous,level.getServer());
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void copperOreSelectsInfestedCopperspine(GameTestHelper h) { BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); h.getLevel().setBlockAndUpdate(pos.offset(2,0,0),Blocks.COPPER_ORE.defaultBlockState()); var child=R196Silverfish.createInfestedChild(h.getLevel(),pos,Blocks.STONE.defaultBlockState()); h.assertTrue(child instanceof R196Silverfish fish && fish.miteKind()==MobKind.COPPERSPINE,"Source nearby copper changes plain-stone inhabitant"); h.succeed(); }
    private static net.minecraft.world.entity.monster.Slime vanilla(GameTestHelper h,boolean magma) {
        var cube=magma ? EntityType.MAGMA_CUBE.create(h.getLevel()) : EntityType.SLIME.create(h.getLevel());
        BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); cube.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); cube.setNoAi(true); cube.setNoGravity(true); h.getLevel().addFreshEntity(cube); return cube;
    }
    @GameTest(template="empty") public static void nativeSlimeRetainsSourceSizeValues(GameTestHelper h) { var e=vanilla(h,false); e.setSize(1,true); near(h,e.getMaxHealth(),1,"Native tiny source health"); near(h,e.getAttributeValue(Attributes.ATTACK_DAMAGE),1,"Tiny can do source damage1"); near(h,e.getAttributeValue(Attributes.MOVEMENT_SPEED),.1,"Native source speed is size-independent"); near(h,e.getBbWidth(),.5,"Source half-block width"); h.assertTrue(e.getExperienceReward(h.getLevel(),null)==1,"Source native slime XP"); h.succeed(); }
    @GameTest(template="empty") public static void nativeMagmaDamageDefenseExperience(GameTestHelper h) { var e=vanilla(h,true); e.setSize(2,true); near(h,e.getAttributeValue(Attributes.ATTACK_DAMAGE),4,"Source magma damage2size"); near(h,e.getAttributeValue(Attributes.ARMOR),0,"Native modern armor must not stack with source natural defense"); near(h,R196Cube.vanillaNaturalDefense(e,e.damageSources().generic()),4,"Source natural2size"); h.assertTrue(e.getExperienceReward(h.getLevel(),null)==6,"Source magma XP3size"); h.succeed(); }
    @GameTest(template="empty") public static void nativeMagmaUsesMineralDamageGate(GameTestHelper h) { var e=vanilla(h,true); h.assertTrue(e.isInvulnerableTo(e.damageSources().generic()) && !e.isInvulnerableTo(MobCombat.source(e,"water")),"Native magma gate water/ordinary"); Player p=player(h); p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE)); h.assertTrue(!e.isInvulnerableTo(e.damageSources().playerAttack(p)),"Effective stone tool passes gate"); h.assertTrue(e.isInvulnerableTo(MobCombat.source(e,"acid")) && e.isInvulnerableTo(MobCombat.source(e,"pepsin")),"Both chemicals cannot harm magma"); h.succeed(); }
    @GameTest(template="empty") public static void nativeSlimeDissolutionSaves(GameTestHelper h) { var e=vanilla(h,false); e.setSize(1,true); BlockPos pos=e.blockPosition(); h.getLevel().setBlockAndUpdate(pos,Blocks.WHITE_WOOL.defaultBlockState()); e.tickCount=Math.floorMod(-e.getId()*47,20); ((CubeState)(Object)e).corrosion().contact(e,false); h.assertTrue(((CubeState)(Object)e).corrosion().progress(pos)==1,"Native slime has original pepsin ledger"); CompoundTag tag=new CompoundTag(); e.addAdditionalSaveData(tag); var restored=vanilla(h,false); restored.readAdditionalSaveData(tag); h.assertTrue(((CubeState)(Object)restored).corrosion().progress(pos)==1,"Native ledger survives native save path"); h.succeed(); }
    @GameTest(template="empty") public static void nativeTinySlimeContactHurtsAnimals(GameTestHelper h) {
        try (var clock = new BloodMoonHuntingTestClock(h.getLevel())) { var e=vanilla(h,false); e.setSize(1,true); Cow target=cow(h); target.moveTo(e.getX()+.2,e.getY(),e.getZ(),0,0); R196Cube.vanillaContact(e,target); near(h,target.getHealth(),99,"Tiny source slime attacks true animals"); h.succeed();
        }
    }
    @GameTest(template="empty") public static void nativeMagmaDoesNotPreyOnAnimals(GameTestHelper h) { var e=vanilla(h,true); e.setSize(2,true); Cow target=cow(h); target.moveTo(e.getX()+.2,e.getY(),e.getZ(),0,0); R196Cube.vanillaContact(e,target); near(h,target.getHealth(),100,"Source magma attacks players only"); h.succeed(); }
    @GameTest(template="empty") public static void nativeBurningSlimeCannotSplit(GameTestHelper h) { var e=vanilla(h,false); e.setSize(4,true); e.setRemainingFireTicks(40); e.setHealth(0); e.remove(Entity.RemovalReason.KILLED); h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,e.getBoundingBox().inflate(3),x->x!=e).isEmpty(),"Native family shares burning split guard"); h.succeed(); }
    @GameTest(template="empty") public static void nativeMagmaMeltsIceAndLosesAbsoluteHealth(GameTestHelper h) { var e=vanilla(h,true); e.setSize(2,true); BlockPos pos=e.blockPosition(); h.getLevel().setBlockAndUpdate(pos,Blocks.ICE.defaultBlockState()); R196Cube.magmaContactBlocks((net.minecraft.world.entity.monster.MagmaCube)e); h.assertTrue(!h.getLevel().getBlockState(pos).is(Blocks.ICE),"Source freezing material melts"); near(h,e.getHealth(),3,"Absolute melt damage ignores natural defense4"); h.succeed(); }
    @GameTest(template="empty") public static void vanillaCropDefaultsStayHealthy(GameTestHelper h) { for (var crop:List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES,Blocks.BEETROOTS)) h.assertTrue(!CubeCropBlight.blighted(crop.defaultBlockState()) && !CubeCropBlight.dead(crop.defaultBlockState()),"Boolean first state must be healthy after mixin registration"); h.succeed(); }
    @GameTest(template="empty") public static void dryNetherspawnDeathExplodes(GameTestHelper h) { R196Silverfish fish=(R196Silverfish)mob(h,MobKind.NETHERSPAWN); fish.hurt(fish.damageSources().generic(),100); h.assertTrue(fish.hasExploded(),"Actual dry death fires source explosion"); h.succeed(); }
    @GameTest(template="empty") public static void waterKilledNetherspawnDoesNotExplode(GameTestHelper h) { R196Silverfish fish=(R196Silverfish)mob(h,MobKind.NETHERSPAWN); fish.hurt(MobCombat.source(fish,"water"),100); h.assertTrue(!fish.hasExploded() && fish.isDeadOrDying(),"Water suppresses death blast even on dry land"); h.succeed(); }
    @GameTest(template="empty") public static void netherspawnBlastProtectsSourceNetherMaterials(GameTestHelper h) { R196Silverfish fish=(R196Silverfish)mob(h,MobKind.NETHERSPAWN); BlockPos pos=fish.blockPosition(); for (var block:List.of(Blocks.NETHERRACK,Blocks.NETHER_QUARTZ_ORE,Blocks.NETHER_BRICKS)) h.assertTrue(!fish.shouldBlockExplode(null,h.getLevel(),pos,block.defaultBlockState(),1),"Source nether explosion excludes protected block"); h.assertTrue(fish.shouldBlockExplode(null,h.getLevel(),pos,Blocks.COBBLESTONE.defaultBlockState(),1),"Other vulnerable blocks retain native explosion calculator"); h.succeed(); }
    @GameTest(template="empty") public static void sourceSphereDropCountMatchesOriginalJavaRandom(GameTestHelper h) {
        R196Cube cube=cube(h,MobKind.JELLY,1);
        for (int seed=0;seed<12;seed++) {
            int expected=new java.util.Random(seed).nextInt(3);
            int before=h.getLevel().getEntitiesOfClass(ItemEntity.class,cube.getBoundingBox().inflate(2),i->i.getItem().is(MiteWeaponContent.OCHRE_JELLY.get())).stream().mapToInt(i->i.getItem().getCount()).sum();
            cube.getRandom().setSeed(seed); R196Cube.sourceDrops(cube,cube.damageSources().generic(),true);
            int after=h.getLevel().getEntitiesOfClass(ItemEntity.class,cube.getBoundingBox().inflate(2),i->i.getItem().is(MiteWeaponContent.OCHRE_JELLY.get())).stream().mapToInt(i->i.getItem().getCount()).sum();
            h.assertTrue(after-before==expected,"Independent R196 JavaRandom 0..2 drop count; not guaranteed1");
        }
        h.succeed();
    }
}
