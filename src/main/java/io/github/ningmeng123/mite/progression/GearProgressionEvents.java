package io.github.ningmeng123.mite.progression;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.mining.SourceMiningRequirements;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/** Experience belongs to the actual completed server action and its original equipment. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class GearProgressionEvents {
    private static final String PROJECTILE_WEAPON = "mite:progression_weapon";
    private static final String KILL_REWARDED = "mite:progression_kill_rewarded";
    private static final String LAST_PLAYER = "mite:progression_last_player";
    private static final String LAST_WEAPON = "mite:progression_last_weapon";
    private static final String LAST_PROJECTILE = "mite:progression_last_projectile";
    private static final String LAST_TIME = "mite:progression_last_hit_time";
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Map<DamageContainer, ArmorHit> ARMOR_HITS = new WeakHashMap<>();
    private static final ThreadLocal<MiningAction> MINING = new ThreadLocal<>();

    private record ArmorHit(ServerPlayer player, List<ItemStack> armor, float damage) {}
    private static final class MiningAction {
        private final ServerPlayer player;
        private final BlockPos pos;
        private final ItemStack tool;
        private final BlockState state;
        private final boolean placed;
        private final float hardness;
        private boolean rewarded;
        private MiningAction(ServerPlayer player, BlockPos pos) {
            this.player = player;
            this.pos = pos.immutable();
            tool = player.getMainHandItem();
            state = player.serverLevel().getBlockState(pos);
            placed = PlayerPlacedBlocks.isPlaced(player.level(), pos);
            hardness = MiningProgress.effectiveHardness(state, state.getDestroySpeed(player.level(), pos));
        }
    }

    private GearProgressionEvents() {}

    public static boolean mining(ServerPlayer player, BlockPos pos, Supplier<Boolean> action) {
        if (!GearProgression.canGain(player) || !player.serverLevel().hasChunkAt(pos)) return action.get();
        MiningAction previous = MINING.get();
        MINING.set(new MiningAction(player, pos));
        try { return action.get(); }
        finally { if (previous == null) MINING.remove(); else MINING.set(previous); }
    }

    public static void mined(ServerPlayer player, BlockPos pos, boolean canHarvest) {
        MiningAction action = MINING.get();
        if (action == null || action.rewarded || action.player != player || !action.pos.equals(pos)) return;
        action.rewarded = true;
        if (canHarvest && GearProgression.canGain(player))
            io.github.ningmeng123.mite.worldgen.StructureExplorationProgress.mined(player, action.state, action.placed);
        if (!canHarvest || action.placed || !GearProgression.canGain(player)
                || !(action.hardness > 0F) || !GearProgression.canMine(action.tool, action.state, player.level(), pos)) return;
        GearProgression.award(action.tool, GearProgressionRules.miningExperienceMillis(action.hardness,
                SourceMiningRequirements.minimumHarvestLevel(action.state)), player);
    }

    /** Called after NeoForge has accepted every placement snapshot and restored committed item data. */
    public static void beforeCommittedUse(UseOnContext context, BlockPos pos, BlockState state, boolean blockItem) {
        if (context.getPlayer() != null && context.getLevel() instanceof ServerLevel
                && blockItem && !state.isAir())
            PlayerPlacedBlocks.set(context.getLevel(), pos, true);
    }

    public static void afterCommittedUse(UseOnContext context, BlockPos pos, BlockState previous) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !GearProgression.canGain(player)
                || previous.is(Blocks.FARMLAND) || !context.getLevel().getBlockState(pos).is(Blocks.FARMLAND)
                || !GearProgression.canTill(context.getItemInHand())) return;
        GearProgression.award(context.getItemInHand(), GearProgressionRules.TILL_EXPERIENCE_MILLIS, player);
    }

    public static void projectile(Projectile projectile, ItemStack weapon) {
        UUID id = GearProgression.ensureId(weapon);
        if (id != null) projectile.getPersistentData().putUUID(PROJECTILE_WEAPON, id);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void killed(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity() instanceof Player
                || event.getEntity().getPersistentData().getBoolean(KILL_REWARDED)
                || !(event.getEntity().level() instanceof ServerLevel level)) return;
        var direct = event.getSource().getDirectEntity();
        ServerPlayer player;
        ItemStack weapon;
        ProgressionThrownWeapon thrown = null;
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            player = attacker;
            weapon = sourceWeapon(player, direct);
            if (direct instanceof ProgressionThrownWeapon carried) thrown = carried;
        } else {
            // Environmental finishes retain only the weapon still credited by vanilla.
            // Another creature or a pet cannot borrow the player's previous weapon.
            if (event.getSource().getEntity() != null || direct != null) return;
            var data = event.getEntity().getPersistentData();
            long age = level.getGameTime() - data.getLong(LAST_TIME);
            if (!data.hasUUID(LAST_PLAYER) || !data.hasUUID(LAST_WEAPON) || age < 0 || age > 100) return;
            player = level.getServer().getPlayerList().getPlayer(data.getUUID(LAST_PLAYER));
            if (player == null || event.getEntity().getKillCredit() != player) return;
            weapon = findWeapon(player, data.getUUID(LAST_WEAPON));
            if (weapon.isEmpty() && data.hasUUID(LAST_PROJECTILE)) {
                var projectile = level.getEntity(data.getUUID(LAST_PROJECTILE));
                if (projectile instanceof ProgressionThrownWeapon carried) {
                    thrown = carried;
                    weapon = carried.mite$progressionWeapon();
                } else if (projectile instanceof ThrownTrident trident) weapon = trident.getWeaponItem();
                if (!data.getUUID(LAST_WEAPON).equals(GearProgression.id(weapon))) weapon = ItemStack.EMPTY;
            }
        }
        if (!GearProgression.canGain(player) || !GearProgression.canKill(weapon)) return;
        event.getEntity().getPersistentData().putBoolean(KILL_REWARDED, true);
        GearProgression.award(weapon, GearProgressionRules.killExperienceMillis(event.getEntity().getMaxHealth()), player);
        if (thrown != null) thrown.mite$syncProgressionWeapon();
    }

    private static ItemStack sourceWeapon(ServerPlayer player, net.minecraft.world.entity.Entity direct) {
        if (direct instanceof ProgressionThrownWeapon carried) return carried.mite$progressionWeapon();
        if (direct instanceof ThrownTrident trident) return trident.getWeaponItem();
        if (direct instanceof Projectile projectile) {
            if (projectile.getPersistentData().hasUUID(PROJECTILE_WEAPON))
                return findWeapon(player, projectile.getPersistentData().getUUID(PROJECTILE_WEAPON));
            else if (projectile instanceof AbstractArrow arrow && arrow.getWeaponItem() != null) {
                UUID id = GearProgression.id(arrow.getWeaponItem());
                if (id != null) return findWeapon(player, id);
            }
        } else if (direct == player) return player.getWeaponItem();
        return ItemStack.EMPTY;
    }

    private static void rememberHit(LivingEntity victim, DamageContainer container) {
        if (victim instanceof Player || !(container.getNewDamage() > 0F)
                || !(container.getSource().getEntity() instanceof ServerPlayer player) || !GearProgression.canGain(player)) return;
        var data = victim.getPersistentData();
        ItemStack weapon = sourceWeapon(player, container.getSource().getDirectEntity());
        if (!GearProgression.canKill(weapon)) {
            data.remove(LAST_PLAYER);
            data.remove(LAST_WEAPON);
            data.remove(LAST_PROJECTILE);
            data.remove(LAST_TIME);
            return;
        }
        data.putUUID(LAST_PLAYER, player.getUUID());
        data.putUUID(LAST_WEAPON, GearProgression.ensureId(weapon));
        data.putLong(LAST_TIME, victim.level().getGameTime());
        if (container.getSource().getDirectEntity() instanceof Projectile projectile)
            data.putUUID(LAST_PROJECTILE, projectile.getUUID());
        else data.remove(LAST_PROJECTILE);
    }

    private static ItemStack findWeapon(ServerPlayer player, UUID id) {
        ItemStack found = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (!id.equals(GearProgression.id(candidate))) continue;
            // Copied UUIDs are ambiguous: never duplicate an experience reward across cloned items.
            if (!found.isEmpty()) return ItemStack.EMPTY;
            found = candidate;
        }
        return found;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !GearProgression.canGain(player)) return;
        List<ItemStack> armor = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (GearProgression.isArmor(stack) && GearProgression.maxLevel(stack) > 0) armor.add(stack);
        }
        if (armor.isEmpty()) return;
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
            // This runs after shield/cooldown processing, before the armor reduction is subtracted.
            float damage = container.getNewDamage();
            if (Float.isFinite(damage) && damage > 0F) ARMOR_HITS.put(container, new ArmorHit(player, armor, damage));
            return reduction;
        });
    }

    public static void damageCompleted(LivingEntity entity, DamageContainer container) {
        rememberHit(entity, container);
        ArmorHit hit = ARMOR_HITS.remove(container);
        if (hit == null || hit.player() != entity || !GearProgression.canGain(hit.player())) return;
        long total = GearProgressionRules.damageExperienceMillis(hit.damage());
        long each = total / hit.armor().size(), remainder = total % hit.armor().size();
        for (int index = 0; index < hit.armor().size(); index++)
            GearProgression.award(hit.armor().get(index), each + (index < remainder ? 1 : 0), hit.player());
    }

    public static void shieldBlocked(LivingEntity entity, LivingShieldBlockEvent event) {
        if (event.isCanceled() || !event.getBlocked() || !(entity instanceof ServerPlayer player)
                || !GearProgression.canGain(player) || !GearProgression.isShield(entity.getUseItem())) return;
        GearProgression.award(entity.getUseItem(), GearProgressionRules.shieldExperienceMillis(event.getBlockedDamage()), player);
    }
}
