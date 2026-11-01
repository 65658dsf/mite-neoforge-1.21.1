package io.github.ningmeng123.mite.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Match the reference's soft gray wash without losing warmth, markings or transparency. @author NingMeng123 */
class SickLivestockPaletteTest {
    @Test void realTexturePixelsKeepTheirAlphaAndMutedReferenceContrast() throws Exception {
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
                    for (int shift : new int[]{0, 8, 16})
                        assertTrue((gray >>> shift & 255) >= 63 && (gray >>> shift & 255) <= 191,
                                "Reference coat must keep muted shadows and soft white markings");
                    assertTrue(spread(gray) <= (spread(source) + 1) / 2,
                            "The gray wash must reduce saturation without erasing all warmth");
                    levels.add(gray & 0x00FFFFFF);
                    if (gray != source) changed++;
                }
            }
            assertTrue(levels.size() >= 8, species + " texture detail must survive the conversion");
            assertTrue(changed > 100, species + " must receive an actual pixel conversion");
        }
    }

    @Test void referenceCowColorsAndPartialTransparencyArePreserved() {
        // This brown-to-warm-gray pair matches a dominant colour in cow_sick.png.
        assertEquals(abgr(95, 87, 81), SickLivestockPalette.gray(abgr(64, 48, 36)));
        assertEquals(abgr(191, 191, 191), SickLivestockPalette.gray(-1), "White markings must stay soft");
        assertEquals(abgr(63, 63, 63), SickLivestockPalette.gray(0xFF000000), "Keep visible dark markings");
        int previous = -1;
        for (int value = 0; value <= 255; value++) {
            int pixel = 0x7F000000 | value << 16 | value << 8 | value;
            int gray = SickLivestockPalette.gray(pixel);
            assertEquals(127, gray >>> 24);
            assertTrue((gray & 255) >= previous, "Darker marks must never become brighter than lighter marks");
            previous = gray & 255;
        }
        int warm = SickLivestockPalette.gray(abgr(64, 48, 36));
        assertTrue((warm & 255) > (warm >>> 8 & 255) && (warm >>> 8 & 255) > (warm >>> 16 & 255),
                "The reference cow retains subtle warmth instead of becoming cold neutral gray");
        assertEquals(0x00123456, SickLivestockPalette.gray(0x00123456));
    }

    private static int abgr(int red, int green, int blue) {
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }

    private static int spread(int pixel) {
        int red = pixel & 255, green = pixel >>> 8 & 255, blue = pixel >>> 16 & 255;
        return Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
    }
}
