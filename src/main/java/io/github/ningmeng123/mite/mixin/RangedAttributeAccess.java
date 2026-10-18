package io.github.ningmeng123.mite.mixin;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Explicit optional range expansion for extension bosses. @author NingMeng123 */
@Mixin(RangedAttribute.class)
public interface RangedAttributeAccess {
    @Mutable @Accessor("maxValue") void mite$setMaxValue(double value);
}
