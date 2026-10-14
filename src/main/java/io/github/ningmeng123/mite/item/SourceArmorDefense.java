package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.rules.SourceArmorDefenseRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.SourceArmorDefenseRules.Piece;
import io.github.ningmeng123.mite.rules.SourceArmorDefenseRules.Source;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Scoped source defense retains the normal NeoForge incoming/pre/post damage events and absorption. @author NingMeng123 */
public final class SourceArmorDefense {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final ThreadLocal<Deque<Scope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    public static final class Scope implements AutoCloseable {
        private final Player player;
        private boolean processed;
        private Scope(Player player) { this.player = player; }
        @Override public void close() {
            Deque<Scope> scopes = SCOPES.get();
            if (scopes.peek() != this) throw new IllegalStateException("Source armor damage scope order");
            scopes.pop();
            if (scopes.isEmpty()) SCOPES.remove();
        }
    }
    private SourceArmorDefense() {}
    public static Scope enter(Player player) { Scope scope = new Scope(player); SCOPES.get().push(scope); return scope; }
    private static Scope scope(Player player) {
        Scope scope = SCOPES.get().peek();
        return scope != null && scope.player == player ? scope : null;
    }
    public static boolean canHandle(Player player) {
        boolean managed = false;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (EquipmentDurability.armorProfile(stack) != null) managed = true;
            else if (stack.getItem() instanceof ArmorItem) return false;
        }
        return managed;
    }
    public static boolean canProcess(Player player) { return scope(player) != null && canHandle(player); }
    public static boolean processed(Player player) { Scope scope = scope(player); return scope != null && scope.processed; }

    public static float absorb(Player player, DamageSource source, float incoming) {
        Source flags = flags(source);
        List<Piece> pieces = pieces(player);
        boolean potionEffectsApply = !source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypes.STARVE);
        int resistance = potionEffectsApply && player.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                ? player.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1 : 0;
        int fireResistance = player.hasEffect(MobEffects.FIRE_RESISTANCE) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
                ? player.getEffect(MobEffects.FIRE_RESISTANCE).getAmplifier() + 1 : 0;
        var defense = SourceArmorDefenseRules.defense(pieces, flags, resistance, fireResistance);
        Scope scope = scope(player);
        if (scope == null) throw new IllegalStateException("Source armor defense called outside damage scope");
        scope.processed = true;
        // Compute defense before wear. Source ud still allocates a minimum wear attempt when mundane armor is bypassed.
        if (!flags.absolute() && !flags.unblockable() && incoming > 0F)
            EquipmentDurability.hurtArmorSourceBudget(player, source,
                    Math.min(EquipmentDurabilityRules.roundedArmorProtection(defense.armor()), incoming));
        // Preserve modern ARMOR_EFFECTIVENESS data functions (including Breach), by changing only the representation
        // of the source mundane contribution. No source piercing enchantment has been registered; crossbow
        // piercing remains entity penetration. Do not cap the ratio at one: source excess-defense RNG needs it.
        float mundane = defense.armor();
        ItemStack weapon = source.getWeaponItem();
        if (!flags.absolute() && incoming > 0F && weapon != null && player.level() instanceof ServerLevel level) {
            float ratio = mundane / incoming;
            float modified = EnchantmentHelper.modifyArmorEffectiveness(level, weapon, player, source, ratio);
            // A no-op function must preserve the exact source float, including random-block equality boundaries.
            if (Float.compare(modified, ratio) != 0) mundane = Math.max(modified, 0F) * incoming;
        }
        return SourceArmorDefenseRules.damage(incoming, mundane + defense.special() + defense.potions(), 0F, flags.absolute(), player.getRandom()::nextFloat);
    }
    public static Source flags(DamageSource source) {
        boolean absolute = source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        boolean unblockable = source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.STARVE);
        boolean lava = source.is(DamageTypes.LAVA);
        // R196 nb.setLavaDamage bypasses mundane armor but does not set nb.m()'s fire flag.
        boolean fire = source.is(DamageTypeTags.IS_FIRE) && !lava;
        return new Source(absolute, unblockable, source.is(DamageTypeTags.BYPASSES_ARMOR) || fire || lava,
                source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS), fire, source.is(DamageTypeTags.IS_FALL),
                source.is(DamageTypeTags.IS_EXPLOSION), source.is(DamageTypeTags.IS_PROJECTILE));
    }
    public static List<Piece> pieces(LivingEntity player) {
        List<Piece> pieces = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            var profile = EquipmentDurability.armorProfile(stack);
            if (profile == null) continue;
            pieces.add(new Piece(profile, stack.getDamageValue(), stack.getMaxDamage(), level(player, stack, Enchantments.PROTECTION),
                    level(player, stack, Enchantments.FIRE_PROTECTION), level(player, stack, Enchantments.FEATHER_FALLING),
                    level(player, stack, Enchantments.BLAST_PROTECTION), level(player, stack, Enchantments.PROJECTILE_PROTECTION)));
        }
        return List.copyOf(pieces);
    }
    private static int level(LivingEntity player, ItemStack stack, ResourceKey<Enchantment> enchantment) {
        return player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(enchantment)
                .map(stack::getEnchantmentLevel).orElse(0);
    }
}
