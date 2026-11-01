package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.SickLivestockVisuals;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** bhi changes the pig body while keeping the modern saddle layer. @author NingMeng123 */
@Mixin(PigRenderer.class)
public abstract class SickPigTexture033Mixin {
    @Inject(method="getTextureLocation(Lnet/minecraft/world/entity/animal/Pig;)Lnet/minecraft/resources/ResourceLocation;",at=@At("HEAD"),cancellable=true)
    private void mite$sickPig(Pig animal,CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation texture=SickLivestockVisuals.bodyTexture(animal,((LivingEntityRenderer<?,?>)(Object)this).getModel());
        if(texture!=null) cir.setReturnValue(texture);
    }
}
