package io.github.ningmeng123.mite.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.rules.ExperienceRules;

/** The saved total to apply at the next death respawn. @author NingMeng123 */
public record DeathExperience(int version, int total) {
    public static final int CURRENT_VERSION = 1;
    public static final Codec<DeathExperience> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(CURRENT_VERSION, CURRENT_VERSION).fieldOf("version").forGetter(DeathExperience::version),
            Codec.intRange(ExperienceRules.MIN_TOTAL_EXPERIENCE, ExperienceRules.MAX_LEVEL_EXPERIENCE)
                    .fieldOf("total").forGetter(DeathExperience::total)
    ).apply(instance, DeathExperience::new));
}
