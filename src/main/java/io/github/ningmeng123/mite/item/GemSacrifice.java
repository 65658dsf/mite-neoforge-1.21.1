package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Instant whole-gem sacrifice; fragments never qualify. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class GemSacrifice {
    private GemSacrifice() {}

    public static int experienceValue(ItemStack stack) {
        if (stack.is(Items.LAPIS_LAZULI)) return 25;
        if (stack.is(Items.QUARTZ)) return 50;
        if (stack.is(Items.EMERALD)) return 250;
        if (stack.is(Items.DIAMOND)) return 500;
        return 0;
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        int value = experienceValue(stack);
        if (event.isCanceled() || value == 0 || stack.isEmpty() || event.getEntity().isSpectator()) return;
        if (!event.getLevel().isClientSide()) {
            var player = event.getEntity();
            var item = stack.getItem();
            // R196 consumes one even in creative and does not heal from this XP source.
            stack.shrink(1);
            PlayerExperience.addPoints(player, value);
            player.awardStat(Stats.ITEM_USED.get(item));
            event.getLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.1F, 1.0F);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }
}
