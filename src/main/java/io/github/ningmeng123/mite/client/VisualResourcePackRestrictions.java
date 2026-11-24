package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.VisualPackBlockedPayload.Reason;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Filters selected local packs before resource reload and reapplies policy on world changes. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class VisualResourcePackRestrictions {
    private static EnumSet<Reason> detected = EnumSet.noneOf(Reason.class);
    private static EnumSet<Reason> blocked = EnumSet.noneOf(Reason.class);
    private static EnumSet<Reason> loading = EnumSet.noneOf(Reason.class);
    private static EnumSet<Reason> loaded = EnumSet.noneOf(Reason.class);
    private static final EnumSet<Reason> PENDING_NOTICES = EnumSet.noneOf(Reason.class);
    private static Map<String, EnumSet<Reason>> blockedPacks = Map.of();
    private static ClientPacketListener notifiedConnection;
    private static CompletableFuture<?> resourceReload;

    private VisualResourcePackRestrictions() {}

    public static List<PackResources> filter(Minecraft minecraft, List<PackResources> packs) {
        finishReload();
        var selected = new ArrayList<PackResources>(packs.size());
        var nextDetected = EnumSet.noneOf(Reason.class);
        var nextBlocked = EnumSet.noneOf(Reason.class);
        var nextLoading = EnumSet.noneOf(Reason.class);
        var nextBlockedPacks = new HashMap<String, EnumSet<Reason>>();
        for (var pack : packs) {
            if (pack.location().source() != PackSource.DEFAULT) {
                selected.add(pack);
                continue;
            }
            var reasons = detect(pack);
            var violations = violations(minecraft, reasons);
            nextDetected.addAll(reasons);
            if (violations.isEmpty()) {
                selected.add(pack);
                nextLoading.addAll(reasons);
            } else {
                nextBlocked.addAll(violations);
                nextBlockedPacks.put(pack.packId(), violations);
                var newViolations = violations.clone();
                var previousViolations = blockedPacks.get(pack.packId());
                if (previousViolations != null) newViolations.removeAll(previousViolations);
                PENDING_NOTICES.addAll(newViolations);
                Mite.LOGGER.debug("Blocking client resource pack {}: {}", pack.packId(), violations);
                pack.close();
            }
        }
        detected = nextDetected;
        blocked = nextBlocked;
        blockedPacks = nextBlockedPacks;
        loading = nextLoading;
        PENDING_NOTICES.retainAll(blocked);
        return selected;
    }

    private static EnumSet<Reason> detect(PackResources pack) {
        var reasons = EnumSet.noneOf(Reason.class);
        if (VisualLightmapDetector.matches(pack)) reasons.add(Reason.NIGHT_VISION_RESOURCE_PACK);
        if (XrayResourcePackDetector.matches(pack) || HiddenTerrainDetector.matches(pack)) {
            reasons.add(Reason.XRAY_RESOURCE_PACK);
        }
        return reasons;
    }

    public static void trackReload(CompletableFuture<?> completion) {
        resourceReload = completion;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        if (connection != notifiedConnection) {
            notifiedConnection = connection;
            PENDING_NOTICES.addAll(blocked);
        }
        if (detected.isEmpty()) return;
        if (!violations(minecraft, detected).equals(blocked) && !(minecraft.getOverlay() instanceof LoadingOverlay)) {
            minecraft.reloadResourcePacks();
        }
        PENDING_NOTICES.removeIf(reason -> VisualPackPolicy.report(minecraft, reason));
    }

    public static boolean shouldHideWorld(Minecraft minecraft) {
        finishReload();
        // An already-loaded pack can become forbidden while the replacement assets are loading.
        return hasViolation(minecraft, loaded) || resourceReload != null && hasViolation(minecraft, loading);
    }

    private static void finishReload() {
        if (resourceReload != null && resourceReload.isDone()) {
            if (!resourceReload.isCompletedExceptionally()) loaded = loading;
            resourceReload = null;
        }
    }

    private static EnumSet<Reason> violations(Minecraft minecraft, EnumSet<Reason> reasons) {
        var result = reasons.clone();
        result.removeIf(reason -> !VisualPackPolicy.blocks(minecraft, reason));
        return result;
    }

    private static boolean hasViolation(Minecraft minecraft, EnumSet<Reason> reasons) {
        for (var reason : reasons) if (VisualPackPolicy.blocks(minecraft, reason)) return true;
        return false;
    }
}
