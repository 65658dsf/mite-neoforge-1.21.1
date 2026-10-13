package io.github.ningmeng123.mite.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.ningmeng123.mite.rules.ExperienceRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.ExperienceCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves command meaning while avoiding float-to-point rounding. @author NingMeng123 */
@Mixin(ExperienceCommand.class)
public abstract class ExperienceCommandMixin {
    @Inject(method = "queryExperience", at = @At("HEAD"), cancellable = true)
    private static void mite$queryExactPoints(CommandSourceStack source, ServerPlayer player,
                                               @Coerce Object type, CallbackInfoReturnable<Integer> cir) {
        if (((Enum<?>) type).name().equals("POINTS")) {
            int points = PlayerExperience.pointsWithinLevel(player);
            source.sendSuccess(() -> Component.translatable("commands.experience.query.points",
                    player.getDisplayName(), points), false);
            cir.setReturnValue(points);
        }
    }

    @Inject(method = "setExperience", at = @At("HEAD"))
    private static void mite$rejectOutOfRangeLevel(CommandSourceStack source, Collection<ServerPlayer> players,
                                                    int amount, @Coerce Object type,
                                                    CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        if (((Enum<?>) type).name().equals("LEVELS") && amount > ExperienceRules.MAX_REPRESENTABLE_LEVEL) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.mite.experience.level_too_high",
                    ExperienceRules.MAX_REPRESENTABLE_LEVEL)).create();
        }
        if (((Enum<?>) type).name().equals("POINTS") && players.stream().anyMatch(player ->
                (long)ExperienceRules.experienceRequiredForLevel(player.experienceLevel) + amount
                        > ExperienceRules.MAX_TOTAL_EXPERIENCE)) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.mite.experience.points_overflow")).create();
        }
    }
}
