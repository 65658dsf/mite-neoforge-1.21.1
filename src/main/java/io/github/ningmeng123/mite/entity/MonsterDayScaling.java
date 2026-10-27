package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Calendar health and a fixed innate attack base, shared by native and MITE monsters. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MonsterDayScaling {
    public static final String DATA_KEY = "mite:day_scaling";
    public static final ResourceLocation HEALTH_MODIFIER = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "day_health_scaling");
    private static long nextFormulaWarning = Long.MIN_VALUE;
    private MonsterDayScaling() {}

    public static boolean handles(Mob mob) {
        return !(mob instanceof EnderDragon) && !(mob instanceof WitherBoss)
                && !mob.getType().is(Tags.EntityTypes.BOSSES)
                && (mob.getType().getCategory() == MobCategory.MONSTER
                || mob instanceof R196Mob source && source.miteKind() != null && source.miteKind().family() == MobKind.Family.BAT);
    }

    private static long day(Mob mob, ServerLevel level) {
        return Math.max(Math.max(1L, BloodMoonInvasion.highestDay(level)),
                mob.getPersistentData().getCompound(DATA_KEY).getLong("day"));
    }

    /** Kept for extension compatibility; calendar progression no longer increases owned attack damage. */
    public static double attackMultiplier(Mob mob) {
        return 1D;
    }

    /** The innate attribute is evaluated before the native weapon, equipment and effect modifiers. */
    public static double attackBase(Mob mob, double nativeBase) {
        if (!handles(mob) || !(mob.level() instanceof ServerLevel)) return nativeBase;
        double configured = MiteConfig.monsterGrowthSettings().damageBase();
        return configured > 0D ? configured : nativeBase;
    }

    private static void reportFallback(MonsterGrowthRules.Result result) {
        if (!result.fallback()) return;
        long now = System.nanoTime();
        if (now < nextFormulaWarning) return;
        nextFormulaWarning = now + 60_000_000_000L;
        Mite.LOGGER.warn("A monster growth formula failed for its actual runtime inputs or exceeded the blood-moon evaluation budget; using the safe default formula. Check the monster growth settings.");
    }

    /** A unique multiplier composes with equipment/leader attributes without compounding on refresh or load. */
    public static void refresh(Mob mob) {
        if (!mob.isAlive() || !(mob.level() instanceof ServerLevel level)) return;
        if (!handles(mob)) {
            // Old saves may still carry our permanent health modifier from before bosses were excluded.
            var attribute = mob.getAttribute(Attributes.MAX_HEALTH);
            if (attribute != null && attribute.hasModifier(HEALTH_MODIFIER)) {
                float health = mob.getHealth(), maximum = mob.getMaxHealth();
                attribute.removeModifier(HEALTH_MODIFIER);
                float next = mob.getMaxHealth();
                if (next != maximum) mob.setHealth(Math.min(next, health / maximum * next));
            }
            mob.getPersistentData().remove(DATA_KEY);
            return;
        }
        long day = day(mob, level);
        var attribute = mob.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        // Explicitly retain the ordinary1024 ceiling even when an extension raises the attribute range.
        var unscaled = new AttributeInstance(attribute.getAttribute(), ignored -> {});
        unscaled.replaceFrom(attribute);
        unscaled.removeModifier(HEALTH_MODIFIER);
        double baseHealth=unscaled.getValue();
        if (!(baseHealth > 0D) || !Double.isFinite(baseHealth)) return;
        double growth=MiteConfig.monsterGrowthMultiplier() * MiteConfig.monsterHealthGrowthMultiplier();
        var growthResult = MonsterGrowthRules.healthPreview(day, baseHealth, mob instanceof AbstractSkeleton,
                MiteConfig.monsterGrowthSettings(), growth);
        reportFallback(growthResult);
        double limit=io.github.ningmeng123.mite.api.MiteExtensions.policy().mobHealthLimit(mob,1024D);
        if (!Double.isFinite(limit)||limit<1D) throw new IllegalStateException("Invalid monster health limit");
        double amount=Math.min(limit,growthResult.value())/baseHealth-1D;
        var previous = attribute.getModifier(HEALTH_MODIFIER);
        if (amount == 0D ? previous != null : previous == null || previous.amount() != amount
                || previous.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            float health = mob.getHealth(), maximum = mob.getMaxHealth();
            attribute.removeModifier(HEALTH_MODIFIER);
            if (amount != 0D) attribute.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            float next = mob.getMaxHealth();
            // Growth preserves injuries as a fraction; it never turns a wounded or explicitly configured mob full-health.
            if (next != maximum) mob.setHealth(Math.min(next, health / maximum * next));
        }
        CompoundTag saved = mob.getPersistentData().getCompound(DATA_KEY);
        if (saved.getInt("version") != 1 || saved.getLong("day") != day) {
            CompoundTag result = new CompoundTag();
            result.putInt("version", 1); result.putLong("day", day);
            mob.getPersistentData().put(DATA_KEY, result);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && !event.getLevel().isClientSide && event.getEntity() instanceof Mob mob) refresh(mob);
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide
                && Math.floorMod(mob.tickCount + mob.getId(), 20) == 0) refresh(mob);
    }

    /** Legacy extension entry point; incoming damage is no longer changed by the world calendar. */
    public static void damage(LivingIncomingDamageEvent event) {
    }
}
