package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Preserve source break reasons through native loot tables, modifiers, events and cancellation. @author NingMeng123 */
public final class CropHarvest {
    public enum Reason { PLAYER,SELF,DROUGHT,SNOW,ILLEGAL,TRAMPLE,FLOOD,EXPLOSION }
    private record Context(Reason reason,ServerLevel level,BlockPos pos) {}
    private static final ThreadLocal<Context> CONTEXT=new ThreadLocal<>();
    private CropHarvest() {}
    public static void withReason(Reason reason,ServerLevel level,BlockPos pos,Runnable operation) {
        Context previous=CONTEXT.get();CONTEXT.set(new Context(reason,level,pos.immutable()));
        try { operation.run(); } finally { if(previous==null)CONTEXT.remove();else CONTEXT.set(previous); }
    }
    public static boolean isNormalHarvest(ServerLevel level,BlockPos pos) {
        var context=CONTEXT.get();return context==null||context.level()!=level||!context.pos().equals(pos)||context.reason()==Reason.PLAYER;
    }
    public static Reason reason(LootParams.Builder params) {
        var context=CONTEXT.get();var origin=params.getOptionalParameter(LootContextParams.ORIGIN);
        if(context!=null&&context.level()==params.getLevel()&&origin!=null&&context.pos().equals(BlockPos.containing(origin)))return context.reason();
        return params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Player?Reason.PLAYER:Reason.SELF;
    }
    public static Item produce(BlockState state) {
        if(state.is(Blocks.WHEAT))return Items.WHEAT;
        if(state.is(Blocks.CARROTS))return Items.CARROT;
        if(state.is(Blocks.POTATOES))return Items.POTATO;
        if(state.is(Blocks.BEETROOTS))return Items.BEETROOT;
        return io.github.ningmeng123.mite.registry.MiteFoods.get("onion");
    }
    public static boolean fruit(BlockState state) { return state.is(Blocks.MELON)||state.is(Blocks.PUMPKIN); }
    public static List<ItemStack> drops(BlockState state,LootParams.Builder params,List<ItemStack> raw) {
        if(!CropLifecycle.managed(state))return raw;
        if(CubeCropBlight.dead(state)||reason(params)==Reason.EXPLOSION||params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS)!=null)return List.of();
        CropBlock crop=(CropBlock)state.getBlock();int age=crop.getAge(state);Reason reason=reason(params);
        // apt's early override also applies to flooding, support loss and non-cow trampling.
        if(state.is(Blocks.POTATOES)&&(CubeCropBlight.blighted(state)||(age>0&&!crop.isMaxAge(state))))return raw;
        if(CubeCropBlight.blighted(state))return List.of();
        if(reason==Reason.TRAMPLE||reason==Reason.ILLEGAL||reason==Reason.FLOOD)return List.of();
        if(age==0)return raw;
        if(!crop.isMaxAge(state)||reason==Reason.SELF)return List.of();
        var tool=params.getOptionalParameter(LootContextParams.TOOL);
        float bonus=tool==null?0F:AgriculturalEnchantments.harvesting(tool,params.getLevel(),state);
        if(bonus<=0F)return raw;
        List<ItemStack> result=new ArrayList<>();
        for(var stack:raw) {
            var copy=stack.copy();int extra=0;
            if(stack.is(produce(state)))for(int i=0;i<stack.getCount();i++)if(params.getLevel().random.nextFloat()<bonus)extra++;
            int remaining=copy.getCount()+extra;
            while(remaining>0) {int count=Math.min(remaining,copy.getMaxStackSize());result.add(copy.copyWithCount(count));remaining-=count;}
        }
        return result;
    }
}
