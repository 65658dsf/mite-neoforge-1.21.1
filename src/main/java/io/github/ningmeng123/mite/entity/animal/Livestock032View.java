package io.github.ningmeng123.mite.entity.animal;

/** Source wellness and thirst watcher values needed for client interaction prediction. @author NingMeng123 */
public interface Livestock032View {
    LivestockRules032.Needs mite$livestockNeeds();
    void mite$setLivestockNeeds(LivestockRules032.Needs needs);
    int mite$recoveryTicks();
    void mite$setRecoveryTicks(int ticks);
    net.minecraft.core.BlockPos mite$lastTrampled();
    void mite$setLastTrampled(net.minecraft.core.BlockPos pos);
}
