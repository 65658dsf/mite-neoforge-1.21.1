package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Spider;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Controller and actual ground displacement, independently derived from on.bl/of.bg/nn.a. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SpiderMovementRegressionGameTests {
    private static List<Spider> spiders(GameTestHelper helper) {
        List<Spider> result = new ArrayList<>();
        result.add(EntityType.SPIDER.create(helper.getLevel()));
        result.add(EntityType.CAVE_SPIDER.create(helper.getLevel()));
        for (MobKind kind : List.of(MobKind.DEMON_SPIDER, MobKind.WOOD_SPIDER, MobKind.BLACK_WIDOW_SPIDER, MobKind.PHASE_SPIDER))
            result.add((R196Spider)MiteMobs.type(kind).create(helper.getLevel()));
        return result;
    }
    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .00001D, message + ": " + actual + " vs " + expected);
    }
    private static void prepare(GameTestHelper helper, Spider spider) {
        BlockPos feet = helper.absolutePos(new BlockPos(3, 3, 3));
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, -1, 12)))
            helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        spider.setNoAi(false); spider.setNoGravity(true); spider.setOnGround(true);
        spider.moveTo(feet.getX() + .5D, feet.getY(), feet.getZ() + .5D, 0F, 0F);
        spider.setDeltaMovement(Vec3.ZERO);
    }
    @GameTest(template = "empty")
    public static void realControllerAndGroundTravelUsePointOneAcceleration(GameTestHelper helper) {
        for (Spider spider : spiders(helper)) {
            prepare(helper, spider);
            double source = spider instanceof R196Spider mite && mite.miteKind() != MobKind.DEMON_SPIDER ? .800000011920929D : 1D;
            close(helper, spider.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), source, "Source base attribute remains intact");
            spider.getMoveControl().setWantedPosition(spider.getX(), spider.getY(), spider.getZ() + 10D, 1D);
            spider.getMoveControl().tick();
            close(helper, spider.getSpeed(), .1D, "Modern travel receives the source fixed ground acceleration");
            close(helper, spider.zza, source, "Controller output stays a forward-input magnitude, not another acceleration multiplier");
            double before = spider.getZ();
            spider.travel(new Vec3(0D, 0D, spider.zza * .98F));
            close(helper, spider.getZ() - before, .1D * source * .98D, "Actual unobstructed stone-ground movement matches the independent legacy step");
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void existingSavedSpidersAreCorrectedWithoutMutatingTheirAttributes(GameTestHelper helper) {
        for (Spider spider : spiders(helper)) {
            CompoundTag saved = new CompoundTag(); spider.saveWithoutId(saved);
            spider.load(saved); prepare(helper, spider);
            double base = spider.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            spider.getMoveControl().setWantedPosition(spider.getX(), spider.getY(), spider.getZ() + 10D, .8D);
            spider.getMoveControl().tick();
            close(helper, spider.getSpeed(), .1D, "Loaded pre-fix source attributes no longer become modern acceleration");
            close(helper, spider.zza, base * .8D, "Navigation speed modifiers remain effective");
            spider.stopInPlace();
            close(helper, spider.zza, 0D, "Native stop still clears forward input");
            close(helper, spider.getSpeed(), 0D, "Native stop still clears acceleration");
            close(helper, spider.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), base, "No destructive save migration or repeated attribute scaling");
        }
        helper.succeed();
    }
}
