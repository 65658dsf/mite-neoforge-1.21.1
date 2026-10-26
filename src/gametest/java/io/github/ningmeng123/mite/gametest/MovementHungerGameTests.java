package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.survival.LiquidJumpEffort;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native statistics and boat-tick entry points, with exhaustion inspected before metabolism. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class MovementHungerGameTests {
    private static final Map<ServerPlayer,EmbeddedChannel> CHANNELS=new IdentityHashMap<>();
    private static ServerPlayer player(GameTestHelper h) {
        var profile=new GameProfile(UUID.randomUUID(),"movement-"+UUID.randomUUID().toString().substring(0,6));
        var cookie=CommonListenerCookie.createInitial(profile,false);
        ServerPlayer p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);CHANNELS.put(p,channel);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);
        p.moveTo(h.absolutePos(new BlockPos(2,2,2)).getCenter());p.setOnGround(true);p.getFoodData().setExhaustion(0F);return p;
    }
    private static void effort(GameTestHelper h,Player p,float expected,String reason) {
        h.assertTrue(Math.abs(p.getFoodData().getExhaustionLevel()-expected)<1E-6F,
                reason+" expected="+expected+" actual="+p.getFoodData().getExhaustionLevel());p.getFoodData().setExhaustion(0F);
    }
    private static void cleanup(GameTestHelper h,ServerPlayer p) {
        LiquidJumpEffort.clear(p);p.stopRiding();h.getLevel().getServer().getPlayerList().remove(p);
        var channel=CHANNELS.remove(p);if(channel!=null)channel.finishAndReleaseAll();
    }
    @GameTest(template="empty")
    public static void nativeWalkSprintCrouchAndJumpPayOnlyTheSourceMovementFee(GameTestHelper h) {
        var p=player(h);
        try {
            p.checkMovementStatistics(1D,0D,0D);effort(h,p,.0115F,"Walking pays its source fee with the global 15% increase once");
            p.setSprinting(true);p.checkMovementStatistics(1D,0D,0D);effort(h,p,.0575F,"Sprinting replaces the modern fee before the global 15% increase");
            p.setSprinting(false);p.setShiftKeyDown(true);p.checkMovementStatistics(1D,0D,0D);effort(h,p,.0115F,"Crouching uses the increased ordinary walking cost");
            p.setShiftKeyDown(false);p.jumpFromGround();effort(h,p,0F,"Native jump statistic and motion do not add a modern-only fee");
            p.setOnGround(false);p.checkMovementStatistics(1D,1D,0D);effort(h,p,0F,"Falling and modern airborne movement keep their native zero fee");
            p.setGameMode(GameType.CREATIVE);p.setOnGround(true);p.checkMovementStatistics(1D,0D,0D);effort(h,p,0F,"Creative invulnerability still exempts exhaustion");
        } finally { cleanup(h,p); }h.succeed();
    }
    @GameTest(template="empty")
    public static void nativeUnderwaterAndClimbingBranchesChargeTheirOwnSourceMetric(GameTestHelper h) {
        var p=player(h);var pos=h.absolutePos(new BlockPos(2,2,2));
        try {
            h.getLevel().setBlock(pos,Blocks.WATER.defaultBlockState(),2);h.getLevel().setBlock(pos.above(),Blocks.WATER.defaultBlockState(),2);
            p.setPos(pos.getX()+.5D,pos.getY(),pos.getZ()+.5D);p.baseTick();p.getFoodData().setExhaustion(0F);
            h.assertTrue(p.isEyeInFluid(FluidTags.WATER),"Fixture eyes are actually submerged");
            p.checkMovementStatistics(3D,4D,0D);effort(h,p,.08625F,"Underwater fee uses the full three-dimensional five meters with the global increase once");
            h.getLevel().setBlock(pos,Blocks.LADDER.defaultBlockState(),2);h.getLevel().setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
            p.baseTick();p.getFoodData().setExhaustion(0F);h.assertTrue(p.onClimbable()&&!p.isInWater(),"Fixture leaves water for the ladder");
            p.checkMovementStatistics(0D,.025D,0D);effort(h,p,.002875F,"Climbing uses unrounded positive vertical distance with the global increase once");
            p.checkMovementStatistics(0D,-.5D,0D);effort(h,p,0F,"Descending a ladder costs no climbing effort");
        } finally { cleanup(h,p);h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);h.getLevel().setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2); }h.succeed();
    }
    @GameTest(template="empty")
    public static void actualBoatTickChargesOnlyItsControllingPlayersForwardOrReverseInput(GameTestHelper h) {
        var p=player(h);Boat boat=EntityType.BOAT.create(h.getLevel());boat.setNoGravity(true);boat.moveTo(p.position());h.getLevel().addFreshEntity(boat);
        try {
            h.assertTrue(p.startRiding(boat,true)&&boat.getControllingPassenger()==p,"The actual boat has its native controlling passenger");
            p.zza=1F;boat.tick();effort(h,p,.0115F,"Forward rowing includes the global 15% increase per boat tick");
            p.zza=-.5F;boat.tick();effort(h,p,.00575F,"Reverse effort follows absolute native forward input with the global increase");
            p.zza=0F;boat.setDeltaMovement(1D,0D,0D);boat.tick();effort(h,p,0F,"Coasting costs no rowing effort regardless of displacement");
            p.zza=1F;p.checkMovementStatistics(1D,0D,0D);effort(h,p,0F,"Passenger statistics cannot duplicate a rowing charge");
        } finally { cleanup(h,p);boat.discard(); }h.succeed();
    }
    @GameTest(template="empty")
    public static void liquidJumpInputIsValidatedAndChargedAtMostOncePerServerTick(GameTestHelper h) {
        var p=player(h);var pos=h.absolutePos(new BlockPos(2,2,2));
        try {
            LiquidJumpEffort.setInput(p,true);LiquidJumpEffort.tick(p);effort(h,p,0F,"Input alone cannot charge an on-land player");
            h.getLevel().setBlock(pos,Blocks.WATER.defaultBlockState(),2);h.getLevel().setBlock(pos.above(),Blocks.WATER.defaultBlockState(),2);
            p.setPos(pos.getX()+.5D,pos.getY(),pos.getZ()+.5D);p.baseTick();p.getFoodData().setExhaustion(0F);
            h.assertTrue(p.isInWater(),"Server validates actual liquid immersion");
            LiquidJumpEffort.setInput(p,false);LiquidJumpEffort.tick(p);effort(h,p,0F,"Being in water without upward input is free");
            LiquidJumpEffort.setInput(p,true);LiquidJumpEffort.tick(p);LiquidJumpEffort.tick(p);
            LiquidJumpEffort.setInput(p,false);LiquidJumpEffort.setInput(p,true);LiquidJumpEffort.tick(p);
            effort(h,p,.0115F,"Repeated packets and callbacks cannot duplicate the globally increased liquid-jump fee");
            LiquidJumpEffort.clear(p);LiquidJumpEffort.tick(p);effort(h,p,0F,"Clearing intent prevents a stale dimension/reconnect charge");
        } finally { cleanup(h,p);h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);h.getLevel().setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2); }h.succeed();
    }
}
