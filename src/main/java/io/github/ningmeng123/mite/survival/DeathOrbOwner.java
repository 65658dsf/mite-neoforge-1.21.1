package io.github.ningmeng123.mite.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** Persistent, synchronized ownership of a player's death XP orb. @author NingMeng123 */
public record DeathOrbOwner(int version, UUID owner) {
    public static final int CURRENT_VERSION = 1;
    public static final Codec<DeathOrbOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(CURRENT_VERSION, CURRENT_VERSION).fieldOf("version").forGetter(DeathOrbOwner::version),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(DeathOrbOwner::owner)
    ).apply(instance, DeathOrbOwner::new));

    public DeathOrbOwner {
        Objects.requireNonNull(owner, "owner");
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported death orb owner version: " + version);
        }
    }
}
