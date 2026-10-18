package io.github.ningmeng123.mite.progression;

import io.github.ningmeng123.mite.mixin.GearCompositeConditionAccess;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.TargetedConditionalEffect;
import net.minecraft.world.item.enchantment.effects.*;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.EnchantmentLevelProvider;

/** Runtime-only combined effect views: registry data and ordinary enchantment tags remain unchanged. @author NingMeng123 */
public final class EnchantmentEffectBridge {
    private record Key(Enchantment enchantment, DataComponentType<?> type, EnchantmentSources.Levels levels) {}
    private static final Map<Key, List<?>> CACHE = new ConcurrentHashMap<>();
    private EnchantmentEffectBridge() {}

    /** Opaque mod effects require an explicit adapter rather than a misleading added level. */
    public static boolean supported(Holder<Enchantment> holder) {
        if (holder.value().effects().isEmpty() && holder.unwrapKey().map(key -> !key.location().getNamespace().equals("minecraft")
                && !key.location().getNamespace().equals("mite")).orElse(true)) return false;
        for (DataComponentType<?> type : holder.value().effects().keySet()) {
            var id = BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(type);
            if (id == null || !id.getNamespace().equals("minecraft")) return false;
            Object value = holder.value().effects().get(type);
            if (value instanceof List<?> list) {
                for (Object row : list) if (!supportedRow(row)) return false;
            } else if (value instanceof EnchantmentValueEffect effect && !supportedValue(effect)) return false;
        }
        return true;
    }
    private static boolean supportedRow(Object row) {
        if (row instanceof ConditionalEffect<?> condition) return condition.requirements().map(requirement -> supportedCondition(requirement)
                && randomConditions(requirement) <= (condition.effect() instanceof EnchantmentValueEffect ? 0 : 1)).orElse(true)
                && supportedRow(condition.effect());
        if (row instanceof TargetedConditionalEffect<?> condition) return condition.requirements().map(requirement -> supportedCondition(requirement)
                && randomConditions(requirement) <= (condition.effect() instanceof EnchantmentValueEffect ? 0 : 1)).orElse(true)
                && supportedRow(condition.effect());
        if (row instanceof EnchantmentValueEffect value) return supportedValue(value);
        if (row instanceof AllOf.EntityEffects all) return all.effects().stream().allMatch(EnchantmentEffectBridge::supportedRow);
        if (row instanceof AllOf.LocationBasedEffects all) return all.effects().stream().allMatch(EnchantmentEffectBridge::supportedRow);
        if (row instanceof EnchantmentLocationBasedEffect) return row instanceof EnchantmentAttributeEffect
                || row instanceof Ignite || row instanceof DamageEntity || row instanceof DamageItem || row instanceof ApplyMobEffect
                || row instanceof ReplaceDisk || row instanceof ExplodeEffect || row instanceof PlaySoundEffect
                || row instanceof ReplaceBlock || row instanceof SetBlockProperties || row instanceof SpawnParticlesEffect || row instanceof SummonEntityEffect;
        return row != null && row.getClass().getName().startsWith("net.minecraft.");
    }
    private static boolean supportedCondition(LootItemCondition condition) {
        var id = BuiltInRegistries.LOOT_CONDITION_TYPE.getKey(condition.getType());
        if (id == null || !id.getNamespace().equals("minecraft") || id.getPath().equals("reference")) return false;
        if (condition instanceof LootItemRandomChanceCondition random && !(random.chance() instanceof ConstantValue)
                && !(random.chance() instanceof EnchantmentLevelProvider)) return false;
        if (condition instanceof GearCompositeConditionAccess composite) return composite.mite$terms().stream().allMatch(EnchantmentEffectBridge::supportedCondition);
        return !(condition instanceof InvertedLootItemCondition inverted)
                || randomConditions(inverted.term()) == 0 && supportedCondition(inverted.term());
    }
    private static int randomConditions(LootItemCondition condition) {
        if (condition instanceof LootItemRandomChanceCondition) return 1;
        if (condition instanceof GearCompositeConditionAccess composite) return composite.mite$terms().stream().mapToInt(EnchantmentEffectBridge::randomConditions).sum();
        return condition instanceof InvertedLootItemCondition inverted ? randomConditions(inverted.term()) : 0;
    }
    private static boolean supportedValue(EnchantmentValueEffect value) {
        if (value instanceof AllOf.ValueEffects all) return all.effects().stream().allMatch(EnchantmentEffectBridge::supportedValue)
                && all.effects().stream().noneMatch(effect -> effect instanceof RemoveBinomial);
        return value instanceof AddValue || value instanceof MultiplyValue || value instanceof SetValue || value instanceof RemoveBinomial;
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> effects(Enchantment enchantment, DataComponentType<List<T>> type, List<T> original) {
        var frame = EnchantmentSources.frame(enchantment);
        if (frame == null) return original;
        // Pure numerical helpers evaluate the complete source chains instead of these combined views.
        return (List<T>)CACHE.computeIfAbsent(new Key(enchantment, type, frame.levels()), ignored -> original.stream()
                .map(row -> adaptRow(row, type, frame)).toList());
    }
    private static Object adaptRow(Object row, DataComponentType<?> type, EnchantmentSources.Frame frame) {
        if (row instanceof EnchantmentAttributeEffect attribute) return attribute(attribute, frame.levels());
        if (row instanceof ConditionalEffect<?> conditional) return new ConditionalEffect<>(
                adaptEffect(conditional.effect(), type, frame), conditional.requirements());
        if (row instanceof TargetedConditionalEffect<?> targeted) return new TargetedConditionalEffect<>(targeted.enchanted(),
                targeted.affected(), adaptEffect(targeted.effect(), type, frame), targeted.requirements());
        return row;
    }
    private static Object adaptEffect(Object effect, DataComponentType<?> type, EnchantmentSources.Frame frame) {
        var levels = frame.levels();
        if (effect instanceof RemoveBinomial remove) return new RemoveBinomial(LevelBasedValue.constant(
                (float)EnchantmentSources.probability(levels, level -> remove.chance().calculate(level))));
        if (effect instanceof AllOf.EntityEffects all) return new AllOf.EntityEffects(all.effects().stream()
                .map(child -> (EnchantmentEntityEffect)adaptEffect(child, type, frame)).toList());
        if (effect instanceof AllOf.LocationBasedEffects all) return new AllOf.LocationBasedEffects(all.effects().stream()
                .map(child -> (EnchantmentLocationBasedEffect)adaptEffect(child, type, frame)).toList());
        if (effect instanceof EnchantmentAttributeEffect attribute) return attribute(attribute, levels);
        if (effect instanceof Ignite ignite) return new Ignite(sum(ignite.duration(), levels));
        if (effect instanceof DamageEntity damage) return new DamageEntity(sum(damage.minDamage(), levels), sum(damage.maxDamage(), levels), damage.damageType());
        if (effect instanceof DamageItem damage) return new DamageItem(sum(damage.amount(), levels));
        if (effect instanceof ApplyMobEffect status) return new ApplyMobEffect(status.toApply(), sum(status.minDuration(), levels),
                sum(status.maxDuration(), levels), amplifier(status.minAmplifier(), levels), amplifier(status.maxAmplifier(), levels));
        if (effect instanceof ReplaceDisk disk) return new ReplaceDisk(sum(disk.radius(), levels), sum(disk.height(), levels), disk.offset(),
                disk.predicate(), disk.blockState(), disk.triggerGameEvent());
        if (effect instanceof ExplodeEffect explosion) return new ExplodeEffect(explosion.attributeToUser(), explosion.damageType(),
                explosion.knockbackMultiplier().map(value -> sum(value, levels)), explosion.immuneBlocks(), explosion.offset(),
                sum(explosion.radius(), levels), explosion.createFire(),
                explosion.blockInteraction(), explosion.smallParticle(), explosion.largeParticle(), explosion.sound());
        // Discrete effects (sound, particles, block conversion, summoning) execute once through the union view.
        return effect;
    }
    private static EnchantmentAttributeEffect attribute(EnchantmentAttributeEffect effect, EnchantmentSources.Levels levels) {
        return new EnchantmentAttributeEffect(effect.id(), effect.attribute(), sum(effect.amount(), levels), effect.operation());
    }
    private static LevelBasedValue sum(LevelBasedValue value, EnchantmentSources.Levels levels) {
        return LevelBasedValue.constant((float)EnchantmentSources.number(levels, level -> value.calculate(level)));
    }
    private static LevelBasedValue amplifier(LevelBasedValue value, EnchantmentSources.Levels levels) {
        return LevelBasedValue.constant((float)Math.max(0D,
                EnchantmentSources.number(levels, level -> value.calculate(level) + 1D) - 1D));
    }
}
