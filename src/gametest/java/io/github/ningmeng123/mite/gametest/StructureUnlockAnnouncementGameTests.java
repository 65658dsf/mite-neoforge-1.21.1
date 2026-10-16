package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.survival.DeathOrbQueue;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import io.github.ningmeng123.mite.worldgen.StructureUnlockAnnouncements;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native packets and player save/respawn lifecycle, without enabling structures in the test world. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StructureUnlockAnnouncementGameTests {
    private static final int BOTH=StructureUnlockAnnouncements.VILLAGE|StructureUnlockAnnouncements.END_PORTAL;
    private static final String PREFIX="message.mite.structure_unlock.";
    private StructureUnlockAnnouncementGameTests() {}

    @GameTest(template="empty")
    public static void actualGeneratorSetsRespectTheSixtiethDayAndCompletedToolGate(GameTestHelper h) {
        try (WorldState world=new WorldState(h)) {
            var options=world.enabledOptions();
            boolean hasVillage=false,hasStronghold=false;
            for (var set:world.level.getChunkSource().getGeneratorState().possibleStructureSets()) {
                for (var entry:set.value().structures()) {
                    var id=entry.structure().unwrapKey().orElseThrow().location();
                    if (!id.getNamespace().equals("minecraft")) continue;
                    hasVillage|=id.getPath().startsWith("village_");
                    hasStronghold|=id.getPath().equals("stronghold");
                }
            }
            h.assertTrue(hasVillage&&hasStronghold,"The real overworld generator enables village and stronghold structure sets");
            world.at(0L,0);
            h.assertValueEqual(StructureUnlockAnnouncements.available(world.level,options),StructureUnlockAnnouncements.END_PORTAL,
                    "The source end portal has no invented date, equipment, or world-achievement lock");
            world.at(1_409_999L,16);
            h.assertValueEqual(R196StructureGeneration.displayDay(world.level.getDayTime()),59L,"The last locked tick belongs to display day59");
            h.assertValueEqual(StructureUnlockAnnouncements.available(world.level,options),StructureUnlockAnnouncements.END_PORTAL,
                    "A qualifying crafted tool cannot bypass the last tick before day60");
            world.at(1_410_000L,15);
            h.assertValueEqual(R196StructureGeneration.displayDay(world.level.getDayTime()),60L,"The source day60 starts at1410000");
            h.assertValueEqual(StructureUnlockAnnouncements.available(world.level,options),StructureUnlockAnnouncements.END_PORTAL,
                    "All four crop bits together still do not meet the integer tool prerequisite16");
            world.at(1_410_000L,16);
            h.assertValueEqual(StructureUnlockAnnouncements.available(world.level,options),BOTH,
                    "The real natural-generation gate allows villages exactly when date and tool progress are both satisfied");
            h.assertValueEqual(StructureUnlockAnnouncements.available(world.level,new WorldOptions(world.level.getSeed(),false,false)),0,
                    "Disabled structure generation overrides the otherwise completed source prerequisites");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void disabledWorldNativeLoginAndAnnouncementTickSendNoFalseUnlocks(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            h.assertFalse(world.level.getServer().getWorldData().worldGenOptions().generateStructures(),
                    "The actual GameTest world keeps its original disabled structure-generation option");
            world.at(1_410_000L,16);
            h.assertTrue(session.drain().isEmpty(),"Actual PlayerList login did not claim disabled structures can generate");
            h.assertValueEqual(StructureUnlockAnnouncements.announce(session.player()),0,
                    "The production announce entry reads disabled real WorldOptions");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(session.player()));
            // Invoke only this subscriber: posting a world tick would run unrelated full-world fixture logic.
            StructureUnlockAnnouncements.tick(new LevelTickEvent.Post(()->true,world.level));
            h.assertTrue(session.drain().isEmpty(),"Registered login and the production tick subscriber remain silent");
            h.assertFalse(session.player().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                    .contains(StructureUnlockAnnouncements.DATA_KEY),"A disabled world does not consume either player's future notice");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void endPortalThenVillageSendSeparateColoredChatAndNeverRepeatOnRollback(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            session.drain();
            CompoundTag persisted=session.player().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            persisted.putString("other_fixture_marker","preserve");
            session.player().getPersistentData().put(Player.PERSISTED_NBT_TAG,persisted);
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),1<<12,1L),0,
                    "Unknown snapshot bits cannot send or consume a structure notice");
            world.at(0L,0);
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),1L),1,"The first eligible structure sends one packet");
            assertPacket(h,only(h,session.drain()),"end_portal",ChatFormatting.AQUA,1L);
            h.assertValueEqual(marker(session.player()).getInt("seen"),StructureUnlockAnnouncements.END_PORTAL,
                    "Only the delivered end portal bit is persisted");
            world.at(1_410_000L,16);
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),60L),1,
                    "Completing the independent village gate sends only the newly available notice");
            assertPacket(h,only(h,session.drain()),"village",ChatFormatting.GREEN,60L);
            for (long day:new long[]{60L,60L,1L,59L,1000L}) {
                h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),BOTH,day),0,
                        "Repeated snapshots and calendar rollback never repeat an already delivered structure");
            }
            h.assertTrue(session.drain().isEmpty(),"No duplicate native system chat was sent");
            h.assertValueEqual(marker(session.player()).getInt("version"),1,"The persisted format is explicitly versioned");
            h.assertValueEqual(marker(session.player()).getInt("seen"),BOTH,"Both independent notices have been delivered");
            h.assertValueEqual(session.player().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                    .getString("other_fixture_marker"),"preserve","Notice updates preserve unrelated persistent player data");
        }
        lateJoiningDifferentUuidReceivesItsOwnTwoNotices(h);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void actualPlayerFileReloginRetainsBothNoticesEvenAfterTimeRollback(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            world.at(1_410_000L,16);session.drain();
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),60L),2,
                    "A first login after both milestones delivers both current eligibility facts");
            assertBoth(h,session.drain(),60L);
            ServerPlayer before=session.player();
            CompoundTag saved=before.saveWithoutId(new CompoundTag());
            CompoundTag savedNotice=saved.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG)
                    .getCompound(StructureUnlockAnnouncements.DATA_KEY);
            h.assertValueEqual(savedNotice.getInt("version"),1,"Actual native player NBT contains the notice schema");
            h.assertValueEqual(savedNotice.getInt("seen"),BOTH,"Actual native player NBT contains both delivered bits");
            world.at(0L,0);
            session.reconnect();
            h.assertTrue(session.player()!=before&&session.player().getUUID().equals(before.getUUID()),
                    "PlayerList.remove and placeNewPlayer load a new object from the same UUID's actual player file");
            h.assertValueEqual(marker(session.player()).getInt("seen"),BOTH,"Real disconnect saving and login loading retain the notice marker");
            h.assertTrue(session.drain().isEmpty(),"Real relogin after time rollback does not repeat notices");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),BOTH,1L),0,
                    "Even a subsequently eligible full snapshot cannot repeat saved notices");
            h.assertTrue(session.drain().isEmpty(),"The restored player's active connection receives no duplicate packet");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void realDeathAndNativeRespawnCopyPlayerPersistedWithoutAnotherNotice(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            world.at(1_410_000L,16);session.drain();
            StructureUnlockAnnouncements.deliver(session.player(),world.available(),60L);
            assertBoth(h,session.drain(),60L);
            ServerPlayer dead=session.player();
            dead.getInventory().clearContent();
            dead.kill();
            h.assertFalse(dead.isAlive(),"The actual native death path ran before respawning");
            dead.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            ServerPlayer replacement=session.player();
            h.assertTrue(replacement!=dead&&replacement.isAlive()&&replacement.getUUID().equals(dead.getUUID()),
                    "The actual respawn packet replaces the dead player and preserves UUID identity");
            h.assertTrue(replacement.server.getPlayerList().getPlayer(replacement.getUUID())==replacement,
                    "The native player registry and network listener agree on the replacement");
            h.assertValueEqual(marker(replacement).getInt("version"),1,"Native death cloning retains the versioned PlayerPersisted marker");
            h.assertValueEqual(marker(replacement).getInt("seen"),BOTH,"Native death cloning retains both delivered notice bits");
            h.assertTrue(session.drain().isEmpty(),"The actual death and native respawn lifecycle itself sends no duplicate structure packet");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(replacement,BOTH,60L),0,
                    "A living native replacement does not replay either notice");
            h.assertTrue(session.drain().isEmpty(),"Native respawn cannot duplicate structure chat");
        }
        h.succeed();
    }

    private static void lateJoiningDifferentUuidReceivesItsOwnTwoNotices(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session first=new Session(h); Session late=new Session(h)) {
            world.at(1_410_000L,16);first.drain();late.drain();
            h.assertFalse(first.player().getUUID().equals(late.player().getUUID()),"The two real connected players have different identities");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(first.player(),world.available(),60L),2,"First player receives both eligible notices");
            assertBoth(h,first.drain(),60L);
            h.assertFalse(marker(late.player()).contains("seen"),"One player's delivery does not consume the other player's eligibility");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(late.player(),world.available(),60L),2,
                    "A late player independently receives both already-available structure notices");
            assertBoth(h,late.drain(),60L);
            h.assertTrue(first.drain().isEmpty(),"The late player's personal notices are not broadcast to the earlier player");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(late.player(),BOTH,60L),0,"The late player's own duplicate is suppressed");
        }
    }

    @GameTest(template="empty")
    public static void crossDimensionEligibilityAndDeliveryAlwaysFollowTheOverworldGate(GameTestHelper h) {
        ServerLevel nether=h.getLevel().getServer().getLevel(Level.NETHER);
        ServerLevel underworld=h.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        h.assertTrue(nether!=null&&underworld!=null,"The real nether and underworld dimensions are registered");
        long netherTime=nether.getDayTime();
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            world.at(0L,0);nether.setDayTime(1_410_000L);session.drain();
            session.player().teleportTo(nether,.5D,160D,.5D,0F,0F);
            h.assertTrue(session.player().serverLevel()==nether,"A native dimension teleport moves the connected player to the nether");
            h.assertValueEqual(StructureUnlockAnnouncements.available(nether,world.enabledOptions()),StructureUnlockAnnouncements.END_PORTAL,
                    "A locally late dimension cannot unlock overworld villages while overworld prerequisites are missing");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),1L),1,
                    "The connected off-world player can receive the overworld end-portal eligibility fact");
            assertPacket(h,only(h,session.drain()),"end_portal",ChatFormatting.AQUA,1L);
            world.at(1_410_000L,16);nether.setDayTime(0L);
            h.assertValueEqual(StructureUnlockAnnouncements.available(nether,world.enabledOptions()),BOTH,
                    "A locally early dimension cannot hide overworld village eligibility");
            h.assertValueEqual(StructureUnlockAnnouncements.available(underworld,world.enabledOptions()),BOTH,
                    "The same shared overworld generator and progress are used for the underworld");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),60L),1,
                    "Only the newly eligible village fact reaches the off-world player");
            assertPacket(h,only(h,session.drain()),"village",ChatFormatting.GREEN,60L);
            session.player().teleportTo(world.level,.5D,160D,.5D,0F,0F);
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),60L),0,
                    "Returning dimensions cannot replay already delivered personal notices");
            h.assertTrue(session.drain().isEmpty(),"The native return teleport does not duplicate eligibility chat");
        } finally {
            nether.setDayTime(netherTime);
        }
        h.succeed();
    }

    private static CompoundTag marker(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(StructureUnlockAnnouncements.DATA_KEY);
    }
    private static ClientboundSystemChatPacket only(GameTestHelper h,List<ClientboundSystemChatPacket> packets) {
        h.assertValueEqual(packets.size(),1,"Exactly one actual structure system-chat packet was sent");return packets.getFirst();
    }
    private static void assertBoth(GameTestHelper h,List<ClientboundSystemChatPacket> packets,long day) {
        h.assertValueEqual(packets.size(),2,"Exactly the two independent native structure packets were sent");
        assertPacket(h,packets.get(0),"end_portal",ChatFormatting.AQUA,day);
        assertPacket(h,packets.get(1),"village",ChatFormatting.GREEN,day);
    }
    private static void assertPacket(GameTestHelper h,ClientboundSystemChatPacket packet,String structure,ChatFormatting color,long day) {
        h.assertFalse(packet.overlay(),"Structure eligibility is displayed in chat rather than the action bar");
        h.assertValueEqual(packet.content().getStyle().getColor(),TextColor.fromLegacyFormat(color),"The actual packet carries the intended structure color");
        h.assertTrue(packet.content().getContents() instanceof TranslatableContents,"The packet is localized on the client");
        var content=(TranslatableContents)packet.content().getContents();
        h.assertValueEqual(content.getKey(),PREFIX+structure,"The packet uses the exact structure translation key");
        h.assertValueEqual(content.getArgs().length,1,"The message carries one authoritative display-day argument");
        h.assertTrue(content.getArgs()[0] instanceof Number&&((Number)content.getArgs()[0]).longValue()==day,
                "The native packet preserves the full authoritative calendar day");
    }

    private static final class WorldState implements AutoCloseable {
        final ServerLevel level;
        final StructureProgressData progress;
        final long dayTime;
        final int conditions;
        WorldState(GameTestHelper h) {
            level=h.getLevel().getServer().overworld();progress=StructureProgressData.get(level);
            dayTime=level.getDayTime();conditions=progress.conditions();
        }
        WorldOptions enabledOptions() { return new WorldOptions(level.getSeed(),true,false); }
        int available() { return StructureUnlockAnnouncements.available(level,enabledOptions()); }
        void at(long time,int bits) { progress.restoreConditions(bits);level.setDayTime(time);R196StructureGeneration.refresh(level); }
        @Override public void close() { progress.restoreConditions(conditions);level.setDayTime(dayTime);R196StructureGeneration.refresh(level); }
    }

    /** Actual login, player-file save/reload, and the same listener followed through native respawning. */
    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;
        final GameProfile profile=new GameProfile(UUID.randomUUID(),"struct-"+UUID.randomUUID().toString().substring(0,8));
        EmbeddedChannel channel;
        ServerGamePacketListenerImpl listener;
        Session(GameTestHelper h) { helper=h;join(); }
        ServerPlayer player() { return listener.player; }
        private void join() {
            var cookie=CommonListenerCookie.createInitial(profile,false);
            var created=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),profile,cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            created.server.getPlayerList().placeNewPlayer(connection,created,cookie);listener=created.connection;
            created.setGameMode(GameType.SURVIVAL);created.setNoGravity(true);
            BlockPos p=helper.absolutePos(new BlockPos(1,24,1));created.moveTo(p.getX()+.5D,p.getY(),p.getZ()+.5D);
        }
        void reconnect() { leave();join(); }
        List<ClientboundSystemChatPacket> drain() {
            channel.runPendingTasks();channel.flushOutbound();List<ClientboundSystemChatPacket> result=new ArrayList<>();
            Object packet;
            while ((packet=channel.readOutbound())!=null) {
                if (packet instanceof ClientboundSystemChatPacket chat
                        && chat.content().getContents() instanceof TranslatableContents text&&text.getKey().startsWith(PREFIX)) result.add(chat);
            }
            return result;
        }
        private void leave() {
            var current=helper.getLevel().getServer().getPlayerList().getPlayer(profile.getId());
            if (current!=null) current.server.getPlayerList().remove(current);
            channel.finishAndReleaseAll();
        }
        @Override public void close() {
            player().getInventory().clearContent();
            for (ServerLevel level:helper.getLevel().getServer().getAllLevels()) DeathOrbQueue.get(level).removeForOwner(profile.getId());
            leave();
        }
    }
}
