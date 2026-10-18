package io.github.ningmeng123.mite.progression;

import io.github.ningmeng123.mite.Mite;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.IntToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

/** Ordinary enchantments and progression affixes are separate effect sources. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class EnchantmentSources {
    public record Levels(int normal, int affix) {
        public int totalLevel() { return normal + affix; }
        public boolean present() { return normal > 0 || affix > 0; }
    }
    public enum Mode { COMBINED, NORMAL, AFFIX }
    public record Frame(ItemStack stack, Holder<Enchantment> enchantment, Levels levels) {}
    private static final ThreadLocal<Mode> MODE = ThreadLocal.withInitial(() -> Mode.COMBINED);
    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private EnchantmentSources() {}

    public static final class Scope implements AutoCloseable {
        private final Mode mode;
        private final Frame frame;
        private Scope(Mode nextMode, Frame nextFrame) {
            mode = MODE.get(); frame = FRAME.get(); MODE.set(nextMode); FRAME.set(nextFrame);
        }
        @Override public void close() { MODE.set(mode); if (frame == null) FRAME.remove(); else FRAME.set(frame); }
    }
    public static Scope source(Mode mode) { return new Scope(mode, null); }
    public static Scope visiting(ItemStack stack, Holder<Enchantment> enchantment) {
        return new Scope(MODE.get(), new Frame(stack, enchantment, levels(stack, enchantment)));
    }
    public static Mode mode() { return MODE.get(); }
    public static Frame currentFrame() { return mode() == Mode.COMBINED ? FRAME.get() : null; }
    public static Frame frame(Enchantment enchantment) {
        Frame frame = FRAME.get();
        return mode() == Mode.COMBINED && frame != null && frame.enchantment().value() == enchantment
                && frame.levels().affix() > 0 ? frame : null;
    }
    public static boolean hasAffixes(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !GearProgression.state(stack).affixes().isEmpty();
    }
    public static boolean hasAffixes(LivingEntity living) {
        if (living == null) return false;
        for (EquipmentSlot slot : EquipmentSlot.values()) if (hasAffixes(living.getItemBySlot(slot))) return true;
        return false;
    }
    public static Levels levels(ItemStack stack, Holder<Enchantment> enchantment) {
        if (stack == null || stack.isEmpty()) return new Levels(0, 0);
        int normal;
        try (Scope ignored = source(Mode.NORMAL)) { normal = Math.max(0, stack.getEnchantmentLevel(enchantment)); }
        int bonus = enchantment.unwrapKey().map(key -> GearProgression.affixLevel(stack, key.location())).orElse(0);
        return new Levels(normal, Math.max(0, bonus));
    }
    public static Levels levels(ItemStack stack, Level world, String id) {
        ResourceLocation key = id.indexOf(':') < 0 ? ResourceLocation.fromNamespaceAndPath("mite", id) : ResourceLocation.parse(id);
        return world.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(
                net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, key))
                .map(holder -> levels(stack, holder)).orElse(new Levels(0, 0));
    }
    public static int totalLevel(ItemStack stack, Level world, String id) { return levels(stack, world, id).totalLevel(); }
    public static boolean present(Levels levels) { return levels.present(); }
    public static boolean isSupported(Holder<Enchantment> enchantment) { return EnchantmentEffectBridge.supported(enchantment); }
    public static double number(Levels levels, IntToDoubleFunction value) {
        return (levels.normal() > 0 ? value.applyAsDouble(levels.normal()) : 0D)
                + (levels.affix() > 0 ? value.applyAsDouble(levels.affix()) : 0D);
    }
    public static double delta(Levels levels, double baseline, IntToDoubleFunction result) {
        return baseline + (levels.normal() > 0 ? result.applyAsDouble(levels.normal()) - baseline : 0D)
                + (levels.affix() > 0 ? result.applyAsDouble(levels.affix()) - baseline : 0D);
    }
    public static double probability(Levels levels, IntToDoubleFunction chance) { return Math.clamp(number(levels, chance), 0D, 1D); }
    public static double equipped(LivingEntity living, String id, IntToDoubleFunction value) {
        double highest = 0D;
        for (EquipmentSlot slot : ARMOR) highest = Math.max(highest, number(levels(living.getItemBySlot(slot), living.level(), id), value));
        return highest;
    }
    public static double equippedDelta(LivingEntity living, Holder<Enchantment> enchantment, double baseline, IntToDoubleFunction value) {
        double result = baseline;
        for (ItemStack stack : enchantment.value().getSlotItems(living).values())
            result = Math.max(result, delta(levels(stack, enchantment), baseline, value));
        return result;
    }
    public static Levels highestLevels(LivingEntity living, Holder<Enchantment> enchantment) {
        Levels highest = new Levels(0, 0);
        for (ItemStack stack : enchantment.value().getSlotItems(living).values()) {
            Levels next = levels(stack, enchantment);
            if (next.totalLevel() > highest.totalLevel()) highest = next;
        }
        return highest;
    }
    /** Evaluate the complete chain twice from the same input, never feeding one source into the other. */
    public static double calculate(boolean applicable, double baseline, DoubleSupplier original) {
        if (!applicable || mode() != Mode.COMBINED) return original.getAsDouble();
        double normal, affix;
        try (Scope ignored = source(Mode.NORMAL)) { normal = original.getAsDouble(); }
        try (Scope ignored = source(Mode.AFFIX)) { affix = original.getAsDouble(); }
        return normal + affix - baseline;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gameplayEnchantments(GetEnchantmentLevelEvent event) {
        if (mode() == Mode.NORMAL) return;
        if (mode() == Mode.AFFIX) event.getEnchantments().removeIf(holder -> true);
        if (!hasAffixes(event.getStack())) return;
        for (Map.Entry<ResourceLocation, Integer> entry : GearProgression.state(event.getStack()).affixes().entrySet()) {
            event.getLookup().get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, entry.getKey())).ifPresent(holder -> {
                if (event.isTargetting(holder)) event.getEnchantments().set(holder,
                        mode() == Mode.AFFIX ? entry.getValue() : Math.max(event.getEnchantments().getLevel(holder), entry.getValue()));
            });
        }
    }
}
