package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import io.github.ningmeng123.mite.block.MaterialAnvilBlock;
import io.github.ningmeng123.mite.integration.jade.AnvilJadeProvider;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Called only inside the existing loaded-Jade audit guard; no window, world or rendering. @author NingMeng123 */
public final class AnvilJadeAudit {
    private static final BlockPos POSITION = new BlockPos(12, 80, -5);
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");
    private AnvilJadeAudit() {}

    static void audit(JsonObject report, Path output) throws java.io.IOException {
        JsonObject result = new JsonObject(); report.add("anvil_jade", result);
        result.addProperty("author", "NingMeng123");
        try {
            inspect(result);
            result.addProperty("status", "passed_real_jade_payload_and_localized_component_audit_without_rendering");
        } catch (RuntimeException failure) {
            result.addProperty("status", "failed"); result.addProperty("failure", failure.toString()); throw failure;
        } finally {
            Path absolute = output.toAbsolutePath().normalize(); Files.createDirectories(absolute.getParent());
            Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n", StandardCharsets.UTF_8);
        }
    }

    private static void inspect(JsonObject result) {
        JsonArray cases = new JsonArray(); result.add("accepted_states", cases);
        for (BlockState state : List.of(Blocks.ANVIL.defaultBlockState(), Blocks.CHIPPED_ANVIL.defaultBlockState(), Blocks.DAMAGED_ANVIL.defaultBlockState()))
            checkState(state, 396800, cases);
        Map<String, Integer> maxima = Map.of("copper", 198400, "silver", 198400, "gold", 198400,
                "ancient_metal", 793600, "mithril", 3174400, "adamantium", 12697600, "vibranium", 25395200);
        for (var entry : MiteFunctionalBlocks.ANVILS.entrySet()) for (int stage = 0; stage < 3; stage++)
            checkState(entry.getValue().get().defaultBlockState().setValue(MaterialAnvilBlock.DAMAGE_STAGE, stage), maxima.get(entry.getKey()), cases);
        check(cases.size() == 24, "The eight material anvils have exactly three displayed stages each");
        BlockState state = Blocks.CHIPPED_ANVIL.defaultBlockState();
        CompoundTag packet = packet(state, 123456, 396800);
        Map<String, Consumer<CompoundTag>> mutations = new LinkedHashMap<>();
        mutations.put("other_position", data -> data.putLong("position", POSITION.east().asLong()));
        mutations.put("other_dimension", data -> data.putString("dimension", "minecraft:the_nether"));
        mutations.put("other_block", data -> data.putString("block", "minecraft:anvil"));
        mutations.put("other_state", data -> data.putString("state", Blocks.DAMAGED_ANVIL.defaultBlockState().toString()));
        mutations.put("missing_position", data -> data.remove("position"));
        mutations.put("missing_state", data -> data.remove("state"));
        mutations.put("wrong_remaining_type", data -> data.putString("remaining", "123456"));
        mutations.put("wrong_maximum_type", data -> data.putLong("maximum", 396800L));
        mutations.put("missing_remaining", data -> data.remove("remaining"));
        mutations.put("missing_maximum", data -> data.remove("maximum"));
        mutations.put("exhausted", data -> data.putInt("remaining", 0));
        mutations.put("negative_remaining", data -> data.putInt("remaining", -1));
        mutations.put("remaining_above_maximum", data -> data.putInt("remaining", 396801));
        mutations.put("wrong_material_maximum", data -> data.putInt("maximum", 198400));
        mutations.put("zero_maximum", data -> data.putInt("maximum", 0));
        JsonArray rejected = new JsonArray(); result.add("rejected_payloads", rejected);
        mutations.forEach((name, mutation) -> {
            CompoundTag corrupt = packet.copy(); mutation.accept(corrupt.getCompound(AnvilJadeProvider.DATA_KEY));
            check(AnvilJadeProvider.snapshot(state, POSITION, DIMENSION, corrupt, false) == null,
                    "Invalid or stale anvil data must display no durability: " + name); rejected.add(name);
        });
        check(AnvilJadeProvider.snapshot(state, POSITION, DIMENSION, packet, true) == null, "Fake inspection targets cannot show server wear");
        check(AnvilJadeProvider.snapshot(state, POSITION, DIMENSION, new CompoundTag(), false) == null, "Missing server data cannot invent wear from the visual stage");
        check(AnvilJadeProvider.snapshot(Blocks.STONE.defaultBlockState(), POSITION, DIMENSION, packet, false) == null, "A late packet cannot display anvil wear over another block");
        result.addProperty("materials", 7); result.addProperty("states", cases.size());
        result.addProperty("missing_fake_unrelated_and_stale_targets_hidden", true);
        result.addProperty("server_values_retained_without_guessing_visual_stage", true);
        result.addProperty("world_created_or_rendering_invoked", false);
        auditItemTooltip(result);
    }

