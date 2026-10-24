package io.github.ningmeng123.mite.mixin.client;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skip the experimental world-creation confirmation requested by the user. @author NingMeng123 */
@Mixin(WorldOpenFlows.class)
public abstract class ExperimentalWorldWarningMixin {
    @Inject(method = "confirmWorldCreation", at = @At("HEAD"), cancellable = true)
    private static void mite$createExperimentalWorld(Minecraft minecraft, CreateWorldScreen screen,
            Lifecycle lifecycle, Runnable create, boolean skipWarnings, CallbackInfo ci) {
        if (lifecycle == Lifecycle.experimental()) {
            create.run();
            ci.cancel();
        }
    }
}
