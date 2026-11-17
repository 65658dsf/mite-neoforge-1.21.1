package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.advancement.StructureAdvancements;
import io.github.ningmeng123.mite.survival.DeathOrbQueue;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import io.github.ningmeng123.mite.worldgen.StructureUnlockAnnouncements;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.StructureSet;
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
    private static final int NEW=StructureUnlockAnnouncements.RUINED_PORTAL|StructureUnlockAnnouncements.PILLAGER_OUTPOST;
    private static final int ALL=BOTH|NEW;
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
            h.assertValueEqual(StructureUnlockAnnouncements.available(world.level,options),0,
                    "A stronghold cannot announce before the new day-32 gate even with the crafted-eye fixture complete");
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
    public static void realPortalAndOutpostGatesSendColoredChatOnlyAfterEveryPrerequisite(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            boolean hasPortal=false,hasOutpost=false;
            for (var set:world.level.getChunkSource().getGeneratorState().possibleStructureSets()) {
                for (var entry:set.value().structures()) {
                    var id=entry.structure().unwrapKey().orElseThrow().location();
                    if (!id.getNamespace().equals("minecraft")) continue;
                    hasPortal|=id.getPath().equals("ruined_portal")||id.getPath().startsWith("ruined_portal_");
                    hasOutpost|=id.getPath().equals("pillager_outpost");
                }
            }
            h.assertTrue(hasPortal&&hasOutpost,"The real overworld generator enables ruined portals and pillager outposts");
            session.drain();world.at(0L,0);
            assertNoNewNotice(h,world,session,0,1L);
            world.at(737_999L,StructureProgressData.ALL_CONDITIONS);
            h.assertValueEqual(R196StructureGeneration.displayDay(world.level.getDayTime()),31L,"The last portal-locked tick is day31");
            assertNoNewNotice(h,world,session,0,31L);
            world.at(738_000L,0);
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),32L),1,
                    "The crafted eye and day 32 independently release the stronghold notice");
            assertPacket(h,only(h,session.drain()),"end_portal",ChatFormatting.AQUA,32L);
            world.at(738_000L,StructureProgressData.ALL_CONDITIONS&~StructureProgressData.UNDERWORLD_VISITED);
            h.assertValueEqual(R196StructureGeneration.displayDay(world.level.getDayTime()),32L,"The ruined portal threshold starts on day32");
            assertNoNewNotice(h,world,session,StructureUnlockAnnouncements.END_PORTAL,32L);
            world.at(738_000L,StructureProgressData.ALL_CONDITIONS);
            int beforeOutpost=StructureUnlockAnnouncements.END_PORTAL|StructureUnlockAnnouncements.RUINED_PORTAL;
            h.assertValueEqual(world.available(),beforeOutpost,"Only the portal gate completes exactly on day32");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),32L),1,
                    "Completing both portal prerequisites sends exactly its new notice");
            assertPacket(h,only(h,session.drain()),"ruined_portal",ChatFormatting.GOLD,32L);
            world.at(1_049_999L,StructureProgressData.ALL_CONDITIONS);
            h.assertValueEqual(R196StructureGeneration.displayDay(world.level.getDayTime()),44L,"The last outpost-locked tick is day44");
            assertNoNewNotice(h,world,session,beforeOutpost,44L);
            world.at(1_050_000L,StructureProgressData.ALL_CONDITIONS&~StructureProgressData.BOW_CRAFTED);
            h.assertValueEqual(R196StructureGeneration.displayDay(world.level.getDayTime()),45L,"The outpost threshold starts on day45");
            assertNoNewNotice(h,world,session,beforeOutpost,45L);
            world.at(1_050_000L,StructureProgressData.ALL_CONDITIONS&~StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED);
            assertNoNewNotice(h,world,session,beforeOutpost,45L);
            world.at(1_050_000L,StructureProgressData.ALL_CONDITIONS);
            h.assertValueEqual(world.available(),beforeOutpost|StructureUnlockAnnouncements.PILLAGER_OUTPOST,
                    "The outpost snapshot uses the real date, bow and mithril generation prerequisites");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),45L),1,
                    "Completing all three outpost prerequisites sends exactly its new notice");
            assertPacket(h,only(h,session.drain()),"pillager_outpost",ChatFormatting.RED,45L);
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void nativeNetherPortalRemainsEligibleWithoutAnOverworldPortalSet(GameTestHelper h) throws IllegalAccessException {
        ServerLevel nether=h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether!=null,"The native nether dimension is loaded");
        boolean hasNetherPortal=nether.getChunkSource().getGeneratorState().possibleStructureSets().stream()
                .flatMap(set->set.value().structures().stream()).anyMatch(entry->entry.structure().unwrapKey()
                        .map(key->key.location().equals(ResourceLocation.withDefaultNamespace("ruined_portal_nether"))).orElse(false));
        h.assertTrue(hasNetherPortal,"The native nether generator includes its actual ruined portal structure");
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            int withPortalCount=world.generator.possibleStructureSets().size();
            var withoutPortals=world.generator.possibleStructureSets().stream().filter(set->set.value().structures().stream()
                    .noneMatch(entry->entry.structure().unwrapKey().map(key->{
                        var id=key.location();
                        return id.getNamespace().equals("minecraft")
                                &&(id.getPath().equals("ruined_portal")||id.getPath().startsWith("ruined_portal_"));
                    }).orElse(false))).toList();
            world.structureSetsField.set(world.generator,withoutPortals);
            h.assertTrue(world.generator.possibleStructureSets().size()<withPortalCount,
                    "The actual overworld portal structure sets were removed from the scoped fixture");
            h.assertTrue((StructureUnlockAnnouncements.enabled(world.level,world.enabledOptions())
                    &StructureUnlockAnnouncements.RUINED_PORTAL)!=0,"A native nether portal enables the shared notice independently of overworld sets");
            session.drain();world.at(0L,0);
            assertNoNewNotice(h,world,session,0,1L);
            world.at(737_999L,StructureProgressData.UNDERWORLD_VISITED);
            assertNoNewNotice(h,world,session,0,31L);
            world.at(738_000L,0);
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),32L),1,"The completed eye releases only the stronghold at day32");
            assertPacket(h,only(h,session.drain()),"end_portal",ChatFormatting.AQUA,32L);
            world.at(738_000L,StructureProgressData.UNDERWORLD_VISITED);
            h.assertValueEqual(world.available(),StructureUnlockAnnouncements.END_PORTAL|StructureUnlockAnnouncements.RUINED_PORTAL,
                    "Only a native nether portal set is enough once the shared date and visit gates are complete");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),32L),1,
                    "Nether-only eligibility delivers the new shared portal notice");
            assertPacket(h,only(h,session.drain()),"ruined_portal",ChatFormatting.GOLD,32L);
            var disabled=new WorldOptions(world.level.getSeed(),false,false);
            h.assertValueEqual(StructureUnlockAnnouncements.enabled(world.level,disabled),0,"Disabled structure options hide even native nether portal sets");
            h.assertValueEqual(StructureUnlockAnnouncements.available(world.level,disabled),0,"Disabled generation suppresses the unlocked nether portal notice");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void oldVersionOneMarkerReceivesOnlyTheTwoNewNoticesOnce(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            world.at(1_410_000L,StructureProgressData.ALL_CONDITIONS);session.drain();
            CompoundTag notices=new CompoundTag();notices.putInt("version",1);notices.putInt("seen",3);
            CompoundTag persisted=session.player().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            persisted.put(StructureUnlockAnnouncements.DATA_KEY,notices);
            session.player().getPersistentData().put(Player.PERSISTED_NBT_TAG,persisted);
            h.assertValueEqual(BOTH,3,"Existing village and end portal bit assignments remain save compatible");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),60L),2,
                    "An existing version1 player receives only the newly introduced notices");
            List<ClientboundSystemChatPacket> packets=session.drain();
            h.assertValueEqual(packets.size(),2,"The old notices are not replayed during upgrade");
            assertPacket(h,packets.get(0),"ruined_portal",ChatFormatting.GOLD,60L);
            assertPacket(h,packets.get(1),"pillager_outpost",ChatFormatting.RED,60L);
            h.assertValueEqual(marker(session.player()).getInt("version"),1,"Adding notice bits retains the compatible schema version");
            h.assertValueEqual(marker(session.player()).getInt("seen"),ALL,"The upgraded marker retains old bits and records both new bits");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),ALL,60L),0,"Upgraded players never repeat either new notice");
            h.assertTrue(session.drain().isEmpty(),"The upgrade sends each new native chat packet only once");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void allTwentyOneStructureNoticesSendOnceAndSurviveNativeRelogin(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            world.explorationState.complete();session.drain();
            int allStructures=StructureAdvancements.ALL_ENABLED;
            h.assertValueEqual(Integer.bitCount(allStructures),21,"The trusted snapshot covers all twenty-one structure families");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),allStructures,60L),21,
                    "Every unlocked structure family sends its own first chat notice");
            List<ClientboundSystemChatPacket> packets=session.drain();
            h.assertValueEqual(packets.size(),21,"All twenty-one notices reach the actual player connection");
            assertAll(h,packets.subList(0,4),60L);
            assertExplorationPackets(h,packets.subList(4,packets.size()),60L);
            h.assertValueEqual(marker(session.player()).getInt("seen"),allStructures,
                    "The persistent marker records every delivered structure family");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),allStructures,60L),0,
                    "A repeated complete snapshot sends no duplicate notice");
            h.assertTrue(session.drain().isEmpty(),"Repeated delivery leaves the native chat connection empty");
            CompoundTag saved=session.player().saveWithoutId(new CompoundTag());
            h.assertValueEqual(saved.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG)
                    .getCompound(StructureUnlockAnnouncements.DATA_KEY).getInt("seen"),allStructures,
                    "Native player saving includes all twenty-one notice bits");
            world.at(0L,0);
            session.reconnect();
            h.assertValueEqual(marker(session.player()).getInt("seen"),allStructures,
                    "The actual player-file relogin preserves every exploration notice");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),allStructures,1L),0,
                    "Relogin and calendar rollback never replay saved exploration notices");
            h.assertTrue(session.drain().isEmpty(),"The reconnected player receives no repeated structure chat");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void existingFourNoticeMarkerReceivesAllSeventeenExplorationNoticesOnce(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            world.explorationState.complete();session.drain();
            CompoundTag notices=new CompoundTag();notices.putInt("version",1);notices.putInt("seen",15);
            CompoundTag persisted=session.player().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            persisted.put(StructureUnlockAnnouncements.DATA_KEY,notices);
            session.player().getPersistentData().put(Player.PERSISTED_NBT_TAG,persisted);
            h.assertValueEqual(ALL,15,"The four existing notice bits retain their saved assignments");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),StructureAdvancements.ALL_ENABLED,60L),17,
                    "Existing players receive every newly supported exploration notice");
            assertExplorationPackets(h,session.drain(),60L);
            h.assertValueEqual(marker(session.player()).getInt("version"),1,"Exploration notices retain the compatible marker schema");
            h.assertValueEqual(marker(session.player()).getInt("seen"),StructureAdvancements.ALL_ENABLED,
                    "The upgraded player retains the original bits alongside all seventeen new notices");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),StructureAdvancements.ALL_ENABLED,60L),0,
                    "An upgraded player receives each exploration notice once");
            h.assertTrue(session.drain().isEmpty(),"Upgrade delivery never replays the four original notices");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void actualFortressDistanceGateSendsOneLocalizedChatAtTheCompletedThreshold(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            int fortress=StructureAdvancements.Entry.FORTRESS.enabledBit();
            h.assertTrue((StructureUnlockAnnouncements.enabled(world.level,world.enabledOptions())&fortress)!=0,
                    "The real Nether generator includes its fortress structure set");
            world.explorationState.values(java.util.Map.of(Metric.NETHER_WALK_CM,50_000L));
            world.at(738_000L,0);session.drain();
            assertNoNewNotice(h,world,session,0,32L);
            world.explorationState.values(java.util.Map.of(Metric.NETHER_WALK_CM,50_001L));
            h.assertValueEqual(world.available(),fortress,"Completing strictly over five hundred Nether blocks releases only the fortress");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),32L),1,
                    "The newly completed fortress gate sends one real chat packet");
            assertExplorationPacket(h,only(h,session.drain()),StructureAdvancements.Entry.FORTRESS,32L);
            h.assertValueEqual(marker(session.player()).getInt("seen"),fortress,
                    "Only the delivered fortress notice is consumed for this player");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),32L),0,
                    "An unchanged completed fortress gate cannot repeat its notice");
            h.assertTrue(session.drain().isEmpty(),"The next fortress snapshot leaves chat empty");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void disabledWorldNativeLoginAndAnnouncementTickSendNoFalseUnlocks(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            h.assertFalse(world.level.getServer().getWorldData().worldGenOptions().generateStructures(),
                    "The actual GameTest world keeps its original disabled structure-generation option");
            world.at(1_410_000L,StructureProgressData.ALL_CONDITIONS);
            h.assertTrue(session.drain().isEmpty(),"Actual PlayerList login did not claim disabled structures can generate");
            h.assertValueEqual(StructureUnlockAnnouncements.announce(session.player()),0,
                    "The production announce entry reads disabled real WorldOptions");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(session.player()));
            // Invoke only this subscriber: posting a world tick would run unrelated full-world fixture logic.
            StructureUnlockAnnouncements.tick(new LevelTickEvent.Post(()->true,world.level));
            h.assertTrue(session.drain().isEmpty(),"Registered login and the production tick subscriber remain silent");
            h.assertFalse(session.player().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                    .contains(StructureUnlockAnnouncements.DATA_KEY),"A disabled world does not consume any of the player's four future notices");
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
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),1<<30,1L),0,
                    "Unknown snapshot bits cannot send or consume a structure notice");
            world.at(738_000L,0);
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),32L),1,"The first eligible structure sends one packet");
            assertPacket(h,only(h,session.drain()),"end_portal",ChatFormatting.AQUA,32L);
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
        lateJoiningDifferentUuidReceivesItsOwnFourNotices(h);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void actualPlayerFileReloginRetainsAllFourNoticesEvenAfterTimeRollback(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            world.at(1_410_000L,StructureProgressData.ALL_CONDITIONS);session.drain();
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),60L),4,
                    "A first login after all milestones delivers every current eligibility fact");
            assertAll(h,session.drain(),60L);
            ServerPlayer before=session.player();
            CompoundTag saved=before.saveWithoutId(new CompoundTag());
            CompoundTag savedNotice=saved.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG)
                    .getCompound(StructureUnlockAnnouncements.DATA_KEY);
            h.assertValueEqual(savedNotice.getInt("version"),1,"Actual native player NBT contains the notice schema");
            h.assertValueEqual(savedNotice.getInt("seen"),ALL,"Actual native player NBT contains all four delivered bits");
            world.at(0L,0);
            session.reconnect();
            h.assertTrue(session.player()!=before&&session.player().getUUID().equals(before.getUUID()),
                    "PlayerList.remove and placeNewPlayer load a new object from the same UUID's actual player file");
            h.assertValueEqual(marker(session.player()).getInt("seen"),ALL,"Real disconnect saving and login loading retain the notice marker");
            h.assertTrue(session.drain().isEmpty(),"Real relogin after time rollback does not repeat notices");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),ALL,1L),0,
                    "Even a subsequently eligible full snapshot cannot repeat saved notices");
            h.assertTrue(session.drain().isEmpty(),"The restored player's active connection receives no duplicate packet");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void realDeathAndNativeRespawnCopyPlayerPersistedWithoutAnotherNotice(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session session=new Session(h)) {
            world.at(1_410_000L,StructureProgressData.ALL_CONDITIONS);session.drain();
            StructureUnlockAnnouncements.deliver(session.player(),world.available(),60L);
            assertAll(h,session.drain(),60L);
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
            h.assertValueEqual(marker(replacement).getInt("seen"),ALL,"Native death cloning retains all four delivered notice bits");
            h.assertTrue(session.drain().isEmpty(),"The actual death and native respawn lifecycle itself sends no duplicate structure packet");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(replacement,ALL,60L),0,
                    "A living native replacement does not replay any notice");
            h.assertTrue(session.drain().isEmpty(),"Native respawn cannot duplicate structure chat");
        }
        h.succeed();
    }

    private static void lateJoiningDifferentUuidReceivesItsOwnFourNotices(GameTestHelper h) {
        try (WorldState world=new WorldState(h); Session first=new Session(h); Session late=new Session(h)) {
            world.at(1_410_000L,StructureProgressData.ALL_CONDITIONS);first.drain();late.drain();
            h.assertFalse(first.player().getUUID().equals(late.player().getUUID()),"The two real connected players have different identities");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(first.player(),world.available(),60L),4,"First player receives all four eligible notices");
            assertAll(h,first.drain(),60L);
            h.assertFalse(marker(late.player()).contains("seen"),"One player's delivery does not consume the other player's eligibility");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(late.player(),world.available(),60L),4,
                    "A late player independently receives all four already-available structure notices");
            assertAll(h,late.drain(),60L);
            h.assertTrue(first.drain().isEmpty(),"The late player's personal notices are not broadcast to the earlier player");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(late.player(),ALL,60L),0,"The late player's own duplicate is suppressed");
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
            h.assertValueEqual(StructureUnlockAnnouncements.available(nether,world.enabledOptions()),0,
                    "A locally late dimension cannot unlock overworld villages while overworld prerequisites are missing");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),1L),0,
                    "The connected off-world player cannot bypass the overworld stronghold date");
            h.assertTrue(session.drain().isEmpty(),"No off-world packet is sent before the shared date");
            world.at(1_410_000L,StructureProgressData.ALL_CONDITIONS);nether.setDayTime(0L);
            h.assertValueEqual(StructureUnlockAnnouncements.available(nether,world.enabledOptions()),ALL,
                    "A locally early dimension cannot hide any overworld structure eligibility");
            h.assertValueEqual(StructureUnlockAnnouncements.available(underworld,world.enabledOptions()),ALL,
                    "The same shared overworld generator and progress are used for the underworld");
            h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),60L),4,
                    "All four newly eligible facts reach the off-world player");
            List<ClientboundSystemChatPacket> packets=session.drain();
            assertAll(h,packets,60L);
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
    private static void assertNoNewNotice(GameTestHelper h,WorldState world,Session session,int expected,long day) {
        h.assertValueEqual(world.available(),expected,"An incomplete actual generation gate is absent from the announcement snapshot");
        h.assertValueEqual(StructureUnlockAnnouncements.deliver(session.player(),world.available(),day),0,
                "Missing any required date or progress prerequisite sends no premature unlock");
        h.assertTrue(session.drain().isEmpty(),"No native structure chat packet is sent before the complete gate");
    }
    private static void assertAll(GameTestHelper h,List<ClientboundSystemChatPacket> packets,long day) {
        h.assertValueEqual(packets.size(),4,"Exactly all four independent native structure packets were sent");
        assertPacket(h,packets.get(0),"end_portal",ChatFormatting.AQUA,day);
        assertPacket(h,packets.get(1),"village",ChatFormatting.GREEN,day);
        assertPacket(h,packets.get(2),"ruined_portal",ChatFormatting.GOLD,day);
        assertPacket(h,packets.get(3),"pillager_outpost",ChatFormatting.RED,day);
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
    private static void assertExplorationPackets(GameTestHelper h,List<ClientboundSystemChatPacket> packets,long day) {
        h.assertValueEqual(packets.size(),17,"Exactly seventeen independent exploration chat packets were sent");
        int index=0;
        for (var entry:StructureAdvancements.Entry.values()) {
            if ((entry.enabledBit()&ALL)==0) assertExplorationPacket(h,packets.get(index++),entry,day);
        }
    }
    private static void assertExplorationPacket(GameTestHelper h,ClientboundSystemChatPacket packet,StructureAdvancements.Entry entry,long day) {
        h.assertFalse(packet.overlay(),"Exploration unlocks appear in ordinary chat");
        h.assertValueEqual(packet.content().getStyle().getColor(),TextColor.fromLegacyFormat(ChatFormatting.AQUA),
                "Exploration unlock chat carries its intended color");
        h.assertTrue(packet.content().getContents() instanceof TranslatableContents,"Exploration chat is localized on the client");
        var content=(TranslatableContents)packet.content().getContents();
        h.assertValueEqual(content.getKey(),PREFIX+"exploration","The generic exploration message is used for each new family");
        h.assertValueEqual(content.getArgs().length,2,"Exploration notices carry the display day and structure title");
        h.assertTrue(content.getArgs()[0] instanceof Number&&((Number)content.getArgs()[0]).longValue()==day,
                "Exploration packets retain the authoritative display day");
        h.assertTrue(content.getArgs()[1] instanceof Component,"Structure titles remain localized components inside the chat packet");
        Component title=(Component)content.getArgs()[1];
        h.assertTrue(title.getContents() instanceof TranslatableContents,"The structure title is translated by the receiving client");
        h.assertValueEqual(((TranslatableContents)title.getContents()).getKey(),"advancement.mite.structures."+entry.path()+".title",
                "Each exploration packet names its exact unlocked structure family");
    }

    private static final class WorldState implements AutoCloseable {
        final ServerLevel level;
        final StructureProgressData progress;
        final long dayTime;
        final int conditions;
        final ChunkGeneratorStructureState generator;
        final Field structureSetsField;
        final List<Holder<StructureSet>> originalStructureSets;
        final StructureUnlockTestState explorationState;
        WorldState(GameTestHelper h) {
            level=h.getLevel().getServer().overworld();progress=StructureProgressData.get(level);
            dayTime=level.getDayTime();conditions=progress.conditions();
            explorationState=new StructureUnlockTestState(level);
            explorationState.values(java.util.Map.of(Metric.EYE_CRAFTED,1L));
            generator=level.getChunkSource().getGeneratorState();
            originalStructureSets=generator.possibleStructureSets();
            // The native flat GameTest preset includes villages/strongholds only. Temporarily enable the
            // real registered portal/outpost sets so production availability can exercise their gates.
            var enabled=new ArrayList<>(originalStructureSets);
            var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
            for (String name:List.of("ruined_portals","pillager_outposts")) {
                Holder<StructureSet> set=registry.getHolder(ResourceLocation.withDefaultNamespace(name)).orElseThrow();
                if (!enabled.contains(set)) enabled.add(set);
            }
            try {
                structureSetsField=ChunkGeneratorStructureState.class.getDeclaredField("possibleStructureSets");
                structureSetsField.setAccessible(true);
                structureSetsField.set(generator,List.copyOf(enabled));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot scope the registered structure-set fixture",e);
            }
        }
        WorldOptions enabledOptions() { return new WorldOptions(level.getSeed(),true,false); }
        int available() { return StructureUnlockAnnouncements.available(level,enabledOptions()); }
        void at(long time,int bits) { progress.restoreConditions(bits);level.setDayTime(time);R196StructureGeneration.refresh(level); }
        @Override public void close() {
            try {
                structureSetsField.set(generator,originalStructureSets);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot restore the original structure sets",e);
            } finally {
                explorationState.close();
            }
        }
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
