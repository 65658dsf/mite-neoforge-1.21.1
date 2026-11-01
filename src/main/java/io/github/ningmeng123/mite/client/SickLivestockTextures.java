package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.Mite;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Derive a soft reference-style gray coat from the atlas actually selected for the animal. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SickLivestockTextures {
    private static final String PREFIX = "dynamic/sick_livestock/";
    private static final Map<ResourceLocation, ResourceLocation> CACHE = new HashMap<>();

    private SickLivestockTextures() {}

    public static ResourceLocation gray(ResourceLocation source) {
        if (source.getNamespace().equals(Mite.MOD_ID) && source.getPath().startsWith(PREFIX)) return source;
        return CACHE.computeIfAbsent(source, SickLivestockTextures::create);
    }

    private static ResourceLocation create(ResourceLocation source) {
        Minecraft minecraft = Minecraft.getInstance();
        NativeImage image = null;
        try {
            // ETF can bind generated blink/patch atlases with no ResourceManager entry.
            // Copy their pixels; never mutate the healthy texture or close its image.
            var texture = minecraft.getTextureManager().getTexture(source);
            if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
                NativeImage original = dynamic.getPixels();
                image = new NativeImage(original.getWidth(), original.getHeight(), true);
                image.copyFrom(original);
            } else {
                try (var input = minecraft.getResourceManager().open(source)) {
                    image = NativeImage.read(input);
                }
            }
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++)
                    image.setPixelRGBA(x, y, SickLivestockPalette.gray(image.getPixelRGBA(x, y)));
            ResourceLocation gray = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,
                    PREFIX + source.getNamespace() + "/" + source.getPath());
            minecraft.getTextureManager().register(gray, new DynamicTexture(image));
            image = null; // TextureManager owns the generated image.
            return gray;
        } catch (IOException failure) {
            Mite.LOGGER.warn("Cannot create gray-white sick livestock atlas from {}", source, failure);
            return source;
        } finally {
            if (image != null) image.close();
        }
    }

    @SubscribeEvent public static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            CACHE.forEach((source, generated) -> {
                if (!source.equals(generated)) Minecraft.getInstance().getTextureManager().release(generated);
            });
            CACHE.clear();
        });
    }
}

/** NativeImage ABGR pixels: blend with mid-gray to match the muted legacy sick palette. @author NingMeng123 */
final class SickLivestockPalette {
    private SickLivestockPalette() {}

    static int gray(int pixel) {
        if (pixel >>> 24 == 0) return pixel;
        // The reference's dark coat retains a little warmth, while its white
        // markings are muted. A neutral luminance fill loses that appearance.
        int red = ((pixel & 255) + 127) >> 1;
        int green = ((pixel >>> 8 & 255) + 127) >> 1;
        int blue = ((pixel >>> 16 & 255) + 127) >> 1;
        return pixel & 0xFF000000 | blue << 16 | green << 8 | red;
    }
}
