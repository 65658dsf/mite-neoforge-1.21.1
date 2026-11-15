package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.ningmeng123.mite.client.L2AttributePanel;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Add the effective melee range row to the optional L2 attribute list. @author NingMeng123 */
@Pseudo
@Mixin(targets = "dev.xkmc.l2tabs.init.data.AttrDispEntry", remap = false)
public abstract class L2AttributeEntriesMixin {
    @ModifyReturnValue(method = "get(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;", at = @At("RETURN"))
    private static List<?> mite$includeEffectiveMeleeRange(List<?> original, LivingEntity entity) {
        return L2AttributePanel.entries(entity, original);
    }
}
