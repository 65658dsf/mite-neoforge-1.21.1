package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Cube;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.DeathOrbQueue;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Actual blob hits, native player death and entity ticks distinguish lost gear from digestion. @author NingMeng123 */
@GameTestHolder("mite_death_recovery")
@PrefixGameTestTemplate(false)
public final class BlobDeathRecoveryGameTests {
    private BlobDeathRecoveryGameTests() {}

    private record Sample(ItemStack before, boolean digestible, boolean lastPoint) {}

    @GameTest(template = "empty")
    public static void nativeDeathDropsKeepMetalGearWhileBlobDigestsOnlySusceptibleLeather(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            List<Sample> matrix = new ArrayList<>();
            for (Item item : List.of(MiteTools.get("mithril_pickaxe"), Items.IRON_PICKAXE,
                    MiteArmorContent.get("mite:mithril_chestplate"), Items.IRON_CHESTPLATE, Items.LEATHER_CHESTPLATE)) {
                for (boolean lastPoint : new boolean[]{false, true}) {
                    ItemStack stack = f.equipment(item, lastPoint);
                    matrix.add(new Sample(stack.copy(), item == Items.LEATHER_CHESTPLATE, lastPoint));
                    f.player.getInventory().setItem(matrix.size() - 1, stack);
                }
            }
            f.killWithBlob();
            helper.assertTrue(f.player.getInventory().isEmpty(), "Actual death empties all native inventory compartments");
            for (Sample sample : matrix) {
                ItemEntity drop = f.drop(GearProgression.id(sample.before()));
                helper.assertTrue(ItemStack.matches(drop.getItem(), sample.before()),
                        "The death transaction preserves exact durability, quality, growth, name and custom data");
                f.holdInBlob(drop);
            }
            f.tickContact(120);
            for (Sample sample : matrix) {
                UUID id = GearProgression.id(sample.before());
                List<ItemEntity> drops = f.drops(id);
                if (sample.digestible() && sample.lastPoint()) {
                    helper.assertTrue(drops.isEmpty(), "One-point leather is genuinely consumed after death by blob stomach fluid");
                } else {
                    helper.assertValueEqual(drops.size(), 1, "Death and six contact pulses retain exactly one recoverable equipment item");
                    ItemStack expected = sample.before().copy();
                    if (sample.digestible()) expected.setDamageValue(expected.getDamageValue() + 12);
                    helper.assertTrue(ItemStack.matches(drops.getFirst().getItem(), expected),
                            "Metal remains byte-for-byte intact; full leather loses exactly two durability per second");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedBlobHitsCanLevelWornMetalArmorWithoutLosingItsDeathDrop(GameTestHelper helper) {
        for (Item item : List.of(Items.IRON_CHESTPLATE, MiteArmorContent.get("mite:mithril_chestplate"))) {
            try (Fixture f = new Fixture(helper)) {
                ItemStack armor = f.equipment(item, true);
                UUID id = GearProgression.id(armor);
                armor.set(GearProgression.PROGRESS.get(), new GearProgress(id, 3,
                        GearProgression.nextLevelExperienceMillis(armor, 3) - 1, Map.of(), 1));
                f.player.setItemSlot(EquipmentSlot.CHEST, armor);
                int maximum = armor.getMaxDamage();
                Quality quality = EquipmentDurability.quality(armor);
                for (int hit = 0; hit < 8; hit++) {
                    f.nonfatalHit();
                    ItemStack worn = f.player.getItemBySlot(EquipmentSlot.CHEST);
                    helper.assertTrue(worn == armor && !worn.isEmpty(), "Real repeated hits retain the worn equipment stack");
                    helper.assertValueEqual(worn.getDamageValue(), maximum - 1, "Ordinary armor wear preserves its final point");
                    helper.assertTrue(worn.getMaxDamage() == maximum && EquipmentDurability.quality(worn) == quality
                            && id.equals(GearProgression.id(worn)), "Growth updates do not replace the item or rewrite durability/quality");
                }
                helper.assertTrue(GearProgression.state(armor).level() >= 4,
                        "Damage through the real blob attack path crosses an actual equipment level and rolls an attribute");
                helper.assertTrue(!GearProgression.state(armor).affixes().isEmpty(), "The actual armor upgrade rolled its modifier");
                f.killWithBlob();
                ItemEntity drop = f.drop(id);
                ItemStack afterDeath = drop.getItem().copy();
                helper.assertTrue(afterDeath.getDamageValue() == maximum - 1 && GearProgression.state(afterDeath).level() >= 4,
                        "The terminal hit and native inventory drop preserve the progressed one-point armor");
                f.holdInBlob(drop);
                f.tickContact(120);
                helper.assertTrue(ItemStack.matches(f.drop(id).getItem(), afterDeath),
                        "Six more seconds of real blob contact cannot delete or alter the grown metal armor");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blobCanDestroyWornLeatherBeforeDeathWhileUnaffectedEquipmentStillDrops(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ItemStack leather = f.equipment(Items.LEATHER_CHESTPLATE, true);
            UUID leatherId = GearProgression.id(leather);
            ItemStack metal = f.equipment(Items.IRON_PICKAXE, true);
            UUID metalId = GearProgression.id(metal);
            ItemStack expectedMetal = metal.copy();
            f.player.setItemSlot(EquipmentSlot.CHEST, leather);
            f.player.getInventory().setItem(1, metal);
            f.nonfatalHit();
            helper.assertTrue(f.player.isAlive(), "The first attack is deliberately nonfatal");
            helper.assertTrue(leather.isEmpty() && f.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                    "Size-four blob stomach fluid can destroy the last leather point before player death");
            helper.assertTrue(ItemStack.matches(f.player.getInventory().getItem(1), expectedMetal),
                    "The same attack leaves the worn metal tool untouched");
            f.killWithBlob();
            helper.assertTrue(f.drops(leatherId).isEmpty(), "Leather already digested while alive cannot appear in the later death loot");
            ItemEntity metalDrop = f.drop(metalId);
            helper.assertTrue(ItemStack.matches(metalDrop.getItem(), expectedMetal), "The rest of the native equipment drop is retained");
            f.holdInBlob(metalDrop);
            f.tickContact(120);
            helper.assertTrue(ItemStack.matches(f.drop(metalId).getItem(), expectedMetal),
                    "Partial equipment loss from leather digestion does not spread to the metal death drop");
        }
        helper.succeed();
    }

    private static ItemStack equipment(Item item, boolean lastPoint) {
        ItemStack stack = new ItemStack(item);
        EquipmentDurability.setQuality(stack, Quality.FINE);
        UUID id = UUID.randomUUID();
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(id, 4, 12_345,
                Map.of(ResourceLocation.parse("mite:regression_saved_affix"), 2), 1));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 " + id));
        CompoundTag custom = new CompoundTag();
        custom.putString("blob_death_identity", id.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        stack.set(DataComponents.REPAIR_COST, 63);
        if (lastPoint) stack.setDamageValue(stack.getMaxDamage() - 1);
        return stack;
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final R196Cube blob;
        final Vec3 origin;
        final AABB area;
        final GameRules.BooleanValue keep;
        final boolean oldKeep;
        final Set<UUID> identities = new HashSet<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            origin = helper.absoluteVec(new Vec3(2.5, 3, 2.5));
            area = AABB.ofSize(origin, 8, 8, 8);
            keep = helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
            oldKeep = keep.get();
            keep.set(false, helper.getLevel().getServer());
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "NingMeng123"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(origin);
            player.setNoGravity(true);
            player.setOnGround(true);
            for (int tick = 0; tick < 60; tick++) player.tick();
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
            player.setHealth(100F);
            PlayerExperience.setTotal(player, 0);
            player.setDeltaMovement(Vec3.ZERO);
            blob = (R196Cube) MiteMobs.type(MobKind.BLOB).create(helper.getLevel());
            helper.assertTrue(blob != null && !blob.acidic(), "The real registered crimson blob uses stomach fluid");
            blob.setSize(4, true);
            blob.setPos(origin);
            blob.setNoGravity(true);
            blob.setPersistenceRequired();
            helper.assertTrue(helper.getLevel().addFreshEntity(blob), "The attacking blob is a real world entity");
            helper.getLevel().setBlockAndUpdate(BlockPos.containing(origin).below(), Blocks.STONE.defaultBlockState());
        }

        void nonfatalHit() {
            player.setPos(origin);
            player.setDeltaMovement(Vec3.ZERO);
            player.invulnerableTime = 0;
            player.setHealth(100F);
            float health = player.getHealth();
            blob.playerTouch(player);
            helper.assertTrue(player.isAlive() && player.getHealth() < health,
                    "A real nonfatal blob contact damages the survival ServerPlayer");
        }

        ItemStack equipment(Item item, boolean lastPoint) {
            ItemStack stack = BlobDeathRecoveryGameTests.equipment(item, lastPoint);
            identities.add(GearProgression.id(stack));
            return stack;
        }

        List<ItemEntity> ownedDrops() {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                    drop -> drop.isAlive() && identities.contains(GearProgression.id(drop.getItem())));
        }

        void killWithBlob() {
            player.setPos(origin);
            player.setDeltaMovement(Vec3.ZERO);
            player.invulnerableTime = 0;
            player.setHealth(.1F);
            blob.playerTouch(player);
            helper.assertTrue(player.isDeadOrDying(), "The blob's native playerTouch attack actually kills the player");
            helper.assertTrue(player.getLastDamageSource() != null && player.getLastDamageSource().getEntity() == blob,
                    "The recorded death source is the real blob rather than a direct kill or synthetic death event");
            blob.setNoAi(true);
            blob.setDeltaMovement(Vec3.ZERO);
            player.setPos(origin.add(0, 10, 0));
        }

        List<ItemEntity> drops(UUID id) {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                    drop -> drop.isAlive() && id.equals(GearProgression.id(drop.getItem())));
        }

        ItemEntity drop(UUID id) {
            List<ItemEntity> found = drops(id);
            helper.assertValueEqual(found.size(), 1, "Native player death produces exactly one item entity for each surviving identity");
            return found.getFirst();
        }

        void holdInBlob(ItemEntity drop) {
            drop.setPos(origin.add(0, .5, 0));
            drop.setDeltaMovement(Vec3.ZERO);
            drop.setNoGravity(true);
        }

        void tickContact(int ticks) {
            int pulses = 0;
            for (int tick = 0; tick < ticks; tick++) {
                blob.setPos(origin);
                blob.setDeltaMovement(Vec3.ZERO);
                // Use the real server tick entry, which increments tickCount and invokes
                // R196Cube.aiStep/contact; never call the chemical damage helper directly.
                helper.getLevel().tickNonPassenger(blob);
                if (MobCombat.ticksWithOffset(blob) % 20 == 0) pulses++;
                for (ItemEntity drop : ownedDrops()) {
                    holdInBlob(drop);
                    helper.getLevel().tickNonPassenger(drop);
                }
            }
            helper.assertValueEqual(pulses, ticks / 20, "The actual entity clock advances one contact pulse per second");
        }

        @Override public void close() {
            blob.discard();
            ownedDrops().forEach(ItemEntity::discard);
            DeathOrbQueue.get(helper.getLevel()).removeForOwner(player.getUUID());
            player.getInventory().clearContent();
            helper.getLevel().getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            keep.set(oldKeep, helper.getLevel().getServer());
        }
    }
}
