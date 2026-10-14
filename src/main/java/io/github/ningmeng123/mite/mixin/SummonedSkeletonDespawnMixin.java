package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.R196Skeleton;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The source gives summoned troops a saved 9600-tick grace before its ordinary despawn check. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class SummonedSkeletonDespawnMixin {
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void mite$summonedGrace(CallbackInfo ci) {
        if (!((Object)this instanceof AbstractSkeleton skeleton) || !skeleton.getPersistentData().contains(R196Skeleton.TROOP_COUNTER)) return;
        int counter = skeleton.getPersistentData().getInt(R196Skeleton.TROOP_COUNTER);
        if (counter < Integer.MAX_VALUE) ++counter;
        skeleton.getPersistentData().putInt(R196Skeleton.TROOP_COUNTER, counter);
        if (counter >= 200) {
            if (!R196Skeleton.tryDespawnTroop(skeleton) && skeleton.getPersistentData().getInt(R196Skeleton.TROOP_COUNTER) >= 200)
                skeleton.getPersistentData().putInt(R196Skeleton.TROOP_COUNTER, skeleton.getRandom().nextInt(100) - 50);
        }
        ci.cancel();
    }
}
