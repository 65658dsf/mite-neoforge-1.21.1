package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ArmorProfile;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.SourceArmorDefenseRules.Piece;
import io.github.ningmeng123.mite.rules.SourceArmorDefenseRules.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static io.github.ningmeng123.mite.rules.SourceArmorDefenseRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal wh/abg/Damage expectations including the exact random-draw boundaries. @author NingMeng123 */
class SourceArmorDefenseRulesTest {
    private static final Source MELEE = new Source(false, false, false, false, false, false, false, false);
    private static List<Piece> set(int protection, float durability, boolean chain, int generalProtection, int specialProtection) {
        List<Piece> pieces = new ArrayList<>();
        for (int components : new int[]{4, 7, 8, 5}) {
            var profile = new ArmorProfile(components, durability, protection, chain, Quality.LEGENDARY);
            pieces.add(new Piece(profile, 0, EquipmentDurabilityRules.baseArmorDurability(profile), generalProtection, specialProtection,
                    0, specialProtection, specialProtection));
        }
        return pieces;
    }
    @Test void sourceArmorStagesAreComparableIncludingRetainedIronAndModernMappings() {
        assertEquals(7F, defense(set(7, 4F, false, 0, 0), MELEE, 0, 0).total());
        assertEquals(5F, defense(set(7, 4F, true, 0, 0), MELEE, 0, 0).total());
        assertEquals(8F, defense(set(8, 8F, false, 0, 0), MELEE, 0, 0).total());
        assertEquals(9F, defense(set(9, 64F, false, 0, 0), MELEE, 0, 0).total());
        assertEquals(10F, defense(set(10, 256F, false, 0, 0), MELEE, 0, 0).total());
    }
    @Test void sourceProtectionFourAddsOneHalfBeforeSpecialEnchantments() {
        assertEquals(12F, defense(set(8, 8F, false, 4, 0), MELEE, 0, 0).total());
        var projectile = new Source(false, false, false, false, false, false, false, true);
        assertEquals(24F, defense(set(8, 8F, false, 4, 4), projectile, 0, 0).total());
        var bypassProjectile = new Source(false, false, true, false, false, false, false, true);
        assertEquals(12F, defense(set(8, 8F, false, 4, 4), bypassProjectile, 0, 0).total());
    }
    @Test void commandedHigherProtectionKeepsSourceFractionRatherThanInventingAnotherCap() {
        assertEquals(16F, defense(set(8, 8F, false, 8, 0), MELEE, 0, 0).total());
    }
    @Test void featherFallingUsesFifteenNotVanillaPercentageOrArmorDefense() {
        var boots = new ArmorProfile(4, 8F, 8, false, Quality.MASTERWORK);
        var fall = new Source(false, false, true, false, false, true, false, false);
        assertEquals(15F, defense(List.of(new Piece(boots, 0, 64, 0, 0, 4, 0, 0)), fall, 0, 0).total());
        assertEquals(7.5F, defense(List.of(new Piece(boots, 48, 64, 4, 0, 4, 0, 0)), fall, 0, 0).total());
        assertEquals(3.75F, defense(List.of(new Piece(boots, 0, 64, 0, 0, 1, 0, 0)), fall, 0, 0).total());
        assertEquals(0F, defense(List.of(new Piece(boots, 63, 64, 0, 0, 4, 0, 0)), fall, 0, 0).total());
    }
    @Test void potionResistanceIsFiveDefensePerLevelAndAbsoluteDamageSkipsIt() {
        assertEquals(13F, defense(set(8, 8F, false, 0, 0), MELEE, 1, 2).total());
        var fire = new Source(false, false, true, false, true, false, false, false);
        assertEquals(23F, defense(set(8, 8F, false, 0, 4), fire, 1, 2).total());
        var lava = new Source(false, false, true, false, false, false, false, false);
        assertEquals(5F, defense(set(8, 8F, false, 0, 4), lava, 1, 2).total());
        var absolute = new Source(true, true, true, true, true, false, false, false);
        assertEquals(0F, defense(set(8, 8F, false, 4, 4), absolute, 3, 4).total());
    }
    @Test void unblockableDamageExcludesArmorEnchantmentsButSourceResistanceRemains() {
        var drowning = new Source(false, true, true, false, false, false, false, false);
        assertEquals(5F, defense(set(8, 8F, false, 4, 4), drowning, 1, 0).total());
    }
    @Test void minimumOneDamageAppliesAfterFlatDefenseAndOnlyEqualityStartsRandomChecks() {
        AtomicInteger draws = new AtomicInteger();
        assertEquals(3F, damage(11F, 8F, 0F, false, () -> { draws.incrementAndGet(); return .1F; }));
        assertEquals(1F, damage(8.5F, 8F, 0F, false, () -> { draws.incrementAndGet(); return .1F; }));
        assertEquals(0, draws.get());
        assertEquals(0F, damage(8F, 8F, 0F, false, () -> { draws.incrementAndGet(); return .199999F; }));
        assertEquals(1, draws.get());
        assertEquals(1F, damage(8F, 8F, 0F, false, () -> .2F));
    }
    @Test void integerDefenseAdvantageAddsExactIndependentTwentyPercentDraws() {
        for (float extra : new float[]{0F, .999F, 1F, 1.999F, 2F}) {
            AtomicInteger draws = new AtomicInteger();
            assertEquals(1F, damage(8F, 8F + extra, 0F, false, () -> { draws.incrementAndGet(); return .9F; }));
            assertEquals((int)extra + 1, draws.get());
        }
        AtomicInteger draws = new AtomicInteger();
        assertEquals(0F, damage(8F, 11F, 0F, false, () -> draws.incrementAndGet() == 3 ? .1F : .9F));
        assertEquals(3, draws.get());
    }
    @Test void piercingIsSubtractedBeforeBlockProbabilityAndAbsolutePreservesSmallHits() {
        assertEquals(4F, damage(10F, 8F, 2F, false, () -> fail("Defense below hit does not draw")));
        assertEquals(2F, damage(10F, 8F, 0F, false, () -> fail("Defense below hit does not draw")));
        assertEquals(.25F, damage(.25F, 100F, 0F, true, () -> fail("Absolute damage skips defense RNG")));
        assertEquals(0F, damage(0F, 100F, 0F, false, () -> fail("Zero damage skips defense RNG")));
    }
}
