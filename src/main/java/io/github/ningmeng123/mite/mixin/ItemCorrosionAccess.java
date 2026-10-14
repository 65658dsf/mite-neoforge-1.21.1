package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The source dropped-item health is independent of its stack count and remaining equipment durability. @author NingMeng123 */
@Mixin(ItemEntity.class)
public interface ItemCorrosionAccess {
    @Accessor("health") int mite$chemicalHealth();
    @Accessor("health") void mite$chemicalHealth(int value);
    @Accessor("pickupDelay") int mite$pickupDelay();
}
