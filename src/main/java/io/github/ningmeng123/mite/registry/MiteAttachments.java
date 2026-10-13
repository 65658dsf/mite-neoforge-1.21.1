package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.DeathExperience;
import io.github.ningmeng123.mite.survival.DeathOrbOwner;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.github.ningmeng123.mite.survival.CowFoodState;
import net.minecraft.world.entity.animal.Cow;
import com.mojang.serialization.Codec;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Persistent player snapshots and synchronized XP orb ownership. @author NingMeng123 */
public final class MiteAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mite.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DeathExperience>> RESPAWN_EXPERIENCE =
            ATTACHMENTS.register("respawn_experience", () -> AttachmentType.builder(
                    () -> new DeathExperience(DeathExperience.CURRENT_VERSION, 0))
                    .serialize(DeathExperience.CODEC)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DeathOrbOwner>> DEATH_ORB_OWNER =
            ATTACHMENTS.register("death_orb_owner", () -> AttachmentType.builder(
                    () -> new DeathOrbOwner(DeathOrbOwner.CURRENT_VERSION, new UUID(0L, 0L)))
                    .serialize(DeathOrbOwner.CODEC)
                    .sync(ByteBufCodecs.fromCodec(DeathOrbOwner.CODEC))
                    .build());

    private MiteAttachments() {
    }

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NutritionState>> NUTRITION =
            ATTACHMENTS.register("nutrition", () -> AttachmentType.builder(() -> NutritionState.INITIAL)
                    .serialize(NutritionState.CODEC).copyOnDeath().build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CowFoodState>> COW_FOOD =
            ATTACHMENTS.register("cow_food", () -> AttachmentType.builder(holder -> CowFoodState.initial(((Cow)holder).getRandom()))
                    .serialize(CowFoodState.CODEC).sync(ByteBufCodecs.fromCodec(CowFoodState.CODEC)).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> COW_MANURE_COUNTDOWN =
            ATTACHMENTS.register("cow_manure_countdown", () -> AttachmentType.builder(holder -> ((Cow)holder).getRandom().nextInt(24000))
                    .serialize(Codec.intRange(0,36000)).build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
