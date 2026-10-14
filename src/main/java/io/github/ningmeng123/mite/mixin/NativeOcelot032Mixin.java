package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.entity.animal.NativeAnimalSpecial032;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Convert old rx tame success into the corresponding modern Cat entity. @author NingMeng123 */
@Mixin(Ocelot.class) public abstract class NativeOcelot032Mixin {
    @Inject(method="mobInteract",at=@At("HEAD"),cancellable=true)
    private void mite$sourceTaming(Player p,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir) { var value=NativeAnimalSpecial032.catInteract((Ocelot)(Object)this,p,hand); if(value!=null) cir.setReturnValue(value); }
    @Inject(method="isFood",at=@At("HEAD"),cancellable=true)
    private void mite$sourceFish(ItemStack item,CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(NativeAnimalSpecial032.rawFish(item)); }
    @Inject(method="reassessTrustingGoals",at=@At("HEAD"),cancellable=true)
    private void mite$avoidDuplicateNativeGoals(CallbackInfo ci) { if(NativeAnimalSpecial032.installed((Ocelot)(Object)this)) ci.cancel(); }
    @Inject(method="finalizeSpawn",at=@At("TAIL"))
    private void mite$sourceLitter(net.minecraft.world.level.ServerLevelAccessor world,net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType reason,net.minecraft.world.entity.SpawnGroupData data,
            CallbackInfoReturnable<net.minecraft.world.entity.SpawnGroupData> cir) {
        NativeAnimalSpecial032.ocelotSpawn((Ocelot)(Object)this,world);
    }
}
