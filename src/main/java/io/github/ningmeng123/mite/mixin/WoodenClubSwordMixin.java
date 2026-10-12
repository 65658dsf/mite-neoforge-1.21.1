package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.WoodenClubBehavior;
import io.github.ningmeng123.mite.rules.WoodenClubRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the retained wooden sword ID becomes a club; all other swords delegate unchanged. @author NingMeng123 */
@Mixin(SwordItem.class)
public abstract class WoodenClubSwordMixin extends TieredItem {
    protected WoodenClubSwordMixin(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    @Inject(method = "canAttackBlock", at = @At("HEAD"), cancellable = true)
    private void mite$clubCanBreak(BlockState state, Level level, BlockPos pos, Player player,
            CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == Items.WOODEN_SWORD) cir.setReturnValue(true);
    }

    @Inject(method = "postHurtEnemy", at = @At("HEAD"), cancellable = true)
    private void mite$clubAttackWear(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfo ci) {
        if (stack.is(Items.WOODEN_SWORD)) {
            stack.hurtAndBreak(WoodenClubRules.ATTACK_WEAR, attacker, EquipmentSlot.MAINHAND);
            ci.cancel();
        }
    }

    @Inject(method = "canPerformAction", at = @At("HEAD"), cancellable = true)
    private void mite$noSwordAbilities(ItemStack stack, ItemAbility ability, CallbackInfoReturnable<Boolean> cir) {
        if (stack.is(Items.WOODEN_SWORD)) cir.setReturnValue(false);
    }

    @Override
    public int getEnchantmentValue() {
        return (Object) this == Items.WOODEN_SWORD ? WoodenClubRules.ENCHANTABILITY : super.getEnchantmentValue();
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return !stack.is(Items.WOODEN_SWORD) && super.isValidRepairItem(stack, repair);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        return stack.is(Items.WOODEN_SWORD) ? WoodenClubBehavior.mineBlock(stack, level, state, pos, miner)
                : super.mineBlock(stack, level, state, pos, miner);
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
        // Player.attack applies this to base damage before critical multiplication and enchantment addition.
        if ((Object) this == Items.WOODEN_SWORD && target instanceof AbstractSkeleton) return damage;
        return super.getAttackDamageBonus(target, damage, source);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return stack.is(Items.WOODEN_SWORD) ? WoodenClubBehavior.allowsEnchantment(enchantment)
                : super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return stack.is(Items.WOODEN_SWORD) ? WoodenClubBehavior.allowsEnchantment(enchantment)
                : super.isPrimaryItemFor(stack, enchantment);
    }
}
