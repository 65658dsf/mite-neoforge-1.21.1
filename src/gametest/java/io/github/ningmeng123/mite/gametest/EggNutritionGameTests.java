package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.EggThrowControl;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import io.github.ningmeng123.mite.survival.NutritionState;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native egg food use and the authoritative Ctrl-throw transaction. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class EggNutritionGameTests {
    @GameTest(template = "empty")
    public static void defaultEggUseConsumesOneEggAfterThirtyTwoTicksAndAddsOneSatiationThreeNutrition(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EGG, 3));
            InteractionResult started = f.player.gameMode.useItem(f.player, helper.getLevel(),
                    f.player.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(started.consumesAction() && f.player.isUsingItem(), "Default egg use starts the real food interaction");
            helper.assertValueEqual(f.player.getUseItemRemainingTicks(), 32, "R196 raw egg uses exactly 32 production ticks");
            f.tick(31);
            f.assertEggs(InteractionHand.MAIN_HAND, 3);
            f.assertResources(0, 0);
            helper.assertTrue(f.player.isUsingItem(), "No consumption or food benefit is granted before completion");
            f.tick(1);
            f.assertEggs(InteractionHand.MAIN_HAND, 2);
            f.assertResources(1, 3);
            helper.assertFalse(f.player.isUsingItem(), "The completed bite stops the food animation");
            f.assertThrown(0);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitThrowConsumesOneEggEvenWhenFullOrNauseousWithoutAddingFood(GameTestHelper helper) {
        for (boolean nauseous : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(helper)) {
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EGG, 3));
                f.resources(6, 6);
                if (nauseous) f.player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 0));
                helper.assertFalse(FoodConsumption.canIngest(f.player, f.player.getMainHandItem()), "Full or nauseous players cannot eat the raw egg");
                f.player.gameMode.useItem(f.player, helper.getLevel(), f.player.getMainHandItem(), InteractionHand.MAIN_HAND);
                helper.assertFalse(f.player.isUsingItem(), "The ordinary food request remains rejected");
                f.assertEggs(InteractionHand.MAIN_HAND, 3);
                f.assertThrown(0);
                helper.assertTrue(EggThrowControl.throwEgg(f.player, InteractionHand.MAIN_HAND).consumesAction(),
                        "The explicit throw independently authorizes the native egg projectile");
                f.assertEggs(InteractionHand.MAIN_HAND, 2);
                f.assertResources(6, 6);
                f.assertThrown(1);
                helper.assertFalse(f.player.isUsingItem(), "Throwing never starts eating as a second action");
                helper.assertFalse(EggThrowControl.isThrowing(f.player), "The scoped exception ends immediately after the one throw");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void offhandThrowConsumesOnlyItsEggAndPreservesTheMainHand(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 4));
            f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.EGG, 3));
            helper.assertTrue(EggThrowControl.throwEgg(f.player, InteractionHand.OFF_HAND).consumesAction(), "The requested offhand can throw");
            f.assertEggs(InteractionHand.OFF_HAND, 2);
            helper.assertTrue(f.player.getMainHandItem().is(Items.STICK), "The main-hand item identity is preserved");
            helper.assertValueEqual(f.player.getMainHandItem().getCount(), 4, "The main-hand stack is not consumed");
            f.assertThrown(1);
            f.assertResources(0, 0);
            helper.assertFalse(f.player.isUsingItem(), "An offhand throw cannot start a food animation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidHeldItemCooldownUsingSpectatorAndDeadRequestsCannotProduceAnEgg(GameTestHelper helper) {
        for (int condition = 0; condition < 5; condition++) {
            try (Fixture f = new Fixture(helper)) {
                f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(condition == 0 ? Items.STICK : Items.EGG, 2));
                if (condition == 1) f.player.getCooldowns().addCooldown(Items.EGG, 25);
                if (condition == 2) {
                    f.player.gameMode.useItem(f.player, helper.getLevel(), f.player.getMainHandItem(), InteractionHand.MAIN_HAND);
                    helper.assertTrue(f.player.isUsingItem(), "The rejection fixture is already eating an egg");
                }
                if (condition == 3) f.player.setGameMode(GameType.SPECTATOR);
                if (condition == 4) f.player.setHealth(0F);
                InteractionResult result = EggThrowControl.throwEgg(f.player, InteractionHand.MAIN_HAND);
                helper.assertTrue(result == InteractionResult.PASS, "Server qualification rejects the forged or unavailable throw request");
                helper.assertValueEqual(f.player.getMainHandItem().getCount(), 2, "A rejected request spends no held item");
                f.assertResources(0, 0);
                f.assertThrown(0);
                helper.assertFalse(EggThrowControl.isThrowing(f.player), "Rejected input creates no active throw scope");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledRightClickProducesNothingAndAlwaysClearsTheThrowScope(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EGG, 2));
            int[] inspected = {0};
            Consumer<PlayerInteractEvent.RightClickItem> cancel = event -> {
                if (event.getEntity() == f.player) {
                    inspected[0]++;
                    helper.assertTrue(EggThrowControl.isThrowing(f.player), "The validated request reaches the real NeoForge interaction hook");
                    event.setCancellationResult(InteractionResult.FAIL);
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, cancel);
            try {
                helper.assertTrue(EggThrowControl.throwEgg(f.player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
                        "Native event cancellation is honored instead of spawning a projectile directly");
                helper.assertValueEqual(inspected[0], 1, "One request invokes the native event exactly once");
                f.assertEggs(InteractionHand.MAIN_HAND, 2);
                f.assertThrown(0);
                f.assertResources(0, 0);
                helper.assertFalse(EggThrowControl.isThrowing(f.player), "The finally block clears the scope even when an event rejects use");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(EggThrowControl.throwEgg(f.player, InteractionHand.MAIN_HAND).consumesAction(), "Removing the veto permits one new explicit request");
            f.assertEggs(InteractionHand.MAIN_HAND, 1);
            f.assertThrown(1);
            helper.assertFalse(EggThrowControl.isThrowing(f.player), "Successful use also clears the scope");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void throwRotationUsesTheCurrentCameraAndRejectsNonFiniteValues(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EGG, 3));
            for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                helper.assertTrue(EggThrowControl.throwEgg(f.player, InteractionHand.MAIN_HAND, invalid, 0F) == InteractionResult.PASS,
                        "Nonfinite yaw cannot produce a projectile");
                helper.assertTrue(EggThrowControl.throwEgg(f.player, InteractionHand.MAIN_HAND, 0F, invalid) == InteractionResult.PASS,
                        "Nonfinite pitch cannot produce a projectile");
            }
            f.assertEggs(InteractionHand.MAIN_HAND, 3);
            f.assertThrown(0);
            helper.assertTrue(EggThrowControl.throwEgg(f.player, InteractionHand.MAIN_HAND, 450F, 20F).consumesAction(),
                    "A finite current-camera throw is accepted");
            helper.assertValueEqual(f.player.getYRot(), 90F, "Yaw uses the vanilla wrapped rotation");
            helper.assertValueEqual(f.player.getXRot(), 20F, "Pitch is applied before native projectile creation");
            f.assertEggs(InteractionHand.MAIN_HAND, 2);
            f.assertThrown(1);
            var velocity = f.eggs().getFirst().getDeltaMovement();
            helper.assertTrue(Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)
                    && velocity.x < -1D && velocity.y < 0D, "The native egg moves west and down with the supplied camera angles");
            helper.assertFalse(EggThrowControl.isThrowing(f.player), "No rotation case leaves an active scope");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final SpawnedEntityCapture spawned;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-egg"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            spawned=new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ThrownEgg egg && egg.getOwner()==player);
            player.setNoGravity(true);
            player.setOnGround(true);
            player.removeAllEffects();
            player.getInventory().clearContent();
            NutritionState.set(player, NutritionState.INITIAL);
            resources(0, 0);
        }

        private void resources(int satiation, int nutrition) {
            player.getFoodData().setFoodLevel(satiation);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(nutrition);
        }

        private void assertResources(int satiation, int nutrition) {
            helper.assertValueEqual(player.getFoodData().getFoodLevel(), satiation, "Only eating supplies satiation");
            helper.assertValueEqual(((DualFoodData) player.getFoodData()).mite$getNutrition(), nutrition, "Only eating supplies the independent nutrition reserve");
        }

        private void assertEggs(InteractionHand hand, int count) {
            helper.assertTrue(player.getItemInHand(hand).is(Items.EGG), "The requested hand still contains the expected egg item");
            helper.assertValueEqual(player.getItemInHand(hand).getCount(), count, "One accepted transaction consumes exactly one egg");
        }

        private List<ThrownEgg> eggs() {
            return spawned.live(ThrownEgg.class).stream().filter(egg -> egg.getOwner()==player).toList();
        }

        private void assertThrown(int count) {
            helper.assertValueEqual(eggs().size(), count, "Only accepted Ctrl requests produce owned native egg projectiles");
        }

        private void tick(int count) {
            for (int tick = 0; tick < count; tick++) player.doTick();
        }

        @Override public void close() {
            try {
                player.stopUsingItem();
                eggs().forEach(ThrownEgg::discard);
                player.getInventory().clearContent();
                player.server.getPlayerList().remove(player);
            } finally {
                spawned.close(); channel.finishAndReleaseAll();
            }
        }
    }
}
