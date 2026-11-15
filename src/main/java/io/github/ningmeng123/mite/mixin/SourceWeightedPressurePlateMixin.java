package io.github.ningmeng123.mite.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** arq counts items within stacks, reaching full power at 64 gold or 640 iron items. @author NingMeng123 */
@Mixin(WeightedPressurePlateBlock.class)
public abstract class SourceWeightedPressurePlateMixin extends BasePressurePlateBlock {
    protected SourceWeightedPressurePlateMixin(Properties properties, BlockSetType type) { super(properties, type); }

    @Inject(method = "getSignalStrength", at = @At("HEAD"), cancellable = true)
    private void mite$weighDroppedItems(Level level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        Object plate = this;
        int capacity;
        if (plate == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE) capacity = 64;
        else if (plate == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE) capacity = 640;
        else return;

        int count = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, TOUCH_AABB.move(pos),
                EntitySelector.NO_SPECTATORS.and(entity -> !entity.isIgnoringBlockTriggers()))) {
            count += Math.min(capacity - count, item.getItem().getCount());
            if (count == capacity) break;
        }
        cir.setReturnValue(Mth.ceil((float) count / (float) capacity * 15F));
    }
}
