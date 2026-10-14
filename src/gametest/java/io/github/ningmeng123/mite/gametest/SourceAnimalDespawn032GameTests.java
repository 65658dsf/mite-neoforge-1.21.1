package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.animal.SourceAnimalDespawn032;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.ranged.ArrowRecovery;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent og/rp/sf/rx/nn literals checked through native persistence, rays and the checkDespawn hook. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class SourceAnimalDespawn032GameTests {
    private static final BlockPos ORIGIN=new BlockPos(1,10,1);
    private static <T extends Mob> T mob(GameTestHelper h,EntityType<T> type) {
        T mob=type.create(h.getLevel()); var pos=h.absolutePos(ORIGIN); mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        mob.setNoAi(true); mob.setNoGravity(true); h.getLevel().addFreshEntity(mob); return mob;
    }
    private static void far(Mob mob) { mob.moveTo(mob.getX()+2000D,mob.getY(),mob.getZ(),0,0); }
    private static Session player(GameTestHelper h) {
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mite-despawn-032"),false);
        ServerPlayer player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.CREATIVE); player.setNoGravity(true);
        var pos=h.absolutePos(ORIGIN); player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        return new Session(player,channel);
    }
    private record Session(ServerPlayer player,EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() { try { player.server.getPlayerList().remove(player); } finally { channel.finishAndReleaseAll(); } }
    }
    @GameTest(template="empty") public static void onlyConfirmedSourceAnimalIdentitiesReplaceDespawn(GameTestHelper h) {
        for(var type:new EntityType<?>[]{EntityType.COW,EntityType.WOLF,EntityType.OCELOT,EntityType.CAT,EntityType.BAT,EntityType.SQUID,
                MiteMobs.type(MobKind.HELLHOUND),MiteMobs.type(MobKind.VAMPIRE_BAT),MiteMobs.type(MobKind.GIANT_VAMPIRE_BAT),MiteMobs.type(MobKind.NIGHTWING)}) {
            var e=(Mob)type.create(h.getLevel()); h.assertTrue(SourceAnimalDespawn032.managed(e),"Confirmed source animal "+type); e.discard();
        }
        for(var type:new EntityType<?>[]{EntityType.ZOMBIE,EntityType.RABBIT,EntityType.GOAT,EntityType.GLOW_SQUID}) {
            var e=(Mob)type.create(h.getLevel()); h.assertFalse(SourceAnimalDespawn032.managed(e),"Monster or modern extra keeps native despawn "+type); e.discard();
        } h.succeed();
    }
    @GameTest(template="empty") public static void rawEntityTicksAndNegativeCounterAreSaved(GameTestHelper h) {
        var bat=mob(h,EntityType.BAT); SourceAnimalDespawn032.counter(bat,-401);
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(bat)); h.assertValueEqual(SourceAnimalDespawn032.counter(bat),-400,"Raw Pre advances saved counter once independent of NoAI");
        var saved=new CompoundTag(); bat.saveWithoutId(saved);var restored=EntityType.BAT.create(h.getLevel());restored.load(saved);
        h.assertValueEqual(SourceAnimalDespawn032.counter(restored),-400,"Actual entity NBT retains negative grace");bat.discard();restored.discard();h.succeed();
    }
    @GameTest(template="empty") public static void refreshOnlyLowersExistingDespawnCounter(GameTestHelper h) {
        var squid=mob(h,EntityType.SQUID); SourceAnimalDespawn032.counter(squid,400); SourceAnimalDespawn032.refresh(squid,-400);
        h.assertValueEqual(SourceAnimalDespawn032.counter(squid),-400,"Visible refresh lowers old400"); SourceAnimalDespawn032.refresh(squid,-9600);SourceAnimalDespawn032.refresh(squid,-1200);
        h.assertValueEqual(SourceAnimalDespawn032.counter(squid),-9600,"Damage refresh cannot shorten squid view grace");squid.discard();h.succeed();
    }
    @GameTest(template="empty") public static void originalHourFiveUsesNormalVerticalDistance(GameTestHelper h) {
        var squid=mob(h,EntityType.SQUID); var player=h.makeMockPlayer(GameType.CREATIVE);player.moveTo(squid.getX(),squid.getY()+9,squid.getZ(),0,0);
        long previous=h.getLevel().getDayTime();
        try {h.getLevel().setDayTime(23000);h.assertValueEqual(SourceAnimalDespawn032.sourceHour(23000),5,"Adjusted midnight source clock");
            h.assertTrue(SourceAnimalDespawn032.weightedDistance(squid,player)==81D,"Source hour5 leaves nine vertical blocks as81");h.getLevel().setDayTime(0);
            h.assertTrue(SourceAnimalDespawn032.weightedDistance(squid,player)==324D,"Other hours double dy before squaring");
            player.moveTo(squid.getX()+16,squid.getY(),squid.getZ(),0,0);h.assertTrue(SourceAnimalDespawn032.weightedDistance(squid,player)==256D,"Sixteen horizontal blocks stays exclusive boundary");}
        finally {h.getLevel().setDayTime(previous);squid.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void nativeFarAwayBatMustKeepSourceTwoHundredTickGrace(GameTestHelper h) {
        try(var session=player(h)) {
            var bat=mob(h,EntityType.BAT);far(bat);SourceAnimalDespawn032.counter(bat,199);bat.checkDespawn();
            h.assertFalse(bat.isRemoved(),"Real Mob.checkDespawn avoids modern128 block cull during source counter<200");
            h.assertValueEqual(SourceAnimalDespawn032.counter(bat),199,"Early protection does not random reset");SourceAnimalDespawn032.counter(bat,200);bat.checkDespawn();
            h.assertTrue(bat.isRemoved(),"At source200 invisible distant bat can disappear");
        } h.succeed();
    }
    @GameTest(template="empty") public static void livestockEightHundredAndTwentyFourHundredBoundariesAreStrict(GameTestHelper h) {
        try(var session=player(h)) {
            var cow=mob(h,EntityType.COW);far(cow);SourceAnimalDespawn032.counter(cow,799);h.assertFalse(SourceAnimalDespawn032.speciesAllows(cow),"rp protects below800");
            SourceAnimalDespawn032.counter(cow,800);h.assertFalse(SourceAnimalDespawn032.speciesAllows(cow),"rp also protects800 after no player/fence guard");
            SourceAnimalDespawn032.counter(cow,2400);h.assertFalse(SourceAnimalDespawn032.speciesAllows(cow),"rp protects inclusive2400");
            SourceAnimalDespawn032.counter(cow,2401);h.assertTrue(SourceAnimalDespawn032.speciesAllows(cow),"Invisible unfenced source livestock counter2401 permits despawn");cow.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void sourcePlayerProtectionIsStrictlyInsideOneThousandTwentyFour(GameTestHelper h) {
        try(var session=player(h)) {
            var cow=mob(h,EntityType.COW);far(cow);session.player.moveTo(cow.getX()-1024,cow.getY(),cow.getZ(),0,0);SourceAnimalDespawn032.counter(cow,2401);
            h.assertTrue(SourceAnimalDespawn032.speciesAllows(cow),"Exactly1024 does not meet abw strict max distance");session.player.moveTo(cow.getX()-1023.5,cow.getY(),cow.getZ(),0,0);
            h.assertFalse(SourceAnimalDespawn032.speciesAllows(cow),"Inside1024 protects even creative player");h.assertValueEqual(SourceAnimalDespawn032.counter(cow),0,"rp player protection sets zero");cow.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void onlyOakFenceWithinSixteenByOneIsSourceProtection(GameTestHelper h) {
        var cow=mob(h,EntityType.COW);BlockPos inside=cow.blockPosition().offset(16,1,16),outside=cow.blockPosition().offset(17,1,16);
        h.getLevel().getChunk(inside);var original=h.getLevel().getBlockState(inside);var outerOriginal=h.getLevel().getBlockState(outside);
        try {h.getLevel().setBlock(inside,Blocks.BIRCH_FENCE.defaultBlockState(),3);h.assertFalse(SourceAnimalDespawn032.oakFenceNear(cow),"Only sourceID85 oak fence counts");
            h.getLevel().setBlock(inside,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(outside,Blocks.OAK_FENCE.defaultBlockState(),3);h.assertFalse(SourceAnimalDespawn032.oakFenceNear(cow),"Horizontal17 is outsideinclusive16");
            h.getLevel().setBlock(inside,Blocks.OAK_FENCE.defaultBlockState(),3);h.assertTrue(SourceAnimalDespawn032.oakFenceNear(cow),"Corner16/1/16 remains inside source square");}
        finally {h.getLevel().setBlock(inside,original,3);h.getLevel().setBlock(outside,outerOriginal,3);cow.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void wolfRawAgeAndWitchAllyProtectBeforeInheritedAnimalGuards(GameTestHelper h) {
        try(var session=player(h)) {
            Wolf wolf=mob(h,EntityType.WOLF);far(wolf);SourceAnimalDespawn032.counter(wolf,2401);wolf.tickCount=2400;
            h.assertFalse(SourceAnimalDespawn032.speciesAllows(wolf),"sf raw age2400 protects independently of saved counter");wolf.tickCount=2401;
            h.assertTrue(SourceAnimalDespawn032.speciesAllows(wolf),"sf old wild wolf proceeds to rp and og");wolf.getPersistentData().putBoolean("mite_wolf_witch_ally_032",true);
            h.assertFalse(SourceAnimalDespawn032.speciesAllows(wolf),"Witch ally never permits species despawn");wolf.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void hellhoundBypassesBothInheritedRawAgeAndLivestockCounterGates(GameTestHelper h) {
        try(var session=player(h)) {
            var hell=mob(h,MiteMobs.type(MobKind.HELLHOUND));far(hell);hell.tickCount=1;SourceAnimalDespawn032.counter(hell,200);
            h.assertTrue(SourceAnimalDespawn032.speciesAllows(hell),"Source th Hellhound bypasses sf2400 and rp800/2400 guards");hell.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void ocelotRawAgeAndModernTamedCatProtect(GameTestHelper h) {
        try(var session=player(h)) {
            var cat=mob(h,EntityType.OCELOT);far(cat);SourceAnimalDespawn032.counter(cat,2401);cat.tickCount=2400;
            h.assertFalse(SourceAnimalDespawn032.speciesAllows(cat),"rx raw2400 protected");cat.tickCount=2401;h.assertTrue(SourceAnimalDespawn032.speciesAllows(cat),"rx raw2401 can reach inherited guards");cat.discard();
            Cat tame=mob(h,EntityType.CAT);tame.setTame(true,true);SourceAnimalDespawn032.counter(tame,5000);tame.checkDespawn();
            h.assertFalse(tame.isRemoved(),"Modern split tamed source cat retained");h.assertValueEqual(SourceAnimalDespawn032.counter(tame),0,"Owned modern persistence clears counter");tame.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void sourceViewUsesSightRatherThanPlayerFacing(GameTestHelper h) {
        var bat=mob(h,EntityType.BAT);var mock=h.makeMockPlayer(GameType.CREATIVE);mock.moveTo(bat.getX()+10,bat.getY(),bat.getZ(),-90,0);
        h.assertTrue(SourceAnimalDespawn032.inView(bat,mock),"An unobstructed backwards-facing creative player still sees despawn protection");
        mock.moveTo(bat.getX()+65,bat.getY(),bat.getZ(),-90,0);h.assertFalse(SourceAnimalDespawn032.inView(bat,mock),"Small bat beyond64 and source rendering extent is not visible");
        var spectator=h.makeMockPlayer(GameType.SPECTATOR);spectator.moveTo(bat.getX()+1,bat.getY(),bat.getZ(),0,0);h.assertFalse(SourceAnimalDespawn032.inView(bat,spectator),"Spectator maps source ghost exclusion");bat.discard();h.succeed();
    }
    @GameTest(template="empty") public static void glassAndOneLeafAreTransparentButSecondLeafAndStoneBlockVision(GameTestHelper h) {
        var bat=mob(h,EntityType.BAT);var p=bat.blockPosition();var a=p.offset(2,0,0);var b=p.offset(3,0,0);var originalA=h.getLevel().getBlockState(a);var originalB=h.getLevel().getBlockState(b);
        Vec3 origin=Vec3.atCenterOf(p),target=origin.add(6,0,0);
        try {h.getLevel().setBlock(a,Blocks.GLASS.defaultBlockState(),3);h.assertTrue(SourceAnimalDespawn032.visionRay(bat,origin,target),"R196 vision ignoresglass unlike physical collider");
            h.getLevel().setBlock(a,Blocks.OAK_LEAVES.defaultBlockState(),3);h.assertTrue(SourceAnimalDespawn032.visionRay(bat,origin,target),"First leaf adds50, remains transparent");
            h.getLevel().setBlock(b,Blocks.OAK_LEAVES.defaultBlockState(),3);h.assertFalse(SourceAnimalDespawn032.visionRay(bat,origin,target),"Second leaf reaches100 and blocks");
            h.getLevel().setBlock(a,Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(b,Blocks.AIR.defaultBlockState(),3);h.assertFalse(SourceAnimalDespawn032.visionRay(bat,origin,target),"Opaque source wall blocks source vision");}
        finally {h.getLevel().setBlock(a,originalA,3);h.getLevel().setBlock(b,originalB,3);bat.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void speciesViewRefreshAndOuterAttemptResetKeepActualSourceOrdering(GameTestHelper h) {
        try(var session=player(h)) {
            var squid=mob(h,EntityType.SQUID);session.player.moveTo(squid.getX()+20,squid.getY(),squid.getZ(),0,0);SourceAnimalDespawn032.counter(squid,500);
            h.assertFalse(SourceAnimalDespawn032.speciesAllows(squid),"Open squid is inview");h.assertValueEqual(SourceAnimalDespawn032.counter(squid),-9600,"se squid inview lowers to-9600");
            SourceAnimalDespawn032.counter(squid,500);SourceAnimalDespawn032.tryDespawn(squid);int counter=SourceAnimalDespawn032.counter(squid);
            h.assertTrue(!squid.isRemoved() && counter>=-50 && counter<=49,"og failedattempt overwrites the viewrefresh with literal-50..49");squid.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void retainedContainedItemsPreventInvisibleAnimalLoss(GameTestHelper h) {
        try(var session=player(h)) {
            var bat=mob(h,EntityType.BAT);far(bat);SourceAnimalDespawn032.counter(bat,200);SourceAnimalDespawn032.state(bat).putIntArray("contained_items",new int[]{37});
            h.assertFalse(SourceAnimalDespawn032.tryDespawn(bat),"Source contained items block discard before species/view");h.assertFalse(bat.isRemoved(),"Contents retained with animal");
            var saved=new CompoundTag();bat.saveWithoutId(saved);var other=EntityType.BAT.create(h.getLevel());other.load(saved);h.assertTrue(SourceAnimalDespawn032.containsItems(other),"Contents guard persists with entity");bat.discard();other.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void modernNamedAndTamedHorsePersistenceIsRetained(GameTestHelper h) {
        var named=mob(h,EntityType.SQUID);named.setCustomName(Component.literal("saved squid"));SourceAnimalDespawn032.counter(named,5000);named.checkDespawn();
        h.assertTrue(!named.isRemoved() && SourceAnimalDespawn032.counter(named)==0,"Modern named preservation resets source timer");named.discard();
        var horse=mob(h,EntityType.HORSE);horse.setTamed(true);SourceAnimalDespawn032.counter(horse,5000);horse.checkDespawn();
        h.assertTrue(!horse.isRemoved() && SourceAnimalDespawn032.counter(horse)==0,"Explicit modern tamedhorse protection retained beyond source rs");horse.discard();h.succeed();
    }
    @GameTest(template="empty") public static void actualContainedArrowAttachmentAlsoPreventsSourceDespawn(GameTestHelper h) {
        try(var session=player(h)) {
            var bat=mob(h,EntityType.BAT);far(bat);ArrowRecovery.contain(bat,new ItemStack(Items.ARROW));SourceAnimalDespawn032.counter(bat,200);
            h.assertTrue(SourceAnimalDespawn032.containsItems(bat),"Existing arrow recovery attachment is actual source contained inventory");
            h.assertFalse(SourceAnimalDespawn032.tryDespawn(bat),"Arrow carrying bat cannot disappear and lose recoverable arrow");
            var saved=new CompoundTag();bat.saveWithoutId(saved);var other=EntityType.BAT.create(h.getLevel());other.load(saved);
            h.assertTrue(SourceAnimalDespawn032.containsItems(other),"Real arrow attachment keeps despawn guard after whole entity load");bat.discard();other.discard();
        }h.succeed();
    }
    @GameTest(template="empty") public static void batAndSquidDamageRefreshActualSourceGrace(GameTestHelper h) {
        var squid=mob(h,EntityType.SQUID);var attacker=mob(h,EntityType.COW);SourceAnimalDespawn032.counter(squid,300);SourceAnimalDespawn032.counter(attacker,300);
        squid.hurt(squid.damageSources().mobAttack(attacker),1);
        h.assertTrue(SourceAnimalDespawn032.counter(squid)==-1200 && SourceAnimalDespawn032.counter(attacker)==-1200,"DamagePost refreshes non-Animal squid and source animal attacker");squid.discard();attacker.discard();
        var bat=mob(h,EntityType.BAT);SourceAnimalDespawn032.counter(bat,300);var player=h.makeMockPlayer(GameType.CREATIVE);bat.hurt(bat.damageSources().playerAttack(player),1);
        h.assertValueEqual(SourceAnimalDespawn032.counter(bat),-9600,"Player attack extends ordinary bat grace beyond general-1200");bat.discard();h.succeed();
    }
    @GameTest(template="empty") public static void worldWithoutNearestPlayerKeepsAccumulatedSourceCounter(GameTestHelper h) {
        var end=h.getLevel().getServer().getLevel(Level.END);h.assertTrue(end!=null && end.players().isEmpty(),"Separate End fixture has no connected players");
        var squid=EntityType.SQUID.create(end);squid.setNoAi(true);SourceAnimalDespawn032.counter(squid,3000);squid.checkDespawn();
        h.assertTrue(!squid.isRemoved() && SourceAnimalDespawn032.counter(squid)==3000,"No nearest player returns before random reset or view checks");
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(squid));squid.checkDespawn();
        h.assertValueEqual(SourceAnimalDespawn032.counter(squid),3001,"Loaded raw ticks can continue accumulating without a player");squid.discard();h.succeed();
    }
}
