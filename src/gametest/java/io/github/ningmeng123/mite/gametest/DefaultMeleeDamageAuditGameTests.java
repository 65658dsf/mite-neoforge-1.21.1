package io.github.ningmeng123.mite.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.SourcePlayerCombat;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Diagnostic coverage using ordinary level-zero melee attributes and actual server players. @author NingMeng123 */
@GameTestHolder("mite_damage_audit")
@PrefixGameTestTemplate(false)
public final class DefaultMeleeDamageAuditGameTests {
    private DefaultMeleeDamageAuditGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void defaultPlayerAndMonsterAttacksRetainRealFractionalHealthDamage(GameTestHelper helper) {
        JsonObject report = new JsonObject();
        JsonArray attempts = new JsonArray();
        report.add("attempts", attempts);
        report.addProperty("status", "running");
        try (Session session = session(helper)) {
            ServerPlayer player = session.player();
            Vec3 origin = player.position();
            assertDefaultPlayer(helper, player);
            report.add("initial_player", playerState(player));
            for (EntityType<? extends Animal> type : animalTypes()) {
                auditPlayer(helper, player, origin, type, attempts);
                auditMonster(helper, origin, type, attempts);
            }
            report.addProperty("status", "passed");
            helper.succeed();
        } catch (RuntimeException | Error failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            throw failure;
        } finally {
            write(report);
        }
    }

    private static java.util.List<EntityType<? extends Animal>> animalTypes() {
        return java.util.List.of(EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN);
    }

    private static void auditPlayer(GameTestHelper helper, ServerPlayer player, Vec3 origin,
            EntityType<? extends Animal> type, JsonArray attempts) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(6);
        ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        tickPlayer(player, origin, 20);
        assertDefaultPlayer(helper, player);
        assertFullAttackStrength(helper, player);
        Animal target = animal(helper, type, origin.add(1D, 0D, 0D));
        try {
            JsonObject full = attack(player, target, "player_full_cooldown_empty_hand", attempts);
            assertDamage(helper, full, 1D);
            helper.assertTrue(target.hurtTime > 0 && target.invulnerableTime > 0,
                    "A full-strength fist causes real hurt feedback and immunity for " + type);

            JsonObject immediate = attack(player, target, "player_immediate_second_click", attempts);
            assertDamage(helper, immediate, 0D);
            assertNoNewHurtFeedback(helper, immediate);
            helper.assertFalse(SourcePlayerCombat.weak(player), "Immunity rejection is not a weak strike");

            tickPair(player, target, origin, 20);
            assertFullAttackStrength(helper, player);
            JsonObject recovered = attack(player, target, "player_after_hurt_immunity", attempts);
            assertDamage(helper, recovered, 1D);
        } finally {
            target.discard();
        }

        // Each click resets native attack strength. Keep the default attack speed of four.
        target = animal(helper, type, origin.add(1D, 0D, 0D));
        try {
            player.resetAttackStrengthTicker();
            float initial = target.getHealth();
            int damagedClicks = 0;
            for (int tick = 0; tick < 24; tick++) {
                tickPair(player, target, origin, 1);
                JsonObject click = attack(player, target, "player_continuous_click_" + tick, attempts);
                helper.assertTrue(FoodConsumption.hasEnergy(player), "Repeated punches retain real food energy");
                helper.assertFalse(SourcePlayerCombat.weak(player), "Normal player remains strong while repeatedly punching");
                helper.assertTrue(click.get("health_loss").getAsDouble() >= 0D, "Clicks cannot heal the animal");
                if (click.get("health_loss").getAsDouble() > 0D) damagedClicks++;
                else assertNoNewHurtFeedback(helper, click);
            }
            helper.assertTrue(damagedClicks >= 2 && target.getHealth() < initial,
                    "Default rapid punches produce fractional damage after each immunity window for " + type);
        } finally {
            target.discard();
        }

