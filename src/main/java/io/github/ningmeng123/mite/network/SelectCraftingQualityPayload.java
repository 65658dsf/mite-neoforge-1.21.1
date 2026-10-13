package io.github.ningmeng123.mite.network;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A request to move one choice; the server derives the quality, cost and eligibility. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record SelectCraftingQualityPayload(int containerId, int direction) implements CustomPacketPayload {
    public static final Type<SelectCraftingQualityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "select_crafting_quality"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectCraftingQualityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectCraftingQualityPayload::containerId,
            ByteBufCodecs.VAR_INT, SelectCraftingQualityPayload::direction, SelectCraftingQualityPayload::new);

    @Override public Type<SelectCraftingQualityPayload> type() { return TYPE; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || (payload.direction() != -1 && payload.direction() != 1)
                    || player.containerMenu.containerId != payload.containerId()
                    || !(player.containerMenu instanceof CraftingMenu || player.containerMenu instanceof InventoryMenu)) return;
            TimedCraftingController.selectQuality(player, player.containerMenu, payload.direction());
        }));
    }
}
