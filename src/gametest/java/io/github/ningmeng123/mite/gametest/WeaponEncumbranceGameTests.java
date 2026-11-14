package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.WeaponEncumbrance;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Actual attributes, lifecycle events, player NBT and dimension transfers verify player-only weapon weight. @author NingMeng123 */
@GameTestHolder("mite_feedback")
@PrefixGameTestTemplate(false)
public final class WeaponEncumbranceGameTests {
    @GameTest(template = "empty")
    public static void mainHandWeightReplacesItselfAndOffhandOrStarterItemsHaveNoResidualPenalty(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var other = ResourceLocation.fromNamespaceAndPath("other_mod", "independent_speed");
        speed.addPermanentModifier(new AttributeModifier(other, .02D, AttributeModifier.Operation.ADD_VALUE));
        double original = speed.getValue();
        equip(player, EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get("iron_war_hammer")));
        close(h, speed.getValue(), original*.82D, "A main-hand war hammer applies its18% player movement tradeoff");
        for (int tick = 0; tick < 100; tick++) NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        close(h, speed.getValue(), original*.82D, "Repeated fallback ticks never compound or duplicate weight");
        equip(player, EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get("iron_battle_axe")));
        close(h, speed.getValue(), original*.85D, "Switching heavy families replaces the existing modifier");
        equip(player, EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get("iron_sword")));
        close(h, speed.getValue(), original, "A lighter weapon removes the previous weight");
        equip(player, EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get("flint_hatchet")));
        close(h, speed.getValue(), original, "Starter weapons retain their original mobility");
        equip(player, EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        equip(player, EquipmentSlot.OFFHAND, new ItemStack(MiteTools.get("iron_war_hammer")));
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        close(h, speed.getValue(), original, "Only the main hand bears the weapon's movement cost");
        h.assertTrue(speed.getModifier(WeaponEncumbrance.MODIFIER_ID) == null && speed.getModifier(other) != null,
                "Weight cleanup preserves unrelated movement modifiers");
        player.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void logoutDeathAndCloneClearTheSingleTransientWeightModifier(GameTestHelper h) {
        try (Session originalSession = new Session(h); Session replacementSession = new Session(h)) {
        ServerPlayer original = originalSession.player, replacement = replacementSession.player;
        var speed = original.getAttribute(Attributes.MOVEMENT_SPEED);
        original.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get("iron_war_hammer")));
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(original));
        h.assertTrue(speed.getModifier(WeaponEncumbrance.MODIFIER_ID) != null, "A held weapon first establishes the transient modifier");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(original));
        h.assertTrue(speed.getModifier(WeaponEncumbrance.MODIFIER_ID) == null, "Logout clears weight from the departing entity");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(original));
        h.assertTrue(speed.getModifier(WeaponEncumbrance.MODIFIER_ID) != null, "Login recalculates weight from the currently held item");
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(original, original.damageSources().generic()));
        h.assertTrue(speed.getModifier(WeaponEncumbrance.MODIFIER_ID) == null, "The completed death event clears weight");
        WeaponEncumbrance.refresh(original);
        replacement.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get("iron_battle_axe")));
        WeaponEncumbrance.refresh(replacement);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, true));
        h.assertTrue(speed.getModifier(WeaponEncumbrance.MODIFIER_ID) == null
                && replacement.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(WeaponEncumbrance.MODIFIER_ID) == null,
                "Cloning clears both old and new attribute maps before inventory restoration");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(replacement, false));
        close(h, replacement.getAttribute(Attributes.MOVEMENT_SPEED).getValue(),
                replacement.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()*.85D,
                "Respawn applies one current-item modifier after restoration");
        WeaponEncumbrance.clear(replacement);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualDimensionTransfersAndPlayerNbtReloadCannotPersistOrCompoundWeight(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ServerPlayer player = session.player;
            double original = player.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
            equip(player, EquipmentSlot.MAINHAND, new ItemStack(MiteTools.get("iron_war_hammer")));
            var underworld = player.server.getLevel(MiteWorldDimensions.UNDERWORLD);
            player.teleportTo(underworld, .5, 250, .5, 0, 0);
            close(h, player.getAttribute(Attributes.MOVEMENT_SPEED).getValue(), original*.82D,
                    "A real dimension transfer retains exactly one current-main-hand weight modifier");
            CompoundTag saved = new CompoundTag(); player.saveWithoutId(saved);
            h.assertFalse(saved.toString().contains(WeaponEncumbrance.MODIFIER_ID.toString()), "The transient weight modifier is absent from real player NBT");
            Player restored = h.makeMockPlayer(GameType.SURVIVAL); restored.load(saved);
            h.assertTrue(restored.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(WeaponEncumbrance.MODIFIER_ID) == null,
                    "Reloading saved player attributes cannot restore a stale weight modifier");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(restored));
            close(h, restored.getAttribute(Attributes.MOVEMENT_SPEED).getValue(), original*.82D,
                    "The first fallback tick computes current held-weapon weight exactly once after load");
            equip(restored, EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            close(h, restored.getAttribute(Attributes.MOVEMENT_SPEED).getValue(), original, "Unequipping after reload restores full movement");
            restored.discard();
            equip(player, EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.teleportTo(h.getLevel(), .5, 250, .5, 0, 0);
            close(h, player.getAttribute(Attributes.MOVEMENT_SPEED).getValue(), original,
                    "Returning dimensions without the weapon leaves no movement residue");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void retainedTridentHasRealRecoveryWhileInventoryFormationCoresHaveNoWeight(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setNoGravity(true); player.tick();
        double movement = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        try {
            int inventorySlot = 9;
            for (String material : java.util.List.of("copper", "silver", "vibranium")) {
                ItemStack core = new ItemStack(MitePhantomBlades.get(material));
                close(h, WeaponEncumbrance.penalty(core), 0D, "A carried formation core is not treated as a held heavy weapon");
                player.getInventory().setItem(inventorySlot++, core);
                h.assertValueEqual(player.getInventory().countItem(MitePhantomBlades.get(material)), 1, "The formation core is genuinely present in the player's inventory");
            }
            h.assertTrue(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(), "All formation cores are in backpack slots rather than either hand");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), movement, "Formation cores in the inventory never slow the player");
            equip(player, EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT)); player.tick();
            close(h, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 6D, "The retained trident has six total base melee damage");
            close(h, player.getAttributeValue(Attributes.ATTACK_SPEED), .9D, "The retained trident has its selected long-range weapon cooldown");
            h.assertTrue(Math.abs(player.getCurrentItemAttackStrengthDelay() - 22.222222222D) < .00001D,
                    "The retained trident's float recovery follows its .9 attack rate");
            close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), movement * .95D, "Only the currently held trident supplies the existing reach weight");
            equip(player, EquipmentSlot.MAINHAND, new ItemStack(Items.MACE)); player.tick();
            close(h, player.getAttributeValue(Attributes.ATTACK_DAMAGE), 10.5D, "The retained mace has its selected high per-hit base damage");
            close(h, player.getAttributeValue(Attributes.ATTACK_SPEED), .6D, "The retained mace has a slow heavy-weapon cooldown");
            h.assertTrue(Math.abs(player.getCurrentItemAttackStrengthDelay() - 33.333333333D) < .00001D,
                    "The retained mace's float recovery follows its .6 attack rate");
            close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), movement * .85D, "Switching to a mace replaces the trident's weight with fifteen percent");
            equip(player, EquipmentSlot.MAINHAND, ItemStack.EMPTY); player.tick();
            close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED), movement, "Removing the main-hand weapon restores movement while formation cores stay in the inventory");
            close(h, player.getAttributeValue(Attributes.ATTACK_SPEED), 4D, "Removing the main-hand weapon restores the native attack rate");
        } finally { WeaponEncumbrance.clear(player); player.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theSameWeaponNeverAppliesItsPlayerMovementPenaltyToMonsters(GameTestHelper h) {
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        ItemStack hammer = new ItemStack(MiteTools.get("iron_war_hammer"));
        double before = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, hammer);
        NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(zombie, EquipmentSlot.MAINHAND, ItemStack.EMPTY, hammer));
        close(h, zombie.getAttributeValue(Attributes.MOVEMENT_SPEED), before, "A monster holding the same heavy weapon keeps its movement speed");
        h.assertTrue(zombie.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(WeaponEncumbrance.MODIFIER_ID) == null,
                "The player-only modifier is never attached to a monster");
        hammer.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> h.assertFalse(attribute.equals(Attributes.MOVEMENT_SPEED),
                "The item itself carries no universal movement penalty"));
        h.assertTrue(WeaponEncumbrance.penalty(new ItemStack(Items.MACE)) == .15D
                && WeaponEncumbrance.penalty(new ItemStack(Items.TRIDENT)) == .05D,
                "Retained modern heavy and reach weapons use the same centralized player path");
        zombie.discard(); h.succeed();
    }

    private static void equip(Player player, EquipmentSlot slot, ItemStack stack) {
        ItemStack previous = player.getItemBySlot(slot);
        player.setItemSlot(slot, stack);
        NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, slot, previous, stack));
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual-expected) < .000001D, message + ": actual=" + actual + ", expected=" + expected);
    }

    private static final class Session implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-weapon-weight"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setInvulnerable(true);
            player.moveTo(h.absolutePos(new BlockPos(1, 3, 1)).getCenter());
            player.getInventory().clearContent(); player.removeAllEffects();
        }
        @Override public void close() {
            WeaponEncumbrance.clear(player); player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
        }
    }
}
