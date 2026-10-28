package io.github.ningmeng123.mite.compat.enderdragonplus;

import io.github.ningmeng123.mite.Mite;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Retain the ITE dragon profile after EnderDragonPlus initializes its skills. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class EnderDragonPlusHealthCompat {
    private static final String ITE_PROFILE = "modern_ite:source_profile";
    private static final String ITE_BOSS = "modern_ite:boss";
    private static final Map<EntityJoinLevelEvent, Double> BEFORE_JOIN = Collections.synchronizedMap(new WeakHashMap<>());
    private static ModConfigSpec.DoubleValue enderDragonPlusHealth;
    private static double iteProfileHealth = Double.NaN;
    private static boolean unavailable;

    private EnderDragonPlusHealthCompat() {}

    public static boolean enabled() {
        return ModList.get().isLoaded("enderdragonplus") && ModList.get().isLoaded("modern_ite");
    }

    private static boolean hasIteProfile(EnderDragon dragon) {
        return dragon.getPersistentData().getBoolean(ITE_PROFILE) && dragon.getPersistentData().getBoolean(ITE_BOSS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void capture(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && !event.getLevel().isClientSide && enabled()
                && event.getEntity() instanceof EnderDragon dragon && hasIteProfile(dragon)) {
            BEFORE_JOIN.put(event, dragon.getAttributeBaseValue(Attributes.MAX_HEALTH));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void restore(EntityJoinLevelEvent event) {
        Double previous = BEFORE_JOIN.remove(event);
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !enabled()
                || !(event.getEntity() instanceof EnderDragon dragon) || !dragon.isAlive() || !hasIteProfile(dragon)
                || !resolveProfiles(level)) return;
        var attribute = dragon.getAttribute(Attributes.MAX_HEALTH);
        double edpHealth = enderDragonPlusHealth.get();
        // An inactive EDP fight, or a later custom attribute owner, has nothing to repair.
        if (attribute == null || attribute.getBaseValue() != edpHealth) return;
        // Old 400-HP saves already carry the ITE marker. Other saved/custom bases remain authoritative.
        double desired = previous != null && previous != edpHealth && Double.isFinite(previous) && previous > 0D
                ? previous : iteProfileHealth;
        if (attribute.getBaseValue() == desired) return;
        float maximum = dragon.getMaxHealth();
        float ratio = maximum > 0F ? Math.clamp(dragon.getHealth() / maximum, 0F, 1F) : 1F;
        attribute.setBaseValue(desired);
        dragon.setHealth(ratio * dragon.getMaxHealth());
    }

    private static synchronized boolean resolveProfiles(ServerLevel level) {
        if (unavailable) return false;
        if (Double.isFinite(iteProfileHealth)) return true;
        EnderDragon prototype = null;
        try {
            enderDragonPlusHealth = (ModConfigSpec.DoubleValue)Class.forName("com.enderdragonplus.EdpConfig")
                    .getField("MAX_HEALTH").get(null);
            Method initialize = Class.forName("io.github.ningmeng123.modernite.entity.ExtremeNativeMobs")
                    .getMethod("initialize", Mob.class);
            // This public initializer only assigns attributes/data for a dragon. Never spawn or tick the probe.
            prototype = EntityType.ENDER_DRAGON.create(level);
            if (prototype == null) throw new IllegalStateException("Cannot construct ITE dragon profile probe");
            initialize.invoke(null, prototype);
            double expected = prototype.getAttributeBaseValue(Attributes.MAX_HEALTH);
            if (!hasIteProfile(prototype) || !Double.isFinite(expected) || expected <= 0D)
                throw new IllegalStateException("Invalid ITE dragon profile");
            iteProfileHealth = expected;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            unavailable = true;
            Mite.LOGGER.warn("Cannot resolve the optional ITE/EnderDragonPlus health profiles; retaining the current dragon health", exception);
            return false;
        } finally {
            if (prototype != null) prototype.discard();
        }
    }
}
