package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.client.L2AttributePanel;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Present effective MITE ranges in the optional L2 attribute panel. @author NingMeng123 */
@Pseudo
@Mixin(targets = "dev.xkmc.l2tabs.tabs.contents.BaseAttributeScreen", remap = false)
public abstract class L2AttributePanelMixin {
    @WrapOperation(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIFLnet/minecraft/world/entity/LivingEntity;Ljava/util/List;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"),
            require = 1, allow = 1)
    private double mite$displayEffectiveRange(LivingEntity entity, Holder<Attribute> attribute, Operation<Double> original) {
        Double value = L2AttributePanel.value(entity, attribute);
        return value == null ? original.call(entity, attribute) : value;
    }

    @Inject(method = "getAttributeDetail(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/Holder;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void mite$explainEffectiveRange(LivingEntity entity, Holder<Attribute> attribute,
            CallbackInfoReturnable<List<Component>> callback) {
        if (L2AttributePanel.handles(entity, attribute)) {
            callback.setReturnValue(L2AttributePanel.details(entity, attribute,
                    entity.level().isClientSide && Screen.hasShiftDown()));
        }
    }
}
