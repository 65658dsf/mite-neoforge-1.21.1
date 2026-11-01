package io.github.ningmeng123.mite.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Regression for the twelve missing texels on the actual legacy chicken head. @author NingMeng123 */
class ChickenHeadUvTest {
    @Test void sourceSkinHasACompleteUndersideWithoutChangingAnyOtherPixel() throws Exception {
        var image = ImageIO.read(Path.of("src/main/resources/assets/mite/textures/entity/livestock/chicken_sick.png").toFile());
        int width = image.getWidth(), height = image.getHeight();
        int[] original = image.getRGB(0, 0, width, height, null, 0, width);
        int[] repaired = original.clone();
        assertEquals(12, ChickenHeadUv.repair(width, height, repaired));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (x >= 7 && x < 11 && y < 3) {
                    assertEquals(255, repaired[index] >>> 24, "Head underside must be opaque");
                    assertEquals(original[index - 4], repaired[index], "Retain the skin's existing head colours");
                } else assertEquals(original[index], repaired[index], "Other UVs and transparency must stay unchanged");
            }
        }
        assertEquals(0, ChickenHeadUv.repair(width, height, repaired), "Repair is idempotent");
    }

    @Test void scaledAtlasAndAuthoredUndersidePixelsArePreserved() {
        int[] pixels = new int[128 * 64];
        java.util.Arrays.fill(pixels, 0x00123456);
        for (int y = 0; y < 6; y++)
            for (int x = 6; x < 14; x++) pixels[y * 128 + x] = 0xFF889977;
        pixels[14] = 0x80443322;
        assertEquals(47, ChickenHeadUv.repair(128, 64, pixels));
        assertEquals(0x80443322, pixels[14], "An authored partial-alpha pixel must not be overwritten");
        assertEquals(0x00123456, pixels[22], "Unused UV padding must stay transparent");
        int[] square = new int[64 * 64];
        assertEquals(0, ChickenHeadUv.repair(64, 64, square), "Unknown atlas layouts must not be rewritten");
    }
}
