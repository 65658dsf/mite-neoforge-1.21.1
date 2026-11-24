package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.client.NightVisionShaderPack;
import io.github.ningmeng123.mite.client.VisualPackPolicy;
import io.github.ningmeng123.mite.network.VisualPackBlockedPayload.Reason;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enforces the current world's shader restriction before Iris builds its pipeline. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public abstract class IrisNightVisionShaderMixin {
    @Shadow public static void loadShaderpack() { throw new AssertionError(); }
    @Shadow private static void destroyEverything() { throw new AssertionError(); }
    @Shadow private static void setShadersDisabled() { throw new AssertionError(); }

    @Unique private static boolean mite$nightVisionRequested;
    @Unique private static boolean mite$nightVisionBlocked;
    @Unique private static ClientPacketListener mite$notifiedConnection;

    @Inject(method = "loadShaderpack()V", at = @At("HEAD"))
    private static void mite$beginShaderLoad(CallbackInfo ci) {
        mite$nightVisionRequested = false;
        mite$nightVisionBlocked = false;
    }

    @Inject(method = "loadExternalShaderpack(Ljava/lang/String;)Z", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/Iris;tryReadConfigProperties(Ljava/nio/file/Path;)Ljava/util/Optional;"),
            cancellable = true)
    private static void mite$filterShaderPack(String name, CallbackInfoReturnable<Boolean> cir,
                                              @Local(ordinal = 2) Path shaderRoot) {
        mite$nightVisionRequested = NightVisionShaderPack.matches(shaderRoot);
        if (!mite$nightVisionRequested || !VisualPackPolicy.blocks(Minecraft.getInstance(), Reason.NIGHT_VISION_SHADER)) return;

        mite$nightVisionBlocked = true;
        setShadersDisabled();
        // Iris has a usable vanilla pipeline and can keep the player's selected pack for other worlds.
        cir.setReturnValue(true);
    }

    @Inject(method = "loadShaderpack()V", at = @At("RETURN"))
    private static void mite$finishShaderLoad(CallbackInfo ci) {
        if (!mite$nightVisionBlocked) mite$notifiedConnection = null;
    }

    @Inject(method = "handleKeybinds(Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
    private static void mite$applyShaderRule(Minecraft minecraft, CallbackInfo ci) {
        var connection = minecraft.getConnection();
        if (connection == null) mite$notifiedConnection = null;
        if (!mite$nightVisionRequested) return;

        if (VisualPackPolicy.blocks(minecraft, Reason.NIGHT_VISION_SHADER) != mite$nightVisionBlocked) {
            destroyEverything();
            loadShaderpack();
        }

        if (mite$nightVisionBlocked && connection != mite$notifiedConnection
                && VisualPackPolicy.report(minecraft, Reason.NIGHT_VISION_SHADER)) {
            mite$notifiedConnection = connection;
        }
    }
}
