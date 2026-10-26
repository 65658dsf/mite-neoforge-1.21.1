package io.github.ningmeng123.mite.client.mob;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.LinkedHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Fits source species colours to the active resource pack's spider UV atlas. @author NingMeng123 */
public final class SpiderPackTextures {
    public record Textures(ResourceLocation body, ResourceLocation glow) {}

    private SpiderPackTextures() {}

    public static Textures create(ResourceLocation sourceBody, ResourceLocation sourceGlow,
            ResourceLocation packBody, ResourceLocation packGlow, String variant) throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resources = minecraft.getResourceManager();
        NativeImage body = null, glow = null;
        try (NativeImage species = read(resources, sourceBody);
                NativeImage speciesEyes = read(resources, sourceGlow);
                NativeImage template = read(resources, packBody);
                NativeImage mask = readMask(resources, packBody, packGlow, template.getWidth(), template.getHeight())) {
            sameSize(species, speciesEyes, sourceGlow.toString());
            SpiderPalette.Result pixels = SpiderPalette.recolour(atlas(species), atlas(speciesEyes), atlas(template), atlas(mask));
            body = new NativeImage(template.getWidth(), template.getHeight(), true);
            glow = new NativeImage(template.getWidth(), template.getHeight(), true);
            for (int y = 0; y < template.getHeight(); y++) {
                for (int x = 0; x < template.getWidth(); x++) {
                    int index = y * template.getWidth() + x;
                    body.setPixelRGBA(x, y, pixels.body().pixels()[index]);
                    glow.setPixelRGBA(x, y, pixels.glow().pixels()[index]);
                }
            }
            ResourceLocation bodyId = ResourceLocation.fromNamespaceAndPath("mite", "dynamic/spider/" + variant);
            ResourceLocation glowId = ResourceLocation.fromNamespaceAndPath("mite", "dynamic/spider/" + variant + "_eyes");
            // TextureManager closes an older texture at the same ID when renderers are rebuilt on reload.
            minecraft.getTextureManager().register(bodyId, new DynamicTexture(body));
            body = null;
            minecraft.getTextureManager().register(glowId, new DynamicTexture(glow));
            glow = null;
            return new Textures(bodyId, glowId);
        } finally {
            if (body != null) body.close();
            if (glow != null) glow.close();
        }
    }

    private static SpiderPalette.Atlas atlas(NativeImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                pixels[y * image.getWidth() + x] = image.getPixelRGBA(x, y);
        return new SpiderPalette.Atlas(image.getWidth(), image.getHeight(), pixels);
    }

    private static NativeImage read(ResourceManager resources, ResourceLocation location) throws IOException {
        try (var stream = resources.open(location)) {
            return NativeImage.read(stream);
        }
    }

    private static NativeImage readMask(ResourceManager resources, ResourceLocation body, ResourceLocation supplied,
            int width, int height) throws IOException {
        var candidates = new LinkedHashSet<ResourceLocation>();
        if (supplied != null) candidates.add(supplied);
        String path = body.getPath();
        if (path.endsWith(".png")) candidates.add(body.withPath(path.substring(0, path.length() - 4) + "_e.png"));
        int slash = path.lastIndexOf('/');
        candidates.add(body.withPath(path.substring(0, slash + 1) + "spider_eyes.png"));
        candidates.add(ResourceLocation.withDefaultNamespace("textures/entity/spider_eyes.png"));
        NativeImage transparent = null;
        try {
            for (ResourceLocation candidate : candidates) {
                if (resources.getResource(candidate).isEmpty()) continue;
                NativeImage image = read(resources, candidate);
                if (image.getWidth() != width || image.getHeight() != height) {
                    image.close();
                    continue;
                }
                if (hasVisiblePixels(image)) return image;
                if (transparent == null) transparent = image;
                else image.close();
            }
            if (transparent != null) {
                NativeImage result = transparent;
                transparent = null;
                return result;
            }
            throw new IOException("No spider eye atlas matches " + body + " (" + width + "x" + height + ")");
        } finally {
            if (transparent != null) transparent.close();
        }
    }

    private static void sameSize(NativeImage body, NativeImage eyes, String name) throws IOException {
        if (body.getWidth() != eyes.getWidth() || body.getHeight() != eyes.getHeight())
            throw new IOException("Spider body and eye atlases differ in size: " + name);
    }

    private static boolean hasVisiblePixels(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if (image.getPixelRGBA(x, y) >>> 24 != 0) return true;
        return false;
    }
}

