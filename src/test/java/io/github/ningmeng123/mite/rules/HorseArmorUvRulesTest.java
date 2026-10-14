package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import io.github.ningmeng123.mite.rules.HorseArmorUvRules.Face;
import io.github.ningmeng123.mite.rules.HorseArmorUvRules.Rect;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Independent source bbp/bcp coordinates; no rendering or source image mutation. @author NingMeng123 */
final class HorseArmorUvRulesTest {
    @Test void originalBodyFacesRemainAtTheir128AtlasCoordinates() {
        var box = HorseArmorUvRules.part("/body").getFirst();
        assertEquals(new Rect(24, 34, 34, 58), box.face(Face.DOWN));
        assertEquals(new Rect(34, 58, 44, 34), box.face(Face.UP));
        assertEquals(new Rect(0, 58, 24, 68), box.face(Face.WEST));
        assertEquals(new Rect(24, 58, 34, 68), box.face(Face.NORTH));
        assertEquals(new Rect(34, 58, 58, 68), box.face(Face.EAST));
        assertEquals(new Rect(58, 58, 68, 68), box.face(Face.SOUTH));
        assertEquals(68F / 128F, box.face(Face.SOUTH).u(0));
        assertEquals(58F / 128F, box.face(Face.SOUTH).v(0));
    }
    @Test void modernMergedLegsKeepDistinctLeftRightFrontHindAndHoofRegions() {
        assertEquals(78, HorseArmorUvRules.part("/left_hind_leg").getFirst().u());
        assertEquals(96, HorseArmorUvRules.part("/right_hind_leg").getFirst().u());
        assertEquals(44, HorseArmorUvRules.part("/left_front_leg").getFirst().u());
        assertEquals(60, HorseArmorUvRules.part("/right_front_leg").getFirst().u());
        assertEquals(17, HorseArmorUvRules.part("/left_hind_leg").stream().mapToInt(HorseArmorUvRules.Box::weight).sum());
        assertEquals(16, HorseArmorUvRules.part("/left_front_leg").stream().mapToInt(HorseArmorUvRules.Box::weight).sum());
        assertEquals(new Rect(48, 55, 52, 58), HorseArmorUvRules.part("/left_front_leg").getLast().face(Face.NORTH));
        assertEquals(HorseArmorUvRules.part("/right_hind_leg"), HorseArmorUvRules.part("/right_hind_baby_leg"));
        assertEquals(2, HorseArmorUvRules.part("/head_parts/upper_mouth").size());
    }
    @Test void all23ModernCubePartsHaveCompleteBoundedSourceFaces() {
        assertEquals(23, HorseArmorUvRules.parts().size());
        assertTrue(HorseArmorUvRules.parts().keySet().containsAll(Set.of("/head_parts/head/left_ear",
                "/head_parts/head/right_ear", "/body/tail", "/head_parts/head_saddle")));
        for (var parts : HorseArmorUvRules.parts().values()) for (var box : parts) {
            assertTrue(box.weight() > 0);
            for (Face face : Face.values()) {
                Rect rect = box.face(face);
                for (float coordinate : new float[]{rect.u1(), rect.u2(), rect.v1(), rect.v2()})
                    assertTrue(coordinate >= 0 && coordinate <= 128, "Source atlas coordinate outside original PNG");
            }
        }
    }
}
