package io.github.ningmeng123.mite.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.mining.SourceBlockHardness;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source block-hardness integration and literal wear vectors. @author NingMeng123 */
@GameTestHolder("mite_hardness")
@PrefixGameTestTemplate(false)
public final class SourceBlockHardnessGameTests {
    @GameTest(template = "empty")
    public static void allMappedStatesReachMiningWithoutMutatingRegisteredProperties(GameTestHelper helper) {
        JsonArray rows = new JsonArray();
        helper.assertTrue(!SourceBlockHardness.registeredHardness().isEmpty(), "The source full-block table is populated");
        helper.assertTrue(!SourceBlockHardness.slabHardness().isEmpty(), "The source slab table is populated");
        new TreeMap<>(SourceBlockHardness.registeredHardness()).forEach((path, expected) -> {
            ResourceLocation id = ResourceLocation.withDefaultNamespace(path);
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Every mapped block exists: " + id);
            auditState(helper, BuiltInRegistries.BLOCK.get(id).defaultBlockState(), expected, "default", rows);
        });
        new TreeMap<>(SourceBlockHardness.slabHardness()).forEach((path, expected) -> {
            ResourceLocation id = ResourceLocation.withDefaultNamespace(path);
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Every mapped slab exists: " + id);
            BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            helper.assertTrue(state.hasProperty(SlabBlock.TYPE), "The mapped slab has the native slab property: " + id);
            auditState(helper, state.setValue(SlabBlock.TYPE, SlabType.BOTTOM), expected, "bottom", rows);
            auditState(helper, state.setValue(SlabBlock.TYPE, SlabType.TOP), expected, "top", rows);
            auditState(helper, state.setValue(SlabBlock.TYPE, SlabType.DOUBLE), expected * 2F, "double", rows);
        });
        writeAudit(rows);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualItemStackMiningUsesLiteralR196WearVectors(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            WearCase[] cases = {
                    new WearCase(Blocks.STONE.defaultBlockState(), Items.IRON_PICKAXE, 240),
                    new WearCase(Blocks.OBSIDIAN.defaultBlockState(), Items.IRON_PICKAXE, 240),
                    new WearCase(Blocks.NETHERRACK.defaultBlockState(), Items.IRON_PICKAXE, 160),
                    new WearCase(Blocks.COAL_BLOCK.defaultBlockState(), Items.IRON_PICKAXE, 120),
                    new WearCase(Blocks.GLASS.defaultBlockState(), Items.IRON_AXE, 200),
                    new WearCase(Blocks.GLASS_PANE.defaultBlockState(), Items.IRON_AXE, 10),
                    new WearCase(Blocks.ICE.defaultBlockState(), Items.IRON_AXE, 100),
                    new WearCase(Blocks.OAK_PLANKS.defaultBlockState(), Items.IRON_AXE, 80),
                    new WearCase(Blocks.OAK_FENCE.defaultBlockState(), Items.IRON_AXE, 40),
                    new WearCase(Blocks.CACTUS.defaultBlockState(), Items.IRON_AXE, 40),
                    new WearCase(Blocks.CLAY.defaultBlockState(), Items.IRON_SHOVEL, 40),
                    new WearCase(Blocks.PUMPKIN.defaultBlockState(), Items.IRON_AXE, 60),
                    new WearCase(Blocks.MELON.defaultBlockState(), Items.IRON_AXE, 60),
                    new WearCase(Blocks.COBWEB.defaultBlockState(), Items.IRON_SWORD, 20),
                    new WearCase(Blocks.STONE_BRICK_SLAB.defaultBlockState(), Items.IRON_PICKAXE, 100),
                    new WearCase(Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), Items.IRON_PICKAXE, 200),
                    new WearCase(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Items.IRON_PICKAXE, 150),
                    new WearCase(Blocks.OAK_SLAB.defaultBlockState(), Items.IRON_AXE, 40),
                    new WearCase(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), Items.IRON_AXE, 80),
                    new WearCase(Blocks.OAK_STAIRS.defaultBlockState(), Items.IRON_AXE, 80)
            };
            for (WearCase test : cases) {
                ItemStack tool = new ItemStack(test.tool());
                player.setItemSlot(EquipmentSlot.MAINHAND, tool);
                helper.assertTrue(tool.getDestroySpeed(test.state()) > 1F, "The fixture tool is effective for " + test.state());
                helper.assertTrue(!test.state().requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(test.state()),
                        "The fixture meets the actual drop tier for " + test.state());
                tool.mineBlock(helper.getLevel(), test.state(), pos, player);
                helper.assertValueEqual(tool.getDamageValue(), test.wear(),
                        "The actual mining callback applies the literal source wear once for " + test.state());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void slabsStairsSnowAndVinesUseTheirOwnSourceStateRules(GameTestHelper helper) {
        assertHardness(helper, Blocks.STONE_BRICKS.defaultBlockState(), 1.5F);
        assertHardness(helper, Blocks.STONE_BRICK_STAIRS.defaultBlockState(), 1.5F);
        assertHardness(helper, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 1F);
        assertHardness(helper, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 2F);
        assertHardness(helper, Blocks.OAK_PLANKS.defaultBlockState(), .8F);
        assertHardness(helper, Blocks.OAK_STAIRS.defaultBlockState(), .8F);
        assertHardness(helper, Blocks.OAK_SLAB.defaultBlockState(), .4F);
        assertHardness(helper, Blocks.SANDSTONE.defaultBlockState(), .8F);
        assertHardness(helper, Blocks.SANDSTONE_STAIRS.defaultBlockState(), .8F);
        assertHardness(helper, Blocks.SANDSTONE_SLAB.defaultBlockState(), .5F);
        assertHardness(helper, Blocks.SANDSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 1F);
        for (int layers = 1; layers <= 8; layers++)
            assertHardness(helper, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), .05F * layers);
        BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.UP, true);
        assertHardness(helper, vine, .2F);
        vine = vine.setValue(VineBlock.NORTH, true);
        assertHardness(helper, vine, .2F);
        vine = vine.setValue(VineBlock.SOUTH, true);
        assertHardness(helper, vine, .4F);
        vine = vine.setValue(VineBlock.EAST, true).setValue(VineBlock.WEST, true);
        assertHardness(helper, vine, .8F);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unmappedModernBlocksNegativeHardnessAndForeignNamespacesRetainTheirInputs(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.CRYING_OBSIDIAN, Blocks.DEEPSLATE,
                Blocks.AMETHYST_BLOCK, Blocks.WHITE_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS_PANE, Blocks.TINTED_GLASS,
                Blocks.ACACIA_PLANKS, Blocks.CHERRY_STAIRS, Blocks.BAMBOO_SLAB, Blocks.STONE_SLAB}) {
            BlockState state = block.defaultBlockState();
            float raw = state.getDestroySpeed(helper.getLevel(), pos);
            helper.assertValueEqual(MiningProgress.effectiveHardness(state, raw), raw,
                    "An unmapped modern block retains its registered hardness: " + state);
            helper.assertValueEqual(SourceBlockHardness.effectiveHardness(state, 17.25F), 17.25F,
                    "No source material value is invented for a modern block: " + state);
        }
        for (BlockState state : new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.SNOW.defaultBlockState(), Blocks.VINE.defaultBlockState()}) {
            helper.assertValueEqual(MiningProgress.effectiveHardness(state, -1F), -1F, "An unbreakable input wins over any mapping");
            helper.assertValueEqual(SourceBlockHardness.effectiveHardness(state, -1F), -1F, "Source mapping retains negative hardness");
        }
        helper.assertValueEqual(SourceBlockHardness.effectiveHardness("example", "stone", false, 19.25F), 19.25F,
                "A foreign block with the exact vanilla path cannot inherit a source hardness");
        helper.assertValueEqual(SourceBlockHardness.effectiveHardness("example", "oak_slab", true, 19.25F), 19.25F,
                "A foreign slab with the exact vanilla path cannot inherit a source hardness");
        int foreign = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")) continue;
            helper.assertValueEqual(SourceBlockHardness.effectiveHardness(block.defaultBlockState(), 19.25F), 19.25F,
                    "Source vanilla mappings do not rewrite another namespace: " + BuiltInRegistries.BLOCK.getKey(block));
            foreign++;
        }
        helper.assertTrue(foreign > 0, "The namespace control actually examines registered non-vanilla blocks");
        helper.succeed();
    }

    private static void assertHardness(GameTestHelper helper, BlockState state, float expected) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertValueEqual(MiningProgress.effectiveHardness(state, state.getDestroySpeed(helper.getLevel(), pos)), expected,
                "Actual effective hardness matches the source state vector: " + state);
    }

    private static void auditState(GameTestHelper helper, BlockState state, float expected, String variant, JsonArray rows) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        float raw = state.getDestroySpeed(helper.getLevel(), pos);
        float blast = state.getBlock().getExplosionResistance();
        float actual = MiningProgress.effectiveHardness(state, raw);
        helper.assertValueEqual(actual, expected, "The full mapping reaches the actual mining entry: " + state);
        helper.assertValueEqual(state.getDestroySpeed(helper.getLevel(), pos), raw, "Mining leaves registered hardness unchanged");
        helper.assertValueEqual(state.getBlock().getExplosionResistance(), blast, "Mining leaves registered blast resistance unchanged");
        JsonObject row = new JsonObject();
        row.addProperty("id", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        row.addProperty("state", variant);
        row.addProperty("raw_hardness", raw);
        row.addProperty("effective_hardness", actual);
        row.addProperty("registered_blast_resistance", blast);
        rows.add(row);
    }

    private static void writeAudit(JsonArray rows) {
        String output = System.getProperty("mite.hardnessAuditOutput");
        if (output == null || output.isBlank()) return;
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("passed", true);
        report.addProperty("mapped_full_blocks", SourceBlockHardness.registeredHardness().size());
        report.addProperty("mapped_slab_blocks", SourceBlockHardness.slabHardness().size());
        report.addProperty("checked_states", rows.size());
        int changed = 0;
        for (var row : rows)
            if (Float.compare(row.getAsJsonObject().get("raw_hardness").getAsFloat(), row.getAsJsonObject().get("effective_hardness").getAsFloat()) != 0) changed++;
        report.addProperty("states_with_changed_hardness", changed);
        report.add("states", rows);
        try {
            Path path = Path.of(output).toAbsolutePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-hardness"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(20);
            player.setOnGround(true);
            return new PlayerSession(player, channel);
        } catch (RuntimeException | Error failure) {
            ServerPlayer registered = player.server.getPlayerList().getPlayer(player.getUUID());
            if (registered != null) helper.getLevel().getServer().getPlayerList().remove(registered);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record WearCase(BlockState state, Item tool, int wear) {}

    private record PlayerSession(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try {
                ServerPlayer registered = player.server.getPlayerList().getPlayer(player.getUUID());
                if (registered != null) player.server.getPlayerList().remove(registered);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}
