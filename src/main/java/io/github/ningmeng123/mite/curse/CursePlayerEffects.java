package io.github.ningmeng123.mite.curse;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Source uf/of restrictions and nn's fear rolls, enforced on the authoritative side. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class CursePlayerEffects {
    private CursePlayerEffects() {}

    public static void realize(ServerPlayer player, R196Curse curse) {
        if (curse == R196Curse.CANNOT_WEAR_ARMOR) dropArmor(player);
        if (curse == R196Curse.CANNOT_HOLD_BREATH) player.setAirSupply(Math.min(player.getAirSupply(), 90));
        if (curse == R196Curse.CANNOT_RUN && player.isSprinting()) player.setSprinting(false);
        if (curse == R196Curse.CANNOT_SLEEP && player.isSleeping()) {
            PlayerCurses.reveal(player);
            player.stopSleepInBed(true, true);
        }
    }

    /** Remove first, then drop: direct inventory writes and dispenser equipment cannot keep armor. */
    public static void dropArmor(ServerPlayer player) {
        boolean removed = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.isEmpty()) continue;
            player.setItemSlot(slot, ItemStack.EMPTY);
            player.drop(worn, true, false);
            removed = true;
        }
        if (removed) {
            PlayerCurses.reveal(player);
            player.inventoryMenu.broadcastChanges();
        }
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerCurses.has(player, R196Curse.CANNOT_WEAR_ARMOR)) dropArmor(player);
        if (player.isSprinting() && PlayerCurses.has(player, R196Curse.CANNOT_RUN, true)) player.setSprinting(false);
    }

    /** Original material.plants/material.vine slows input, not accumulated velocity or vertical motion. */
    public static float entanglementFactor(BlockState state) {
        var block = state.getBlock();
        if (block instanceof VineBlock) return .2F;
        return block instanceof BushBlock || block instanceof GrowingPlantBlock
                || block instanceof SugarCaneBlock || block instanceof BambooStalkBlock ? .4F : 1F;
    }

    public static Vec3 movement(Player player, Vec3 input) {
        float factor = entanglementFactor(player.level().getBlockState(player.blockPosition()));
        return factor < 1F && PlayerCurses.has(player, R196Curse.ENTANGLEMENT, true)
                ? input.multiply(factor, 1D, factor) : input;
    }

    public static boolean cannotEquip(Player player, EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && PlayerCurses.has(player, R196Curse.CANNOT_WEAR_ARMOR, true);
    }

    /** Resolve identity before availability: LivingEntity's helper falls back to MAINHAND
     * when our canUseSlot guard rejects armor, which must never turn it into a held tool. */
    public static EquipmentSlot rawEquipmentSlot(ItemStack stack) {
        if (stack.isEmpty()) return EquipmentSlot.MAINHAND;
        EquipmentSlot override = stack.getEquipmentSlot();
        if (override != null) return override; // Preserve NeoForge's per-stack slot override priority.
        Equipable equipable = Equipable.get(stack); // Also finds equipable blocks such as pumpkins/skulls.
        return equipable == null ? EquipmentSlot.MAINHAND : equipable.getEquipmentSlot();
    }

    /** The check precedes equipment durability, on-hit enchantments and native attack accounting. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        // One authoritative roll avoids independent client/server results consuming two rolls.
        if (event.getEntity().level().isClientSide) return;
        if (preventsAttack(event.getEntity(), target)) event.setCanceled(true);
    }

    public static R196Curse fearFor(Entity target) {
        if (target instanceof Spider) return R196Curse.FEAR_OF_SPIDERS;
        if (target instanceof Wolf) return R196Curse.FEAR_OF_WOLVES;
        if (target instanceof Creeper) return R196Curse.FEAR_OF_CREEPERS;
        return target.getType().is(EntityTypeTags.UNDEAD) ? R196Curse.FEAR_OF_UNDEAD : null;
    }

    public static boolean preventsAttack(Player player, LivingEntity target) {
        R196Curse fear = fearFor(target);
        // Source evaluates nextInt before querying the curse; successful 1/4 hits do not reveal it.
        return fear != null && target.getRandom().nextInt(4) > 0 && PlayerCurses.has(player, fear, true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sleep(CanPlayerSleepEvent event) {
        if (PlayerCurses.has(event.getEntity(), R196Curse.CANNOT_SLEEP, true))
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sleeping(CanContinueSleepingEvent event) {
        if (event.getEntity() instanceof Player player && PlayerCurses.has(player, R196Curse.CANNOT_SLEEP, true))
            event.setContinueSleeping(false);
    }
}
