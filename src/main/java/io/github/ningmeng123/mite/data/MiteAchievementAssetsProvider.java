package io.github.ningmeng123.mite.data;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.advancement.MiteAchievements;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
/** Modern progression tab generated from the 62 independently extracted source registrations. @author NingMeng123 */
public final class MiteAchievementAssetsProvider implements DataProvider {
    private final Path output;
    public MiteAchievementAssetsProvider(PackOutput output){this.output=output.getOutputFolder();}
    @Override public CompletableFuture<?> run(CachedOutput cache){
        var jobs=new ArrayList<CompletableFuture<?>>();
        for(var a:MiteAchievements.catalog().values()){
            JsonObject json=new JsonObject(),display=new JsonObject(),icon=new JsonObject();
            icon.addProperty("id",a.icon());display.add("icon",icon);
            JsonObject title=new JsonObject();title.addProperty("translate","advancement.mite."+a.path()+".title");display.add("title",title);
            JsonObject desc=new JsonObject();desc.addProperty("translate","advancement.mite."+a.path()+".description");display.add("description",desc);
            display.addProperty("frame",a.challenge()?"challenge":"task");display.addProperty("show_toast",true);
            display.addProperty("announce_to_chat",true);display.addProperty("hidden",false);
            if(a.parent()==null)display.addProperty("background","minecraft:textures/block/stone.png");
            else json.addProperty("parent","mite:r196/"+a.parent());
            json.add("display",display);
            JsonObject criteria=new JsonObject(),trigger=new JsonObject();trigger.addProperty("trigger","minecraft:impossible");criteria.add("earned",trigger);json.add("criteria",criteria);
            JsonArray requirements=new JsonArray(),row=new JsonArray();row.add("earned");requirements.add(row);json.add("requirements",requirements);
            jobs.add(DataProvider.saveStable(cache,json,output.resolve("data/mite/advancement/r196/"+a.path()+".json")));
        }
        return CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new));
    }
    @Override public String getName(){return "MITE R196 achievement tree";}
}
