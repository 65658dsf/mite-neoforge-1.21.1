package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a validated total player limit beside the native LAN port field. @author NingMeng123 */
@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {
    @Shadow private EditBox portEdit;
    @Unique private EditBox mite$maxPlayersEdit;
    @Unique private Button mite$startButton;
    @Unique private String mite$portValue;

    protected ShareToLanScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void mite$rememberPort(CallbackInfo ci) {
        mite$portValue = portEdit == null ? null : portEdit.getValue();
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/EditBox;setResponder(Ljava/util/function/Consumer;)V"))
    private void mite$combinePortValidation(EditBox edit, Consumer<String> responder, Operation<Void> original,
                                            @Local Button startButton) {
        mite$startButton = startButton;
        original.call(edit, (Consumer<String>) value -> {
            responder.accept(value);
            mite$refreshStartButton();
        });
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void mite$addPlayerLimit(CallbackInfo ci) {
        String value = mite$maxPlayersEdit == null
                ? Integer.toString(Math.clamp(minecraft.getSingleplayerServer().getMaxPlayers(), 1, 999))
                : mite$maxPlayersEdit.getValue();
        portEdit.setX(width / 2 - 155);
        mite$maxPlayersEdit = addRenderableWidget(new EditBox(font, width / 2 + 5, 160, 150, 20,
                Component.translatable("mite.lan.max_players")));
        mite$maxPlayersEdit.setMaxLength(10);
        mite$maxPlayersEdit.setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '9'));
        mite$maxPlayersEdit.setResponder(text -> {
            boolean valid = mite$playerLimit() != -1;
            mite$maxPlayersEdit.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : 0xFF5555);
            mite$maxPlayersEdit.setTooltip(Tooltip.create(Component.translatable(valid
                    ? "mite.lan.max_players.tooltip" : "mite.lan.max_players.invalid")));
            mite$refreshStartButton();
        });
        mite$maxPlayersEdit.setValue(value);
        // Re-run native validation when resize rebuilds the port field.
        if (mite$portValue != null) portEdit.setValue(mite$portValue);
    }

    @Unique
    private int mite$playerLimit() {
        if (mite$maxPlayersEdit == null) return -1;
        try {
            int value = Integer.parseInt(mite$maxPlayersEdit.getValue());
            return value >= 1 && value <= 999 ? value : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Unique
    private void mite$refreshStartButton() {
        mite$startButton.active = portEdit.getTooltip() == null && mite$playerLimit() != -1;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
            ordinal = 2), index = 2)
    private int mite$movePortLabel(int centerX) {
        return centerX - 80;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mite$renderPlayerLimitLabel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        graphics.drawCenteredString(font, Component.translatable("mite.lan.max_players"), width / 2 + 80, 142, 0xFFFFFF);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/server/IntegratedServer;publishServer(Lnet/minecraft/world/level/GameType;ZI)Z"))
    private boolean mite$publishWithPlayerLimit(IntegratedServer server, GameType gameMode, boolean commands, int port,
                                               Operation<Boolean> original) {
        int limit = mite$playerLimit();
        if (limit == -1) return false;
        int previousLimit = server.getMaxPlayers();
        mite$setPlayerLimit(server, limit);
        boolean published = false;
        try {
            published = original.call(server, gameMode, commands, port);
            return published;
        } finally {
            if (!published) mite$setPlayerLimit(server, previousLimit);
        }
    }

    @Unique
    private static void mite$setPlayerLimit(IntegratedServer server, int limit) {
        server.executeBlocking(() -> {
            ((LanPlayerListAccess) server.getPlayerList()).mite$setMaxPlayers(limit);
            server.invalidateStatus();
        });
    }
}
