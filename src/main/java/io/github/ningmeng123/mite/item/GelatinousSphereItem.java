package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.ranged.SphereThrowing;
import io.github.ningmeng123.mite.rules.R196WeaponRules;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Native instant-use projectile; source metadata is represented by distinct item IDs. @author NingMeng123 */
public final class GelatinousSphereItem extends Item {
    private final int subtype;
    public GelatinousSphereItem(int subtype, Properties properties) { super(properties.stacksTo(16)); this.subtype = subtype; }
    public int subtype() { return subtype; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return SphereThrowing.use(level, player, hand);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mite.sphere.damage", R196WeaponRules.sphereDamage(subtype)));
    }
}
