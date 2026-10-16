package io.github.ningmeng123.mite.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.curse.WitchCurseData;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Witch;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator diagnostics use a real nearby witch, preserving the ordinary lifting path. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class CurseCommands {
    private CurseCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var typeArgument = Commands.argument("type", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(R196Curse.values())
                        .map(type -> type.name().toLowerCase(Locale.ROOT)), builder))
                .executes(context -> apply(context.getSource(), EntityArgument.getPlayer(context, "player"),
                        StringArgumentType.getString(context, "type"), 0))
                .then(Commands.argument("delay", IntegerArgumentType.integer(0)).executes(context ->
                        apply(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                StringArgumentType.getString(context, "type"), IntegerArgumentType.getInteger(context, "delay"))));
        var curse = Commands.literal("curse")
                .then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> status(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("clear").then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                            boolean changed = PlayerCurses.cure(player);
                            context.getSource().sendSuccess(() -> Component.translatable("commands.mite.curse.cleared", player.getDisplayName()), true);
                            return changed ? 1 : 0;
                        })))
                .then(Commands.literal("apply").then(Commands.argument("player", EntityArgument.player()).then(typeArgument)));
        event.getDispatcher().register(Commands.literal("mite").requires(source -> source.hasPermission(2)).then(curse));
    }

    private static int apply(CommandSourceStack source, ServerPlayer player, String typeName, int delay) throws CommandSyntaxException {
        R196Curse type;
        try { type = R196Curse.valueOf(typeName.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw error("commands.mite.curse.invalid_type"); }
        Witch witch = player.serverLevel().getEntitiesOfClass(Witch.class, player.getBoundingBox().inflate(64), Witch::isAlive)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (witch == null) throw error("commands.mite.curse.no_witch");
        if (!PlayerCurses.schedule(player, witch, type, delay)) throw error("commands.mite.curse.already_cursed");
        PlayerCurses.reconcile(player);
        source.sendSuccess(() -> Component.translatable("commands.mite.curse.applied", player.getDisplayName(), type.title(), delay), true);
        return 1;
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        var entry = WitchCurseData.get(player.serverLevel()).entry(player.getUUID());
        source.sendSuccess(() -> entry == null ? Component.translatable("commands.mite.curse.none", player.getDisplayName())
                : Component.translatable("commands.mite.curse.status", player.getDisplayName(), entry.type().title(),
                        entry.realized(), entry.known(), Math.max(0L, entry.realizationTime() - player.server.overworld().getGameTime()),
                        entry.witch().toString()), false);
        return entry == null ? 0 : entry.type().id();
    }

    private static CommandSyntaxException error(String key) {
        return new SimpleCommandExceptionType(Component.translatable(key)).create();
    }
}
