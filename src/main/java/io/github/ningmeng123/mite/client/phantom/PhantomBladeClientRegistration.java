package io.github.ningmeng123.mite.client.phantom;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registration does not link rendering classes on a dedicated server. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PhantomBladeClientRegistration {
    private PhantomBladeClientRegistration() {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MitePhantomBlades.SWARM.get(), PhantomBladeSwarmRenderer::new);
        event.registerEntityRenderer(MitePhantomBlades.PROJECTILE.get(), PhantomBladeProjectileRenderer::new);
    }
}
