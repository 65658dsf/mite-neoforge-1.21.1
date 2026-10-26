package io.github.ningmeng123.mite.client.mob;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Checks source species colours against both supported atlas sizes without Minecraft or OpenGL. @author NingMeng123 */
class SpiderPaletteTest {
    private static final String ROOT = "src/main/resources/assets/mite/textures/entity/spider/";

    @Test void currentSpeciesRemainDistinctOnBothPackAtlasSizes() throws IOException {
        for (int height : new int[]{32, 64}) {
            var template = template(height);
            var mask = mask(height);
            var demon = adapt("demon_spider", template, mask);
            var wood = adapt("wood_spider", template, mask);
            var phase = adapt("phase_spider", template, mask);
            var widow = adapt("black_widow", template, mask);
            int red = demon.body().pixels()[0], brown = wood.body().pixels()[0];
            int cyan = phase.body().pixels()[0], black = widow.body().pixels()[0];
            assertTrue(r(red) > 2 * g(red), "Demon spider retains its red body");
            assertTrue(r(brown) > g(brown) && g(brown) > b(brown), "Wood spider retains its brown body");
            assertTrue(g(cyan) > 2 * r(cyan) && b(cyan) > 2 * r(cyan), "Phase spider retains its turquoise body");
            assertTrue(Math.max(r(black), Math.max(g(black), b(black))) < 30, "Black widow remains dark");
            for (var result : new SpiderPalette.Result[]{demon, wood, phase, widow}) {
                assertEquals(64, result.body().width());
                assertEquals(height, result.body().height());
                assertEquals(64 * height, result.body().pixels().length);
                assertEquals(64 * height, result.glow().pixels().length);
            }
        }
    }

    @Test void eyePixelsFollowPackMaskInsteadOfOldUvsAndKeepSpeciesEyeColour() throws IOException {
        for (int height : new int[]{32, 64}) {
            var template = template(height);
            var mask = mask(height);
            var phase = adapt("phase_spider", template, mask);
            var demon = adapt("demon_spider", template, mask);
            for (int i = 0; i < mask.pixels().length; i++) {
                int expectedAlpha = mask.pixels()[i] >>> 24;
                assertEquals(expectedAlpha, phase.glow().pixels()[i] >>> 24);
                assertEquals(expectedAlpha, demon.glow().pixels()[i] >>> 24);
                if (expectedAlpha == 0) continue;
                int greenEye = phase.glow().pixels()[i], redEye = demon.glow().pixels()[i];
                assertTrue(g(greenEye) > r(greenEye) + 50, "Phase spider has green eyes at the new UVs");
                assertTrue(r(redEye) > g(redEye) + 50, "Demon spider has red eyes at the new UVs");
                assertEquals(greenEye & 0xFFFFFF, phase.body().pixels()[i] & 0xFFFFFF);
            }
        }
    }

    @Test void templateTransparencyAndNeutralTeethSurviveRecolouring() throws IOException {
        var template = template(64);
        template.pixels()[1] = 0x00302010;
        template.pixels()[2] = 0x80303030;
        template.pixels()[3] = 0xFF7F8992;
        var result = adapt("phase_spider", template, mask(64));
        assertEquals(0x00302010, result.body().pixels()[1], "Transparent atlas padding is preserved exactly");
        assertEquals(128, result.body().pixels()[2] >>> 24, "Partial alpha survives");
        assertEquals(0xFF7F8992, result.body().pixels()[3], "Pale teeth keep their natural colour");
        assertEquals(0, result.glow().pixels()[3], "Teeth never become emissive");
        assertArrayEquals(template.pixels(), template(64, 0x00302010, 0x80303030, 0xFF7F8992).pixels(),
                "Input pixels are not changed");
    }

    @Test void transparentSpeciesEyesDoNotCreateNewGlowingPixels() throws IOException {
        var species = read("phase_spider");
        var transparentEyes = new SpiderPalette.Atlas(64, 32, new int[64 * 32]);
        var result = SpiderPalette.recolour(species, transparentEyes, template(64), mask(64));
        assertTrue(Arrays.stream(result.glow().pixels()).allMatch(pixel -> pixel == 0));
    }

    @Test void mismatchedMasksAreRejectedBeforeSamplingTheWrongAtlas() throws IOException {
        var species = read("wood_spider");
        var eyes = read("wood_spider_glow");
        assertThrows(IllegalArgumentException.class,
                () -> SpiderPalette.recolour(species, eyes, template(64), mask(32)));
        assertThrows(IllegalArgumentException.class,
                () -> SpiderPalette.recolour(species, mask(64), template(64), mask(64)));
        assertThrows(IllegalArgumentException.class, () -> new SpiderPalette.Atlas(64, 64, new int[64 * 32]));
    }

    private static SpiderPalette.Result adapt(String species, SpiderPalette.Atlas template, SpiderPalette.Atlas mask) throws IOException {
        return SpiderPalette.recolour(read(species), read(species + "_glow"), template, mask);
    }

    private static SpiderPalette.Atlas read(String name) throws IOException {
        var image = ImageIO.read(Path.of(ROOT, name + ".png").toFile());
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                pixels[y * image.getWidth() + x] = (argb & 0xFF00FF00) | ((argb >>> 16) & 255) | ((argb & 255) << 16);
            }
        }
        return new SpiderPalette.Atlas(image.getWidth(), image.getHeight(), pixels);
    }

    private static SpiderPalette.Atlas template(int height, int... special) {
        int[] pixels = new int[64 * height];
        Arrays.fill(pixels, 0xFF202A30);
        for (int i = 0; i < special.length; i++) pixels[i + 1] = special[i];
        return new SpiderPalette.Atlas(64, height, pixels);
    }

    private static SpiderPalette.Atlas mask(int height) {
        int[] pixels = new int[64 * height];
        for (int i = 0; i < 20; i++) pixels[(height - 5) * 64 + i + 30] = 0xFF0000C0;
        return new SpiderPalette.Atlas(64, height, pixels);
    }

    private static int r(int pixel) { return pixel & 255; }
    private static int g(int pixel) { return pixel >>> 8 & 255; }
    private static int b(int pixel) { return pixel >>> 16 & 255; }
}
