package io.github.ningmeng123.mite.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** All reachable ub.q candidates; migration-only offers and unused stock-map entries are absent. @author NingMeng123 */
public final class SourceVillagerTradeRules {
    public enum Kind { BUY, SELL, GRAVEL, BOOK, ENCHANT }
    public record BookPayment(boolean consumesBook, int firstEmeralds, int secondEmeralds) {
        public int emeralds() { return firstEmeralds + secondEmeralds; }
    }
    public record Listing(String id, int legacyProfession, String professions, Kind kind, String item,
                          float probability, int minimum, int maximum) {
        public boolean applies(String profession) { return List.of(professions.split(",")).contains(profession); }
    }
    private static final List<Listing> LISTINGS = build();
    private SourceVillagerTradeRules() {}
    public static List<Listing> listings() { return LISTINGS; }
    public static List<Listing> listings(String profession) { return LISTINGS.stream().filter(l -> l.applies(profession)).toList(); }

    public static float probability(float base, int existingSourceOffers) {
        float value = base + (float)Math.sqrt(Math.max(existingSourceOffers, 0)) * .2F;
        return value > .9F ? .9F - (value - .9F) : value;
    }

    /** ub.b/c uses an exclusive upper bound, after stock-list endpoint clamping for purchases. */
    public static int count(int minimum, int maximum, IntUnaryOperator nextInt) {
        return minimum >= maximum ? minimum : minimum + nextInt.applyAsInt(maximum - minimum);
    }
    public static int purchaseCount(Listing listing, int stackLimit, IntUnaryOperator nextInt) {
        return count(Math.min(listing.minimum(), stackLimit), Math.min(listing.maximum(), stackLimit), nextInt);
    }
    public static BookPayment bookPayment(int authoredEmeralds, int stackLimit) {
        if (authoredEmeralds < 1 || stackLimit < 1) throw new IllegalArgumentException("Invalid book payment");
        if (authoredEmeralds <= stackLimit) return new BookPayment(true,0,authoredEmeralds);
        int total = Math.min(authoredEmeralds,stackLimit*2);
        return new BookPayment(false,stackLimit,total-stackLimit);
    }

