package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.survival.FoodConsumption;
import io.github.ningmeng123.mite.survival.FoodVessels;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Matching food and crafting remainders are handled separately. @author NingMeng123 */
public final class MiteBowlItem extends Item {
    public enum Contents { WATER, MILK, FOOD, SOUP }
    private final Contents contents;

    public MiteBowlItem(Properties properties, Contents contents) {
        super(properties.craftRemainder(Items.BOWL));
        this.contents = contents;
    }

    public Contents contents() { return contents; }

    @Override public UseAnim getUseAnimation(ItemStack stack) {
        return contents == Contents.FOOD ? UseAnim.EAT : UseAnim.DRINK;
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && !FoodConsumption.canIngest(player, stack)) return stack;
        if (contents == Contents.MILK && !level.isClientSide) entity.removeAllEffects();
        return super.finishUsingItem(stack, level, entity);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        return FoodVessels.useOn(context);
    }

    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) { return new ItemStack(Items.BOWL); }
}
