package io.github.ningmeng123.mite.phantom;

import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A carried core controlling one returning sword, without duplicating inventory items. @author NingMeng123 */
public final class PhantomBladeItem extends Item {
    private final String material;
    public PhantomBladeItem(String material, Properties properties) {
        super(properties.stacksTo(1));
        this.material = Objects.requireNonNull(PhantomBladeRules.profile(material)).material();
    }
    public String material() { return material; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        var profile = PhantomBladeRules.profile(material);
        lines.add(Component.translatable("item.mite.phantom_blade.activation").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.mite.phantom_blade.stats", number(profile.damage()), number(profile.intervalTicks()/20D))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.mite.phantom_blade.recharge").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.mite.phantom_blade.selection").withStyle(ChatFormatting.DARK_GRAY));
    }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
}
