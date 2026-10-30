package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/** Actual loaded Punchy selectors and resource-pack combo clips in a disposable client world. @author NingMeng123 */
final class PunchyClubAnimationClientAudit {
    private PunchyClubAnimationClientAudit() {}

    static void audit(Minecraft mc, JsonObject report) throws ReflectiveOperationException, java.io.IOException {
        check(ModList.get().isLoaded("punchy"), "The actual Punchy mod must be loaded");
        check(!BetterCombatBridge.enabled(), "This regression must exercise the user's environment without Better Combat");
        report.addProperty("punchy_loaded", true);
        report.addProperty("scope", "actual transformed Punchy ItemStack/Item/arm classifiers and loaded sword attack combo clips; no attack packets");
        report.add("selected_resource_packs", new GsonBuilder().create().toJsonTree(mc.options.resourcePacks));
        Path transformed = Path.of(".mixin.out/class/punchy/client/render/ToolKindResolver.class");
        check(Files.isRegularFile(transformed), "The actual transformed Punchy classifier must be exported");
        ClassNode node = new ClassNode(); new ClassReader(Files.readAllBytes(transformed)).accept(node, 0);
        long injected = node.methods.stream().filter(method -> {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            return annotations.stream().anyMatch(annotation -> annotation.desc.endsWith("/MixinMerged;")
                    && annotation.values != null && annotation.values.contains(
                            "io.github.ningmeng123.mite.mixin.client.PunchyClubAnimationMixin"));
        }).count();
        check(injected >= 2, "Both real Punchy classifier entrypoints contain the merged club animation compatibility");
        report.addProperty("actual_punchy_class_export", transformed.toAbsolutePath().normalize().toString());

        Class<?> resolver = Class.forName("punchy.client.render.ToolKindResolver");
        Class<?> itemKind = Class.forName("punchy.client.renderer.layer.VanillaFirstPersonItemLayer$ToolKind");
        Class<?> armKind = Class.forName("punchy.client.renderer.layer.PlayerArmModelLayer$ToolKind");
        Class<?> entries = Class.forName("punchy.config.PunchyTuningConfig$CustomAnimationEntry");
        Class<?> state = Class.forName("punchy.client.state.AttackStateMachine");
        Class<?> custom = Class.forName("punchy.client.state.CustomAnimationResolver");
        Method stackKind = resolver.getMethod("resolveItemKind", ItemStack.class, itemKind);
        Method rawKind = resolver.getMethod("resolveItemKind", Item.class, itemKind);
        Method handKind = resolver.getMethod("resolveArmKind", ItemStack.class, armKind);
        Method entry = custom.getDeclaredMethod("findEntry", ItemStack.class, String.class); entry.setAccessible(true);
        Method names = custom.getDeclaredMethod("resolveComboNames", entries); names.setAccessible(true);
        Method select = state.getDeclaredMethod("resolveCustomAttackClip", ItemStack.class, InteractionHand.class, Minecraft.class, entries);
        select.setAccessible(true);
        ItemStack originalMain = mc.player.getMainHandItem(), originalOff = mc.player.getOffhandItem();
        JsonArray cases = new JsonArray(); report.add("punchy_club_cases", cases);
        try {
            mc.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ItemStack sword = new ItemStack(MiteTools.get("mite:mithril_sword"));
            check(!sword.isEmpty() && sword.is(ItemTags.SWORDS), "The reference is the actual tagged mithril sword");
            check("SWORD".equals(String.valueOf(stackKind.invoke(null, sword, null))), "The actual mithril sword selects the loaded sword group");
            Object swordEntry = entry.invoke(null, sword, "attack");
            check(swordEntry != null, "The current animation resource packs supply a sword attack entry");
            List<?> comboNames = (List<?>)names.invoke(null, swordEntry);
            check(!comboNames.isEmpty(), "The active sword attack entry contains actual combo variations");
            report.add("active_sword_attack_entry", new GsonBuilder().create().toJsonTree(swordEntry));
            List<String> reference = resolveCombo(mc, sword, swordEntry, state, select, comboNames.size());
            report.add("active_sword_resolved_combo_clips", new GsonBuilder().create().toJsonTree(reference));
            List<Item> clubs = new ArrayList<>(List.of(Items.WOODEN_SWORD, MiteTools.get("mite:wooden_cudgel")));
            for (String metal : List.of("copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium")) {
                var id = ResourceLocation.fromNamespaceAndPath("modern_ite", "club_" + metal);
                check(BuiltInRegistries.ITEM.containsKey(id), "Every actual addon club is registered: " + id);
                clubs.add(BuiltInRegistries.ITEM.get(id));
            }
            for (Item item : clubs) for (boolean saved : new boolean[]{false, true}) {
                ItemStack stack = new ItemStack(item);
                if (saved) stack = ItemStack.parse(mc.level.registryAccess(), stack.save(mc.level.registryAccess())).orElseThrow();
                check(!stack.is(ItemTags.SWORDS), "Animation compatibility must not add clubs to gameplay sword tags: " + item);
                check("SWORD".equals(String.valueOf(stackKind.invoke(null, stack, null))), "The actual ItemStack classifier selects sword animation: " + item);
                check("SWORD".equals(String.valueOf(rawKind.invoke(null, item, null))), "The actual Item classifier selects sword animation: " + item);
                check("SWORD".equals(String.valueOf(handKind.invoke(null, stack, null))), "The actual arm classifier selects sword animation: " + item);
                Object attack = entry.invoke(null, stack, "attack");
                check(attack == swordEntry, "The club uses the same live resource-pack attack entry as the mithril sword: " + item);
                List<String> clips = resolveCombo(mc, stack, attack, state, select, comboNames.size());
                check(clips.equals(reference), "Every actual club combo selects exactly the sword's loaded animation clips: " + item);
                JsonObject fact = new JsonObject(); fact.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString());
                fact.addProperty("saved_stack", saved); fact.addProperty("item_stack_item_and_arm_kind", "SWORD");
                fact.addProperty("gameplay_sword_tag", false); fact.add("actual_attack_clips", new GsonBuilder().create().toJsonTree(clips));
                fact.addProperty("passed", true); cases.add(fact);
            }
            for (Item item : List.of(Items.APPLE, Items.IRON_PICKAXE, MiteTools.get("mite:iron_war_hammer")))
                check(!"SWORD".equals(String.valueOf(stackKind.invoke(null, new ItemStack(item), null))),
                        "Unrelated food, tools and hammers keep their existing Punchy animation identity: " + item);
            report.addProperty("ten_clubs_new_and_saved_share_the_active_sword_animation_without_bettercombat", true);
            report.addProperty("animation_clips_and_user_resource_packs_not_replaced", true);
            report.addProperty("visual_appearance_tested", false);
        } finally {
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, originalOff);
        }
    }

    private static List<String> resolveCombo(Minecraft mc, ItemStack stack, Object entry, Class<?> state, Method select, int count)
            throws ReflectiveOperationException {
        mc.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Object machine = state.getConstructor().newInstance();
        List<String> result = new ArrayList<>();
        for (int index = 0; index < count; ++index) {
            Object clip = select.invoke(machine, stack, InteractionHand.MAIN_HAND, mc, entry);
            check(clip != null, "The real Punchy attack selector resolves a loaded combo clip for " + stack);
            result.add((String)clip.getClass().getMethod("getName").invoke(clip));
        }
        return result;
    }

    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
