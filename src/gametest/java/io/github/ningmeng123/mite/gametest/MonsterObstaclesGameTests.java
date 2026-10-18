package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.*;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteTools;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent pj/pm/AnimalWatcher literals exercised against real world blocks and actual goals. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class MonsterObstaclesGameTests {
    private static Zombie zombie(GameTestHelper h) {
        Zombie mob=EntityType.ZOMBIE.create(h.getLevel());
        BlockPos p=h.absolutePos(new BlockPos(1,4,1));mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        mob.setNoAi(true);mob.setNoGravity(true);h.getLevel().addFreshEntity(mob);
        mob.getPersistentData().putBoolean(R196MonsterAi.SMART,true);
        var prey=h.makeMockPlayer(GameType.SURVIVAL);prey.moveTo(mob.getX()+3,mob.getY(),mob.getZ(),0,0);mob.setTarget(prey);
        return mob;
    }
    private static R196DiggerGoal digger(PathfinderMob mob) {
        R196DiggerGoal goal=R196DiggerGoal.first(mob);
        if(goal==null) { goal=new R196DiggerGoal(mob);mob.goalSelector.addGoal(1,goal); }
        return goal;
    }
    private static void put(GameTestHelper h,BlockPos pos,BlockState state) {
        if(h.getLevel().getBlockState(pos.below()).isAir()) h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(pos,state,2);
        if(state.getBlock() instanceof DoorBlock)
            h.getLevel().setBlock(pos.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
    }
    private static SourceBreakPortalGoal portal(GameTestHelper h,Zombie mob,BlockState state) {
        // Give the target real support and make this portal the only way through the corridor.
        BlockPos origin=mob.blockPosition();
        for(int x=-1;x<=4;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=2;y++) {
            boolean boundary=y==-1||y==2||z!=0||x==-1||x==4;
            h.getLevel().setBlock(origin.offset(x,y,z),(boundary?Blocks.BEDROCK:Blocks.AIR).defaultBlockState(),2);
        }
        mob.setOnGround(true);
        if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state=state.setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST);
        BlockPos p=mob.blockPosition().east();put(h,p,state);
        var navigation=(net.minecraft.world.entity.ai.navigation.GroundPathNavigation)mob.getNavigation();
        navigation.setCanOpenDoors(true);navigation.setCanPassDoors(true);
        navigation.moveTo(new Path(List.of(new Node(p.getX(),p.getY(),p.getZ()),new Node(p.getX()+1,p.getY(),p.getZ())),
                p.east(),true),1);
        mob.horizontalCollision=true;
        return new SourceBreakPortalGoal(mob);
    }
    private static void ordinaryTime(GameTestHelper h,Runnable action) {
        long old=h.getLevel().getDayTime();try { h.getLevel().setDayTime(18000);action.run(); }
        finally { h.getLevel().setDayTime(old); }
    }
    private static void difficulty(GameTestHelper h,Difficulty value,Runnable action) {
        // Legacy low-difficulty branches need a direct fixture; the actual server setter is fixed to Extreme.
        var data=h.getLevel().getServer().getWorldData();
        Difficulty old=h.getLevel().getDifficulty();try { data.setDifficulty(value);action.run(); }
        finally { data.setDifficulty(old); }
    }
    @GameTest(template="empty") public static void portalThresholdsUse1920ForDoorAnd480ForOtherPortals(GameTestHelper h) {
        ordinaryTime(h,()->{var mob=zombie(h);var goal=new SourceBreakPortalGoal(mob);
            h.assertValueEqual(goal.threshold(Blocks.OAK_DOOR.defaultBlockState()),1920,"pj wood door64");
            h.assertValueEqual(goal.threshold(Blocks.OAK_TRAPDOOR.defaultBlockState()),480,"pj trapdoor96");
            h.assertValueEqual(goal.threshold(Blocks.OAK_FENCE_GATE.defaultBlockState()),480,"pj gate107");mob.discard();});
        h.succeed();
    }
    @GameTest(template="empty") public static void realPathAndCollisionSelectDoorAndTrapdoorAndGate(GameTestHelper h) {
        for(var state:List.of(Blocks.OAK_DOOR.defaultBlockState(),Blocks.OAK_TRAPDOOR.defaultBlockState(),Blocks.OAK_FENCE_GATE.defaultBlockState())) {
            var mob=zombie(h);mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            var goal=portal(h,mob,state);h.assertTrue(goal.canUse(),"pm finds a real closed wooden portal through its path");
            goal.stop();h.getLevel().setBlock(mob.blockPosition().east().above(),Blocks.AIR.defaultBlockState(),2);mob.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void portalNeedsCollisionAndAnActivePath(GameTestHelper h) {
        var mob=zombie(h);var goal=portal(h,mob,Blocks.OAK_TRAPDOOR.defaultBlockState());
        // The source collision/path branch is independent of the newer player route planner.
        mob.setTarget(null);
        mob.horizontalCollision=false;h.assertFalse(goal.canUse(),"No horizontal collision cannot start");
        mob.horizontalCollision=true;mob.getNavigation().stop();h.assertFalse(goal.canUse(),"No active path cannot start");mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void openPortalsAndMetalDoorsNeverStartDedicatedPounding(GameTestHelper h) {
        var mob=zombie(h);var goal=portal(h,mob,Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN,true));
        h.assertFalse(goal.canUse(),"Open trapdoor cannot start");goal=portal(h,mob,Blocks.IRON_DOOR.defaultBlockState());
        h.assertFalse(goal.canUse(),"Iron is not a source wooden portal");mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void gateNeedsPlayerButDoorAllowsNonplayerTarget(GameTestHelper h) {
        var mob=zombie(h);var cow=EntityType.COW.create(h.getLevel());mob.setTarget(cow);
        var goal=portal(h,mob,Blocks.OAK_FENCE_GATE.defaultBlockState());h.assertFalse(goal.canUse(),"Gate requires player target");
        goal=portal(h,mob,Blocks.OAK_DOOR.defaultBlockState());h.assertTrue(goal.canUse(),"Door does not require player target");goal.stop();mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void dedicatedPortalGoalDoesNotClaimMoveOrLookMutex(GameTestHelper h) {
        var mob=zombie(h);h.assertTrue(new SourceBreakPortalGoal(mob).getFlags().isEmpty(),"pm/pj default mutex0");mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void hardDifficultyRemovesBothDoorHalvesWithoutDropOrWear(GameTestHelper h) {
        ordinaryTime(h,()->difficulty(h,Difficulty.HARD,()->{
            var mob=zombie(h);mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            var goal=portal(h,mob,Blocks.OAK_DOOR.defaultBlockState());h.assertTrue(goal.canUse(),"Sword still pounds doors");goal.start();
            h.assertFalse(digger(mob).pursuitPlan().completeWalkingRoute(),"The supported enclosed corridor offers no walking alternative to its closed door");
            BlockPos p=mob.blockPosition().east();
            int[] doorDrops={0};
            java.util.function.Consumer<EntityJoinLevelEvent> capture=event->{
                if(event.getLevel()==h.getLevel()&&event.getEntity() instanceof ItemEntity item&&item.getItem().is(Items.OAK_DOOR))doorDrops[0]++;
            };
            NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,capture);
            try {for(int i=0;i<1920;i++)goal.tick();}finally {NeoForge.EVENT_BUS.unregister(capture);}
            h.assertTrue(h.getLevel().getBlockState(p).isAir()&&h.getLevel().getBlockState(p.above()).isAir(),"Both halves removed");
            h.assertValueEqual(doorDrops[0],0,"Actual door-pounding ticks never spawn a door item");
            h.assertValueEqual(mob.getMainHandItem().getDamageValue(),0,"Door pounding never wears held sword");goal.stop();mob.discard();
        }));h.succeed();
    }
    @GameTest(template="empty") public static void normalDifficultyHoldsAtFullProgressWithoutBreaking(GameTestHelper h) {
        ordinaryTime(h,()->difficulty(h,Difficulty.NORMAL,()->{
            var mob=zombie(h);mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            // Nonplayer prey isolates the source Normal-difficulty pounding branch from player route choices.
            var cow=EntityType.COW.create(h.getLevel());mob.setTarget(cow);
            var goal=portal(h,mob,Blocks.OAK_TRAPDOOR.defaultBlockState());
            h.assertTrue(goal.canUse(),"Normal can pound");goal.start();for(int i=0;i<500;i++)goal.tick();
            h.assertValueEqual(goal.progress(),480,"Progress caps at source threshold");
            h.assertTrue(goal.canContinueToUse()&&!h.getLevel().getBlockState(mob.blockPosition().east()).isAir(),"Normal keeps pounding full bar");goal.stop();mob.discard();cow.discard();
        }));h.succeed();
    }
    @GameTest(template="empty") public static void portalPoundingAndBreakSendDoorSoundsInsteadOfJukeboxPackets(GameTestHelper h) {
        ordinaryTime(h,()->difficulty(h,Difficulty.HARD,()->{
            var mob=zombie(h);mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            var goal=portal(h,mob,Blocks.OAK_DOOR.defaultBlockState());
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mite-door-sounds"),false);
            var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);
            var packets=new ArrayList<ClientboundLevelEventPacket>();
            try {
                h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
                player.setGameMode(GameType.CREATIVE);player.moveTo(mob.position());player.setNoGravity(true);
                channel.pipeline().addLast("door-sound-capture",new ChannelOutboundHandlerAdapter() {
                    @Override public void write(ChannelHandlerContext context,Object message,ChannelPromise promise) throws Exception {
                        if(message instanceof ClientboundLevelEventPacket packet) packets.add(packet);
                        super.write(context,message,promise);
                    }
                });
                h.assertTrue(goal.canUse(),"The actual source door task starts before its packets are captured");goal.start();
                BlockPos door=goal.targetBlock();mob.getRandom().setSeed(0L);goal.tick();channel.runPendingTasks();
                h.assertTrue(packets.stream().anyMatch(p->p.getPos().equals(door)&&p.getType()==1019),"First real pounding tick broadcasts the modern zombie wood-door sound");
                for(int tick=1;tick<1920;tick++)goal.tick();channel.runPendingTasks();
                h.assertTrue(h.getLevel().getBlockState(door).isAir(),"The packet fixture actually completes the door break");
                h.assertTrue(packets.stream().anyMatch(p->p.getPos().equals(door)&&p.getType()==1021),"Real completed break broadcasts the modern door-crash sound");
                h.assertTrue(packets.stream().noneMatch(p->p.getPos().equals(door)&&(p.getType()==1010||p.getType()==1012)),"Neither jukebox playback nor the obsolete door-break number reaches the client");
            } finally {
                goal.stop();mob.discard();
                try { h.getLevel().getServer().getPlayerList().remove(player); } finally {channel.finishAndReleaseAll();}
            }
        }));h.succeed();
    }
    @GameTest(template="empty") public static void sourceAxeStrengthRoundsIntoPortalProgress(GameTestHelper h) {
        ordinaryTime(h,()->{var mob=zombie(h);mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
            var goal=portal(h,mob,Blocks.OAK_TRAPDOOR.defaultBlockState());h.assertTrue(goal.canUse(),"Axe portal fixture");goal.start();goal.tick();
            h.assertValueEqual(goal.progress(),8,"Iron axe source strength8 yields int(8+.5)=8");goal.stop();mob.discard();});h.succeed();
    }
    @GameTest(template="empty") public static void bloodMoonHalvesPortalAndDiggingIntervals(GameTestHelper h) {
        ordinaryTime(h,()->{var mob=zombie(h);var portal=new SourceBreakPortalGoal(mob);var goal=digger(mob);
            BlockPos p=mob.blockPosition().east();put(h,p,Blocks.DIRT.defaultBlockState());
            h.assertValueEqual(goal.stageDelay(p),150,"Dirt source .5*300");
            h.getLevel().setDayTime(31L*24000+18000);
            h.assertValueEqual(portal.threshold(Blocks.OAK_DOOR.defaultBlockState()),960,"Blood moon halves wood door");
            h.assertValueEqual(goal.stageDelay(p),75,"Blood moon halves digging");mob.discard();});h.succeed();
    }
    @GameTest(template="empty") public static void griefRuleBlocksBothActualGoalEntrypoints(GameTestHelper h) {
        boolean old=h.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        var mob=zombie(h);try {
            var portal=portal(h,mob,Blocks.OAK_TRAPDOOR.defaultBlockState());
            h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,h.getLevel().getServer());
            h.assertFalse(portal.canUse(),"Source portal grief rule");
            h.assertFalse(digger(mob).canDestroy(mob.blockPosition().east(),true),"Modern grief adaptation also protects generic digging");
        }finally {h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(old,h.getLevel().getServer());mob.discard();}
        h.succeed();
    }
    @GameTest(template="empty") public static void emptyHandsCannotMineStoneButCanDismantleWoodDoor(GameTestHelper h) {
        ordinaryTime(h,()->{var mob=zombie(h);var goal=digger(mob);BlockPos p=mob.blockPosition().east();
            put(h,p,Blocks.STONE.defaultBlockState());h.assertFalse(goal.canDestroy(p,false),"Source stone material requires tool regardless modern drop flag");
            put(h,p,Blocks.OAK_DOOR.defaultBlockState());h.assertTrue(goal.canDestroy(p,false),"Wood material permits generic digging independently of dedicated hard rule");
            mob.discard();});h.succeed();
    }
    @GameTest(template="empty") public static void correctPickaxeUnlocksStoneAndWrongAxeDoesNot(GameTestHelper h) {
        var mob=zombie(h);var goal=digger(mob);BlockPos p=mob.blockPosition().east();put(h,p,Blocks.STONE.defaultBlockState());
        mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));h.assertFalse(goal.canDestroy(p,false),"Ineffective axe returns source strength0");
        mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));h.assertTrue(goal.canDestroy(p,false),"Source iron pick can mine stone");
        mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void swordClubAndScytheBlockGenericDigging(GameTestHelper h) {
        var mob=zombie(h);var goal=digger(mob);BlockPos p=mob.blockPosition().east();put(h,p,Blocks.DIRT.defaultBlockState());
        for(String id:List.of("minecraft:iron_sword","minecraft:wooden_sword","mite:wooden_cudgel","mite:iron_scythe")) {
            mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(MiteTools.get(id)));h.assertFalse(goal.canDestroy(p,false),"Source blocked family "+id);
        }mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void onlyShovelAllowsDiggingBelowFeet(GameTestHelper h) {
        var mob=zombie(h);var goal=digger(mob);BlockPos p=mob.blockPosition().below();put(h,p,Blocks.DIRT.defaultBlockState());
        h.assertFalse(goal.canDestroy(p,false),"No downward digging tool");
        mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(MiteTools.get("mite:iron_mattock")));h.assertFalse(goal.canDestroy(p,false),"Mattock is not ItemShovel");
        mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SHOVEL));h.assertTrue(goal.canDestroy(p,false),"Shovel permits below feet");
        mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void reachHeightAndBedrockGuardsRemainBounded(GameTestHelper h) {
        var mob=zombie(h);var goal=digger(mob);BlockPos p=mob.blockPosition().east();
        put(h,p.above(2),Blocks.DIRT.defaultBlockState());h.assertFalse(goal.canDestroy(p.above(2),false),"Never above foot+1");
        put(h,p.east(2),Blocks.DIRT.defaultBlockState());h.assertFalse(goal.canDestroy(p.east(2),false),"Source center distance squared3.25");
        put(h,p,Blocks.BEDROCK.defaultBlockState());h.assertFalse(goal.canDestroy(p,false),"Modern immutable safeguard");mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void firstActionWaits40TicksThenNeedsElevenActions(GameTestHelper h) {
        ordinaryTime(h,()->{var mob=zombie(h);var goal=digger(mob);BlockPos p=mob.blockPosition().east();put(h,p,Blocks.DIRT.defaultBlockState());
            h.assertTrue(goal.setBlockToDig(p,true),"Actual chosen dirt");goal.start();
            for(int i=0;i<39;i++)goal.tick();h.assertValueEqual(goal.progress(),-1,"39 ticks no action");
            goal.tick();h.assertValueEqual(goal.progress(),0,"First action is stage0");
            for(int i=0;i<150*9;i++)goal.tick();h.assertValueEqual(goal.progress(),9,"Ten actions still not broken");
            for(int i=0;i<150;i++)goal.tick();h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Eleventh action finally breaks");
            goal.stop();mob.discard();});h.succeed();
    }
    @GameTest(template="empty") public static void switchingTargetsResetsStageButKeepsCooldown(GameTestHelper h) {
        var mob=zombie(h);var goal=digger(mob);BlockPos first=mob.blockPosition().east(),second=first.above();
        put(h,first,Blocks.DIRT.defaultBlockState());put(h,second,Blocks.DIRT.defaultBlockState());
        h.assertTrue(goal.setBlockToDig(first,false),"First candidate");goal.tick();int remaining=goal.cooldown();
        h.assertTrue(goal.setBlockToDig(second,false),"Second candidate");h.assertValueEqual(goal.cooldown(),remaining,"Same active timer survives changing block");
        h.assertValueEqual(goal.progress(),-1,"Changed block restarts crack");goal.stop();mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void existing031SavedStageResumesAndCancelRemovesState(GameTestHelper h) {
        var mob=zombie(h);BlockPos p=mob.blockPosition().east();put(h,p,Blocks.DIRT.defaultBlockState());
        var saved=new CompoundTag();saved.putLong("position",p.asLong());saved.putInt("progress",5);saved.putInt("cooldown",1);
        mob.getPersistentData().put("mite_digging_031",saved);var goal=new R196DiggerGoal(mob);
        h.assertTrue(goal.canUse(),"031 NBT resumes after constructor");goal.tick();
        h.assertValueEqual(goal.progress(),6,"Saved sixth stage advances without reset");h.assertValueEqual(goal.pauseTicks(),0,"No transient pause restored");
        goal.stop();h.assertFalse(mob.getPersistentData().contains("mite_digging_031"),"Cancel clears saved active state");mob.discard();h.succeed();
    }
    @GameTest(template="empty") public static void realActivePeerOwnsBlockButStaleNbtDoesNot(GameTestHelper h) {
        var first=zombie(h);var second=zombie(h);var a=digger(first);var b=digger(second);BlockPos p=first.blockPosition().east();put(h,p,Blocks.DIRT.defaultBlockState());
        h.assertTrue(a.setBlockToDig(p,false),"First active miner");h.assertFalse(b.canDestroy(p,false),"Other active watcher owns same block");a.stop();
        var stale=new CompoundTag();stale.putLong("position",p.asLong());first.getPersistentData().put("mite_digging_031",stale);
        h.assertTrue(b.canDestroy(p,false),"Stale saved position alone does not claim block");first.discard();second.discard();h.succeed();
    }
    @GameTest(template="empty") public static void ghoulStageIntervalHasExtraFinalDivisionByTwo(GameTestHelper h) {
        ordinaryTime(h,()->{var mob=(PathfinderMob)MiteMobs.type(MobKind.GHOUL).create(h.getLevel());BlockPos origin=h.absolutePos(new BlockPos(1,4,1));
            mob.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0);var p=mob.blockPosition().east();put(h,p,Blocks.DIRT.defaultBlockState());
            h.assertValueEqual(new R196DiggerGoal(mob).stageDelay(p),75,"Ghoul parent150 then /2");mob.discard();});h.succeed();
    }
    @GameTest(template="empty") public static void elementalPortalDivisorsDistinguishClayHardenedAndStone(GameTestHelper h) {
        ordinaryTime(h,()->{var clay=(R196Elemental)MiteMobs.type(MobKind.CLAY_GOLEM).create(h.getLevel());
            var goal=new SourceBreakPortalGoal(clay);h.assertValueEqual(goal.threshold(Blocks.OAK_DOOR.defaultBlockState()),480,"Normal clay1920/4");
            clay.setMaterial(Blocks.TERRACOTTA,true);h.assertValueEqual(goal.threshold(Blocks.OAK_DOOR.defaultBlockState()),320,"Hardened1920/6");
            var stone=(R196Elemental)MiteMobs.type(MobKind.EARTH_ELEMENTAL).create(h.getLevel());
            h.assertValueEqual(new SourceBreakPortalGoal(stone).threshold(Blocks.OAK_DOOR.defaultBlockState()),240,"Other elemental1920/8");clay.discard();stone.discard();});h.succeed();
    }
    @GameTest(template="empty") public static void genericStoneMiningDropsCobbleAndWearsTheRealTool(GameTestHelper h) {
        ordinaryTime(h,()->{var mob=zombie(h);mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
            var goal=digger(mob);BlockPos p=mob.blockPosition().east();put(h,p,Blocks.STONE.defaultBlockState());
            h.assertTrue(goal.setBlockToDig(p,true),"Real pickaxe source stone target");
            h.assertValueEqual(goal.stageDelay(p),144,"int(300*2.4)/(1+8*.5)=144");
            for(int i=0;i<40+10*144;i++)goal.tick();
            h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Generic final action really removes block");
            h.assertTrue(mob.getMainHandItem().getDamageValue()>0,"Generic mining invokes real source durability callback");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,mob.getBoundingBox().inflate(4)).stream()
                    .anyMatch(item->item.getItem().is(Items.COBBLESTONE)),"Generic mining emits harvested block loot");
            goal.stop();mob.discard();});h.succeed();
    }
    @GameTest(template="empty") public static void elementalHarvestShortcutPrecedesAnotherMinersClaim(GameTestHelper h) {
        var owner=zombie(h);owner.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        var goal=digger(owner);BlockPos p=owner.blockPosition().east();put(h,p,Blocks.STONE.defaultBlockState());
        h.assertTrue(goal.setBlockToDig(p,false),"Other watcher owns stone");
        var earth=(R196Elemental)MiteMobs.type(MobKind.EARTH_ELEMENTAL).create(h.getLevel());
        earth.moveTo(owner.getX(),owner.getY(),owner.getZ(),0,0);
        h.assertTrue(new R196DiggerGoal(earth).canDestroy(p,false),"Earth source harvest shortcut returns before peer-claim test");
        goal.stop();owner.discard();earth.discard();h.succeed();
    }
    @GameTest(template="empty") public static void sourceConstructorLevelOneBlocksNeedToolUntilBloodMoon(GameTestHelper h) {
        ordinaryTime(h,()->{var mob=zombie(h);var goal=digger(mob);BlockPos p=mob.blockPosition().east();
            for(var state:List.of(Blocks.OAK_LOG.defaultBlockState(),Blocks.GLASS.defaultBlockState(),Blocks.ICE.defaultBlockState(),Blocks.SANDSTONE.defaultBlockState())) {
                put(h,p,state);h.assertValueEqual(R196DiggerGoal.minimumHarvestLevel(state),1,"Actual source constructor tier1");
                h.assertFalse(goal.canDestroy(p,false),"Normal empty hand cannot mine tier1");
                h.getLevel().setDayTime(31L*24000+18000);h.assertTrue(goal.canDestroy(p,false),"Blood frenzy permits source min tier<2");
                h.getLevel().setDayTime(18000);
            }mob.discard();});h.succeed();
    }
}
