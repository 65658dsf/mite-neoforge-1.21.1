package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.SickLivestockVisuals;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** bge changes only the body texture; ordinary wing animation remains. @author NingMeng123 */
@Mixin(ChickenRenderer.class)
public abstract class SickChickenTexture033Mixin {
    @Inject(method="getTextureLocation(Lnet/minecraft/world/entity/animal/Chicken;)Lnet/minecraft/resources/ResourceLocation;",at=@At("HEAD"),cancellable=true)
    private void mite$sickChicken(Chicken animal,CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation texture=SickLivestockVisuals.bodyTexture(animal,((LivingEntityRenderer<?,?>)(Object)this).getModel());
        if(texture!=null) cir.setReturnValue(texture);
    }
}
