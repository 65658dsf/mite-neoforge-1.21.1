package io.github.ningmeng123.mite.survival;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.NutritionRules;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only diagnostics for independently checking food and long-term nutrient rules. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class FoodDebugCommands {
    private FoodDebugCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mite").requires(s->s.hasPermission(2))
                .then(Commands.literal("food")
                    .then(Commands.literal("status").then(Commands.argument("player",EntityArgument.player()).executes(ctx->{
                        ServerPlayer p=EntityArgument.getPlayer(ctx,"player");
                        ctx.getSource().sendSuccess(()->description(p),false); return 1;
                    })))
                    .then(Commands.literal("set")
                        .then(Commands.literal("saturation").then(Commands.argument("player",EntityArgument.player())
                            .then(Commands.argument("value",FloatArgumentType.floatArg(0F,20F)).executes(ctx->{
                                ServerPlayer p=EntityArgument.getPlayer(ctx,"player");
                                p.getFoodData().setSaturation(FloatArgumentType.getFloat(ctx,"value"));
                                PlayerFoodEvents.sync(p,true);
                                ctx.getSource().sendSuccess(()->description(p),false); return 1;
                            }))))
                        .then(Commands.literal("reserves").then(Commands.argument("player",EntityArgument.player())
                            .then(Commands.argument("satiation",IntegerArgumentType.integer(0,20))
                            .then(Commands.argument("nutrition",IntegerArgumentType.integer(0,20)).executes(ctx->{
                                ServerPlayer p=EntityArgument.getPlayer(ctx,"player");
                                p.getFoodData().setFoodLevel(IntegerArgumentType.getInteger(ctx,"satiation"));
                                ((DualFoodData)p.getFoodData()).mite$setNutrition(IntegerArgumentType.getInteger(ctx,"nutrition"));
                                PlayerFoodEvents.sync(p,true);
                                ctx.getSource().sendSuccess(()->description(p),false); return 1;
                            })))))
                        .then(Commands.literal("nutrients").then(Commands.argument("player",EntityArgument.player())
                            .then(Commands.argument("protein",IntegerArgumentType.integer(0,160000))
                            .then(Commands.argument("fat",IntegerArgumentType.integer(0,160000))
                            .then(Commands.argument("plants",IntegerArgumentType.integer(0,160000)).executes(ctx->{
                                ServerPlayer p=EntityArgument.getPlayer(ctx,"player");
                                NutritionState old=NutritionState.get(p);
                                NutritionState.set(p,new NutritionState(1,IntegerArgumentType.getInteger(ctx,"protein"),
                                        IntegerArgumentType.getInteger(ctx,"fat"),IntegerArgumentType.getInteger(ctx,"plants"),
                                        old.resistance(),old.resistanceLevel()));
                                PlayerFoodEvents.sync(p,true);
                                ctx.getSource().sendSuccess(()->description(p),false); return 1;
                            }))))))
                        .then(Commands.literal("resistance").then(Commands.argument("player",EntityArgument.player())
                            .then(Commands.argument("value",IntegerArgumentType.integer(0,192000)).executes(ctx->{
                                ServerPlayer p=EntityArgument.getPlayer(ctx,"player");
                                NutritionState old=NutritionState.get(p);
                                int value=IntegerArgumentType.getInteger(ctx,"value");
                                NutritionState.set(p,new NutritionState(1,old.protein(),old.fat(),old.plants(),value,
                                        NutritionRules.resistanceLevel(value,-1)));
                                PlayerFoodEvents.sync(p,true);
                                ctx.getSource().sendSuccess(()->description(p),false); return 1;
                            })))))));
    }
    private static Component description(ServerPlayer p) {
        NutritionState n=NutritionState.get(p);
        return Component.literal(p.getScoreboardName()+"：饱食 "+p.getFoodData().getFoodLevel()+"，营养 "
                +((DualFoodData)p.getFoodData()).mite$getNutrition()+"，原版饱和度 "+p.getFoodData().getSaturationLevel()
                +"；蛋白 "+n.protein()+"，脂肪 "+n.fat()
                +"，植物 "+n.plants()+"；糖抵抗 "+n.resistance()+"（阶段 "+n.resistanceLevel()+"）");
    }
}
