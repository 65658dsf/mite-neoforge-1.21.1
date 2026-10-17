package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.data.WeaponAssetsProvider;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** All render references are isolated from dedicated-server class loading. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WeaponRendering {
    private WeaponRendering() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MiteWeaponContent.SPHERE.get(), ThrownItemRenderer::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(net.minecraft.world.item.Items.BOW, ResourceLocation.withDefaultNamespace("pull"), (stack, world, living, seed) ->
                    living != null && living.getUseItem() == stack ? io.github.ningmeng123.mite.enchantment.RangedEnchantments.pull(stack, living.level(), living.getTicksUsingItem()) : 0F);
            for (var bow : java.util.List.of(MiteWeaponContent.ANCIENT_METAL_BOW.get(), MiteWeaponContent.MITHRIL_BOW.get(), MiteWeaponContent.VIBRANIUM_BOW.get())) {
                ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, world, living, seed) ->
                        living != null && living.getUseItem() == stack ? io.github.ningmeng123.mite.enchantment.RangedEnchantments.pull(stack, living.level(), living.getTicksUsingItem()) : 0F);
                ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pulling"), (stack, world, living, seed) ->
                        living != null && living.isUsingItem() && living.getUseItem() == stack && living.getTicksUsingItem() > 0 ? 1F : 0F);
                ItemProperties.register(bow, ResourceLocation.parse("mite:arrow_material"), (stack, world, living, seed) -> {
                    if (!(living instanceof Player player)) return 0F;
                    var material = MiteRangedItems.arrowMaterial(player.getProjectile(stack));
                    return material == null ? 0F : Math.max(WeaponAssetsProvider.ARROW_MATERIALS.indexOf(material.id), 0);
                });
            }
        });
    }
}
