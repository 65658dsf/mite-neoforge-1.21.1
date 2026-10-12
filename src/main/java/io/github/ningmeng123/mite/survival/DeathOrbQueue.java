package io.github.ningmeng123.mite.survival;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Per-dimension delayed drops, independent of a dead player's lifetime. @author NingMeng123 */
public final class DeathOrbQueue extends SavedData {
    public static final String DATA_NAME = "mite_death_orbs";
    public static final int SCHEMA_VERSION = 1;
    public static final int DROP_DELAY_TICKS = 20;
    public static final Factory<DeathOrbQueue> FACTORY = new Factory<>(DeathOrbQueue::new, DeathOrbQueue::load);

    private final List<PendingDrop> drops = new ArrayList<>();

    public DeathOrbQueue() {
    }

    public static DeathOrbQueue get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void enqueue(UUID owner, Vec3 position, int amount, long gameTime) {
        if (amount <= 0) {
            return;
        }
        drops.add(new PendingDrop(owner, position, amount, DROP_DELAY_TICKS, gameTime));
        setDirty();
    }

    /** Advances only where entities can tick; never loads a chunk or counts offline time. */
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        List<PendingDrop> ready = new ArrayList<>();
        Iterator<PendingDrop> iterator = drops.iterator();
        while (iterator.hasNext()) {
            PendingDrop drop = iterator.next();
            BlockPos position = BlockPos.containing(drop.position);
            if (now == drop.lastTick || !level.isPositionEntityTicking(position)
                    || !level.areEntitiesLoaded(ChunkPos.asLong(position))) {
                continue;
            }
            drop.lastTick = now;
            drop.remainingTicks--;
            setDirty();
            if (drop.remainingTicks <= 0) {
                // Remove before entity-spawn callbacks, which may enqueue other
                // drops. Normal saves cannot replay an already delivered entry.
                iterator.remove();
                ready.add(drop);
            }
        }
        for (PendingDrop drop : ready) {
            int remaining = drop.amount;
            while (remaining > 0) {
                int value = ExperienceOrb.getExperienceValue(remaining);
                remaining -= value;
                ExperienceOrb orb = new ExperienceOrb(level, drop.position.x, drop.position.y, drop.position.z, value);
                DeathExperienceOrbs.mark(orb, drop.owner);
                // Deliberately bypass award(): it can merge into an existing orb
                // before a new orb's ownership has been assigned. Spawn-event
                // cancellation is honored, without repeatedly retrying a veto.
                level.addFreshEntity(orb);
            }
        }
    }

    public int pendingCount() {
        return drops.size();
    }

    /** Cancels outstanding deliveries belonging to a player without touching live orbs. */
    public void removeForOwner(UUID owner) {
        if (drops.removeIf(drop -> drop.owner.equals(owner))) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", SCHEMA_VERSION);
        ListTag entries = new ListTag();
        for (PendingDrop drop : drops) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", drop.owner);
            entry.putDouble("x", drop.position.x);
            entry.putDouble("y", drop.position.y);
            entry.putDouble("z", drop.position.z);
            entry.putInt("amount", drop.amount);
            entry.putInt("remaining_ticks", drop.remainingTicks);
            entry.putLong("last_tick", drop.lastTick);
            entries.add(entry);
        }
        tag.put("drops", entries);
        return tag;
    }

    public static DeathOrbQueue load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.getInt("version");
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported MITE death orb queue version: " + version);
        }
        DeathOrbQueue queue = new DeathOrbQueue();
        ListTag entries = tag.getList("drops", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            double x = entry.getDouble("x"), y = entry.getDouble("y"), z = entry.getDouble("z");
            int amount = entry.getInt("amount");
            if (!entry.hasUUID("owner") || amount <= 0
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Invalid MITE death orb queue entry " + index);
            }
            int delay = Math.max(1, Math.min(DROP_DELAY_TICKS, entry.getInt("remaining_ticks")));
            queue.drops.add(new PendingDrop(entry.getUUID("owner"), new Vec3(x, y, z),
                    amount, delay, entry.getLong("last_tick")));
        }
        return queue;
    }

    private static final class PendingDrop {
        private final UUID owner;
        private final Vec3 position;
        private final int amount;
        private int remainingTicks;
        private long lastTick;

        private PendingDrop(UUID owner, Vec3 position, int amount, int remainingTicks, long lastTick) {
            this.owner = owner;
            this.position = position;
            this.amount = amount;
            this.remainingTicks = remainingTicks;
            this.lastTick = lastTick;
        }
    }
}
