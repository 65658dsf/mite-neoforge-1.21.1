package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.rules.ExperienceRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes vanilla XP mutations through the integer model. @author NingMeng123 */
@Mixin(Player.class)
public abstract class PlayerExperienceMixin {
    @Shadow private int lastLevelUpTime;

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"), cancellable = true)
    private void mite$addExperiencePoints(int amount, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        int oldLevel = player.experienceLevel;
        PlayerExperience.addPoints(player, amount);
        if (player.experienceLevel > oldLevel) {
            // Vanilla's point loop plays at the first multiple of five it crosses.
            long milestone = (Math.floorDiv((long) oldLevel, 5L) + 1L) * 5L;
            if (milestone > 0 && milestone <= player.experienceLevel) {
                mite$playLevelUpSound(player, (int) milestone);
            }
        }
        ci.cancel();
    }

    @Inject(method = "giveExperienceLevels", at = @At("HEAD"), cancellable = true)
    private void mite$addExperienceLevels(int levels, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        int oldLevel = player.experienceLevel;
        PlayerExperience.addLevels(player, levels);
        if (player.experienceLevel > oldLevel && player.experienceLevel % 5 == 0) {
            mite$playLevelUpSound(player, player.experienceLevel);
        }
        ci.cancel();
    }

    @Inject(method = "getXpNeededForNextLevel", at = @At("HEAD"), cancellable = true)
    private void mite$useR196LevelWidth(CallbackInfoReturnable<Integer> cir) {
        int level = ((Player) (Object) this).experienceLevel;
        cir.setReturnValue(level >= ExperienceRules.MAX_LEVEL
                ? 1 : ExperienceRules.experienceNeededForNextLevel(Math.max(0, level)));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mite$loadExperience(CompoundTag tag, CallbackInfo ci) {
        PlayerExperience.normalizeLoaded((Player) (Object) this, tag);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mite$saveExperienceVersion(CompoundTag tag, CallbackInfo ci) {
        tag.putInt(PlayerExperience.VERSION_TAG, PlayerExperience.CURRENT_VERSION);
    }

    @Unique
    private void mite$playLevelUpSound(Player player, int soundingLevel) {
        if ((float) lastLevelUpTime < (float) player.tickCount - 100.0F) {
            float volume = soundingLevel > 30 ? 1.0F : (float) soundingLevel / 30.0F;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), volume * 0.75F, 1.0F);
            lastLevelUpTime = player.tickCount;
        }
    }
}
