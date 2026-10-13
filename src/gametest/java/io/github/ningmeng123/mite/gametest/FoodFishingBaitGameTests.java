package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Pinned native fishing methods verify bait wait speed and once-only consumption. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodFishingBaitGameTests {
    @GameTest(template = "empty")
    public static void hotbarWormDoublesWaitingProgressWithoutChangingTheBiteWindow(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            FishingHook hook = new FishingHook(f.player, helper.getLevel(), 0, 0);
            f.hook = hook;
            hook.moveTo(f.player.position());
            set(hook, "timeUntilLured", 1000);
            hook.getRandom().setSeed(196L);
            tickCatching(hook, f.player.blockPosition());
            int ordinary = 1000 - get(hook, "timeUntilLured");
            helper.assertTrue(ordinary > 0, "The real native method advances an unbaited open-sky waiting phase");
            f.player.getInventory().setItem(1, new ItemStack(MiteFoods.get("raw_worm"), 3));
            set(hook, "timeUntilLured", 1000);
            hook.getRandom().setSeed(196L);
            tickCatching(hook, f.player.blockPosition());
            helper.assertValueEqual(1000 - get(hook, "timeUntilLured"), ordinary * 2,
                    "The same weather/random input consumes exactly twice the wait countdown when bait is in the hotbar");
            helper.assertValueEqual(f.player.getInventory().getItem(1).getCount(), 3, "Waiting never spends the bait");
            set(hook, "nibble", 20);
            tickCatching(hook, f.player.blockPosition());
            helper.assertValueEqual(get(hook, "nibble"), 19, "Bait cannot halve the player's modern bite reaction window");
            f.player.getInventory().setItem(1, ItemStack.EMPTY);
            f.player.getInventory().setItem(12, new ItemStack(MiteFoods.get("raw_worm")));
            set(hook, "nibble", 0);
            set(hook, "timeUntilLured", 1000);
            hook.getRandom().setSeed(196L);
            tickCatching(hook, f.player.blockPosition());
            helper.assertValueEqual(1000 - get(hook, "timeUntilLured"), ordinary, "Worms outside the source hotbar slots do not speed fishing");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualApprovedCatchUsesOnlyOneFirstHotbarWormAndCanceledCatchUsesNone(GameTestHelper helper) {
        for (boolean cancel : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(helper)) {
                f.player.getInventory().setItem(1, new ItemStack(MiteFoods.get("raw_worm"), 3));
                f.player.getInventory().setItem(2, new ItemStack(MiteFoods.get("raw_worm"), 4));
                FishingHook hook = new FishingHook(f.player, helper.getLevel(), 0, 0);
                f.hook = hook;
                hook.moveTo(f.player.position());
                set(hook, "nibble", 20);
                int[] events = {0};
                Consumer<ItemFishedEvent> veto = event -> {
                    if (event.getEntity() == f.player) { events[0]++; if (cancel) event.setCanceled(true); }
                };
                NeoForge.EVENT_BUS.addListener(ItemFishedEvent.class, veto);
                try {
                    hook.retrieve(f.player.getMainHandItem());
                } finally { NeoForge.EVENT_BUS.unregister(veto); }
                helper.assertValueEqual(events[0], 1, "The native retrieval reaches the actual cancellable fishing event");
                helper.assertValueEqual(f.player.getInventory().getItem(1).getCount(), cancel ? 3 : 2,
                        "Only an approved actual catch consumes exactly one bait from the first hotbar slot");
                helper.assertValueEqual(f.player.getInventory().getItem(2).getCount(), 4, "A catch cannot consume a second worm stack");
                long newDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, f.player.getBoundingBox().inflate(5D)).stream()
                        .filter(entity -> !f.originalEntities.contains(entity.getId())).count();
                helper.assertTrue(cancel ? newDrops == 0 : newDrops > 0, "The bait transaction tracks a real spawned fishing result, not a direct test-only item grant");
            }
        }
        helper.succeed();
    }
    private static void set(FishingHook hook, String name, int value) {
        try { Field field = FishingHook.class.getDeclaredField(name); field.setAccessible(true); field.setInt(hook, value); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Pinned fishing field missing: " + name, error); }
    }
    private static int get(FishingHook hook, String name) {
        try { Field field = FishingHook.class.getDeclaredField(name); field.setAccessible(true); return field.getInt(hook); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Pinned fishing field missing: " + name, error); }
    }
    private static void tickCatching(FishingHook hook, BlockPos pos) {
        try { Method method = FishingHook.class.getDeclaredMethod("catchingFish", BlockPos.class); method.setAccessible(true); method.invoke(hook, pos); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Native fishing method failed", error); }
    }
    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final Set<Integer> originalEntities = new HashSet<>();
        private FishingHook hook;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-bait"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(2, 4, 2)).getCenter());
            player.setNoGravity(true);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
            for (Entity entity : helper.getLevel().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(5D))) originalEntities.add(entity.getId());
        }
        @Override public void close() {
            try {
                if (hook != null) hook.discard();
                for (Entity entity : helper.getLevel().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(5D)))
                    if (!originalEntities.contains(entity.getId()) && (entity instanceof ItemEntity || entity instanceof ExperienceOrb)) entity.discard();
                player.getInventory().clearContent();
                player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}
