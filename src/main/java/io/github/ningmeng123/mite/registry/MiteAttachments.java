package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.DeathExperience;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Persistent server-owned player data. @author NingMeng123 */
public final class MiteAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mite.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DeathExperience>> RESPAWN_EXPERIENCE =
            ATTACHMENTS.register("respawn_experience", () -> AttachmentType.builder(
                    () -> new DeathExperience(DeathExperience.CURRENT_VERSION, 0))
                    .serialize(DeathExperience.CODEC)
                    .build());

    private MiteAttachments() {
    }

    public static void register(IEventBus modBus) {
        // No copyOnDeath or sync: the clone handler consumes this server snapshot.
        ATTACHMENTS.register(modBus);
    }
}
