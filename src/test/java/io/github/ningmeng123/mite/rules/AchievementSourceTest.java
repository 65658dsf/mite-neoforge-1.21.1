package io.github.ningmeng123.mite.rules;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Source declarations and explicit rare conditions, not production-generated counts. @author NingMeng123 */
class AchievementSourceTest {
    @Test void allSixtyTwoRegisteredIdentitiesAndParentsArePresent()throws Exception{
        var facts=JsonParser.parseString(Files.readString(Path.of("spec/r196-achievements.json"))).getAsJsonObject();
        var rows=facts.getAsJsonArray("achievements");assertEquals(62,rows.size());
        var ids=new HashSet<String>();var legacy=new HashSet<Integer>();
        for(var row:rows){var a=row.getAsJsonObject();assertTrue(ids.add(a.get("id").getAsString()));assertTrue(legacy.add(a.get("legacy_id").getAsInt()));assertFalse(a.getAsJsonArray("call_sites").isEmpty());}
        for(var row:rows){var a=row.getAsJsonObject();if(!a.get("parent").isJsonNull())assertTrue(ids.contains(a.get("parent").getAsString()));}
        assertTrue(ids.contains("enlightenment"));assertTrue(ids.contains("wear_all_adamantium_plate_armor"));assertTrue(legacy.contains(135));
    }
    @Test void nineBooksHaveUniqueIndicesAndSourceRewards()throws Exception{
        var data=JsonParser.parseString(Files.readString(Path.of("src/main/resources/mite/referenced-books.json"))).getAsJsonObject().getAsJsonArray("books");
        assertEquals(9,data.size());var indices=new HashSet<Integer>();
        for(var value:data){var a=value.getAsJsonObject();assertTrue(indices.add(a.get("index").getAsInt()));assertEquals(100,a.get("xp").getAsInt());assertFalse(a.getAsJsonArray("pages").isEmpty());}
    }
}
