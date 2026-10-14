package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.StrongboxBlock;
import io.github.ningmeng123.mite.block.entity.StrongboxBlockEntity;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Original chest sheets retain native lid animation in world and held rendering. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FunctionalBlockRendering {
    private FunctionalBlockRendering() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MiteFunctionalBlocks.ANVILS.forEach((material, block) ->
                ItemProperties.register(block.get().asItem(), ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "anvil_stage"),
                        (stack, level, owner, seed) -> FunctionalBlockRules.damageStage(stack.getOrDefault(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), 0),
                                FunctionalBlockRules.anvilDurability(WorkbenchRules.materialCapacity(material))))));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MiteFunctionalBlocks.STRONGBOX_ENTITY.get(), StrongboxRenderer::new);
        event.registerBlockEntityRenderer(MiteFunctionalBlocks.EMERALD_ENTITY.get(), EnchantTableRenderer::new);
    }
    @SubscribeEvent public static void itemRenderers(RegisterClientExtensionsEvent event) {
        IClientItemExtensions extension = new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new StrongboxItemRenderer();
                return renderer;
            }
        };
        MiteFunctionalBlocks.STRONGBOXES.values().forEach(block -> event.registerItem(extension, block.get().asItem()));
    }
    private static final class StrongboxRenderer extends ChestRenderer<StrongboxBlockEntity> {
        StrongboxRenderer(BlockEntityRendererProvider.Context context) { super(context); }
        @Override protected Material getMaterial(StrongboxBlockEntity entity, ChestType type) {
            String metal = ((StrongboxBlock)entity.getBlockState().getBlock()).material();
            return new Material(Sheets.CHEST_SHEET, ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "entity/chest/" + metal + "_single"));
        }
    }
    private static final class StrongboxItemRenderer extends BlockEntityWithoutLevelRenderer {
        StrongboxItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
        @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                MultiBufferSource buffers, int light, int overlay) {
            if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof StrongboxBlock block) {
                var entity = new StrongboxBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(entity, pose, buffers, light, overlay);
            }
        }
    }
}
