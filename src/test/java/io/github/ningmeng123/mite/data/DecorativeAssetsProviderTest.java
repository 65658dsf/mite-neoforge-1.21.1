package io.github.ningmeng123.mite.data;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.rules.DecorativeResourceRules;
import org.junit.jupiter.api.Test;

/** Surface block identity replacement must preserve the pinned vanilla noise reference. @author NingMeng123 */
class DecorativeAssetsProviderTest {
    @Test void surfaceIdentityRewriteDoesNotRewriteNoiseNamesOrOtherBlockNames() {
        var nativeRules = JsonParser.parseString("{\"sequence\":[{\"if_true\":{\"noise\":\"minecraft:gravel_layer\"},\"then_run\":{\"result_state\":{\"Name\":\"minecraft:gravel\"}}},{\"result_state\":{\"Name\":\"minecraft:gravelly_example\"}}]}");
        DecorativeResourceRules.replaceBlockIdentity(nativeRules, "minecraft:gravel", "mite:nether_gravel");
        var sequence = nativeRules.getAsJsonObject().getAsJsonArray("sequence");
        assertEquals("minecraft:gravel_layer", sequence.get(0).getAsJsonObject().getAsJsonObject("if_true").get("noise").getAsString());
        assertEquals("mite:nether_gravel", sequence.get(0).getAsJsonObject().getAsJsonObject("then_run").getAsJsonObject("result_state").get("Name").getAsString());
        assertEquals("minecraft:gravelly_example", sequence.get(1).getAsJsonObject().getAsJsonObject("result_state").get("Name").getAsString());
    }
}
