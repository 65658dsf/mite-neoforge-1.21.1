package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.Livestock032View;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.world.BucketPlacementIntent;
import io.github.ningmeng123.mite.world.PlacementDelay;
import io.github.ningmeng123.mite.world.TemporaryFluidChanges;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class GameplayBalanceGameTests {
    private GameplayBalanceGameTests() {}

    @GameTest(template = "empty")
    public static void grassFallsWhenItsSupportIsRemovedWithoutPlayerMining(GameTestHelper helper) {
        BlockPos soil = helper.absolutePos(new BlockPos(2, 10, 2));
        helper.getLevel().setBlock(soil.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(soil, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        helper.getLevel().removeBlock(soil.below(), false);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(soil).isAir(), "Unsupported grass falls after a real lower-neighbor update");
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, new AABB(soil).inflate(2),
                    falling -> falling.getBlockState().is(Blocks.GRASS_BLOCK)).isEmpty(), "Collapse preserves grass in a native falling entity");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void firstBlockPacketWaitsSixRealTicksAndRepeatedPacketsDoNotDuplicate(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
        long started = fixture.player.server.overworld().getGameTime();
        fixture.block(1);
        fixture.block(2);
        helper.assertTrue(helper.getLevel().getBlockState(fixture.target).isAir(), "Even the first real block packet cannot place immediately");
        helper.runAfterDelay(5, () -> helper.assertTrue(fixture.player.server.overworld().getGameTime() == started + 5
                && helper.getLevel().getBlockState(fixture.target).isAir(), "Five actual server ticks remain inside the preparation interval"));
        helper.runAfterDelay(6, () -> {
            try {
                helper.assertValueEqual(fixture.player.server.overworld().getGameTime(), started + 6, "The block assertion runs at exactly six elapsed server ticks");
                PlacementDelay.tickPlayer(fixture.player);
                helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.GLASS), "Six real ticks complete the queued native placement");
                helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "Repeated packets schedule only one action and spend one item");
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(template = "empty")
    public static void firstWaterBucketWaitsSixRealTicksAndRetainsPaidSourceIntent(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        PlayerExperience.setTotal(fixture.player, 1000);
        BucketPlacementIntent.receive(fixture.player, InteractionHand.MAIN_HAND, 1, true);
        fixture.player.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, 0F, 90F));
        helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(), "The initial falling-water rescue cannot bypass preparation");
        helper.runAfterDelay(5, () -> helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(), "Water also waits through five real ticks"));
        helper.runAfterDelay(6, () -> {
            try {
                PlacementDelay.tickPlayer(fixture.player);
                helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isSource(), "The paid source appears at the common six-tick deadline");
                helper.assertValueEqual(fixture.player.totalExperience, 900, "The delayed source spends exactly the original cost once");
                helper.assertFalse(TemporaryFluidChanges.get(helper.getLevel()).pending().containsKey(fixture.target.asLong()), "Paid intent survives the delay and creates a permanent source");
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(template = "empty")
    public static void swappingTheHeldStackCancelsPreparedPlacement(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS));
        fixture.block(1);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS));
        helper.runAfterDelay(6, () -> {
            try {
                PlacementDelay.tickPlayer(fixture.player);
                helper.assertTrue(helper.getLevel().getBlockState(fixture.target).isAir(), "A different held stack cannot inherit the old prepared action");
                helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "Cancellation never consumes the replacement item");
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(template = "empty")
    public static void codBucketAlsoWaitsSixTicksAndNormallyPlacesTemporaryWater(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD_BUCKET));
        PlayerExperience.setTotal(fixture.player, 1000);
        fixture.player.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, 0F, 90F));
        helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(), "A fish bucket cannot place immediate water through its separate item class");
        helper.runAfterDelay(5, () -> helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(), "Fish bucket placement remains pending for five real ticks"));
        helper.runAfterDelay(6, () -> {
            try {
                PlacementDelay.tickPlayer(fixture.player);
                helper.assertFalse(helper.getLevel().getFluidState(fixture.target).isEmpty(), "The actual fish bucket places water after preparation");
                helper.assertTrue(TemporaryFluidChanges.get(helper.getLevel()).pending().containsKey(fixture.target.asLong()), "Fish buckets normally produce the same temporary water as ordinary buckets");
                var fish = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cod.class, new AABB(fixture.target).inflate(3));
                helper.assertValueEqual(fish.size(), 1, "The native bucket path releases exactly one cod");
                helper.assertTrue(fixture.player.getMainHandItem().is(Items.BUCKET), "Delayed fish placement returns its empty bucket");
                helper.assertValueEqual(fixture.player.totalExperience, 1000, "An ordinary fish-bucket pour spends no source XP");
                fish.forEach(net.minecraft.world.entity.Entity::discard);
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void everyMobBucketNormallyReleasesOneMobIntoTemporaryWater(GameTestHelper helper) {
        verifyMobBucketSequence(helper, false, 1000, 0);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void everyMobBucketControlUsePaysForSourceAndStillReleasesOneMob(GameTestHelper helper) {
        verifyMobBucketSequence(helper, true, 1000, 0);
    }

    @GameTest(template = "empty")
    public static void axolotlBucketControlWithInsufficientExperienceFallsBackToTemporaryWater(GameTestHelper helper) {
        verifyMobBucketSequence(helper, true, 99, 5);
    }

    private static void verifyMobBucketSequence(GameTestHelper helper, boolean control, int experience, int index) {
        var items = new net.minecraft.world.item.Item[]{Items.COD_BUCKET, Items.SALMON_BUCKET, Items.PUFFERFISH_BUCKET,
                Items.TROPICAL_FISH_BUCKET, Items.TADPOLE_BUCKET, Items.AXOLOTL_BUCKET};
        var types = new EntityType<?>[]{EntityType.COD, EntityType.SALMON, EntityType.PUFFERFISH,
                EntityType.TROPICAL_FISH, EntityType.TADPOLE, EntityType.AXOLOTL};
        if (index == items.length) {
            helper.succeed();
            return;
        }
        Fixture fixture = new Fixture(helper);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(items[index]));
        PlayerExperience.setTotal(fixture.player, experience);
        BucketPlacementIntent.receive(fixture.player, InteractionHand.MAIN_HAND, 1, control);
        fixture.player.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, 0F, 90F));
        fixture.player.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 2, 0F, 90F));
        helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(), "Every mob bucket waits before creating water: " + items[index]);
        helper.runAfterDelay(5, () -> helper.assertTrue(helper.getLevel().getFluidState(fixture.target).isEmpty(), "Mob-bucket water cannot bypass the first five ticks: " + items[index]));
        helper.runAfterDelay(6, () -> {
            try {
                PlacementDelay.tickPlayer(fixture.player);
                boolean source = control && experience >= 100;
                helper.assertFalse(helper.getLevel().getFluidState(fixture.target).isEmpty(), "Native bucket use places its water after preparation: " + items[index]);
                helper.assertValueEqual(TemporaryFluidChanges.get(helper.getLevel()).pending().containsKey(fixture.target.asLong()), !source,
                        "Only an affordable Ctrl request creates permanent water: " + items[index]);
                helper.assertValueEqual(fixture.player.totalExperience, experience - (source ? 100 : 0), "Mob buckets use the ordinary source payment rules: " + items[index]);
                var mobs = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(fixture.target).inflate(3), mob -> mob.getType() == types[index]);
                helper.assertValueEqual(mobs.size(), 1, "Repeated packets release exactly one matching bucket mob: " + items[index]);
                helper.assertTrue(fixture.player.getMainHandItem().is(Items.BUCKET) && fixture.player.getMainHandItem().getCount() == 1,
                        "One completed mob release returns one empty bucket: " + items[index]);
                mobs.forEach(net.minecraft.world.entity.Entity::discard);
            } finally { fixture.close(); }
            verifyMobBucketSequence(helper, control, experience, index + 1);
        });
    }

    @GameTest(template = "empty")
    public static void leavingReachCancelsPreparedBlockPlacement(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS));
        fixture.block(1);
        fixture.player.setPos(fixture.player.position().add(20D, 0D, 0D));
        helper.runAfterDelay(6, () -> {
            try {
                PlacementDelay.tickPlayer(fixture.player);
                helper.assertTrue(helper.getLevel().getBlockState(fixture.target).isAir(), "An old prepared target cannot be placed after leaving its interaction range");
                helper.assertValueEqual(fixture.player.getMainHandItem().getCount(), 1, "Distance cancellation preserves the held block");
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(template = "empty")
    public static void powderSnowBucketUsesTheSameBlockPlacementDelay(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POWDER_SNOW_BUCKET));
        long started = fixture.player.server.overworld().getGameTime();
        fixture.block(1);
        helper.assertTrue(helper.getLevel().getBlockState(fixture.target).isAir(), "The solid bucket block-item path also waits before placing");
        helper.runAfterDelay(6, () -> {
            try {
                helper.assertValueEqual(fixture.player.server.overworld().getGameTime(), started + 6, "The powder-snow assertion runs at exactly six elapsed server ticks");
                PlacementDelay.tickPlayer(fixture.player);
                helper.assertTrue(helper.getLevel().getBlockState(fixture.target).is(Blocks.POWDER_SNOW), "Powder snow appears only at the common six-tick deadline");
                helper.assertTrue(fixture.player.getMainHandItem().is(Items.BUCKET), "The completed snow placement returns one empty vessel");
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(template = "empty")
    public static void boneMealCannotGrowOrdinaryCherryOrMangroveSaplings(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(2, 3, 2));
        for (var sapling : new net.minecraft.world.level.block.Block[]{Blocks.OAK_SAPLING, Blocks.CHERRY_SAPLING, Blocks.MANGROVE_PROPAGULE}) {
            helper.getLevel().setBlock(target.below(), Blocks.DIRT.defaultBlockState(), 2);
            var state = sapling.defaultBlockState();
            helper.getLevel().setBlock(target, state, 2);
            var boneMeal = new ItemStack(Items.BONE_MEAL, 2);
            helper.assertFalse(BoneMealItem.applyBonemeal(boneMeal, helper.getLevel(), target, null), "The shared player/dispenser bone-meal path refuses " + sapling);
            helper.assertTrue(helper.getLevel().getBlockState(target).equals(state) && boneMeal.getCount() == 2, "Refusal preserves sapling stage and bone meal");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recoveryRequiresExactlyThreeHealthyMinutesAndRestartsAfterRelapse(GameTestHelper helper) {
        var cow = EntityType.COW.create(helper.getLevel());
        Livestock032.needs(cow, new LivestockRules032.Needs(.1F, 1F, 1F));
        Livestock032.needs(cow, new LivestockRules032.Needs(1F, 1F, 1F));
        for (int tick = 0; tick < LivestockRules032.RECOVERY_TICKS - 1; tick++) Livestock032.tickRecovery(cow);
        helper.assertTrue(!Livestock032.well(cow) && ((Livestock032View)cow).mite$recoveryTicks() == 1, "3599 healthy ticks leave one final sick tick");
        Livestock032.tickRecovery(cow);
        helper.assertTrue(Livestock032.well(cow), "The 3600th healthy tick completes recovery");
        Livestock032.needs(cow, new LivestockRules032.Needs(1F, .1F, 1F));
        Livestock032.needs(cow, new LivestockRules032.Needs(1F, 1F, 1F));
        for (int tick = 0; tick < 100; tick++) Livestock032.tickRecovery(cow);
        CompoundTag saved = new CompoundTag();
        cow.saveWithoutId(saved);
        var restored = EntityType.COW.create(helper.getLevel());
        restored.load(saved);
        helper.assertValueEqual(Livestock032.state(restored).getInt("recovery_ticks"), 3500, "Saving and loading retains recovery progress");
        Livestock032.needs(restored, new LivestockRules032.Needs(1F, 1F, .1F));
        helper.assertValueEqual(Livestock032.state(restored).getInt("recovery_ticks"), 3600, "A new deficiency restarts the full recovery interval");
        cow.discard(); restored.discard(); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernRabbitBreedingAlsoUsesR196Cooldown(GameTestHelper helper) {
        var first = EntityType.RABBIT.create(helper.getLevel());
        var second = EntityType.RABBIT.create(helper.getLevel());
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
        first.setPos(position);
        second.setPos(position);
        first.spawnChildFromBreeding(helper.getLevel(), second);
        helper.assertTrue(first.getAge() == 16000 && second.getAge() == 16000, "Both modern parents receive the source nk.setGrowingAgeAfterBreeding interval");
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Rabbit.class, new AABB(first.blockPosition()).inflate(2), rabbit -> rabbit.isBaby()).forEach(net.minecraft.world.entity.Entity::discard);
        first.discard(); second.discard(); helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breedingCooldownCountsThroughSixThousandAndEventuallyReachesZero(GameTestHelper helper) {
        for (var type : new EntityType<?>[]{EntityType.COW, EntityType.RABBIT, EntityType.TURTLE}) {
            var animal = (net.minecraft.world.entity.animal.Animal)type.create(helper.getLevel());
            animal.setNoAi(true);
            animal.setNoGravity(true);
            animal.setAge(6001);
            animal.aiStep();
            helper.assertValueEqual(animal.getAge(), 6000, "A running cooldown decrements through 6000 without restarting for " + type);
            for (int tick = 0; tick < 6000; tick++) animal.aiStep();
            helper.assertValueEqual(animal.getAge(), 0, "The actual age tick eventually completes the cooldown for " + type);
            animal.discard();
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos target;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-prepare"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            channel.runPendingTasks();
            int initialTeleport = -1;
            for (Object packet : channel.outboundMessages())
                if (packet instanceof ClientboundPlayerPositionPacket position) initialTeleport = position.getId();
            helper.assertTrue(initialTeleport >= 0, "The real login emits its initial position packet before interaction testing");
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(initialTeleport));
            player.setGameMode(GameType.SURVIVAL);
            target = helper.absolutePos(new BlockPos(2, 8, 2));
            helper.getLevel().setBlock(target.below(), Blocks.STONE.defaultBlockState(), 2);
            helper.getLevel().setBlock(target, Blocks.AIR.defaultBlockState(), 2);
            player.moveTo(target.getX() + .5, target.getY() + 2, target.getZ() + .5, 0F, 90F);
            player.setNoGravity(true);
            player.setOnGround(false);
            player.getInventory().clearContent();
        }

        private void block(int sequence) {
            player.setPos(target.getX() + 1.8D, target.getY(), target.getZ() + .5D);
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(target), Direction.UP, target.below(), false);
            helper.assertTrue(SourcePlayerReach.canReachBlock(player, hit.getBlockPos(), InteractionHand.MAIN_HAND),
                    "The real block packet starts within the existing MITE interaction range");
            player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, sequence));
        }

        @Override public void close() {
            PlacementDelay.clear(player);
            BucketPlacementIntent.clear(player);
            TemporaryFluidChanges.get(helper.getLevel()).cancel(target);
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
