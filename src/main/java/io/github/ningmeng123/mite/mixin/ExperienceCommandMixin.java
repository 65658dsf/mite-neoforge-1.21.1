package io.github.ningmeng123.mite.mixin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.ningmeng123.mite.command.PlayerStatusCommands;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves command meaning while avoiding float-to-point rounding. @author NingMeng123 */
@Mixin(ExperienceCommand.class)
public abstract class ExperienceCommandMixin {
    @ModifyArg(method = "register", at = @At(value = "INVOKE",
            target = "Lcom/mojang/brigadier/CommandDispatcher;register(Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;)Lcom/mojang/brigadier/tree/LiteralCommandNode;",
            ordinal = 1), index = 0)
    private static LiteralArgumentBuilder<CommandSourceStack> mite$allowPersonalExperienceQuery(
            LiteralArgumentBuilder<CommandSourceStack> alias) {
        return PlayerStatusCommands.experienceAlias(alias);
    }

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
