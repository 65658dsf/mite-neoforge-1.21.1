package io.github.ningmeng123.mite.item;

/** Source material repair identity for reinforced bows and fishing hooks. @author NingMeng123 */
public interface R196RepairMaterial {
    String repairMaterial();
    int repairComponents();
    default boolean repairFishingRod() { return false; }
}
