package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.advancement.StructureAdvancements;
import io.github.ningmeng123.mite.client.StructureProgressTooltip;
import io.github.ningmeng123.mite.network.StructureProgressPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rebuild only structure tooltips from the latest authoritative counters. @author NingMeng123 */
@Mixin(AdvancementWidget.class)
public abstract class StructureAdvancementTooltipMixin {
    @Shadow @Final private AdvancementNode advancementNode;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private FormattedCharSequence title;
    @Shadow @Final @Mutable private List<FormattedCharSequence> description;
    @Shadow @Final @Mutable private int width;

    @Inject(method = "drawHover", at = @At("HEAD"))
    private void mite$showCurrentRequirements(GuiGraphics graphics, int x, int y, float fade,
            int screenX, int screenY, CallbackInfo ci) {
        var entry = StructureAdvancements.entry(advancementNode.holder().id());
        var snapshot = StructureProgressPayload.latest();
        if (entry == null || snapshot == null) return;
        int textWidth = Math.min(250, Math.max(155, minecraft.getWindow().getGuiScaledWidth() / 2 - 25));
        List<FormattedCharSequence> lines = new ArrayList<>();
        int count = entry.criteria().size();
        int measured = Math.max(160, 29 + minecraft.font.width(title)
                + minecraft.font.width(Component.translatable("advancements.progress", count, count)) + 8);
        for (var line : StructureProgressTooltip.lines(entry, snapshot)) {
            for (var formatted : minecraft.font.split(line, textWidth)) {
                lines.add(formatted);
                measured = Math.max(measured, minecraft.font.width(formatted));
            }
        }
        description = List.copyOf(lines);
        width = measured + 10;
    }
}
