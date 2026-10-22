package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.BucketPlacementInputPayload;
import io.github.ningmeng123.mite.world.BucketPlacementIntent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends Ctrl intent immediately before the matching vanilla use packet. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class BucketPlacementInput {
    private BucketPlacementInput() {}

    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if (BucketPlacementIntent.isPlacementBucket(event.getItemStack()))
            event.getToolTip().add(Component.translatable("tooltip.mite.bucket_source").withStyle(ChatFormatting.GRAY));
        else if (BucketPlacementIntent.isEmptyBucket(event.getItemStack()))
            event.getToolTip().add(Component.translatable("tooltip.mite.bucket_pickup").withStyle(ChatFormatting.GRAY));
    }

    /** Called from the prediction packet send hook, after vanilla synchronizes the selected slot. */
    public static void beforeSend(Packet<?> packet) {
        if (!(packet instanceof ServerboundUseItemPacket use)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        // Prediction may already have exchanged empty and full buckets. The server
        // therefore checks the real held item instead of trusting a post-prediction client stack.
        PacketDistributor.sendToServer(new BucketPlacementInputPayload(
                use.getHand(), use.getSequence(), Screen.hasControlDown()));
    }
}
