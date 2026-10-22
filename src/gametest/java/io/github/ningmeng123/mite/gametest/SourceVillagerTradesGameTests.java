package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.SourceVillagerTrades;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.rules.SourceVillagerTradeRules;
import io.github.ningmeng123.mite.rules.SourceVillagerTradeRules.Kind;
import io.github.ningmeng123.mite.rules.SourceVillagerTradeRules.Listing;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real registered items, native quotes, payment clicks, villager persistence and restock. @author NingMeng123 */
@GameTestHolder("mite_trades")
@PrefixGameTestTemplate(false)
public final class SourceVillagerTradesGameTests {
    @GameTest(template="ranged_027")
    public static void everyReachableSourceListingBuildsALegalQuoteWithTheActualRegisteredItems(GameTestHelper helper) {
        Villager villager=new Villager(EntityType.VILLAGER,helper.getLevel());
        helper.assertValueEqual(SourceVillagerTradeRules.listings().size(),75,"All source q candidates, including eight priest services, are enumerated");
        for(Listing listing:SourceVillagerTradeRules.listings()) for(int seed=0;seed<16;seed++) {
            MerchantOffer quote=SourceVillagerTrades.create(villager,listing,RandomSource.create(seed));
            helper.assertFalse(quote.getResult().isEmpty(),"Source listing resolves a registered output: "+listing.id());
            helper.assertValueEqual(quote.getMaxUses(),7,"Source quotes start with seven uses");
            helper.assertTrue(quote.getBaseCostA().getCount()<=quote.getBaseCostA().getMaxStackSize(),"Primary cost fits its real stack limit");
            helper.assertTrue(quote.getCostB().isEmpty()||quote.getCostB().getCount()<=quote.getCostB().getMaxStackSize(),"Secondary cost is actually payable");
            helper.assertTrue(quote.getResult().getCount()<=quote.getResult().getMaxStackSize(),"Source sale quantity has the source item limit");
            if(listing.kind()==Kind.BUY&&listing.item().equals("minecraft:wheat"))
                helper.assertValueEqual(quote.getBaseCostA().getCount(),16,"Source wheat clamps both range endpoints before drawing");
            if(listing.kind()==Kind.BUY&&listing.item().equals("minecraft:white_wool"))
                helper.assertValueEqual(quote.getBaseCostA().getCount(),8,"Source wool purchase uses its eight-item stock cap");
            if(listing.kind()==Kind.BOOK) {
                var enchantments=quote.getResult().get(DataComponents.STORED_ENCHANTMENTS);
                helper.assertTrue(enchantments!=null&&enchantments.entrySet().size()==1,"A source librarian generates exactly one actual stored enchantment");
            }
        }
        villager.discard();helper.succeed();
    }

