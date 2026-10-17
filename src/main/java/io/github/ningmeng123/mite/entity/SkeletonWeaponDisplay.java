package io.github.ningmeng123.mite.entity;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** Client presentation snapshots never move, consume or replace actual equipment. @author NingMeng123 */
public final class SkeletonWeaponDisplay {
    private static final Map<AbstractSkeleton, State> STATES = new WeakHashMap<>();
    private static final double STOW_TICKS = 4D, DRAW_START = 5D, END_TICKS = 9D;
    public record Frame(ItemStack stack, float lowering, boolean switching) {}
    private static final class State {
        ItemStack observed = ItemStack.EMPTY, previous = ItemStack.EMPTY;
        double started, lastTime;
        boolean initialized, switching;
    }
    private SkeletonWeaponDisplay() {}

    public static boolean hidesOffhand(AbstractSkeleton skeleton) {
        return SkeletonWeapons.eligible(skeleton) && weapon(skeleton.getOffhandItem())
                && (weapon(skeleton.getMainHandItem()) || skeleton.getMainHandItem().isEmpty());
    }

    private static boolean weapon(ItemStack stack) {
        return stack.getItem() instanceof BowItem || SkeletonWeapons.melee(stack);
    }

    private static boolean pair(ItemStack main, ItemStack spare) {
        return main.getItem() instanceof BowItem && SkeletonWeapons.melee(spare)
                || SkeletonWeapons.melee(main) && spare.getItem() instanceof BowItem;
    }

    public static void clear(AbstractSkeleton skeleton) { STATES.remove(skeleton); }

    /** Observe complete hand swaps, including native entity event 55, instead of individual slot setters. */
    public static Frame frame(AbstractSkeleton skeleton, double time) {
        ItemStack current = skeleton.getMainHandItem(), spare = skeleton.getOffhandItem();
        if (!SkeletonWeapons.eligible(skeleton) || !Double.isFinite(time)) {
            clear(skeleton);
            return new Frame(current, 0F, false);
        }
        State state = STATES.computeIfAbsent(skeleton, ignored -> new State());
        time = state.initialized ? Math.max(time, state.lastTime) : time;
        if (!state.initialized || !pair(current, spare)) {
            state.initialized = true; state.observed = current.copy(); state.previous = ItemStack.EMPTY;
            state.switching = false; state.lastTime = time;
            return new Frame(current, 0F, false);
        }
        // Durability, enchantments and stack counts can change without changing the held weapon.
        if (!ItemStack.isSameItem(state.observed, current)) {
            if (pair(state.observed, current) && ItemStack.isSameItem(state.observed, spare)) {
                ItemStack visible = sample(state, time, state.observed).stack();
                state.switching = !ItemStack.isSameItem(visible, current);
                state.previous = (visible.isEmpty() ? state.observed : visible).copy();
                state.started = time;
            } else state.switching = false;
        }
        state.observed = current.copy(); state.lastTime = time;
        return sample(state, time, current);
    }

    private static Frame sample(State state, double time, ItemStack current) {
        if (!state.switching) return new Frame(current, 0F, false);
        double elapsed = time - state.started;
        if (elapsed >= END_TICKS) {
            state.switching = false; state.previous = ItemStack.EMPTY;
            return new Frame(current, 0F, false);
        }
        if (elapsed < STOW_TICKS) return new Frame(state.previous, smooth(elapsed / STOW_TICKS), true);
        if (elapsed < DRAW_START) return new Frame(ItemStack.EMPTY, 1F, true);
        return new Frame(current, 1F - smooth((elapsed - DRAW_START) / (END_TICKS - DRAW_START)), true);
    }

    private static float smooth(double progress) {
        double clamped = Math.clamp(progress, 0D, 1D);
        return (float)(clamped * clamped * (3D - 2D * clamped));
    }
}
