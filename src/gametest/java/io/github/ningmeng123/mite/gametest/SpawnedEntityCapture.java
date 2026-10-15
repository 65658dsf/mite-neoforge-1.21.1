package io.github.ningmeng123.mite.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Synchronous spawn assertions must not depend on random-grid chunk tracking becoming visible. @author NingMeng123 */
final class SpawnedEntityCapture implements AutoCloseable {
    private final List<Entity> entities=new ArrayList<>();
    private final Consumer<EntityJoinLevelEvent> listener;
    SpawnedEntityCapture(ServerLevel level,Predicate<Entity> filter) {
        listener=event->{if(!event.isCanceled()&&event.getLevel()==level&&filter.test(event.getEntity()))entities.add(event.getEntity());};
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityJoinLevelEvent.class,listener);
    }
    <T extends Entity> List<T> live(Class<T> type) {
        return entities.stream().filter(e->!e.isRemoved()&&type.isInstance(e)).map(type::cast).toList();
    }
    @Override public void close() { NeoForge.EVENT_BUS.unregister(listener);entities.forEach(Entity::discard);entities.clear(); }
}
