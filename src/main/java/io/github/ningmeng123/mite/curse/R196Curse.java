package io.github.ningmeng123.mite.curse;

import net.minecraft.network.chat.Component;

/** The sixteen enabled entries in R196's Curse.cursesList. @author NingMeng123 */
public enum R196Curse {
    EQUIPMENT_DECAY("equipmentDecay"),
    CANNOT_HOLD_BREATH("cantHoldBreath"),
    CANNOT_RUN("cantRun"),
    CANNOT_EAT_ANIMALS("cantEatAnimals"),
    CANNOT_EAT_PLANTS("cantEatPlants"),
    CANNOT_DRINK("cantDrink"),
    ENDERMEN_ENEMY("endermenEnemy"),
    CLUMSINESS("clumsiness"),
    ENTANGLEMENT("entanglement"),
    CANNOT_WEAR_ARMOR("cantWearArmor"),
    CANNOT_OPEN_CHESTS("cantOpenChests"),
    CANNOT_SLEEP("cantSleep"),
    FEAR_OF_SPIDERS("fearOfSpiders"),
    FEAR_OF_WOLVES("fearOfWolves"),
    FEAR_OF_CREEPERS("fearOfCreepers"),
    FEAR_OF_UNDEAD("fearOfUndead");

    private final String key;
    R196Curse(String key) { this.key = key; }
    public int id() { return ordinal() + 1; }
    public String key() { return key; }
    public Component title() { return Component.translatable("curse.mite." + key + ".name"); }
    public Component description() { return Component.translatable("curse.mite." + key + ".desc"); }
    public static R196Curse fromId(int id) { return id >= 1 && id <= values().length ? values()[id - 1] : null; }
}
