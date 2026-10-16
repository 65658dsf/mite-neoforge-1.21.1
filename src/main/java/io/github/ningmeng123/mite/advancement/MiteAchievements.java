package io.github.ningmeng123.mite.advancement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Source kp registration and wd/vq actions; previews and automation cannot earn player achievements. @author NingMeng123 */
public final class MiteAchievements {
    public record Definition(String name,String path,String parent,String secondParent,String icon,boolean challenge) {
        public ResourceLocation id(){return ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,"r196/"+path);}
    }
    private static final Map<String,Definition> CATALOG=load();
    private MiteAchievements(){}
    private static Map<String,Definition> load(){
        Map<String,Definition> result=new LinkedHashMap<>();
        try(var reader=new InputStreamReader(Objects.requireNonNull(MiteAchievements.class.getResourceAsStream("/mite/achievements.json")),StandardCharsets.UTF_8)){
            for(var row:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("achievements")){
                JsonObject a=row.getAsJsonObject();String name=a.get("name").getAsString();
                result.put(name,new Definition(name,a.get("id").getAsString(),nullable(a,"parent"),nullable(a,"second_parent"),a.get("icon").getAsString(),a.get("challenge").getAsBoolean()));
            }
        }catch(Exception e){throw new IllegalStateException("Cannot load R196 achievements",e);}
        return Collections.unmodifiableMap(result);
    }
    private static String nullable(JsonObject a,String key){return a.get(key).isJsonNull()?null:a.get(key).getAsString();}
    public static Map<String,Definition> catalog(){return CATALOG;}
    public static boolean award(Player player,String name){
        if(!(player instanceof ServerPlayer server)||!CATALOG.containsKey(name))return false;
        var holder=server.server.getAdvancements().get(CATALOG.get(name).id());
        return holder!=null && server.getAdvancements().award(holder,"earned");
    }
    public static void crafted(Player player,ItemStack stack){
        if(!(player instanceof ServerPlayer)||stack.isEmpty())return;
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var tool=MiteTools.definition(stack);
        var armor=MiteArmorContent.definition(stack.getItem());
        if(id.equals("mite:clay_oven")||id.equals("mite:large_clay_oven"))award(player,"buildOven");
        else if(id.endsWith("_crafting_table")&&id.startsWith("mite:"))award(player,
                id.equals("mite:flint_crafting_table")||id.equals("mite:obsidian_crafting_table")?"buildWorkBench":"betterTools");
        else if(stack.is(Items.TORCH))award(player,"buildTorches");
        else if(tool!=null&&tool.family()==ToolFamily.PICKAXE&&(tool.material().equals("copper")||tool.material().equals("silver")||tool.material().equals("gold")))award(player,"buildPickaxe");
        else if(stack.is(Items.FURNACE))award(player,"buildFurnace");
        else if(id.equals("mite:obsidian_furnace"))award(player,"obsidianFurnace");
        else if(id.equals("mite:netherrack_furnace"))award(player,"netherrackFurnace");
        else if(tool!=null&&(tool.family()==ToolFamily.HOE||tool.family()==ToolFamily.MATTOCK))award(player,"buildHoe");
        else if(stack.is(Items.CAKE))award(player,"bakeCake");
        else if(tool!=null&&stack.isCorrectToolForDrops(net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState())){
            award(player,"buildBetterPickaxe");
            if(tool.harvestLevel()>=io.github.ningmeng123.mite.mining.SourceMiningRequirements.minimumHarvestLevel(
                    io.github.ningmeng123.mite.registry.MiteMetalBlocks.STORAGE.get("mithril").get().defaultBlockState()))award(player,"crystalBreaker");
        }
        else if(id.equals("mite:flint_hatchet")||id.equals("mite:flint_knife"))award(player,"cuttingEdge");
        else if(stack.is(Items.WOODEN_SWORD))award(player,"buildClub");
        else if(tool!=null&&tool.family()==ToolFamily.AXE)award(player,"buildAxe");
        else if(stack.is(Items.ENCHANTING_TABLE)||id.equals("mite:emerald_enchanting_table"))award(player,"enchantments");
        else if(stack.is(Items.BOOKSHELF))award(player,"bookcase");
        else if(tool!=null&&tool.family()==ToolFamily.SHOVEL)award(player,"buildShovel");
        else if(tool!=null&&tool.family()==ToolFamily.SCYTHE)award(player,"buildScythe");
        else if(armor!=null&&armor.chainmail())award(player,"buildChainMail");
        else if(stack.getItem() instanceof net.minecraft.world.item.FishingRodItem)award(player,"fishingRod");
        else if(id.equals("mite:flour"))award(player,"flour");
        else if(id.equals("mite:salad")||id.startsWith("mite:")&&(id.contains("_stew")||id.contains("_soup"))||stack.is(Items.MUSHROOM_STEW))award(player,"fineDining");
    }
    public static void cooked(Player player,ItemStack stack){
        if(stack.is(Items.IRON_INGOT))award(player,"acquireIron");
        else if(stack.is(Items.COOKED_COD)||stack.is(Items.COOKED_SALMON)||id(stack,"mite:cooked_large_fish"))award(player,"cookFish");
        else if(stack.is(Items.BREAD))award(player,"makeBread");
        else if(id(stack,"mite:mithril_ingot"))award(player,"mithrilIngot");
        else if(id(stack,"mite:adamantium_ingot"))award(player,"adamantiumIngot");
    }
    public static boolean id(ItemStack stack,String id){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);}
}
