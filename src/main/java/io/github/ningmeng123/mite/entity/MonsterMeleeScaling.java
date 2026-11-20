package io.github.ningmeng123.mite.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Apply calendar growth to innate melee damage while retaining equipment and effect modifiers. @author NingMeng123 */
public final class MonsterMeleeScaling {
    private record Frame(Mob attacker, Frame previous) {}
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private MonsterMeleeScaling() {}

    /** Each nested real melee invocation owns its attacker and restores its caller on every exit path. */
    public static Scope enter(Mob attacker) { return new Scope(attacker); }

    public static boolean active(Mob attacker) {
        Frame frame = CURRENT.get();
        return frame != null && frame.attacker() == attacker;
    }

    /** Apply weapon bonuses to the grown base on a copy of the current attribute. */
    public static double attackValue(Mob attacker, double nativeValue) {
        if (!active(attacker) || !MonsterDayScaling.handles(attacker)) return nativeValue;
        AttributeInstance attribute = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) return nativeValue;
        double base = MonsterDayScaling.attackBase(attacker, attribute.getBaseValue());
        if (base == attribute.getBaseValue()) return nativeValue;
        AttributeInstance grown = new AttributeInstance(attribute.getAttribute(), ignored -> {});
        grown.replaceFrom(attribute);
        grown.setBaseValue(base);
        return grown.getValue();
    }

    public static final class Scope implements AutoCloseable {
        private final Frame previous;
        private boolean closed;

        private Scope(Mob attacker) {
            previous = CURRENT.get();
            CURRENT.set(new Frame(attacker, previous));
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