/** Pixel-only adaptation can be checked without starting Minecraft or allocating a texture. @author NingMeng123 */
final class SpiderPalette {
    record Atlas(int width, int height, int[] pixels) {
        Atlas {
            if (width <= 0 || height <= 0 || pixels.length != width * height)
                throw new IllegalArgumentException("Spider atlas dimensions do not match its pixels");
        }
    }
    record Result(Atlas body, Atlas glow) {}

    private SpiderPalette() {}

    static Result recolour(Atlas species, Atlas speciesEyes, Atlas template, Atlas mask) {
        sameSize(species, speciesEyes);
        sameSize(template, mask);
        int bodyColour = average(species.pixels(), speciesEyes.pixels(), false);
        int eyeColour = average(speciesEyes.pixels(), null, false);
        boolean luminousEyes = alpha(eyeColour) != 0;
        double bodyBrightness = brightness(average(template.pixels(), mask.pixels(), true));
        double eyeBrightness = brightness(average(mask.pixels(), null, false));
        int[] body = new int[template.pixels().length], glow = new int[body.length];
        for (int i = 0; i < body.length; i++) {
            int pixel = template.pixels()[i], eye = mask.pixels()[i];
            body[i] = tint(pixel, bodyColour, bodyBrightness, true);
            if (alpha(eye) != 0) {
                int eyePixel = tint(eye, luminousEyes ? eyeColour : bodyColour, eyeBrightness, false);
                // The active pack defines eye locations, independently of the old species UVs.
                body[i] = (pixel & 0xFF000000) | (eyePixel & 0x00FFFFFF);
                glow[i] = luminousEyes ? eyePixel : 0;
            }
        }
        return new Result(new Atlas(template.width(), template.height(), body), new Atlas(template.width(), template.height(), glow));
    }

    private static void sameSize(Atlas body, Atlas eyes) {
        if (body.width() != eyes.width() || body.height() != eyes.height())
            throw new IllegalArgumentException("Spider body and eye atlases differ in size");
    }

    private static int average(int[] pixels, int[] excluded, boolean preserveTeeth) {
        long red = 0, green = 0, blue = 0, weight = 0;
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i], a = alpha(pixel);
            if (a == 0 || excluded != null && alpha(excluded[i]) != 0 || preserveTeeth && isTooth(pixel)) continue;
            red += (long) red(pixel) * a;
            green += (long) green(pixel) * a;
            blue += (long) blue(pixel) * a;
            weight += a;
        }
        if (weight == 0) return 0;
        return 0xFF000000 | (int)(blue / weight) << 16 | (int)(green / weight) << 8 | (int)(red / weight);
    }

    /** NativeImage colours use ABGR; alpha and the template's local shading are retained. */
    static int tint(int pixel, int colour, double averageBrightness, boolean preserveTeeth) {
        if (alpha(pixel) == 0 || preserveTeeth && isTooth(pixel)) return pixel;
        double shade = brightness(pixel) / Math.max(1D, averageBrightness);
        int red = channel(red(colour) * shade);
        int green = channel(green(colour) * shade);
        int blue = channel(blue(colour) * shade);
        return (pixel & 0xFF000000) | blue << 16 | green << 8 | red;
    }

    private static boolean isTooth(int pixel) {
        int max = Math.max(red(pixel), Math.max(green(pixel), blue(pixel)));
        int min = Math.min(red(pixel), Math.min(green(pixel), blue(pixel)));
        return min > 100 && max - min < 45;
    }

    private static int channel(double value) { return (int)Math.clamp(Math.round(value), 0, 255); }
    private static double brightness(int pixel) { return .2126 * red(pixel) + .7152 * green(pixel) + .0722 * blue(pixel); }
    private static int alpha(int pixel) { return pixel >>> 24; }
    private static int red(int pixel) { return pixel & 255; }
    private static int green(int pixel) { return pixel >>> 8 & 255; }
    private static int blue(int pixel) { return pixel >>> 16 & 255; }
}
