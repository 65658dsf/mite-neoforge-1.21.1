package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.registry.MiteTags;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.NativeWeaponBalanceRules;
import io.github.ningmeng123.mite.rules.WoodenClubRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/** Preserve the vanilla ID while replacing its default components and sword behavior. @author NingMeng123 */
public final class WoodenClubBehavior {
    private static final ResourceKey<Enchantment> ATTACK_SPEED = R196Enchantments.key("attack_speed");

    private WoodenClubBehavior() {}

    public static void modifyComponents(ModifyDefaultComponentsEvent event) {
        event.modify(Items.WOODEN_SWORD, builder -> builder
                .set(DataComponents.MAX_DAMAGE, WoodenClubRules.MAX_DAMAGE)
                .set(DataComponents.TOOL, new Tool(List.of(
                        Tool.Rule.minesAndDrops(MiteTags.WOODEN_CLUB_EFFICIENT, WoodenClubRules.MINING_SPEED)),
                        1.0F, 0))
                .set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE,
                                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 2.0, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED,
                                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                                        NativeWeaponBalanceRules.attacksPerSecond(ToolFamily.CLUB, "wood") - 4D,
                                        AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build()));
    }

    public static boolean allowsEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.KNOCKBACK) || enchantment.is(Enchantments.LOOTING)
                || enchantment.is(Enchantments.UNBREAKING) || enchantment.is(Enchantments.MENDING)
                || enchantment.is(Enchantments.VANISHING_CURSE) || enchantment.is(ATTACK_SPEED);
    }

    public static boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        return EquipmentDurability.mineBlock(stack, level, state, pos, miner);
    }
}
