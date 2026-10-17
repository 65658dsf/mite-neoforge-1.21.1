package io.github.ningmeng123.mite.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Read-only day and personal experience queries available to ordinary players. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlayerStatusCommands {
    private PlayerStatusCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("day").executes(context -> {
            CommandSourceStack source = context.getSource();
            long day = R196StructureGeneration.displayDay(source.getServer().overworld().getDayTime());
            source.sendSuccess(() -> Component.translatable("gui.mite.world_day", day), false);
            return (int) Math.min(Integer.MAX_VALUE, day);
        }));
    }

    /** Replaces vanilla's restricted redirect before registration, keeping its subcommands restricted. */
    public static LiteralArgumentBuilder<CommandSourceStack> experienceAlias(
            LiteralArgumentBuilder<CommandSourceStack> original) {
        var alias = Commands.literal(original.getLiteral()).executes(context -> experience(context.getSource()));
        var target = original.getRedirect();
        for (var child : target.getChildren()) {
            var guarded = child.createBuilder().requires(source -> original.getRequirement().test(source)
                    && target.canUse(source) && child.canUse(source));
            for (var grandchild : child.getChildren()) {
                guarded.then(grandchild);
            }
            alias.then(guarded);
        }
        return alias;
    }

    private static int experience(CommandSourceStack source) throws CommandSyntaxException {
        int total = source.getPlayerOrException().totalExperience;
        source.sendSuccess(() -> Component.translatable("commands.mite.xp", total), false);
        return total;
    }
}
