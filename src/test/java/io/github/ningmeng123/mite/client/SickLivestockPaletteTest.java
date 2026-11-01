package io.github.ningmeng123.mite.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Appearance regressions: gray-white must remove brown/pink/green while retaining coat detail. @author NingMeng123 */
class SickLivestockPaletteTest {
    @Test void realLivestockSkinsBecomeNeutralWithoutLosingAlphaOrPattern() throws Exception {
        for (String species : new String[]{"cow", "pig", "chicken", "sheep"}) {
            var image = ImageIO.read(Path.of("src/main/resources/assets/mite/textures/entity/livestock/" + species + "_sick.png").toFile());
            var levels = new HashSet<Integer>();
            int changed = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int source = argb & 0xFF00FF00 | (argb & 255) << 16 | argb >>> 16 & 255;
                    int gray = SickLivestockPalette.gray(source);
                    assertEquals(source >>> 24, gray >>> 24, species + " alpha must be unchanged");
                    if (source >>> 24 == 0) {
                        assertEquals(source, gray, "Unused atlas pixels must stay untouched");
                        continue;
                    }
                    assertEquals(gray & 255, gray >>> 8 & 255, species + " must have no color cast");
                    assertEquals(gray & 255, gray >>> 16 & 255, species + " must have no color cast");
                    assertTrue((gray & 255) >= 80 && (gray & 255) <= 245, "Coat must stay in the gray-white brightness range");
                    levels.add(gray & 255);
                    if (gray != source) changed++;
                }
            }
            assertTrue(levels.size() >= 8, species + " texture detail must survive the conversion");
            assertTrue(changed > 100, species + " must receive an actual pixel conversion");
        }
    }

    @Test void grayWhiteKeepsShadingOrderAndPartialTransparency() {
        int previous = -1;
        for (int value = 0; value <= 255; value++) {
            int pixel = 0x7F000000 | value << 16 | value << 8 | value;
            int gray = SickLivestockPalette.gray(pixel);
            assertEquals(127, gray >>> 24);
            assertTrue((gray & 255) >= previous, "Darker marks must never become brighter than lighter marks");
            previous = gray & 255;
        }
        assertTrue((SickLivestockPalette.gray(0xFF000000) & 255) > 64, "Very dark fur must brighten visibly");
        assertTrue((SickLivestockPalette.gray(-1) & 255) > 200, "Light fur must remain gray-white");
        assertNotEquals(SickLivestockPalette.gray(0xFF0000FF), SickLivestockPalette.gray(0xFFFF0000),
                "Luminance must account for NativeImage's ABGR channel order");
        assertEquals(0x00123456, SickLivestockPalette.gray(0x00123456));
    }
}