    private static List<Listing> build() {
        List<Listing> result = new ArrayList<>();
        add(result,0,"farmer",Kind.BUY,"minecraft:wheat",.9F,18,22);
        add(result,0,"shepherd",Kind.BUY,"minecraft:white_wool",.5F,14,22);
        add(result,0,"butcher",Kind.BUY,"minecraft:chicken",.5F,14,18);
        add(result,0,"fisherman",Kind.BUY,"minecraft:cooked_cod",.4F,9,13);
        add(result,0,"farmer",Kind.SELL,"minecraft:bread",.9F,-4,-2);
        add(result,0,"farmer",Kind.SELL,"minecraft:melon_slice",.3F,-8,-4);
        add(result,0,"farmer",Kind.SELL,"minecraft:apple",.3F,-8,-4);
        add(result,0,"farmer",Kind.SELL,"minecraft:cookie",.3F,-10,-7);
        add(result,0,"shepherd",Kind.SELL,"minecraft:shears",.3F,3,4);
        add(result,0,"toolsmith",Kind.SELL,"minecraft:flint_and_steel",.3F,3,4);
        add(result,0,"butcher",Kind.SELL,"minecraft:cooked_chicken",.3F,-8,-6);
        add(result,0,"fletcher",Kind.SELL,"minecraft:arrow",.5F,-12,-8);
        add(result,0,"fletcher",Kind.GRAVEL,"minecraft:flint",.5F,4,6);

        add(result,1,"librarian",Kind.BUY,"minecraft:paper",.8F,24,36);
        add(result,1,"librarian",Kind.BUY,"minecraft:book",.8F,11,13);
        add(result,1,"librarian",Kind.BUY,"minecraft:written_book",.3F,1,1);
        add(result,1,"librarian",Kind.SELL,"minecraft:bookshelf",.8F,3,4);
        add(result,1,"cartographer",Kind.SELL,"minecraft:glass",.2F,-5,-3);
        add(result,1,"cartographer",Kind.SELL,"minecraft:compass",.2F,10,12);
        add(result,1,"librarian",Kind.SELL,"minecraft:clock",.2F,10,12);
        add(result,1,"librarian",Kind.BOOK,"minecraft:enchanted_book",.07F,0,0);

        add(result,2,"cleric",Kind.SELL,"minecraft:ender_eye",.3F,7,11);
        add(result,2,"cleric",Kind.SELL,"minecraft:redstone",.4F,-4,-1);
        add(result,2,"cleric",Kind.SELL,"minecraft:glowstone",.3F,-3,-1);
        for (String item : List.of("mite:copper_sword","minecraft:iron_sword","mite:copper_chestplate","minecraft:iron_chestplate",
                "mite:copper_axe","minecraft:iron_axe","mite:copper_pickaxe","minecraft:iron_pickaxe"))
            add(result,2,"cleric",Kind.ENCHANT,item,.05F,2,5);

        String smiths = "toolsmith,weaponsmith,armorer";
        add(result,3,smiths,Kind.BUY,"minecraft:coal",.7F,16,24);
        add(result,3,smiths,Kind.BUY,"minecraft:iron_ingot",.5F,4,8);
        add(result,3,smiths,Kind.BUY,"minecraft:gold_ingot",.5F,4,8);
        add(result,3,"weaponsmith",Kind.SELL,"minecraft:iron_sword",.5F,7,11);
        add(result,3,"toolsmith",Kind.SELL,"minecraft:iron_axe",.3F,6,8);
        add(result,3,"toolsmith",Kind.SELL,"minecraft:iron_pickaxe",.5F,7,9);
        add(result,3,"toolsmith",Kind.SELL,"minecraft:iron_shovel",.2F,4,6);
        add(result,3,"toolsmith",Kind.SELL,"minecraft:iron_hoe",.2F,4,6);
        armor(result,3,"armorer","minecraft:iron",.2F,4,6,10,14,8,10,4,6);
        add(result,3,"toolsmith",Kind.SELL,"mite:copper_pickaxe",.5F,7,9);
        add(result,3,"toolsmith",Kind.SELL,"mite:copper_shovel",.2F,4,6);
        add(result,3,"toolsmith",Kind.SELL,"mite:copper_axe",.3F,6,8);
        add(result,3,"toolsmith",Kind.SELL,"mite:copper_hoe",.2F,4,6);
        add(result,3,"weaponsmith",Kind.SELL,"mite:copper_dagger",.5F,4,6);
        add(result,3,"weaponsmith",Kind.SELL,"mite:copper_sword",.5F,7,11);
        add(result,3,"weaponsmith",Kind.SELL,"mite:iron_dagger",.5F,4,6);
        armor(result,3,"armorer","mite:copper",.2F,4,6,10,14,8,10,4,6);
        armor(result,3,"armorer","mite:copper_chain",.1F,5,7,11,15,9,11,5,7);
        armor(result,3,"armorer","minecraft:chainmail",.1F,5,7,11,15,9,11,5,7);

        add(result,4,"butcher",Kind.BUY,"minecraft:coal",.7F,16,24);
        add(result,4,"butcher",Kind.BUY,"minecraft:porkchop",.5F,14,18);
        add(result,4,"butcher",Kind.BUY,"minecraft:beef",.5F,14,18);
        add(result,4,"butcher",Kind.BUY,"minecraft:mutton",.5F,14,18);
        add(result,4,"leatherworker",Kind.SELL,"minecraft:saddle",.1F,6,8);
        armor(result,4,"leatherworker","minecraft:leather",.3F,2,4,4,5,2,4,2,4);
        add(result,4,"butcher",Kind.SELL,"minecraft:cooked_porkchop",.3F,-7,-5);
        add(result,4,"butcher",Kind.SELL,"minecraft:cooked_beef",.3F,-7,-5);
        add(result,4,"butcher",Kind.SELL,"minecraft:cooked_mutton",.3F,-7,-5);
        return List.copyOf(result);
    }
    private static void armor(List<Listing> list,int legacy,String professions,String prefix,float chance,int... ranges) {
        String[] pieces = {"helmet","chestplate","leggings","boots"};
        for (int i=0;i<pieces.length;i++) add(list,legacy,professions,Kind.SELL,prefix+"_"+pieces[i],chance,ranges[i*2],ranges[i*2+1]);
    }
    private static void add(List<Listing> list,int legacy,String professions,Kind kind,String item,float chance,int minimum,int maximum) {
        list.add(new Listing(legacy+"_"+kind.name().toLowerCase(java.util.Locale.ROOT)+"_"+item.replace(':','_'),
                legacy,professions,kind,item,chance,minimum,maximum));
    }
}
