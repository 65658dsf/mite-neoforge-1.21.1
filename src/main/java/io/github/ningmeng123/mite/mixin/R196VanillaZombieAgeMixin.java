package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** tw.g_ and tw.a(boolean) force source zombie and pig-zombie identities adult. @author NingMeng123 */
@Mixin(Zombie.class)
public abstract class R196VanillaZombieAgeMixin {
    @ModifyVariable(method = "setBaby", at = @At("HEAD"), argsOnly = true)
    private boolean mite$sourceAdultZombies(boolean baby) {
        var type = ((Zombie)(Object)this).getType();
        return type == EntityType.ZOMBIE || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.ZOMBIFIED_PIGLIN ? false : baby;
    }
    @ModifyExpressionValue(method = "finalizeSpawn", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/monster/Zombie$ZombieGroupData;isBaby:Z"))
    private boolean mite$adultSourcesNeverCreateChickenJockeys(boolean baby) { return mite$sourceAdultZombies(baby); }
}
