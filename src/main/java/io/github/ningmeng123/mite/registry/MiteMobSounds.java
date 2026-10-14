package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.entity.MobKind;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Original sound-event identities, with modern plural sounds directory. @author NingMeng123 */
public final class MiteMobSounds {
    private static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, "mite");
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> EVENTS = new HashMap<>();
    static {
        for (String mob : new String[]{"ghoul", "wight", "invisiblestalker", "shadow", "hellhound", "demonspider"})
            for (String action : new String[]{"say", "hurt", "death"}) add("imported.mob." + mob + "." + action);
        add("imported.mob.hellhound.breath");
        for (String action : new String[]{"cackle", "hurt", "death"}) add("imported.mob.witch." + action);
        add("imported.mob.wolf.howl");
    }
    private static void add(String path) { EVENTS.put(path, REGISTER.register(path, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.parse("mite:" + path)))); }
    public static void register(IEventBus bus) { REGISTER.register(bus); }
    public static SoundEvent get(String path) { return EVENTS.get(path).get(); }
    public static SoundEvent get(MobKind kind, String action) {
        String mob = switch (kind) { case INVISIBLE_STALKER -> "invisiblestalker"; case DEMON_SPIDER -> "demonspider"; default -> kind.path(); };
        return get("imported.mob." + mob + "." + action);
    }
    private MiteMobSounds() {}
}
