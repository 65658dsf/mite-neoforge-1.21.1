package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real optional Better Combat player requests and the vanilla packet damage path. @author NingMeng123 */
@GameTestHolder("mite_bettercombat")
@PrefixGameTestTemplate(false)
public final class BetterCombatPlayerReachGameTests {
    private BetterCombatPlayerReachGameTests() {}

    private static boolean absent(GameTestHelper h) {
        if (BetterCombatBridge.enabled()) return false;
        h.succeed();
        return true;
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void absentBetterCombatKeepsSourceSwordRangeAndPacketRejection(GameTestHelper h) {
        if (BetterCombatBridge.enabled()) { h.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings(1D, 0D); Session session = session(h)) {
            ServerPlayer player = session.player();
            equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
            try (Target near = target(h, player, 2.1D); Target far = target(h, player, 2.8D)) {
                h.assertTrue(Math.abs(SourcePlayerReach.meleeReach(player, near.entity()) - 2.25D) < .00001D,
                        "Without the optional mod, a sword retains the source 2.25 block reach");
                vanillaAttack(player, near.entity());
                h.assertTrue(near.entity().getHealth() < 200F,
                        "The actual vanilla attack packet still damages a target inside source reach");
                vanillaAttack(player, far.entity());
                h.assertValueEqual(far.entity().getHealth(), 200F,
                        "Without Better Combat, the same packet still rejects a target 2.8 blocks from its hitbox");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void realSwordRequestKeepsSourceCapAndHeightAdjustment(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.realSword(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void simplySwordsLongWeaponKeepsItsSourceRangeCap(GameTestHelper h) {
        if (absent(h)) return;
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.simplySwordsLongWeapon(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void attackRangeMultiplierFollowsTheRequestedComboAndDoesNotLeak(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.comboRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void dualWieldingUsesTheSelectedHandsRangeAcrossTheRealSwap(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.dualWielding(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void survivalConfigurationAppliesOnceAndCreativeKeepsBothRangeCaps(GameTestHelper h) {
        if (absent(h)) return;
        Loaded.configuredRange(h);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void realPlayerScaleShrinksNativeRangeButCannotExceedSourceCap(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.scaledRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void invalidWeaponRangesCannotAuthorizeAnAttack(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.invalidRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void configuredRangeAlsoPassesTheOptionalNonVanillaValidationGate(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(2D, .25D)) { Loaded.nonVanillaValidation(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void nestedAttackScopesRestoreAfterExceptionsAndBindOnlyTheirPlayer(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.scopeCleanup(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void zeroConfiguredReachRejectsAnOverlappingTarget(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(0D, 0D)) { Loaded.zeroRange(h); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "bettercombat_player_reach")
    public static void explicitItemEntityRangeOverridesTheWeaponRangeBonus(GameTestHelper h) {
        if (absent(h)) return;
        try (ReachSettings ignored = new ReachSettings(1D, 0D)) { Loaded.itemRange(h); }
        h.succeed();
    }

    private static void equip(ServerPlayer player, ItemStack main, ItemStack off) {
        Vec3 position = player.position();
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        // Apply the actual equipment components before Better Combat swaps their attributes.
        player.tick();
        player.doTick();
        player.setPos(position);
        player.setDeltaMovement(Vec3.ZERO);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10D);
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
        player.getFoodData().setFoodLevel(6);
        player.setOnGround(true);
        player.fallDistance = 0F;
    }

    private static Target target(GameTestHelper h, ServerPlayer player, double hitboxDistance) {
        return target(h, player, hitboxDistance, null);
    }

    private static Target target(GameTestHelper h, ServerPlayer player, double hitboxDistance, Double feetDifference) {
        Zombie target = EntityType.ZOMBIE.create(h.getLevel());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setOnGround(true);
        target.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        target.setHealth(200F);
        double halfWidth = target.getBbWidth() * .5D + target.getPickRadius();
        double feetOffset = feetDifference == null
                ? Math.max(0D, player.getEyeHeight() - target.getBbHeight() * .8D) : -feetDifference;
        target.setPos(player.position().add(0D, feetOffset, 0D));
        double verticalGap = Math.abs(player.getEyeY() - SourcePlayerReach.closestPoint(
                target.getBoundingBox().inflate(target.getPickRadius()), player.getEyePosition()).y);
        h.assertTrue(verticalGap <= hitboxDistance,
                "The requested distance must contain the fixture's vertical separation");
        double horizontalGap = Math.sqrt(Math.max(0D, hitboxDistance * hitboxDistance - verticalGap * verticalGap));
        target.setPos(player.position().add(0D, feetOffset, horizontalGap + halfWidth));
        h.getLevel().addFreshEntity(target);
        double actual = player.getEyePosition().distanceTo(SourcePlayerReach.closestPoint(
                target.getBoundingBox().inflate(target.getPickRadius()), player.getEyePosition()));
        h.assertTrue(Math.abs(actual - hitboxDistance) < .00001D,
                "The fixture measures eyes to the target hitbox, independently of its center: " + actual);
        return new Target(target);
    }

    private static void vanillaAttack(ServerPlayer player, Zombie target) {
        target.invulnerableTime = 0;
        player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(target, false));
    }

    private static Session session(GameTestHelper h) {
        UUID playerId = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId,
                "bc-reach-" + playerId.toString().substring(0, 6)), false);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 15, 2))));
            player.setNoGravity(true);
            player.setOnGround(true);
            player.setYRot(0F);
            player.setXRot(0F);
            for (int tick = 0; tick < 60; ++tick) player.tick();
            player.getFoodData().setFoodLevel(6);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
            player.setHealth(player.getMaxHealth());
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null)
                player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record Target(Zombie entity) implements AutoCloseable {
        @Override public void close() { entity.discard(); }
    }

    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null)
                    player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }

    private static final class ReachSettings implements AutoCloseable {
        private final double multiplier = MiteConfig.WEAPON_REACH_MULTIPLIER.get();
        private final double reduction = MiteConfig.WEAPON_REACH_REDUCTION.get();

        private ReachSettings(double multiplier, double reduction) {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
            MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
            MiteConfig.refreshEnchantmentRules();
        }

        @Override public void close() {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
            MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
            MiteConfig.refreshEnchantmentRules();
        }
    }

    /** Loading the test holder never resolves optional Better Combat classes on an absent-mod run. */
    private static final class Loaded {
        private static void nonVanillaValidation(GameTestHelper h) {
            boolean vanilla = net.bettercombat.network.Packets.C2S_AttackRequest.UseVanillaPacket;
            boolean validation = net.bettercombat.BetterCombatMod.config.server_target_range_validation;
            net.bettercombat.network.Packets.C2S_AttackRequest.UseVanillaPacket = false;
            net.bettercombat.BetterCombatMod.config.server_target_range_validation = true;
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                try (Target near = target(h, player, 4.1D); Target far = target(h, player, 4.4D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The optional BC validation gate admits the source-configured 4.25 block sword reach");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "MITE still enforces the exact configured limit on the non-vanilla attack path");
                }
            } finally {
                net.bettercombat.network.Packets.C2S_AttackRequest.UseVanillaPacket = vanilla;
                net.bettercombat.BetterCombatMod.config.server_target_range_validation = validation;
            }
        }

        private static void request(ServerPlayer player, Zombie target, int combo) {
            target.invulnerableTime = 0;
            net.bettercombat.network.ServerNetwork.handleAttackRequest(
                    new net.bettercombat.network.Packets.C2S_AttackRequest(combo, false,
                            player.getInventory().selected, target.getId(), new int[] { target.getId() }),
                    player.server, player, player.connection);
        }

        private static void realSword(GameTestHelper h) {
            h.assertTrue(net.bettercombat.network.Packets.C2S_AttackRequest.UseVanillaPacket,
                    "The installed Better Combat uses its normal vanilla attack packet bridge");
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                double range = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, player.getMainHandItem());
                h.assertTrue(Math.abs(range - 3D) < .00001D, "The real sword profile has the default three block range");
                try (Target near = target(h, player, 2.1D);
                        Target capped = target(h, player, 2.4D);
                        Target far = target(h, player, range + .2D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The real Better Combat request damages a target inside the source 2.25 block sword reach");
                    request(player, capped.entity(), 0);
                    h.assertValueEqual(capped.entity().getHealth(), 200F,
                            "The native three block sword profile cannot exceed the source 2.25 block cap");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "A forged Better Combat request cannot hit outside the actual sword range");
                }
                try (Target downhillNear = target(h, player, 2.6D, 1.5D);
                        Target downhillFar = target(h, player, 2.9D, 1.5D);
                        Target uphillNear = target(h, player, 1.6D, -1.5D);
                        Target uphillFar = target(h, player, 1.9D, -1.5D)) {
                    h.assertTrue(Math.abs(SourcePlayerReach.sourceMeleeReach(player, downhillNear.entity()) - 2.75D) < .00001D,
                            "The source cap retains the downhill height advantage");
                    h.assertTrue(Math.abs(SourcePlayerReach.sourceMeleeReach(player, uphillNear.entity()) - 1.75D) < .00001D,
                            "The source cap retains the uphill height penalty");
                    request(player, downhillNear.entity(), 0);
                    h.assertTrue(downhillNear.entity().getHealth() < 200F,
                            "Better Combat retains the source downhill sword reach");
                    request(player, downhillFar.entity(), 0);
                    h.assertValueEqual(downhillFar.entity().getHealth(), 200F,
                            "The downhill height bonus still has its source limit");
                    request(player, uphillNear.entity(), 0);
                    h.assertTrue(uphillNear.entity().getHealth() < 200F,
                            "Better Combat can hit inside the source uphill sword reach");
                    request(player, uphillFar.entity(), 0);
                    h.assertValueEqual(uphillFar.entity().getHealth(), 200F,
                            "The native sword profile cannot bypass the source uphill penalty");
                }
                try (Target target = target(h, player, 2.8D)) {
                    vanillaAttack(player, target.entity());
                    h.assertValueEqual(target.entity().getHealth(), 200F,
                            "A normal vanilla packet cannot reuse the completed Better Combat request's reach");
                    player.attack(target.entity());
                    h.assertValueEqual(target.entity().getHealth(), 200F,
                            "A direct attack outside a Better Combat request still obeys the source sword guard");
                }
            }
        }

        private static void simplySwordsLongWeapon(GameTestHelper h) {
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("simplyswords", "iron_glaive");
                h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The actual Simply Swords glaive is registered");
                equip(player, new ItemStack(BuiltInRegistries.ITEM.get(id)), ItemStack.EMPTY);
                var hand = net.bettercombat.logic.PlayerAttackHelper.getCurrentAttack(player, 0);
                h.assertTrue(hand != null, "The actual glaive resolves a Better Combat attack");
                double range = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, hand.itemStack())
                        * hand.attack().rangeMultiplier();
                h.assertTrue(range > 3D, "The real long weapon extends beyond the ordinary sword range");
                try (Target near = target(h, player, 2.1D); Target far = target(h, player, 2.4D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The registered long weapon hits inside its source 2.25 block reach through the real request");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The actual long weapon cannot exceed its source 2.25 block cap");
                }
            }
        }

        private static net.bettercombat.api.WeaponAttributes.Attack attack(float rangeMultiplier) {
            return attack(rangeMultiplier, (net.bettercombat.api.WeaponAttributes.Condition[]) null);
        }

        private static net.bettercombat.api.WeaponAttributes.Attack attack(float rangeMultiplier,
                net.bettercombat.api.WeaponAttributes.Condition... conditions) {
            return new net.bettercombat.api.WeaponAttributes.Attack(conditions,
                    net.bettercombat.api.WeaponAttributes.HitBoxShape.FORWARD_BOX,
                    1D, 1F, rangeMultiplier, 0D, .5D,
                    "bettercombat:one_handed_stab", null, null, List.of());
        }

        private static net.bettercombat.api.WeaponAttributes attributes(double range,
                net.bettercombat.api.WeaponAttributes.Attack... attacks) {
            return new net.bettercombat.api.WeaponAttributes(range, 0D, null, null, false,
                    "mite_player_reach_test", attacks, null);
        }

        private static void comboRange(GameTestHelper h) {
            Item item = Items.IRON_SWORD;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(item));
            net.bettercombat.logic.WeaponRegistry.register(id, attributes(3D, attack(.6F), attack(1.25F)));
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(item), ItemStack.EMPTY);
                try (Target shortNear = target(h, player, 1.7D);
                        Target shortFar = target(h, player, 1.9D);
                        Target longNear = target(h, player, 2.1D);
                        Target longFar = target(h, player, 2.4D)) {
                    request(player, shortNear.entity(), 0);
                    h.assertTrue(shortNear.entity().getHealth() < 200F, "The shortened combo attack still hits inside 1.8 blocks");
                    request(player, shortFar.entity(), 0);
                    h.assertValueEqual(shortFar.entity().getHealth(), 200F,
                            "The 0.6 range multiplier is enforced for the requested short attack");
                    request(player, longNear.entity(), 1);
                    h.assertTrue(longNear.entity().getHealth() < 200F,
                            "The longer combo attack still hits inside the source 2.25 block sword reach");
                    request(player, longFar.entity(), 1);
                    h.assertValueEqual(longFar.entity().getHealth(), 200F,
                            "A long combo attack cannot exceed the source 2.25 block cap");
                    equip(player, ItemStack.EMPTY, ItemStack.EMPTY);
                    vanillaAttack(player, shortFar.entity());
                    h.assertValueEqual(shortFar.entity().getHealth(), 200F,
                            "Completing a Better Combat request leaves no extended range on a later empty-hand vanilla packet");
                }
            } finally { net.bettercombat.logic.WeaponRegistry.register(id, previous); }
        }

        private static void dualWielding(GameTestHelper h) {
            ResourceLocation daggerId = ResourceLocation.fromNamespaceAndPath("mite", "iron_dagger");
            ResourceLocation scytheId = ResourceLocation.fromNamespaceAndPath("mite", "iron_scythe");
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(daggerId) && BuiltInRegistries.ITEM.containsKey(scytheId),
                    "The test uses the registered MITE dagger and scythe with distinct source reaches");
            Item main = BuiltInRegistries.ITEM.get(daggerId), off = BuiltInRegistries.ITEM.get(scytheId);
            ResourceLocation mainId = BuiltInRegistries.ITEM.getKey(main), offId = BuiltInRegistries.ITEM.getKey(off);
            var previousMain = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(main));
            var previousOff = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(off));
            net.bettercombat.logic.WeaponRegistry.register(mainId,
                    new net.bettercombat.api.WeaponAttributes(2D, 0D, null, null, false,
                            "mite_short_test", new net.bettercombat.api.WeaponAttributes.Attack[] { attack(1F) }, null));
            net.bettercombat.logic.WeaponRegistry.register(offId,
                    new net.bettercombat.api.WeaponAttributes(4D, 0D, null, null, false,
                            "mite_long_test", new net.bettercombat.api.WeaponAttributes.Attack[] {
                                attack(.25F, net.bettercombat.api.WeaponAttributes.Condition.DUAL_WIELDING_SAME_CATEGORY),
                                attack(1F, net.bettercombat.api.WeaponAttributes.Condition.OFF_HAND_ONLY) }, null));
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(main), new ItemStack(off));
                h.assertTrue(net.bettercombat.logic.PlayerAttackHelper.isDualWielding(player),
                        "The test uses Better Combat's actual dual wielding state");
                try (Target mainNear = target(h, player, 1.9D);
                        Target mainFar = target(h, player, 2.1D);
                        Target offNear = target(h, player, 2.4D);
                        Target offFar = target(h, player, 2.7D)) {
                    request(player, mainNear.entity(), 0);
                    h.assertTrue(mainNear.entity().getHealth() < 200F, "The main hand hits inside its own short range");
                    request(player, mainFar.entity(), 0);
                    h.assertValueEqual(mainFar.entity().getHealth(), 200F, "The main hand cannot borrow the offhand's long reach");
                    request(player, offNear.entity(), 1);
                    h.assertTrue(offNear.entity().getHealth() < 200F,
                            "The captured offhand condition survives the temporary swap and keeps the scythe's source 2.5 block reach");
                    request(player, offFar.entity(), 1);
                    h.assertValueEqual(offFar.entity().getHealth(), 200F, "The offhand cannot exceed the scythe's source 2.5 block cap");
                    request(player, mainFar.entity(), 2);
                    h.assertValueEqual(mainFar.entity().getHealth(), 200F,
                            "The following main-hand attack does not retain the previous offhand's reach");
                    h.assertTrue(player.getMainHandItem().is(main) && player.getOffhandItem().is(off),
                            "The real request restores both original held items after an offhand attack");
                }
            } finally {
                net.bettercombat.logic.WeaponRegistry.register(mainId, previousMain);
                net.bettercombat.logic.WeaponRegistry.register(offId, previousOff);
            }
        }

        private static void configuredRange(GameTestHelper h) {
            try (ReachSettings ignored = new ReachSettings(.75D, .25D); Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                try (Target near = target(h, player, 1.3D); Target far = target(h, player, 1.6D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The configured source sword range 2.25 * 0.75 - 0.25 still reaches 1.3 blocks");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The configured source 1.4375 block sword reach rejects 1.6 blocks");
                }
                player.setGameMode(GameType.CREATIVE);
                Vec3 position = player.position();
                player.tick();
                player.setPos(position);
                double creativeRange = Math.min(5D,
                        net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, player.getMainHandItem()));
                try (Target near = target(h, player, creativeRange - .2D); Target far = target(h, player, creativeRange + .2D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "Creative Better Combat retains the smaller native or source range without survival reach settings");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "Creative Better Combat still enforces both native and source range limits");
                }
            }
            try (ReachSettings ignored = new ReachSettings(2D, .25D); Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                try (Target near = target(h, player, 4.1D); Target far = target(h, player, 4.4D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "Increasing the source sword range to 2.25 * 2 - 0.25 survives the actual vanilla packet bridge");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The increased range is still bounded by the source-configured 4.25 blocks");
                }
            }
        }

        private static void scaledRange(GameTestHelper h) {
            float previousInfluence = net.bettercombat.BetterCombatMod.config.entity_scale_attack_range_influence;
            net.bettercombat.BetterCombatMod.config.entity_scale_attack_range_influence = 1F;
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                player.getAttribute(Attributes.SCALE).setBaseValue(.5D);
                player.refreshDimensions();
                try (Target near = target(h, player, 1.4D, 0D); Target far = target(h, player, 1.7D, 0D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "A half-size player can hit inside Better Combat's scaled 1.5 block sword range");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "A half-size player cannot borrow the unscaled three block sword range");
                }
                player.getAttribute(Attributes.SCALE).setBaseValue(1.5D);
                player.refreshDimensions();
                try (Target near = target(h, player, 2.1D, 0D); Target far = target(h, player, 2.4D, 0D)) {
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "A larger player's actual scale can still hit inside the source 2.25 block sword reach");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The scaled native 4.5 block sword range cannot bypass the source 2.25 block cap");
                }
            } finally {
                net.bettercombat.BetterCombatMod.config.entity_scale_attack_range_influence = previousInfluence;
            }
        }

        private static void invalidRange(GameTestHelper h) {
            Item item = Items.END_ROD;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(item));
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(item), ItemStack.EMPTY);
                for (double invalid : new double[] { Double.NaN, Double.POSITIVE_INFINITY, -3D }) {
                    net.bettercombat.logic.WeaponRegistry.register(id, attributes(invalid, attack(1F)));
                    try (Target target = target(h, player, 1D)) {
                        request(player, target.entity(), 0);
                        h.assertValueEqual(target.entity().getHealth(), 200F,
                                "A non-positive or non-finite weapon range cannot authorize a real attack: " + invalid);
                    }
                }
                for (float invalid : new float[] { Float.NaN, Float.POSITIVE_INFINITY, -1F, 0F }) {
                    net.bettercombat.logic.WeaponRegistry.register(id, attributes(3D, attack(invalid)));
                    try (Target target = target(h, player, 1D)) {
                        request(player, target.entity(), 0);
                        h.assertValueEqual(target.entity().getHealth(), 200F,
                                "A non-positive or non-finite attack range multiplier cannot authorize a real attack: " + invalid);
                    }
                }
            } finally { net.bettercombat.logic.WeaponRegistry.register(id, previous); }
        }

        private static void scopeCleanup(GameTestHelper h) {
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                var other = h.makeMockPlayer(GameType.SURVIVAL);
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                ItemStack sword = player.getMainHandItem();
                h.assertTrue(BetterCombatPlayerReach.reach(player) == null,
                        "No server range scope exists before the actual attack");
                RuntimeException expected = new IllegalStateException("intentional nested attack scope failure");
                BetterCombatPlayerReach.duringAttack(player, sword, 1D, () -> {
                    h.assertTrue(Math.abs(BetterCombatPlayerReach.reach(player) - 3D) < .00001D,
                            "The outer request scope resolves the captured player's sword");
                    h.assertTrue(BetterCombatPlayerReach.reach(other) == null,
                            "An active scope never grants its range to another player on the same thread");
                    try {
                        BetterCombatPlayerReach.duringAttack(other, sword, .5D, () -> {
                            h.assertTrue(BetterCombatPlayerReach.reach(player) == null,
                                    "A nested request remains bound to its own player");
                            h.assertTrue(Math.abs(BetterCombatPlayerReach.reach(other) - 1.5D) < .00001D,
                                    "The nested player's captured move has its own range");
                            throw expected;
                        });
                        h.fail("The nested scope must propagate the actual attack failure");
                    } catch (RuntimeException failure) {
                        if (failure != expected) throw failure;
                    }
                    h.assertTrue(Math.abs(BetterCombatPlayerReach.reach(player) - 3D) < .00001D,
                            "A failed nested attack restores the outer player's previous range");
                    h.assertTrue(BetterCombatPlayerReach.reach(other) == null,
                            "The failed nested player's range is removed");
                });
                h.assertTrue(BetterCombatPlayerReach.reach(player) == null,
                        "The completed outer request removes the thread-local range");
                try {
                    BetterCombatPlayerReach.duringAttack(player, sword, 1D, () -> { throw expected; });
                    h.fail("The outer scope must propagate the actual attack failure");
                } catch (RuntimeException failure) {
                    if (failure != expected) throw failure;
                }
                h.assertTrue(BetterCombatPlayerReach.reach(player) == null,
                        "A failed outer request also removes its range");
            }
        }

        private static void zeroRange(GameTestHelper h) {
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                equip(player, new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY);
                try (Target overlap = target(h, player, 0D)) {
                    request(player, overlap.entity(), 0);
                    h.assertValueEqual(overlap.entity().getHealth(), 200F,
                            "A zero configured range disables damage even when nearest collision distance is zero");
                }
            }
        }

        private static void itemRange(GameTestHelper h) {
            Item item = Items.PRISMARINE_SHARD;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            var previous = net.bettercombat.logic.WeaponRegistry.getAttributes(new ItemStack(item));
            net.bettercombat.logic.WeaponRegistry.register(id,
                    new net.bettercombat.api.WeaponAttributes(0D, 3D, null, null, false,
                            "mite_item_range_test", new net.bettercombat.api.WeaponAttributes.Attack[] { attack(1F) }, null));
            try (Session session = session(h)) {
                ServerPlayer player = session.player();
                ItemStack stack = new ItemStack(item);
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().add(
                        Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite", "player_reach_test"),
                                2D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
                equip(player, stack, ItemStack.EMPTY);
                double actual = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, stack);
                h.assertTrue(Math.abs(actual - 5D) < .00001D,
                        "Better Combat's actual item-range precedence chooses the attribute's five blocks: " + actual);
                try (Target near = target(h, player, 3.3D); Target far = target(h, player, 3.7D)) {
                    h.assertTrue(Math.abs(SourcePlayerReach.sourceMeleeReach(player, near.entity()) - 3.5D) < .00001D,
                            "The source cap preserves the explicit external attribute on top of the source base reach");
                    request(player, near.entity(), 0);
                    h.assertTrue(near.entity().getHealth() < 200F,
                            "The actual attack preserves explicit ENTITY_INTERACTION_RANGE item components within the source cap");
                    request(player, far.entity(), 0);
                    h.assertValueEqual(far.entity().getHealth(), 200F,
                            "The weapon profile's native range bonus cannot bypass the source cap or double the explicit attribute");
                }
            } finally { net.bettercombat.logic.WeaponRegistry.register(id, previous); }
        }
    }
}
