package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.rules.ExperienceRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Keeps the modern player's experience fields consistent with one integer total.
 *
 * <p>This first gameplay stage supports only nonnegative XP and caps progress at
 * level 200. Negative experience and R196's sentinel interval are not enabled.
 *
 * @author NingMeng123
 */
public final class PlayerExperience {
    public static final int CURRENT_VERSION = 1;
    public static final String VERSION_TAG = "mite_experience_version";

    private PlayerExperience() {
    }

    /**
     * Posts the point event and, when needed, one aggregate level event before
     * committing any XP fields or score. Cancellation leaves our state untouched.
     */
    public static void addPoints(Player player, int amount) {
        PlayerXpEvent.XpChange pointEvent = new PlayerXpEvent.XpChange(player, amount);
        if (NeoForge.EVENT_BUS.post(pointEvent).isCanceled()) {
            return;
        }

        int oldTotal = positiveTotal(player.totalExperience);
        int oldLevel = ExperienceRules.levelForTotalExperience(oldTotal);
        int proposedTotal = positiveTotal((long) oldTotal + pointEvent.getAmount());
        int proposedLevel = ExperienceRules.levelForTotalExperience(proposedTotal);
        int levelDelta = proposedLevel - oldLevel;
        if (levelDelta != 0) {
            PlayerXpEvent.LevelChange levelEvent = new PlayerXpEvent.LevelChange(player, levelDelta);
            if (NeoForge.EVENT_BUS.post(levelEvent).isCanceled()) {
                return;
            }
            if (levelEvent.getLevels() != levelDelta) {
                proposedTotal = totalAtLevelWithSameFraction(
                        proposedTotal, (long) oldLevel + levelEvent.getLevels());
            }
        }

        // Vanilla score follows the accepted signed point delta, including at a cap.
        player.increaseScore(pointEvent.getAmount());
        commit(player, proposedTotal);
    }

    /** Adds levels while carrying the old bar proportion with integer arithmetic. */
    public static void addLevels(Player player, int levels) {
        PlayerXpEvent.LevelChange event = new PlayerXpEvent.LevelChange(player, levels);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return;
        }
        int oldTotal = positiveTotal(player.totalExperience);
        int oldLevel = ExperienceRules.levelForTotalExperience(oldTotal);
        commit(player, totalAtLevelWithSameFraction(oldTotal, (long) oldLevel + event.getLevels()));
    }

    /** Sets a level without events, preserving the old bar proportion. */
    public static void setLevels(Player player, int level) {
        commit(player, totalAtLevelWithSameFraction(positiveTotal(player.totalExperience), level));
    }

    /** Sets points within the current level without promoting the player or posting events. */
    public static void setPoints(Player player, int points) {
        int level = ExperienceRules.levelForTotalExperience(positiveTotal(player.totalExperience));
        if (level == ExperienceRules.MAX_LEVEL) {
            commit(player, ExperienceRules.MAX_LEVEL_EXPERIENCE);
            return;
        }
        int boundedPoints = Math.max(0, Math.min(points, barWidth(level) - 1));
        commit(player, ExperienceRules.experienceRequiredForLevel(level) + boundedPoints);
    }

    /**
     * Runs once after vanilla NBT loading. Old worlds retain their level and bar
     * proportion; versioned worlds use the saved integer total as authority.
     */
    public static void normalizeLoaded(Player player, CompoundTag tag) {
        if (tag.getInt(VERSION_TAG) >= CURRENT_VERSION) {
            commit(player, tag.getInt("XpTotal"));
            return;
        }

        int level = tag.getInt("XpLevel");
        if (level < 0) {
            commit(player, 0);
        } else if (level >= ExperienceRules.MAX_LEVEL) {
            commit(player, ExperienceRules.MAX_LEVEL_EXPERIENCE);
        } else {
            float savedProgress = tag.getFloat("XpP");
            double progress = Float.isFinite(savedProgress)
                    ? Math.max(0.0, Math.min(1.0, (double) savedProgress)) : 0.0;
            int width = barWidth(level);
            // Migration quantizes down to a point and never changes the saved level.
            int points = Math.min(width - 1, (int) Math.floor(progress * width));
            commit(player, ExperienceRules.experienceRequiredForLevel(level) + points);
        }
    }

    /** Returns exact points, without reconstructing them from the rounded float bar. */
    public static int pointsWithinLevel(Player player) {
        int total = positiveTotal(player.totalExperience);
        return total - ExperienceRules.experienceRequiredForLevel(ExperienceRules.levelForTotalExperience(total));
    }

    private static int totalAtLevelWithSameFraction(int sourceTotal, long targetLevel) {
        if (targetLevel < 0) {
            return 0;
        }
        if (targetLevel >= ExperienceRules.MAX_LEVEL) {
            return ExperienceRules.MAX_LEVEL_EXPERIENCE;
        }
        int sourceLevel = ExperienceRules.levelForTotalExperience(sourceTotal);
        int sourcePoints = sourceTotal - ExperienceRules.experienceRequiredForLevel(sourceLevel);
        int target = (int) targetLevel;
        int targetPoints = (int) ((long) sourcePoints * barWidth(target) / barWidth(sourceLevel));
        return ExperienceRules.experienceRequiredForLevel(target) + targetPoints;
    }

    private static int positiveTotal(long total) {
        return (int) Math.max(0L, Math.min(ExperienceRules.MAX_LEVEL_EXPERIENCE, total));
    }

    private static int barWidth(int level) {
        return level >= ExperienceRules.MAX_LEVEL ? 1 : ExperienceRules.experienceNeededForNextLevel(level);
    }

    private static void commit(Player player, int total) {
        int boundedTotal = positiveTotal(total);
        int level = ExperienceRules.levelForTotalExperience(boundedTotal);
        int points = boundedTotal - ExperienceRules.experienceRequiredForLevel(level);
        player.totalExperience = boundedTotal;
        player.experienceLevel = level;
        player.experienceProgress = level == ExperienceRules.MAX_LEVEL ? 0.0F : (float) points / barWidth(level);
    }
}
