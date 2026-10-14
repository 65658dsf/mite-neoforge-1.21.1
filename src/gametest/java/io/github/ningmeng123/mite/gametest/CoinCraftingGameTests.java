package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real result-slot exchanges are atomic under event veto, overflow, repeated clicks and full inventories. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CoinCraftingGameTests {
    private CoinCraftingGameTests() {}
    @GameTest(template = "ranged_027")
    public static void allSixCoinsMintOneNuggetAndExactExperienceAndRedeemBothResources(GameTestHelper helper) {
        String[] metals = {"copper","silver","gold","ancient_metal","mithril","adamantium"}; int[] values={5,25,100,500,2500,10000};
        for (int index=0;index<metals.length;index++) try (Fixture f=new Fixture(helper,values[index],metals[index])) {
            f.input(new ItemStack(nugget(metals[index])));
            helper.assertTrue(f.menu.getSlot(0).getItem().is(MiteUtilityContent.get(metals[index]+"_coin")),"Actual mint recipe matches required metal bench");
            f.one(); helper.assertValueEqual(f.player.totalExperience,0,"Mint withdraws exact source amount once");
            helper.assertValueEqual(f.player.getInventory().countItem(MiteUtilityContent.get(metals[index]+"_coin")),1,"Mint creates exactly one stored coin");
            f.player.getInventory().clearContent(); f.input(new ItemStack(MiteUtilityContent.get(metals[index]+"_coin")));
            f.one();helper.assertValueEqual(f.player.totalExperience,values[index],"Redeem restores exact source XP");helper.assertValueEqual(f.player.getInventory().countItem(nugget(metals[index])),1,"Redeem restores exactly the original nugget");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void insufficientExperienceKeepsVisibleCoinPreviewButCannotConsumeNugget(GameTestHelper helper) {
        try (Fixture f=new Fixture(helper,4,"copper")) {
            f.input(new ItemStack(MiteMaterials.COPPER_NUGGET.get()));
            helper.assertTrue(f.menu.getSlot(0).getItem().is(MiteUtilityContent.get("copper_coin")),"Coin remains a recipe preview below its XP cost");
            helper.assertFalse(TimedCraftingController.start(f.player,f.menu),"Four XP cannot pay source five-XP coin");
            f.tick(400);helper.assertValueEqual(f.player.totalExperience,4,"Rejected mint preserves XP");helper.assertValueEqual(f.grid.getItem(0).getCount(),1,"Rejected mint preserves nugget");helper.assertValueEqual(f.player.getInventory().countItem(MiteUtilityContent.get("copper_coin")),0,"Rejected mint creates no currency");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void creativeMintingStillPaysSourceExperience(GameTestHelper helper) {
        try (Fixture f=new Fixture(helper,5,"copper")) {
            f.player.setGameMode(GameType.CREATIVE);f.input(new ItemStack(MiteMaterials.COPPER_NUGGET.get()));f.one();
            helper.assertValueEqual(f.player.totalExperience,0,"ItemCoin has no creative XP exemption");helper.assertValueEqual(f.player.getInventory().countItem(MiteUtilityContent.get("copper_coin")),1,"Creative pays for one actual coin");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void canceledMintOrRedeemExperienceEventLeavesEveryResourceUnchanged(GameTestHelper helper) {
        for (boolean redeem:new boolean[]{false,true}) try (Fixture f=new Fixture(helper,redeem?0:5,"copper")) {
            Item input=redeem?MiteUtilityContent.get("copper_coin"):MiteMaterials.COPPER_NUGGET.get();f.input(new ItemStack(input));
            Consumer<PlayerXpEvent.XpChange> cancel=event->{if(event.getEntity()==f.player && event.getAmount()==(redeem?5:-5))event.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(cancel);
            try { f.menu.clicked(0,0,ClickType.PICKUP,f.player);f.tick(f.period()); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertValueEqual(f.player.totalExperience,redeem?0:5,"Vetoed exchange preserves its original XP");helper.assertValueEqual(f.grid.getItem(0).getCount(),1,"Vetoed exchange consumes no input");helper.assertTrue(f.player.getInventory().isEmpty(),"Vetoed exchange creates no item output");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void overflowCannotDestroyACoinForAnIncompleteRefund(GameTestHelper helper) {
        try (Fixture f=new Fixture(helper,Integer.MAX_VALUE-2,"copper")) {
            f.input(new ItemStack(MiteUtilityContent.get("copper_coin")));f.menu.clicked(0,0,ClickType.PICKUP,f.player);f.tick(400);
            helper.assertValueEqual(f.player.totalExperience,Integer.MAX_VALUE-2,"Five-XP refund cannot overflow signed storage");helper.assertValueEqual(f.grid.getItem(0).getCount(),1,"An unrepresentable refund retains its coin");helper.assertValueEqual(f.player.getInventory().countItem(MiteMaterials.COPPER_NUGGET.get()),0,"Rejected refund creates no nugget");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void plainClickMakesOneCoinAndShiftContinuouslyPaysUntilInputsRunOut(GameTestHelper helper) {
        try (Fixture f=new Fixture(helper,20,"copper")) {
            f.input(new ItemStack(MiteMaterials.COPPER_NUGGET.get(),4));f.one();f.tick(400);
            helper.assertValueEqual(f.player.getInventory().countItem(MiteUtilityContent.get("copper_coin")),1,"Plain click cannot continue minting");helper.assertValueEqual(f.player.totalExperience,15,"Plain click pays one five-XP batch");
            f.menu.clicked(0,0,ClickType.QUICK_MOVE,f.player);f.tick(600);
            helper.assertValueEqual(f.player.getInventory().countItem(MiteUtilityContent.get("copper_coin")),4,"Shift consumes the remaining three batches");helper.assertValueEqual(f.player.totalExperience,0,"Continuous mint pays all four source debits");helper.assertTrue(f.grid.isEmpty(),"Continuous mint consumes only existing inputs");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void fullBackpackDropsExactlyOnePaidCoinWithoutDuplicatingExperience(GameTestHelper helper) {
        try (Fixture f=new Fixture(helper,5,"copper")) {
            f.input(new ItemStack(MiteMaterials.COPPER_NUGGET.get()));for(int slot=0;slot<f.player.getInventory().items.size();slot++)f.player.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
            f.one();helper.assertValueEqual(f.player.totalExperience,0,"Overflow output still pays once");helper.assertTrue(f.grid.isEmpty(),"Exactly one nugget is consumed");
            int total=helper.getLevel().getEntitiesOfClass(ItemEntity.class,f.player.getBoundingBox().inflate(3),entity->entity.getItem().is(MiteUtilityContent.get("copper_coin"))).stream().mapToInt(entity->entity.getItem().getCount()).sum();
            helper.assertValueEqual(total,1,"Full inventory emits precisely one paid ground coin");
            helper.getLevel().getEntitiesOfClass(ItemEntity.class,f.player.getBoundingBox().inflate(3),entity->entity.getItem().is(MiteUtilityContent.get("copper_coin"))).forEach(ItemEntity::discard);
        }
        helper.succeed();
    }
    private static Item nugget(String material) { return material.equals("gold")?Items.GOLD_NUGGET:MiteUtilityContent.item("mite:"+material+"_nugget"); }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper; final ServerPlayer player; final EmbeddedChannel channel; final BlockPos table; final BlockState original;final CraftingMenu menu;final CraftingContainer grid;
        Fixture(GameTestHelper helper,int xp,String material) {
            this.helper=helper;var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mite-coins"),false);
            player=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
            player.setGameMode(GameType.SURVIVAL);player.moveTo(helper.absolutePos(new BlockPos(2,3,2)).getCenter());player.setNoGravity(true);player.setOnGround(true);player.getInventory().clearContent();PlayerExperience.setTotal(player,xp);player.getFoodData().setFoodLevel(6);player.removeAllEffects();
            table=helper.absolutePos(new BlockPos(2,1,2));original=helper.getLevel().getBlockState(table);helper.getLevel().setBlockAndUpdate(table,MiteWorkbenches.TABLES.get(material).get().defaultBlockState());
            menu=new CraftingMenu(48,player.getInventory(),ContainerLevelAccess.create(helper.getLevel(),table));player.containerMenu=menu;grid=(CraftingContainer)menu.getSlot(1).container;
        }
        void input(ItemStack input){grid.clearContent();menu.getSlot(1).set(input);tick(1);}
        int period(){int period=TimedCraftingController.period(player,menu);helper.assertTrue(period>=25,"Real utility manufacturing period exists");return period;}
        void one(){TimedCraftingTestSupport.craftOne(helper,player,menu,ClickType.PICKUP);}
        void tick(int ticks){for(int i=0;i<ticks;i++)TimedCraftingController.tick(player);}
        @Override public void close(){try{TimedCraftingController.cancel(player);grid.clearContent();player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;player.server.getPlayerList().remove(player);}finally{helper.getLevel().setBlockAndUpdate(table,original);channel.finishAndReleaseAll();}}
    }
}
