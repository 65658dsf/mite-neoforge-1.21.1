package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.entity.animal.NativeAnimalSpecial032;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** sf interactions, nonplayer damage fraction and exact-class breeding. @author NingMeng123 */
@Mixin(Wolf.class) public abstract class NativeWolf032Mixin {
    @Inject(method="mobInteract",at=@At("HEAD"),cancellable=true)
    private void mite$sourceInteraction(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir) {
        var value=NativeAnimalSpecial032.wolfInteract((Wolf)(Object)this,player,hand); if(value!=null) cir.setReturnValue(value);
    }
    @ModifyVariable(method="hurt",at=@At("HEAD"),argsOnly=true)
    private float mite$sourceNonplayerDamage(float amount,DamageSource source,float originalAmount) {
        var wolf=(Wolf)(Object)this; var attacker=source.getDirectEntity();
        return (wolf.getType()==EntityType.WOLF || wolf instanceof io.github.ningmeng123.mite.entity.R196Wolf)
                && attacker!=null && !(attacker instanceof Player) && !(attacker instanceof AbstractArrow)?(amount+1)/2:amount;
    }
    @Inject(method="canMate",at=@At("HEAD"),cancellable=true)
    private void mite$sourceSameSpecies(Animal other,CallbackInfoReturnable<Boolean> cir) {
        var wolf=(Wolf)(Object)this;
        if(NativeAnimalSpecial032.wolf(wolf)) cir.setReturnValue(other!=wolf && other.getType()==wolf.getType() && other instanceof Wolf w && !w.isOrderedToSit() && wolf.isInLove() && w.isInLove());
    }
    @Inject(method="getBreedOffspring",at=@At("HEAD"),cancellable=true)
    private void mite$sourceSameSpeciesChild(ServerLevel level,AgeableMob mate,CallbackInfoReturnable<Wolf> cir) {
        var wolf=(Wolf)(Object)this; if(!NativeAnimalSpecial032.wolf(wolf)) return;
        Wolf child=(Wolf)wolf.getType().create(level);
        if(child!=null) { child.setVariant(wolf.getVariant()); if(wolf.getOwnerUUID()!=null) { child.setOwnerUUID(wolf.getOwnerUUID()); child.setTame(true,true); } }
        cir.setReturnValue(child);
    }
}