    private static void auditItemTooltip(JsonObject result) {
        ItemStack copper = new ItemStack(MiteFunctionalBlocks.ANVILS.get("copper").get(), 3);
        itemTooltip(copper, 198400, 198400);
        ItemStack adamantium = new ItemStack(MiteFunctionalBlocks.ANVILS.get("adamantium").get(), 2);
        adamantium.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), 1234567);
        adamantium.set(DataComponents.CUSTOM_NAME, Component.literal("high precision source anvil"));
        itemTooltip(adamantium, 11463033, 12697600);
        ItemStack ordinary = new ItemStack(Items.STONE, 4), before = ordinary.copy();
        List<Component> lines = new java.util.ArrayList<>();
        io.github.ningmeng123.mite.client.AnvilDurabilityDisplay.tooltip(
                new ItemTooltipEvent(ordinary, null, lines, TooltipFlag.NORMAL, Item.TooltipContext.EMPTY));
        check(lines.isEmpty() && ItemStack.matches(ordinary, before), "A non-anvil tooltip neither adds durability nor changes its stack");
        result.addProperty("real_item_tooltip_event_fresh_copper_and_exact_damaged_adamantium", true);
        result.addProperty("non_anvil_event_ignored_and_all_stack_components_unchanged", true);
        result.addProperty("render_callback_invoked", false);
    }

    private static void itemTooltip(ItemStack stack, int remaining, int maximum) {
        ItemStack before = stack.copy(); List<Component> lines = new java.util.ArrayList<>();
        io.github.ningmeng123.mite.client.AnvilDurabilityDisplay.tooltip(
                new ItemTooltipEvent(stack, null, lines, TooltipFlag.NORMAL, Item.TooltipContext.EMPTY));
        check(lines.size() == 1 && lines.getFirst().getContents() instanceof TranslatableContents translated
                        && translated.getKey().equals("tooltip.mite.durability")
                        && List.of(translated.getArgs()).equals(List.of(remaining, maximum)),
                "The actual item event emits exact remaining/maximum integers without a player or Minecraft instance");
        check(ItemStack.matches(stack, before), "Displaying item durability never changes count, wear or custom components");
    }

    private static void checkState(BlockState state, int maximum, JsonArray cases) {
        CompoundTag packet = packet(state, maximum - 37, maximum);
        var snapshot = AnvilJadeProvider.snapshot(state, POSITION, DIMENSION, packet, false);
        check(snapshot != null && snapshot.remaining() == maximum - 37 && snapshot.maximum() == maximum,
                "The exact server values survive decoding for " + state);
        var line = AnvilJadeProvider.line(snapshot);
        check(line.getContents() instanceof TranslatableContents translated
                        && translated.getKey().equals("tooltip.mite.durability")
                        && List.of(translated.getArgs()).equals(List.of(maximum - 37, maximum)),
                "The shared localized durability label receives remaining and maximum in that order");
        JsonObject fact = new JsonObject(); fact.addProperty("state", state.toString());
        fact.addProperty("remaining", snapshot.remaining()); fact.addProperty("maximum", snapshot.maximum()); cases.add(fact);
    }

    private static CompoundTag packet(BlockState state, int remaining, int maximum) {
        CompoundTag payload = new CompoundTag();
        payload.putLong("position", POSITION.asLong()); payload.putString("dimension", DIMENSION.toString());
        payload.putString("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()); payload.putString("state", state.toString());
        payload.putInt("remaining", remaining); payload.putInt("maximum", maximum);
        CompoundTag data = new CompoundTag(); data.put(AnvilJadeProvider.DATA_KEY, payload); return data;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
