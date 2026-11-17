package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.portal.MitePortalProperties;
import io.github.ningmeng123.mite.portal.MitePortals;
import java.lang.ref.WeakReference;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** R196 portal particles and the matching animated entry overlay. @author NingMeng123 */
public final class PortalVisuals {
    private static WeakReference<ClientLevel> overlayLevel = new WeakReference<>(null);
    @Nullable private static BlockState lastOverlayState;

    private PortalVisuals() {}

    public static void colorParticle(Particle particle, ClientLevel level, BlockPos pos, float brightness) {
        if (particle.getClass() != PortalParticle.class) return;
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.NETHER_PORTAL)) return;
        switch (MitePortals.destinationId(level, state)) {
            case 0 -> particle.setColor(brightness * 8F / 255F, brightness * 121F / 255F, brightness * 153F / 255F);
            case 2 -> particle.setColor(brightness * 190F / 255F, brightness * 37F / 255F, brightness * 11F / 255F);
            default -> { } // Destination 1 uses the native purple particle color.
        }
    }

    public static BlockState overlayState(BlockState fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (overlayLevel.get() != level) {
            overlayLevel = new WeakReference<>(level);
            lastOverlayState = null;
        }
        if (level == null || minecraft.player == null) return fallback;
        if (minecraft.player.portalProcess != null) {
            BlockState state = level.getBlockState(minecraft.player.portalProcess.getEntryPosition());
            if (state.is(Blocks.NETHER_PORTAL)) {
                lastOverlayState = state.setValue(MitePortalProperties.DESTINATION, MitePortals.destinationId(level, state));
            }
        }
        // The source portal can disappear before the native overlay finishes fading.
        return lastOverlayState == null ? fallback : lastOverlayState;
    }
}
