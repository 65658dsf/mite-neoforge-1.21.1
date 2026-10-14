package io.github.ningmeng123.mite.entity;

import java.util.Locale;
import net.minecraft.world.entity.MobCategory;

/** The 29 living identities actually registered by R196, including its category exceptions. @author NingMeng123 */
public enum MobKind {
    GHOUL(512, Family.UNDEAD), WIGHT(513, Family.UNDEAD), INVISIBLE_STALKER(514, Family.UNDEAD),
    DEMON_SPIDER(515, Family.SPIDER), HELLHOUND(516, Family.WOLF), DIRE_WOLF(517, Family.WOLF),
    WOOD_SPIDER(518, Family.SPIDER), INFERNAL_CREEPER(519, Family.CREEPER), SHADOW(520, Family.UNDEAD),
    FIRE_ELEMENTAL(521, Family.FIRE), BLACK_WIDOW_SPIDER(522, Family.SPIDER), REVENANT(523, Family.ZOMBIE),
    EARTH_ELEMENTAL(524, Family.EARTH), JELLY(525, Family.CUBE), BLOB(526, Family.CUBE),
    OOZE(527, Family.CUBE), PUDDING(528, Family.CUBE), VAMPIRE_BAT(529, Family.BAT),
    GIANT_VAMPIRE_BAT(530, Family.BAT), LONGDEAD(531, Family.SKELETON),
    LONGDEAD_GUARDIAN(532, Family.SKELETON), NIGHTWING(533, Family.BAT),
    NETHERSPAWN(534, Family.SILVERFISH), COPPERSPINE(535, Family.SILVERFISH),
    HOARY_SILVERFISH(536, Family.SILVERFISH), CLAY_GOLEM(537, Family.EARTH),
    BONE_LORD(538, Family.SKELETON), ANCIENT_BONE_LORD(539, Family.SKELETON),
    PHASE_SPIDER(540, Family.SPIDER);

    public enum Family { UNDEAD, SPIDER, WOLF, CREEPER, FIRE, ZOMBIE, EARTH, CUBE, BAT, SKELETON, SILVERFISH }
    private final int sourceId;
    private final Family family;
    MobKind(int sourceId, Family family) { this.sourceId = sourceId; this.family = family; }
    public int sourceId() { return sourceId; }
    public Family family() { return family; }
    public String path() { return name().toLowerCase(Locale.ROOT); }
    public String sourceClass() {
        StringBuilder result = new StringBuilder("Entity");
        for (String part : path().split("_")) result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        return result.toString();
    }
    public MobCategory category() {
        return this == DIRE_WOLF ? MobCategory.CREATURE : family == Family.BAT ? MobCategory.AMBIENT : MobCategory.MONSTER;
    }
}
