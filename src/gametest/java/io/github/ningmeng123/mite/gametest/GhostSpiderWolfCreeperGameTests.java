package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.*;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Explicit extracted source constants verify real immunity, AI, effects, projectile placement and blast lifecycle. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class GhostSpiderWolfCreeperGameTests {
    private static Mob mob(GameTestHelper h,MobKind kind) { Mob e=MiteMobs.type(kind).create(h.getLevel()); BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); e.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); e.setNoAi(true); e.setNoGravity(true); h.getLevel().addFreshEntity(e); return e; }
    private static Player player(GameTestHelper h) { Player p=h.makeMockPlayer(GameType.SURVIVAL); p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); p.setHealth(100); p.moveTo(h.absolutePos(new BlockPos(3,3,2)).getCenter()); return p; }
    private static Cow cow(GameTestHelper h) { Cow c=EntityType.COW.create(h.getLevel()); c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); c.setHealth(100); c.moveTo(h.absolutePos(new BlockPos(3,3,2)).getCenter()); c.setNoAi(true); c.setNoGravity(true); h.getLevel().addFreshEntity(c); return c; }
    private static long firstSeed(java.util.function.Predicate<Float> predicate) { for (long seed=0;seed<100000;seed++) if (predicate.test(new java.util.Random(seed).nextFloat())) return seed; throw new AssertionError("No independent JavaRandom fixture"); }
    private static void near(GameTestHelper h,double actual,double expected,String text) { h.assertTrue(Math.abs(actual-expected)<.001,text+" expected "+expected+", got "+actual); }

    @GameTest(template="empty") public static void all29SourceLivingIdentitiesAndBaselines(GameTestHelper h) {
        double[][] rows={
            {20,5,.28,40,10},{20,5,.25,40,10},{20,4,.23,40,10},{18,5,1,28,15},{20,4,.4,16,15},{16,5,.4,16,10},{6,1,.8,28,5},{20,2,.25,32,15},{20,5,.23,40,10},{20,5,.25,40,15},
            {6,1,.8,28,8},{30,7,.26,40,15},{30,12,.2,20,15},{1,2,.1,16,2},{1,3,.1,16,3},{1,3,.1,16,4},{1,4,.1,16,5},{3,1,.7,16,5},{6,2,.7,16,10},{12,6,.29,40,15},
            {24,8,.29,40,25},{3,1,.7,16,10},{8,3,.6,32,10},{8,3,.6,32,10},{8,3,.6,32,10},{30,6,.2,20,15},{20,5,.26,40,15},{24,8,.27,40,30},{6,3,.8,28,10}};
        h.assertTrue(MobKind.values().length==29,"Actual nt512..540 source registry count");
        for (MobKind kind:MobKind.values()) {
            Mob e=mob(h,kind); if (e instanceof R196Cube cube) cube.setSize(1,true);
            double[] expected=rows[kind.sourceId()-512];
            h.assertTrue(((R196Mob)e).miteKind()==kind,"Spawned identity matches source ID");
            near(h,e.getMaxHealth(),expected[0],kind+" health"); near(h,e.getAttributeValue(Attributes.ATTACK_DAMAGE),expected[1],kind+" effective attack");
            near(h,e.getAttributeValue(Attributes.MOVEMENT_SPEED),expected[2],kind+" source attribute speed"); near(h,e.getAttributeValue(Attributes.FOLLOW_RANGE),expected[3],kind+" source attribute follow");
            h.assertTrue(e.getExperienceReward(h.getLevel(),null)==expected[4],kind+" unarmed source XP");
            ResourceLocation id=ResourceLocation.fromNamespaceAndPath("mite",kind.path()+"_spawn_egg"); h.assertTrue(BuiltInRegistries.ITEM.containsKey(id),kind+" actual egg registered");
            e.discard();
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void wightRejectsPlainButTakesSilver(GameTestHelper h) { R196Undead e=(R196Undead)mob(h,MobKind.WIGHT); Player p=player(h); float before=e.getHealth(); h.assertTrue(!e.hurt(e.damageSources().playerAttack(p),3) && e.getHealth()==before,"Source plain damage rejected"); p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(MiteTools.get("mite:silver_sword"))); h.assertTrue(e.hurt(e.damageSources().playerAttack(p),3) && e.getHealth()<before,"Source silver damage admitted"); h.succeed(); }
    @GameTest(template="empty") public static void shadowRejectsFireButAcceptsMagic(GameTestHelper h) { R196Undead e=(R196Undead)mob(h,MobKind.SHADOW); h.assertTrue(!e.hurt(e.damageSources().inFire(),2),"Shadow is fire immune"); h.assertTrue(e.hurt(e.damageSources().magic(),2),"Source magic admits damage"); h.succeed(); }
    @GameTest(template="empty") public static void absoluteDamageBypassesGhostSpecificGates(GameTestHelper h) { for (MobKind kind:List.of(MobKind.WIGHT,MobKind.SHADOW)) { var e=mob(h,kind); h.assertTrue(e.hurt(e.damageSources().genericKill(),1000) && e.isDeadOrDying(),"Source absolute/ignore-specific route cannot be blocked by "+kind); } h.succeed(); }
    @GameTest(template="empty") public static void stalkerDoesNotInheritShadowImmunity(GameTestHelper h) { var e=mob(h,MobKind.INVISIBLE_STALKER); h.assertTrue(e.hurt(e.damageSources().generic(),2),"Actual stalker has no special immune override"); h.succeed(); }
    @GameTest(template="empty") public static void ghoulHealthLossAddsStrongSlowness(GameTestHelper h) { var e=mob(h,MobKind.GHOUL); Cow c=cow(h); e.doHurtTarget(c); var effect=c.getEffect(MobEffects.MOVEMENT_SLOWDOWN); h.assertTrue(effect!=null && effect.getDuration()==50 && effect.getAmplifier()==5,"Source ghoul stun50 level5"); h.succeed(); }
    @GameTest(template="empty") public static void wightDrainsExperienceFromConfirmedHit(GameTestHelper h) { var e=mob(h,MobKind.WIGHT); Player p=player(h); PlayerExperience.setLevels(p,10); int before=p.totalExperience; e.getRandom().setSeed(firstSeed(f->f<.4F)); h.assertTrue(e.doHurtTarget(p),"Source health-losing wight hit"); h.assertTrue(p.totalExperience==before-110,"Source max((level+1)*10,20) atlevel10, not fixedXP"); h.succeed(); }
    @GameTest(template="empty") public static void shadowHitWeaknessAndPendingDimming(GameTestHelper h) { var e=mob(h,MobKind.SHADOW); Player p=player(h); e.doHurtTarget(p); var effect=p.getEffect(MobEffects.WEAKNESS); h.assertTrue(effect!=null && effect.getDuration()==600 && effect.getAmplifier()==0,"Source weakness600"); near(h,p.getPersistentData().getFloat(VisionDimming.PENDING),2,"Actual hit accumulates source visual resistance amount2"); h.succeed(); }
    @GameTest(template="empty") public static void silverArmorHalvesShadowDimming(GameTestHelper h) { Player p=player(h); String[] pieces={"boots","leggings","chestplate","helmet"}; EquipmentSlot[] slots={EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD}; for (int i=0;i<4;i++) p.setItemSlot(slots[i],new ItemStack(MiteArmorContent.get("mite:silver_"+pieces[i]))); VisionDimming.apply(p,2); near(h,p.getPersistentData().getFloat(VisionDimming.PENDING),1,"Source complete24components silver covers one, resistance .5"); h.succeed(); }
    @GameTest(template="empty") public static void shadowRemovesLitJackLanternAndDropsTorch(GameTestHelper h) { R196Undead e=(R196Undead)mob(h,MobKind.SHADOW); BlockPos pos=e.blockPosition().east(); h.getLevel().setBlockAndUpdate(pos,Blocks.JACK_O_LANTERN.defaultBlockState()); h.assertTrue(e.disableLight(),"Nearby light source disabled"); h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.CARVED_PUMPKIN),"Source lantern becomes unlit pumpkin"); h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,e.getBoundingBox().inflate(2),i->i.getItem().is(Items.TORCH)).size()==1,"Source one torch recovered"); h.succeed(); }
    @GameTest(template="empty") public static void hurtStalkerCannotDisableLight(GameTestHelper h) { R196Undead e=(R196Undead)mob(h,MobKind.INVISIBLE_STALKER); e.hurtTime=2; BlockPos pos=e.blockPosition().east(); h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(pos,Blocks.TORCH.defaultBlockState()); h.assertTrue(!e.disableLight() && h.getLevel().getBlockState(pos).is(Blocks.TORCH),"Source hurt timer gates extinguishing"); h.succeed(); }
    @GameTest(template="empty") public static void spiderPoisonDurationsAreDistinct(GameTestHelper h) { for (MobKind kind:List.of(MobKind.WOOD_SPIDER,MobKind.BLACK_WIDOW_SPIDER,MobKind.DEMON_SPIDER)) { var e=mob(h,kind); Cow c=cow(h); e.doHurtTarget(c); var poison=c.getEffect(MobEffects.POISON); int expected=kind==MobKind.WOOD_SPIDER ? 240 : kind==MobKind.BLACK_WIDOW_SPIDER ? 960 : 480; h.assertTrue(poison!=null && poison.getDuration()==expected && poison.getAmplifier()==0,"Independent source spider poison durations"); if (kind==MobKind.DEMON_SPIDER) h.assertTrue(c.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==5,"Demon combines stun and poison"); } h.succeed(); }
    @GameTest(template="empty") public static void phaseHasEvasionStockAndNoWebStock(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.PHASE_SPIDER); h.assertTrue(e.evasions()>=2 && e.evasions()<=4 && e.webStock()==0,"Source2..4charges and web0"); h.succeed(); }
    @GameTest(template="empty") public static void phaseTeleportWorksBelowZero(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.PHASE_SPIDER); BlockPos anchor=e.blockPosition(); int y=Math.max(h.getLevel().getMinBuildHeight()+5,-40); for (BlockPos p:BlockPos.betweenClosed(anchor.getX()-2,y,anchor.getZ()-2,anchor.getX()+2,y+3,anchor.getZ()+2)) h.getLevel().setBlockAndUpdate(p,Blocks.AIR.defaultBlockState()); h.getLevel().setBlockAndUpdate(new BlockPos(anchor.getX(),y-1,anchor.getZ()),Blocks.STONE.defaultBlockState()); h.assertTrue(e.teleportToSource(anchor.getX()+.5,y,anchor.getZ()+.5) && e.getY()<0,"Modern min-height adaptation admits negativeY and valid standing space"); h.succeed(); }
    @GameTest(template="empty") public static void phaseNeverTeleportsPastMinHeight(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.PHASE_SPIDER); h.assertTrue(!e.teleportToSource(e.getX()+4,h.getLevel().getMinBuildHeight(),e.getZ()),"Source lower-bound adapted to dimension minimum"); h.succeed(); }
    @GameTest(template="empty") public static void phaseExcludedFireDoesNotConsumeEvasion(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.PHASE_SPIDER); int before=e.evasions(); e.hurt(e.damageSources().inFire(),1); h.assertTrue(e.evasions()==before,"Source fire exception never attempts evasion"); h.succeed(); }
    @GameTest(template="empty") public static void phaseStockSurvivesEntitySave(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.PHASE_SPIDER); CompoundTag tag=new CompoundTag(); e.addAdditionalSaveData(tag); R196Spider restored=(R196Spider)mob(h,MobKind.PHASE_SPIDER); restored.readAdditionalSaveData(tag); h.assertTrue(restored.evasions()==e.evasions(),"Source evasions are entity state, not regenerated upon load"); h.succeed(); }
    @GameTest(template="empty") public static void phaseAbsoluteDamageNeverAttemptsEvasion(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.PHASE_SPIDER); int before=e.evasions(); h.assertTrue(e.hurt(e.damageSources().genericKill(),1000) && e.isDeadOrDying(),"Absolute source damage bypasses teleport immunity"); h.assertTrue(e.evasions()==before,"Absolute hit preserves unused evasion count"); h.succeed(); }
    @GameTest(template="empty") public static void phaseRecoversOnlyAtHundredOffsetTicks(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.PHASE_SPIDER); CompoundTag tag=new CompoundTag(); e.addAdditionalSaveData(tag); tag.putInt("max_num_evasions",4); tag.putInt("num_evasions",1); e.readAdditionalSaveData(tag); e.tickCount=Math.floorMod(-e.getId()*47,100)+1; e.tick(); h.assertTrue(e.evasions()==1,"Ordinary tick cannot regenerate a charge"); e.tickCount=Math.floorMod(-e.getId()*47,100); e.tick(); h.assertTrue(e.evasions()==2,"Exactly one charge restored at100offsetticks"); h.succeed(); }
    @GameTest(template="empty") public static void webPredictsFourTicksOfMovement(GameTestHelper h) { Cow c=cow(h); BlockPos origin=c.blockPosition(); c.setDeltaMovement(.5,0,0); BlockPos lead=origin.east(2); h.getLevel().removeBlock(lead,false); h.assertTrue(R196Web.impact(c,false) && h.getLevel().getBlockState(lead).is(Blocks.COBWEB),"Source entity hit first tries four-tick horizontal lead"); h.succeed(); }
    @GameTest(template="empty") public static void webOnlyReplacesSingleSnowLayer(GameTestHelper h) { Player p=player(h); BlockPos pos=h.absolutePos(new BlockPos(2,3,2)); h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(pos,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,2)); h.assertTrue(!R196Web.place(h.getLevel(),pos,false,p),"Source cannot overwrite thick snow"); h.getLevel().setBlockAndUpdate(pos,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,1)); h.assertTrue(R196Web.place(h.getLevel(),pos,false,p),"Source replaces only1layer"); h.succeed(); }
    @GameTest(template="empty") public static void burningWebIgnitesFiveSeconds(GameTestHelper h) { Cow c=cow(h); R196Web.impact(c,true); h.assertTrue(c.getRemainingFireTicks()==100,"Source web impact adds five seconds"); h.succeed(); }
    @GameTest(template="empty") public static void thrownDemonWebConsumesOneStock(GameTestHelper h) {
        R196Spider e=(R196Spider)mob(h,MobKind.DEMON_SPIDER);
        CompoundTag tag=new CompoundTag(); e.addAdditionalSaveData(tag); tag.putInt("num_webs",2); e.readAdditionalSaveData(tag);
        Cow target=cow(h);
        // A successful addFreshEntity can precede random-grid entity-section visibility.
        // Capture the actual spawn synchronously instead of querying an inaccessible section.
        try (var capture=new SpawnedEntityCapture(h.getLevel(),entity->entity instanceof R196Web web && web.getOwner()==e)) {
            e.throwWeb(target);
            h.assertTrue(e.webStock()==1,"Successful spawn consumes exactlyone source web");
            var missiles=capture.live(R196Web.class);
            h.assertTrue(missiles.size()==1,"Exactly one real projectile is spawned by this demon spider");
            h.assertTrue(missiles.get(0).isOnFire() && missiles.get(0).getRemainingFireTicks()==200,"Demon projectile starts burning10seconds");
        }
        h.succeed();
    }
    private static long leapSeed(boolean success) { for(long n=0;n<1000;n++) if((new java.util.Random(n).nextInt(10)==0)==success) return n; throw new AssertionError("Missing leap seed"); }
    @GameTest(template="empty") public static void spiderLeapRejectsExactTwoBlockBoundary(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.WOOD_SPIDER); Cow target=cow(h); target.setPos(e.getX()+2,e.getY(),e.getZ()); e.setOnGround(true); e.getRandom().setSeed(leapSeed(true)); h.assertTrue(!e.trySourceLeap(target),"Source strict distance>2 rejects exactlytwo"); h.succeed(); }
    @GameTest(template="empty") public static void spiderLeapRejectsExactSixBlockBoundary(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.WOOD_SPIDER); Cow target=cow(h); target.setPos(e.getX()+6,e.getY(),e.getZ()); e.setOnGround(true); e.getRandom().setSeed(leapSeed(true)); h.assertTrue(!e.trySourceLeap(target),"Source strict distance<6 rejects exactlysix"); h.succeed(); }
    @GameTest(template="empty") public static void spiderLeapUsesOneInTenDraw(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.WOOD_SPIDER); Cow target=cow(h); target.setPos(e.getX()+3,e.getY(),e.getZ()); e.setOnGround(true); e.getRandom().setSeed(leapSeed(false)); h.assertTrue(!e.trySourceLeap(target),"Independent nextInt10 nonzero fixture cannotleap"); h.succeed(); }
    @GameTest(template="empty") public static void spiderLeapPreservesSourceImpulseAndInertia(GameTestHelper h) { R196Spider e=(R196Spider)mob(h,MobKind.WOOD_SPIDER); Cow target=cow(h); target.setPos(e.getX()+3,e.getY(),e.getZ()); e.setOnGround(true); e.setDeltaMovement(.5,0,.25); e.getRandom().setSeed(leapSeed(true)); h.assertTrue(e.trySourceLeap(target),"Valid source leap succeeds"); near(h,e.getDeltaMovement().x,.5,"Source .4 target impulse+.2*.5 oldx"); near(h,e.getDeltaMovement().y,.4,"Source fixed .4 vertical"); near(h,e.getDeltaMovement().z,.05,"Source .2*.25 transverse inertia"); h.succeed(); }
    @GameTest(template="empty") public static void wolfTamingBoundariesFromSource(GameTestHelper h) { h.assertTrue(R196Wolf.tamingOutcome(.199F,1,200)==-1,"Below .2 always anger"); h.assertTrue(R196Wolf.tamingOutcome(.2F,1,200)==0,".2..4 always neutral"); h.assertTrue(R196Wolf.tamingOutcome(.95F,0,0)==0 && R196Wolf.tamingOutcome(.951F,0,0)==1,"Strict >.95 unconditional tame"); h.assertTrue(R196Wolf.tamingOutcome(.8F,.5F,20)==1,".8+.5*20*.02 reaches1success"); h.succeed(); }
    @GameTest(template="empty") public static void actualDireWolfTamingChangesHealthAndOwner(GameTestHelper h) { R196Wolf e=(R196Wolf)mob(h,MobKind.DIRE_WOLF); Player p=player(h); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BONE,2)); e.getRandom().setSeed(firstSeed(f->f>.95F)); e.mobInteract(p,InteractionHand.MAIN_HAND); h.assertTrue(e.isTame() && p.getUUID().equals(e.getOwnerUUID()) && e.isOrderedToSit(),"Actual native interaction changes tame owner and sitting state"); near(h,e.getMaxHealth(),24,"Source tame24"); near(h,e.getHealth(),24,"Taming heals to source capacity"); near(h,e.getAttributeValue(Attributes.ATTACK_DAMAGE),5,"Source tame damage remains5"); h.assertTrue(p.getMainHandItem().getCount()==1,"One bone consumed"); h.succeed(); }
    @GameTest(template="empty") public static void hellhoundRefusesFoodAndBones(GameTestHelper h) { R196Wolf e=(R196Wolf)mob(h,MobKind.HELLHOUND); Player p=player(h); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BONE,2)); e.mobInteract(p,InteractionHand.MAIN_HAND); h.assertTrue(!e.isTame() && p.getMainHandItem().getCount()==2 && !e.isFood(new ItemStack(Items.BEEF)),"Source untamable and unfeedable hound"); h.assertTrue(!e.biologicallyAlive() && e.healsWithTime(),"Source biological false but ordinary non-undead natural healing true"); h.succeed(); }
    @GameTest(template="empty") public static void hellhoundCannotBurnInLava(GameTestHelper h) { R196Wolf e=(R196Wolf)mob(h,MobKind.HELLHOUND); float health=e.getHealth(); e.lavaHurt(); h.assertTrue(e.getHealth()==health && !e.isOnFire(),"Source immunity also prevents burn side state"); h.succeed(); }
    @GameTest(template="empty") public static void infernalRetainsOrdinaryBodyAndNaturalDefense(GameTestHelper h) { var e=mob(h,MobKind.INFERNAL_CREEPER); near(h,e.getBbWidth(),.6,"Source static scale1"); near(h,e.getBbHeight(),1.8,"Explosion doubling never doubles body"); near(h,((R196Mob)e).naturalDefense(e.damageSources().generic()),2,"Source natural defense+2"); h.succeed(); }
    private static void explode(Creeper e) throws Exception { Method method=Creeper.class.getDeclaredMethod("explodeCreeper"); method.setAccessible(true); method.invoke(e); }
    private static final TicketType<Integer> BLAST_TICKET=TicketType.<Integer>create("mite_blast_test",Comparator.naturalOrder(),200);
    @FunctionalInterface private interface BlastCheck { void run(Creeper creeper,Cow target) throws Exception; }
    private static void blastTarget(GameTestHelper h,double distance,int arena,BlastCheck check) {
        // A FULL block chunk alone does not make its persistent entity sections accessible.
        // Wait for real ENTITY_TICKING tickets before adding either fixture at its final position.
        var level=h.getLevel(); BlockPos anchor=h.absolutePos(new BlockPos(2,0,2));
        Creeper e=(Creeper)MiteMobs.type(MobKind.INFERNAL_CREEPER).create(level);
        e.moveTo(anchor.getX()+.5,120+arena*32,anchor.getZ()+.5,0,0);e.setNoAi(true);e.setNoGravity(true);
        Cow c=EntityType.COW.create(level);c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);c.setHealth(100);
        c.moveTo(e.getX()+distance,e.getY(),e.getZ(),0,0);c.setNoAi(true);c.setNoGravity(true);
        // Separate heights prevent concurrently waiting blast tests from damaging each other's cows.
        List<ChunkPos> chunks=java.util.stream.Stream.of(new ChunkPos(e.blockPosition()),new ChunkPos(c.blockPosition())).distinct().toList();
        int ticketKey=e.getId();
        for(ChunkPos chunk:chunks) level.getChunkSource().addRegionTicket(BLAST_TICKET,chunk,2,ticketKey);
        Runnable cleanup=()->{
            e.discard();c.discard();
            for(ChunkPos chunk:chunks) level.getChunkSource().removeRegionTicket(BLAST_TICKET,chunk,2,ticketKey);
        };
        // A sequence timeout must not leave the out-of-template entities or tickets behind.
        h.runAfterDelay(199,cleanup);
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(level.isPositionEntityTicking(e.blockPosition()) && level.isPositionEntityTicking(c.blockPosition()),"Both blast fixture chunks reach real entity-ticking status");
        }).thenExecute(()->{
            try {
                h.assertTrue(level.addFreshEntity(e),"The source creeper enters the native entity lifecycle");
                h.assertTrue(level.addFreshEntity(c),"The target cow enters the native entity lifecycle");
            } catch(RuntimeException | Error failure) { cleanup.run();throw failure; }
        }).thenWaitUntil(()->{
            h.assertTrue(level.getEntities(e,e.getBoundingBox().inflate(10)).contains(c),"Blast fixture is indexed in the actual living-entity query before exploding");
        }).thenExecute(()->{
            try {
                near(h,c.getHealth(),100,"Waiting for native indexing must not pre-damage the blast target");
                h.assertTrue(net.minecraft.world.level.Explosion.getSeenPercent(e.position().add(0,e.getBbHeight()*.25,0),c)>0F,"Blast fixture has positive real exposure");
                check.run(e,c);
            } catch(Exception failure) { throw new IllegalStateException(failure); }
            finally { cleanup.run(); }
        }).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=200) public static void infernalRealExplosionHitsFourBlocks(GameTestHelper h) { blastTarget(h,4,0,(e,c)->{ explode(e);h.assertTrue(c.getHealth()<100,"Source living radius8.8 is not clipped to native block radius2.86"); }); }
    @GameTest(template="empty",timeoutTicks=200) public static void infernalRealExplosionHitsEightBlocks(GameTestHelper h) { blastTarget(h,8,1,(e,c)->{ explode(e);h.assertTrue(c.getHealth()<100,"Real living query and normalized distance both use source radius"); }); }
    @GameTest(template="empty",timeoutTicks=200) public static void infernalRealExplosionDamageIsLinear(GameTestHelper h) { blastTarget(h,4,2,(e,c)->{ double distance=Math.sqrt(16+Math.pow(e.getBbHeight()*.25,2));double expected=(8.8-distance)*4*.6666667;explode(e);near(h,100-c.getHealth(),expected,"Independent abr formula, actual unarmored health loss"); }); }
    @GameTest(template="empty",timeoutTicks=200) public static void infernalRealExplosionDoesNotHitOutsideLivingRadius(GameTestHelper h) { blastTarget(h,9,3,(e,c)->{ explode(e);near(h,c.getHealth(),100,"Outside source living radius8.8"); }); }
    @GameTest(template="empty") public static void sourceDiggerHasElevenStagesAfterInitialDelay(GameTestHelper h) {
        R196Elemental e=(R196Elemental)mob(h,MobKind.EARTH_ELEMENTAL); BlockPos pos=e.blockPosition().east(); h.getLevel().setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState());
        CompoundTag saved=new CompoundTag(); saved.putLong("position",pos.asLong()); saved.putInt("progress",-1); saved.putInt("cooldown",40); e.getPersistentData().put("mite_digging_031",saved);
        R196DiggerGoal goal=new R196DiggerGoal(e); h.assertTrue(goal.canDestroy(pos,true),"Source nearfoot reachable dirt fixture"); h.assertTrue(goal.stageDelay(pos)==18,"Source(int)(300*.5)/8");
        e.setTarget(cow(h)); h.assertTrue(goal.canUse(),"Actual selector restores saved digging state before activation"); goal.start();
        for (int i=0;i<39;i++) goal.tick(); h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.DIRT),"Initial40ticks have not completed"); goal.tick();
        h.assertTrue(e.getPersistentData().getCompound("mite_digging_031").getInt("progress")==0,"First stage moves-1to0");
        for (int i=0;i<179;i++) goal.tick(); h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.DIRT),"Ten further stages require180ticks"); goal.tick();
        h.assertTrue(h.getLevel().isEmptyBlock(pos),"Eleventh stage removes block through native destroy path"); h.succeed();
    }
    @GameTest(template="empty") public static void clayDiggerCoolingDivisorsAreSourceValues(GameTestHelper h) { R196Elemental clay=(R196Elemental)mob(h,MobKind.CLAY_GOLEM); BlockPos pos=clay.blockPosition().east(); h.getLevel().setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState()); R196DiggerGoal goal=new R196DiggerGoal(clay); h.assertTrue(goal.stageDelay(pos)==37,"Source int150/4 normalclay"); clay.convertToMagma(); h.assertTrue(goal.stageDelay(pos)==25,"Source150/6 firedclay"); h.succeed(); }
    @GameTest(template="empty") public static void diggerResumesStateReadAfterGoalConstruction(GameTestHelper h) { R196Elemental e=(R196Elemental)mob(h,MobKind.EARTH_ELEMENTAL); R196DiggerGoal goal=new R196DiggerGoal(e); BlockPos pos=e.blockPosition().east(); h.getLevel().setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState()); CompoundTag stored=new CompoundTag(); stored.putLong("position",pos.asLong()); stored.putInt("progress",9); stored.putInt("cooldown",1); e.getPersistentData().put("mite_digging_031",stored); e.setTarget(cow(h)); h.assertTrue(goal.canUse(),"Native lifecycle creates goals before loading ForgeData, delayedread restores saved target"); goal.tick(); h.assertTrue(h.getLevel().isEmptyBlock(pos),"Saved stage9 and remaining1tick finish rather than restarting40+11stages"); h.succeed(); }
    @GameTest(template="empty") public static void npcFoodTakesOneAndHealsHalfMaximum(GameTestHelper h) { Cow cow=EntityType.COW.create(h.getLevel()); cow.setHealth(1); ItemEntity food=new ItemEntity(h.getLevel(),0,0,0,new ItemStack(Items.WHEAT,2)); h.assertTrue(R196NpcNeeds.pickUp(cow,food),"Source cow eats dropped wheat"); near(h,cow.getHealth(),1+cow.getMaxHealth()*.5,"Independent source heal50%max"); h.assertTrue(food.getItem().getCount()==1 && food.getAge()==-18000 && cow.getPersistentData().getInt(R196NpcNeeds.COOLDOWN)==400,"One eaten, remainder lifetime extended, pickup cooldown400"); h.assertTrue(!R196NpcNeeds.pickUp(cow,food),"Cooldown prevents duplicate second uptake"); h.succeed(); }
    @GameTest(template="empty") public static void sourceSkeletonBoneRepairsHalfMaximum(GameTestHelper h) { var skeleton=EntityType.SKELETON.create(h.getLevel()); skeleton.setHealth(1); ItemEntity bone=new ItemEntity(h.getLevel(),0,0,0,new ItemStack(Items.BONE)); h.assertTrue(R196NpcNeeds.pickUp(skeleton,bone) && !bone.isAlive(),"Source skeletal repair takes and destroys one bone entity"); near(h,skeleton.getHealth(),1+skeleton.getMaxHealth()*.5,"Repair works despite undead biological flag"); h.succeed(); }
    @GameTest(template="empty") public static void nativeCowNaturalRecoveryUsesTenPercent(GameTestHelper h) { Cow cow=EntityType.COW.create(h.getLevel()); cow.setNoAi(true); cow.setHealth(1); cow.tickCount=Math.floorMod(-cow.getId()*47,1000); cow.aiStep(); near(h,cow.getHealth(),1+cow.getMaxHealth()*.1,"Actual native aiStep source10% every1000offsetticks"); h.succeed(); }
    @GameTest(template="empty") public static void nonBiologicalHoundStillRecoversNaturally(GameTestHelper h) { R196Wolf hound=(R196Wolf)mob(h,MobKind.HELLHOUND); hound.setHealth(1); hound.tickCount=Math.floorMod(-hound.getId()*47,1000); R196NpcNeeds.tick(new EntityTickEvent.Post(hound)); near(h,hound.getHealth(),3,"Non-undead20HP body recovers10%, despite non-biological flag"); h.succeed(); }
    @GameTest(template="empty") public static void sourceFishIsMeatForGhoulAndWolves(GameTestHelper h) { ItemStack fish=new ItemStack(Items.COD),large=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:raw_large_fish"))); h.assertTrue(R196NpcNeeds.food(mob(h,MobKind.GHOUL),fish) && R196NpcNeeds.food(mob(h,MobKind.GHOUL),large),"Source ItemMeat includes fish absent from modern MEATtag"); var wolf=EntityType.WOLF.create(h.getLevel()); h.assertTrue(wolf.isFood(fish) && ((R196Wolf)mob(h,MobKind.DIRE_WOLF)).isFood(large),"Source wolf ItemMeat food filter includes both fishsizes"); h.assertTrue(!R196NpcNeeds.food(cow(h),fish) && !R196NpcNeeds.food(mob(h,MobKind.WIGHT),fish) && !R196NpcNeeds.food(mob(h,MobKind.HELLHOUND),large),"Distinct source cow/wight/hellhound rules never inherit ghoulmeatdiet"); h.succeed(); }
    @GameTest(template="empty") public static void undeadZombieConsumesFishWithoutHealing(GameTestHelper h) { var zombie=EntityType.ZOMBIE.create(h.getLevel()); zombie.setHealth(1); ItemEntity fish=new ItemEntity(h.getLevel(),0,0,0,new ItemStack(Items.COD)); h.assertTrue(R196NpcNeeds.pickUp(zombie,fish) && !fish.isAlive(),"Source droppedfish consumed by actual undead zombie"); near(h,zombie.getHealth(),1,"Undead food refreshes despawn and cooldown but neverheals"); h.succeed(); }
}
