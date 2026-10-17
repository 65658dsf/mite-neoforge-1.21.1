package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.client.AutoHarvestInput;
import io.github.ningmeng123.mite.network.AutoRestockScreenPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the native held-attack pipeline and all its server-side mining checks. @author NingMeng123 */
@Mixin(Minecraft.class)
public abstract class AutoHarvestMinecraftMixin {
    @Shadow private int rightClickDelay;
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void mite$readAutoHarvestGesture(CallbackInfo ci) { AutoHarvestInput.beginInput((Minecraft)(Object)this); }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean mite$continueMatchingAutoHarvest(KeyMapping key, Operation<Boolean> original) {
        boolean held = original.call(key);
        Minecraft minecraft = (Minecraft)(Object)this;
        if (key == minecraft.options.keyAttack && AutoHarvestInput.consumeAttack(minecraft)) return false;
        return held || key == minecraft.options.keyAttack && AutoHarvestInput.wantsContinue(minecraft)
                || key == minecraft.options.keyUse && AutoHarvestInput.wantsUse(minecraft);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void mite$consumeAutoUseGesture(CallbackInfoReturnable<Boolean> cir) {
        if (AutoHarvestInput.consumeAttack((Minecraft)(Object)this)) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"))
    private void mite$recordContinuousAttempt(boolean held, CallbackInfo ci) {
        AutoHarvestInput.recordAttempt((Minecraft)(Object)this, held);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void mite$consumeAutoHarvestGesture(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft)(Object)this;
        if (AutoHarvestInput.consumeUse(minecraft)) ci.cancel();
        else if (AutoHarvestInput.automaticUse(minecraft)) {
            rightClickDelay = 4;
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void mite$clearAutoHarvestForScreen(Screen screen, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft)(Object)this;
        if (minecraft.player != null && minecraft.getConnection() != null && minecraft.getConnection().hasChannel(AutoRestockScreenPayload.TYPE))
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AutoRestockScreenPayload(screen != null));
        if (screen != null) AutoHarvestInput.reset();
    }

    @Inject(method = "setWindowActive", at = @At("HEAD"))
    private void mite$clearAutoHarvestOnFocusLoss(boolean active, CallbackInfo ci) { if (!active) AutoHarvestInput.reset(); }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void mite$clearAutoHarvestOnDisconnect(CallbackInfo ci) { AutoHarvestInput.reset(); }
}
