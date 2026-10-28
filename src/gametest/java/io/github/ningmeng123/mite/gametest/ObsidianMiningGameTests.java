package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Obsidian iron-pick timing, R196 tool wear, actual drops and mining modifiers. @author NingMeng123 */
@GameTestHolder("mite_obsidian")
@PrefixGameTestTemplate(false)
public final class ObsidianMiningGameTests {
    @GameTest(template = "empty")
    public static void ironPickMatchesVanillaDiamondTimingWithoutChangingBlockProperties(GameTestHelper helper) {
        Player player = miner(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        float progress = obsidian.getDestroyProgress(player, helper.getLevel(), pos);
        assertClose(helper, progress, 8F / 50F / 30F,
                "A fed grounded level-zero iron pick matches unenchanted vanilla diamond mining");
        float accumulated = 0F;
        int ticks = 0;
        while (accumulated < 1F && ticks < 1000) {
            accumulated += progress;
            ticks++;
        }
        helper.assertValueEqual(ticks, 188, "Vanilla diamond timing is 188 accumulated mining ticks");
        helper.assertTrue(player.getMainHandItem().isCorrectToolForDrops(obsidian),
                "An iron pick retains correct-tool drops for obsidian");
        helper.assertValueEqual(obsidian.getDestroySpeed(helper.getLevel(), pos), 50F,
                "Obsidian retains its registered hardness");
        helper.assertValueEqual(obsidian.getBlock().getExplosionResistance(), 1200F,
                "Faster mining does not change obsidian blast resistance");
        assertClose(helper, Blocks.CRYING_OBSIDIAN.defaultBlockState().getDestroyProgress(player, helper.getLevel(), pos),
                0.0003125F, "Crying obsidian retains its existing R196 mining progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualIronHarvestWears240PerObsidianWhileLowerToolsCannotRemoveIt(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState previous = helper.getLevel().getBlockState(pos);
        GameRules.BooleanValue tileDrops = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);
        boolean previousTileDrops = tileDrops.get();
        AABB area = new AABB(pos).inflate(2);
        try (PlayerSession session = newServerPlayer(helper);
                SpawnedEntityCapture drops = new SpawnedEntityCapture(helper.getLevel(),
                        entity -> entity instanceof ItemEntity && entity.getBoundingBox().intersects(area))) {
            tileDrops.set(true, helper.getLevel().getServer());
            ServerPlayer player = session.player();
            player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2D);
            prepare(player);
            BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
            helper.getLevel().setBlockAndUpdate(pos, obsidian);
            helper.assertTrue(player.gameMode.destroyBlock(pos), "Actual survival server harvest accepts the iron pick");
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "The server removes the harvested obsidian");
            helper.assertValueEqual(countObsidian(drops), 1, "The iron pick produces exactly one obsidian item");
            helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 240,
                    "R196 obsidian hardness 2.4 costs 240 iron-pick durability per actual harvest");
            helper.assertValueEqual(player.getMainHandItem().getMaxDamage() - player.getMainHandItem().getDamageValue(), 9360,
                    "A fresh ordinary iron pick retains 9360 durability after one obsidian");

