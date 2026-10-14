package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.item.R196RecordItem;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Release only after every expiry listener has made its final lifetime decision. @author NingMeng123 */
@Mixin(ItemEntity.class)
public abstract class UniqueRecordLifecycleMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;onItemExpire(Lnet/minecraft/world/entity/item/ItemEntity;)I"))
    private int mite$releaseExpiredUnique(int extraLife) {
        ItemEntity entity = (ItemEntity)(Object)this;
        if (extraLife <= 0 && entity.getItem().getItem() instanceof R196RecordItem record) record.release(entity);
        return extraLife;
    }
}
