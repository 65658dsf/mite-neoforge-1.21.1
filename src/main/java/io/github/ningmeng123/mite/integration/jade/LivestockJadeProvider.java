package io.github.ningmeng123.mite.integration.jade;

import io.github.ningmeng123.mite.integration.LivestockInspection;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Optional Jade display of existing animal sickness and recovery metadata. @author NingMeng123 */
public enum LivestockJadeProvider implements IEntityComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mite", "livestock_health");

    @Override public ResourceLocation getUid() { return UID; }
    @Override public int getDefaultPriority() { return 100; }

    @Override public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        LivestockInspection.lines(LivestockInspection.inspect(accessor.getEntity()))
                .forEach(line -> tooltip.add(line, UID));
    }
}
