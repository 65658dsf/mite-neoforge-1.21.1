package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.R196CoinItem;
import io.github.ningmeng123.mite.item.R196RecordItem;
import io.github.ningmeng123.mite.utility.UniqueMusicSavedData;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source-age and unique-record rules exercised through actual registered chest loot. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class RemainingChestContentGameTests {
    private static final String[] RECORDS = {"underworld", "descent", "wanderer", "legends"};
    private RemainingChestContentGameTests() {}

    @GameTest(template = "empty")
    public static void surfaceAndDesertCoinsUnlockAtSourceDayTwentyWithoutFollowingDaytimeCommands(GameTestHelper helper) {
        ServerLevel world = helper.getLevel().getServer().overworld();
        ServerLevelData clock = (ServerLevelData) world.getLevelData();
        long oldAge = world.getGameTime(), oldDaytime = world.getDayTime();
        try {
            for (String id : new String[]{"chests/simple_dungeon", "chests/desert_pyramid"}) {
                LootTable table = table(world, id);
                LootParams params = params(world, helper);
                clock.setGameTime(19L * 24000L - 6001L);
                world.setDayTime(100L * 24000L);
                int before = 0, after = 0;
                for (int seed = 1; seed <= 128; seed++)
                    before += table.getRandomItems(params, seed).stream().filter(s -> s.getItem() instanceof R196CoinItem).count();
                helper.assertValueEqual(before, 0, "Time-of-day commands cannot unlock source-day twenty coins: " + id);
                clock.setGameTime(19L * 24000L - 6000L);
                world.setDayTime(0);
                for (int seed = 1; seed <= 128; seed++)
                    after += table.getRandomItems(params, seed).stream().filter(s -> s.getItem() instanceof R196CoinItem).count();
                helper.assertTrue(after > 0, "Exact source-day twenty unlocks real chest coins: " + id);
            }
        } finally {
            clock.setGameTime(oldAge);
            world.setDayTime(oldDaytime);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void underworldRecordsSharePersistentClaimsAndRetainLiteralR196RetryBehavior(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerLevel underworld = world.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(underworld != null, "Registered source underworld exists");
        UniqueMusicSavedData claims = UniqueMusicSavedData.get(world);
        helper.assertTrue(claims == UniqueMusicSavedData.get(underworld), "Every dimension uses the same source signature storage");
        Set<String> previous = new LinkedHashSet<>();
        for (String name : RECORDS) { if (claims.has(name)) previous.add(name); claims.release(name); }
        try {
            LootTable table = table(underworld, "chests/simple_dungeon");
            LootParams params = params(underworld, helper);
            Set<String> found = new LinkedHashSet<>();
            for (int seed = 1; seed <= 512; seed++) {
                int recordsInChest = 0;
                for (var stack : table.getRandomItems(params, seed)) {
                    if (!(stack.getItem() instanceof R196RecordItem record)) continue;
                    recordsInChest += stack.getCount();
                    found.add(record.recordName());
                }
                helper.assertTrue(recordsInChest <= 9, "Eight source rolls plus one reserved signature bound literal fourth-retry record outcomes");
            }
            helper.assertValueEqual(found.size(), 4, "All four source record entries can be obtained through registered underworld chest loot");
            UniqueMusicSavedData loaded = UniqueMusicSavedData.load(claims.save(new CompoundTag(), world.registryAccess()), world.registryAccess());
            for (String name : RECORDS) helper.assertTrue(loaded.has(name), "Actual generated signatures survive serialization: " + name);
            claims.release("underworld");
            boolean regenerated = false;
            for (int seed = 513; seed <= 768; seed++) {
                for (var stack : table.getRandomItems(params, seed)) {
                    if (!(stack.getItem() instanceof R196RecordItem record)) continue;
                    if (record.recordName().equals("underworld")) regenerated = true;
                }
            }
            helper.assertTrue(regenerated, "A released signature becomes available to later real chests");
        } finally {
            for (String name : RECORDS) { claims.release(name); if (previous.contains(name)) claims.mark(name); }
        }
        helper.succeed();
    }

    private static LootTable table(ServerLevel world, String id) {
        return world.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace(id)));
    }
    private static LootParams params(ServerLevel world, GameTestHelper helper) {
        return new LootParams.Builder(world).withParameter(LootContextParams.ORIGIN,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)))).create(LootContextParamSets.CHEST);
    }
}
