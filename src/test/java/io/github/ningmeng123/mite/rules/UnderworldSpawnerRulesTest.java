package io.github.ningmeng123.mite.rules;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independent acceptance cases for the requested modern underworld dungeon adaptation. @author NingMeng123 */
class UnderworldSpawnerRulesTest {
    static Stream<Arguments> cageMappings() {
        return Stream.of(
                Arguments.of("minecraft:zombie", "mite:revenant"),
                Arguments.of("minecraft:husk", "mite:revenant"),
                Arguments.of("minecraft:zombie_villager", "mite:revenant"),
                Arguments.of("minecraft:drowned", "mite:ghoul"),
                Arguments.of("minecraft:skeleton", "mite:longdead"),
                Arguments.of("minecraft:stray", "mite:longdead_guardian"),
                Arguments.of("minecraft:bogged", "mite:longdead_guardian"),
                Arguments.of("minecraft:wither_skeleton", "mite:longdead_guardian"),
                Arguments.of("minecraft:spider", "mite:demon_spider"),
                Arguments.of("minecraft:cave_spider", "mite:phase_spider"),
                Arguments.of("minecraft:slime", "mite:ooze"),
                Arguments.of("minecraft:creeper", "mite:infernal_creeper"),
                Arguments.of("minecraft:silverfish", "mite:hoary_silverfish"),
                Arguments.of("mite:bone_lord", "mite:longdead_guardian"),
                Arguments.of("mite:ancient_bone_lord", "mite:longdead_guardian"));
    }

    @ParameterizedTest @MethodSource("cageMappings")
    void mapsEachVanillaFamilyAndRejectsBothLordIdentities(String original, String expected) {
        assertEquals(expected, UnderworldSpawnerRules.replacement(original));
    }

    @Test void modernUniqueEnemiesAndOtherModsKeepTheirIdentity() {
        for (String id : Set.of("minecraft:breeze", "minecraft:blaze", "minecraft:witch", "minecraft:enderman",
                "minecraft:magma_cube", "minecraft:zombified_piglin", "example:custom_zombie", ""))
            assertEquals(id, UnderworldSpawnerRules.replacement(id));
    }

    @Test void existingNonLordMiteCreaturesRemainAvailable() {
        for (String id : Set.of("mite:revenant", "mite:ghoul", "mite:wight", "mite:longdead", "mite:longdead_guardian",
                "mite:demon_spider", "mite:phase_spider", "mite:ooze", "mite:pudding", "mite:jelly", "mite:shadow"))
            assertEquals(id, UnderworldSpawnerRules.replacement(id));
    }

    @Test void repeatedTicksAndReloadsCannotRerollOrChangeAnAlreadyMappedMob() {
        for (Map.Entry<String, String> entry : UnderworldSpawnerRules.replacements().entrySet())
            assertEquals(entry.getValue(), UnderworldSpawnerRules.replacement(UnderworldSpawnerRules.replacement(entry.getKey())));
    }

    @Test void noMappingCanProduceALord() {
        assertFalse(UnderworldSpawnerRules.replacements().containsValue("mite:bone_lord"));
        assertFalse(UnderworldSpawnerRules.replacements().containsValue("mite:ancient_bone_lord"));
    }

    @Test void sharedMappingCannotBeMutatedByAnotherCaller() {
        assertThrows(UnsupportedOperationException.class,
                () -> UnderworldSpawnerRules.replacements().put("minecraft:zombie", "mite:bone_lord"));
    }
}
