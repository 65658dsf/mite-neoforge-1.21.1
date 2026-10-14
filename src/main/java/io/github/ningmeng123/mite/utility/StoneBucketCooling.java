package io.github.ningmeng123.mite.utility;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** jv/ss solidify carried and dropped lava only upon actual water immersion. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class StoneBucketCooling {
    private StoneBucketCooling() {}
    public static String lavaMaterial(ItemStack stack) {
        if (stack.is(Items.LAVA_BUCKET)) return "iron";
        return stack.getItem() instanceof MiteBucketItem bucket && bucket.contents() == MiteBucketItem.Contents.LAVA ? bucket.metal() : null;
    }
    public static ItemStack cooled(ItemStack stack) {
        String metal = lavaMaterial(stack);
        // ud.convertAllItemsInSlot and ss.convertItem construct a fresh vessel; no old name/NBT is copied.
        return metal == null ? ItemStack.EMPTY : new ItemStack(MiteUtilityContent.get(metal + "_stone_bucket"), stack.getCount());
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel) || !player.isEyeInFluid(FluidTags.WATER)) return;
        boolean changed = solidifyInventory(player);
        if (changed) steam((ServerLevel)player.level(), player.getX(), player.getY(), player.getZ());
    }
    public static boolean solidifyInventory(Player player) {
        boolean changed = false;
        // Main inventory is source; the new offhand receives the same carried-vessel rule.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (slot >= player.getInventory().items.size() && slot != player.getInventory().getContainerSize() - 1) continue;
            ItemStack result = cooled(player.getInventory().getItem(slot));
            if (result.isEmpty()) continue;
            int surplus = result.getCount() - result.getMaxStackSize();
            result.setCount(Math.min(result.getCount(), result.getMaxStackSize()));
            player.getInventory().setItem(slot, result);
            while (surplus-- > 0) {
                ItemStack extra = result.copyWithCount(1);
                if (!player.getInventory().add(extra)) player.drop(extra, false);
            }
            changed = true;
        }
        if (changed) player.getInventory().setChanged();
        return changed;
    }
    @SubscribeEvent public static void entityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity entity) || !(entity.level() instanceof ServerLevel level)
                || entity.isRemoved() || !entity.isInWater()) return;
        solidifyDropped(entity);
    }
    public static boolean solidifyDropped(ItemEntity entity) {
        ItemStack result = cooled(entity.getItem());
        if (result.isEmpty()) return false;
        int surplus = result.getCount() - result.getMaxStackSize();
        result.setCount(Math.min(result.getCount(), result.getMaxStackSize()));
        entity.setItem(result);
        while (surplus-- > 0) {
            ItemEntity extra = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), result.copyWithCount(1));
            extra.setDeltaMovement(entity.getDeltaMovement()); extra.setDefaultPickUpDelay();
            entity.level().addFreshEntity(extra);
        }
        if (entity.level() instanceof ServerLevel level) steam(level, entity.getX(), entity.getY(), entity.getZ());
        return true;
    }
    private static void steam(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.CLOUD, x, y + .4, z, 4, .2, .2, .2, .02);
        level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .7F, 1F);
    }
}
