package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.network.AutoRestockSelectionPayload;
import io.github.ningmeng123.mite.rules.AutoRestockRules;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Real item actions authorize a bounded, server-owned restock; no client inventory declarations. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class AutoRestock {
    public record Before(ServerPlayer player, int slot, ItemStack item) {}
    private record Pending(int slot, ItemStack previous, ItemStack after, long started) {}
    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();
    private static final Set<ServerPlayer> PAUSED = Collections.newSetFromMap(new WeakHashMap<>());
    private AutoRestock() {}

    public static Before before(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer server) || hand != InteractionHand.MAIN_HAND
                || !server.isAlive() || server.isSpectator() || server.hasInfiniteMaterials() || PAUSED.contains(server)) return null;
        return new Before(server, server.getInventory().selected, server.getMainHandItem().copy());
    }
    public static void after(Before before) { after(before, Util.getMillis()); }
    public static void after(Before before, long now) {
        if (before == null || before.item.isEmpty() || before.player.getInventory().selected != before.slot || PAUSED.contains(before.player)) return;
        var current = before.player.getMainHandItem();
        if (!current.isEmpty() && current.is(before.item.getItem())) return;
        PENDING.put(before.player, new Pending(before.slot, before.item.copy(), current.copy(), now));
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) tick(player, Util.getMillis());
    }
    public static void pause(ServerPlayer player, boolean paused) {
        if (paused) { PAUSED.add(player); PENDING.remove(player); }
        else PAUSED.remove(player);
    }
    public static void tick(ServerPlayer player, long now) {
        Pending pending = PENDING.get(player);
        if (pending == null) return;
        long elapsed = now - pending.started;
        if (elapsed < 0 || elapsed >= AutoRestockRules.EXPIRES_MILLIS || PAUSED.contains(player) || !player.isAlive() || player.isSpectator()
                || player.isSleeping() || player.containerMenu != player.inventoryMenu
                || player.getInventory().selected != pending.slot
                || !ItemStack.matches(player.getMainHandItem(), pending.after)) {
            PENDING.remove(player); return;
        }
        int next = AutoRestockRules.nextSlot(pending.slot, player.getInventory().items.size(),
                slot -> similarity(pending.previous, player.getInventory().getItem(slot)));
        if (next < 0 || next == pending.slot) {
            if (elapsed >= AutoRestockRules.INVENTORY_DELAY_MILLIS) {
                AutoRestockSelectionPayload.send(player, pending.slot, -1);
                PENDING.remove(player);
            }
            return;
        }
        if (next >= 9) {
            if (elapsed < AutoRestockRules.INVENTORY_DELAY_MILLIS) return;
            if (!player.getMainHandItem().isEmpty()) {
                AutoRestockSelectionPayload.send(player, pending.slot, -1);
                PENDING.remove(player); return;
            }
            // Move the complete existing stack with all its components; nothing is recreated or discarded.
            ItemStack replacement = player.getInventory().getItem(next);
            player.getInventory().setItem(next, ItemStack.EMPTY);
            player.getInventory().setItem(pending.slot, replacement);
        } else {
            AutoRestockSelectionPayload.send(player, pending.slot, next);
            player.getInventory().selected = next;
            if (player.connection != null) player.connection.send(new ClientboundSetCarriedItemPacket(next));
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        PENDING.remove(player);
    }

    public static int similarity(ItemStack previous, ItemStack candidate) {
        if (previous.isEmpty() || candidate.isEmpty()) return 0;
        if (previous.is(candidate.getItem())) {
            if (previous.getItem() instanceof io.github.ningmeng123.mite.item.RunestoneItem
                    && io.github.ningmeng123.mite.item.RunestoneItem.rune(previous) != io.github.ningmeng123.mite.item.RunestoneItem.rune(candidate)) return 0;
            // Old potion subtype mattered; damage, names, enchantments and quality did not.
            return java.util.Objects.equals(previous.get(DataComponents.POTION_CONTENTS), candidate.get(DataComponents.POTION_CONTENTS)) ? 100 : 0;
        }
        var first = EquipmentDurability.toolProfile(previous);
        var second = EquipmentDurability.toolProfile(candidate);
        if ((previous.is(Items.GRAVEL) || previous.is(io.github.ningmeng123.mite.registry.MiteResourceBlocks.NETHER_GRAVEL.get().asItem()))
                && second != null && second.family() == io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.SHOVEL) return 1;
        if (first != null && second != null) return AutoRestockRules.familySimilarity(first.family().name(), second.family().name());
        if (first != null && (first.family() == io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.CUDGEL
                || first.family() == io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.CLUB)
                && (candidate.is(Items.STICK) || candidate.is(Items.BONE))) return 1;
        if (previous.getItem() instanceof BowItem && candidate.getItem() instanceof BowItem) return 99;
        Bucket a = bucket(previous), b = bucket(candidate);
        if (a != null && b != null) {
            if (a.contents.equals(b.contents)) return 99;
            return a.contents.equals("empty") || b.contents.equals("empty") ? a.material.equals(b.material) ? 98 : 97 : 0;
        }
        boolean bowlA = bowl(previous), bowlB = bowl(candidate);
        if (bowlA && bowlB) return previous.is(Items.BOWL) || candidate.is(Items.BOWL) ? 2 : 1;
        if (previous.is(Items.POTION) && candidate.is(Items.GLASS_BOTTLE)) return 1;
        return 0;
    }
    private record Bucket(String material, String contents) {}
    private static Bucket bucket(ItemStack stack) {
        if (stack.getItem() instanceof io.github.ningmeng123.mite.item.R196StoneBucketItem item) return new Bucket(item.material(), "stone");
        if (stack.getItem() instanceof MiteBucketItem item) return new Bucket(item.metal(), item.contents().name().toLowerCase(java.util.Locale.ROOT));
        if (stack.is(Items.BUCKET)) return new Bucket("iron", "empty");
        if (stack.is(Items.WATER_BUCKET)) return new Bucket("iron", "water");
        if (stack.is(Items.LAVA_BUCKET)) return new Bucket("iron", "lava");
        if (stack.is(Items.MILK_BUCKET)) return new Bucket("iron", "milk");
        return null;
    }
    private static boolean bowl(ItemStack stack) { return stack.is(Items.BOWL) || FoodVessels.empty(stack).is(Items.BOWL); }
}
