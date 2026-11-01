package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.animal.AnimalFleeNavigationAccess;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Candidate probes retain the active route's native recomputation target. @author NingMeng123 */
@Mixin(PathNavigation.class)
public interface AnimalFleeNavigationMixin extends AnimalFleeNavigationAccess {
    @Override @Accessor("targetPos") @Nullable BlockPos mite$fleeTarget();
    @Override @Accessor("targetPos") void mite$fleeTarget(@Nullable BlockPos target);
    @Override @Accessor("reachRange") int mite$fleeAccuracy();
    @Override @Accessor("reachRange") void mite$fleeAccuracy(int accuracy);
    @Override @Accessor("speedModifier") double mite$fleeSpeed();
    @Override @Accessor("speedModifier") void mite$fleeSpeed(double speed);
    @Override @Invoker("canUpdatePath") boolean mite$fleeCanUpdatePath();
    @Override @Invoker("getTempMobPos") Vec3 mite$fleeTempMobPos();
    @Override @Invoker("canMoveDirectly") boolean mite$fleeCanMoveDirectly(Vec3 from, Vec3 to);
}
