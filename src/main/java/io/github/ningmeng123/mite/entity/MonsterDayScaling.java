package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Calendar health and attributed attack damage, shared by native and MITE monsters. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MonsterDayScaling {
    public static final String DATA_KEY = "mite:day_scaling";
    public static final ResourceLocation HEALTH_MODIFIER = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "day_health_scaling");
    private static long nextFormulaWarning = Long.MIN_VALUE;
    private MonsterDayScaling() {}

    public static boolean handles(Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER
                || mob instanceof R196Mob source && source.miteKind() != null && source.miteKind().family() == MobKind.Family.BAT;
    }

    private static long day(Mob mob, ServerLevel level) {
        return Math.max(Math.max(1L, BloodMoonInvasion.highestDay(level)),
                mob.getPersistentData().getCompound(DATA_KEY).getLong("day"));
    }

    /** Other owned attacks retain their native damage and use only the formula's relative growth. */
    public static double attackMultiplier(Mob mob) {
        if (!handles(mob) || !(mob.level() instanceof ServerLevel level)) return 1D;
        var settings = MiteConfig.monsterGrowthSettings();
        // A configured melee base is not the damage of an arrow, explosion, spell or other owned attack.
        var relative = new MonsterGrowthRules.Settings(settings.healthBase(), 0D, settings.skeletonReference(),
                settings.healthRate(), settings.damageRate(), settings.healthFormula(), settings.damageFormula(),
                settings.healthBloodMoonFormula(), settings.damageBloodMoonFormula());
        var result = MonsterGrowthRules.damagePreview(day(mob, level), 1D, relative,
                MiteConfig.monsterGrowthMultiplier() * MiteConfig.monsterDamageGrowthMultiplier());
        reportFallback(result);
        return result.value();
    }

    /** The innate attribute is evaluated before the native weapon, equipment and effect modifiers. */
    public static double attackBase(Mob mob, double nativeBase) {
        if (!handles(mob) || !(mob.level() instanceof ServerLevel level)) return nativeBase;
        var result = MonsterGrowthRules.damagePreview(day(mob, level), nativeBase, MiteConfig.monsterGrowthSettings(),
                MiteConfig.monsterGrowthMultiplier() * MiteConfig.monsterDamageGrowthMultiplier());
        reportFallback(result);
        return result.value();
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
        if (!handles(mob) || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) return;
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

    /** Native melee already grew its body attribute; other owned attacks grow at the common pre-mitigation boundary. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getAmount() > 0F) || !Float.isFinite(event.getAmount())
                || !(event.getSource().getEntity() instanceof Mob attacker) || !handles(attacker)
                || !(attacker.level() instanceof ServerLevel) || event.getEntity().level().isClientSide) return;
        if (MonsterMeleeScaling.active(attacker) && event.getSource().getDirectEntity() == attacker
                && event.getSource().is(DamageTypes.MOB_ATTACK)) return;
        double scaled = event.getAmount() * attackMultiplier(attacker);
        event.setAmount((float)Math.min(Float.MAX_VALUE, scaled));
    }
}
