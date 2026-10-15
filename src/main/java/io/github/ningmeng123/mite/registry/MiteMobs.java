package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.*;
import io.github.ningmeng123.mite.rules.R196MobRules;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All and only the 29 concrete added source species, plus its actually used thrown-web helper. @author NingMeng123 */
public final class MiteMobs {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final Map<MobKind, Supplier<? extends EntityType<? extends Mob>>> MOBS = new EnumMap<>(MobKind.class);
    public static final DeferredHolder<EntityType<?>, EntityType<R196Web>> WEB = TYPES.register("thrown_web", () ->
            EntityType.Builder.<R196Web>of(R196Web::new, MobCategory.MISC).sized(.25F, .25F).clientTrackingRange(4).updateInterval(10).build("mite:thrown_web"));
    static {
        for (MobKind kind : MobKind.values()) {
            switch (kind.family()) {
                case UNDEAD -> MiteMobs.<R196Undead>add(kind, (t, l) -> new R196Undead(t, l, kind));
                case SPIDER -> MiteMobs.<R196Spider>add(kind, (t, l) -> new R196Spider(t, l, kind));
                case WOLF -> MiteMobs.<R196Wolf>add(kind, (t, l) -> new R196Wolf(t, l, kind));
                case CREEPER -> MiteMobs.<R196InfernalCreeper>add(kind, (t, l) -> new R196InfernalCreeper(t, l, kind));
                case ZOMBIE -> MiteMobs.<R196Zombie>add(kind, (t, l) -> new R196Zombie(t, l, kind));
                case SKELETON -> MiteMobs.<R196Skeleton>add(kind, (t, l) -> new R196Skeleton(t, l, kind));
                case BAT -> MiteMobs.<R196Bat>add(kind, (t, l) -> new R196Bat(t, l, kind));
                case EARTH -> MiteMobs.<R196Elemental>add(kind, (t, l) -> new R196Elemental(t, l, kind));
                case FIRE -> MiteMobs.<R196FireElemental>add(kind, (t, l) -> new R196FireElemental(t, l, kind));
                case CUBE -> MiteMobs.<R196Cube>add(kind, (t, l) -> new R196Cube(t, l, kind));
                case SILVERFISH -> MiteMobs.<R196Silverfish>add(kind, (t, l) -> new R196Silverfish(t, l, kind));
            }
            var row = R196MobRules.sourceRow(kind);
            var egg = row.getAsJsonObject("spawn_egg");
            int base = egg.get("nu.b").getAsInt(), accent = egg.get("nu.c").getAsInt();
            ITEMS.registerItem(kind.path() + "_spawn_egg", p -> new DeferredSpawnEggItem(() -> type(kind), base, accent, p.stacksTo(16)));
        }
    }
    private MiteMobs() {}
    private static <T extends Mob> void add(MobKind kind, EntityType.EntityFactory<T> factory) {
        MOBS.put(kind, TYPES.register(kind.path(), () -> {
            var entry = R196MobRules.entry(kind);
            var builder = EntityType.Builder.of(factory, kind.category()).sized(entry.width(), entry.height()).clientTrackingRange(8);
            var state = R196MobRules.sourceRow(kind).getAsJsonArray("variants").get(0).getAsJsonObject();
            if (!state.get("harmed_by_fire").getAsBoolean() && !state.get("harmed_by_lava").getAsBoolean()) builder.fireImmune();
            return builder.build("mite:" + kind.path());
        }));
    }
    public static EntityType<? extends Mob> type(MobKind kind) { return MOBS.get(kind).get(); }
    public static void register(IEventBus bus) {
        TYPES.register(bus); ITEMS.register(bus);
        bus.addListener(MiteMobs::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        for (MobKind kind : MobKind.values()) event.put(type(kind), R196MobRules.attributes(kind).build());
    }
}
