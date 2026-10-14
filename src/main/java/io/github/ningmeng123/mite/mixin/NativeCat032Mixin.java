package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.entity.animal.NativeAnimalSpecial032;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Modern Cat is the tame half of old rx; preserve modern variants/armor UI interactions. @author NingMeng123 */
@Mixin(Cat.class) public abstract class NativeCat032Mixin {
    @Inject(method="mobInteract",at=@At("HEAD"),cancellable=true)
    private void mite$sourceFishInteraction(Player p,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir) { var value=NativeAnimalSpecial032.catInteract((Cat)(Object)this,p,hand); if(value!=null) cir.setReturnValue(value); }
    @Inject(method="isFood",at=@At("HEAD"),cancellable=true)
    private void mite$sourceFish(ItemStack item,CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(NativeAnimalSpecial032.rawFish(item)); }
    @Inject(method="reassessTameGoals",at=@At("HEAD"),cancellable=true)
    private void mite$avoidDuplicateNativeGoals(CallbackInfo ci) { if(NativeAnimalSpecial032.installed((Cat)(Object)this)) ci.cancel(); }
    @Inject(method="getBreedOffspring",at=@At("HEAD"),cancellable=true)
    private void mite$sourceMotherAppearance(net.minecraft.server.level.ServerLevel level,net.minecraft.world.entity.AgeableMob mate,CallbackInfoReturnable<Cat> cir) {
        Cat mother=(Cat)(Object)this,child=net.minecraft.world.entity.EntityType.CAT.create(level);
        if(child!=null && mother.isTame()) { child.setVariant(mother.getVariant());child.setOwnerUUID(mother.getOwnerUUID());child.setTame(true,true); }
        cir.setReturnValue(child);
    }
}
