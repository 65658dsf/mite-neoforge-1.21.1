package io.github.ningmeng123.mite.entity.animal;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Measures straight-line ground travel using the native player and animal movement controls. @author NingMeng123 */
@GameTestHolder("mite_animal_flee")
@PrefixGameTestTemplate(false)
public final class AnimalSprintCatchupGameTests {
    private static final int CHASE_TICKS = 105;
    private static final double STARTING_GAP = 1D;

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void ordinarySprintGraduallyCatchesFastFleeingAnimals(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(3, 8, 3));
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        var pig = EntityType.PIG.create(level);
        var horse = EntityType.HORSE.create(level);
        try {
            for (BlockPos position : BlockPos.betweenClosed(start.offset(-1, -1, -1), start.offset(3, 2, 39))) {
                original.put(position.immutable(), level.getBlockState(position));
                level.setBlock(position, (position.getY() < start.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            }
            horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.3375D);
            assertCatchup(helper, start, pig, 1.4F, true);
            assertCatchup(helper, start, horse, 1.2F, false);
            assertCatchup(helper, start, horse, 1.2F, true);
        } finally {
            pig.discard();
            horse.discard();
            original.forEach((position, state) -> level.setBlock(position, state, 2));
        }
        helper.succeed();
    }

    private static void assertCatchup(GameTestHelper helper, BlockPos start, PathfinderMob animal,
            float fleeSpeed, boolean panicking) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            Vec3 playerStart = Vec3.atBottomCenterOf(start);
            // Parallel lanes keep entity pushing out of the distance measurement.
            Vec3 animalStart = playerStart.add(2D, 0D, STARTING_GAP);
            startOnTrack(player, playerStart);
            startOnTrack(animal, animalStart);
            player.setSprinting(true);
            player.setSpeed((float) player.getAttributeValue(Attributes.MOVEMENT_SPEED));
            var flee = new AnimalCommon032Goals.Flee(animal, fleeSpeed, 0F);
            R196AnimalAi032.state(animal).putInt("panic_countdown", panicking ? CHASE_TICKS + 1 : 0);

            for (int tick = 0; tick < CHASE_TICKS; tick++) {
                player.travel(new Vec3(0D, 0D, .98F));
                animal.getMoveControl().setWantedPosition(animalStart.x, animalStart.y, animalStart.z + 37D, flee.movementSpeed());
                animal.getMoveControl().tick();
                animal.travel(new Vec3(animal.xxa * .98F, 0D, animal.zza * .98F));
            }

            double playerDistance = player.getZ() - playerStart.z;
            double animalDistance = animal.getZ() - animalStart.z;
            String description = EntityType.getKey(animal.getType()) + (panicking ? " panic" : " normal escape");
            helper.assertTrue(playerDistance > 20D, "A normal sprint travels along the track: " + playerDistance);
            helper.assertTrue(animalDistance > playerDistance * .90D && player.getZ() > animal.getZ(),
                    description + " remains close in speed while the player closes its head start. Player traveled "
                            + playerDistance + " and animal traveled " + animalDistance);
        } finally {
            player.discard();
        }
    }

    private static void startOnTrack(LivingEntity entity, Vec3 start) {
        entity.moveTo(start.x, start.y, start.z, 0F, 0F);
        entity.setDeltaMovement(0D, -.08D, 0D);
        entity.setOnGround(true);
    }
}
