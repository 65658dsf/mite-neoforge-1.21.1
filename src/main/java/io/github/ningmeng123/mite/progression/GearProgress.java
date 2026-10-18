package io.github.ningmeng123.mite.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/** Immutable, versioned item identity and independently stored growth affixes. @author NingMeng123 */
public record GearProgress(UUID id, int level, long experienceMillis, Map<ResourceLocation, Integer> affixes, int version) {
    public static final UUID UNASSIGNED = new UUID(0L, 0L);
    public static final GearProgress EMPTY = new GearProgress(UNASSIGNED, 0, 0, Map.of(), 1);
    public static final Codec<GearProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("id", UNASSIGNED).forGetter(GearProgress::id),
            Codec.intRange(0, 25).optionalFieldOf("level", 0).forGetter(GearProgress::level),
            Codec.LONG.validate(value -> value >= 0 ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "Negative gear experience"))
                    .optionalFieldOf("experience_millis", 0L).forGetter(GearProgress::experienceMillis),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(1, 255))
                    .optionalFieldOf("affixes", Map.of()).forGetter(GearProgress::affixes),
            Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(GearProgress::version)
    ).apply(instance, GearProgress::new));

    public GearProgress {
        Objects.requireNonNull(id);
        if (level < 0 || level > 25 || experienceMillis < 0 || version != 1)
            throw new IllegalArgumentException("Invalid equipment growth state");
        affixes = Map.copyOf(affixes);
        if (affixes.values().stream().anyMatch(value -> value == null || value < 1 || value > 255))
            throw new IllegalArgumentException("Invalid growth affix rank");
    }
}
