package io.github.ningmeng123.mite.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.utility.UniqueMusicSavedData;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Nine source book identities support Enlightenment without inventing nine registry items. @author NingMeng123 */
public final class ReferencedBooks {
    private static final JsonArray BOOKS=load();
    private ReferencedBooks(){}
    private static JsonArray load(){try(var r=new InputStreamReader(Objects.requireNonNull(ReferencedBooks.class.getResourceAsStream("/mite/referenced-books.json")),StandardCharsets.UTF_8)){return JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("books");}catch(Exception e){throw new IllegalStateException(e);}}
    public static int index(ItemStack stack){
        if(!stack.is(Items.WRITTEN_BOOK))return 0;
        int index=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("mite_reference_index");
        return index>=1&&index<=9?index:0;
    }
    public static ItemStack book(int index){
        if(index<1||index>9)throw new IllegalArgumentException("Reference index must be 1..9");
        var row=BOOKS.get(index-1).getAsJsonObject();
        ItemStack result=new ItemStack(Items.WRITTEN_BOOK);
        var pages=new ArrayList<Filterable<Component>>();
        for(var page:row.getAsJsonArray("pages"))pages.add(Filterable.passThrough(Component.literal(page.getAsString())));
        result.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough(row.get("title").getAsString()),row.get("author").getAsString(),0,pages,true));
        CompoundTag data=new CompoundTag();data.putInt("mite_reference_index",index);result.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        return result;
    }
    public static void read(ServerPlayer player,ItemStack book){
        int index=index(book);if(index==0)return;
        var data=player.getPersistentData();int before=data.getInt("mite_books_read"),bit=1<<(index-1);
        if((before&bit)!=0)return;
        data.putInt("mite_books_read",before|bit);
        io.github.ningmeng123.mite.survival.PlayerExperience.addPoints(player,100);
        if(((before|bit)&511)==511)MiteAchievements.award(player,"enlightenment");
    }
    public static void release(ServerLevel level,ItemStack stack){int index=index(stack);if(index>0)UniqueMusicSavedData.get(level).release("reference_book_"+index);}
    public static ItemStack artifact(LootContext context){
        String table=context.getQueriedLootTableId().toString();
        float chance=switch(table){case "minecraft:chests/desert_pyramid"->.1F;case "minecraft:chests/jungle_temple"->.25F;case "minecraft:chests/stronghold_library"->.5F;default->0F;};
        if(chance==0F||context.getRandom().nextFloat()>=chance)return ItemStack.EMPTY;
        var level=context.getLevel();
        if(io.github.ningmeng123.mite.worldgen.R196StructureGeneration.displayDay(level.getServer().overworld().getDayTime())<40
                ||!WorldAchievementData.get(level).firsts().containsKey(MiteAchievements.catalog().get("bookcase").id().toString()))return ItemStack.EMPTY;
        var uniques=UniqueMusicSavedData.get(level);
        // A modern loot invocation represents one chest. Share the source one-book cap across its structure piece.
        var origin=context.getParamOrNull(LootContextParams.ORIGIN);String group=null;
        if(origin!=null){
            String structure=table.contains("desert")?"desert_pyramid":table.contains("jungle")?"jungle_pyramid":"stronghold";
            var registry=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            var type=registry.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(structure));
            if(type!=null){
                var start=level.structureManager().getStructureWithPieceAt(net.minecraft.core.BlockPos.containing(origin),type);
                if(start.isValid()){
                    var box=start.getBoundingBox();
                    // R196 caps each library piece, not every library in the entire stronghold together.
                    if(structure.equals("stronghold"))for(var piece:start.getPieces())
                        if(piece instanceof net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces.Library
                                &&piece.getBoundingBox().isInside(net.minecraft.core.BlockPos.containing(origin))){box=piece.getBoundingBox();break;}
                    group="reference_structure_"+level.dimension().location()+"_"+structure+"_"+box.minX()+"_"+box.minY()+"_"+box.minZ();
                    if(uniques.has(group))return ItemStack.EMPTY;
                }
            }
        }
        for(int attempt=0;attempt<10;attempt++){
            int index=context.getRandom().nextInt(9)+1;
            if(!uniques.mark("reference_book_"+index))continue;
            if(group!=null)uniques.mark(group);
            return book(index);
        }
        return ItemStack.EMPTY;
    }
}
