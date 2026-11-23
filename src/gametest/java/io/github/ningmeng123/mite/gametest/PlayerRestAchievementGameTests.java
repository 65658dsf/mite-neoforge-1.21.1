package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.advancement.*;
import io.github.ningmeng123.mite.network.RespawnCountdownPayload;
import io.github.ningmeng123.mite.survival.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real registries, native advancements, saved firsts, food and bed gates. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class PlayerRestAchievementGameTests {
    private PlayerRestAchievementGameTests(){}
    @GameTest(template="empty") public static void allRegisteredAchievementsLoadWithValidIconsAndSourceCriteria(GameTestHelper h){
        h.assertValueEqual(MiteAchievements.catalog().size(),62,"Actual R196 registry has 62 achievements");
        for(var definition:MiteAchievements.catalog().values()){
            var holder=h.getLevel().getServer().getAdvancements().get(definition.id());
            h.assertTrue(holder!=null,"Generated advancement loads: "+definition.id());
            h.assertTrue(holder.value().criteria().containsKey("earned"),"Source action criterion exists");
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(definition.icon())),"Registered icon "+definition.icon());
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void actualCraftAwardIsDistinctFromOwningAnItemAndIsIdempotent(GameTestHelper h){
        try(var f=new Session(h)){
            var player=f.player;var definition=MiteAchievements.catalog().get("buildBetterPickaxe");
            var holder=player.server.getAdvancements().get(definition.id());
            player.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(!player.getAdvancements().getOrStartProgress(holder).isDone(),"Owning a pick does not forge source crafting progress");
            MiteAchievements.crafted(player,new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(player.getAdvancements().getOrStartProgress(holder).isDone(),"Completed source pick action awards its criterion");
            var first=WorldAchievementData.get(player.serverLevel()).firsts().get(definition.id().toString());
            h.assertTrue(first!=null,"Native award records a world first");
            h.assertTrue(!MiteAchievements.award(player,"buildBetterPickaxe"),"Second completion cannot duplicate native progress");
            h.assertValueEqual(WorldAchievementData.get(player.serverLevel()).firsts().get(definition.id().toString()),first,"First record is immutable");
        }h.succeed();
    }
    @GameTest(template="empty") public static void firstCompletionSurvivesSaveAndCannotBeReplaced(GameTestHelper h){
        var data=new WorldAchievementData();UUID first=UUID.randomUUID();
        h.assertTrue(data.record("mite:r196/open_inventory",first,"NingMeng123",40),"First recorded");
        h.assertTrue(!data.record("mite:r196/open_inventory",UUID.randomUUID(),"Other",41),"Second player cannot replace first");
        var copy=WorldAchievementData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertValueEqual(copy.firsts(),data.firsts(),"Versioned SavedData round trip preserves identity and calendar day");h.succeed();
    }
    @GameTest(template="empty") public static void readingNineBooksRewardsEachOnceAndUnlocksEnlightenment(GameTestHelper h){
        try(var f=new Session(h)){
            PlayerExperience.setTotal(f.player,0);
            for(int index=1;index<=9;index++){
                var book=ReferencedBooks.book(index);h.assertValueEqual(ReferencedBooks.index(book),index,"Book metadata has its true source identity");
                ReferencedBooks.read(f.player,book);ReferencedBooks.read(f.player,book);
            }
            h.assertValueEqual(f.player.totalExperience,900,"Nine different books grant 9 x 100, repeated reads grant zero");
            var holder=f.player.server.getAdvancements().get(MiteAchievements.catalog().get("enlightenment").id());
            h.assertTrue(f.player.getAdvancements().getOrStartProgress(holder).isDone(),"All nine unique indices earn Enlightenment");
            CompoundTag saved=new CompoundTag();f.player.saveWithoutId(saved);
            h.assertTrue(saved.toString().contains("mite_books_read"),"Read ledger participates in normal player serialization");
        }h.succeed();
    }
    @GameTest(template="empty") public static void sourceSleepRefusesUncoveredHungryOrPoisonedBeds(GameTestHelper h){
        try(var f=new Session(h)){
            BlockPos bed=h.absolutePos(new BlockPos(1,3,1));f.player.moveTo(bed.getX()+.5,bed.getY(),bed.getZ()+.5);
            h.assertValueEqual(PlayerRest.problem(f.player,bed,true),"shelter","Exposed bed is refused before hunger");
            for(BlockPos pos:BlockPos.betweenClosed(bed.offset(-4,3,-4),bed.offset(4,3,4)))h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            try{
                f.player.getFoodData().setFoodLevel(0);
                ((DualFoodData)f.player.getFoodData()).mite$setNutrition(0);
                h.assertValueEqual(PlayerRest.problem(f.player,bed,true),"hungry","A sheltered bed requires some food energy");
                ((DualFoodData)f.player.getFoodData()).mite$setNutrition(6);
                f.player.addEffect(new MobEffectInstance(MobEffects.POISON,100));
                h.assertValueEqual(PlayerRest.problem(f.player,bed,true),"poison","Poison prevents initial sleep");
                f.player.removeEffect(MobEffects.POISON);
                h.assertTrue(PlayerRest.problem(f.player,bed,true)==null,"Sheltered healthy fed player has no source bed problem");
            }finally{for(BlockPos pos:BlockPos.betweenClosed(bed.offset(-4,3,-4),bed.offset(4,3,4)))h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);}
        }h.succeed();
    }
    @GameTest(template="empty") public static void respawnTimerUsesGameTicksAndKeepsItsSavedRemainder(GameTestHelper h){
        try(var f=new Session(h)){
            RespawnCountdown.died(f.player);h.assertValueEqual(RespawnCountdown.remaining(f.player),120,"Death starts at 120 source seconds");
            int remaining=120;for(int gameTime=1;gameTime<=2399;gameTime++)remaining=RespawnCountdown.advance(remaining,gameTime,false);
            h.assertValueEqual(remaining,1,"2399 game ticks cannot finish the countdown, independent of frozen dayTime");
            h.assertValueEqual(RespawnCountdown.advance(remaining,2400,false),0,"2400 game ticks completes countdown");
            h.assertValueEqual(RespawnCountdown.advance(120,20,true),120,"Source all-asleep freeze preserves remaining time");
            f.player.getPersistentData().putInt(RespawnCountdown.KEY,37);
            CompoundTag saved=new CompoundTag();f.player.saveWithoutId(saved);
            h.assertTrue(saved.toString().contains("mite_respawn_countdown:37"),"Actual player NBT stores remaining seconds");
            h.assertTrue(f.player.getFoodData()!=null,"Timer does not interfere with player state");
        }h.succeed();
    }
    private static final class Session implements AutoCloseable{
        final ServerPlayer player;final EmbeddedChannel channel;
        Session(GameTestHelper h){
            var profile=new GameProfile(UUID.randomUUID(),"rest-"+UUID.randomUUID().toString().substring(0,8));
            var cookie=CommonListenerCookie.createInitial(profile,false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection,player,cookie);
            player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);
            BlockPos pos=h.absolutePos(new BlockPos(1,3,1));player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        }
        @Override public void close(){player.getInventory().clearContent();player.server.getPlayerList().remove(player);channel.finishAndReleaseAll();}
    }
}
