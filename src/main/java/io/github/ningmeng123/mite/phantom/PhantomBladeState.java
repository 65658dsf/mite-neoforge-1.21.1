package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** The single blade's cooldown belongs to its player across core and entity replacements. @author NingMeng123 */
public final class PhantomBladeState {
    public static final String KEY = "mite:phantom_blade_state";
    private static final String VERSION = "Version", NEXT_ATTACK = "NextAttack";
    public record Snapshot(long nextAttack) {}

    private PhantomBladeState() {}
    public static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }

    public static Snapshot current(ServerPlayer player, PhantomBladeRules.Profile profile) {
        CompoundTag root = player.getPersistentData();
        long now = now(player);
        if (!root.contains(KEY, CompoundTag.TAG_COMPOUND)) {
            Snapshot initial = new Snapshot(now + profile.intervalTicks());
            write(player, initial);
            return initial;
        }
        CompoundTag tag = root.getCompound(KEY);
        long next = Math.max(0L, tag.getLong(NEXT_ATTACK));
        if (tag.getInt(VERSION) < 3) {
            // Old magazines become one reusable sword. Retain at most one normal cooldown,
            // rather than waiting out the removed full-magazine recharge.
            next = Math.min(Math.max(next, tag.getLong("RechargeEnd")), now + profile.intervalTicks());
        }
        Snapshot state = new Snapshot(next);
        write(player, state);
        return state;
    }

    public static boolean ready(ServerPlayer player, Snapshot state) {
        return now(player) >= state.nextAttack();
    }

    /** Reserve a cooldown before departure so replacing the core cannot grant another shot. */
    public static Snapshot consume(ServerPlayer player, PhantomBladeRules.Profile profile) {
        Snapshot state = current(player, profile);
        if (!ready(player, state)) throw new IllegalStateException("Phantom blade launched during its player's cooldown");
        Snapshot next = new Snapshot(now(player) + profile.intervalTicks());
        write(player, next);
        return next;
    }

    /** Called once when a flight hits, is blocked, or loses its target; return travel counts toward cooldown. */
    public static Snapshot cooldown(ServerPlayer player, PhantomBladeRules.Profile profile) {
        Snapshot state = current(player, profile);
        Snapshot next = new Snapshot(Math.max(state.nextAttack(), now(player) + profile.intervalTicks()));
        write(player, next);
        return next;
    }

    public static void copy(ServerPlayer original, ServerPlayer replacement) {
        if (original.getPersistentData().contains(KEY, CompoundTag.TAG_COMPOUND))
            replacement.getPersistentData().put(KEY, original.getPersistentData().getCompound(KEY).copy());
    }

    private static void write(ServerPlayer player, Snapshot state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION, 3);
        tag.putLong(NEXT_ATTACK, state.nextAttack());
        player.getPersistentData().put(KEY, tag);
    }
}
