package io.github.ningmeng123.mite.furnace;

/** Client-visible furnace values synchronized by native menu data slots. @author NingMeng123 */
public interface FurnaceThermalMenuAccess {
    int mite$getHeat();
    int mite$getMaxHeat();
    int mite$getRequiredHeat();
}
