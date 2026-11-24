package io.github.ningmeng123.mite.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/** Detects resource assets that make ordinarily unlit terrain bright. @author NingMeng123 */
public final class VisualLightmapDetector {
    private static final List<String> LIGHTMAP_DIRECTORIES =
            List.of("optifine/lightmap", "mcpatcher/lightmap", "colormatic/lightmap", "polytone/lightmaps");
    private static final int MIN_FULLBRIGHT_CHANNEL = 224;
    private static final int MAX_PALETTE_WIDTH = 4096;
    private static final List<String> TERRAIN_SHADERS =
            List.of("rendertype_solid", "rendertype_cutout", "rendertype_cutout_mipped");
    private static final Pattern RAW_VERTEX_COLOR = Pattern.compile("\\bvertexColor\\s*=\\s*Color\\s*;");
    private static final Pattern ASSIGN_VERTEX_COLOR = Pattern.compile("\\bvertexColor(?:\\s*\\.\\s*\\w+)?\\s*[+*/-]?=");
    private static final Pattern SAMPLE_LIGHTMAP = Pattern.compile("\\bminecraft_sample_lightmap\\s*\\("
            + "|\\b(?:texture(?:2D|Lod)?|texelFetch)\\s*\\(\\s*Sampler2\\b");

    private VisualLightmapDetector() {}

    public static boolean matches(PackResources pack) {
        for (String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
            for (String directory : LIGHTMAP_DIRECTORIES) {
                boolean[] found = {false};
                pack.listResources(PackType.CLIENT_RESOURCES, namespace, directory, (location, supplier) -> {
                    if (!found[0] && location.getPath().endsWith(".png")) found[0] = fullbrightPalette(supplier);
                });
                if (found[0]) return true;
            }
        }
        for (String shader : TERRAIN_SHADERS) {
            if (bypassesVanillaLightmap(pack, shader)) return true;
        }
        return false;
    }

    private static boolean fullbrightPalette(IoSupplier<InputStream> supplier) {
        var readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) return false;
        var reader = readers.next();
        try (var stream = supplier.get(); var imageStream = new MemoryCacheImageInputStream(stream)) {
            reader.setInput(imageStream, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || width > MAX_PALETTE_WIDTH || (height != 16 && height != 32 && height != 64)) return false;
            return fullbrightRows(reader.read(0));
        } catch (IOException exception) {
            return false;
        } finally {
            reader.dispose();
        }
    }

    private static boolean fullbrightRows(BufferedImage image) {
        // The first four rows of both normal-light ramps describe unlit and very dim blocks.
        // The additional rows in a 64-pixel palette apply during the actual Night Vision effect.
        int[] sky = darkestChannels(image, 0);
        int[] block = image.getHeight() == 16 ? new int[3] : darkestChannels(image, 16);
        for (int channel = 0; channel < sky.length; channel++) {
            if (sky[channel] + block[channel] < MIN_FULLBRIGHT_CHANNEL) return false;
        }
        return true;
    }

    private static int[] darkestChannels(BufferedImage image, int startRow) {
        int[] minimum = {255, 255, 255};
        for (int y = startRow; y < startRow + 4; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) != 255) return new int[3];
                minimum[0] = Math.min(minimum[0], pixel >>> 16 & 255);
                minimum[1] = Math.min(minimum[1], pixel >>> 8 & 255);
                minimum[2] = Math.min(minimum[2], pixel & 255);
            }
        }
        return minimum;
    }

    private static boolean bypassesVanillaLightmap(PackResources pack, String shader) {
        try {
            String vertex = readCoreShader(pack, shader + ".vsh");
            if (vertex == null || !RAW_VERTEX_COLOR.matcher(vertex).find()
                    || ASSIGN_VERTEX_COLOR.matcher(vertex).results().count() != 1
                    || SAMPLE_LIGHTMAP.matcher(vertex).find()) return false;
            String fragment = readCoreShader(pack, shader + ".fsh");
            return fragment == null || !SAMPLE_LIGHTMAP.matcher(fragment).find();
        } catch (IOException exception) {
            return false;
        }
    }

    private static String readCoreShader(PackResources pack, String filename) throws IOException {
        var supplier = pack.getResource(PackType.CLIENT_RESOURCES,
                ResourceLocation.withDefaultNamespace("shaders/core/" + filename));
        if (supplier == null) return null;
        try (var stream = supplier.get()) {
            return NightVisionShaderPack.readSource(stream);
        }
    }
}
