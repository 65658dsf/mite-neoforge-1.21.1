package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.rules.FlintToolRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/** R196 flint hatchet with modern axe targets and attack cooldown, without axe right-click actions. @author NingMeng123 */
public final class FlintHatchetItem extends Item {
    public FlintHatchetItem(Properties properties) {
        super(properties.durability(FlintToolRules.MAX_DAMAGE)
                .component(DataComponents.TOOL, new Tool(List.of(
                        Tool.Rule.deniesDrops(BlockTags.INCORRECT_FOR_WOODEN_TOOL),
                        Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_AXE, FlintToolRules.MINING_SPEED)),
                        1.0F, 0))
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE,
                                new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 3.0, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED,
                                new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build()));
        // Item defaults retain PASS on right-click, zero enchantability and no material repair.
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return ability == ItemAbilities.AXE_DIG;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && stack.isDamageableItem()
                && !(miner instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            float hardness = MiningProgress.effectiveHardness(state, state.getDestroySpeed(level, pos));
            if (hardness > 0.0F && getDestroySpeed(stack, state) > 1.0F) {
                stack.hurtAndBreak(FlintToolRules.blockWear(hardness), miner, EquipmentSlot.MAINHAND);
            }
        }
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // hurtAndBreak applies on the server and preserves modern creative/unbreaking handling.
        stack.hurtAndBreak(FlintToolRules.attackWear(), attacker, EquipmentSlot.MAINHAND);
    }
}
