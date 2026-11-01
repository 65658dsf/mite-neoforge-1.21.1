package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.SickLivestockVisuals;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** bhk changes the bare body only; colored wool and shearing retain their own layer. @author NingMeng123 */
@Mixin(SheepRenderer.class)
public abstract class SickSheepTexture033Mixin {
    @Inject(method="getTextureLocation(Lnet/minecraft/world/entity/animal/Sheep;)Lnet/minecraft/resources/ResourceLocation;",at=@At("HEAD"),cancellable=true)
    private void mite$sickSheep(Sheep animal,CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation texture=SickLivestockVisuals.bodyTexture(animal,((LivingEntityRenderer<?,?>)(Object)this).getModel());
        if(texture!=null) cir.setReturnValue(texture);
    }
}
