package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.ShieldGuard;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real successful blocks retain native damage/wear/experience before cumulative guard failure. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class ShieldGuardGameTests {
    private ShieldGuardGameTests() {}

    private static final class GuardPlayer extends ServerPlayer {
        private final EmbeddedChannel channel;
        private GuardPlayer(GameTestHelper helper) {
            this(helper, CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "shield-guard"), false));
        }
        private GuardPlayer(GameTestHelper helper, CommonListenerCookie cookie) {
            super(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            try { server.getPlayerList().placeNewPlayer(connection, this, cookie); }
            catch (RuntimeException | Error failure) { channel.finishAndReleaseAll(); throw failure; }
            setGameMode(GameType.SURVIVAL);
            for (int tick = 0; tick < 60; tick++) tick();
        }
        private void close() {
            try { server.getPlayerList().remove(this); }
            finally { channel.finishAndReleaseAll(); }
        }
    }

    private static final class Session implements AutoCloseable {
        private final GameTestHelper helper;
        private final LivingEntity defender;
        private final Cow attacker;
        private final ItemStack shield;
        private final InteractionHand hand;
        private boolean closed;
        private Session(GameTestHelper helper, boolean player) {
            this(helper, player, InteractionHand.OFF_HAND);
        }
        private Session(GameTestHelper helper, boolean player, InteractionHand hand) {
            this.helper = helper;
            this.hand = hand;
            if (player) defender = new GuardPlayer(helper);
            else {
                Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
                zombie.setNoAi(true);
                defender = zombie;
            }
            defender.setPos(helper.absolutePos(new BlockPos(1, 4, 1)).getCenter());
            defender.setNoGravity(true);
            defender.setOnGround(true);
            defender.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
            defender.setHealth(200F);
            shield = new ItemStack(MiteShields.get("iron"));
            defender.setItemInHand(hand, shield);
            attacker = EntityType.COW.create(helper.getLevel());
            attacker.setNoAi(true);
            attacker.setNoGravity(true);
            raise();
        }
        private void forward() {
            defender.setXRot(0F);
            defender.setYRot(0F);
            defender.setYHeadRot(0F);
            defender.yBodyRot = 0F;
            attacker.setPos(defender.position().add(0D, 0D, 3D));
        }
        private void raise() {
            defender.startUsingItem(hand);
            for (int tick = 0; tick < 6; tick++) {
                if (defender instanceof ServerPlayer player) player.doTick(); else defender.tick();
            }
            forward();
            helper.assertTrue(defender.isBlocking(), "The real equipped shield completes its native raising delay");
        }
        private void hit(float damage) {
            defender.invulnerableTime = 0;
            defender.hurt(defender.damageSources().mobAttack(attacker), damage);
        }
        private long experience() { return GearProgression.state(shield).experienceMillis(); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try { if (defender instanceof GuardPlayer player) player.close(); else defender.discard(); }
            finally { attacker.discard(); }
        }
    }

    private static void state(GameTestHelper helper, ItemStack shield, double damage, int blocks, String message) {
        var state = ShieldGuard.state(shield);
        helper.assertTrue(Math.abs(state.blockedDamage() - damage) < 1E-6D && state.blocks() == blocks,
                message + ": actual=" + state + ", expected=" + damage + "/" + blocks);
    }

    @GameTest(template = "empty")
    public static void damageLimitWaitsAtTwentyFourAndTripsAtExactlyTwentyFiveOnOnlyTheSecondBlock(GameTestHelper helper) {
        try (Session session = new Session(helper, true)) {
            float health = session.defender.getHealth();
            session.hit(24F);
            state(helper, session.shield, 24D, 1, "Twenty-four blocked damage is still below the damage boundary");
            helper.assertTrue(session.defender.isUsingItem() && !ShieldGuard.disabled(session.defender),
                    "A twenty-four-damage first block does not disable the shield early");
            helper.assertValueEqual(session.shield.getDamageValue(), 25, "The large accepted hit keeps native1+floor24 wear");
            helper.assertValueEqual(session.experience(), 48_000L, "Every blocked point grants two shield experience");
            session.hit(1F);
            helper.assertTrue(ShieldGuard.disabled(session.defender) && !session.defender.isUsingItem(),
                    "Exactly twenty-five cumulative damage forces the shield down even with only two blocks");
            helper.assertValueEqual(session.defender.getHealth(), health, "The boundary-crossing hit is still fully blocked");
            helper.assertValueEqual(session.experience(), 50_000L, "The final one-point block retains its doubled experience");
            helper.assertValueEqual(session.shield.getDamageValue(), 25, "A sub-three terminal hit retains native zero wear");
            state(helper, session.shield, 0D, 0, "Guard pressure resets when the forced cooldown begins");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void terminalDamagingBlockKeepsItsNativeDurabilityAndExperienceBeforeTheShieldLowers(GameTestHelper helper) {
        try (Session session = new Session(helper, true)) {
            session.shield.set(ShieldGuard.GUARD.get(), new ShieldGuard.State(24D, 1));
            float health = session.defender.getHealth();
            session.hit(4F);
            helper.assertValueEqual(session.shield.getDamageValue(), 5, "The terminal hit spends its actual five durability points");
            helper.assertValueEqual(session.experience(), 8_000L, "The terminal hit earns its complete doubled blocked-damage reward");
            helper.assertValueEqual(session.defender.getHealth(), health, "Forced lowering happens after the accepted health block");
            helper.assertTrue(ShieldGuard.disabled(session.defender) && !session.defender.isUsingItem(), "The terminal hit then starts cooldown");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void thirdSmallBlockDisablesPlayersAndMobsAndLoweringDoesNotClearTheFirstTwoBlocks(GameTestHelper helper) {
        for (boolean player : new boolean[]{false, true}) for (InteractionHand hand : InteractionHand.values())
            try (Session session = new Session(helper, player, hand)) {
            float health = session.defender.getHealth();
            session.hit(1F);
            session.hit(1F);
            state(helper, session.shield, 2D, 2, "Two small successful blocks persist below the third-block boundary");
            helper.assertTrue(session.defender.isUsingItem(), "Two blocks keep the shield raised");
            session.defender.stopUsingItem();
            session.raise();
            state(helper, session.shield, 2D, 2, "Lowering and raising the same shield cannot clear cumulative guard pressure");
            session.hit(1F);
            helper.assertTrue(ShieldGuard.disabled(session.defender) && !session.defender.isUsingItem(),
                    "The third one-point block disables both real survival players and shield-bearing monsters");
            helper.assertValueEqual(session.shield.getDamageValue(), 0, "All three small hits retain the native zero-durability rule");
            helper.assertValueEqual(session.defender.getHealth(), health, "The third small hit is still blocked");
            if (player) helper.assertValueEqual(session.experience(), 6_000L, "Small blocks still grant their complete doubled experience");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledRearAndZeroBlocksDoNotCountAndPartialBlocksCountOnlyTheApprovedAmount(GameTestHelper helper) {
        try (Session session = new Session(helper, true)) {
            session.hit(4F);
            var before = ShieldGuard.state(session.shield);
            long experience = session.experience();
            boolean[] seen = new boolean[3];
            Consumer<LivingShieldBlockEvent> cancel = event -> {
                if (event.getEntity() == session.defender) { seen[0] = true; event.setCanceled(true); }
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try { session.hit(.25F); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(seen[0] && session.defender.isAlive(), "The cancelled-hit fixture reaches the real shield event without killing the survival player");
            helper.assertValueEqual(ShieldGuard.state(session.shield), before, "A cancelled block cannot increase pressure");
            session.attacker.setPos(session.defender.position().add(0D, 0D, -3D));
            helper.assertFalse(session.defender.isDamageSourceBlocked(session.defender.damageSources().mobAttack(session.attacker)),
                    "The rear-hit fixture really lies outside the native shield arc");
            session.hit(.25F);
            helper.assertTrue(session.defender.isAlive(), "The real rear-hit fixture leaves the survival player alive");
            helper.assertValueEqual(ShieldGuard.state(session.shield), before, "A rear hit cannot increase pressure");
            session.forward();
            Consumer<LivingShieldBlockEvent> zero = event -> {
                if (event.getEntity() == session.defender) { seen[1] = true; event.setBlockedDamage(0F); }
            };
            NeoForge.EVENT_BUS.addListener(zero);
            try { session.hit(.25F); }
            finally { NeoForge.EVENT_BUS.unregister(zero); }
            helper.assertTrue(seen[1] && session.defender.isAlive(), "The zero-block fixture reaches the real event and remains nonlethal");
            helper.assertValueEqual(ShieldGuard.state(session.shield), before, "A zero-blocked-damage event cannot count as a successful block");
            helper.assertValueEqual(session.experience(), experience, "Cancelled, rear and zero blocks grant no shield experience");
            Consumer<LivingShieldBlockEvent> partial = event -> {
                if (event.getEntity() == session.defender) {
                    event.setBlockedDamage(2F); event.setShieldDamage(2F);
                    seen[2] = event.getBlocked() && event.getBlockedDamage() == 2F;
                }
            };
            helper.assertTrue(session.defender.isAlive() && session.defender.isBlocking(), "The player is still alive and actually blocking before the partial hit");
            NeoForge.EVENT_BUS.addListener(partial);
            try { session.hit(4F); }
            finally { NeoForge.EVENT_BUS.unregister(partial); }
            helper.assertTrue(seen[2] && session.defender.isAlive(), "The real partial event approves two blocked points and its unblocked remainder is nonlethal");
            state(helper, session.shield, 6D, 2, "The accepted partial block adds only two actual blocked points and one block");
            helper.assertValueEqual(session.experience(), experience + 4_000L, "The partial block rewards only its approved two points");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void allMetalShieldsStayDisabledThroughTickNinetyNineAndRecoverAtTickOneHundred(GameTestHelper helper) {
        Session player = new Session(helper, true), mob = new Session(helper, false);
        try {
            for (Session session : List.of(player, mob)) {
                session.hit(1F); session.hit(1F); session.hit(1F);
                helper.assertTrue(ShieldGuard.disabled(session.defender), "Three successful blocks start a real cooldown");
                helper.assertValueEqual(session.defender.getPersistentData().getLong(MobShields.DISABLED_UNTIL),
                        helper.getLevel().getGameTime() + ShieldGuard.COOLDOWN_TICKS, "Both kinds of wearer store exactly one hundred world ticks");
                for (var definition : MiteShields.definitions()) {
                    session.defender.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MiteShields.get(definition.material())));
                    session.defender.startUsingItem(InteractionHand.OFF_HAND);
                    helper.assertFalse(session.defender.isUsingItem(), "Changing shield material cannot bypass the forced cooldown");
                    if (session.defender instanceof ServerPlayer realPlayer)
                        helper.assertTrue(realPlayer.getCooldowns().isOnCooldown(MiteShields.get(definition.material())),
                                "Every registered metal shield also receives the native player cooldown");
                }
            }
        } catch (RuntimeException | Error failure) { player.close(); mob.close(); throw failure; }
        helper.runAfterDelay(99L, () -> {
            try {
                for (int tick = 0; tick < 99; tick++) ((ServerPlayer)player.defender).doTick();
                helper.assertTrue(ShieldGuard.disabled(player.defender) && ShieldGuard.disabled(mob.defender), "The cooldown remains active at tick99");
                player.defender.startUsingItem(InteractionHand.OFF_HAND);
                mob.defender.startUsingItem(InteractionHand.OFF_HAND);
                helper.assertFalse(player.defender.isUsingItem() || mob.defender.isUsingItem(), "Neither wearer can raise a shield one tick early");
            } catch (RuntimeException | Error failure) { player.close(); mob.close(); throw failure; }
        });
        helper.runAfterDelay(100L, () -> {
            try {
                ((ServerPlayer)player.defender).doTick();
                helper.assertFalse(ShieldGuard.disabled(player.defender) || ShieldGuard.disabled(mob.defender), "The world cooldown ends exactly at tick100");
                helper.assertFalse(((ServerPlayer)player.defender).getCooldowns().isOnCooldown(player.defender.getOffhandItem().getItem()),
                        "Exactly one hundred real player ticks also clear the native item cooldown");
                player.raise(); mob.raise();
                helper.assertTrue(player.defender.isBlocking() && mob.defender.isBlocking(), "Both wearers can complete a fresh shield raise after cooldown");
                helper.succeed();
            } finally { player.close(); mob.close(); }
        });
    }

    @GameTest(template = "empty")
    public static void savedPressureAndAllThreeRepairRoutesKeepOnlyTheFirstShieldsPressure(GameTestHelper helper) {
        try (Session session = new Session(helper, true)) {
            session.hit(4F);
            var expected = ShieldGuard.state(session.shield);
            ItemStack saved = ItemStack.parse(helper.getLevel().registryAccess(), session.shield.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(ShieldGuard.state(saved), expected, "Both cumulative damage and block count survive real ItemStack save/load");
            ItemStack first = saved.copy(); first.setDamageValue(50);
            ItemStack second = new ItemStack(first.getItem()); second.setDamageValue(100);
            second.set(ShieldGuard.GUARD.get(), new ShieldGuard.State(20D, 2));
            GrindstoneMenu grindstone = new GrindstoneMenu(1, ((ServerPlayer)session.defender).getInventory());
            grindstone.getSlot(0).set(first.copy()); grindstone.getSlot(1).set(second.copy());
            helper.assertFalse(grindstone.getSlot(2).getItem().isEmpty(), "The real grindstone produces its normal equal-quality shield repair");
            helper.assertValueEqual(ShieldGuard.state(grindstone.getSlot(2).getItem()), expected, "Grindstone repair keeps the first shield's pressure without adding the second");
            AnvilMenu anvil = new AnvilMenu(2, ((ServerPlayer)session.defender).getInventory());
            anvil.getSlot(0).set(first.copy()); anvil.getSlot(1).set(second.copy());
            helper.assertFalse(anvil.getSlot(2).getItem().isEmpty(), "The real anvil produces its normal equal-quality shield repair");
            helper.assertValueEqual(ShieldGuard.state(anvil.getSlot(2).getItem()), expected, "Anvil repair keeps the first shield's pressure without adding the second");
            var repair = new RepairItemRecipe(CraftingBookCategory.MISC);
            ItemStack crafted = repair.assemble(CraftingInput.of(2, 1, List.of(first.copy(), second.copy())), helper.getLevel().registryAccess());
            helper.assertFalse(crafted.isEmpty(), "The actual two-item repair recipe produces a shield");
            helper.assertValueEqual(ShieldGuard.state(crafted), expected, "Crafting repair preserves exactly the first shield's guard state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryAxeDisableAlsoClearsAccumulatedPressureForPlayersAndMobs(GameTestHelper helper) {
        for (boolean player : new boolean[]{false, true}) try (Session session = new Session(helper, player)) {
            session.hit(4F);
            state(helper, session.shield, 4D, 1, "The fixture has existing guard pressure before an ordinary axe disable");
            session.attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("copper_axe")));
            helper.assertTrue(session.attacker.canDisableShield(), "The actual cow attack source now carries a shield-disabling MITE axe");
            session.hit(4F);
            helper.assertFalse(session.defender.isUsingItem(), "The ordinary native axe-disable path lowers the shield");
            helper.assertTrue(ShieldGuard.disabled(session.defender), "The ordinary disable starts the shared five-second cooldown");
            state(helper, session.shield, 0D, 0, "An ordinary axe disable cannot leave stale cumulative pressure on either kind of wearer");
        }
        helper.succeed();
    }
}
