package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.entity.animal.NativeAnimalSpecial032;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Donkey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** rs.a(nk) verified receiver behavior and modern Horse/Donkey type split. @author NingMeng123 */
@Mixin({Horse.class,Donkey.class}) public abstract class NativeHorseBreeding032Mixin {
    @Inject(method="getBreedOffspring",at=@At("HEAD"),cancellable=true)
    private void mite$actualSourceInheritance(ServerLevel level,AgeableMob mate,CallbackInfoReturnable<AgeableMob> cir) { cir.setReturnValue(NativeAnimalSpecial032.horseOffspring((net.minecraft.world.entity.animal.horse.AbstractHorse)(Object)this,level,mate)); }
}
