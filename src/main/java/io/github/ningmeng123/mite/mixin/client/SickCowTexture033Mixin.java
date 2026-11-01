package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.SickLivestockVisuals;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** bgf changes only an unwell ordinary cow's body texture. @author NingMeng123 */
@Mixin(CowRenderer.class)
public abstract class SickCowTexture033Mixin {
    @Inject(method="getTextureLocation(Lnet/minecraft/world/entity/animal/Cow;)Lnet/minecraft/resources/ResourceLocation;",at=@At("HEAD"),cancellable=true)
    private void mite$sickCow(Cow animal,CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation texture=SickLivestockVisuals.bodyTexture(animal,((LivingEntityRenderer<?,?>)(Object)this).getModel());
        if(texture!=null) cir.setReturnValue(texture);
    }
}
