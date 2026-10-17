package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source one-second deadline is inclusive and independent of game ticks. @author NingMeng123 */
class IngestionDelayTest {
    @Test void finishedIngestionBlocksExactlyOneRealSecond() {
        var d = new IngestionDelay(); assertTrue(d.ready(10)); d.finished(100);
        assertFalse(d.ready(1099)); assertTrue(d.ready(1100)); d.finished(2000);
        assertFalse(d.ready(2999)); d.clear(); assertTrue(d.ready(2999));
    }
}
