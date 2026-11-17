package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceBreakPortalGoal;
import io.github.ningmeng123.mite.entity.ZombieRoutePlanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native routes, actual mining timers and cancellable block destruction. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ZombieRouteGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    @GameTest(template="empty")
    public static void zombieWalksAroundOpaqueWallWhenWalkingIsQuicker(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.wall(1,4,0,2,Blocks.DIRT.defaultBlockState());
                var plan=f.digger.pursuitPlan();
                h.assertTrue(plan.completeWalkingRoute()&&!plan.choosesBreaking(),"Real complete detour is faster than staged dirt excavation");
                h.assertTrue(plan.breakingTicks()>plan.walkingTicks(),"Both wall-clearing and actual path have finite comparable travel estimates");
                h.assertTrue(detours(plan.path(),f.origin.getZ()),"Selected native path actually goes around the wall");
                h.assertFalse(f.mob.getSensing().hasLineOfSight(f.player),"Opaque wall removes line of sight");
                h.assertFalse(f.digger.canUse(),"Priority-one digging cannot preempt the faster route");
                var melee=new SourceMeleeGoal(f.mob,Player.class,1D,false);
                h.assertTrue(melee.canUse(),"Player pursuit accepts a path around the obstacle");melee.start();
                f.mob.getNavigation().stop();
                h.assertTrue(melee.canContinueToUse(),"Losing line of sight and finishing a partial route does not abandon the player");
                melee.tick();
                h.assertTrue(f.mob.getNavigation().getPath()!=null&&detours(f.mob.getNavigation().getPath(),f.origin.getZ()),
                        "Unseen target still receives a real detour on the next pursuit replan");
                melee.stop();
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void ordinaryUnarmedZombieComparesDirtEvenWithoutSmartFlag(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.wall(1,20,0,3,Blocks.DIRT.defaultBlockState());
                f.mob.getPersistentData().putBoolean(R196MonsterAi.SMART,false);
                var plan=f.digger.pursuitPlan();
                h.assertTrue(!plan.completeWalkingRoute()&&plan.choosesBreaking(),"An unreachable partial native path is not a complete walking alternative");
                h.assertTrue(f.digger.canUse(),"Ordinary empty-hand zombie can select the source-permitted dirt opening");
                f.digger.start();f.digger.tick();
                h.assertTrue(!h.getLevel().isEmptyBlock(plan.firstBlock())&&f.digger.progress()==-1,
                        "Path planning preserves the forty-tick opening delay instead of instantly breaking dirt");
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void nearlyFinishedOpeningBeatsCompleteLongerDetour(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.wall(1,10,1,2,Blocks.DIRT.defaultBlockState());
                BlockPos head=f.origin.east().above();
                f.resume(head,9,1);
                h.assertTrue(f.digger.canUse(),"The saved final mining stage resumes through the actual goal entrypoint");
                var plan=f.digger.pursuitPlan();
                h.assertTrue(plan.completeWalkingRoute()&&plan.choosesBreaking()&&plan.breakingTicks()<plan.walkingTicks(),
                        "A one-tick remaining opening beats the longer physically reachable walking route");
                f.digger.start();f.digger.tick();
                h.assertTrue(h.getLevel().isEmptyBlock(head),"Selected quicker opening completes the actual staged break");
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void bedrockAndWrongToolsCannotBecomeExcavationShortcuts(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.wall(1,4,0,2,Blocks.STONE.defaultBlockState());
                f.mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
                var plan=f.digger.pursuitPlan();
                h.assertTrue(plan.completeWalkingRoute()&&!plan.choosesBreaking()&&!Double.isFinite(plan.breakingTicks()),
                        "An ineffective axe cannot make stone a planned excavation route");
                f.mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
                h.assertTrue(Double.isFinite(f.digger.pursuitPlan().breakingTicks()),"Changing to a real pickaxe immediately updates route eligibility");
                f.wall(1,4,0,2,Blocks.BEDROCK.defaultBlockState());
                // Recreate the planner because the fixture intentionally changes terrain within one server tick.
                plan=new R196DiggerGoal(f.mob).pursuitPlan();
                h.assertTrue(!plan.choosesBreaking()&&!Double.isFinite(plan.breakingTicks()),"Bedrock remains immutable with a correct mining tool");
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void mobGriefingAndCancelledDestroyEventProtectActualOpening(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.wall(1,10,1,2,Blocks.DIRT.defaultBlockState());
                BlockPos head=f.origin.east().above();f.resume(head,9,1);
                h.assertTrue(f.digger.canUse(),"Ready-to-finish actual excavation fixture");
                boolean grief=h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
                try {
                    h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,h.getLevel().getServer());
                    h.assertFalse(f.digger.canDestroy(head,true),"Mob griefing disables actual excavation");
                    h.assertFalse(f.digger.pursuitPlan().choosesBreaking(),"A cached shortcut is invalidated when griefing changes");
                }finally {h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(grief,h.getLevel().getServer());}
                int[] calls={0};Consumer<LivingDestroyBlockEvent> cancel=event->{
                    if(event.getEntity()==f.mob&&event.getPos().equals(head)) {calls[0]++;event.setCanceled(true);}
                };
                NeoForge.EVENT_BUS.addListener(LivingDestroyBlockEvent.class,cancel);
                try {f.digger.start();f.digger.tick();}finally {NeoForge.EVENT_BUS.unregister(cancel);}
                h.assertTrue(calls[0]==1&&h.getLevel().getBlockState(head).is(Blocks.DIRT),"Cancelled real break leaves the wall intact");
                var next=f.digger.pursuitPlan();
                h.assertTrue(next.completeWalkingRoute()&&!next.choosesBreaking()&&!f.digger.canUse(),
                        "A real protection rejection immediately invalidates the shortcut and selects the available detour");
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void movingPlayerInvalidatesCachedDetour(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.wall(1,4,0,2,Blocks.DIRT.defaultBlockState());
                Path first=f.digger.pursuitPlan().path();
                f.player.moveTo(f.origin.getX()+.5,f.origin.getY(),f.origin.getZ()+9.5,0,0);
                var next=f.digger.pursuitPlan();
                h.assertTrue(next.path()!=null&&first!=next.path()&&next.path().getTarget().equals(f.player.blockPosition()),
                        "A moved player triggers a new route before the periodic cache expires");
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void fasterGatePoundingHoldsPositionAndUsesActualToolProgress(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.wall(1,10,0,0,Blocks.OAK_FENCE_GATE.defaultBlockState());
                f.mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
                var plan=f.digger.pursuitPlan();
                h.assertTrue(plan.completeWalkingRoute()&&plan.portal()&&plan.breakingTicks()<plan.walkingTicks(),
                        "The tool's real gate-pounding speed beats the longer complete detour");
                h.assertFalse(f.digger.canUse(),"Slower generic staged mining yields to the faster dedicated gate task");
                var portal=SourceBreakPortalGoal.first(f.mob);
                h.assertTrue(portal!=null&&portal.canUse(),"A selected reachable gate starts without first colliding against it");portal.start();
                var melee=new SourceMeleeGoal(f.mob,Player.class,1D,false);
                h.assertTrue(melee.canUse(),"The pursuit task keeps ownership of movement while pounding");melee.start();
                h.assertTrue(plan.path().getEndNode().asBlockPos().equals(f.origin),"A faster adjacent opening holds position instead of starting the detour");
                for(int i=0;i<60;i++)portal.tick();
                h.assertTrue(h.getLevel().isEmptyBlock(f.origin.east()),"Iron axe advances eight of 480 gate progress each real tick");
                portal.stop();melee.stop();
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void cancellingOtherDoorHalfProtectsWholeExcavation(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.wall(1,10,0,2,Blocks.BEDROCK.defaultBlockState());
                BlockPos lower=f.origin.east(),upper=lower.above();
                BlockState door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST);
                h.getLevel().setBlock(lower,door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);
                h.getLevel().setBlock(upper,door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
                f.resume(lower,9,1);h.assertTrue(f.digger.canUse(),"Completed door excavation is the quickest ready opening");
                int[] calls={0};Consumer<LivingDestroyBlockEvent> cancel=event->{
                    if(event.getEntity()==f.mob&&event.getPos().equals(upper)) {calls[0]++;event.setCanceled(true);}
                };
                NeoForge.EVENT_BUS.addListener(LivingDestroyBlockEvent.class,cancel);
                try {f.digger.start();f.digger.tick();}finally {NeoForge.EVENT_BUS.unregister(cancel);}
                h.assertTrue(calls[0]==1&&h.getLevel().getBlockState(lower).is(Blocks.OAK_DOOR)
                        &&h.getLevel().getBlockState(upper).is(Blocks.OAK_DOOR),"Protecting either door half prevents both halves from disappearing");
                h.assertTrue(f.digger.destructionRejected(lower)&&f.digger.destructionRejected(upper),
                        "Rejecting one half prevents retrying the same protected door through its partner");
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void rejectedFastGateSwitchesToRealWalkingRoute(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.wall(1,10,0,0,Blocks.OAK_FENCE_GATE.defaultBlockState());
                f.mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
                h.assertTrue(f.digger.pursuitPlan().portal(),"The unprotected gate really is the faster original route");
                var portal=SourceBreakPortalGoal.first(f.mob);h.assertTrue(portal.canUse(),"Actual fast gate task starts");portal.start();
                int[] calls={0};Consumer<LivingDestroyBlockEvent> cancel=event->{
                    if(event.getEntity()==f.mob&&event.getPos().equals(f.origin.east())) {calls[0]++;event.setCanceled(true);}
                };
                NeoForge.EVENT_BUS.addListener(LivingDestroyBlockEvent.class,cancel);
                try {for(int tick=0;tick<60;tick++)portal.tick();}finally {NeoForge.EVENT_BUS.unregister(cancel);}
                var next=f.digger.pursuitPlan();
                h.assertTrue(calls[0]==1&&h.getLevel().getBlockState(f.origin.east()).is(Blocks.OAK_FENCE_GATE),
                        "The actual protection hook preserves the gate");
                h.assertTrue(next.completeWalkingRoute()&&!next.choosesBreaking()&&!portal.canUse()
                        &&!f.digger.canDestroyMaterial(f.origin.east()),"Both generic and dedicated breaking avoid the denied position and use the real complete detour");
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void fractionalPlayerCornerUsesExactPathUntilActuallyInMeleeReach(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                f.player.moveTo(f.origin.getX()+1.99D,f.origin.getY(),f.origin.getZ()+.99D,0,0);
                h.assertFalse(SourceMeleeGoal.canStrike(f.mob,f.player),"A 1.568-block diagonal is outside the real unarmed reach");
                var plan=f.digger.pursuitPlan();
                h.assertTrue(plan.completeWalkingRoute()&&!plan.choosesBreaking(),"Exact path fallback finds a real attack position");
                var endpoint=plan.path().getEntityPosAtNode(f.mob,plan.path().getNodeCount()-1);
                h.assertTrue(endpoint.distanceToSqr(f.mob.position())>.25D&&SourceMeleeGoal.canStrikeFrom(f.mob,f.player,endpoint),
                        "Accuracy-one cannot reuse the current unreachable node; its replacement endpoint passes the actual attack predicate");
                f.mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
                h.assertTrue(SourceMeleeGoal.canStrike(f.mob,f.player),"Source sword reach legitimately covers the same fractional target");
                var sword=f.digger.pursuitPlan();
                h.assertTrue(sword.completeWalkingRoute()&&sword.walkingTicks()<plan.walkingTicks(),
                        "The planner uses the same weapon reach and can stop earlier when the real sword can hit");
            }h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void narrowCorridorFractionalPlayerCannotTrapZombieAtAccuracyOneEndpoint(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try(Fixture f=new Fixture(h)) {
                for(int x=-1;x<=12;x++)for(int y=0;y<=2;y++)for(int z:new int[]{-1,1})
                    h.getLevel().setBlock(f.origin.offset(x,y,z),Blocks.BEDROCK.defaultBlockState(),2);
                f.player.moveTo(f.origin.getX()+8.999D,f.origin.getY(),f.origin.getZ()+.68D,0,0);
                var tooFar=new net.minecraft.world.phys.Vec3(f.origin.getX()+7.5D,f.origin.getY(),f.origin.getZ()+.5D);
                h.assertFalse(SourceMeleeGoal.canStrikeFrom(f.mob,f.player,tooFar),"The first endpoint is only slightly over 1.5 blocks away but still cannot hit");
                var plan=f.digger.pursuitPlan();
                h.assertTrue(plan.completeWalkingRoute()&&SourceMeleeGoal.canStrikeFrom(f.mob,f.player,
                        plan.path().getEntityPosAtNode(f.mob,plan.path().getNodeCount()-1)),"One-block-wide corridor uses a reachable exact endpoint that can really attack");
            }h.succeed();
        });
    }

    private static boolean detours(Path path,int z) {
        if(path==null)return false;
        for(int i=0;i<path.getNodeCount();i++) if(Math.abs(path.getNode(i).z-z)>4)return true;
        return false;
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;final BlockPos origin;final Zombie mob;final Player player;
        R196DiggerGoal digger;
        Fixture(GameTestHelper h) {
            helper=h;origin=new BlockPos(180_000+IDS.getAndIncrement()*128,h.absolutePos(BlockPos.ZERO).getY()+8,180_000);
            for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++) {
                BlockPos floor=origin.offset(x,-1,z);h.getLevel().setBlock(floor,Blocks.BEDROCK.defaultBlockState(),2);
                for(int y=0;y<4;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            mob=EntityType.ZOMBIE.create(h.getLevel());mob.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0);
            mob.setNoAi(true);mob.setNoGravity(true);mob.setOnGround(true);h.getLevel().addFreshEntity(mob);
            player=h.makeMockPlayer(GameType.SURVIVAL);player.moveTo(origin.getX()+8.5,origin.getY(),origin.getZ()+.5,0,0);
            mob.setTarget(player);digger=R196DiggerGoal.first(mob);
            if(digger==null) {digger=new R196DiggerGoal(mob);mob.goalSelector.addGoal(1,digger);}
        }
        void wall(int x,int halfWidth,int minY,int maxY,BlockState state) {
            for(int z=-halfWidth;z<=halfWidth;z++)for(int y=minY;y<=maxY;y++)
                helper.getLevel().setBlock(origin.offset(x,y,z),state,2);
        }
        void resume(BlockPos pos,int progress,int cooldown) {
            digger.stop();mob.goalSelector.removeGoal(digger);CompoundTag saved=new CompoundTag();saved.putLong("position",pos.asLong());
            saved.putInt("progress",progress);saved.putInt("cooldown",cooldown);mob.getPersistentData().put("mite_digging_031",saved);
            digger=new R196DiggerGoal(mob);mob.goalSelector.addGoal(1,digger);
        }
        @Override public void close() {digger.stop();mob.discard();player.discard();}
    }
}
