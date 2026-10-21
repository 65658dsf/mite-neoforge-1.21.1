package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.mixin.SourceItemAgeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real item ticks, player drops, save migration and native expiry events. @author NingMeng123 */
@GameTestHolder("mite_item_lifetime")
@PrefixGameTestTemplate(false)
public final class DroppedItemLifetimeGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ordinaryItemSurvivesFiveMinutesAndExpiresAfterFifteen(GameTestHelper h) {
        ItemEntity item = item(h);
        h.assertValueEqual(item.lifespan, 18000, "Ordinary items receive fifteen minutes");
        for (int tick = 0; tick < 6000; ++tick) item.tick();
        h.assertTrue(item.isAlive() && item.getAge() == 6000, "The original five-minute deadline no longer clears the item");
        for (int tick = 6000; tick < 17999; ++tick) item.tick();
        h.assertTrue(item.isAlive() && item.getAge() == 17999, "The original entity survives the entire extended lifetime");
        item.tick();
        h.assertTrue(item.isRemoved(), "The item expires on exactly its 18000th entity tick");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void playerTossesAndDeathInventoryDropsHaveFifteenMinutes(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        try (var capture = new SpawnedEntityCapture(h.getLevel(), e -> e instanceof ItemEntity)) {
            ItemEntity tossed = player.drop(new ItemStack(Items.COBBLESTONE), false);
            h.assertTrue(tossed != null && tossed.lifespan == 18000, "The actual player toss path extends its world drop");
            player.getInventory().setItem(0, new ItemStack(Items.DIRT, 3));
            player.getInventory().dropAll();
            var deathDrops = capture.live(ItemEntity.class).stream().filter(e -> e.getItem().is(Items.DIRT)).toList();
            h.assertTrue(deathDrops.size() == 1 && deathDrops.getFirst().lifespan == 18000,
                    "The death inventory drop path extends its ground item too");
        } finally {
            player.server.getPlayerList().remove(player);
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void savingAndLoadingRetainsElapsedTimeAndMigratesOldDrops(GameTestHelper h) {
        ItemEntity original = item(h);
        ((SourceItemAgeAccess)original).mite$setAge(12345);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        ItemEntity loaded = load(h, saved);
        h.assertTrue(loaded.getAge() == 12345 && loaded.lifespan == 18000,
                "Reloading retains the remaining 5655 ticks rather than restarting the timer");
        saved.putInt("Lifespan", 6000);
        saved.putShort("Age", (short)5999);
        loaded = load(h, saved);
        loaded.tick();
        h.assertTrue(loaded.isAlive() && loaded.getAge() == 6000 && loaded.lifespan == 18000,
                "An old saved five-minute drop migrates without losing its elapsed age");
        saved.remove("Lifespan");
        loaded = load(h, saved);
        h.assertTrue(loaded.lifespan == 18000 && loaded.getAge() == 5999,
                "Legacy saves without NeoForge's lifespan field use the new default");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void customLifetimesUnlimitedItemsAndFakeItemsKeepTheirSemantics(GameTestHelper h) {
        ItemEntity item = item(h);
        item.lifespan = 24000;
        ((SourceItemAgeAccess)item).mite$setAge(17000);
        ItemEntity custom = load(h, item.saveWithoutId(new CompoundTag()));
        h.assertTrue(custom.lifespan == 24000 && custom.getAge() == 17000,
                "An explicitly customized lifespan survives reload");
        item.setUnlimitedLifetime();
        ItemEntity unlimited = load(h, item.saveWithoutId(new CompoundTag()));
        unlimited.tick();
        h.assertTrue(unlimited.isAlive() && unlimited.getAge() == -32768, "Unlimited items keep the native sentinel age");
        ItemEntity fake = item(h);
        fake.makeFakeItem();
        fake.tick();
        h.assertTrue(fake.isRemoved(), "A fake item still disappears on its next tick");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeExpiryListenersCanStillExtendTheDeadline(GameTestHelper h) {
        ItemEntity item = item(h);
        java.util.function.Consumer<ItemExpireEvent> listener = event -> {
            if (event.getEntity() == item) event.addExtraLife(20);
        };
        NeoForge.EVENT_BUS.addListener(ItemExpireEvent.class, listener);
        try {
            ((SourceItemAgeAccess)item).mite$setAge(17999);
            item.tick();
            h.assertTrue(item.isAlive() && item.lifespan == 18020, "Native expiry listeners still extend the fifteen-minute deadline");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        for (int tick = 0; tick < 20; ++tick) item.tick();
        h.assertTrue(item.isRemoved(), "After the listener is removed, the extended deadline expires normally");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void itemsRemainMergeableBeyondTheOriginalDeadline(GameTestHelper h) throws Exception {
        ItemEntity item = item(h);
        var mergeable = ItemEntity.class.getDeclaredMethod("isMergable");
        mergeable.setAccessible(true);
        ((SourceItemAgeAccess)item).mite$setAge(12000);
        h.assertTrue((boolean)mergeable.invoke(item), "Ten-minute-old items remain eligible for normal stacking");
        ((SourceItemAgeAccess)item).mite$setAge(18000);
        h.assertFalse((boolean)mergeable.invoke(item), "Expired items cannot merge");
        h.succeed();
    }

    private static ItemEntity item(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 3, 1));
        ItemEntity item = new ItemEntity(h.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.COBBLESTONE));
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        return item;
    }

    private static ItemEntity load(GameTestHelper h, CompoundTag tag) {
        ItemEntity loaded = new ItemEntity(EntityType.ITEM, h.getLevel());
        loaded.load(tag);
        return loaded;
    }
}
