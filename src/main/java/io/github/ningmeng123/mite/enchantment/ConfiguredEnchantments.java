package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.progression.GearProgression;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Registry identities let configuration cover data-driven and third-party enchantments. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class ConfiguredEnchantments {
    private static volatile Map<Enchantment, String> serverIds = Map.of();
    private static volatile Map<Enchantment, String> clientIds = Map.of();
    private static final AtomicLong REVISION = new AtomicLong();
    private static final Map<LivingEntity, Long> REFRESHED = new WeakHashMap<>();
    private static Map<String, Integer> lastCaps = Map.of();
    private static Set<String> lastDisabled = Set.of();

    private ConfiguredEnchantments() {}

    @SubscribeEvent
    public static void updateRegistry(TagsUpdatedEvent event) {
        Map<Enchantment, String> ids = new IdentityHashMap<>();
        event.getRegistryAccess().registry(Registries.ENCHANTMENT).ifPresent(registry ->
                registry.holders().forEach(holder -> ids.put(holder.value(), holder.key().location().toString())));
        // Integrated servers can have distinct client registry instances. Keep both snapshots until replaced.
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) serverIds = ids;
        else clientIds = ids;
        io.github.ningmeng123.mite.config.EnchantmentCatalog.observe(event.getRegistryAccess());
    }

    private static String id(Enchantment enchantment) {
        String id = serverIds.get(enchantment);
        return id == null ? clientIds.get(enchantment) : id;
    }

    public static boolean enabled(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(key -> MiteConfig.enchantmentEnabled(key.location().toString())).orElse(true);
    }

    public static boolean enabled(Enchantment enchantment) {
        String id = id(enchantment);
        return id == null || MiteConfig.enchantmentEnabled(id);
    }

    public static int maximumLevel(Enchantment enchantment, int fallback) {
        String id = id(enchantment);
        return id == null ? fallback : MiteConfig.enchantmentMaxLevel(id, fallback);
    }

    /** Apply only explicit caps here, preserving saved over-level commands and independent growth defaults. */
    public static int effectiveLevel(Holder<Enchantment> enchantment, int level) {
        if (!enabled(enchantment)) return 0;
        return enchantment.unwrapKey().map(key -> effectiveLevel(key.location().toString(), level)).orElse(level);
    }

    public static int effectiveLevel(String id, int level) {
        return MiteConfig.enchantmentEnabled(id) ? Math.min(Math.max(0, level), MiteConfig.enchantmentMaxLevel(id, 255)) : 0;
    }

    /** This is a gameplay or newly-created result view; persisted components are never rewritten on load. */
    public static void apply(ItemEnchantments.Mutable enchantments) {
        for (var holder : java.util.List.copyOf(enchantments.keySet())) {
            int previous = enchantments.getLevel(holder);
            int configured = effectiveLevel(holder, previous);
            if (configured != previous) enchantments.set(holder, configured);
        }
    }

    public static synchronized void rulesChanged() {
        var snapshot = MiteConfig.localSnapshot();
        if (lastCaps.equals(snapshot.caps()) && lastDisabled.equals(snapshot.disabled())) return;
        lastCaps = snapshot.caps();
        lastDisabled = snapshot.disabled();
        REVISION.incrementAndGet();
    }

    @SubscribeEvent
    public static void refreshOnTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !(living.level() instanceof ServerLevel)) return;
        long revision = REVISION.get();
        if (revision == 0) return;
        Long previous = REFRESHED.put(living, revision);
        if (previous == null || previous != revision) refreshEffects(living);
    }

    /** Remove previous enchantment attributes and location effects before applying the new gameplay view. */
    public static void refreshEffects(LivingEntity living) {
        if (!(living.level() instanceof ServerLevel world)) return;
        var registry = world.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = living.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            Map<Holder<Enchantment>, Integer> previous = new HashMap<>();
            stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()
                    .forEach(entry -> previous.put(entry.getKey(), entry.getIntValue()));
            GearProgression.state(stack).affixes().forEach((id, level) -> registry.getHolder(
                    net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id))
                    .ifPresent(holder -> previous.merge(holder, level, Math::max)));
            var item = new EnchantedItemInUse(stack, slot, living);
            previous.forEach((holder, level) -> {
                var enchantment = holder.value();
                if (!enchantment.matchingSlot(slot)) return;
                enchantment.stopLocationBasedEffects(level, item, living);
                java.util.List<EnchantmentAttributeEffect> attributes = enchantment.effects().get(EnchantmentEffectComponents.ATTRIBUTES);
                if (attributes == null) return;
                for (var effect : attributes) {
                    var attribute = living.getAttribute(effect.attribute());
                    if (attribute != null) attribute.removeModifier(effect.getModifier(level, slot).id());
                }
            });
            EnchantmentHelper.forEachModifier(stack, slot, (holder, modifier) -> {
                var attribute = living.getAttribute(holder);
                if (attribute != null) attribute.addOrUpdateTransientModifier(modifier);
            });
        }
        EnchantmentHelper.runLocationChangedEffects(world, living);
    }
}
