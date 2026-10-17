package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.client.AutoHarvestInput;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Both requested automatic modes color the selected background and frame before its item. @author NingMeng123 */
@Mixin(Gui.class)
public abstract class AutoModeHotbarMixin {
    @WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Gui;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V"))
    private void mite$automaticSlot(Gui gui, GuiGraphics graphics, int x, int y, DeltaTracker delta,
            Player player, ItemStack stack, int seed, Operation<Void> original) {
        if (seed == player.getInventory().selected + 1 && AutoHarvestInput.highlighted(Minecraft.getInstance())) {
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xB03D7D32);
            int green = 0xFF91DC74;
            graphics.fill(x - 4, y - 4, x + 20, y - 2, green);
            graphics.fill(x - 4, y + 17, x + 20, y + 19, green);
            graphics.fill(x - 4, y - 2, x - 2, y + 17, green);
            graphics.fill(x + 18, y - 2, x + 20, y + 17, green);
        }
        original.call(gui, graphics, x, y, delta, player, stack, seed);
    }
}
