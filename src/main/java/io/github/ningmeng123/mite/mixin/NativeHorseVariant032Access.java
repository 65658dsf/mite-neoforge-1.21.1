package io.github.ningmeng123.mite.mixin;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Markings;
import net.minecraft.world.entity.animal.horse.Variant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Exact rs offspring appearance without reflective field mutation. @author NingMeng123 */
@Mixin(Horse.class) public interface NativeHorseVariant032Access {
    @Invoker("setVariantAndMarkings") void mite$setSourceVariantAndMarkings(Variant variant,Markings markings);
}
