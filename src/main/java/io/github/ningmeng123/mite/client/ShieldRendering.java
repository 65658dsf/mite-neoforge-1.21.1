package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteShields;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Native shield geometry with a separate metal-rim texture and native banner layers. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ShieldRendering {
    private static ShieldItemRenderer renderer;

    private ShieldRendering() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MiteShields.definitions().stream().filter(MiteShields.Definition::isNew).forEach(definition ->
                ItemProperties.register(MiteShields.get(definition.material()), ResourceLocation.withDefaultNamespace("blocking"),
                        (stack, level, owner, seed) -> owner != null && owner.isUsingItem() && owner.getUseItem() == stack ? 1F : 0F)));
    }

    @SubscribeEvent public static void itemRenderers(RegisterClientExtensionsEvent event) {
        IClientItemExtensions extension = new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer(); }
        };
        MiteShields.definitions().stream().filter(MiteShields.Definition::isNew)
                .forEach(definition -> event.registerItem(extension, MiteShields.get(definition.material())));
    }

    @SubscribeEvent public static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(renderer());
    }

    private static ShieldItemRenderer renderer() {
        if (renderer == null) renderer = new ShieldItemRenderer();
        return renderer;
    }

    public static ResourceLocation texture(String material) {
        return ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "textures/entity/shield/" + material + ".png");
    }

    private static final class ShieldItemRenderer extends BlockEntityWithoutLevelRenderer {
        private ShieldModel model;

        private ShieldItemRenderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override public void onResourceManagerReload(ResourceManager resources) { model = null; }

        @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                MultiBufferSource buffers, int light, int overlay) {
            var definition = MiteShields.definition(stack);
            if (definition == null || !definition.isNew()) return;
            if (model == null) model = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
            pose.pushPose();
            try {
                pose.scale(1F, -1F, -1F);
                VertexConsumer base = ItemRenderer.getFoilBufferDirect(buffers, model.renderType(texture(definition.material())), true, stack.hasFoil());
                model.handle().render(pose, base, light, overlay);
                model.plate().render(pose, base, light, overlay);

                BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
                DyeColor color = stack.get(DataComponents.BASE_COLOR);
                if (color != null || !patterns.layers().isEmpty()) {
                    renderPattern(pose, buffers, light, overlay, Sheets.SHIELD_BASE, color == null ? DyeColor.WHITE : color);
                    for (int i = 0; i < Math.min(16, patterns.layers().size()); i++) {
                        var layer = patterns.layers().get(i);
                        renderPattern(pose, buffers, light, overlay, Sheets.getShieldMaterial(layer.pattern()), layer.color());
                    }
                }
            } finally { pose.popPose(); }
        }

        private void renderPattern(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                Material material, DyeColor color) {
            model.plate().render(pose, material.buffer(buffers, RenderType::entityNoOutline), light, overlay, color.getTextureDiffuseColor());
        }
    }
}
