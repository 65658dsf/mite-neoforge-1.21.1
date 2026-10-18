package io.github.ningmeng123.mite.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.rules.ShieldGuardRules;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Persistent per-shield guard exhaustion, applied only after the native blocked hit finishes. @author NingMeng123 */
public final class ShieldGuard {
    public static final int COOLDOWN_TICKS = ShieldGuardRules.COOLDOWN_TICKS;
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Mite.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<State>> GUARD =
            COMPONENTS.registerComponentType("shield_guard", builder -> builder.persistent(State.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(State.CODEC)));
    private static final ThreadLocal<HitScope> CURRENT_HIT = new ThreadLocal<>();

    public record State(double blockedDamage, int blocks) {
        public static final State EMPTY = new State(0D, 0);
        public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0D, ShieldGuardRules.MAX_DAMAGE_LIMIT).optionalFieldOf("blocked_damage", 0D).forGetter(State::blockedDamage),
                Codec.intRange(0, ShieldGuardRules.MAX_BLOCK_LIMIT).optionalFieldOf("blocks", 0).forGetter(State::blocks),
                Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(value -> 1)
        ).apply(instance, (damage, blocks, version) -> new State(damage, blocks)));

        public State {
            if (!Double.isFinite(blockedDamage) || blockedDamage < 0D || blockedDamage > ShieldGuardRules.MAX_DAMAGE_LIMIT
                    || blocks < 0 || blocks > ShieldGuardRules.MAX_BLOCK_LIMIT)
                throw new IllegalArgumentException("Invalid shield guard state");
        }
    }

    private record Accepted(ItemStack shield, Item item, State before, float blockedDamage, ShieldGuardRules.Profile profile) {}
    private static final class HitScope {
        private final LivingEntity defender;
        private final HitScope parent;
        private Accepted accepted;
        private boolean nativeDisabled;
        private HitScope(LivingEntity defender, HitScope parent) { this.defender = defender; this.parent = parent; }
    }

    private ShieldGuard() {}

    public static void register(IEventBus bus) { COMPONENTS.register(bus); }
    public static boolean shield(ItemStack stack) { return !stack.isEmpty() && stack.canPerformAction(ItemAbilities.SHIELD_BLOCK); }
    public static ShieldGuardRules.Profile profile(ItemStack stack) {
        MiteShields.Definition definition = MiteShields.definition(stack);
        return ShieldGuardRules.profile(definition == null ? null : definition.material());
    }
    public static State state(ItemStack stack) { return stack.isEmpty() ? State.EMPTY : stack.getOrDefault(GUARD.get(), State.EMPTY); }
    public static boolean disabled(LivingEntity defender) {
        return defender.getPersistentData().getLong(MobShields.DISABLED_UNTIL) > defender.level().getGameTime();
    }

    /** Capture every native return, including a fully blocked hit returning false during cooldown. */
    public static boolean hurt(LivingEntity defender, Supplier<Boolean> original) {
        if (!(defender.level() instanceof ServerLevel)) return original.get();
        HitScope previous = CURRENT_HIT.get();
        HitScope scope = new HitScope(defender, previous);
        CURRENT_HIT.set(scope);
        try {
            boolean result = original.get();
            complete(scope);
            return result;
        } finally {
            if (previous == null) CURRENT_HIT.remove(); else CURRENT_HIT.set(previous);
        }
    }

    /** NeoForge's event reads a mutable damage container; retain the accepted amount right now. */
    public static void accepted(LivingEntity defender, LivingShieldBlockEvent event) {
        HitScope scope = CURRENT_HIT.get();
        ItemStack stack = defender.getUseItem();
        if (scope == null || scope.defender != defender || event.isCanceled() || !event.getBlocked()
                || !shield(stack) || !Float.isFinite(event.getBlockedDamage()) || !(event.getBlockedDamage() > 0F)) return;
        scope.accepted = new Accepted(stack, stack.getItem(), state(stack), event.getBlockedDamage(), profile(stack));
    }

    private static void complete(HitScope scope) {
        Accepted accepted = scope.accepted;
        if (accepted == null) return;
        if (scope.nativeDisabled) {
            reset(accepted.shield(), scope.defender);
            // The existing mob AI uses offhand shields. A manually equipped mainhand
            // shield still obeys the same accepted axe interruption after native wear.
            if (scope.defender.getUseItem() == accepted.shield() && shield(accepted.shield())) {
                scope.defender.stopUsingItem();
                if (!(scope.defender instanceof Player))
                    scope.defender.playSound(SoundEvents.SHIELD_BREAK, .8F, .9F + scope.defender.getRandom().nextFloat() * .2F);
                scope.defender.level().broadcastEntityEvent(scope.defender, (byte)30);
            }
            return;
        }
        State before = accepted.shield().isEmpty() ? accepted.before() : state(accepted.shield());
        ShieldGuardRules.Result next = ShieldGuardRules.advance(accepted.profile(), before.blockedDamage(), before.blocks(), accepted.blockedDamage());
        write(accepted.shield(), new State(next.blockedDamage(), next.blocks()), scope.defender);
        if (next.broken()) disable(scope.defender, accepted);
    }

    /** Native axe interruptions clear this shield and share their cooldown across every shield item. */
    public static void nativeDisabled(LivingEntity defender, ItemStack capturedShield) {
        if (!(defender.level() instanceof ServerLevel)) return;
        HitScope matching = null;
        for (HitScope scope = CURRENT_HIT.get(); scope != null; scope = scope.parent) {
            if (scope.defender == defender && scope.accepted != null) {
                scope.nativeDisabled = true;
                reset(scope.accepted.shield(), defender);
                if (matching == null) matching = scope;
            }
        }
        if (!shield(capturedShield) && matching == null) return;
        reset(capturedShield, defender);
        cooldown(defender, matching != null ? matching.accepted.item() : capturedShield.getItem());
    }

    /** Keep MobShields' native stop/sound path intact, regardless of mixin tail ordering. */
    public static void axeBlocked(LivingEntity defender, LivingEntity attacker) {
        if (!(defender instanceof Mob) || !attacker.canDisableShield()) return;
        HitScope scope = CURRENT_HIT.get();
        if (scope != null && scope.defender == defender && scope.accepted != null)
            nativeDisabled(defender, scope.accepted.shield());
    }

    private static void disable(LivingEntity defender, Accepted accepted) {
        // disableShield reads useItem internally. A native axe hit or a physically broken
        // shield may already have cleared it, so never invoke that method with an empty use item.
        if (defender instanceof Player player && defender.getUseItem() == accepted.shield() && shield(defender.getUseItem())) {
            player.disableShield();
            return;
        }
        cooldown(defender, accepted.item());
        defender.stopUsingItem();
        if (!(defender instanceof Player))
            defender.playSound(SoundEvents.SHIELD_BREAK, .8F, .9F + defender.getRandom().nextFloat() * .2F);
        defender.level().broadcastEntityEvent(defender, (byte)30);
    }

    private static void cooldown(LivingEntity defender, Item capturedItem) {
        defender.getPersistentData().putLong(MobShields.DISABLED_UNTIL,
                Math.max(defender.getPersistentData().getLong(MobShields.DISABLED_UNTIL), defender.level().getGameTime() + COOLDOWN_TICKS));
        if (defender instanceof Player player) {
            // Default stacks cover vanilla, all MITE materials and ordinary third-party shields.
            // The shared entity deadline also blocks component-dependent shields from swapping around it.
            for (Item item : BuiltInRegistries.ITEM) if (shield(item.getDefaultInstance()))
                player.getCooldowns().addCooldown(item, COOLDOWN_TICKS);
            player.getCooldowns().addCooldown(capturedItem, COOLDOWN_TICKS);
        }
    }

    private static void reset(ItemStack stack, LivingEntity defender) { write(stack, State.EMPTY, defender); }
    private static void write(ItemStack stack, State progress, LivingEntity defender) {
        if (stack.isEmpty()) return;
        if (progress.equals(State.EMPTY)) stack.remove(GUARD.get()); else stack.set(GUARD.get(), progress);
        if (defender instanceof Player player) player.getInventory().setChanged();
    }

    /** Combining shields retains the first shield's history without borrowing the second shield's guard. */
    public static void preserve(ItemStack source, ItemStack result) {
        if (!shield(source) || !shield(result)) return;
        State progress = source.get(GUARD.get());
        if (progress == null) result.remove(GUARD.get()); else result.set(GUARD.get(), progress);
    }
}
