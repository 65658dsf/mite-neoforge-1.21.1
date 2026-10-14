package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.rules.UtilityRules;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Coins store the exact source experience; server manufacturing owns atomic debit/redeem. @author NingMeng123 */
public final class R196CoinItem extends Item {
    private final String material;
    public R196CoinItem(String material) { super(new Properties().stacksTo(64)); this.material = material; }
    public String material() { return material; }
    public int experienceValue() { return UtilityRules.coinExperience(material); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        super.appendHoverText(stack, context, tooltip, flags);
        tooltip.add(Component.translatable("item.mite.coin.experience", experienceValue()).withStyle(ChatFormatting.GRAY));
    }
}
