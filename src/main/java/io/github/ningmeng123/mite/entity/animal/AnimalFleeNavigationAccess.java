package io.github.ningmeng123.mite.entity.animal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Escape probes preserve the active native navigation episode until a result is chosen. @author NingMeng123 */
public interface AnimalFleeNavigationAccess {
    boolean mite$fleeCanUpdatePath();
    Vec3 mite$fleeTempMobPos();
    boolean mite$fleeCanMoveDirectly(Vec3 from, Vec3 to);
    @Nullable BlockPos mite$fleeTarget();
    void mite$fleeTarget(@Nullable BlockPos target);
    int mite$fleeAccuracy();
    void mite$fleeAccuracy(int accuracy);
    double mite$fleeSpeed();
    void mite$fleeSpeed(double speed);
}
