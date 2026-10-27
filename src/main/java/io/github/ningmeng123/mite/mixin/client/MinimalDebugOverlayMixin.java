package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retain FPS, the displayed world day and time, and the requested hardware summary. @author NingMeng123 */
@Mixin(DebugScreenOverlay.class)
public abstract class MinimalDebugOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract boolean showDebugScreen();
    @Shadow public abstract void toggleOverlay();
    @Shadow protected abstract List<String> collectSystemInformationText();

    @Shadow
    private void renderLines(GuiGraphics graphics, List<String> lines, boolean left) {
        throw new AssertionError();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mite$drawFpsAndPerformance(GuiGraphics graphics, CallbackInfo ci) {
        if (showDebugScreen()) {
            graphics.drawManaged(() -> {
                List<String> performance = new ArrayList<>(collectSystemInformationText());
                // Optional extensions can supply direct-buffer and off-heap memory information.
                // Never render its left list: listeners cannot restore coordinates or world details.
                NeoForge.EVENT_BUS.post(new CustomizeGuiOverlayEvent.DebugText(
                        minecraft.getWindow(), graphics, minecraft.getTimer(), new ArrayList<>(), performance));
                renderLines(graphics, mite$hardwareSummary(performance), false);
                // R196 GuiIngame -> Gui.drawString -> FontRenderer.drawStringWithShadow.
                graphics.drawString(minecraft.font, Integer.toString(minecraft.getFps()), 2, 2, 0xE0E0E0, true);
                if (minecraft.level != null) {
                    long dayTime = minecraft.level.getDayTime();
                    graphics.drawString(minecraft.font, Component.translatable("gui.mite.world_day",
                            mite$displayDay(dayTime)), 2, 12, 0xE0E0E0, true);
                    graphics.drawString(minecraft.font, Component.translatable("gui.mite.world_time",
                            mite$displayTime(dayTime), Component.translatable(mite$displayTimePeriod(dayTime))),
                            2, 22, 0xE0E0E0, true);
                }
            });
        }
        // Do not collect the modern left-hand world information or raycast debug targets.
        ci.cancel();
    }

    @Unique
    private static long mite$displayDay(long time) {
        // R196 getDayOfWorld / R196StructureGeneration.displayDay: display days start at raw tick 18000.
        // Keep this distinct from LunarCalendar's unadjusted special-moon day; avoid time + 6000 overflow.
        long safe = Math.max(time, 0L);
        return safe / 24_000L + (safe % 24_000L + 6_000L) / 24_000L + 1L;
    }

    @Unique
    private static String mite$displayTime(long time) {
        // World tick zero is 06:00. Reduce before adding the offset to avoid long overflow.
        int ticks = (int)((Math.floorMod(time, 24_000L) + 6_000L) % 24_000L);
        return String.format(Locale.ROOT, "%02d:%02d", ticks / 1_000, ticks % 1_000 * 60 / 1_000);
    }

    @Unique
    private static String mite$displayTimePeriod(long time) {
        return LunarCalendar.daytime((int)Math.floorMod(time, 24_000L))
                ? "gui.mite.time.day" : "gui.mite.time.night";
    }

    @Unique
    private static List<String> mite$hardwareSummary(List<String> lines) {
        List<String> summary = new ArrayList<>();
        boolean separateGroup = false;
        int displayRows = 0;
        for (String line : lines) {
            boolean displayDetail = displayRows > 0;
            if (displayDetail) displayRows--;
            String plain = ChatFormatting.stripFormatting(line);
            if (plain == null || plain.isBlank()) {
                if (!summary.isEmpty()) separateGroup = true;
                continue;
            }
            boolean display = plain.startsWith("Display:");
            boolean hardware = display || plain.startsWith("Java:") || plain.startsWith("Mem:")
                    || plain.startsWith("Allocation rate:") || plain.startsWith("Direct Buffers:")
                    || plain.startsWith("Allocated:") || plain.startsWith("Off-Heap:") || plain.startsWith("CPU:");
            if (displayDetail && (hardware || mite$extraDebugSection(plain))) {
                // A listener inserted another section into the native pair; do not treat its
                // heading or following properties as a graphics renderer/version continuation.
                displayDetail = false;
                displayRows = 0;
            }
            if (!hardware && !displayDetail) continue;
            if (separateGroup && !summary.isEmpty()) summary.add("");
            summary.add(line);
            separateGroup = false;
            // Native getSystemInformation puts the renderer and OpenGL version directly after Display.
            if (display) displayRows = 2;
        }
        return summary;
    }

    @Unique
    private static boolean mite$extraDebugSection(String line) {
        return line.startsWith("Sodium ") || line.startsWith("ModernFix")
                || line.startsWith("Iris ") || line.startsWith("[Iris]")
                || line.startsWith("Pool:") || line.startsWith("TransferQueue:")
                || line.startsWith("ChunkBuilder:") || line.startsWith("Task:") || line.startsWith("Tasks:")
                || line.startsWith("XYZ:") || line.startsWith("Block:") || line.startsWith("Chunk:")
                || line.startsWith("Facing:") || line.startsWith("Biome:")
                || line.startsWith("Local Difficulty:") || line.startsWith("Targeted ");
    }

    @ModifyExpressionValue(method = "getSystemInformation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;showOnlyReducedInfo()Z"))
    private boolean mite$hardwareInformationOnly(boolean original) {
        // The native early return follows all hardware rows, including Sodium's additions.
        // It precedes all targeted block/fluid/entity rows and their properties and tags.
        return true;
    }

    @ModifyExpressionValue(method = "getSystemInformation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;isDemo()Z"))
    private boolean mite$omitDemoWorldTime(boolean original) {
        return false;
    }

    @Inject(method = {"showProfilerChart", "showFpsCharts", "showNetworkCharts"},
            at = @At("HEAD"), cancellable = true)
    private void mite$hideDebugCharts(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = {"toggleProfilerChart", "toggleFpsCharts", "toggleNetworkCharts"},
            at = @At("HEAD"), cancellable = true)
    private void mite$toggleFpsOverlayInsteadOfCharts(CallbackInfo ci) {
        toggleOverlay();
        ci.cancel();
    }
}
