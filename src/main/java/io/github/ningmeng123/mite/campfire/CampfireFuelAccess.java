package io.github.ningmeng123.mite.campfire;

/** Persistent reserve and the native cooking arrays of a campfire. @author NingMeng123 */
public interface CampfireFuelAccess {
    int mite$fuelPoints();
    int mite$fuelTicks();
    int mite$cookedMeat();
    void mite$setFuelPoints(int value);
    void mite$setFuelTicks(int value);
    void mite$setCookedMeat(int value);
    int[] mite$cookingProgress();
    int[] mite$cookingTime();
}
