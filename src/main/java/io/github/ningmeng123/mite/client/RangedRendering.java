package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.ranged.MaterialArrowView;
import io.github.ningmeng123.mite.ranged.FishingHookView;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only cast predicates and synchronized material missile textures. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RangedRendering {
    private RangedRendering() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.ARROW, MaterialArrowRenderer::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (var material : MiteRangedItems.rods()) ItemProperties.register(MiteRangedItems.rod(material),
                    ResourceLocation.withDefaultNamespace("cast"), (stack, level, player, seed) -> {
                        if (player == null || !(player instanceof net.minecraft.world.entity.player.Player actual) || actual.fishing == null) return 0F;
                        return actual.getItemInHand(((FishingHookView)actual.fishing).mite$castHand()) == stack ? 1F : 0F;
                    });
        });
    }
    private static final class MaterialArrowRenderer extends TippableArrowRenderer {
        MaterialArrowRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(Arrow arrow) {
            String material = ((MaterialArrowView)arrow).mite$arrowMaterial();
            return material.isEmpty() || "flint".equals(material) && arrow.getColor() > 0 ? super.getTextureLocation(arrow)
                    : ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "textures/entity/arrows/" + material + ".png");
        }
    }
}
