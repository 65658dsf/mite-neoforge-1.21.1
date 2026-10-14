package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.animal.Livestock032;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** rr/rz accept wheat; ry accepts carrot or the source brown mushroom block item. @author NingMeng123 */
@Mixin({Cow.class,Pig.class,Sheep.class})
public abstract class Livestock032FoodMixin {
    @Inject(method="isFood",at=@At("HEAD"),cancellable=true)
    private void mite$exactAnimalFood(ItemStack stack,CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(Livestock032.foodItem((Mob)(Object)this,stack)); }
}
