package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.worldgen.MossyDungeonLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises native room generation and the server-side item use. @author NingMeng123 */
@GameTestHolder("mite_mossy_dungeon_eye")
@PrefixGameTestTemplate(false)
public final class MossyDungeonEyeGameTests {
    @GameTest(template = "empty")
    public static void signalsToTheNaturalRoomAndConsumesOneEye(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(8, 12, 8));
        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-4, -2, -4), target.offset(4, 4, 4))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int x = 3; x <= 4; x++) {
            level.setBlock(target.offset(x, 0, 0), Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(target.offset(x, 1, 0), Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        helper.assertTrue(Feature.MONSTER_ROOM.place(NoneFeatureConfiguration.INSTANCE, level,
                level.getChunkSource().getGenerator(), RandomSource.create(196), target), "Native room generates at the cave entrance");
        var spawner = (SpawnerBlockEntity) level.getBlockEntity(target);
        var saved = spawner.saveWithFullMetadata(level.registryAccess());
        var reloaded = BlockEntity.loadStatic(target, level.getBlockState(target), saved, level.registryAccess());
        helper.assertTrue(reloaded instanceof SpawnerBlockEntity persisted && MossyDungeonLocator.isGeneratedRoom(persisted),
                "World generation records provenance and persists it to the spawner NBT");

        BlockPos origin = target.west(7);
        level.setBlock(origin.east(2), Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(origin.east(), Blocks.TRIAL_SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertValueEqual(MossyDungeonLocator.findNearest(level, origin), target, "Unrelated nearby spawners are ignored");

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        var stack = new ItemStack(MiteItems.MOSSY_DUNGEON_EYE.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try (var capture = new SpawnedEntityCapture(level, entity -> entity instanceof EyeOfEnder)) {
            stack.use(level, player, InteractionHand.MAIN_HAND);
            var eyes = capture.live(EyeOfEnder.class);
            helper.assertValueEqual(stack.getCount(), 1, "The thrown eye costs one item in survival");
            helper.assertTrue(eyes.size() == 1 && eyes.getFirst().getItem().is(MiteItems.MOSSY_DUNGEON_EYE.get()),
                    "The flying eye carries the correct item and model");
            EyeOfEnder eye = eyes.getFirst();
            eye.tick();
            helper.assertTrue(eye.getDeltaMovement().x > 0.0, "The eye flies east toward the natural dungeon");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyRoomIsFoundWithoutAChestAndMissingTargetKeepsTheEye(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(210, 100, 0));
        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-2, -1, -2), target.offset(2, -1, 2))) {
            level.setBlock(pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(target, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertValueEqual(MossyDungeonLocator.findNearest(level, target.above()), target,
                "The legacy floor still identifies a room with a looted or missing chest");
        PlayerPlacedBlocks.set(level, target, true);
        helper.assertTrue(MossyDungeonLocator.findNearest(level, target.above()) == null, "Player-placed spawners cannot be located");

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(target.getX(), target.getY() + 1, target.getZ());
        var stack = new ItemStack(MiteItems.MOSSY_DUNGEON_EYE.get());
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        try (var capture = new SpawnedEntityCapture(level, entity -> entity instanceof EyeOfEnder)) {
            var result = stack.use(level, player, InteractionHand.OFF_HAND);
            helper.assertTrue(!result.getResult().consumesAction() && stack.getCount() == 1 && capture.live(EyeOfEnder.class).isEmpty(),
                    "A failed search does not throw or spend the held eye");
        }
        helper.succeed();
    }
}
