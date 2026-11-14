package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.ranged.FishingHookView;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Pinned fishing methods verify source geometric probability and once-only bait consumption. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodFishingBaitGameTests {
    @GameTest(template = "ranged_027")
    public static void realBiteMethodUsesR196GeometricRollAndOnlyHotbarBait(GameTestHelper helper) {
        long oldTime = helper.getLevel().getDayTime();
        try (Fixture f = new Fixture(helper)) {
            helper.getLevel().setDayTime(23500L); // R196 adjusted 5500: source base denominator 600.
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 4, 7))) helper.setBlock(pos, Blocks.WATER);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 5, 1), new BlockPos(7, 5, 7))) helper.setBlock(pos, Blocks.AIR);
            FishingHook hook = new FishingHook(f.player, helper.getLevel(), 0, 0);
            f.hook = hook;
            BlockPos water = helper.absolutePos(new BlockPos(4, 4, 4));
            hook.moveTo(water.getX() + .5D, water.getY() + .21D, water.getZ() + .5D);
            int unbaited = helper.getLevel().isRainingAt(water.above()) ? 300 : 600;
            int successes = 0;
            for (int baitSlot : new int[]{-1, 1, 12}) {
                f.player.getInventory().setItem(1, ItemStack.EMPTY);
                f.player.getInventory().setItem(12, ItemStack.EMPTY);
                if (baitSlot >= 0) f.player.getInventory().setItem(baitSlot, new ItemStack(MiteFoods.get("raw_worm"), 3));
                int denominator = baitSlot == 1 ? unbaited / 2 : unbaited;
                for (int seed = 0; seed < 2048; seed++) {
                    RandomSource oracle = RandomSource.create(seed);
                    oracle.nextInt(7); oracle.nextInt(4); oracle.nextInt(7);
                    boolean expected = oracle.nextInt(denominator) == 0;
                    int expectedWindow = expected ? oracle.nextInt(30) + 30 : 0;
                    set(hook, "nibble", 0);
                    set(hook, "timeUntilLured", 1000);
                    hook.getRandom().setSeed(seed);
                    tickCatching(hook, water);
                    helper.assertValueEqual(get(hook, "nibble"), expectedWindow,
                            "Exact source sample order, denominator and 30..59 tick reaction window match native fishing at seed " + seed + " slot " + baitSlot);
                    helper.assertValueEqual(get(hook, "timeUntilLured"), 0, "The modern fixed countdown does not replace source geometric rolls");
                    if (expected) successes++;
                }
                if (baitSlot >= 0) helper.assertValueEqual(f.player.getInventory().getItem(baitSlot).getCount(), 3, "Waiting never spends bait");
            }
            helper.assertTrue(successes > 0, "The sample includes actual successful native bites");
            set(hook, "nibble", 20);
            tickCatching(hook, water);
            helper.assertValueEqual(get(hook, "nibble"), 19, "Bait leaves the reaction window countdown at exactly one tick per tick");
        } finally { helper.getLevel().setDayTime(oldTime); }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
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
    @GameTest(template = "ranged_027")
    public static void EveryRealHookCanCastAndReelAnEntityUsingThreeSourceDurability(GameTestHelper helper) {
        for (var material : MiteRangedItems.rods()) try (Fixture f = new Fixture(helper)) {
            f.player.setOnGround(true);
            ItemStack rod = new ItemStack(MiteRangedItems.rod(material));
            f.player.setItemInHand(InteractionHand.MAIN_HAND, rod);
            ((FishingRodItem)rod.getItem()).use(helper.getLevel(), f.player, InteractionHand.MAIN_HAND);
            helper.assertTrue(f.player.fishing != null, "Actual source hook material casts a native bobber: " + material.id);
            f.hook = f.player.fishing;
            try {
                Field field = FishingHook.class.getDeclaredField("hookedIn"); field.setAccessible(true);
                field.set(f.hook, new Pig(EntityType.PIG, helper.getLevel()));
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            ((FishingRodItem)rod.getItem()).use(helper.getLevel(), f.player, InteractionHand.MAIN_HAND);
            helper.assertTrue(f.player.fishing == null, "Actual retrieval clears the owner hook");
            if (material == io.github.ningmeng123.mite.rules.R196RangedRules.Material.FLINT)
                helper.assertTrue(rod.isEmpty(), "Three source entity-reel wear breaks the three-durability flint hook");
            else helper.assertValueEqual(rod.getDamageValue(), 3, "Source living-entity reeling costs three rather than modern five");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void SourceCastRequiresFootingAndRejectsASubmergedHead(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.player.setOnGround(false);
            ((FishingRodItem)Items.FISHING_ROD).use(helper.getLevel(), f.player, InteractionHand.MAIN_HAND);
            helper.assertTrue(f.player.fishing == null, "R196 does not cast while airborne");
            f.player.setOnGround(true);
            BlockPos head = BlockPos.containing(f.player.getX(), f.player.getEyeY(), f.player.getZ());
            var old = helper.getLevel().getBlockState(head);
            helper.getLevel().setBlockAndUpdate(head, Blocks.WATER.defaultBlockState());
            try {
                ((FishingRodItem)Items.FISHING_ROD).use(helper.getLevel(), f.player, InteractionHand.MAIN_HAND);
                helper.assertTrue(f.player.fishing == null, "The exact modern head fluid state rejects underwater casting even before an entity tick");
            } finally { helper.getLevel().setBlockAndUpdate(head, old); }
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void mountedBoatAndHorseCastWithoutFootingButMinecartDoesNotReceiveTheHorseException(GameTestHelper helper) {
        for (EntityType<?> type : new EntityType<?>[]{EntityType.BOAT, EntityType.HORSE, EntityType.MINECART}) {
            try (Fixture f = new Fixture(helper)) {
                Entity vehicle = type.create(helper.getLevel());
                helper.assertTrue(vehicle != null, "Registered native mount can be created");
                vehicle.moveTo(f.player.position()); vehicle.setNoGravity(true);
                helper.getLevel().addFreshEntity(vehicle);
                try {
                    helper.assertTrue(f.player.startRiding(vehicle, true), "The test uses actual native riding state");
                    f.player.setOnGround(false);
                    ((FishingRodItem)Items.FISHING_ROD).use(helper.getLevel(), f.player, InteractionHand.MAIN_HAND);
                    boolean expected = type != EntityType.MINECART;
                    f.hook = f.player.fishing;
                    helper.assertTrue((f.hook != null) == expected, "Boat and Horse are source exceptions; a minecart still needs ordinary footing");
                } finally { f.player.stopRiding(); vehicle.discard(); }
            }
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void SourceAdamantiumHookResistsFireAndOtherBurningHooksWearTheHeldRod(GameTestHelper helper) {
        for (var material : MiteRangedItems.rods()) try (Fixture f = new Fixture(helper)) {
            ItemStack rod = new ItemStack(MiteRangedItems.rod(material));
            f.player.setItemInHand(InteractionHand.MAIN_HAND, rod);
            FishingHook hook = new FishingHook(f.player, helper.getLevel(), 0, 0);
            f.hook = hook;
            boolean immune = material == io.github.ningmeng123.mite.rules.R196RangedRules.Material.ADAMANTIUM;
            helper.assertTrue(hook.fireImmune() == immune, "Native hook captures source material fire/lava behavior at cast time");
            hook.setRemainingFireTicks(20);
            hook.tickCount = 4;
            helper.getLevel().tickNonPassenger(hook);
            helper.assertValueEqual(rod.getDamageValue(), immune ? 0 : 1, "ul wears the held rod once each five burning hook ticks, with adamantium immunity");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void OffhandCastingCapturesTheActualHookAndBurnsOnlyThatHand(GameTestHelper helper) {
        var flint = io.github.ningmeng123.mite.rules.R196RangedRules.Material.FLINT;
        var adamantium = io.github.ningmeng123.mite.rules.R196RangedRules.Material.ADAMANTIUM;
        for (boolean offhandImmune : new boolean[]{true, false}) try (Fixture f = new Fixture(helper)) {
            ItemStack main = new ItemStack(MiteRangedItems.rod(offhandImmune ? flint : adamantium));
            ItemStack off = new ItemStack(MiteRangedItems.rod(offhandImmune ? adamantium : flint));
            f.player.setItemInHand(InteractionHand.MAIN_HAND, main);
            f.player.setItemInHand(InteractionHand.OFF_HAND, off);
            f.player.setOnGround(true);
            ((FishingRodItem)off.getItem()).use(helper.getLevel(), f.player, InteractionHand.OFF_HAND);
            f.hook = f.player.fishing;
            helper.assertTrue(f.hook != null, "Actual offhand item-use creates a hook even with a different rod in the main hand");
            var view = (FishingHookView)f.hook;
            helper.assertTrue(view.mite$castHand() == InteractionHand.OFF_HAND, "Tracked hook records the actual casting hand");
            helper.assertTrue(view.mite$hookMaterial().equals(offhandImmune ? "adamantium" : "flint")
                    && f.hook.fireImmune() == offhandImmune, "A main-hand rod cannot override the offhand hook's material or fire immunity");
            f.hook.setRemainingFireTicks(20); f.hook.tickCount = 4; helper.getLevel().tickNonPassenger(f.hook);
            helper.assertValueEqual(main.getDamageValue(), 0, "A burning offhand cast does not damage the other hand's rod");
            helper.assertValueEqual(off.getDamageValue(), offhandImmune ? 0 : 1, "Only the actual non-immune casting hand receives source fire wear");
            FishingHook trackedPeer = new FishingHook(EntityType.FISHING_BOBBER, helper.getLevel());
            var metadata = f.hook.getEntityData().getNonDefaultValues();
            if (metadata != null) trackedPeer.getEntityData().assignValues(metadata);
            helper.assertTrue(((FishingHookView)trackedPeer).mite$castHand() == InteractionHand.OFF_HAND,
                    "Native entity metadata also synchronizes the casting hand used by client cast models");
            trackedPeer.discard();
        }
        helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void blueMoonBitesUseTheSourceDuskBoundaryInsteadOfVanillaNight(GameTestHelper helper) {
        long old=helper.getLevel().getDayTime();
        try(Fixture f=new Fixture(helper)) {
            for(BlockPos p:BlockPos.betweenClosed(new BlockPos(1,1,1),new BlockPos(7,4,7))) helper.setBlock(p,Blocks.WATER);
            for(BlockPos p:BlockPos.betweenClosed(new BlockPos(1,5,1),new BlockPos(7,5,7))) helper.setBlock(p,Blocks.AIR);
            FishingHook hook=new FishingHook(f.player,helper.getLevel(),0,0);f.hook=hook;
            BlockPos water=helper.absolutePos(new BlockPos(4,4,4));hook.moveTo(water.getX()+.5D,water.getY()+.21D,water.getZ()+.5D);
            // Source float multiplication truncates 1498.9998779296875, rather than exact rational 1499.
            int[] times={12999,13000,18000,23000,23001};int[] sourceDenominators={1498,600,600,600,600};
            for(int i=0;i<times.length;i++) {
                helper.getLevel().setDayTime(127L*24000+times[i]);
                int denominator=sourceDenominators[i]/(helper.getLevel().isRainingAt(water.above())?2:1);
                for(int seed=0;seed<2048;seed++) {
                    RandomSource oracle=RandomSource.create(seed);oracle.nextInt(7);oracle.nextInt(4);oracle.nextInt(7);
                    int window=oracle.nextInt(denominator)==0?oracle.nextInt(30)+30:0;
                    set(hook,"nibble",0);hook.getRandom().setSeed(seed);tickCatching(hook,water);
                    helper.assertValueEqual(get(hook,"nibble"),window,"Actual blue-date fishing at source boundary "+times[i]+" seed "+seed);
                }
            }
        } finally {helper.getLevel().setDayTime(old);}helper.succeed();
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
            NetworkRegistry.configureMockConnection(connection);
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
