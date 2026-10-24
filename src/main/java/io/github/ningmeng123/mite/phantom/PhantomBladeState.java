package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Charges belong to the player, so replacing a transient formation cannot reset its budget. @author NingMeng123 */
public final class PhantomBladeState {
    public static final String KEY = "mite:phantom_blade_state";
    private static final String CHARGES = "Charges", RECHARGE_END = "RechargeEnd", NEXT_ATTACK = "NextAttack";
    private static final String VERSION = "Version", SPENT = "Spent";
    private static final int LEGACY_CAPACITY = 8;
    public record Snapshot(int charges, long rechargeEnd, long nextAttack, int spent) {
        public int orbitMask(PhantomBladeRules.Profile profile) {
            return (profile.fullMask() << (profile.bladeCount()-charges)) & profile.fullMask();
        }
    }
    private PhantomBladeState() {}
    public static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }

    public static Snapshot current(ServerPlayer player, PhantomBladeRules.Profile profile) {
        CompoundTag root = player.getPersistentData();
        long now = now(player);
        if (!root.contains(KEY, CompoundTag.TAG_COMPOUND)) {
            write(player, new Snapshot(profile.bladeCount(), 0L, now+profile.intervalTicks(), 0));
        }
        CompoundTag tag = root.getCompound(KEY);
        int charges = Math.clamp(tag.getInt(CHARGES), 0, PhantomBladeRules.maximumBladeCount());
        int spent = tag.getInt(VERSION) >= 2 ? Math.clamp(tag.getInt(SPENT), 0, PhantomBladeRules.maximumBladeCount())
                : Math.max(0, LEGACY_CAPACITY-charges);
        long end = tag.getLong(RECHARGE_END), next = tag.getLong(NEXT_ATTACK);
        if (charges == 0 && end > 0L && now >= end) {
            charges = profile.bladeCount(); spent = 0; end = 0L;
        } else {
            // Retain the spent count when shrinking, and never grant ammunition by switching upward again.
            charges = Math.min(charges, Math.max(0, profile.bladeCount()-spent));
            if (charges == 0 && end <= 0L) {
                end = now+profile.rechargeTicks(); next = Math.max(next, end+profile.intervalTicks());
            }
        }
        Snapshot result = new Snapshot(charges, end, next, spent);
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
        Snapshot result = new Snapshot(charges, end, (charges == 0 ? end : now(player))+profile.intervalTicks(), state.spent()+1);
        write(player, result); return result;
    }

    public static void copy(ServerPlayer original, ServerPlayer replacement) {
        if (original.getPersistentData().contains(KEY, CompoundTag.TAG_COMPOUND))
            replacement.getPersistentData().put(KEY, original.getPersistentData().getCompound(KEY).copy());
    }

    private static void write(ServerPlayer player, Snapshot state) {
        CompoundTag tag = new CompoundTag(); tag.putInt(CHARGES, state.charges());
        tag.putInt(VERSION, 2); tag.putInt(SPENT, state.spent());
        tag.putLong(RECHARGE_END, state.rechargeEnd()); tag.putLong(NEXT_ATTACK, state.nextAttack());
        player.getPersistentData().put(KEY, tag);
    }
}
