package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.NativeArachnidEvents;
import io.github.ningmeng123.mite.entity.R196Bat;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.entity.R196Web;
import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.SourceSpiderFinalization;
import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import io.github.ningmeng123.mite.entity.ai.NativeLeapAtTargetGoal;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.BowItem;
import net.neoforged.neoforge.event.EventHooks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent source time/draw fixtures exercise native registration, event, projectile, toxin and saved inventory paths. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class NativeArachnidGameTests {
    private static Spider spider(GameTestHelper h,boolean cave) {
        Spider entity=cave ? new CaveSpider(EntityType.CAVE_SPIDER,h.getLevel()) : new Spider(EntityType.SPIDER,h.getLevel());
        entity.moveTo(h.absolutePos(new BlockPos(1,3,1)).getCenter()); return entity;
    }
    private static Cow cow(GameTestHelper h,Vec3 position) {
        Cow entity=new Cow(EntityType.COW,h.getLevel()); entity.moveTo(position); entity.setNoAi(true); entity.setNoGravity(true);
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); entity.setHealth(100); h.getLevel().addFreshEntity(entity); return entity;
    }
    private static void clock(Spider spider,int offsetTime) { spider.tickCount=offsetTime-spider.getId()*47; }
    private static List<R196Web> webs(Spider spider) {
        return spider.level().getEntitiesOfClass(R196Web.class,spider.getBoundingBox().inflate(8),web -> web.getOwner()==spider);
    }
    private static void close(GameTestHelper h,double actual,double expected,String description) {
        h.assertTrue(Math.abs(actual-expected)<.00001,description+": "+actual+" != "+expected);
    }
    private static GoalSelector goals(Spider spider,String name) throws Exception {
        Field field=Mob.class.getDeclaredField(name); field.setAccessible(true); return (GoalSelector)field.get(spider);
    }
    @GameTest(template="ranged_027") public static void nativeArachnidsReplaceModernLeapAndBrightRoomGoalsOnly(GameTestHelper h) throws Exception {
        for(boolean cave:new boolean[]{false,true}) {
            Spider spider=spider(h,cave); var actions=goals(spider,"goalSelector").getAvailableGoals();
            h.assertValueEqual(actions.stream().filter(g -> g.getGoal() instanceof NativeLeapAtTargetGoal).count(),1L,"Actual native constructor registers one source leap goal");
            h.assertTrue(actions.stream().noneMatch(g -> g.getGoal() instanceof LeapAtTargetGoal),"Vanilla closed [2,4] reduced-delay leap is removed");
            h.assertTrue(goals(spider,"targetSelector").getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof NearestAttackableTargetGoal<?>),"Vanilla room-brightness and golem prey selectors are removed");
        }
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void webClockUsesFiveHundredAndTwoHundredOffsetTicksAndSharedSavedStock(GameTestHelper h) {
        for(boolean cave:new boolean[]{false,true}) {
            Spider spider=spider(h,cave); Cow target=cow(h,spider.position().add(5,0,0)); spider.setTarget(target);
            SourceVanillaMobLoot.setWebStock(spider,2); int interval=cave ? 200 : 500;
            clock(spider,interval-1); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(spider));
            h.assertValueEqual(webs(spider).size(),0,"No throw one source offset tick early");
            h.assertValueEqual(SourceVanillaMobLoot.webStock(spider),2,"Waiting preserves saved ammunition");
            h.assertTrue(NativeArachnidEvents.clearThrowRay(spider,target),"Unobstructed actual target qualifies");
            clock(spider,interval); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(spider));
            h.assertValueEqual(webs(spider).size(),1,"Registered tick event creates actual web projectile at source interval");
            h.assertValueEqual(SourceVanillaMobLoot.webStock(spider),1,"Source throw depletes the same stock as death loot");
            CompoundTag saved=new CompoundTag(); spider.saveWithoutId(saved);
            Spider restored=spider(h,cave); restored.load(saved);
            h.assertValueEqual(SourceVanillaMobLoot.webStock(restored),1,"NeoForge saved stock is not rerolled on reload");
            webs(spider).forEach(R196Web::discard); target.discard();
        }
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void nativeWebRayRejectsWaterWallsAndNearestInterveningCreature(GameTestHelper h) {
        Spider spider=spider(h,false); Cow target=cow(h,spider.position().add(5,0,0)); SourceVanillaMobLoot.setWebStock(spider,2);
        Cow intervening=cow(h,spider.position().add(2,0,0));
        h.assertTrue(!NativeArachnidEvents.clearThrowRay(spider,target),"Nearest entity must be the chosen target"); intervening.discard();
        BlockPos middle=BlockPos.containing(spider.getX()+2,spider.getY(),spider.getZ());
        h.getLevel().setBlock(middle,Blocks.WATER.defaultBlockState(),3); h.getLevel().setBlock(middle.above(),Blocks.WATER.defaultBlockState(),3);
        h.assertTrue(!NativeArachnidEvents.clearThrowRay(spider,target),"Source thrown-web selection ray includes liquids");
        h.getLevel().setBlock(middle,Blocks.STONE.defaultBlockState(),3); h.getLevel().setBlock(middle.above(),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!NativeArachnidEvents.clearThrowRay(spider,target),"Both head and quarter-height rays blocked by wall");
        spider.setTarget(target); clock(spider,500); NativeArachnidEvents.tickSource(spider);
        h.assertValueEqual(SourceVanillaMobLoot.webStock(spider),2,"Failed qualification does not consume ammo");
        h.getLevel().setBlock(middle,Blocks.AIR.defaultBlockState(),3); h.getLevel().setBlock(middle.above(),Blocks.AIR.defaultBlockState(),3); target.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void burningWebUsesTenTickPredictionAndCanceledSpawnKeepsInventory(GameTestHelper h) {
        Spider spider=spider(h,false); Cow target=cow(h,spider.position().add(5,0,0)); target.setDeltaMovement(.1,0,.2);
        SourceVanillaMobLoot.setWebStock(spider,2); spider.igniteForSeconds(5);
        h.assertTrue(NativeArachnidEvents.throwWeb(spider,target),"Actual native burning spider creates a source web");
        R196Web web=webs(spider).getFirst(); h.assertTrue(web.isOnFire(),"Burning shooter ignites web for ten seconds");
        Vec3 predicted=target.position().add(1,0,2); double x=predicted.x-spider.getX(),z=predicted.z-spider.getZ();
        double y=target.getEyeY()-1.1F-web.getY()+Math.sqrt(x*x+z*z)*.2F; Vec3 expected=new Vec3(x,y,z).normalize().scale(.8F);
        close(h,web.getDeltaMovement().x,expected.x,"Ten-tick predicted horizontal X"); close(h,web.getDeltaMovement().z,expected.z,"Ten-tick predicted horizontal Z");
        close(h,web.getDeltaMovement().y,expected.y,"Source launch height and .2 arc"); web.discard();
        Consumer<EntityJoinLevelEvent> cancel=event -> { if(event.getEntity() instanceof R196Web candidate && candidate.getOwner()==spider) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,cancel);
        try { h.assertTrue(!NativeArachnidEvents.throwWeb(spider,target),"Canceled native entity join rejects projectile"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        h.assertValueEqual(SourceVanillaMobLoot.webStock(spider),1,"Canceled throw leaves exact remaining stock"); target.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void sourceLeapHasStrictTwoSixDistanceAndIndependentOneInTenDraw(GameTestHelper h) {
        Spider spider=spider(h,false); Cow target=cow(h,spider.position().add(5,0,0)); int successes=0;
        for(long seed=0;seed<80;seed++) {
            spider.setOnGround(true); spider.setDeltaMovement(.1,0,.2); spider.getRandom().setSeed(seed);
            boolean expected=new Random(seed).nextInt(10)==0,actual=NativeArachnidEvents.trySourceLeap(spider,target);
            h.assertTrue(actual==expected,"Source exact randomInt10 at five blocks seed "+seed);
            if(actual) { successes++; close(h,spider.getDeltaMovement().x,.42,".4 leap plus .2 old X"); close(h,spider.getDeltaMovement().y,.4,"Source vertical impulse"); close(h,spider.getDeltaMovement().z,.04,"Source .2 old Z"); }
        }
        h.assertTrue(successes>0 && successes<80,"Independent fixtures exercise both leap and no-leap");
        for(int boundary:new int[]{2,6}) {
            target.setPos(spider.getX()+boundary,spider.getY(),spider.getZ()); spider.getRandom().setSeed(0); spider.setOnGround(true); spider.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(!NativeArachnidEvents.trySourceLeap(spider,target),"Strict source boundary "+boundary);
            h.assertValueEqual(spider.getRandom().nextInt(10),new Random(0).nextInt(10),"Out-of-range attempts do not consume leap RNG");
        }
        target.discard(); h.succeed();
    }
    private static final class BrightSpider extends Spider {
        BrightSpider(Level level) { super(EntityType.SPIDER,level); }
        @Override public float getLightLevelDependentMagicValue() { return 1F; }
    }
    @GameTest(template="ranged_027") public static void dayPeaceRequiresActualOutdoorRouteAndRetaliationAndCaveAlwaysHostile(GameTestHelper h) {
        BrightSpider spider=new BrightSpider(h.getLevel()); BlockPos origin=h.absolutePos(new BlockPos(2,3,2)).atY(h.getLevel().getMaxBuildHeight()-8);
        spider.moveTo(origin.getCenter()); var player=h.makeMockPlayer(GameType.SURVIVAL); spider.setTarget(player);
        SourceVanillaMobLoot.setWebStock(spider,0); // Initialize its constructor-equivalent stock before the independent peaceful draw seed.
        h.assertTrue(NativeArachnidEvents.isOutdoors(spider) && NativeArachnidEvents.peacefulHere(spider),"Bright open sky permits ordinary source peaceful behavior");
        long seed=0; while(new Random(seed).nextInt(100)!=0) seed++;
        h.assertTrue(spider.isAlive() && !spider.isNoAi(),"Bright fixture is alive with source AI enabled");
        h.assertTrue(spider.getLastHurtByMob()==null && spider.getTarget()==player,"Bright fixture begins with an unprovoked player target");
        h.assertValueEqual(SourceVanillaMobLoot.webStock(spider),0,"Stock already initialized before the peaceful draw");
        spider.getRandom().setSeed(seed); h.assertValueEqual(spider.getRandom().nextInt(100),0,"Actual entity random source agrees with independent peaceful fixture");
        spider.getRandom().setSeed(seed); NativeArachnidEvents.tickSource(spider); h.assertTrue(spider.getTarget()==null,"Unprovoked player forgotten on exact source one-in-hundred draw; stock="+SourceVanillaMobLoot.webStock(spider)+", last="+spider.getLastHurtByMob()+", target="+spider.getTarget());
        spider.setTarget(player); spider.setLastHurtByMob(player); spider.getRandom().setSeed(seed); NativeArachnidEvents.tickSource(spider);
        h.assertTrue(spider.getTarget()==player,"Player retaliation is retained in bright outdoors"); spider.setLastHurtByMob(null);
        for(BlockPos roof:BlockPos.betweenClosed(origin.offset(-3,3,-3),origin.offset(3,3,3))) h.getLevel().setBlock(roof,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!NativeArachnidEvents.isOutdoors(spider) && !NativeArachnidEvents.peacefulHere(spider),"Bright indoor source creature remains hostile beneath complete roof");
        spider.setTarget(player); spider.getRandom().setSeed(seed); NativeArachnidEvents.tickSource(spider); h.assertTrue(spider.getTarget()==player,"No modern brightness-only indoor pacification");
        CaveSpider cave=new CaveSpider(EntityType.CAVE_SPIDER,h.getLevel()); cave.moveTo(origin.getCenter());
        h.assertTrue(!NativeArachnidEvents.peacefulHere(cave),"Source te explicitly overrides peacefulDuringDay false");
        for(BlockPos roof:BlockPos.betweenClosed(origin.offset(-3,3,-3),origin.offset(3,3,3))) h.getLevel().setBlock(roof,Blocks.AIR.defaultBlockState(),3);
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void cavePoisonHasFixedTwentyFourSecondsAndNeedsRealHealthLoss(GameTestHelper h) {
        CaveSpider spider=(CaveSpider)spider(h,true); Cow target=cow(h,spider.position().add(1,0,0));
        h.assertTrue(spider.doHurtTarget(target),"Actual native cave spider attack succeeds");
        h.assertTrue(target.hasEffect(MobEffects.POISON),"Actual loss of health triggers native source toxin");
        h.assertValueEqual(target.getEffect(MobEffects.POISON).getDuration(),480,"Source fixed24 seconds ignores modern difficulty durations");
        Cow immune=cow(h,spider.position().add(2,0,0)); immune.setInvulnerable(true);
        h.assertTrue(!spider.doHurtTarget(immune) && !immune.hasEffect(MobEffects.POISON),"Invulnerable target acquires no source toxin");
        Cow absorbed=cow(h,spider.position().add(3,0,0)); absorbed.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100); absorbed.setAbsorptionAmount(100);
        float before=absorbed.getHealth(); spider.doHurtTarget(absorbed);
        h.assertValueEqual(absorbed.getHealth(),before-1F,"Source of keeps minimum one health loss after absorption");
        h.assertTrue(absorbed.hasEffect(MobEffects.POISON),"Source actual one-point loss still triggers cave spider toxin");
        target.discard(); immune.discard(); absorbed.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void nativeArachnidFindsVisibleChickenInsteadOfModernGolemPrey(GameTestHelper h) {
        Spider spider=spider(h,true); SourceVanillaMobLoot.setWebStock(spider,0);
        Chicken chicken=new Chicken(EntityType.CHICKEN,h.getLevel()); chicken.moveTo(spider.position().add(3,0,0)); chicken.setNoAi(true); h.getLevel().addFreshEntity(chicken);
        long seed=0; while(new Random(seed).nextFloat()>=.01F) seed++;
        clock(spider,1); spider.getRandom().setSeed(seed); NativeArachnidEvents.acquireTarget(spider);
        h.assertTrue(spider.getTarget()==chicken,"Source visible rq prey selected on independent one-percent draw"); chicken.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void vampirePreySelectionNeedsSightAndSourceInitialFlagsAndExcludesHellhound(GameTestHelper h) {
        R196Bat bat=(R196Bat)MiteMobs.type(MobKind.VAMPIRE_BAT).create(h.getLevel()); bat.moveTo(h.absolutePos(new BlockPos(1,3,1)).getCenter());
        h.assertTrue(!bat.isResting(),"Source initial BatFlags zero overrides modern resting constructor");
        Cow hidden=cow(h,bat.position().add(3,0,0)),visible=cow(h,bat.position().add(0,0,4));
        BlockPos wall=BlockPos.containing(bat.getX()+1,bat.getY(),bat.getZ());
        for(BlockPos p:BlockPos.betweenClosed(wall.offset(0,0,-1),wall.offset(0,2,1))) h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(bat.closestVisiblePrey()==visible,"Closer prey behind wall is rejected by source LOS flag");
        LivingEntity hellhound=(LivingEntity)MiteMobs.type(MobKind.HELLHOUND).create(h.getLevel());
        h.assertTrue(!bat.preysUpon(hellhound),"Source nn.isTrueAnimal explicitly excludes hellhound");
        hidden.discard(); visible.discard(); for(BlockPos p:BlockPos.betweenClosed(wall.offset(0,0,-1),wall.offset(0,2,1))) h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),3);
        h.succeed();
    }
    private static long jockeySeed(boolean positive) {
        for(long seed=0;seed<10000;seed++) if((new Random(seed).nextInt(100)==0)==positive) return seed;
        throw new AssertionError("No independent source world-random jockey fixture");
    }
    @GameTest(template="ranged_027") public static void onlyNativeTtUsesWorldRandomOnePercentJockeyAndEveryRiderIsRanged(GameTestHelper h) {
        for(boolean positive:new boolean[]{false,true}) {
            Spider spider=spider(h,false); spider.getRandom().setSeed(7); h.getLevel().getRandom().setSeed(jockeySeed(positive));
            EventHooks.finalizeMobSpawn(spider,h.getLevel(),h.getLevel().getCurrentDifficultyAt(spider.blockPosition()),MobSpawnType.SPAWN_EGG,null);
            h.assertValueEqual(spider.getPassengers().size(),positive ? 1 : 0,"Exact source world randomInt100 controls tt jockey");
            if(positive) {
                AbstractSkeleton rider=(AbstractSkeleton)spider.getFirstPassenger();
                h.assertTrue(rider.getType()==EntityType.SKELETON && ((OrdinarySkeletonState)rider).mite$sourceSkeletonType()==0
                        && rider.getMainHandItem().getItem() instanceof BowItem,"Ordinary source rejection loop accepts only subtype0 bow rider"); rider.discard();
            }
            h.assertTrue(spider.getSpawnType()==MobSpawnType.SPAWN_EGG,"Source callback retains native spawn reason");
        }
        h.succeed();
    }
    @GameTest(template="ranged_027") public static void fourNonTtSourceSpidersAndCaveReceiveOnlyBaseFollowCallback(GameTestHelper h) {
        for(MobKind kind:new MobKind[]{MobKind.WOOD_SPIDER,MobKind.BLACK_WIDOW_SPIDER,MobKind.DEMON_SPIDER,MobKind.PHASE_SPIDER}) {
            for(long seed=0;seed<16;seed++) {
                Spider spider=(Spider)MiteMobs.type(kind).create(h.getLevel()); spider.moveTo(h.absolutePos(new BlockPos(1,3,1)).getCenter());
                spider.getRandom().setSeed(seed); h.getLevel().getRandom().setSeed(jockeySeed(true));
                Spider.SpiderEffectsGroupData foreignGroup=new Spider.SpiderEffectsGroupData(); foreignGroup.effect=MobEffects.REGENERATION;
                var returned=EventHooks.finalizeMobSpawn(spider,h.getLevel(),h.getLevel().getCurrentDifficultyAt(spider.blockPosition()),MobSpawnType.SPAWN_EGG,foreignGroup);
                h.assertTrue(returned==foreignGroup && spider.getPassengers().isEmpty() && spider.getActiveEffects().isEmpty(),"Non-tt source callback cannot inherit modern jockey or random potion "+kind);
                close(h,spider.getAttribute(Attributes.FOLLOW_RANGE).getModifier(SourceSpiderFinalization.FOLLOW_BONUS).amount(),new Random(seed).nextGaussian()*.05,"Source common og Gaussian follow bonus "+kind);
                h.assertValueEqual(h.getLevel().getRandom().nextInt(100),0,"Source non-tt callback never draws world jockey RNG");
                h.assertTrue(spider.getSpawnType()==MobSpawnType.SPAWN_EGG,"New source species keeps spawn metadata");
            }
        }
        CaveSpider cave=(CaveSpider)spider(h,true); cave.getRandom().setSeed(17);
        EventHooks.finalizeMobSpawn(cave,h.getLevel(),h.getLevel().getCurrentDifficultyAt(cave.blockPosition()),MobSpawnType.SPAWN_EGG,null);
        h.assertTrue(cave.getSpawnType()==MobSpawnType.SPAWN_EGG && cave.getPassengers().isEmpty(),"Source te has common og callback but no tt rider");
        close(h,cave.getAttribute(Attributes.FOLLOW_RANGE).getModifier(SourceSpiderFinalization.FOLLOW_BONUS).amount(),new Random(17).nextGaussian()*.05,"Source te follow bonus is not skipped by modern CaveSpider no-op"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void undergroundNativeSpiderJockeyIsLongdeadAndRanged(GameTestHelper h) {
        var underground=h.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD); h.assertTrue(underground!=null,"Registered underground level");
        underground.getChunk(0,0); Spider spider=new Spider(EntityType.SPIDER,underground); spider.moveTo(.5,underground.getSeaLevel(),.5,0,0);
        underground.getRandom().setSeed(jockeySeed(true));
        EventHooks.finalizeMobSpawn(spider,underground,underground.getCurrentDifficultyAt(spider.blockPosition()),MobSpawnType.SPAWN_EGG,null);
        h.assertValueEqual(spider.getPassengers().size(),1,"Source underground tt positive one-percent jockey");
        h.assertTrue(spider.getFirstPassenger() instanceof R196Skeleton rider && rider.miteKind()==MobKind.LONGDEAD && rider.skeletonType()==0
                && rider.getMainHandItem().getItem() instanceof BowItem,"Underground source rider identity and rejected melee subtype");
        spider.getFirstPassenger().discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void canceledSourceRiderFinalizationCannotBeRetriedAroundOrCreatePassenger(GameTestHelper h) {
        Spider spider=spider(h,false); h.getLevel().getRandom().setSeed(jockeySeed(true)); int[] callbacks={0};
        Consumer<FinalizeSpawnEvent> cancel=event -> {
            if(event.getEntity() instanceof AbstractSkeleton) { callbacks[0]++; event.setCanceled(true); event.setSpawnCancelled(true); }
        };
        NeoForge.EVENT_BUS.addListener(FinalizeSpawnEvent.class,cancel);
        try { EventHooks.finalizeMobSpawn(spider,h.getLevel(),h.getLevel().getCurrentDifficultyAt(spider.blockPosition()),MobSpawnType.SPAWN_EGG,null); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        h.assertValueEqual(callbacks[0],1,"Canceled native rider request is honored immediately rather than rerolled");
        h.assertTrue(spider.getPassengers().isEmpty(),"Cancellation produces no retained mounted entity"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void commandedNetherSpiderHasBoundedRiderLoopAndLegalSourceBowFallback(GameTestHelper h) {
        var nether=h.getLevel().getServer().getLevel(Level.NETHER); h.assertTrue(nether!=null,"Native Nether level"); nether.getChunk(0,0);
        Spider spider=new Spider(EntityType.SPIDER,nether); spider.moveTo(.5,70,.5,0,0); int[] callbacks={0};
        Consumer<FinalizeSpawnEvent> inspect=event -> { if(event.getEntity().level()==nether && event.getEntity() instanceof AbstractSkeleton) callbacks[0]++; };
        AbstractSkeleton rider;
        NeoForge.EVENT_BUS.addListener(FinalizeSpawnEvent.class,inspect);
        try { rider=SourceSpiderFinalization.createRider(spider,nether,nether.getCurrentDifficultyAt(spider.blockPosition()),MobSpawnType.SPAWN_EGG); }
        finally { NeoForge.EVENT_BUS.unregister(inspect); }
        h.assertValueEqual(callbacks[0],64,"Modern guard bounds the source unreachable Nether type0 rejection loop");
        h.assertTrue(rider!=null && ((OrdinarySkeletonState)rider).mite$sourceSkeletonType()==0 && rider.getMainHandItem().getItem() instanceof BowItem,"Safe command-only Nether adaptation retains source ranged rider kit");
        rider.discard(); h.succeed();
    }
}
