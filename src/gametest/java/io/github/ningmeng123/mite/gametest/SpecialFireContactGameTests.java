package io.github.ningmeng123.mite.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual vanilla contact entry points preserve source ignition timing and modern duration attributes. @author NingMeng123 */
@GameTestHolder("mite_special_blocks")
@PrefixGameTestTemplate(false)
public final class SpecialFireContactGameTests {
    private SpecialFireContactGameTests() {}

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void ordinaryFireOnlyDamagesDroppedItemsEveryTenEntityTicks(GameTestHelper helper) {
        ItemEntity item = item(helper);
        try {
            for (int tick = 1; tick < 50; tick++) {
                item.tickCount = tick;
                contact(helper, Blocks.FIRE.defaultBlockState(), item);
                helper.assertValueEqual(itemHealth(item), 5 - tick / 10,
                        "The actual dropped item's saved health changes only on ten-tick boundaries");
                helper.assertTrue(!item.isRemoved(), "Four accepted contact hits keep the dropped item alive");
            }
            item.tickCount = 50;
            contact(helper, Blocks.FIRE.defaultBlockState(), item);
            helper.assertTrue(item.isRemoved(), "The fifth accepted native fire hit removes the dropped item");
        } finally { item.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void ordinaryFireKeepsLivingContactDamageBetweenTenTickBoundaries(GameTestHelper helper) {
        Player player = player(helper);
        try {
            for (int tick : new int[]{1, 2, 9, 10, 11}) {
                player.tickCount = tick;
                player.invulnerableTime = 0;
                player.setHealth(player.getMaxHealth());
                float before = player.getHealth();
                contact(helper, Blocks.FIRE.defaultBlockState(), player);
                helper.assertValueEqual(before - player.getHealth(), 1F,
                        "Living contact damage still reaches the native chain on every callback");
            }
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void soulFireKeepsDroppedItemDamageOnEveryContact(GameTestHelper helper) {
        ItemEntity item = item(helper);
        try {
            item.tickCount = 1;
            contact(helper, Blocks.SOUL_FIRE.defaultBlockState(), item);
            helper.assertValueEqual(itemHealth(item), 3, "Soul fire deals its native two points on tick one");
            item.tickCount = 2;
            contact(helper, Blocks.SOUL_FIRE.defaultBlockState(), item);
            helper.assertValueEqual(itemHealth(item), 1, "Soul fire deals its native two points on tick two");
            item.tickCount = 3;
            contact(helper, Blocks.SOUL_FIRE.defaultBlockState(), item);
            helper.assertTrue(item.isRemoved(), "The third native soul-fire contact removes the item");
        } finally { item.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void ordinaryFireIgnitesAPlayerOnTheTenthDryContactFor170Ticks(GameTestHelper helper) {
        Player player = player(helper);
        try {
            helper.assertValueEqual(player.getRemainingFireTicks(), -10,
                    "A freshly constructed player has the source ten-contact ignition delay");
            for (int tick = 1; tick < 10; tick++) {
                contact(helper, Blocks.FIRE.defaultBlockState(), player);
                helper.assertValueEqual(player.getRemainingFireTicks(), tick - 10,
                        "Dry fire contact advances the native counter without early ignition");
            }
            contact(helper, Blocks.FIRE.defaultBlockState(), player);
            helper.assertValueEqual(player.getRemainingFireTicks(), 170,
                    "The tenth contact requests the source 8 * 20 + 10 tick duration");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void ordinaryFireKeepsOnePointOfNativeContactDamage(GameTestHelper helper) {
        Player player = player(helper);
        try {
            float before = player.getHealth();
            contact(helper, Blocks.FIRE.defaultBlockState(), player);
            helper.assertValueEqual(before - player.getHealth(), 1F,
                    "Unarmored ordinary-fire contact retains its one-point base damage");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void soulFireKeepsItsNative160TickDurationAndTwoPointDamage(GameTestHelper helper) {
        Player player = player(helper);
        try {
            player.setRemainingFireTicks(-1);
            float before = player.getHealth();
            contact(helper, Blocks.SOUL_FIRE.defaultBlockState(), player);
            helper.assertValueEqual(player.getRemainingFireTicks(), 160,
                    "The modern soul-fire duration stays eight seconds");
            helper.assertValueEqual(before - player.getHealth(), 2F,
                    "The modern soul-fire damage stays two points");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void lavaContactRequests310TicksAndKeepsFourPointDamage(GameTestHelper helper) {
        Player player = player(helper);
        try {
            float before = player.getHealth();
            player.lavaHurt();
            helper.assertValueEqual(player.getRemainingFireTicks(), 310,
                    "The real lava contact path requests 15 * 20 + 10 ticks");
            helper.assertValueEqual(before - player.getHealth(), 4F,
                    "The real unarmored lava contact retains four-point base damage");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void contactDurationsStillPassThroughTheNativeBurningTimeAttribute(GameTestHelper helper) {
        Player player = player(helper);
        try {
            player.getAttribute(Attributes.BURNING_TIME).setBaseValue(.5D);
            player.setRemainingFireTicks(-1);
            contact(helper, Blocks.FIRE.defaultBlockState(), player);
            helper.assertValueEqual(player.getRemainingFireTicks(), 85,
                    "Ordinary fire applies the native duration attribute to the full source request");
            player.setRemainingFireTicks(-1);
            player.invulnerableTime = 0;
            player.setHealth(player.getMaxHealth());
            player.lavaHurt();
            helper.assertValueEqual(player.getRemainingFireTicks(), 155,
                    "Lava applies the native duration attribute to the full source request");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite_special_blocks", template = "empty")
    public static void lavaContactKeepsLongerExistingBurning(GameTestHelper helper) {
        Player player = player(helper);
        try {
            player.setRemainingFireTicks(400);
            player.lavaHurt();
            helper.assertValueEqual(player.getRemainingFireTicks(), 400,
                    "The normal ignition method preserves a longer already-running duration");
        } finally { player.discard(); }
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(new BlockPos(3, 3, 3)).getCenter());
        player.setNoGravity(true);
        return player;
    }

    private static ItemEntity item(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        return new ItemEntity(helper.getLevel(), pos.getX() + .5D, pos.getY(), pos.getZ() + .5D,
                new ItemStack(Items.PAPER));
    }

    private static int itemHealth(ItemEntity item) {
        return item.saveWithoutId(new CompoundTag()).getShort("Health");
    }

    private static void contact(GameTestHelper helper, BlockState fire, Entity entity) {
        fire.entityInside(helper.getLevel(), entity.blockPosition(), entity);
    }
}
