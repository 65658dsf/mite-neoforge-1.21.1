package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;

/** R196 abg helpers use the strongest worn enchantment and source float rounding. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class NativeProtectionEnchantments {
    private NativeProtectionEnchantments() {}

    /** Called only when a new ignition is requested, after the native burning-time attribute. */
    public static int ignitionTicks(Entity entity, int ticks) {
        if (!(entity instanceof LivingEntity living) || ticks <= 0) return ticks;
        double reduction = EnchantmentSources.equipped(living, "minecraft:fire_protection",
                value -> Math.ceil((float)ticks * (float)value * .15F));
        return Math.max(0, ticks - (int)reduction);
    }

    public static double explosionImpulse(double impulse, int highestLevel) {
        if (highestLevel <= 0 || impulse <= 0) return impulse;
        return Math.max(0D, impulse - Math.floor(impulse * (double)((float)highestLevel * .15F)));
    }

    public static Vec3 explosionKnockback(LivingEntity entity, Vec3 velocity) {
        double length = velocity.length();
        if (length <= 0D) return velocity;
        double reduction = EnchantmentSources.equipped(entity, "minecraft:blast_protection",
                value -> Math.floor(length * (double)((float)value * .15F)));
        double reduced = Math.max(0D, length - reduction);
        return reduced == length ? velocity : velocity.scale(reduced / length);
    }

    /** The event runs after vanilla/external resistance attributes and retains their resulting velocity. */
    @SubscribeEvent
    public static void explosionKnockback(ExplosionKnockbackEvent event) {
        if (!event.getLevel().isClientSide && event.getAffectedEntity() instanceof LivingEntity living)
            event.setKnockbackVelocity(explosionKnockback(living, event.getKnockbackVelocity()));
    }
}
