package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** A permanent single sword improves its damage and cooldown with material rank. @author NingMeng123 */
final class PhantomBladeRulesTest {
    @Test void everyMaterialHasOneSwordAndItsOwnShortAttackCooldown() {
        String[] materials = {"rusted_iron", "gold", "copper", "silver", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"};
        double[] damage = {.4D, .45D, .5D, .5D, 1D, 1.25D, 1.5D, 2D, 3D};
        int[] cooldown = {70, 65, 60, 60, 50, 45, 40, 30, 20};
        double[] ranges = {2D, 2.5D, 3D, 3D, 3.5D, 4D, 4.5D, 5D, 5.5D};
        assertEquals(materials.length, PhantomBladeRules.profiles().size());
        for (int index = 0; index < materials.length; index++) {
            var profile = PhantomBladeRules.profile(materials[index]);
            assertNotNull(profile);
            assertEquals(damage[index], profile.damage());
            assertEquals(cooldown[index], profile.intervalTicks());
            assertEquals(ranges[index], profile.targetRange());
            assertEquals(cooldown[index], profile.rechargeTicks(), "There is no separate long magazine recharge");
            assertEquals(1, profile.bladeCount());
            assertEquals(1, profile.fullMask());
        }
        assertEquals(1,PhantomBladeRules.maximumBladeCount());
        assertNull(PhantomBladeRules.profile("missing"));
    }

    @Test void higherRanksStrictlyImproveBothDamageAndCooldown() {
        for (var lower : PhantomBladeRules.profiles()) for (var higher : PhantomBladeRules.profiles()) {
            if (higher.rank() <= lower.rank()) continue;
            assertTrue(higher.damage() > lower.damage(), higher.material()+" deals more damage than "+lower.material());
            assertTrue(higher.intervalTicks() < lower.intervalTicks(), higher.material()+" recovers faster than "+lower.material());
            assertEquals((higher.rank()-lower.rank()) * .5D, higher.targetRange()-lower.targetRange(),
                    "Each material rank extends the attack radius by half a block");
        }
        assertEquals(PhantomBladeRules.profile("copper").rank(),PhantomBladeRules.profile("silver").rank());
    }
}
