package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.client.config.ConfigSearchQuery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Queries follow the labels, paths and categories presented in the settings browser. @author NingMeng123 */
final class ConfigSearchQueryTest {
    @Test void chineseNamesAndTechnicalPathsFindTheSameSetting() {
        String[] setting = {"modern_ite:GameMechanics.longer_day_time_end_day", "双倍白天结束天数", "ITE / 游戏机制", "达到此历史最高天数后恢复正常白天"};
        assertTrue(ConfigSearchQuery.matches("双倍白天", setting));
        assertTrue(ConfigSearchQuery.matches("LONGER_DAY_TIME_END_DAY", setting));
        assertTrue(ConfigSearchQuery.matches("  ITE\t结束天数  ", setting));
        assertFalse(ConfigSearchQuery.matches("结束天数 附魔", setting));
    }

    @Test void entityIdsAndCategoryWordsCanBeCombinedWithoutSpecialQuerySyntax() {
        assertTrue(ConfigSearchQuery.matches("minecraft:zombie 权重", "monster_spawning:minecraft:zombie", "僵尸", "怪物刷新权重"));
        assertFalse(ConfigSearchQuery.matches("minecraft:skeleton 权重", "monster_spawning:minecraft:zombie", "僵尸", "怪物刷新权重"));
        assertTrue(ConfigSearchQuery.matches(" \t ", "anything"));
    }
}
