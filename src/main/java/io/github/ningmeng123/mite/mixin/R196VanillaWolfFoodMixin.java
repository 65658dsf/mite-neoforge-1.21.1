package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.entity.R196NpcNeeds;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Source sf eats any ItemMeat, including fish; native wolf-food tags alone omit fish. @author NingMeng123 */
@Mixin(Wolf.class)
public abstract class R196VanillaWolfFoodMixin {
    @Inject(method = "isFood", at = @At("HEAD"), cancellable = true)
    private void mite$sourceMeat(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (((Wolf)(Object)this).getType() == EntityType.WOLF) cir.setReturnValue(R196NpcNeeds.meat(stack));
    }
}
