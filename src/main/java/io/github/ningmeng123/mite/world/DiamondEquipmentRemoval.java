package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Remove retired gear at storage and gameplay boundaries without removing registry identities. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class DiamondEquipmentRemoval {
    private DiamondEquipmentRemoval() {}

    /** Copy only when nested contents change; retain unrelated components and container slot positions. */
    public static ItemStack clean(ItemStack stack) {
        if (RemovedDiamondEquipment.isRemoved(stack)) return ItemStack.EMPTY;
        if (stack.isEmpty()) return stack;
        ItemStack result = stack;
        var contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (var item : contents.nonEmptyItems()) if (clean(item) != item) {
                result = result.copy();
                result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents.stream().map(DiamondEquipmentRemoval::clean).toList()));
                break;
            }
        }
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (var item : bundle.items()) if (clean(item) != item) {
                var cleaned = new ArrayList<ItemStack>();
                for (var child : bundle.items()) {
                    var replacement = clean(child);
                    if (!replacement.isEmpty()) cleaned.add(replacement);
                }
                if (result == stack) result = result.copy();
                result.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(cleaned));
                break;
            }
        }
        return result;
    }

    public static boolean prune(Container container) {
        boolean changed = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            var current = container.getItem(i);
            var replacement = clean(current);
            if (replacement != current) { container.setItem(i, replacement); changed = true; }
        }
        if (changed) container.setChanged();
        return changed;
    }

    public static void purge(LivingEntity entity) {
        for (var slot : EquipmentSlot.values()) {
            var current = entity.getItemBySlot(slot);
            var replacement = clean(current);
            if (replacement != current) entity.setItemSlot(slot, replacement);
        }
        if (entity instanceof InventoryCarrier carrier) prune(carrier.getInventory());
        if (entity instanceof ServerPlayer player) {
            prune(player.getInventory());
            prune(player.getEnderChestInventory());
            cleanMenu(player.containerMenu);
        }
    }

    public static void cleanMenu(AbstractContainerMenu menu) {
        for (var slot : menu.slots) {
            var current = slot.getItem();
            var replacement = clean(current);
            if (replacement != current) slot.set(replacement);
        }
        var carried = menu.getCarried();
        var replacement = clean(carried);
        if (replacement != carried) menu.setCarried(replacement);
    }

    private static void cleanDrop(ItemEntity entity) {
        var current = entity.getItem();
        var replacement = clean(current);
        if (replacement.isEmpty()) entity.discard();
        else if (replacement != current) entity.setItem(replacement);
    }

    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof ItemEntity item) {
            cleanDrop(item);
            if (item.isRemoved()) event.setCanceled(true);
        } else if (event.getEntity() instanceof LivingEntity living) purge(living);
    }

    @SubscribeEvent public static void onTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof ItemEntity item) cleanDrop(item);
        else if (event.getEntity() instanceof LivingEntity living && !(living instanceof ServerPlayer)) purge(living);
    }

    @SubscribeEvent public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) purge(player);
    }

    @SubscribeEvent public static void onAttack(AttackEntityEvent event) {
        if (RemovedDiamondEquipment.isRemoved(event.getEntity().getMainHandItem())) {
            event.setCanceled(true);
            if (!event.getEntity().level().isClientSide) purge(event.getEntity());
        }
    }

    @SubscribeEvent public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (RemovedDiamondEquipment.isRemoved(event.getEntity().getMainHandItem())) {
            event.setNewSpeed(0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void onBreak(BlockEvent.BreakEvent event) {
        if (RemovedDiamondEquipment.isRemoved(event.getPlayer().getMainHandItem())) {
            event.setCanceled(true);
            purge(event.getPlayer());
        }
    }

    @SubscribeEvent public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (RemovedDiamondEquipment.isRemoved(event.getItemStack())) event.setCanceled(true);
    }
    @SubscribeEvent public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (RemovedDiamondEquipment.isRemoved(event.getItemStack())) event.setCanceled(true);
    }
    @SubscribeEvent public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (RemovedDiamondEquipment.isRemoved(event.getItemStack())) event.setCanceled(true);
    }
    @SubscribeEvent public static void onUseEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (RemovedDiamondEquipment.isRemoved(event.getItemStack())) event.setCanceled(true);
    }
}
