package io.github.ningmeng123.mite.rules;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source trade endpoints, probability folding and complete reachable stock, independently pinned to ub. @author NingMeng123 */
class SourceVillagerTradeRulesTest {
    @Test void legacyStockIsCoveredWithoutMigrationOrUnusedEntries() {
        var list=SourceVillagerTradeRules.listings();
        assertEquals(75,list.size());
        assertEquals(list.size(),list.stream().map(SourceVillagerTradeRules.Listing::id).distinct().count());
        assertEquals(Set.of(0,1,2,3,4),list.stream().map(SourceVillagerTradeRules.Listing::legacyProfession).collect(Collectors.toSet()));
        assertEquals(8,list.stream().filter(l->l.kind()==SourceVillagerTradeRules.Kind.ENCHANT).count());
        assertEquals(1,list.stream().filter(l->l.kind()==SourceVillagerTradeRules.Kind.BOOK).count());
        assertEquals(1,list.stream().filter(l->l.kind()==SourceVillagerTradeRules.Kind.GRAVEL).count());
        assertTrue(list.stream().noneMatch(l->l.item().contains("disenchanting")||l.item().contains("knife")||l.item().contains("diamond")));
        for (var listing:list) assertFalse(listing.professions().isBlank());
    }
    @Test void sourceProbabilityUsesSquareRootAndReflectionAbovePointNine() {
        assertEquals(.5F,SourceVillagerTradeRules.probability(.5F,0));
        assertEquals(.7F,SourceVillagerTradeRules.probability(.5F,1),1E-6F);
        assertEquals(.7F,SourceVillagerTradeRules.probability(.9F,1),1E-6F);
        assertEquals(.9F,SourceVillagerTradeRules.probability(.5F,4),1E-6F);
    }
    @Test void sourceBuyEndpointsClampBeforeDrawingAndSalesKeepTheExclusiveMaximum() {
        var wheat=SourceVillagerTradeRules.listings().stream().filter(l->l.item().equals("minecraft:wheat")).findFirst().orElseThrow();
        assertEquals(16,SourceVillagerTradeRules.purchaseCount(wheat,16,bound->{fail("Both wheat endpoints clamp to sixteen");return 0;}));
        assertEquals(7,SourceVillagerTradeRules.count(4,8,bound->{assertEquals(4,bound);return bound-1;}));
        assertEquals(-8,SourceVillagerTradeRules.count(-10,-7,bound->{assertEquals(3,bound);return bound-1;}));
    }
    @Test void bookPaymentsPreserveSourcePriceThroughTwoLegalEmeraldStacks() {
        for(int price:new int[]{5,31,32}) {
            var payment=SourceVillagerTradeRules.bookPayment(price,32);
            assertTrue(payment.consumesBook());assertEquals(price,payment.emeralds());
        }
        for(int price:new int[]{33,47,63,64,65,71}) {
            var payment=SourceVillagerTradeRules.bookPayment(price,32);
            assertFalse(payment.consumesBook());assertEquals(Math.min(price,64),payment.emeralds());
            assertTrue(payment.firstEmeralds()<=32&&payment.secondEmeralds()<=32);
        }
    }
}
