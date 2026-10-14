package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** tr.java:250: ordinary skeleton ammunition; modern variants retain their own arrows. @author NingMeng123 */
@Mixin(AbstractSkeleton.class)
public abstract class SkeletonMaterialArrowMixin {
    @Inject(method = "getArrow", at = @At("HEAD"), cancellable = true)
    private void mite$sourceAmmunition(ItemStack ammunition, float power, ItemStack weapon,
            CallbackInfoReturnable<AbstractArrow> cir) {
        AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
        if (skeleton.getType() == EntityType.SKELETON)
            cir.setReturnValue(ProjectileUtil.getMobArrow(skeleton,
                    new ItemStack(MiteRangedItems.arrow(Material.RUSTED_IRON)), power, weapon));
    }
}
