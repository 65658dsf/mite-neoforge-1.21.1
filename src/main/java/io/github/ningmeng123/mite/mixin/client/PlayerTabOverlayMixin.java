package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.client.TabListStatusLayout;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Aligns level and latency columns to the native signal icon. @author NingMeng123 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract Component getNameForDisplay(PlayerInfo player);
    @Unique private int mite$levelWidth;
    @Unique private int mite$latencyWidth;
    @Unique private int mite$statusWidth;

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getPlayerInfos()Ljava/util/List;"))
    private List<PlayerInfo> mite$measureStatusColumns(List<PlayerInfo> players) {
        mite$levelWidth = 0;
        mite$latencyWidth = 0;
        mite$statusWidth = 0;
        for (PlayerInfo player : players) {
            var parts = TabListStatusLayout.split(getNameForDisplay(player));
            if (parts == null) continue;
            mite$levelWidth = Math.max(mite$levelWidth, minecraft.font.width(parts.level()));
            mite$latencyWidth = Math.max(mite$latencyWidth, minecraft.font.width(parts.latency()));
            mite$statusWidth = TabListStatusLayout.NAME_GAP + mite$levelWidth
                    + TabListStatusLayout.STATUS_GAP + mite$latencyWidth;
        }
        return players;
    }

    // Reserve the same status width for every row, independently of its name or numbers.
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I", ordinal = 0))
    private int mite$reserveStatusColumns(Font font, FormattedText displayName, Operation<Integer> original) {
        var parts = displayName instanceof Component component ? TabListStatusLayout.split(component) : null;
        return original.call(font, parts == null ? displayName : parts.name()) + mite$statusWidth;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"), index = 1)
    private Component mite$drawOnlyName(Component displayName) {
        var parts = TabListStatusLayout.split(displayName);
        return parts == null ? displayName : parts.name();
    }

    // Vanilla places scores after its measured name width, which includes our reserved space.
    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/PlayerTabOverlay;renderTablistScore(Lnet/minecraft/world/scores/Objective;ILnet/minecraft/client/gui/components/PlayerTabOverlay$ScoreDisplayEntry;IILjava/util/UUID;Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void mite$keepScoreBeforeStatus(Args args) {
        args.set(3, args.<Integer>get(3) - mite$statusWidth);
        args.set(4, args.<Integer>get(4) - mite$statusWidth);
    }

    @Inject(method = "renderPingIcon", at = @At("HEAD"))
    private void mite$drawStatusBySignal(GuiGraphics graphics, int columnWidth, int columnX, int y,
            PlayerInfo player, CallbackInfo ci) {
        var parts = TabListStatusLayout.split(getNameForDisplay(player));
        if (parts == null) return;
        int right = columnX + columnWidth - TabListStatusLayout.PING_RIGHT_INSET;
        int color = player.getGameMode() == GameType.SPECTATOR ? -1862270977 : -1;
        graphics.drawString(minecraft.font, parts.latency(), right - minecraft.font.width(parts.latency()), y, color);
        graphics.drawString(minecraft.font, parts.level(), right - mite$latencyWidth
                - TabListStatusLayout.STATUS_GAP - minecraft.font.width(parts.level()), y, color);
    }
}
