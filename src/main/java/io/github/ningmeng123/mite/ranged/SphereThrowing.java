package io.github.ningmeng123.mite.ranged;

import io.github.ningmeng123.mite.entity.GelatinousSphere;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.rules.R196WeaponRules;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** ItemGelatinousSphere consumption/exhaustion and uq launch. @author NingMeng123 */
public final class SphereThrowing {
    private SphereThrowing() {}
    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var usedItem = stack.getItem();
        if (MiteWeaponContent.sphereSubtype(stack) < 0) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            GelatinousSphere sphere = new GelatinousSphere(level, player, stack);
            sphere.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, 1.2F,
                    R196WeaponRules.inaccuracy(player.experienceLevel, player.isInWaterOrBubble() || player.isInLava()));
            if (!level.addFreshEntity(sphere)) return InteractionResultHolder.fail(stack);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1); player.causeFoodExhaustion(.25F);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
                    SoundSource.PLAYERS, .5F, .4F / (player.getRandom().nextFloat() * .4F + .8F));
            player.awardStat(Stats.ITEM_USED.get(usedItem));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
