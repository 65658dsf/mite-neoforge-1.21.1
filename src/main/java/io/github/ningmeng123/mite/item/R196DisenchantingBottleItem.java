package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.utility.PlayerDisenchantingEvent;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** ItemBottleOfDisenchanting drinks for 32 ticks; it is not a thrown potion. @author NingMeng123 */
public final class R196DisenchantingBottleItem extends Item {
    public R196DisenchantingBottleItem() { super(new Properties().stacksTo(1)); }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 32; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    public static boolean canDrink(LivingEntity entity) {
        // uf.canEatAndDrink applies even to this bottle; only the separate
        // cannot_drink player curse is exempted by the source.
        return !entity.hasEffect(MobEffects.CONFUSION) && (!(entity instanceof Player player) || !player.isEyeInFluid(FluidTags.WATER));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!canDrink(player)) return InteractionResultHolder.fail(player.getItemInHand(hand));
        return ItemUtils.startUsingInstantly(level, player, hand);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!canDrink(entity)) return stack;
        if (!level.isClientSide) {
            entity.removeAllEffects();
            if (entity instanceof ServerPlayer player) {
                CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
                NeoForge.EVENT_BUS.post(new PlayerDisenchantingEvent(player));
            }
        }
        if (entity instanceof Player player) {
            player.awardStat(Stats.ITEM_USED.get(this));
            entity.gameEvent(GameEvent.DRINK);
            if (player.hasInfiniteMaterials()) return stack;
            return ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE));
        }
        stack.shrink(1);
        return stack.isEmpty() ? new ItemStack(Items.GLASS_BOTTLE) : stack;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        super.appendHoverText(stack, context, tooltip, flags);
        tooltip.add(Component.translatable("item.mite.disenchanting.help").withStyle(ChatFormatting.GRAY));
    }
}
