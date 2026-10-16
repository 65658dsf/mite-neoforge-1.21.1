package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Actual client target transforms and call sites, with no Minecraft instance, world or GL initialization. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class SelectedMechanicsClientAudit implements DataProvider {
    private static final String MIXINS="io.github.ningmeng123.mite.mixin.client.";
    private static final String REACH="io/github/ningmeng123/mite/combat/SourcePlayerReach";
    private static final String CALLBACK="org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String RESULT_CALLBACK=CALLBACK+"Returnable";
    private final Path output;
    private SelectedMechanicsClientAudit(Path output){this.output=output;}

    @SubscribeEvent public static void gather(GatherDataEvent event){
        if(!event.includeClient()||!Boolean.getBoolean("mite.witchCurseAudit"))return;
        Path existing=Path.of(System.getProperty("mite.witchCurseAuditOutput","build/verification/witch-curse-client-audit.json"));
        event.getGenerator().addProvider(true,new SelectedMechanicsClientAudit(existing.resolveSibling("selected-mechanics-client-audit.json")));
    }
    @Override public String getName(){return "Selected R196 mechanics actual client transforms without rendering";}
    @Override public CompletableFuture<?> run(CachedOutput unused){
        JsonObject report=new JsonObject();
        report.addProperty("author","NingMeng123");
        report.addProperty("minecraft_instance_created",false);
        report.addProperty("world_created",false);
        report.addProperty("opengl_or_render_invoked",false);
        report.addProperty("target_initialization_requested",false);
        report.addProperty("game_ui_automated",false);
        report.addProperty("visual_appearance_passed",false);
        report.addProperty("scope","actual Mixin transformation and bytecode wiring; not visual or interactive testing");
        JsonArray targets=new JsonArray();report.add("targets",targets);
        try{
            check(Boolean.getBoolean("mixin.debug.export"),"Actual transformed-class export is required");
            auditSelection(transformed("net/minecraft/client/renderer/GameRenderer",targets),report);
            auditActions(transformed("net/minecraft/client/multiplayer/MultiPlayerGameMode",targets),report);
            auditSleep(transformed("net/minecraft/client/gui/Gui",targets),report);
            report.addProperty("status","passed_actual_client_transforms_and_call_sites_without_initialization_or_rendering");
            write(report);return CompletableFuture.completedFuture(null);
        }catch(Throwable failure){
            report.addProperty("status","failed");report.addProperty("failure",failure.toString());
            try{write(report);}catch(Exception secondary){failure.addSuppressed(secondary);}
            return CompletableFuture.failedFuture(failure);
        }
    }
    private static void auditSelection(ClassNode renderer,JsonObject report){
        MethodNode pick=method(renderer,"pick","(F)V");
        MethodNode ray=merged(renderer,"SourceReachSelectionMixin","mite$sourceSelectionRay");
        MethodNode filter=merged(renderer,"SourceReachSelectionMixin","mite$filterTargetReach");
        check(calls(pick,renderer.name,ray.name)==1,"Actual public pick supplies modified selection ray once");
        check(calls(pick,renderer.name,filter.name)==1,"Actual public pick filters the nearest collision once");
        check(calls(ray,"org/spongepowered/asm/mixin/injection/invoke/arg/Args","set")==2,
                "Both block and entity native ray limits are changed");
        long sixteen=0;
        for(var instruction:ray.instructions)if(instruction instanceof LdcInsnNode constant&&constant.cst instanceof Double value&&value==16D)sixteen++;
        check(sixteen==2,"Both selection limits are source sixteen, not an increased effective interaction radius");
        check(calls(filter,REACH,"canReachBlock")==2,"Block selection tests both explicitly adapted modern hands");
        check(calls(filter,REACH,"canReachEntityAt")==2,"Entity selection tests melee and interaction collision contexts");
        check(field(filter,"net/minecraft/client/Minecraft","hitResult",Opcodes.PUTFIELD)
                &&field(filter,"net/minecraft/client/Minecraft","crosshairPickEntity",Opcodes.PUTFIELD),
                "An unreachable nearest collision clears both actionable hit and selected entity");
        check(calls(filter,"net/minecraft/world/phys/BlockHitResult","miss")==1,
                "Rejected selection becomes a native MISS without selecting a further object through it");
        report.addProperty("selection_nearest_collision_source_range_filter_wired",true);
        report.addProperty("selection_distinct_melee_interaction_and_both_block_hands_wired",true);
    }
    private static void auditActions(ClassNode mode,JsonObject report){
        String entity="Lnet/minecraft/world/entity/Entity;",player="Lnet/minecraft/world/entity/player/Player;";
        String hand="Lnet/minecraft/world/InteractionHand;",result="Lnet/minecraft/world/InteractionResult;";
        MethodNode attack=merged(mode,"SourceReachActionsMixin","mite$meleePointReach");
        check(calls(method(mode,"attack","("+player+entity+")V"),mode.name,attack.name)==1,"Native attack has the client source guard");
        check(calls(attack,REACH,"canMelee")==1&&calls(attack,REACH,"canReachEntityAt")==1,
                "Attack checks target eligibility and the actually selected collision point");
        check(calls(attack,CALLBACK,"cancel")==1,"Out-of-range attack is cancellable before sending its native packet");
        assertAction(mode,"interact","("+player+entity+hand+")"+result,"mite$interactReach","canInteract");
        assertAction(mode,"interactAt","("+player+entity+"Lnet/minecraft/world/phys/EntityHitResult;"+hand+")"+result,
                "mite$interactPointReach","canReachEntityAt");
        assertAction(mode,"useItemOn","(Lnet/minecraft/client/player/LocalPlayer;"+hand+"Lnet/minecraft/world/phys/BlockHitResult;)"+result,
                "mite$blockUseReach","canReachBlock");
        MethodNode dig=merged(mode,"SourceReachActionsMixin","mite$blockDigReach");
        for(String action:List.of("startDestroyBlock","continueDestroyBlock"))
            check(calls(method(mode,action,"(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"),mode.name,dig.name)==1,
                    "Actual "+action+" revalidates the current main hand");
        check(calls(dig,REACH,"canReachBlock")==1&&calls(dig,RESULT_CALLBACK,"setReturnValue")==1,
                "Both dig phases share one cancellable source block gate");
        report.addProperty("client_attack_interaction_use_and_both_dig_phases_wired",true);
        report.addProperty("client_melee_does_not_inherit_longer_entity_interaction_range",true);
    }
    private static void assertAction(ClassNode mode,String action,String descriptor,String handler,String gate){
        MethodNode merged=merged(mode,"SourceReachActionsMixin",handler);
        check(calls(method(mode,action,descriptor),mode.name,merged.name)==1,"Actual "+action+" calls source guard once");
        check(calls(merged,REACH,gate)==1&&calls(merged,RESULT_CALLBACK,"setReturnValue")==1,
                action+" rejects invalid reach before the native transaction");
    }
    private static void auditSleep(ClassNode gui,JsonObject report)throws Exception{
        MethodNode render=method(gui,"renderSleepOverlay","(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V");
        MethodNode cancel=merged(gui,"SourceSleepOverlayMixin","mite$sourceFade");
        check(calls(render,gui.name,cancel.name)==1&&calls(cancel,CALLBACK,"cancel")==1,
                "Actual native sleep overlay is cancelled, preventing an independent native darkness timer");
        check(calls(render,CALLBACK,"isCancelled")==1,"The transformed native overlay has the injected early-return gate");
        ClassNode layer=resource("io/github/ningmeng123/mite/client/SleepConsciousnessDisplay");
        check(layer.methods.stream().mapToLong(m->calls(m,"io/github/ningmeng123/mite/survival/PlayerRest","counter")).sum()==1,
                "The replacement fade actually reads source consciousness rather than native sleepCounter");
        check(layer.methods.stream().mapToLong(m->calls(m,"net/neoforged/neoforge/client/event/RegisterGuiLayersEvent","registerBelowAll")).sum()==1,
                "The replacement source fade is registered beneath the native controls");
        report.addProperty("native_sleep_fade_cancelled_and_source_counter_layer_wired",true);
    }
    private static ClassNode transformed(String name,JsonArray targets)throws Exception{
        Class.forName(name.replace('/','.'),false,SelectedMechanicsClientAudit.class.getClassLoader());
        Path path=Path.of(".mixin.out","class",name+".class");
        check(Files.isRegularFile(path),"Missing actual transformed client export "+name);
        byte[] bytes=Files.readAllBytes(path);
        JsonObject fact=new JsonObject();fact.addProperty("class",name);fact.addProperty("initialized",false);
        fact.addProperty("export",path.toAbsolutePath().normalize().toString());
        fact.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));targets.add(fact);
        return read(bytes);
    }
    private static ClassNode resource(String name)throws Exception{
        try(var in=SelectedMechanicsClientAudit.class.getClassLoader().getResourceAsStream(name+".class")){
            check(in!=null,"Missing production layer class "+name);return read(in.readAllBytes());
        }
    }
    private static ClassNode read(byte[] bytes){ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static MethodNode method(ClassNode type,String name,String descriptor){
        return type.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(descriptor)).findFirst()
                .orElseThrow(()->new IllegalStateException("Missing actual method "+type.name+"."+name+descriptor));
    }
    private static MethodNode merged(ClassNode type,String mixin,String original){
        var matches=type.methods.stream().filter(m->m.name.contains(original)
                &&(mergedFrom(m.visibleAnnotations,MIXINS+mixin)||mergedFrom(m.invisibleAnnotations,MIXINS+mixin))).toList();
        check(matches.size()==1,"Exactly one actual merged handler "+mixin+"."+original);return matches.getFirst();
    }
    private static boolean mergedFrom(List<AnnotationNode> annotations,String mixin){
        if(annotations==null)return false;
        for(var a:annotations)if(a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")&&a.values!=null)
            for(int i=0;i+1<a.values.size();i+=2)if(a.values.get(i).equals("mixin")&&a.values.get(i+1).equals(mixin))return true;
        return false;
    }
    private static long calls(MethodNode method,String owner,String name){
        long count=0;for(var i:method.instructions)if(i instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))count++;return count;
    }
    private static boolean field(MethodNode method,String owner,String name,int opcode){
        for(var i:method.instructions)if(i instanceof FieldInsnNode field&&field.owner.equals(owner)&&field.name.equals(name)&&field.getOpcode()==opcode)return true;return false;
    }
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private void write(JsonObject report)throws Exception{
        Path target=output.toAbsolutePath().normalize();Files.createDirectories(target.getParent());
        Files.writeString(target,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n",StandardCharsets.UTF_8);
    }
}
