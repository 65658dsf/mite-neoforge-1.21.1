package io.github.ningmeng123.mite.client.phantom;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.phantom.PhantomBladeProjectile;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Short material-colored embers and tiny luminous stars follow the flying sword's hilt. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class PhantomBladeParticles {
    public static final double MAX_DISTANCE_SQUARED = 24D * 24D;
    public static final int MAX_PER_CLIENT_TICK = 48;
    public static final int MAX_REDUCED_PER_CLIENT_TICK = 24;
    private static final double HILT_DISTANCE = .31D;
    private static final Map<PhantomBladeProjectile, Track> TRACKS = new WeakHashMap<>();
    private static final Map<ClientLevel, Budget> BUDGETS = new WeakHashMap<>();
    private static final Emission NONE = new Emission(0, 0);

    private PhantomBladeParticles() {}

    public record Emission(int motes, int stars) {
        public int total() { return motes + stars; }
    }

    /** Tick cadence is independent of FPS; minimal particles disables this cosmetic effect. */
    public static Emission emission(int tick, int entityId, ParticleStatus quality, boolean docked,
            double distanceSquared, double speedSquared) {
        if (docked || quality == ParticleStatus.MINIMAL || !Double.isFinite(distanceSquared)
                || distanceSquared > MAX_DISTANCE_SQUARED || !Double.isFinite(speedSquared)
                || speedSquared < .00001D) return NONE;
        int phase = tick + entityId;
        if (quality == ParticleStatus.DECREASED)
            return (phase & 1) == 0 ? new Emission(1, (phase & 7) == 0 ? 1 : 0) : NONE;
        return new Emission(2, (phase & 3) == 0 ? 1 : 0);
    }

    public static Vec3 hilt(Vec3 position, Vec3 motion) {
        return position.subtract(motion.normalize().scale(HILT_DISTANCE));
    }

    @SubscribeEvent public static void afterTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PhantomBladeProjectile sword)
                || !(sword.level() instanceof ClientLevel level)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || sword.isRemoved()) { TRACKS.remove(sword); return; }
        var camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) { TRACKS.remove(sword); return; }
        ParticleStatus quality = minecraft.options.particles().get();
        Vec3 motion = sword.getDeltaMovement();
        boolean docked = sword.isDocked();
        if (docked || quality == ParticleStatus.MINIMAL
                || camera.getPosition().distanceToSqr(sword.position()) > MAX_DISTANCE_SQUARED
                || motion.lengthSqr() < .00001D) { TRACKS.remove(sword); return; }
        Segment segment = TRACKS.computeIfAbsent(sword, ignored -> new Track())
                .sample(sword.tickCount, hilt(sword.position(), motion));
        if (segment == null) return;
        Emission emission = emission(sword.tickCount, sword.getId(), quality, docked,
                camera.getPosition().distanceToSqr(sword.position()), motion.lengthSqr());
        int available = BUDGETS.computeIfAbsent(level, ignored -> new Budget()).claim(level.getGameTime(), emission.total(),
                quality == ParticleStatus.DECREASED ? MAX_REDUCED_PER_CLIENT_TICK : MAX_PER_CLIENT_TICK);
        if (available == 0) return;
        int color = PhantomBladeVisuals.color(sword.material());
        Vec3 rgb = Vec3.fromRGB24(color);
        Vec3 drift = motion.normalize().scale(-.012D);
        for (int index = 0; index < emission.motes() && available > 0; index++, available--) {
            Vec3 position = segment.at((index + .5D) / emission.motes()).add(
                    (level.random.nextDouble() - .5D) * .045D,
                    (level.random.nextDouble() - .5D) * .045D,
                    (level.random.nextDouble() - .5D) * .045D);
            var mote = minecraft.particleEngine.createParticle(new DustParticleOptions(rgb.toVector3f(), .58F),
                    position.x, position.y, position.z, drift.x, drift.y, drift.z);
            if (mote != null) {
                mote.setLifetime(8 + level.random.nextInt(4));
                mote.setParticleSpeed(drift.x, drift.y, drift.z);
            }
        }
        if (emission.stars() > 0 && available > 0) {
            Vec3 position = segment.at(.7D);
            var star = minecraft.particleEngine.createParticle(ParticleTypes.END_ROD,
                    position.x, position.y, position.z, drift.x, drift.y, drift.z);
            if (star != null) {
                star.scale(.38F);
                star.setLifetime(7);
                star.setColor((float) (rgb.x * .65D + .35D), (float) (rgb.y * .65D + .35D), (float) (rgb.z * .65D + .35D));
                if (star instanceof SimpleAnimatedParticle animated) animated.setFadeColor(color);
            }
        }
    }

    public record Segment(Vec3 from, Vec3 to) {
        public Vec3 at(double fraction) { return from.lerp(to, fraction); }
    }

    /** Gaps, duplicate callbacks and teleports cannot create long or repeated particle trails. */
    public static final class Track {
        private int tick = Integer.MIN_VALUE;
        @Nullable private Vec3 previous;
        @Nullable public Segment sample(int currentTick, Vec3 position) {
            if (currentTick == tick) return null;
            if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
                previous = null; tick = currentTick; return null;
            }
            Vec3 from = previous;
            boolean continuous = (long) currentTick - tick == 1L;
            previous = position; tick = currentTick;
            if (!continuous || from == null || from.distanceToSqr(position) > 4D
                    || from.distanceToSqr(position) < .000001D) return null;
            return new Segment(from, position);
        }
    }

    /** A shared local cap keeps simultaneous multiplayer flights from filling the screen. */
    public static final class Budget {
        private long tick = Long.MIN_VALUE;
        private int spent, cap;
        public int claim(long currentTick, int requested, int limit) {
            if (currentTick != tick) { tick = currentTick; spent = 0; cap = limit; }
            cap = Math.min(cap, limit);
            int allowed = Math.min(Math.max(requested, 0), Math.max(0, cap - spent));
            spent += allowed;
            return allowed;
        }
    }
}
