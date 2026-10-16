package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered materials, actual server mining/combat and persisted tool wear. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FlintToolGameTests {
    @GameTest(template = "empty")
    public static void registeredItemsApplyToolSpeedsAndActualAttackWear(GameTestHelper helper) {
        helper.assertValueEqual(new ItemStack(MiteItems.FLINT_CHIP.get()).getMaxStackSize(), 64, "Flint chips stack to 64");
        helper.assertValueEqual(new ItemStack(MiteItems.SINEW.get()).getMaxStackSize(), 16, "Sinew stacks to 16");
        Cow target = helper.spawn(EntityType.COW, new BlockPos(1, 2, 0));
        target.setNoAi(true);
        target.setNoGravity(true);
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            ItemStack hatchet = player.getMainHandItem();
            helper.assertValueEqual(hatchet.getMaxStackSize(), 1, "The hatchet cannot stack");
            helper.assertValueEqual(hatchet.getMaxDamage(), 400, "The unenchanted average-quality hatchet has 400 durability");
            helper.assertValueEqual(player.getDigSpeed(Blocks.OAK_LOG.defaultBlockState(), target.blockPosition()), 2.5F,
                    "An actual fed level-zero player mines axe-tagged wood at strength 2.5");
            helper.assertValueEqual(player.getDigSpeed(Blocks.STONE.defaultBlockState(), target.blockPosition()), 1.0F,
                    "Stone receives no effective tool speed");
            for (int i = 0; i < 21; i++) player.doTick();
            player.setOnGround(true);
            player.setSprinting(false);
            helper.assertValueEqual(player.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.0,
                    "Equipment updates apply the +3 attack modifier above the player's base 1");
            helper.assertValueEqual(player.getAttributeValue(Attributes.ATTACK_SPEED), 1.0,
                    "The modern cooldown allows one fully charged attack per second");
            float before = target.getHealth();
            player.attack(target);
            helper.assertTrue(target.getHealth() < before, "The normal player attack damages a living target");
            helper.assertValueEqual(hatchet.getDamageValue(), 133, "One successful attack spends 133 durability, once");

            player.setGameMode(GameType.CREATIVE);
            ItemStack creativeHatchet = new ItemStack(MiteItems.FLINT_HATCHET.get());
            player.setItemSlot(EquipmentSlot.MAINHAND, creativeHatchet);
            target.invulnerableTime = 0;
            target.setHealth(target.getMaxHealth());
            player.attack(target);
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "The creative attack still reaches the target");
            helper.assertValueEqual(creativeHatchet.getDamageValue(), 0, "Modern creative attacks do not consume durability");
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualLogBreaksWearSaveReloadAndBreakOnTheThirdBlock(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        try (BlockFixture fixture = new BlockFixture(helper, position); PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            for (int count = 1; count <= 3; count++) {
                helper.getLevel().setBlockAndUpdate(position, Blocks.OAK_LOG.defaultBlockState());
                helper.assertValueEqual(helper.getLevel().getBlockState(position).getDestroySpeed(helper.getLevel(), position), 2.0F,
                        "The local 1.2 mining hardness does not change the registered modern log hardness");
                helper.assertTrue(player.gameMode.destroyBlock(position), "Actual server block destruction succeeds");
                helper.assertTrue(helper.getLevel().getBlockState(position).isAir(), "Each log is removed from the world");
                if (count < 3) {
                    helper.assertValueEqual(player.getMainHandItem().getDamageValue(), count == 1 ? 160 : 320,
                            "Effective log hardness 1.2 gives the recorded 160-point wear increment");
                    if (count == 1) {
                        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(),
                                player.getMainHandItem().save(helper.getLevel().registryAccess())).orElseThrow();
                        helper.assertTrue(restored.is(MiteItems.FLINT_HATCHET.get()), "The saved stack retains its registered item");
                        helper.assertValueEqual(restored.getDamageValue(), 160, "Stack codec round-trip retains spent durability");
                        helper.assertValueEqual(restored.getMaxDamage(), 400, "Stack codec round-trip retains the durability capacity");
                        player.setItemSlot(EquipmentSlot.MAINHAND, restored);
                    }
                } else {
                    helper.assertTrue(player.getMainHandItem().isEmpty(), "The third log breaks the 400-point hatchet");
                }
            }
            helper.assertValueEqual(fixture.countDrops(Items.OAK_LOG), 3,
                    "The normal server harvest path drops all three logs, including the breaking-tool harvest");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wrongToolZeroHardnessAndCreativeMiningDoNotSpendWear(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        try (BlockFixture fixture = new BlockFixture(helper, position); PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            ItemStack hatchet = player.getMainHandItem();
            BlockState stone = Blocks.STONE.defaultBlockState();
            helper.getLevel().setBlockAndUpdate(position, stone);
            helper.assertFalse(stone.canHarvestBlock(helper.getLevel(), position, player), "A flint hatchet cannot harvest stone drops");
            helper.assertFalse(player.gameMode.destroyBlock(position), "R196 tool qualification rejects wrong-tool final removal");
            helper.assertTrue(helper.getLevel().getBlockState(position).is(Blocks.STONE), "The rejected block stays in the world");
            helper.assertValueEqual(fixture.countDrops(Items.COBBLESTONE), 0, "Rejected harvest produces no cobblestone");
            helper.assertValueEqual(hatchet.getDamageValue(), 0, "Ineffective mining does not spend hatchet durability");

            BlockState mushroom = Blocks.BROWN_MUSHROOM.defaultBlockState();
            helper.assertTrue(mushroom.is(BlockTags.MINEABLE_WITH_AXE), "The zero-hardness fixture is an effective axe target");
            helper.assertValueEqual(mushroom.getDestroySpeed(helper.getLevel(), position), 0.0F, "The mushroom has zero hardness");
            hatchet.mineBlock(helper.getLevel(), mushroom, position, player);
            helper.assertValueEqual(hatchet.getDamageValue(), 0, "The normal stack mining callback spends no wear on an effective zero-hardness block");

            player.setGameMode(GameType.CREATIVE);
            helper.getLevel().setBlockAndUpdate(position, Blocks.OAK_LOG.defaultBlockState());
            helper.assertTrue(player.gameMode.destroyBlock(position), "Actual creative block removal succeeds");
            helper.assertValueEqual(hatchet.getDamageValue(), 0, "Actual creative mining does not spend wear");
            for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
                player.setGameMode(mode);
                hatchet.mineBlock(helper.getLevel(), Blocks.OAK_LOG.defaultBlockState(), position, player);
                helper.assertValueEqual(hatchet.getDamageValue(), 0, "The stack callback also bypasses wear in " + mode);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualRightClickDoesNotStripLogsOrSpendDurability(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState previous = helper.getLevel().getBlockState(position);
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            ItemStack hatchet = player.getMainHandItem();
            helper.getLevel().setBlockAndUpdate(position, Blocks.OAK_LOG.defaultBlockState());
            helper.assertTrue(hatchet.canPerformAction(ItemAbilities.AXE_DIG), "The hatchet advertises its intended mining action");
            helper.assertFalse(hatchet.canPerformAction(ItemAbilities.AXE_STRIP), "The hatchet does not advertise the modern strip action");
            InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), hatchet, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
            helper.assertValueEqual(result, InteractionResult.PASS, "Normal server right-click passes without consuming the action");
            helper.assertTrue(helper.getLevel().getBlockState(position).is(Blocks.OAK_LOG), "The original unstripped log remains");
            helper.assertValueEqual(hatchet.getDamageValue(), 0, "Right-click does not spend durability");
        } finally {
            helper.getLevel().setBlockAndUpdate(position, previous);
        }
        helper.succeed();
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-flint-test"), false);
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
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteItems.FLINT_HATCHET.get()));
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

    private static final class BlockFixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final BlockPos position;
        private final BlockState previous;
        private final AABB area;
        private final Set<UUID> existingDrops = new HashSet<>();
        private final SpawnedEntityCapture spawned;
        private final boolean previousTileDrops;

        private BlockFixture(GameTestHelper helper, BlockPos position) {
            this.helper = helper;
            this.position = position;
            this.previous = helper.getLevel().getBlockState(position);
            this.area = new AABB(position).inflate(1.0);
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) existingDrops.add(item.getUUID());
            // Newly generated grid chunks may not have accessible entity sections until the next tick.
            // Observe accepted spawns at this fixture instead of querying that still-hidden spatial index.
            spawned = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity
                    && !existingDrops.contains(entity.getUUID()) && entity.getBoundingBox().intersects(area));
            GameRules.BooleanValue drops = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);
            previousTileDrops = drops.get();
            drops.set(true, helper.getLevel().getServer());
        }

        private int countDrops(net.minecraft.world.item.Item item) {
            return spawned.live(ItemEntity.class).stream()
                    .filter(entity -> entity.getItem().is(item))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
        }

        @Override
        public void close() {
            try {
                helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(previousTileDrops, helper.getLevel().getServer());
                helper.getLevel().setBlockAndUpdate(position, previous);
            } finally {
                spawned.close();
            }
        }
    }
}
