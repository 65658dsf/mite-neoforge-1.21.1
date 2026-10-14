package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteRangedItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All hook variants can attract and steer pigs using the native pig AI. @author NingMeng123 */
@Mixin(Pig.class)
public abstract class RangedPigControlMixin extends Animal {
    protected RangedPigControlMixin(EntityType<? extends Animal> type, Level level) { super(type, level); }
    @Redirect(method = "getControllingPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHolding(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mite$allHookMaterials(Player player, Item item) {
        return item == Items.CARROT_ON_A_STICK ? player.isHolding(stack -> MiteRangedItems.carrotRodMaterial(stack) != null) : player.isHolding(item);
    }
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void mite$customCarrotTemptation(CallbackInfo ci) {
        goalSelector.addGoal(4, new TemptGoal((Pig)(Object)this, 1.2D,
                stack -> MiteRangedItems.carrotRodMaterial(stack) != null && !stack.is(Items.CARROT_ON_A_STICK), false));
    }
}
