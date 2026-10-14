package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.mob.LegacyArachnidModel;
import io.github.ningmeng123.mite.client.mob.LegacyBatModel;
import io.github.ningmeng123.mite.client.mob.LegacySkeletonModel;
import io.github.ningmeng123.mite.client.mob.LegacyStalkerModel;
import io.github.ningmeng123.mite.client.mob.R196MobRenderers;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.R196Bat;
import io.github.ningmeng123.mite.entity.R196Elemental;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Datagen-only CPU geometry/resource audit: no Minecraft instance, world, OpenGL, UI or actual render call. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class MobModelAudit031 implements DataProvider {
    private final Path output;
    private MobModelAudit031(Path output) { this.output=output; }
    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient()) return;
        Path generated=event.getGenerator().getPackOutput().getOutputFolder().toAbsolutePath().normalize();
        Path project=generated.getParent().getParent().getParent();
        event.getGenerator().addProvider(true,new MobModelAudit031(project.resolve("build/verification/031-client-model-audit.json")));
    }
    @Override public String getName() { return "031 client geometry/UV/resource audit without render or UI"; }
    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report=new JsonObject(); report.addProperty("author","NingMeng123");
        report.addProperty("minecraft_instance_created",false); report.addProperty("world_created",false);
        report.addProperty("opengl_or_render_invoked",false); report.addProperty("game_ui_automated",false);
        report.addProperty("visual_appearance_passed",false);
        try {
            JsonArray models=new JsonArray();
            LayerDefinition humanoid=LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,32);
            ModelPart humanoidRoot=auditLayer(models,"source_humanoid",humanoid,64,32,
                    List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"));
            ModelPart skeleton=auditLayer(models,"source_skeleton",SkeletonModel.createBodyLayer(),64,32,
                    List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"));
            cube(skeleton.getChild("right_arm"),-1,-2,-1,1,10,1,"source skinny skeleton arm");
            new LegacySkeletonModel<>(skeleton);
            ModelPart spider=auditLayer(models,"source_arachnid",LegacyArachnidModel.createLayer(),64,32,
                    List.of("head","body0","body1","right_hind_leg","left_hind_leg","right_middle_hind_leg","left_middle_hind_leg",
                            "right_middle_front_leg","left_middle_front_leg","right_front_leg","left_front_leg"));
            cube(spider.getChild("head"),-4,-4,-8,4,4,0,"source spider head");
            cube(spider.getChild("body1"),-5,-4,-6,5,4,6,"source spider abdomen");
            cube(spider.getChild("left_hind_leg"),-1,-1,-1,15,1,1,"source unmirrored left leg dimensions");
            Object[] polygons=(Object[])field(firstCube(spider.getChild("left_hind_leg")),"polygons");
            org.joml.Vector3f normal=(org.joml.Vector3f)field(polygons[2],"normal");
            check(normal.x()==-1F,"Source left spider leg must retain unmirrored west UV orientation");
            new LegacyArachnidModel(spider);
            ModelPart bat=auditLayer(models,"source_bat",LegacyBatModel.createLayer(),64,64,List.of("head","body"));
            check(bat.getChild("head").hasChild("right_ear")&&bat.getChild("head").hasChild("left_ear"),"Source bat ears");
            for(String wing:List.of("right_wing","left_wing")) check(bat.getChild("body").getChild(wing).hasChild("tip"),"Source bat outer wing hierarchy");
            cube(bat.getChild("head"),-3,-3,-3,3,3,3,"Source bat six-pixel head");
            cube(bat.getChild("body").getChild("right_wing"),-12,1,1.5F,-2,17,2.5F,"Source bat right wing");
            check(bat.getAllParts().count()==9,"Source bat eight parts plus modern mesh root"); new LegacyBatModel(bat);
            ModelPart inner=auditLayer(models,"cube_inner",SlimeModel.createInnerBodyLayer(),64,32,List.of("cube","right_eye","left_eye","mouth"));
            cube(inner.getChild("cube"),-3,17,-3,3,23,3,"Source inner gelatinous cube");
            ModelPart outer=auditLayer(models,"cube_outer",SlimeModel.createOuterBodyLayer(),64,32,List.of("cube"));
            cube(outer.getChild("cube"),-4,16,-4,4,24,4,"Source outer gelatinous cube");
            auditLayer(models,"source_inner_armor",LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(.5F),0),64,32),64,32,
                    List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"));
            auditLayer(models,"source_outer_armor",LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1F),0),64,32),64,32,
                    List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"));
            LegacyStalkerModel<?> invisible=new LegacyStalkerModel<>(humanoidRoot,.05F),fire=new LegacyStalkerModel<>(humanoid.bakeRoot(),0F);
            check((float)field(invisible,"opacity")==.05F&& (float)field(fire,"opacity")==0F,"Source stalker/fire opacity values");
            report.add("layers",models); report.addProperty("layer_count",models.size());
            JsonObject source;
            try(var input=MobModelAudit031.class.getResourceAsStream("/mite/mobs-031.json")) {
                check(input!=null,"Runtime source snapshot present");
                source=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            }
            Map<String,JsonObject> textures=new LinkedHashMap<>(); int identities=0;
            for(var element:source.getAsJsonArray("entities")) {
                JsonObject entity=element.getAsJsonObject(); int id=entity.get("legacy_id").getAsInt();
                if(id<512||id>540) continue; ++identities;
                if(id==514) check(entity.getAsJsonObject("renderer").get("opacity").getAsFloat()==.05F,"Source invisible-stalker opacity metadata");
                if(id==521) check(entity.getAsJsonObject("renderer").get("opacity").getAsFloat()==0F,"Source fire-elemental opacity metadata");
                for(var texture:entity.getAsJsonArray("textures")) {
                    JsonObject recorded=texture.getAsJsonObject(); String original=recorded.get("source").getAsString();
                    String relative=original.substring("assets/minecraft/textures/entity/".length());
                    String path="/assets/mite/textures/entity/"+relative;
                    check(R196MobRenderers.texture(relative.substring(0,relative.length()-4)).getPath().equals(path.substring("/assets/mite/".length())),"Renderer source texture path");
                    if(textures.containsKey(path)) continue;
                    byte[] png;
                    try(var input=MobModelAudit031.class.getResourceAsStream(path)) { check(input!=null,"Missing render asset "+path); png=input.readAllBytes(); }
                    check(ByteBuffer.wrap(png,16,8).getInt()==recorded.get("width").getAsInt(),"PNG width "+path);
                    check(ByteBuffer.wrap(png,20,4).getInt()==recorded.get("height").getAsInt(),"PNG height "+path);
                    String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
                    check(sha.equals(recorded.get("sha256").getAsString()),"Source PNG bytes changed "+path);
                    JsonObject fact=new JsonObject(); fact.addProperty("path",path); fact.addProperty("width",recorded.get("width").getAsInt());
                    fact.addProperty("height",recorded.get("height").getAsInt()); fact.addProperty("sha256",sha); textures.put(path,fact);
                }
            }
            check(identities==29&&textures.size()==44,"All 29 identities and 44 deduplicated PNGs");
            JsonArray textureList=new JsonArray(); textures.values().forEach(textureList::add); report.add("textures",textureList);
            report.addProperty("source_identity_count",identities); report.addProperty("texture_count",textures.size());
            check(OrdinarySkeletonState.class.getMethod("mite$sourceSkeletonType").getReturnType()==int.class,"Persistent ordinary skeleton state interface");
            check(R196Skeleton.class.getMethod("skeletonType").getReturnType()==int.class,"Custom skeleton synced state API");
            check(R196Elemental.class.getMethod("isMagma").getReturnType()==boolean.class,"Elemental synced material renderer API");
            check(R196Bat.class.getMethod("initializeHangingAt",BlockPos.class).getReturnType()==void.class,"Spawn-time hanging sync API");
            report.addProperty("state_interfaces_checked",4); report.addProperty("registered_source_kind_count",MobKind.values().length);
            report.addProperty("status","passed_structure_and_resource_audit_only");
            write(report); return CompletableFuture.completedFuture(null);
        } catch(Exception error) {
            report.addProperty("status","failed"); report.addProperty("failure",error.toString());
            try { write(report); } catch(Exception secondary) { error.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(error);
        }
    }
    private void write(JsonObject report) throws Exception {
        Files.createDirectories(output.getParent()); Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n",StandardCharsets.UTF_8);
    }
    private static ModelPart auditLayer(JsonArray facts,String name,LayerDefinition layer,int width,int height,List<String> parts) throws Exception {
        Object material=field(layer,"material"); check((int)field(material,"xTexSize")==width&&(int)field(material,"yTexSize")==height,"Layer texture dimensions "+name);
        ModelPart root=layer.bakeRoot(); for(String part:parts) check(root.hasChild(part),"Missing source part "+name+"/"+part);
        JsonObject fact=new JsonObject(); fact.addProperty("layer",name); fact.addProperty("texture_width",width); fact.addProperty("texture_height",height);
        fact.addProperty("baked_parts",root.getAllParts().count()); JsonArray required=new JsonArray(); parts.forEach(required::add); fact.add("required_parts",required); facts.add(fact); return root;
    }
    @SuppressWarnings("unchecked") private static ModelPart.Cube firstCube(ModelPart part) throws Exception { return ((List<ModelPart.Cube>)field(part,"cubes")).getFirst(); }
    private static void cube(ModelPart part,float x0,float y0,float z0,float x1,float y1,float z1,String note) throws Exception {
        ModelPart.Cube cube=firstCube(part); check(cube.minX==x0&&cube.minY==y0&&cube.minZ==z0&&cube.maxX==x1&&cube.maxY==y1&&cube.maxZ==z1,note);
    }
    private static Object field(Object target,String name) throws Exception { Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void check(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
}