        target = animal(helper, type, origin.add(1D, 0D, 0D));
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
            tickPair(player, target, origin, 20);
            assertFullAttackStrength(helper, player);
            JsonObject weapon = attack(player, target, "player_full_cooldown_wooden_sword", attempts);
            helper.assertTrue(weapon.get("health_loss").getAsDouble() > 0D, "Ordinary equipped sword damages " + type);
        } finally {
            target.discard();
        }

        target = animal(helper, type, origin.add(1D, 0D, 0D));
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            tickPair(player, target, origin, 1);
            assertDefaultPlayer(helper, player);
            JsonObject switched = attack(player, target, "player_one_tick_after_switching_to_empty_hand", attempts);
            helper.assertTrue(switched.get("health_loss").getAsDouble() > 0D,
                    "A fresh target takes actual fractional damage immediately after unequipping a sword for " + type);
            tickPair(player, target, origin, 20);
            assertFullAttackStrength(helper, player);
            JsonObject full = attack(player, target, "player_full_cooldown_after_weapon_switch", attempts);
            assertDamage(helper, full, 1D);
        } finally {
            target.discard();
        }
    }

    private static void auditMonster(GameTestHelper helper, Vec3 origin,
            EntityType<? extends Animal> type, JsonArray attempts) {
        Zombie monster = EntityType.ZOMBIE.create(helper.getLevel());
        monster.setPos(origin);
        monster.setNoAi(true);
        monster.setNoGravity(true);
        helper.getLevel().addFreshEntity(monster);
        try {
            for (boolean armed : new boolean[] { false, true }) {
                monster.setItemInHand(InteractionHand.MAIN_HAND, armed ? new ItemStack(Items.WOODEN_SWORD) : ItemStack.EMPTY);
                monster.tick();
                monster.setPos(origin);
                monster.setOnGround(true);
                for (int hit = 0; hit < 3; hit++) {
                    Animal target = animal(helper, type, origin.add(1D, 0D, 0D));
                    try {
                        JsonObject attempt = snapshot(target, "monster_" + (armed ? "wooden_sword_" : "empty_hand_") + hit);
                        attempt.addProperty("attacker_attack_damage", monster.getAttributeValue(Attributes.ATTACK_DAMAGE));
                        float before = target.getHealth();
                        boolean accepted = monster.doHurtTarget(target);
                        complete(attempt, target, before);
                        attempt.addProperty("accepted", accepted);
                        attempts.add(attempt);
                        helper.assertTrue(accepted && target.getHealth() < before,
                                "Default monster attack must cause health loss for " + type + ", armed=" + armed);
                        helper.assertTrue(target.hurtTime > 0, "Real monster damage starts hurt feedback");
                        JsonObject immediate = snapshot(target,
                                "monster_immediate_second_click_" + (armed ? "armed" : "unarmed") + hit);
                        before = target.getHealth();
                        boolean immediateAccepted = monster.doHurtTarget(target);
                        complete(immediate, target, before);
                        immediate.addProperty("accepted", immediateAccepted);
                        attempts.add(immediate);
                        helper.assertFalse(immediateAccepted, "Native immunity rejects the same monster's immediate second hit");
                        assertNoNewHurtFeedback(helper, immediate);
                        if (target.isAlive()) {
                            for (int tick = 0; tick < 20; tick++) tickAnimal(target, origin.add(1D, 0D, 0D));
                            attempt = snapshot(target, "monster_repeat_after_immunity_" + (armed ? "armed" : "unarmed") + hit);
                            before = target.getHealth();
                            accepted = monster.doHurtTarget(target);
                            complete(attempt, target, before);
                            attempt.addProperty("accepted", accepted);
                            attempts.add(attempt);
                            helper.assertTrue(accepted && target.getHealth() < before,
                                    "Default monster can damage again after native immunity expires");
                        }
                    } finally {
                        target.discard();
                    }
                }
            }
        } finally {
            monster.discard();
        }
    }

    private static JsonObject attack(ServerPlayer player, Animal target, String scenario, JsonArray attempts) {
        JsonObject attempt = snapshot(target, scenario);
        attempt.add("player", playerState(player));
        float before = target.getHealth();
        player.attack(target);
        complete(attempt, target, before);
        attempts.add(attempt);
        return attempt;
    }

    private static JsonObject snapshot(LivingEntity target, String scenario) {
        JsonObject attempt = new JsonObject();
        attempt.addProperty("scenario", scenario);
        attempt.addProperty("target", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        attempt.addProperty("health_before", target.getHealth());
        attempt.addProperty("hurt_time_before", target.hurtTime);
        attempt.addProperty("immunity_before", target.invulnerableTime);
        attempt.addProperty("motion_before", target.getDeltaMovement().toString());
        return attempt;
    }

    private static void complete(JsonObject attempt, LivingEntity target, float before) {
        attempt.addProperty("health_after", target.getHealth());
        attempt.addProperty("health_loss", before - target.getHealth());
        attempt.addProperty("hurt_time_after", target.hurtTime);
        attempt.addProperty("immunity_after", target.invulnerableTime);
        attempt.addProperty("motion_after", target.getDeltaMovement().toString());
    }

    private static JsonObject playerState(ServerPlayer player) {
        JsonObject state = new JsonObject();
        state.addProperty("health", player.getHealth());
        state.addProperty("max_health", player.getMaxHealth());
        state.addProperty("level", player.experienceLevel);
        state.addProperty("food", player.getFoodData().getFoodLevel());
        state.addProperty("nutrition", ((DualFoodData) player.getFoodData()).mite$getNutrition());
        state.addProperty("has_energy", FoodConsumption.hasEnergy(player));
        state.addProperty("weak", SourcePlayerCombat.weak(player));
        state.addProperty("attack_damage", player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        state.addProperty("attack_speed", player.getAttributeValue(Attributes.ATTACK_SPEED));
        state.addProperty("attack_strength", player.getAttackStrengthScale(.5F));
        state.addProperty("held", BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
        return state;
    }

    private static void assertDefaultPlayer(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(player.experienceLevel == 0 && player.getMaxHealth() == 6F && player.getHealth() == 6F,
                "Fixture must use native level-zero six-point health: " + playerState(player));
        helper.assertTrue(FoodConsumption.hasEnergy(player), "Ordinary food supplies real energy: " + playerState(player));
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == 1D
                && player.getAttributeValue(Attributes.ATTACK_SPEED) == 4D,
                "Fixture must retain native one-point fist damage and four-point speed: " + playerState(player));
        helper.assertFalse(SourcePlayerCombat.weak(player), "Healthy fed level-zero fists must not be weak");
    }

    private static void assertFullAttackStrength(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(player.getAttackStrengthScale(.5F) >= .999F,
                "A full-cooldown scenario must actually advance the native attack ticker: " + playerState(player));
    }

    private static void assertDamage(GameTestHelper helper, JsonObject attempt, double expected) {
        helper.assertTrue(Math.abs(attempt.get("health_loss").getAsDouble() - expected) < .0001D,
                "Exact damage expectation " + expected + ": " + attempt);
    }

    private static void assertNoNewHurtFeedback(GameTestHelper helper, JsonObject attempt) {
        assertDamage(helper, attempt, 0D);
        helper.assertTrue(attempt.get("motion_after").getAsString().equals(attempt.get("motion_before").getAsString()),
                "Rejected repeated hits must not add knockback: " + attempt);
        helper.assertTrue(attempt.get("hurt_time_after").getAsInt() == attempt.get("hurt_time_before").getAsInt(),
                "Rejected repeated hits must not refresh the hurt animation: " + attempt);
        helper.assertTrue(attempt.get("immunity_after").getAsInt() == attempt.get("immunity_before").getAsInt(),
                "Rejected repeated hits must not restart the native immunity window: " + attempt);
    }

    private static Animal animal(GameTestHelper helper, EntityType<? extends Animal> type, Vec3 origin) {
        Animal target = type.create(helper.getLevel());
        target.setPos(origin);
        target.setNoAi(true);
        target.setNoGravity(true);
        helper.getLevel().addFreshEntity(target);
        target.setHealth(target.getMaxHealth());
        target.setOnGround(true);
        return target;
    }

    private static void tickPair(ServerPlayer player, Animal target, Vec3 origin, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tickPlayer(player, origin, 1);
            tickAnimal(target, origin.add(1D, 0D, 0D));
        }
    }

    private static void tickAnimal(Animal target, Vec3 origin) {
        target.tick();
        target.setPos(origin);
        target.setDeltaMovement(Vec3.ZERO);
        target.setOnGround(true);
    }

    private static void tickPlayer(ServerPlayer player, Vec3 origin, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            player.tick();
            // The live server connection calls doTick separately; it reaches Player.tick and
            // advances native attack strength, equipment attributes, food and survival state.
            player.doTick();
            player.setPos(origin);
            player.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(true);
        }
    }

    private static Session session(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-damage-audit"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.experienceLevel = 0;
            player.setNoGravity(true);
            Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 15, 1)));
            tickPlayer(player, origin, 60);
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(6);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null)
                player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private static void write(JsonObject report) {
        Path output = Path.of(System.getProperty("mite.defaultMeleeAuditOutput",
                workspace().resolve("build/verification/default-melee-audit.json").toString())).toAbsolutePath().normalize();
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write default melee diagnostic report to " + output, failure);
        }
    }

    private static Path workspace() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))) return candidate;
        }
        return current;
    }

    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null)
                    player.server.getPlayerList().remove(player);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}
