package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Charges belong to the player, so replacing a transient formation cannot reset its budget. @author NingMeng123 */
public final class PhantomBladeState {
    public static final String KEY = "mite:phantom_blade_state";
    private static final String CHARGES = "Charges", RECHARGE_END = "RechargeEnd", NEXT_ATTACK = "NextAttack";
    public record Snapshot(int charges, long rechargeEnd, long nextAttack) {
        public int orbitMask() { return (255 << (8-charges)) & 255; }
    }
    private PhantomBladeState() {}
    public static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }

    public static Snapshot current(ServerPlayer player, PhantomBladeRules.Profile profile) {
        CompoundTag root = player.getPersistentData();
        long now = now(player);
        if (!root.contains(KEY, CompoundTag.TAG_COMPOUND)) {
            write(player, new Snapshot(8, 0L, now+profile.intervalTicks()));
        }
        CompoundTag tag = root.getCompound(KEY);
        int charges = Math.clamp(tag.getInt(CHARGES), 0, 8);
        long end = tag.getLong(RECHARGE_END), next = tag.getLong(NEXT_ATTACK);
        if (charges == 0 && end > 0L && now >= end) {
            charges = 8; end = 0L;
        }
        Snapshot result = new Snapshot(charges, end, next);
        write(player, result); return result;
    }

    public static boolean ready(ServerPlayer player, Snapshot state) {
        return state.charges() > 0 && now(player) >= state.nextAttack();
    }

    /** Call only after the real projectile was accepted into the level. */
    public static Snapshot consume(ServerPlayer player, PhantomBladeRules.Profile profile) {
        Snapshot state = current(player, profile);
        if (!ready(player, state)) throw new IllegalStateException("Phantom blade fired outside its player-owned budget");
        int charges = state.charges()-1;
        long end = charges == 0 ? now(player)+profile.rechargeTicks() : 0L;
        Snapshot result = new Snapshot(charges, end, (charges == 0 ? end : now(player))+profile.intervalTicks());
        write(player, result); return result;
    }

    public static void copy(ServerPlayer original, ServerPlayer replacement) {
        if (original.getPersistentData().contains(KEY, CompoundTag.TAG_COMPOUND))
            replacement.getPersistentData().put(KEY, original.getPersistentData().getCompound(KEY).copy());
    }

    private static void write(ServerPlayer player, Snapshot state) {
        CompoundTag tag = new CompoundTag(); tag.putInt(CHARGES, state.charges());
        tag.putLong(RECHARGE_END, state.rechargeEnd()); tag.putLong(NEXT_ATTACK, state.nextAttack());
        player.getPersistentData().put(KEY, tag);
    }
}
