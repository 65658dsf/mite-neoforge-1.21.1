package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.rules.ExperienceRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Captures death once and applies its saved result before respawn synchronization. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlayerDeathExperienceEvents {
    private PlayerDeathExperienceEvents() {
    }

    /** Called only after ServerPlayer.die's non-cancelled path has finished. */
    public static void captureDeath(ServerPlayer player) {
        if (!player.hasData(MiteAttachments.RESPAWN_EXPERIENCE)) {
            int total = ExperienceRules.experienceAfterDeath(player.totalExperience);
            player.setData(MiteAttachments.RESPAWN_EXPERIENCE,
                    new DeathExperience(DeathExperience.CURRENT_VERSION, total));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getOriginal() instanceof ServerPlayer original)) {
            return;
        }

        if (event.isWasDeath()
                && !player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)
                && !original.isSpectator()) {
            int total = original.getExistingData(MiteAttachments.RESPAWN_EXPERIENCE)
                    .map(DeathExperience::total)
                    .orElseGet(() -> ExperienceRules.experienceAfterDeath(original.totalExperience));
            PlayerExperience.setTotal(player, total);
        }

        // NeoForge's normal-priority clone listener may copy serialized data on
        // End return. Clear afterward, including exempt respawns, to avoid replay.
        player.removeData(MiteAttachments.RESPAWN_EXPERIENCE);
        original.removeData(MiteAttachments.RESPAWN_EXPERIENCE);
    }
}
