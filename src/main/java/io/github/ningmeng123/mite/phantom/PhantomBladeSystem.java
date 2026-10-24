package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** One temporary sword formation per online player's main inventory. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PhantomBladeSystem {
    public static final double RANGE = 10D;
    private static final Map<UUID, PhantomBladeSwarm> ACTIVE = new HashMap<>();

    private PhantomBladeSystem() {}

    @Nullable public static PhantomBladeRules.Profile selected(ServerPlayer player) {
        PhantomBladeRules.Profile strongest = null;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || !(stack.getItem() instanceof PhantomBladeItem item)) continue;
            var profile = PhantomBladeRules.profile(item.material());
            if (profile != null && (strongest == null || profile.rank() > strongest.rank())) strongest = profile;
        }
        return strongest;
    }

    public static boolean active(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    /** Neutral enemies require actual anger at this owner; allies and pets are never collateral targets. */
    public static boolean validTarget(ServerPlayer owner, LivingEntity target) {
        if (!(target instanceof Mob mob) || !target.isAlive() || target.isSpectator() || !target.isAttackable()
                || !target.canBeHitByProjectile() || target.level() != owner.level()
                || owner.distanceToSqr(target) > RANGE * RANGE || owner.isAlliedTo(target) || target.isAlliedTo(owner)
                || target instanceof TamableAnimal tame && tame.isTame()
                || target instanceof OwnableEntity owned && owned.getOwnerUUID() != null) return false;
        if (target instanceof NeutralMob neutral) return neutral.isAngryAt(owner) || mob.getTarget() == owner;
        return target instanceof Enemy || mob.getTarget() == owner;
    }

    @Nullable public static PhantomBladeSwarm swarm(ServerPlayer player) {
        PhantomBladeSwarm swarm = ACTIVE.get(player.getUUID());
        return swarm != null && !swarm.isRemoved() ? swarm : null;
    }

    public static void refresh(ServerPlayer player) {
        var selected = active(player) ? selected(player) : null;
        PhantomBladeSwarm old = ACTIVE.get(player.getUUID());
        if (old != null && (old.isRemoved() || old.level() != player.level() || selected == null
                || !old.material().equals(selected.material()))) clear(player);
        if (selected == null || ACTIVE.containsKey(player.getUUID())) return;
        PhantomBladeSwarm swarm = new PhantomBladeSwarm(MitePhantomBlades.SWARM.get(), player.level());
        swarm.initialize(player, selected);
        if (player.serverLevel().addFreshEntity(swarm)) ACTIVE.put(player.getUUID(), swarm);
    }

    public static void clear(ServerPlayer player) {
        PhantomBladeSwarm swarm = ACTIVE.remove(player.getUUID());
        if (swarm != null) swarm.discard();
    }

    static void removed(PhantomBladeSwarm swarm) { ACTIVE.values().removeIf(existing -> existing == swarm); }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer replacement)
            PhantomBladeState.copy(original, replacement);
        if (event.getOriginal() instanceof ServerPlayer player) clear(player);
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}
