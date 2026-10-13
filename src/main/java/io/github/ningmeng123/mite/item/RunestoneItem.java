package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.block.RunestoneBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomModelData;

/** The selected symbol travels with the item and is applied on placement. @author NingMeng123 */
public final class RunestoneItem extends BlockItem {
    public RunestoneItem(RunestoneBlock block, Properties properties) { super(block, properties); }
    public static int rune(ItemStack stack) {
        Integer value = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(RunestoneBlock.RUNE);
        return value == null ? 0 : value;
    }
    public static ItemStack withRune(ItemStack stack, int rune) {
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(RunestoneBlock.RUNE, Math.floorMod(rune, 16)));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(Math.floorMod(rune, 16)));
        return stack;
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId()).append(" \"" + RunestoneBlock.NAMES[rune(stack)] + "\"");
    }
}
