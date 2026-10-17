package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.R196WeaponRules;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Wooden bow reinforcement changes source shot speed, repair material and quality. @author NingMeng123 */
public final class R196ReinforcedBowItem extends BowItem implements EquipmentDurability.QualityProfiled, R196RepairMaterial {
    private final String material;
    public R196ReinforcedBowItem(String material, Properties properties) {
        super(properties.durability(switch (material) {
            case "vibranium" -> 512;
            case "mithril" -> 128;
            default -> 64;
        }));
        this.material = material;
    }
    public String material() { return material; }
    @Override public Quality maximumQuality() { return material.equals("mithril") || material.equals("vibranium") ? Quality.LEGENDARY : Quality.MASTERWORK; }
    @Override public String repairMaterial() { return material; }
    @Override public int repairComponents() { return 1; }
    @Override public int getEnchantmentValue() { return switch (material) {
        case "vibranium" -> 120;
        case "mithril" -> 100;
        default -> 40;
    }; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.mite.bow.velocity", Math.round((R196WeaponRules.velocityMultiplier(material) - 1F) * 100F)));
    }
}
