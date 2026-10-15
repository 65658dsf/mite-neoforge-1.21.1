package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Opt-in transformed renderer/resource audit: no target initialization, renderer, world or OpenGL. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class LivestockHealthRenderAudit033 implements DataProvider {
    private static final String RENDERERS="net.minecraft.client.renderer.entity.";
    private static final String MIXINS="io.github.ningmeng123.mite.mixin.client.";
    private static final String HELPER="io/github/ningmeng123/mite/entity/animal/LivestockHealth033";
    private static final String HELPER_DESCRIPTOR="(Lnet/minecraft/world/entity/Mob;)Lnet/minecraft/resources/ResourceLocation;";
    private static final List<Skin> SKINS=List.of(
            new Skin("Cow","cow","b547af8cb364bf46a4d669a60cdc8e06522095307f49dc306529c1750ad5217b"),
            new Skin("Chicken","chicken","21398ff4ef0cb6b6c67869e2ffc1afb903e6ecd1ca329edb4b9e16261deceb3d"),
            new Skin("Pig","pig","6d9d6004dd6e5a80448afbf04abffa06ca727fe1018aaf8f7f532830d5d0ce63"),
            new Skin("Sheep","sheep","0bdfad217ff0b5e20e171d7d63dc6bf1e47f744ec1b9371582e0467825726d0a"));
    private final Path output;
    private LivestockHealthRenderAudit033(Path output) { this.output=output; }
    private record Skin(String animal,String species,String sha256) {}

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if(!event.includeClient() || !Boolean.getBoolean("mite.livestockHealthAudit")) return;
        Path workspace=event.getGenerator().getPackOutput().getOutputFolder().toAbsolutePath().resolve("../../..").normalize();
        Path report=Path.of(System.getProperty("mite.livestockHealthAuditOutput",
                workspace.resolve("build/verification/033-client-health-audit.json").toString()));
        event.getGenerator().addProvider(true,new LivestockHealthRenderAudit033(report));
    }
    @Override public String getName() { return "033 transformed livestock body texture audit without render or UI"; }
    @Override public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report=new JsonObject(); report.addProperty("author","NingMeng123");
        report.addProperty("minecraft_instance_created",false); report.addProperty("world_created",false);
        report.addProperty("opengl_or_render_invoked",false); report.addProperty("game_ui_automated",false);
        report.addProperty("visual_appearance_passed",false); report.addProperty("target_initialization_requested",false);
        JsonArray rendererFacts=new JsonArray(),textureFacts=new JsonArray();
        report.add("renderers",rendererFacts); report.add("textures",textureFacts);
        try {
            check(Boolean.getBoolean("mixin.debug.export"),"Mixin transformed-class export must be enabled");
            var loader=LivestockHealthRenderAudit033.class.getClassLoader();
            for(Skin skin:SKINS) {
                String target=RENDERERS+skin.animal()+"Renderer";
                // Defining the class triggers Mixin; false prevents renderer/Minecraft static initialization.
                Class.forName(target,false,loader);
                Path exported=Path.of(".mixin.out","class",target.replace('.','/')+".class");
                check(Files.isRegularFile(exported),"Missing transformed renderer export: "+exported);
                byte[] bytecode=Files.readAllBytes(exported); ClassNode type=new ClassNode();
                new ClassReader(bytecode).accept(type,0);
                String descriptor="(Lnet/minecraft/world/entity/animal/"+skin.animal()+";)Lnet/minecraft/resources/ResourceLocation;";
                MethodNode texture=method(type,"getTextureLocation",descriptor);
                String mixin=MIXINS+"Sick"+skin.animal()+"Texture033Mixin";
                var handlers=type.methods.stream().filter(method -> mergedFrom(method,mixin)).toList();
                check(handlers.size()==1,"Expected exactly one injected method from "+mixin+", found "+handlers.size());
                MethodNode handler=handlers.getFirst();
                String callback="(Lnet/minecraft/world/entity/animal/"+skin.animal()+";Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
                check(handler.desc.equals(callback),"Typed callback descriptor: "+mixin);
                check(calls(texture,type.name,handler.name,handler.desc)==1,"Typed texture method must invoke the injected handler once: "+mixin);
                check(calls(handler,HELPER,"sickBodyTexture",HELPER_DESCRIPTOR)==1,"Injected handler must invoke the shared wellness texture helper once: "+mixin);
                check(calls(handler,"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable","setReturnValue","(Ljava/lang/Object;)V")==1,
                        "Injected handler must set the conditional body texture: "+mixin);
                for(var instruction:handler.instructions) if(instruction instanceof MethodInsnNode call
                        && call.owner.equals(HELPER) && call.name.equals("sickBodyTexture"))
                    check(call.getOpcode()==Opcodes.INVOKESTATIC,"Shared texture helper is a static common-code call");
                JsonObject renderer=new JsonObject(); renderer.addProperty("target",target); renderer.addProperty("typed_method",texture.name+texture.desc);
                renderer.addProperty("mixin",mixin); renderer.addProperty("handler",handler.name+handler.desc);
                renderer.addProperty("export",exported.toAbsolutePath().normalize().toString()); renderer.addProperty("export_sha256",sha256(bytecode));
                renderer.addProperty("typed_handler_calls",1); renderer.addProperty("shared_helper_calls",1); rendererFacts.add(renderer);

                String path="/assets/mite/textures/entity/livestock/"+skin.species()+"_sick.png";
                byte[] png;
                try(var input=loader.getResourceAsStream(path.substring(1))) {
                    check(input!=null,"Missing source disease texture "+path); png=input.readAllBytes();
                }
                check(png.length>=33 && ByteBuffer.wrap(png).getLong()==0x89504e470d0a1a0aL,"PNG signature "+path);
                int width=ByteBuffer.wrap(png,16,4).getInt(),height=ByteBuffer.wrap(png,20,4).getInt();
                check(width==64&&height==32,"Pinned livestock skin UV dimensions "+path);
                String sha256=sha256(png); check(sha256.equals(skin.sha256()),"Original source texture bytes "+path);
                JsonObject asset=new JsonObject(); asset.addProperty("entity_id","minecraft:"+skin.species()); asset.addProperty("path",path.substring(1));
                asset.addProperty("width",width); asset.addProperty("height",height); asset.addProperty("sha256",sha256); textureFacts.add(asset);
            }
            report.addProperty("renderer_count",rendererFacts.size()); report.addProperty("texture_count",textureFacts.size());
            report.addProperty("status","passed_transformed_bytecode_and_resource_audit_only");
            write(report); return CompletableFuture.completedFuture(null);
        } catch(Throwable error) {
            report.addProperty("status","failed"); report.addProperty("failure",error.toString());
            try { write(report); } catch(Exception secondary) { error.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(error);
        }
    }
    private void write(JsonObject report) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n",StandardCharsets.UTF_8);
    }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static MethodNode method(ClassNode type,String name,String descriptor) {
        return type.methods.stream().filter(method -> method.name.equals(name)&&method.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing pinned typed method "+type.name+"."+name+descriptor));
    }
    private static boolean mergedFrom(MethodNode method,String mixin) {
        return annotationFrom(method.visibleAnnotations,mixin)||annotationFrom(method.invisibleAnnotations,mixin);
    }
    private static boolean annotationFrom(List<AnnotationNode> annotations,String mixin) {
        if(annotations==null) return false;
        for(AnnotationNode annotation:annotations) if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values!=null)
            for(int i=0;i+1<annotation.values.size();i+=2)
                if(annotation.values.get(i).equals("mixin") && annotation.values.get(i+1).equals(mixin)) return true;
        return false;
    }
    private static int calls(MethodNode method,String owner,String name,String descriptor) {
        int count=0; for(var instruction:method.instructions) if(instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) ++count;
        return count;
    }
    private static void check(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
}
