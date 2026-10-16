package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Names the advancement screen without changing other uses of its vanilla translation. @author NingMeng123 */
@Mixin(AdvancementsScreen.class)
public abstract class MiteAdvancementsTitleMixin {
    @Shadow @Final @Mutable
    private static Component TITLE;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void mite$replaceTitle(CallbackInfo ci) {
        TITLE = Component.literal("MITE");
    }
}
