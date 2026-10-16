package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native bush callbacks and player health transactions for both berry species. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class BerryThornGameTests {
    private BerryThornGameTests() {}

    @GameTest(template = "empty")
    public static void bothBerrySpeciesHurtMovingSurvivalPlayersAtEveryThornBearingAge(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            for (Block block : bushes()) for (int age = 1; age <= 3; age++) {
                reset(helper, session.player(), .1D, 0D);
                close(helper, contact(helper, state(block, age), session.player()), 1D,
                        "The real bush callback deals one point to an unarmored moving player: " + block + " age " + age);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void seedlingsNeverHurtButBothSpeciesStillSlowActualMovement(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            for (Block block : bushes()) {
                var player = session.player();
                reset(helper, player, .1D, 0D);
                close(helper, contact(helper, state(block, 0), player), 0D, "Newly planted seedlings do not acquire premature thorns");
                double before = player.getX();
                player.move(MoverType.SELF, new Vec3(.25D, 0D, 0D));
                close(helper, player.getX() - before, .2D, "The inherited native stuck vector really slows movement");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void standingStillVerticalMotionAndSubThresholdJitterNeverCauseThornDamage(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            for (Block block : bushes()) {
                var player = session.player();
                for (double movement : new double[]{0D, .002D}) {
                    reset(helper, player, movement, 0D);
                    player.yOld = player.getY() - .5D;
                    close(helper, contact(helper, state(block, 3), player), 0D,
                            "Native thorns require meaningful horizontal motion rather than standing, vertical motion or jitter");
                }
                reset(helper, player, 0D, .1D);
                close(helper, contact(helper, state(block, 3), player), 1D, "Z-axis motion is treated the same as X-axis motion");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorAndProtectionReduceThornsWithoutSilentlyDeletingAllDamage(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            player.doTick();
            for (Block block : bushes()) {
                reset(helper, player, .1D, 0D);
                float damage = contact(helper, state(block, 3), player);
                helper.assertTrue(damage > 0F && damage < 1F, "Modern physical armor reduces thorns while retaining nonzero health loss");
            }
            player.getItemBySlot(EquipmentSlot.CHEST).enchant(helper.getLevel().registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION), 4);
            player.doTick();
            for (Block block : bushes()) {
                reset(helper, player, .1D, 0D);
                float damage = contact(helper, state(block, 3), player);
                helper.assertTrue(damage > 0F && damage < .42F, "Native Protection applies after armor without an invented immunity");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foxesAndBeesRetainBothThornAndSlowdownImmunity(GameTestHelper helper) {
        for (EntityType<? extends LivingEntity> type : List.of(EntityType.FOX, EntityType.BEE)) {
            LivingEntity animal = type.create(helper.getLevel());
            helper.assertTrue(animal != null, "The native immune species is registered");
            try {
                for (Block block : bushes()) {
                    reset(helper, animal, .1D, 0D);
                    close(helper, contact(helper, state(block, 3), animal), 0D, "Foxes and bees retain their native thorn immunity");
                    double before = animal.getX();
                    animal.move(MoverType.SELF, new Vec3(.25D, 0D, 0D));
                    close(helper, animal.getX() - before, .25D, "Native immune species also avoid bush slowdown");
                }
            } finally { animal.discard(); }
        }
        helper.succeed();
    }

    private static Block[] bushes() { return new Block[]{Blocks.SWEET_BERRY_BUSH, MiteFoodPlants.BLUEBERRY_BUSH.get()}; }
    private static BlockState state(Block block, int age) { return block.defaultBlockState().setValue(SweetBerryBushBlock.AGE, age); }

    private static float contact(GameTestHelper helper, BlockState state, LivingEntity entity) {
        float before = entity.getHealth();
        state.entityInside(helper.getLevel(), entity.blockPosition(), entity);
        return before - entity.getHealth();
    }

    private static void reset(GameTestHelper helper, LivingEntity entity, double xMotion, double zMotion) {
        entity.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 12, 2))));
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        // Clear a prior native stuck vector through its actual one-shot consumption path.
        entity.move(MoverType.SELF, Vec3.ZERO);
        entity.xOld = entity.getX() - xMotion;
        entity.yOld = entity.getY();
        entity.zOld = entity.getZ() - zMotion;
        entity.invulnerableTime = 0;
        entity.setHealth(entity.getMaxHealth());
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .0001D, message + ": expected " + expected + ", got " + actual);
    }

    private static Session newPlayer(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "berry-thorn-test"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 12, 2))));
            player.setNoGravity(true); player.setOnGround(true);
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            for (int tick = 0; tick < 60; tick++) player.tick();
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll(); throw failure;
        }
    }

    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try { if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
        }
    }
}
