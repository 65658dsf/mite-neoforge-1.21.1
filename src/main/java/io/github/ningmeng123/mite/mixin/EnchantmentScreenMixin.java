package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain the modern book animation while displaying actual source point costs and native lapis requirements. @author NingMeng123 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {
    @Unique private static final ResourceLocation MITE_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/enchanting_table.png");
    @Unique private static final ResourceLocation MITE_ENABLED = ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot");
    @Unique private static final ResourceLocation MITE_HOVERED = ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot_highlighted");
    @Unique private static final ResourceLocation MITE_DISABLED = ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot_disabled");
    @Shadow @org.spongepowered.asm.mixin.Final private static ResourceLocation[] ENABLED_LEVEL_SPRITES;
    @Shadow @org.spongepowered.asm.mixin.Final private static ResourceLocation[] DISABLED_LEVEL_SPRITES;
    @Shadow private void renderBook(GuiGraphics graphics, int x, int y, float partialTick) { throw new AssertionError(); }
    protected EnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @Unique private boolean mite$source() { return ((R196EnchantmentMenuAccess)menu).mite$usesSourceEnchanting(); }

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void mite$drawPointCosts(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (!mite$source()) return;
        ci.cancel();
        int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
        graphics.blit(MITE_BACKGROUND, x, y, 0, 0, imageWidth, imageHeight);
        renderBook(graphics, x, y, partialTick);
        EnchantmentNames.getInstance().initSeed(menu.getEnchantmentSeed());
        for (int slot = 0; slot < 3; slot++) {
            int energy = menu.costs[slot], points = ((R196EnchantmentMenuAccess)menu).mite$experienceCost(slot);
            boolean conversion = menu.getSlot(0).getItem().is(Items.GOLDEN_APPLE);
            boolean affordable = energy > 0 && (conversion || menu.enchantClue[slot] >= 0) && minecraft.player != null
                    && (minecraft.player.getAbilities().instabuild || minecraft.player.experienceLevel >= energy && minecraft.player.totalExperience >= points
                            && R196EnchantmentTable.hasLapis(menu.getSlot(1).getItem(), slot));
            boolean hovered = mouseX >= x + 60 && mouseX < x + 168 && mouseY >= y + 14 + slot * 19 && mouseY < y + 33 + slot * 19;
            graphics.blitSprite(!affordable ? MITE_DISABLED : hovered ? MITE_HOVERED : MITE_ENABLED, x + 60, y + 14 + slot * 19, 108, 19);
            if (energy == 0) continue;
            graphics.blitSprite(affordable ? ENABLED_LEVEL_SPRITES[slot] : DISABLED_LEVEL_SPRITES[slot],
                    x + 61, y + 15 + slot * 19, 16, 16);
            Component label = Component.translatable("gui.mite.enchant.cost", points);
            int labelWidth = font.width(label), runeWidth = 86 - labelWidth;
            if (runeWidth > 5) graphics.drawWordWrap(font, EnchantmentNames.getInstance().getRandomName(font, runeWidth),
                    x + 80, y + 17 + slot * 19, runeWidth, affordable ? 0x68654a : 0x343225);
            graphics.drawString(font, label, x + 165 - labelWidth, y + 23 + slot * 19, affordable ? 0x80ff20 : 0x705840, false);
        }
    }

    @WrapMethod(method = "render")
    private void mite$pointTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, Operation<Void> original) {
        if (!mite$source()) { original.call(graphics, mouseX, mouseY, partialTick); return; }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (minecraft.player == null) return;
        for (int slot = 0; slot < 3; slot++) {
            if (menu.costs[slot] <= 0 || !isHovering(60, 14 + slot * 19, 108, 19, mouseX, mouseY)) continue;
            List<Component> tooltip = new ArrayList<>();
            // Offer clues remain internal for validation; the player only sees requirements and costs.
            if (menu.enchantClue[slot] < 0 && !menu.getSlot(0).getItem().is(Items.GOLDEN_APPLE))
                tooltip.add(Component.translatable("neoforge.container.enchant.limitedEnchantability").withStyle(ChatFormatting.RED));
            boolean creative = minecraft.player.getAbilities().instabuild;
            tooltip.add(Component.translatable("container.enchant.level.requirement", menu.costs[slot])
                    .withStyle(creative || minecraft.player.experienceLevel >= menu.costs[slot] ? ChatFormatting.GRAY : ChatFormatting.RED));
            int points = ((R196EnchantmentMenuAccess)menu).mite$experienceCost(slot);
            boolean affordable = minecraft.player.getAbilities().instabuild || minecraft.player.totalExperience >= points;
            tooltip.add(Component.translatable("gui.mite.enchant.available", minecraft.player.totalExperience).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.mite.enchant.points", points).withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED));
            tooltip.add((slot == 0 ? Component.translatable("container.enchant.lapis.one")
                    : Component.translatable("container.enchant.lapis.many", slot + 1))
                    .withStyle(creative || R196EnchantmentTable.hasLapis(menu.getSlot(1).getItem(), slot) ? ChatFormatting.GRAY : ChatFormatting.RED));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            break;
        }
    }
}
