package io.github.ningmeng123.mite.advancement;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.ningmeng123.mite.Mite;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
/** Public read-only world-first lookup; book provisioning is restricted to operators. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class AchievementCommands {
    private AchievementCommands(){}
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("miteachievements")
                .then(Commands.argument("achievement",StringArgumentType.word())
                        .suggests((c,b)->SharedSuggestionProvider.suggest(MiteAchievements.catalog().keySet(),b))
                        .executes(c->{
                            String name=StringArgumentType.getString(c,"achievement");var definition=MiteAchievements.catalog().get(name);
                            if(definition==null){c.getSource().sendFailure(Component.translatable("command.mite.achievement_unknown",name));return 0;}
                            var first=WorldAchievementData.get(c.getSource().getLevel()).firsts().get(definition.id().toString());
                            c.getSource().sendSuccess(()->first==null?Component.translatable("command.mite.achievement_unearned",name)
                                    :Component.translatable("command.mite.achievement_first",name,first.name(),first.day()),false);
                            return first==null?0:1;
                        })));
        event.getDispatcher().register(Commands.literal("mitebook").requires(s->s.hasPermission(2))
                .then(Commands.argument("index",IntegerArgumentType.integer(1,9)).executes(c->{
                    var player=c.getSource().getPlayerOrException();var book=ReferencedBooks.book(IntegerArgumentType.getInteger(c,"index"));
                    if(!player.getInventory().add(book))player.drop(book,false);return 1;
                })));
    }
}
