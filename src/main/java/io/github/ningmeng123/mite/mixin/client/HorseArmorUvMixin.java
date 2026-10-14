package io.github.ningmeng123.mite.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.client.SourceHorseArmorModel;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HorseArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The five source bardings keep their original 128-square PNGs on the animated modern horse. @author NingMeng123 */
@Mixin(HorseArmorLayer.class)
public abstract class HorseArmorUvMixin extends RenderLayer<Horse, HorseModel<Horse>> {
    @Unique private HorseModel<Horse> mite$sourceArmorModel;
    protected HorseArmorUvMixin(RenderLayerParent<Horse, HorseModel<Horse>> parent) { super(parent); }
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/animal/horse/Horse;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void mite$sourceHorseAtlas(PoseStack pose, MultiBufferSource buffers, int light, Horse horse,
            float limbSwing, float limbAmount, float partialTick, float age, float headYaw, float headPitch, CallbackInfo ci) {
        var stack = horse.getBodyArmorItem();
        var definition = MiteArmorContent.definition(stack.getItem());
        if (definition == null || !definition.horse() || !definition.isNew()
                || !(stack.getItem() instanceof AnimalArmorItem armor) || armor.getBodyType() != AnimalArmorItem.BodyType.EQUESTRIAN) return;
        if (mite$sourceArmorModel == null) mite$sourceArmorModel = new HorseModel<>(SourceHorseArmorModel.bakeRoot());
        getParentModel().copyPropertiesTo(mite$sourceArmorModel);
        mite$sourceArmorModel.prepareMobModel(horse, limbSwing, limbAmount, partialTick);
        mite$sourceArmorModel.setupAnim(horse, limbSwing, limbAmount, age, headYaw, headPitch);
        int color = stack.is(ItemTags.DYEABLE) ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536)) : -1;
        mite$sourceArmorModel.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(armor.getTexture())), light, OverlayTexture.NO_OVERLAY, color);
        ci.cancel();
    }
}
