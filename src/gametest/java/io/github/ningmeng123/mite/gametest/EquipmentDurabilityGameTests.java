package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Save/network, real player equipment refresh, armor damage and repair integration. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class EquipmentDurabilityGameTests {
    private EquipmentDurabilityGameTests() {}

    @GameTest(template = "empty")
    public static void qualitySurvivesNbtAndNetworkAndHonorsMaterialMaximum(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        helper.assertValueEqual(stack.getMaxDamage(), 9600, "Average iron pickaxe maximum");
        EquipmentDurability.setQuality(stack, Quality.FINE);
        stack.setDamageValue(1000);
        helper.assertValueEqual(stack.getMaxDamage(), 14400, "Quality affects the actual stack API");
        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(),
                stack.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertValueEqual(EquipmentDurability.quality(restored), Quality.FINE, "NBT quality survives");
        helper.assertValueEqual(restored.getMaxDamage(), 14400, "NBT maximum is not multiplied twice");
        helper.assertValueEqual(restored.getDamageValue(), 1000, "NBT damage survives");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, restored);
            ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(EquipmentDurability.quality(decoded), Quality.FINE, "Network quality survives");
            helper.assertValueEqual(decoded.getMaxDamage(), 14400, "Network uses the same maximum");
            helper.assertValueEqual(decoded.getDamageValue(), 1000, "Network damage survives");
        } finally {
            buffer.release();
        }
        stack.set(EquipmentDurability.QUALITY.get(), 7);
        helper.assertValueEqual(EquipmentDurability.quality(stack), Quality.MASTERWORK, "Iron material caps command quality");
        helper.assertValueEqual(stack.getMaxDamage(), 28800, "The material cap applies to durability");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernCombineRepairPreservesQualityWithoutDoubleScaling(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.IRON_PICKAXE);
        EquipmentDurability.setQuality(first, Quality.FINE);
        first.setDamageValue(10000);
        ItemStack second = first.copy();
        CraftingInput input = CraftingInput.of(2, 1, List.of(first, second));
        RepairItemRecipe recipe = new RepairItemRecipe(CraftingBookCategory.MISC);
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "Matching quality remains repairable");
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertValueEqual(result.getOrDefault(DataComponents.MAX_DAMAGE, 0), 9600, "Result stores the unscaled base");
        helper.assertValueEqual(result.getMaxDamage(), 14400, "Quality is applied exactly once after repair");
        helper.assertValueEqual(result.getDamageValue(), 5600, "R196 sums remaining durability without a five-percent bonus");
        helper.assertValueEqual(EquipmentDurability.quality(result), Quality.FINE, "Combine repair keeps quality");
        EquipmentDurability.setQuality(second, Quality.AVERAGE);
        helper.assertFalse(recipe.matches(input, helper.getLevel()), "Different quality cannot silently be combined");
        helper.assertTrue(recipe.assemble(input, helper.getLevel().registryAccess()).isEmpty(), "Direct assembly also refuses mixed quality");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryRepairRequiresTwoDamagedUnenchantedMatchingColors(GameTestHelper helper) {
        RepairItemRecipe recipe = new RepairItemRecipe(CraftingBookCategory.MISC);
        ItemStack first = new ItemStack(Items.LEATHER_CHESTPLATE);
        first.setDamageValue(10);
        ItemStack second = first.copy();
        CraftingInput input = CraftingInput.of(2, 1, List.of(first, second));
        second.setDamageValue(0);
        helper.assertFalse(recipe.matches(input, helper.getLevel()), "A pristine component cannot be used for ordinary combine repair");
        second.setDamageValue(10);
        second.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.VANISHING_CURSE), 1);
        helper.assertFalse(recipe.matches(input, helper.getLevel()), "Cursed items cannot be laundered through ordinary repair");
        second = first.copy();
        input = CraftingInput.of(2, 1, List.of(first, second));
        first.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699, true));
        helper.assertFalse(recipe.matches(input, helper.getLevel()), "Dyed and undyed pieces cannot combine");
        second.set(DataComponents.DYED_COLOR, new DyedItemColor(0x993366, true));
        helper.assertFalse(recipe.matches(input, helper.getLevel()), "Different dye colors cannot combine");
        second.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699, false));
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "Same RGB is valid regardless of tooltip visibility");
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertValueEqual(result.getDamageValue(), 4, "Six plus six remaining gives twelve of sixteen");
        helper.assertValueEqual(result.get(DataComponents.DYED_COLOR).rgb(), 0x336699, "Matching dye survives repair");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wornArmorAttributesRefreshAndTheLastPointSurvives(GameTestHelper helper) {
        try (PlayerSession session = newPlayer(helper)) {
            ServerPlayer player = session.player();
            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.doTick();
            helper.assertValueEqual(chest.getMaxDamage(), 128, "Iron chestplate has the original maximum");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 6D, "Iron chestplate uses balanced armor six");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 1.25D, "Iron chestplate uses balanced toughness one and a quarter");
            chest.setDamageValue(96);
            player.doTick();
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 6D, "Seventy-five percent wear retains full modern protection");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 1.25D, "Seventy-five percent wear retains full toughness");
            chest.setDamageValue(127);
            player.doTick();
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 6D, "One remaining durability retains full protection");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 1.25D, "One remaining durability retains full toughness");
            chest.hurtAndBreak(500, player, EquipmentSlot.CHEST);
            helper.assertFalse(chest.isEmpty(), "Ordinary worn-armor damage cannot destroy the last point");
            helper.assertValueEqual(chest.getDamageValue(), 127, "The last point is not consumed");
            chest.setDamageValue(0);
            player.doTick();
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 6D, "Repair retains the same full protection without unequipping");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 1.25D, "Repair retains the same full toughness without unequipping");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorQualityNbtAndNetworkRefreshBothArmorAndToughness(GameTestHelper helper) {
        ItemStack original = new ItemStack(MiteArmorContent.get("mite:mithril_chestplate"));
        EquipmentDurability.setQuality(original, Quality.FINE);
        helper.assertValueEqual(original.getMaxDamage(), 1536, "Mithril chest1024 times Fine1.5");
        original.setDamageValue(1152);
        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(), original.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertValueEqual(EquipmentDurability.quality(restored), Quality.FINE, "Armor NBT keeps quality");
        helper.assertValueEqual(restored.getDamageValue(), 1152, "Armor NBT keeps exact wear");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try (PlayerSession session = newPlayer(helper)) {
            ItemStack.STREAM_CODEC.encode(buffer, restored);
            ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded.getMaxDamage(), 1536, "Armor network maximum is quality adjusted once");
            helper.assertValueEqual(decoded.getDamageValue(), 1152, "Armor network keeps exact wear");
            helper.assertValueEqual(EquipmentDurability.quality(decoded), Quality.FINE, "Armor network keeps quality");
            var player = session.player();
            player.setItemSlot(EquipmentSlot.CHEST, decoded); player.doTick();
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 7D, "Decoded worn player mithril chest retains modern armor seven");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 2D, "Decoded worn player mithril chest retains toughness two");
            EquipmentDurability.setQuality(decoded, Quality.LEGENDARY); player.doTick();
            helper.assertValueEqual(decoded.getMaxDamage(), 3584, "Quality increases durability rather than direct defensive stats");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 7D, "Quality changes capacity without changing full base armor");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 2D, "Quality changes capacity without changing full base toughness");
            decoded.setDamageValue(decoded.getMaxDamage() - 1); player.doTick();
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 7D, "Player last durability point retains full armor");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 2D, "Player last durability point retains full toughness");
            decoded.hurtAndBreak(500, player, EquipmentSlot.CHEST);
            helper.assertFalse(decoded.isEmpty(), "Final point remains repairable under retained source wear rules");
            decoded.setDamageValue(0); player.doTick();
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR), 7D, "Repair updates modern armor without re-equipping");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 2D, "Repair updates modern toughness without re-equipping");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerDamageDistributesOneEightPointBudgetAcrossFullIronArmor(GameTestHelper helper) {
        try (PlayerSession session = newPlayer(helper)) {
            ServerPlayer player = session.player();
            finishSpawnProtection(player);
            ItemStack[] pieces = {new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.IRON_LEGGINGS),
                    new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_HELMET)};
            EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            for (int i = 0; i < 4; i++) player.setItemSlot(slots[i], pieces[i]);
            player.doTick();
            helper.assertFalse(player.damageSources().cactus().is(DamageTypeTags.BYPASSES_ARMOR),
                    "The damage fixture must enter armor absorption");
            float healthBefore = player.getHealth();
            helper.assertTrue(player.hurt(player.damageSources().cactus(), 9F), "Native modern armor mitigation still produces a real health transaction");
            helper.assertTrue(player.getHealth() < healthBefore, "The real hit also reduces player health");
            int totalWear = 0;
            for (ItemStack piece : pieces) totalWear += piece.getDamageValue();
            helper.assertValueEqual(totalWear, 8, "The R196 full-iron protection budget is eight total points");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledArmorEventPreventsAllWear(GameTestHelper helper) {
        try (PlayerSession session = newPlayer(helper)) {
            ServerPlayer player = session.player();
            finishSpawnProtection(player);
            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.doTick();
            helper.assertFalse(player.damageSources().cactus().is(DamageTypeTags.BYPASSES_ARMOR),
                    "The damage fixture must enter armor absorption");
            boolean[] observed = {false};
            Consumer<ArmorHurtEvent> listener = event -> {
                if (event.getEntity() == player) {
                    observed[0] = true;
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                float healthBefore = player.getHealth();
                helper.assertTrue(player.hurt(player.damageSources().cactus(), 4F), "Damage traverses the real player path");
                helper.assertTrue(observed[0], "The standard NeoForge armor event is posted");
                helper.assertValueEqual(chest.getDamageValue(), 0, "Event cancellation is respected");
                helper.assertTrue(player.getHealth() < healthBefore,
                        "Canceling armor wear does not cancel the actual health damage");
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void originalWorkbenchIsPortableForTheRealItemStackMiningCallback(GameTestHelper helper) {
        try (PlayerSession session = newPlayer(helper)) {
            ServerPlayer player = session.player();
            ItemStack axe = new ItemStack(Items.IRON_AXE);
            player.setItemSlot(EquipmentSlot.MAINHAND, axe);
            axe.mineBlock(helper.getLevel(), Blocks.CRAFTING_TABLE.defaultBlockState(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), player);
            helper.assertValueEqual(axe.getDamageValue(), 0, "Portable workbench costs no tool durability");
            axe.mineBlock(helper.getLevel(), Blocks.OAK_LOG.defaultBlockState(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), player);
            helper.assertValueEqual(axe.getDamageValue(), 120, "Nonportable log uses hardness-based wear exactly once");
            ItemStack weakPick = new ItemStack(Items.STONE_PICKAXE);
            player.setItemSlot(EquipmentSlot.MAINHAND, weakPick);
            helper.assertFalse(weakPick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()),
                    "The modern stone pickaxe fixture is below the obsidian harvest tier");
            helper.assertTrue(weakPick.getDestroySpeed(Blocks.OBSIDIAN.defaultBlockState()) > 1F,
                    "A speed bonus alone does not prove the tier is sufficient");
            weakPick.mineBlock(helper.getLevel(), Blocks.OBSIDIAN.defaultBlockState(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), player);
            helper.assertValueEqual(weakPick.getDamageValue(), 0,
                    "An under-tier tool does not spend durability through the actual ItemStack callback");
        }
        helper.succeed();
    }

    private static void finishSpawnProtection(ServerPlayer player) {
        // ServerPlayer.tick(), not doTick(), advances the normal 60-tick login protection.
        for (int tick = 0; tick < 60; tick++) player.tick();
    }

    private static PlayerSession newPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-wear-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
            player.setNoGravity(true);
            player.setOnGround(true);
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.setHealth(player.getMaxHealth());
            return new PlayerSession(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record PlayerSession(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}
