package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.R196InfernalCreeper;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.ai.ConspicuousCactus;
import io.github.ningmeng123.mite.entity.ai.SourceCreeperSwellGoal;
import io.github.ningmeng123.mite.entity.ai.SourceMonsterFire;
import io.github.ningmeng123.mite.entity.ai.ZombieMoveToTreeGoal;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual qs, tree pursuit and its cactus/fire dependencies; never drives the user's client. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class CreeperTreeGameTests {
    private static <T extends Mob> T mob(GameTestHelper h,EntityType<T> type) {
        T mob=type.create(h.getLevel()); BlockPos pos=h.absolutePos(new BlockPos(3,3,3));
        mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0); mob.setNoAi(true); mob.setNoGravity(true); mob.setOnGround(true);
        h.getLevel().addFreshEntity(mob); return mob;
    }
    private static Cow target(GameTestHelper h,Creeper creeper,double distance) {
        Cow cow=mob(h,EntityType.COW); cow.moveTo(creeper.position().add(distance,0,0)); creeper.setTarget(cow); return cow;
    }
    private static void activePath(Creeper creeper) {
        BlockPos end=creeper.blockPosition().east(3);
        creeper.getNavigation().moveTo(new Path(List.of(new Node(end.getX(),end.getY(),end.getZ())),end,true),1D);
    }
    private static long seed(Predicate<Random> predicate) {
        for(long seed=0;seed<1_000_000;seed++) if(predicate.test(new Random(seed))) return seed;
        throw new AssertionError("Independent random seed not found");
    }
    @GameTest(template="ranged_027") public static void stoppedPathCreeperStartsStrictlyInsideFourBlocks(GameTestHelper h) {
        Creeper creeper=mob(h,EntityType.CREEPER); Cow target=target(h,creeper,4);
        var goal=new SourceCreeperSwellGoal(creeper);
        h.assertValueEqual(goal.triggerDistanceSquared(),16F,"A completed path uses squared distance sixteen");
        h.assertTrue(!goal.canUse(),"The activation boundary is strict"); target.moveTo(creeper.position().add(3.999,0,0));
        h.assertTrue(goal.canUse(),"Inside four blocks activates even at full HP"); creeper.discard(); target.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void movingCreeperUsesDistinctHealthyAndHurtDistances(GameTestHelper h) {
        Creeper creeper=mob(h,EntityType.CREEPER); Cow target=target(h,creeper,2.5); activePath(creeper);
        h.assertTrue(!creeper.getNavigation().isDone(),"The fixture creates a real active navigation path");
        var goal=new SourceCreeperSwellGoal(creeper);
        h.assertValueEqual(goal.triggerDistanceSquared(),4.5F,"A moving full-health creeper uses 4.5 squared distance");
        h.assertTrue(!goal.canUse(),"A healthy moving creeper does not use modern nine squared distance");
        creeper.setHealth(creeper.getMaxHealth()-.1F);
        h.assertValueEqual(goal.triggerDistanceSquared(),9F,"Any missing health expands moving activation to nine");
        h.assertTrue(goal.canUse(),"The same target activates a hurt moving creeper"); creeper.discard(); target.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void infernalSwellUsesItsLargerThresholdAndHealthClamp(GameTestHelper h) {
        Creeper infernal=(Creeper)MiteMobs.type(MobKind.INFERNAL_CREEPER).create(h.getLevel());
        BlockPos p=h.absolutePos(new BlockPos(3,3,3)); infernal.moveTo(p.getCenter()); infernal.setNoAi(true); h.getLevel().addFreshEntity(infernal);
        var goal=new SourceCreeperSwellGoal(infernal);
        h.assertTrue(infernal instanceof R196InfernalCreeper,"The actual added source identity is used");
        h.assertValueEqual(goal.triggerDistanceSquared(),32F,"Infernal activation squared distance doubles");
        h.assertValueEqual(goal.continuationDistanceSquared(),36F,"Full-health infernal continuation starts at thirty-six");
        infernal.setHealth(infernal.getMaxHealth()*.1F);
        h.assertValueEqual(goal.continuationDistanceSquared(),90F,"Continuation uses the 40 percent minimum HP fraction"); infernal.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void creatureTargetAloneCannotSustainTheFuse(GameTestHelper h) {
        Creeper creeper=mob(h,EntityType.CREEPER); Cow cow=target(h,creeper,1);
        var goal=new SourceCreeperSwellGoal(creeper); h.assertTrue(goal.canUse(),"A nearby nonplayer target activates the goal");
        goal.start(); goal.tick(); h.assertValueEqual(creeper.getSwellDir(),-1,"Without a visible living player it immediately defuses");
        creeper.discard(); cow.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void anotherVisibleCreativePlayerSustainsCachedNonplayerTarget(GameTestHelper h) {
        Creeper creeper=mob(h,EntityType.CREEPER); Cow cow=target(h,creeper,1);
        try(PlayerSession session=new PlayerSession(h,creeper.position().add(2,0,0))) {
            session.player.setGameMode(GameType.CREATIVE); var goal=new SourceCreeperSwellGoal(creeper); goal.start();
            creeper.setTarget(null); goal.tick();
            h.assertValueEqual(creeper.getSwellDir(),1,"Any living visible player sustains the cached goal; creativity and changed target do not exclude them");
        } finally { creeper.discard(); cow.discard(); } h.succeed();
    }
    @GameTest(template="ranged_027") public static void cachedNullAndStoppedGoalsDoNotAdoptNewTargets(GameTestHelper h) {
        Creeper creeper=mob(h,EntityType.CREEPER);
        try(PlayerSession session=new PlayerSession(h,creeper.position().add(2,0,0))) {
            var goal=new SourceCreeperSwellGoal(creeper); goal.start(); creeper.setTarget(session.player); goal.tick();
            h.assertValueEqual(creeper.getSwellDir(),-1,"A new target cannot replace the null target cached at start");
            goal.start(); goal.tick(); h.assertValueEqual(creeper.getSwellDir(),1,"Restart captures the target");
            goal.stop(); goal.tick(); h.assertValueEqual(creeper.getSwellDir(),-1,"Stopping clears the cached target");
        } finally { creeper.discard(); } h.succeed();
    }
    @GameTest(template="ranged_027") public static void solidCoverBlocksBothRaysButGlassPreservesSourceVision(GameTestHelper h) {
        Creeper creeper=mob(h,EntityType.CREEPER); Cow cow=target(h,creeper,1);
        try(PlayerSession session=new PlayerSession(h,creeper.position().add(3,0,0))) {
            BlockPos base=creeper.blockPosition().east();
            for(int y=0;y<3;y++) for(int z=-1;z<=1;z++) h.getLevel().setBlock(base.offset(0,y,z),Blocks.STONE.defaultBlockState(),2);
            var goal=new SourceCreeperSwellGoal(creeper); goal.start(); goal.tick();
            h.assertValueEqual(creeper.getSwellDir(),-1,"A full wall blocks knee and eye to both target body heights");
            for(int y=0;y<3;y++) for(int z=-1;z<=1;z++) h.getLevel().setBlock(base.offset(0,y,z),Blocks.GLASS.defaultBlockState(),2);
            goal.tick(); h.assertValueEqual(creeper.getSwellDir(),1,"Source vision permits transparent glass");
        } finally { creeper.discard(); cow.discard(); } h.succeed();
    }
    @GameTest(template="ranged_027") public static void ordinaryContinuationExpandsWithInjuryAndStaysCapped(GameTestHelper h) {
        Creeper creeper=mob(h,EntityType.CREEPER); var goal=new SourceCreeperSwellGoal(creeper);
        h.assertValueEqual(goal.continuationDistanceSquared(),16F,"Full HP continuation distance squared");
        creeper.setHealth(creeper.getMaxHealth()*.5F); h.assertValueEqual(goal.continuationDistanceSquared(),32F,"Half HP continuation distance squared");
        creeper.setHealth(creeper.getMaxHealth()*.1F); h.assertValueEqual(goal.continuationDistanceSquared(),40F,"Lower HP never exceeds the source cap");
        creeper.discard(); h.succeed();
    }
    private static BlockPos cactus(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(5,3,3)); h.getLevel().setBlock(pos.below(),Blocks.SAND.defaultBlockState(),2);
        h.getLevel().setBlock(pos,Blocks.CACTUS.defaultBlockState(),2); return pos;
    }
    private static void killAtCactus(GameTestHelper h,BlockPos cactus) {
        var chicken=mob(h,EntityType.CHICKEN); chicken.setHealth(.5F); chicken.moveTo(cactus.getCenter());
        h.getLevel().getBlockState(cactus).entityInside(h.getLevel(),cactus,chicken); chicken.discard();
    }
    @GameTest(template="ranged_027") public static void establishedCactusTrapCausesRealContactMemoryAndTimedDefusing(GameTestHelper h) {
        BlockPos cactus=cactus(h); killAtCactus(h,cactus); killAtCactus(h,cactus);
        h.assertValueEqual(ConspicuousCactus.killCount(h.getLevel(),cactus),2,"Two real lethal contacts establish a conspicuous cactus");
        Creeper creeper=mob(h,EntityType.CREEPER); creeper.moveTo(cactus.getCenter().add(1,0,0));
        for(int attempt=0;attempt<32&&ConspicuousCactus.recentTicks(creeper)==0;attempt++) {
            creeper.setHealth(creeper.getMaxHealth()); creeper.invulnerableTime=0;
            h.getLevel().getBlockState(cactus).entityInside(h.getLevel(),cactus,creeper);
        }
        h.assertValueEqual(ConspicuousCactus.recentTicks(creeper),120,"A successful real damage roll sets 120 transient ticks");
        var goal=new SourceCreeperSwellGoal(creeper); h.assertTrue(goal.canUse(),"A remembered trap activates without a target"); goal.start(); goal.tick();
        h.assertValueEqual(creeper.getSwellDir(),1,"A nearby conspicuous cactus overrides absent players and targets");
        creeper.moveTo(cactus.getCenter().add(6,0,0)); goal.tick(); h.assertValueEqual(creeper.getSwellDir(),-1,"Leaving the nearby cactus cancels the override");
        for(int i=0;i<120;i++) ConspicuousCactus.tick(creeper);
        h.assertValueEqual(ConspicuousCactus.recentTicks(creeper),0,"The source counter expires after exactly 120 decays"); creeper.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void cactusKillsCapPersistDecayAndResetWithBaseRemoval(GameTestHelper h) {
        BlockPos cactus=cactus(h); for(int i=0;i<9;i++) killAtCactus(h,cactus);
        h.assertValueEqual(ConspicuousCactus.killCount(h.getLevel(),cactus),7,"Source support sand stores at most seven kills");
        var saved=ConspicuousCactus.data(h.getLevel()).save(new CompoundTag(),h.getLevel().registryAccess());
        h.assertTrue(ConspicuousCactus.Data.load(saved,h.getLevel().registryAccess()).save(new CompoundTag(),h.getLevel().registryAccess()).equals(saved),"Source trap counts survive their actual SavedData codec");
        for(int i=0;i<100&&ConspicuousCactus.killCount(h.getLevel(),cactus)==7;i++) ConspicuousCactus.randomDecay(h.getLevel(),cactus);
        h.assertValueEqual(ConspicuousCactus.killCount(h.getLevel(),cactus),6,"A successful random decay removes one trap kill");
        h.getLevel().setBlock(cactus,Blocks.AIR.defaultBlockState(),3);
        h.assertValueEqual(ConspicuousCactus.killCount(h.getLevel(),cactus),0,"Removing the base cactus clears the support history"); h.succeed();
    }
    @GameTest(template="ranged_027") public static void treeGoalRequiresBurningAndItsOneInFortyDraw(GameTestHelper h) {
        Zombie zombie=mob(h,EntityType.ZOMBIE); var goal=new ZombieMoveToTreeGoal(zombie);
        long yes=seed(random -> random.nextInt(40)==0),no=seed(random -> random.nextInt(40)!=0);
        zombie.getRandom().setSeed(yes); h.assertTrue(!goal.canUse(),"A nonburning zombie cannot activate tree pursuit");
        zombie.igniteForSeconds(10); zombie.getRandom().setSeed(no); h.assertTrue(!goal.canUse(),"Burning does not bypass the one-in-forty draw");
        zombie.getRandom().setSeed(yes); h.assertTrue(goal.canUse(),"A burning source zombie accepts the successful draw"); zombie.discard(); h.succeed();
    }
    @GameTest(template="ranged_027") public static void elevatedPlayerBoundariesAndActualTreePathEnableFireBoost(GameTestHelper h) {
        Zombie zombie=mob(h,EntityType.ZOMBIE); BlockPos log=h.absolutePos(new BlockPos(6,3,3));
        for(int x=1;x<=9;x++) for(int z=1;z<=5;z++) h.getLevel().setBlock(h.absolutePos(new BlockPos(x,2,z)),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(log,Blocks.OAK_LOG.defaultBlockState(),2); zombie.igniteForSeconds(10);
        try(PlayerSession session=new PlayerSession(h,log.getCenter().add(0,2,0))) {
            h.assertTrue(!ZombieMoveToTreeGoal.playerAboveTree(session.player,log),"Two blocks above is excluded");
            session.player.moveTo(log.getCenter().add(0,3,0)); h.assertTrue(ZombieMoveToTreeGoal.playerAboveTree(session.player,log),"Three blocks above is included");
            var goal=new ZombieMoveToTreeGoal(zombie); h.assertTrue(goal.findPathToTree()!=null,"A real modern path reaches an adjacent point at the tree");
            h.assertValueEqual(SourceMonsterFire.boostTicks(zombie),100,"Finding that path starts the actual fire consumer's hundred tick boost");
            session.player.moveTo(log.getCenter().add(0,9,0)); h.assertTrue(!ZombieMoveToTreeGoal.playerAboveTree(session.player,log),"Nine blocks above is excluded");
            session.player.moveTo(log.getCenter().add(6,3,0)); h.assertTrue(!ZombieMoveToTreeGoal.playerAboveTree(session.player,log),"Horizontal distance squared over thirty-two is excluded");
        } finally { zombie.discard(); } h.succeed();
    }
    @GameTest(template="ranged_027") public static void treeCandidatesUseTheFirstEightInSourceColumnAndSpiralOrder(GameTestHelper h) {
        Zombie zombie=mob(h,EntityType.ZOMBIE); var goal=new ZombieMoveToTreeGoal(zombie);
        BlockPos origin=BlockPos.containing(zombie.getX(),zombie.getY()+zombie.getBbHeight()*.75F,zombie.getZ());
        try {
            for(int dy=-4;dy<=4;dy++) h.getLevel().setBlock(origin.offset(0,dy,0),Blocks.OAK_LOG.defaultBlockState(),2);
            h.getLevel().setBlock(origin.east(),Blocks.OAK_LOG.defaultBlockState(),2);
            List<BlockPos> column=goal.treeCandidates();
            h.assertValueEqual(column.size(),8,"The source search stops at its eighth match");
            for(int i=0;i<8;i++) h.assertValueEqual(column.get(i),origin.offset(0,i-4,0),"Each column scans low to high, even before geometrically closer logs");
            for(int dy=-4;dy<=4;dy++) h.getLevel().setBlock(origin.offset(0,dy,0),Blocks.AIR.defaultBlockState(),2);
            List<BlockPos> spiral=List.of(origin,origin.east(),origin.east().south(),origin.south(),origin.west().south(),origin.west(),origin.west().north(),origin.north(),origin.east().north());
            for(BlockPos pos:spiral) h.getLevel().setBlock(pos,Blocks.OAK_LOG.defaultBlockState(),2);
            List<BlockPos> candidates=goal.treeCandidates();
            h.assertValueEqual(candidates.size(),8,"The ninth matching column is not considered");
            for(int i=0;i<8;i++) h.assertValueEqual(candidates.get(i),spiral.get(i),"Source XZ spiral order is preserved without distance sorting");
            h.assertTrue(!candidates.contains(spiral.get(8)),"A nearby ninth match cannot displace one of the first eight");
        } finally { zombie.discard(); } h.succeed();
    }
    @GameTest(template="ranged_027") public static void treeFireBoostIsConsumedByRealWorldIgnitionAndRespectsProtection(GameTestHelper h) {
        Zombie zombie=mob(h,EntityType.ZOMBIE); zombie.igniteForSeconds(10); BlockPos feet=zombie.blockPosition();
        var rules=h.getLevel().getGameRules(); boolean grief=rules.getBoolean(GameRules.RULE_MOBGRIEFING),fire=rules.getBoolean(GameRules.RULE_DOFIRETICK);
        long drawn=seed(random -> { float value=random.nextFloat(); return value>=.00125F&&value<.01F&&random.nextInt(10)==0; });
        try {
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(true,h.getLevel().getServer()); rules.getRule(GameRules.RULE_DOFIRETICK).set(true,h.getLevel().getServer());
            h.getLevel().setBlock(feet.below(),Blocks.STONE.defaultBlockState(),2); h.getLevel().setBlock(feet.west(),Blocks.OAK_LOG.defaultBlockState(),2);
            zombie.getRandom().setSeed(drawn); SourceMonsterFire.tickSource(zombie);
            h.assertTrue(h.getLevel().isEmptyBlock(feet),"The same draw is outside ordinary burning probability");
            SourceMonsterFire.boost(zombie,100); zombie.getRandom().setSeed(drawn); SourceMonsterFire.tickSource(zombie);
            h.assertTrue(h.getLevel().getBlockState(feet).is(Blocks.FIRE),"The tree boost causes actual nearby ignition, not an unused saved field");
            h.assertValueEqual(SourceMonsterFire.boostTicks(zombie),99,"The boost counter is consumed");
            h.getLevel().setBlock(feet,Blocks.AIR.defaultBlockState(),2);
            SourceMonsterFire.boost(zombie,1); zombie.getRandom().setSeed(drawn); SourceMonsterFire.tickSource(zombie);
            h.assertTrue(h.getLevel().getBlockState(feet).is(Blocks.FIRE),"The last remaining tick still receives the strengthened ignition roll");
            h.assertValueEqual(SourceMonsterFire.boostTicks(zombie),0,"The final strengthened tick expires after its environment pulse");
            h.getLevel().setBlock(feet,Blocks.AIR.defaultBlockState(),2); zombie.getRandom().setSeed(drawn); SourceMonsterFire.tickSource(zombie);
            h.assertTrue(h.getLevel().isEmptyBlock(feet),"The very next tick returns to ordinary burning probability");
            h.getLevel().setBlock(feet,Blocks.AIR.defaultBlockState(),2); rules.getRule(GameRules.RULE_MOBGRIEFING).set(false,h.getLevel().getServer());
            h.assertTrue(!SourceMonsterFire.spreadPulse(zombie)&&h.getLevel().isEmptyBlock(feet),"Modern grief protection blocks the ignition pulse");
        } finally { rules.getRule(GameRules.RULE_MOBGRIEFING).set(grief,h.getLevel().getServer()); rules.getRule(GameRules.RULE_DOFIRETICK).set(fire,h.getLevel().getServer()); zombie.discard(); } h.succeed();
    }
    @GameTest(template="ranged_027") public static void firePulseMeltsActualIceAndDoesNotInventColdZombieFire(GameTestHelper h) {
        Zombie zombie=mob(h,EntityType.ZOMBIE); BlockPos floor=zombie.blockPosition().below();
        var rules=h.getLevel().getGameRules(); boolean grief=rules.getBoolean(GameRules.RULE_MOBGRIEFING),fire=rules.getBoolean(GameRules.RULE_DOFIRETICK);
        try {
            h.assertValueEqual(SourceMonsterFire.ignitionChance(zombie),0F,"A cold ordinary zombie has no environment ignition chance");
            zombie.igniteForSeconds(10); h.assertValueEqual(SourceMonsterFire.ignitionChance(zombie),.00125F,"Ordinary burning source probability");
            SourceMonsterFire.boost(zombie,100); h.assertValueEqual(SourceMonsterFire.ignitionChance(zombie),.01F,"Tree-enhanced source probability");
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(true,h.getLevel().getServer()); rules.getRule(GameRules.RULE_DOFIRETICK).set(true,h.getLevel().getServer());
            long noAirIgnition=seed(random -> random.nextInt(10)!=0);
            h.getLevel().setBlock(floor,Blocks.ICE.defaultBlockState(),2); zombie.setOnGround(false); zombie.getRandom().setSeed(noAirIgnition);
            SourceMonsterFire.spreadPulse(zombie); h.assertTrue(h.getLevel().getBlockState(floor).is(Blocks.ICE),"An airborne entity cannot claim supporting ice");
            Vec3 resting=zombie.position(); zombie.moveTo(resting.add(0,.05D,0)); zombie.setOnGround(true); zombie.getRandom().setSeed(noAirIgnition);
            SourceMonsterFire.spreadPulse(zombie); h.assertTrue(h.getLevel().getBlockState(floor).is(Blocks.ICE),"The source .1 argument does not permit a .05 block gap below the feet");
            zombie.moveTo(resting); Cow vehicle=mob(h,EntityType.COW);
            try {
                h.assertTrue(zombie.startRiding(vehicle,true)&&zombie.isPassenger(),"The fixture is a real passenger");
                zombie.setOnGround(true); zombie.getRandom().setSeed(noAirIgnition);
                SourceMonsterFire.spreadPulse(zombie); h.assertTrue(h.getLevel().getBlockState(floor).is(Blocks.ICE),"Riding excludes the resting-block melt even with onGround set");
            } finally { zombie.stopRiding(); vehicle.discard(); }
            zombie.moveTo(resting); zombie.setOnGround(true); zombie.getRandom().setSeed(noAirIgnition);
            SourceMonsterFire.spreadPulse(zombie); h.assertTrue(h.getLevel().getBlockState(floor).is(Blocks.WATER),"The source heat pulse melts the supporting ice");
            h.getLevel().setBlock(floor,Blocks.STONE.defaultBlockState(),2); h.getLevel().setBlock(floor.west(),Blocks.ICE.defaultBlockState(),2);
            zombie.moveTo(resting.add(-.4D,0,0)); zombie.setOnGround(true); zombie.getRandom().setSeed(noAirIgnition);
            SourceMonsterFire.spreadPulse(zombie); h.assertTrue(h.getLevel().getBlockState(floor.west()).is(Blocks.ICE),"With two touching supports the nearer block center wins, rather than melting every foot contact");
            h.getLevel().setBlock(floor,Blocks.AIR.defaultBlockState(),2); zombie.getRandom().setSeed(noAirIgnition);
            SourceMonsterFire.spreadPulse(zombie); h.assertTrue(h.getLevel().getBlockState(floor.west()).is(Blocks.WATER),"The full foot AABB finds edge support outside the center column");
        } finally { rules.getRule(GameRules.RULE_MOBGRIEFING).set(grief,h.getLevel().getServer()); rules.getRule(GameRules.RULE_DOFIRETICK).set(fire,h.getLevel().getServer()); zombie.discard(); } h.succeed();
    }
    private static final class PlayerSession implements AutoCloseable {
        private final ServerPlayer player; private final EmbeddedChannel channel;
        PlayerSession(GameTestHelper h,Vec3 position) {
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mite-creeper-035"),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            Connection connection=new Connection(PacketFlow.SERVERBOUND); channel=new EmbeddedChannel(connection);
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie); player.setGameMode(GameType.CREATIVE);
            player.moveTo(position); player.setNoGravity(true);
        }
        @Override public void close() { try { player.server.getPlayerList().remove(player); } finally { channel.finishAndReleaseAll(); } }
    }
}
