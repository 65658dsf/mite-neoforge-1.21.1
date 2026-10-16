package io.github.ningmeng123.mite.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.advancement.ReferencedBooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** ss artifacts do not expire; destruction releases their world signature, pickup does not. @author NingMeng123 */
@Mixin(ItemEntity.class)
public abstract class ReferencedBookItemMixin {
    @Inject(method="tick",at=@At("HEAD")) private void mite$artifact(CallbackInfo ci){
        ItemEntity item=(ItemEntity)(Object)this;
        if(ReferencedBooks.index(item.getItem())==0)return;
        ((SourceItemAgeAccess)item).mite$setAge(0);
        if(item.level() instanceof ServerLevel level && item.getY()<level.getMinBuildHeight()-64)ReferencedBooks.release(level,item.getItem());
    }
    @WrapOperation(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void mite$release(ItemStack stack,ItemEntity item,DamageSource source,Operation<Void> original){
        if(item.level() instanceof ServerLevel level)ReferencedBooks.release(level,stack);
        original.call(stack,item,source);
    }
}
