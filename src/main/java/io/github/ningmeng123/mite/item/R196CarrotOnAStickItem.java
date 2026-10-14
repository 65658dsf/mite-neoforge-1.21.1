package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import net.minecraft.stats.Stats;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** ws preserves the hook when a pig consumes the carrot; every source carrot has 25 uses. @author NingMeng123 */
public final class R196CarrotOnAStickItem extends FoodOnAStickItem<Pig> {
    private final Material material;
    public R196CarrotOnAStickItem(Material material, Properties properties) {
        super(properties.durability(25), EntityType.PIG, 7); this.material = material;
    }
    public Material material() { return material; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return useSource(level, player, hand, material);
    }
    public static InteractionResultHolder<ItemStack> useSource(Level level, Player player, InteractionHand hand, Material material) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (player.isPassenger() && player.getVehicle() instanceof Pig pig && stack.getMaxDamage() - stack.getDamageValue() >= 7
                && pig.boost()) {
            ItemStack result = stack.hurtAndConvertOnBreak(7, MiteRangedItems.rod(material), player, LivingEntity.getSlotForHand(hand));
            if (result.getItem() == MiteRangedItems.rod(material)) result.set(DataComponents.MAX_DAMAGE, material.rodDurability());
            return InteractionResultHolder.success(result);
        }
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        return InteractionResultHolder.pass(stack);
    }
}
