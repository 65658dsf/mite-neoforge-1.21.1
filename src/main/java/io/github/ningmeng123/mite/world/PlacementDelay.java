package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlacementDelay {
    public static final int DELAY_TICKS = 6;
    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();

    private record Pending(long due, ResourceKey<Level> dimension, InteractionHand hand, ItemStack stack,
                           ItemStack snapshot, int selectedSlot, @Nullable BlockHitResult hit, boolean sourceRequested) {}

    private PlacementDelay() {}

    public static InteractionResult defer(ServerPlayer player, InteractionHand hand, @Nullable BlockHitResult hit, boolean sourceRequested) {
        if (!PENDING.containsKey(player)) {
            ItemStack stack = player.getItemInHand(hand);
            PENDING.put(player, new Pending(now(player) + DELAY_TICKS, player.level().dimension(), hand,
                    stack, stack.copy(), player.getInventory().selected, hit, sourceRequested));
        }
        return InteractionResult.FAIL;
    }

    private static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }

    public static void tickPlayer(ServerPlayer player) {
        tickPlayer(player, now(player));
    }

    public static void tickPlayer(ServerPlayer player, long gameTime) {
        Pending pending = PENDING.get(player);
        if (pending == null) return;
        ItemStack stack = player.getItemInHand(pending.hand());
        if (!player.isAlive() || player.isSpectator() || player.hasInfiniteMaterials() || player.isSleeping()
                || player.level().dimension() != pending.dimension() || stack != pending.stack()
                || !ItemStack.matches(stack, pending.snapshot())
                || pending.hand() == InteractionHand.MAIN_HAND && player.getInventory().selected != pending.selectedSlot()) {
            PENDING.remove(player);
            return;
        }
        if (gameTime < pending.due()) return;
        PENDING.remove(player);
        if (pending.hit() == null) {
            BucketPlacementController.withIntent(player, pending.sourceRequested(),
                    () -> player.gameMode.useItem(player, player.level(), stack, pending.hand()));
        } else if (SourcePlayerReach.canReachBlock(player, pending.hit().getBlockPos(), pending.hand())
                && !player.level().isOutsideBuildHeight(pending.hit().getBlockPos())
                && player.level().mayInteract(player, pending.hit().getBlockPos())) {
            player.gameMode.useItemOn(player, player.level(), stack, pending.hand(), pending.hit());
        }
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.sendAllDataToRemote();
    }

    public static void clear(ServerPlayer player) { PENDING.remove(player); }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) tickPlayer(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) clear(player);
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
}
