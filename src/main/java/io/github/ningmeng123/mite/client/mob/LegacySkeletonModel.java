package io.github.ningmeng123.mite.client.mob;

import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.AbstractSkeleton;

/** Source geometry uses the native animated skeleton pose, including material bows. @author NingMeng123 */
public final class LegacySkeletonModel<T extends AbstractSkeleton> extends SkeletonModel<T> {
    public LegacySkeletonModel(ModelPart root) { super(root); }
}