            helper.getLevel().setBlockAndUpdate(pos, obsidian);
            helper.assertTrue(player.gameMode.destroyBlock(pos), "The same iron pick can harvest another obsidian");
            helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 480,
                    "Two actual obsidian harvests apply the 240-point cost exactly twice");
            helper.assertValueEqual(countObsidian(drops), 2, "Two harvested blocks produce two obsidian items");

            PlayerExperience.setLevels(player, 200);
            for (ItemStack tool : new ItemStack[]{new ItemStack(MiteTools.get("mite:copper_pickaxe")), ItemStack.EMPTY}) {
                player.setItemSlot(EquipmentSlot.MAINHAND, tool);
                helper.getLevel().setBlockAndUpdate(pos, obsidian);
                helper.assertValueEqual(obsidian.getDestroyProgress(player, helper.getLevel(), pos), 0F,
                        "Copper and bare hands cannot gain obsidian progress even at maximum level");
                helper.assertFalse(player.gameMode.destroyBlock(pos), "The final server harvest rejects insufficient tools");
                helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.OBSIDIAN),
                        "Rejected harvesting retains the obsidian block");
                helper.assertValueEqual(countObsidian(drops), 2, "Rejected tools produce no additional obsidian drops");
                helper.assertValueEqual(tool.getDamageValue(), 0, "Rejected tools do not lose durability");
            }
        } finally {
            tileDrops.set(previousTileDrops, helper.getLevel().getServer());
            helper.getLevel().setBlockAndUpdate(pos, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaAndProfiledPickCallbacksUseR196ObsidianWearAndRespectExemptions(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            prepare(player);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
            for (ItemStack pick : new ItemStack[]{new ItemStack(Items.IRON_PICKAXE),
                    new ItemStack(MiteTools.get("mite:ancient_metal_pickaxe")),
                    new ItemStack(MiteTools.get("mite:mithril_pickaxe"))}) {
                player.setItemSlot(EquipmentSlot.MAINHAND, pick);
                helper.assertTrue(pick.isCorrectToolForDrops(obsidian), "The tested pick can harvest obsidian");
                pick.mineBlock(helper.getLevel(), obsidian, pos, player);
                helper.assertValueEqual(pick.getDamageValue(), 240,
                        "Vanilla and profiled picks both use R196 obsidian wear exactly once");
                pick.mineBlock(helper.getLevel(), Blocks.STONE.defaultBlockState(), pos, player);
                helper.assertValueEqual(pick.getDamageValue(), 480,
                        "The adjacent stone also uses its source hardness 2.4 and costs 240");
            }

            ItemStack weakPick = new ItemStack(MiteTools.get("mite:copper_pickaxe"));
            player.setItemSlot(EquipmentSlot.MAINHAND, weakPick);
            weakPick.mineBlock(helper.getLevel(), obsidian, pos, player);
            helper.assertValueEqual(weakPick.getDamageValue(), 0, "An insufficient profiled pick does not spend durability");

            ItemStack creativePick = new ItemStack(Items.IRON_PICKAXE);
            player.setItemSlot(EquipmentSlot.MAINHAND, creativePick);
            player.setGameMode(GameType.CREATIVE);
            creativePick.mineBlock(helper.getLevel(), obsidian, pos, player);
            helper.assertValueEqual(creativePick.getDamageValue(), 0, "Creative mining remains exempt from obsidian wear");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void levelHasteAirborneAndCanceledSpeedStillAffectObsidian(GameTestHelper helper) {
        Player player = miner(helper);
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        float baseline = obsidian.getDestroyProgress(player, helper.getLevel(), pos);
        PlayerExperience.setLevels(player, 35);
        assertClose(helper, obsidian.getDestroyProgress(player, helper.getLevel(), pos), baseline * 1.7F,
                "Experience scales the new obsidian progress once");
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 0));
        assertClose(helper, obsidian.getDestroyProgress(player, helper.getLevel(), pos), baseline * 1.7F * 1.2F,
                "Haste composes with the experience multiplier");
        player.setOnGround(false);
        assertClose(helper, obsidian.getDestroyProgress(player, helper.getLevel(), pos), baseline * 1.7F * 1.2F / 5F,
                "Airborne mining retains its fivefold penalty");

        Consumer<PlayerEvent.BreakSpeed> cancel = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerEvent.BreakSpeed.class, cancel);
        try {
            helper.assertValueEqual(obsidian.getDestroyProgress(player, helper.getLevel(), pos), 0F,
                    "A canceled BreakSpeed event still prevents all obsidian progress");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    private static Player miner(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        prepare(player);
        return player;
    }

    private static void prepare(Player player) {
        PlayerExperience.setTotal(player, 0);
        player.getFoodData().setFoodLevel(20);
        player.setOnGround(true);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
    }

    private static int countObsidian(SpawnedEntityCapture drops) {
        return drops.live(ItemEntity.class).stream().filter(entity -> entity.getItem().is(Items.OBSIDIAN))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "mite-obsidian"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            return new PlayerSession(player, channel);
        } catch (RuntimeException | Error failure) {
            ServerPlayer registered = helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID());
            if (registered != null) helper.getLevel().getServer().getPlayerList().remove(registered);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record PlayerSession(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override
        public void close() {
            try {
                ServerPlayer registered = player.server.getPlayerList().getPlayer(player.getUUID());
                if (registered != null) player.server.getPlayerList().remove(registered);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    private static void assertClose(GameTestHelper helper, float actual, float expected, String context) {
        float tolerance = Math.max(0.00000001F, Math.abs(expected) * 0.000001F);
        helper.assertTrue(Float.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                context + ": expected " + expected + ", actual " + actual);
    }
}
