package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.ThrowEggPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cancels vanilla use before its packets are sent, yielding exactly one explicit throw request. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class EggThrowInput {
    private EggThrowInput() {}

    @SubscribeEvent public static void throwWithControl(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !Screen.hasControlDown()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.getConnection() == null
                || minecraft.player.isSpectator() || !minecraft.player.getItemInHand(event.getHand()).is(Items.EGG)) return;
        // This event precedes useItemOn/useItem. Cancellation returns from the whole
        // vanilla hand loop, preventing a second food/throw packet or offhand action.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (minecraft.player.isUsingItem() || minecraft.player.getCooldowns().isOnCooldown(Items.EGG)) return;
        // MultiPlayerGameMode.useItem would normally synchronize the selected slot.
        // Our earlier interception must send it before the custom request as well.
        minecraft.getConnection().send(new ServerboundSetCarriedItemPacket(minecraft.player.getInventory().selected));
        PacketDistributor.sendToServer(new ThrowEggPayload(event.getHand(), minecraft.player.getYRot(), minecraft.player.getXRot()));
        event.setSwingHand(true);
    }

    @SubscribeEvent public static void explainControl(ItemTooltipEvent event) {
        if (event.getItemStack().is(Items.EGG))
            event.getToolTip().add(Component.translatable("tooltip.mite.egg_throw").withStyle(ChatFormatting.GRAY));
    }
}
