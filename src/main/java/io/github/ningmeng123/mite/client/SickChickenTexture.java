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

/** Repair the legacy chicken head's missing underside without changing the source artwork. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SickChickenTexture {
    private static final Map<ResourceLocation, ResourceLocation> CACHE = new HashMap<>();

    private SickChickenTexture() {}

    public static ResourceLocation repair(ResourceLocation sick) {
        return CACHE.computeIfAbsent(sick, SickChickenTexture::create);
    }

    private static ResourceLocation create(ResourceLocation sick) {
        Minecraft minecraft = Minecraft.getInstance();
        NativeImage image = null;
        try (var input = minecraft.getResourceManager().open(sick)) {
            image = NativeImage.read(input);
            int width = image.getWidth(), height = image.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) pixels[y * width + x] = image.getPixelRGBA(x, y);
            if (ChickenHeadUv.repair(width, height, pixels) == 0) return sick;
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) image.setPixelRGBA(x, y, pixels[y * width + x]);
            ResourceLocation repaired = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,
                    "dynamic/livestock/" + sick.getNamespace() + "/" + sick.getPath());
            minecraft.getTextureManager().register(repaired, new DynamicTexture(image));
            image = null; // TextureManager owns and closes the uploaded image.
            return repaired;
        } catch (IOException failure) {
            Mite.LOGGER.warn("Cannot repair sick chicken head texture {}", sick, failure);
            return sick;
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

/** Pixel-only UV repair, also valid for uniformly scaled copies of the legacy atlas. @author NingMeng123 */
final class ChickenHeadUv {
    private ChickenHeadUv() {}

    static int repair(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0 || pixels.length != width * height)
            throw new IllegalArgumentException("Chicken atlas dimensions do not match its pixels");
        if (width % 64 != 0 || height != width / 2) return 0;
        int scale = width / 64, changed = 0;
        // ChickenModel head: texOffs(0, 0), box 4x6x3. Copy the top face's
        // existing sick colours into missing underside texels only.
        for (int y = 0; y < 3 * scale; y++) {
            for (int x = 7 * scale; x < 11 * scale; x++) {
                int index = y * width + x, top = pixels[index - 4 * scale];
                if (pixels[index] >>> 24 == 0 && top >>> 24 != 0) {
                    pixels[index] = top;
                    changed++;
                }
            }
        }
        return changed;
    }
}