    @GameTest(template="ranged_027")
    public static void nativeGetOffersAddsSourceStockOnceAndSavedVillagersKeepUsesThroughReloadAndRestock(GameTestHelper helper) {
        for(String profession:new String[]{"farmer","fisherman","shepherd","fletcher","librarian","cartographer","cleric","toolsmith","weaponsmith","armorer","butcher","leatherworker"}) {
            Villager villager=new Villager(EntityType.VILLAGER,helper.getLevel());
            villager.setVillagerData(villager.getVillagerData().setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(profession))));
            MerchantOffers quotes=villager.getOffers();
            MerchantOffer source=sourceOffer(helper,villager);
            int count=quotes.size();source.increaseUses();
            for(int i=0;i<5;i++) helper.assertValueEqual(villager.getOffers().size(),count,"Repeated native reads never reroll source stock");
            CompoundTag saved=new CompoundTag();villager.saveWithoutId(saved);
            Villager loaded=new Villager(EntityType.VILLAGER,helper.getLevel());loaded.load(saved);
            helper.assertValueEqual(loaded.getOffers().size(),count,"Real entity NBT preserves the source-generation stamp and complete offers");
            helper.assertValueEqual(sourceOffer(helper,loaded).getUses(),1,"The recorded source offer's uses survive an actual native save/load");
            loaded.restock();
            helper.assertValueEqual(loaded.getOffers().size(),count,"Restock refreshes existing goods without duplicating them");
            helper.assertValueEqual(sourceOffer(helper,loaded).getUses(),0,"Source offers use the ordinary modern restock lifecycle");
            villager.discard();loaded.discard();
        }
        helper.succeed();
    }

    @GameTest(template="ranged_027")
    public static void anExistingVillagerKeepsAuthoredModernOffersWhileNewRanksOnlyAddSourceAttempts(GameTestHelper helper) {
        Villager villager=new Villager(EntityType.VILLAGER,helper.getLevel());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
        MerchantOffer nativeMarker=new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD,2),new ItemStack(Items.STICK),12,4,.05F);
        MerchantOffers authored=new MerchantOffers();authored.add(nativeMarker);villager.setOffers(authored);
        villager.getOffers();
        helper.assertTrue(villager.getOffers().getFirst()==nativeMarker,"Source stock is appended without rewriting an existing modern offer");
        int initial=villager.getOffers().size();
        villager.setVillagerData(villager.getVillagerData().setLevel(2));villager.getOffers();
        int after=villager.getOffers().size();
        helper.assertTrue(after>=initial&&after<=initial+1,"One additional modern rank gives at most one nonduplicate source quote");
        villager.getOffers();helper.assertValueEqual(villager.getOffers().size(),after,"Rank-generated source stock is also idempotent");
        villager.discard();helper.succeed();
    }

    @GameTest(template="ranged_027")
    public static void jobLossAndTheSameJobReacquisitionRestoreSourceStockEvenWithoutAnUnemployedOfferRead(GameTestHelper helper) {
        for(boolean readWhileUnemployed:new boolean[]{false,true}) {
            Villager villager=new Villager(EntityType.VILLAGER,helper.getLevel());
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
            sourceOffer(helper,villager);
            helper.assertValueEqual(villager.getPersistentData().getCompound(SourceVillagerTrades.STATE).getList("known",8).size(),1,
                    "The original job has one generated source entry");
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
            helper.assertFalse(villager.getPersistentData().contains(SourceVillagerTrades.STATE),
                    "Actual native job loss invalidates the source stamp immediately, independently of getOffers");
            if(readWhileUnemployed) helper.assertTrue(villager.getOffers().isEmpty(),"An unemployed villager has no native or source stock");
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.TOOLSMITH));
            int count=villager.getOffers().size();
            sourceOffer(helper,villager);
            helper.assertValueEqual(villager.getPersistentData().getCompound(SourceVillagerTrades.STATE).getList("known",8).size(),1,
                    "The new employment gets one fresh source entry rather than inheriting an obsolete level stamp");
            Villager loaded=new Villager(EntityType.VILLAGER,helper.getLevel());
            loaded.load(villager.saveWithoutId(new CompoundTag()));
            helper.assertValueEqual(loaded.getOffers().size(),count,"Loading the new job preserves its generated stock without rerolling");
            sourceOffer(helper,loaded);
            villager.discard();loaded.discard();
        }
        helper.succeed();
    }

    @GameTest(template="ranged_027")
    public static void librarianBookPricesKeepTheAuthoredAmountUpToTwoLegalEmeraldStacks(GameTestHelper helper) {
        Villager villager=new Villager(EntityType.VILLAGER,helper.getLevel());
        Listing listing=SourceVillagerTradeRules.listings().stream().filter(l->l.kind()==Kind.BOOK).findFirst().orElseThrow();
        Set<Integer> boundaries=new HashSet<>(Set.of(32,33,64,65,71));
        for(int seed=0;seed<100000&&!boundaries.isEmpty();seed++) {
            RandomSource oracle=RandomSource.create(seed);
            var definition=R196EnchantmentCatalog.definitions().get(oracle.nextInt(38));
            int maximum=definition.resourceId().equals("minecraft:protection")?4:definition.maxLevel();
            int level=oracle.nextInt(maximum)+1;
            int price=2+oracle.nextInt(5+level*10)+3*level;
            if(!boundaries.remove(price))continue;
            MerchantOffer quote=SourceVillagerTrades.create(villager,listing,RandomSource.create(seed));
            int actual=(quote.getBaseCostA().is(Items.EMERALD)?quote.getBaseCostA().getCount():0)+quote.getCostB().getCount();
            helper.assertValueEqual(actual,Math.min(price,64),"Book source total is retained until the two-slot capacity boundary at authored price "+price);
            helper.assertTrue(quote.getBaseCostA().is(price<=32?Items.BOOK:Items.EMERALD),"Only high-price books replace the ordinary-book input with a second legal emerald stack");
            try(Fixture f=new Fixture(helper,quote)) { f.payAndTake(); }
        }
        helper.assertTrue(boundaries.isEmpty(),"Independent seeded samples cover 32/33/64/65/71 authored-price boundaries");
        villager.discard();helper.succeed();
    }

    @GameTest(template="ranged_027")
    public static void nativeTradeClicksPayForSourceCopperAndFourGravelProcessingExactlyOnce(GameTestHelper helper) {
        Villager villager=new Villager(EntityType.VILLAGER,helper.getLevel());
        for(Listing listing:SourceVillagerTradeRules.listings().stream().filter(l->l.item().equals("mite:copper_pickaxe")||l.kind()==Kind.GRAVEL).toList()) {
            MerchantOffer quote=SourceVillagerTrades.create(villager,listing,RandomSource.create(15));
            try(Fixture f=new Fixture(helper,quote)) { f.payAndTake(); }
        }
        villager.discard();helper.succeed();
    }

    private static MerchantOffer sourceOffer(GameTestHelper helper, Villager villager) {
        MerchantOffers quotes=villager.getOffers();
        var known=villager.getPersistentData().getCompound(SourceVillagerTrades.STATE).getList("known",8);
        helper.assertValueEqual(known.size(),1,"A first-rank employment records exactly one source listing");
        String selected=known.getString(0);
        Listing listing=SourceVillagerTradeRules.listings().stream().filter(entry -> entry.id().equals(selected)).findFirst().orElse(null);
        helper.assertTrue(listing!=null || selected.equals("fallback_gold"),"The saved source listing is reachable: "+selected);
        var item=listing==null ? Items.GOLD_INGOT : BuiltInRegistries.ITEM.get(ResourceLocation.parse(listing.item()));
        boolean purchase=listing==null || listing.kind()==Kind.BUY;
        var matching=quotes.stream().filter(offer -> offer.getMaxUses()==7 && (purchase
                ? offer.getBaseCostA().is(item) && offer.getResult().is(Items.EMERALD) : offer.getResult().is(item))).toList();
        helper.assertValueEqual(matching.size(),1,"The saved source listing resolves one actual seven-use quote: "+describe(villager,quotes));
        return matching.getFirst();
    }

    private static String describe(Villager villager, MerchantOffers quotes) {
        return villager.getPersistentData().getCompound(SourceVillagerTrades.STATE)+" "+quotes.stream().map(offer ->
                BuiltInRegistries.ITEM.getKey(offer.getBaseCostA().getItem())+"->"+BuiltInRegistries.ITEM.getKey(offer.getResult().getItem())
                +" max="+offer.getMaxUses()+" uses="+offer.getUses()).toList();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;final ServerPlayer player;final EmbeddedChannel channel;final Villager villager;
        final MerchantOffer quote;final MerchantMenu menu;final MerchantContainer payments;
        Fixture(GameTestHelper helper,MerchantOffer quote) {
            this.helper=helper;this.quote=quote;
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mite-source-trade"),false);
            player=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            Connection connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(2,4,2)).getCenter());player.setNoGravity(true);player.getInventory().clearContent();
            villager=new Villager(EntityType.VILLAGER,helper.getLevel());villager.setNoAi(true);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
            villager.moveTo(player.position().add(1,0,0));helper.getLevel().addFreshEntity(villager);
            MerchantOffers offers=new MerchantOffers();offers.add(quote);villager.setOffers(offers);villager.setTradingPlayer(player);
            menu=new MerchantMenu(91,player.getInventory(),villager);player.containerMenu=menu;
            payments=(MerchantContainer)menu.getSlot(0).container;menu.setSelectionHint(0);
        }
        void payAndTake() {
            payments.setItem(0,quote.getBaseCostA().copy());payments.setItem(1,quote.getCostB().copy());
            helper.assertTrue(ItemStack.matches(menu.getSlot(2).getItem(),quote.assemble()),"Actual native input slots can preview the complete source quote");
            menu.clicked(2,0,ClickType.PICKUP,player);
            helper.assertTrue(ItemStack.matches(menu.getCarried(),quote.assemble()),"Native result pickup delivers precisely the paid source output and its components");
            helper.assertValueEqual(quote.getUses(),1,"The real trade consumes one source offer use");
            helper.assertTrue(payments.getItem(0).isEmpty()&&payments.getItem(1).isEmpty(),"Exact visible native payment is consumed once");
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty(),"The preview clears after payment is exhausted");
        }
        @Override public void close() {
            try {
                menu.setCarried(ItemStack.EMPTY);payments.clearContent();player.getInventory().clearContent();
                villager.setTradingPlayer(null);villager.discard();player.containerMenu=player.inventoryMenu;player.server.getPlayerList().remove(player);
            }finally{channel.finishAndReleaseAll();}
        }
    }
}
