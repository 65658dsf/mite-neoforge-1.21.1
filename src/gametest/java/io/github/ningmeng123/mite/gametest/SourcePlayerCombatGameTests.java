package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.SourcePlayerCombat;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/** Real primary attacks, source callback order and server packet reach, without live-client control. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SourcePlayerCombatGameTests {
    private SourcePlayerCombatGameTests() {}
    private static Player player(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1,15,1))));
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10D);
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
        player.setHealth(100F); player.setOnGround(true); player.setNoGravity(true);
        player.getFoodData().setFoodLevel(6);
        ((DualFoodData)player.getFoodData()).mite$setNutrition(0);
        return player;
    }
    private static Cow cow(GameTestHelper h, Player player, double distance) {
        Cow cow = EntityType.COW.create(h.getLevel());
        cow.setPos(player.position().add(distance,0,0)); cow.setNoAi(true); cow.setNoGravity(true);
        cow.setOnGround(true); cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); cow.setHealth(200F);
        return cow;
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .0001D, message + ": " + actual + " vs " + expected);
    }
    @GameTest(template = "empty")
    public static void realPrimaryAttacksKeepNegativePenaltiesAndCapPositiveLevelBenefits(GameTestHelper h) {
        int[] levels = {-25,0,100,199,200,201,312,403,Integer.MAX_VALUE};
        float[] expected = {5F,10F,15F,19.95F,20F,20F,20F,20F,20F};
        for (int i=0;i<levels.length;i++) {
            Player player = player(h); player.experienceLevel = levels[i];
            Cow target = cow(h,player,1D);
            player.attack(target);
            close(h,200F-target.getHealth(),expected[i],"Primary base damage at level " + levels[i]);
            target.discard();
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void criticalLevelSilverAndBluntUseSourceOrderExactlyOnce(GameTestHelper h) {
        Player player = player(h); player.experienceLevel=100;
        ItemStack hammer = new ItemStack(MiteTools.get("silver_war_hammer"));
        hammer.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND,hammer);
        player.setOnGround(false); player.fallDistance=1F;
        RecordingSkeleton target = new RecordingSkeleton(h);
        target.setPos(player.position().add(1D,0,0));
        player.attack(target);
        close(h,target.damage,56.25D,"10 * critical1.5 * level1.5 * silver1.25 * blunt2");
        h.succeed();
    }
    private static final class RecordingSkeleton extends Skeleton {
        float damage;
        RecordingSkeleton(GameTestHelper h) { super(EntityType.SKELETON,h.getLevel()); setNoAi(true); }
        @Override public boolean hurt(DamageSource source,float amount) { damage=amount; return false; }
    }
    @GameTest(template = "empty")
    public static void lowHealthWeakStrikeOnlyKnocksBackWithoutHealthOrArmorWear(GameTestHelper h) {
        Player player=player(h); player.setHealth(1F);
        Cow target=cow(h,player,1D);
        ItemStack chest=new ItemStack(Items.IRON_CHESTPLATE); target.setItemSlot(EquipmentSlot.CHEST,chest);
        player.attack(target);
        close(h,target.getHealth(),200D,"Weak strike never enters the health pipeline");
        h.assertTrue(target.getDeltaMovement().x>0D,"Weak strike keeps outward knockback");
        h.assertValueEqual(chest.getDamageValue(),0,"Weak strike cannot wear target armor");
        h.assertValueEqual(target.invulnerableTime,0,"Weak strike does not start damage immunity");
        target.discard(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void weakPlayerKnockbackReachesTheVictimsOwnConnectionWithoutDamageOrCanceledMotion(GameTestHelper h) {
        try (Session session=session(h)) {
            ServerPlayer victim=session.player;
            Player attacker=player(h); attacker.setHealth(1F);
            attacker.setPos(victim.position().add(-1D,0D,0D));
            victim.setDeltaMovement(Vec3.ZERO); victim.hurtMarked=false; victim.invulnerableTime=0;
            h.assertTrue(SourcePlayerReach.canMelee(attacker,victim),"Actual weak attack reaches the real connected player");
            List<ClientboundSetEntityMotionPacket> received=new ArrayList<>();
            session.channel.pipeline().addLast(new ChannelOutboundHandlerAdapter(){
                @Override public void write(ChannelHandlerContext context,Object message,ChannelPromise promise)throws Exception{
                    if(message instanceof ClientboundSetEntityMotionPacket packet&&packet.getId()==victim.getId())received.add(packet);
                    super.write(context,message,promise);
                }
            });
            ServerEntity tracker=new ServerEntity(h.getLevel(),victim,1,true,packet->{});
            float health=victim.getHealth();
            attacker.attack(victim);
            close(h,victim.getHealth(),health,"Weak player strike spends no health");
            h.assertValueEqual(victim.invulnerableTime,0,"Weak strike creates no hurt immunity frames");
            h.assertTrue(victim.getDeltaMovement().x>0D&&victim.hurtMarked,"Actual changed motion requests native broadcastAndSend");
            tracker.sendChanges(); session.channel.runPendingTasks();
            h.assertTrue(received.stream().anyMatch(packet->packet.getXa()>0D),"Victim's own real Connection receives the outward motion packet");
            h.assertFalse(victim.hurtMarked,"Native tracker consumes the one motion synchronization mark");
            received.clear(); victim.setDeltaMovement(Vec3.ZERO); victim.hurtMarked=false;
            int[] cancelCalls={0};
            Consumer<LivingKnockBackEvent> cancel=event->{if(event.getEntity()==victim){cancelCalls[0]++;event.setCanceled(true);}};
            NeoForge.EVENT_BUS.addListener(cancel);
            try { attacker.attack(victim); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertValueEqual(cancelCalls[0],1,"Weak strike still uses the real cancellable knockback event");
            h.assertTrue(victim.getDeltaMovement().equals(Vec3.ZERO)&&!victim.hurtMarked,"Canceled knockback creates no motion or self-packet mark");
            tracker.sendChanges(); session.channel.runPendingTasks();
            h.assertTrue(received.isEmpty(),"No false own-player motion packet follows a canceled weak strike");
            victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1D);
            attacker.attack(victim);
            h.assertTrue(victim.getDeltaMovement().equals(Vec3.ZERO)&&!victim.hurtMarked,"Full native resistance also creates no synchronization mark");
            close(h,victim.getHealth(),health,"Canceled/resisted attempts still cannot inflict health damage");
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void nutritionEnergyPreventsWeaknessAndMainHandProtectionIsRequired(GameTestHelper h) {
        Player player=player(h); player.getFoodData().setFoodLevel(0);
        Cow weak=cow(h,player,1D); player.attack(weak);
        close(h,weak.getHealth(),200D,"Empty energy is weak");
        ((DualFoodData)player.getFoodData()).mite$setNutrition(1);
        Cow strong=cow(h,player,1D); player.attack(strong);
        close(h,strong.getHealth(),190D,"Nutrition reserve remains real energy");
        player.setHealth(1F); player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.STICK));
        h.assertTrue(SourcePlayerCombat.weak(player),"Offhand stick does not shield the attacking hand");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BONE));
        h.assertFalse(SourcePlayerCombat.weak(player),"Source bone protects the attacking hand even with low health");
        weak.discard(); strong.discard(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void fireCreatureInjuresHandThroughArmorAndArbitraryHeldItems(GameTestHelper h) {
        Player player=player(h);
        player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT));
        var blaze=EntityType.BLAZE.create(h.getLevel()); blaze.setNoAi(true);
        blaze.setPos(player.position().add(1D,0,0));
        player.attack(blaze);
        close(h,player.getHealth(),99D,"Blaze hand injury always deals one unarmored point");
        h.assertValueEqual(player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue(),0,"Unblockable hand injury does not wear armor");
        player.invulnerableTime=0; player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        player.attack(blaze);
        close(h,player.getHealth(),99D,"Stick protects against the same callback");
        blaze.discard(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void immuneCubeStillExecutesHandInjuryAndCorrosionCallback(GameTestHelper h) {
        Player player=player(h);
        var pudding=MiteMobs.type(MobKind.PUDDING).create(h.getLevel());
        pudding.setNoAi(true); pudding.setPos(player.position().add(1D,0,0));
        player.attack(pudding);
        close(h,player.getHealth(),96D,"Pudding hand injury uses type multiplier four even though it rejects a normal fist hit");
        player.invulnerableTime=0;
        ItemStack sword=new ItemStack(Items.IRON_SWORD); sword.set(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND,sword);
        player.attack(pudding);
        h.assertTrue(sword.getDamageValue()>0,"Immune gelatinous target still corrodes the attacking tool");
        close(h,player.getHealth(),96D,"Tool prevents the hand injury");
        pudding.discard(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void blockReachRequiresEffectiveFamilyAndHonorsTheChosenModernHand(GameTestHelper h) {
        Player player=player(h);
        BlockPos pos=player.blockPosition().offset(3,1,0); h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        h.assertFalse(SourcePlayerReach.canReachBlock(player,pos,InteractionHand.MAIN_HAND),"Empty hand cannot reach center three blocks away");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(SourcePlayerReach.canReachBlock(player,pos,InteractionHand.MAIN_HAND),"Effective pick adds .75");
        h.getLevel().setBlockAndUpdate(pos,Blocks.OAK_LOG.defaultBlockState());
        h.assertFalse(SourcePlayerReach.canReachBlock(player,pos,InteractionHand.MAIN_HAND),"Ineffective pick cannot claim its tool bonus");
        player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.IRON_AXE));
        h.assertTrue(SourcePlayerReach.canReachBlock(player,pos,InteractionHand.OFF_HAND),"Offhand use evaluates that hand's effective tool");
        h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()); h.succeed();
    }
    @GameTest(template = "empty")
    public static void modernTridentAndMaceProtectMeleeWithoutPretendingToBeMiningTools(GameTestHelper h) {
        Player player=player(h); player.setHealth(1F); player.getFoodData().setFoodLevel(0);
        Cow target=cow(h,player,1D);
        for (var item:new net.minecraft.world.item.Item[]{Items.TRIDENT,Items.MACE}) {
            ItemStack held=new ItemStack(item); player.setItemInHand(InteractionHand.MAIN_HAND,held);
            h.assertTrue(SourcePlayerCombat.handProtected(held),"Modern melee weapon protects the main hand");
            h.assertFalse(SourcePlayerCombat.weak(player),"Protected modern weapon is usable with low health and no food energy");
            close(h,SourcePlayerReach.meleeReach(player,target),2.25D,"Modern weapon uses source sword/hammer reach");
            h.assertFalse(SourcePlayerReach.effective(held,Blocks.STONE.defaultBlockState()),"A melee weapon cannot claim pickaxe effectiveness");
            close(h,SourcePlayerReach.blockReach(player,Blocks.STONE.defaultBlockState(),InteractionHand.MAIN_HAND),2.75D,"Ineffective weapon adds no block reach");
        }
        target.discard(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void storageMaterialTierMustPermitHarvestBeforeToolReachApplies(GameTestHelper h) {
        Player player=player(h);
        var state=io.github.ningmeng123.mite.registry.MiteMetalBlocks.STORAGE.get("mithril").get().defaultBlockState();
        ItemStack iron=new ItemStack(Items.IRON_PICKAXE); player.setItemInHand(InteractionHand.MAIN_HAND,iron);
        h.assertTrue(iron.getDestroySpeed(state)>1F,"Modern generic pickaxe component alone would claim effectiveness");
        h.assertFalse(SourcePlayerReach.effective(iron,state),"Actual R196 mithril-block material threshold rejects iron");
        close(h,SourcePlayerReach.blockReach(player,state,InteractionHand.MAIN_HAND),2.75D,"Rejected material tier gets no tool bonus");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MiteTools.get("adamantium_pickaxe")));
        h.assertTrue(SourcePlayerReach.effective(player.getMainHandItem(),state),"Adamantium reaches the actual source material threshold");
        close(h,SourcePlayerReach.blockReach(player,state,InteractionHand.MAIN_HAND),3.5D,"Sufficient effective pick receives exactly .75");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void entityContextsKeepHeightBoatAndFireballOverrides(GameTestHelper h) {
        Player player=player(h); Cow target=cow(h,player,1D);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        close(h,SourcePlayerReach.meleeReach(player,target),2.25D,"Sword melee reach");
        close(h,SourcePlayerReach.interactionReach(player,target),2.5D,"Sword has no entity-interaction bonus");
        player.setPos(player.position().add(0D,2.5D,0D));
        close(h,SourcePlayerReach.meleeReach(player,target),3.25D,"High ground reaches the +1 cap");
        Boat boat=EntityType.BOAT.create(h.getLevel()); boat.setPos(target.position());
        close(h,SourcePlayerReach.interactionReach(player,boat),2.5D,"Boat ignores height");
        LargeFireball ball=EntityType.FIREBALL.create(h.getLevel()); ball.setPos(target.position());
        close(h,SourcePlayerReach.meleeReach(player,ball),5.25D,"Source fireball adds two after height and weapon");
        target.discard(); boat.discard(); ball.discard(); h.succeed();
    }
    @GameTest(template = "empty")
    public static void serverPacketsDistinguishShortMeleeGeneralInteractionAndUntrustedHitPoint(GameTestHelper h) {
        try (Session session=session(h)) {
            ServerPlayer player=session.player;
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            player.setDeltaMovement(Vec3.ZERO);
            InteractionCow target=new InteractionCow(h); target.setAge(0); target.setPos(player.position().add(2.7D,0,0));
            h.getLevel().getChunkAt(target.blockPosition());
            h.assertTrue(h.getLevel().addFreshEntity(target),"The packet target enters the real server level");
            try {
            h.assertTrue(h.getLevel().getEntity(target.getId())==target,"Native packet lookup resolves the same target before sending");
            h.assertFalse(SourcePlayerReach.canMelee(player,target),"Fixture lies outside the unchanged short melee range");
            h.assertTrue(SourcePlayerReach.canInteract(player,target),"Fixture lies inside the unchanged general interaction range");
            player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(target,false));
            h.assertValueEqual(target.hits,0,"Native padded packet range cannot bypass shorter melee reach");
            player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(target,false,InteractionHand.MAIN_HAND));
            h.assertValueEqual(target.interactions,1,"The longer interaction context still reaches the same cow");
            player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(target,false,InteractionHand.MAIN_HAND,new Vec3(-100,1,0)));
            h.assertValueEqual(target.specific,0,"Forged relative hit location outside target bounds is rejected");
            player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(target,false,InteractionHand.MAIN_HAND,new Vec3(-.44,1.2,0)));
            h.assertValueEqual(target.specific,1,"An actual near-side collision point within interaction reach is accepted");
            } finally { target.discard(); }
        }
        h.succeed();
    }
    private static final class InteractionCow extends Cow {
        int hits,interactions,specific;
        InteractionCow(GameTestHelper h) { super(EntityType.COW,h.getLevel()); setNoAi(true); setNoGravity(true); }
        @Override public boolean hurt(DamageSource source,float amount) { hits++; return false; }
        @Override public InteractionResult mobInteract(Player player,InteractionHand hand) { interactions++; return InteractionResult.SUCCESS; }
        @Override public InteractionResult interactAt(Player player,Vec3 relative,InteractionHand hand) { specific++; return InteractionResult.SUCCESS; }
    }
    @GameTest(template = "empty")
    public static void serverDigStartAndFinishRevalidateCurrentEffectiveToolAndDistance(GameTestHelper h) {
        try (Session session=session(h)) {
            ServerPlayer player=session.player;
            BlockPos pos=player.blockPosition().offset(3,1,0); h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
            player.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.WEST,h.getLevel().getMaxBuildHeight(),1);
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            h.assertFalse(player.gameMode.destroyBlock(pos),"Delayed completion cannot reuse a removed tool's reach");
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Rejected final transaction preserves the block");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
            player.setPos(player.position().add(-1D,0,0));
            player.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,Direction.WEST,h.getLevel().getMaxBuildHeight(),2);
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Moving outside reach cannot finish the old dig packet");
            h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }
    private static Session session(GameTestHelper h) {
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mite-source-combat"),false);
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection);
        try {
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1,15,1))));
            player.setNoGravity(true); player.setOnGround(true);
            for(int tick=0;tick<60;tick++) player.tick();
            player.getFoodData().setFoodLevel(6); ((DualFoodData)player.getFoodData()).mite$setNutrition(0);
            player.setHealth(player.getMaxHealth());
            return new Session(player,channel);
        } catch(RuntimeException | Error failure) {
            if(player.server.getPlayerList().getPlayer(player.getUUID())!=null) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll(); throw failure;
        }
    }
    private record Session(ServerPlayer player,EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try { if(player.server.getPlayerList().getPlayer(player.getUUID())!=null) player.server.getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
        }
    }
}
