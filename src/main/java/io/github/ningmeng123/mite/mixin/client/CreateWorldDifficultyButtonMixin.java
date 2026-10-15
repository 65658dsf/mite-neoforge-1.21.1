package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.client.DifficultyCreationUi;
import net.minecraft.client.gui.components.CycleButton;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The native state listeners may update every button; only the difficulty selector is fixed. @author NingMeng123 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldDifficultyButtonMixin {
    @WrapOperation(method = "*", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/components/CycleButton;active:Z", opcode = Opcodes.PUTFIELD), require = 3)
    private static void mite$keepDifficultyFixed(CycleButton<?> button, boolean requested, Operation<Void> original) {
        original.call(button, DifficultyCreationUi.active(button.getValue(), requested));
    }
}
