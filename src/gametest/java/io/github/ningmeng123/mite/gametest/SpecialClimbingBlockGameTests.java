package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native climb collision, movement and modern-block controls. @author NingMeng123 */
@GameTestHolder("mite_special_blocks")
@PrefixGameTestTemplate(false)
public final class SpecialClimbingBlockGameTests {
    private SpecialClimbingBlockGameTests() {}

    private static BlockState ladder() {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
    }

    private static BlockState vine() {
        return Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true);
    }

    private static BlockPos prepare(GameTestHelper helper, BlockState state) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 15, 4));
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 3, 2)))
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(origin.south(), Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(origin.south().above(), Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(origin, state, 2);
        helper.getLevel().setBlock(origin.above(), state, 2);
        return origin;
    }

    private static Player player(GameTestHelper helper, BlockPos origin) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setNoGravity(true);
        reset(player, origin);
        return player;
    }

    private static void reset(LivingEntity entity, BlockPos origin) {
        boolean ladder = entity.level().getBlockState(origin).is(Blocks.LADDER);
        entity.setPos(origin.getX() + .5D, origin.getY(), origin.getZ() + (ladder ? .5D : .69D));
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setOnGround(false);
        entity.horizontalCollision = false;
        entity.setJumping(false);
    }

    private static double ascend(GameTestHelper helper, LivingEntity entity, BlockPos origin) {
        reset(entity, origin);
        helper.assertTrue(entity.onClimbable(), "The fixture occupies a real climbable block");
        entity.setDeltaMovement(0D, 0D, .3D);
        Vec3 movement = entity.handleRelativeFrictionAndCalculateMovement(Vec3.ZERO, .6F);
        helper.assertTrue(entity.horizontalCollision, "Native movement actually collides with the ladder or its backing wall");
        return movement.y;
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .000001D,
                message + " expected=" + expected + " actual=" + actual);
    }

    @GameTest(template = "empty", templateNamespace = "mite_special_blocks")
    public static void ladderAndVineAscentCombineSpeedAndSlowness(GameTestHelper helper) {
        for (BlockState state : new BlockState[]{ladder(), vine()}) {
            BlockPos origin = prepare(helper, state);
            Player player = player(helper, origin);
            close(helper, ascend(helper, player, origin), .2D, "Ordinary ascent keeps its source baseline");
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400));
            close(helper, ascend(helper, player, origin), .172D, "SlowI uses the source softened climbing penalty");
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
            close(helper, ascend(helper, player, origin), .24D, "SpeedII and SlowI combine before ascent is scaled");
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            close(helper, ascend(helper, player, origin), .28D, "Removing slowness immediately leaves SpeedII ascent");
            player.removeAllEffects();
            close(helper, ascend(helper, player, origin), .2D, "Effect removal immediately restores ordinary ascent");
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_special_blocks")
    public static void strongParalysisAndFreeActionUseSourceClimbingResistance(GameTestHelper helper) {
        for (BlockState state : new BlockState[]{ladder(), vine()}) {
            BlockPos origin = prepare(helper, state);
            Player player = player(helper, origin);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 5));
            close(helper, ascend(helper, player, origin), .06D, "Complete paralysis retains thirty percent ascent");
            ItemStack leggings = new ItemStack(Items.IRON_LEGGINGS);
            leggings.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(R196Enchantments.key("free_action")), 4);
            player.setItemSlot(EquipmentSlot.LEGS, leggings);
            close(helper, ascend(helper, player, origin), .1664D, "FreeActionIV resists eighty percent of SlowVI once");
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            close(helper, ascend(helper, player, origin), .06D, "Unequipping immediately removes climbing resistance");
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_special_blocks")
    public static void ladderAndVineApplyHorizontalGripAfterNativeClamps(GameTestHelper helper) {
        for (BlockState state : new BlockState[]{ladder(), vine()}) {
            BlockPos origin = prepare(helper, state);
            Player player = player(helper, origin);
            double x = player.getX();
            double z = player.getZ();
            player.setDeltaMovement(.3D, -.6D, -.3D);
            Vec3 movement = player.handleRelativeFrictionAndCalculateMovement(Vec3.ZERO, .6F);
            close(helper, player.getX() - x, .12D, "Real horizontal displacement includes source eighty percent grip");
            close(helper, player.getZ() - z, -.12D, "Both horizontal axes receive source grip");
            close(helper, movement.y, -.15D, "Native downward speed cap is preserved");
            reset(player, origin);
            player.setShiftKeyDown(true);
            player.setDeltaMovement(0D, -.6D, 0D);
            movement = player.handleRelativeFrictionAndCalculateMovement(Vec3.ZERO, .6F);
            close(helper, movement.y, 0D, "Sneaking still prevents sliding down the climbable block");
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_special_blocks")
    public static void nativeJumpClimbingStillUsesTheSourceAscentSpeed(GameTestHelper helper) {
        BlockPos origin = prepare(helper, ladder());
        Player player = player(helper, origin);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400));
        player.setJumping(true);
        Vec3 movement = player.handleRelativeFrictionAndCalculateMovement(Vec3.ZERO, .6F);
        helper.assertTrue(!player.horizontalCollision, "Jump ascent exercises the native collision-free climbing branch");
        close(helper, movement.y, .24D, "Native jump ascent remains available with source speed scaling");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_special_blocks")
    public static void upperBodyWebAlsoContributesToSourceClimbingSpeed(GameTestHelper helper) {
        for (BlockState state : new BlockState[]{ladder(), vine()}) {
            BlockPos origin = prepare(helper, state);
            helper.getLevel().setBlock(origin.above(), Blocks.COBWEB.defaultBlockState(), 2);
            Player player = player(helper, origin);
            close(helper, ascend(helper, player, origin), .095D, "Upper-body web slowdown contributes to ladder and vine ascent");
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_special_blocks")
    public static void modernOnlySpeciesRetainTheirNativeClimbingMotion(GameTestHelper helper) {
        BlockPos origin = prepare(helper, ladder());
        var husk = EntityType.HUSK.create(helper.getLevel());
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 5));
        close(helper, ascend(helper, husk, origin), .2D, "Modern-only husks retain the native fixed ascent");
        reset(husk, origin);
        husk.setDeltaMovement(.3D, -.6D, -.3D);
        Vec3 movement = husk.handleRelativeFrictionAndCalculateMovement(Vec3.ZERO, .6F);
        close(helper, movement.x, .15D, "Modern-only species retain native horizontal climbing clamps");
        close(helper, movement.z, -.15D, "Modern-only species retain both native horizontal axes");
        husk.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_special_blocks")
    public static void powderSnowKeepsItsNativeLeatherBootJumpAscent(GameTestHelper helper) {
        BlockPos origin = prepare(helper, Blocks.POWDER_SNOW.defaultBlockState());
        Player player = player(helper, origin);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 5));
        player.setJumping(true);
        Vec3 movement = player.handleRelativeFrictionAndCalculateMovement(Vec3.ZERO, .6F);
        helper.assertTrue(!player.onClimbable(), "Powder snow uses the native extra climbing branch");
        close(helper, movement.y, .2D, "Modern powder-snow ascent retains its native speed");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_special_blocks")
    public static void scaffoldingKeepsNativeAscentAndHorizontalClamps(GameTestHelper helper) {
        BlockState scaffolding = Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0);
        BlockPos origin = prepare(helper, scaffolding);
        Player player = player(helper, origin);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 5));
        player.setJumping(true);
        Vec3 movement = player.handleRelativeFrictionAndCalculateMovement(Vec3.ZERO, .6F);
        helper.assertTrue(player.onClimbable(), "The player occupies native climbable scaffolding");
        close(helper, movement.y, .2D, "Scaffolding retains its native fixed ascent");
        reset(player, origin);
        player.setDeltaMovement(.3D, -.6D, -.3D);
        movement = player.handleRelativeFrictionAndCalculateMovement(Vec3.ZERO, .6F);
        close(helper, movement.x, .15D, "Scaffolding retains its native horizontal clamp");
        close(helper, movement.z, -.15D, "Scaffolding retains its native second horizontal axis");
        player.discard();
        helper.succeed();
    }
}
