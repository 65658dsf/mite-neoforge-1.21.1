package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.advancement.MiteAchievements;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Completed manufacturing, dimension events and shared persistent structure progress. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StructureProgressDataGameTests {
    @GameTest(template = "empty")
    public static void actualIronPickManufactureUnlocksOnlyOnItsFinalTick(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            f.loadIronPick();
            helper.assertTrue(R196StructureGeneration.qualifies(f.menu.getSlot(0).getItem()),
                    "The actual R196 iron pick is effective against obsidian");
            world.assertConditions(0);
            helper.assertFalse(world.unlocked(), "Day sixty by itself cannot unlock a village");
            int period = f.period();
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(period - 1);
            helper.assertValueEqual(TimedCraftingController.progress(f.player), period - 1,
                    "The real manufacture session reaches its final pending tick");
            f.assertIngredients();
            helper.assertValueEqual(f.player.getInventory().countItem(Items.IRON_PICKAXE), 0,
                    "The preview is not a published tool");
            world.assertConditions(0);
            helper.assertFalse(world.unlocked(), "An almost-complete recipe cannot unlock villages");
            f.tick(1);
            helper.assertValueEqual(f.player.getInventory().countItem(Items.IRON_PICKAXE), 1,
                    "A legal completed recipe delivers exactly one tool");
            world.assertConditions(16);
            helper.assertTrue(world.unlocked(), "The successful final transaction publishes the village snapshot immediately");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledAndInvalidatedManufactureNeverUnlocksVillages(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            f.loadIronPick();
            int period = f.period();
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(period / 2);
            helper.assertTrue(TimedCraftingController.isActive(f.player), "A real manufacture batch has started");
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            f.tick(period);
            helper.assertFalse(TimedCraftingController.isActive(f.player), "Right-click canceled the running batch");
            f.assertIngredients();
            world.assertConditions(0);

            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(period - 1);
            f.level.setBlockAndUpdate(f.table, Blocks.AIR.defaultBlockState());
            f.tick(1);
            helper.assertFalse(TimedCraftingController.isActive(f.player), "Removing the real bench fails final validation");
            f.assertIngredients();
            helper.assertValueEqual(f.player.getInventory().countItem(Items.IRON_PICKAXE), 0,
                    "Neither canceled nor invalid manufacture delivers a tool");
            world.assertConditions(0);
            helper.assertFalse(world.unlocked(), "Failed manufacturing cannot fulfill world progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mithrilBowRequiresAnActualCompletedManufacture(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            f.loadMithrilBow();
            world.assertConditions(0);
            int period = f.period();
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(period / 2);
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
            f.tick(period);
            helper.assertFalse(TimedCraftingController.isActive(f.player), "Canceling the reinforced bow stops its manufacture");
            world.assertConditions(0);
            helper.assertValueEqual(f.player.getInventory().countItem(MiteWeaponContent.MITHRIL_BOW.get()), 0,
                    "A preview and a canceled recipe deliver no reinforced bow");

            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            f.tick(period - 1);
            world.assertConditions(0);
            f.tick(1);
            helper.assertValueEqual(f.player.getInventory().countItem(MiteWeaponContent.MITHRIL_BOW.get()), 1,
                    "The successful final transaction delivers the reinforced bow");
            world.assertConditions(StructureProgressData.BOW_CRAFTED | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED);
            helper.assertFalse(world.unlocked(), "New structure progress cannot satisfy the village's old tool prerequisite");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftedEquipmentRecognizesEveryMithrilCategoryAndRejectsMaterials(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            for (Item item : List.of(MiteTools.get("mithril_hatchet"), MiteTools.get("mithril_sword"),
                    MiteArmorContent.get("mite:mithril_helmet"), MiteArmorContent.get("mite:mithril_chain_helmet"))) {
                world.data.restoreConditions(0);
                R196StructureGeneration.crafted(f.player, new ItemStack(item));
                world.assertConditions(StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED);
            }
            world.data.restoreConditions(0);
            R196StructureGeneration.crafted(f.player, new ItemStack(MiteTools.get("mithril_pickaxe")));
            world.assertConditions(StructureProgressData.OBSIDIAN_TOOL | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED);
            world.data.restoreConditions(0);
            for (Item item : List.of(Items.BOW, MiteWeaponContent.ANCIENT_METAL_BOW.get())) {
                R196StructureGeneration.crafted(f.player, new ItemStack(item));
                world.assertConditions(StructureProgressData.BOW_CRAFTED);
            }
            world.data.restoreConditions(0);
            for (Item item : List.of(MiteMaterials.MITHRIL_INGOT.get(), MiteMaterials.MITHRIL_NUGGET.get(),
                    MiteArmorContent.chain("mithril"), MiteArmorContent.get("mite:mithril_horse_armor"),
                    MiteTools.get("adamantium_sword"), Items.ARROW)) {
                R196StructureGeneration.crafted(f.player, new ItemStack(item));
                world.assertConditions(0);
            }
            R196StructureGeneration.crafted(f.player, ItemStack.EMPTY);
            world.assertConditions(0);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void differentPlayersShareTheirCompletedEquipmentProgress(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture archer = new Fixture(helper); Fixture smith = new Fixture(helper)) {
            R196StructureGeneration.crafted(archer.player, new ItemStack(Items.BOW));
            world.assertConditions(StructureProgressData.BOW_CRAFTED);
            R196StructureGeneration.crafted(smith.player, new ItemStack(MiteTools.get("mithril_sword")));
            world.assertConditions(StructureProgressData.BOW_CRAFTED | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED);
            helper.assertTrue(StructureProgressData.get(archer.player.serverLevel()) == StructureProgressData.get(smith.player.serverLevel()),
                    "Different players fulfill prerequisites in the same world save");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void previewsOwnershipAndActualToolPickupDoNotCountAsCrafting(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            f.loadIronPick();
            for (int quality = 0; quality < 8; quality++) {
                TimedCraftingController.selectQuality(f.player, f.menu, 1);
                f.tick(1);
                world.assertConditions(0);
            }
            f.player.getInventory().clearContent();
            f.pickup(Items.IRON_PICKAXE);
            f.player.getInventory().selected = 0;
            helper.assertTrue(f.player.getMainHandItem().is(Items.IRON_PICKAXE), "The player actually holds the picked-up tool");
            helper.assertValueEqual(f.player.getStats().getValue(Stats.ITEM_CRAFTED.get(Items.IRON_PICKAXE)), 0,
                    "Item pickup never fabricates completed crafting statistics");
            f.pickup(Items.BOW);
            f.pickup(MiteTools.get("mithril_sword"));
            f.pickup(MiteWeaponContent.MITHRIL_BOW.get());
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(f.player));
            world.assertConditions(0);
            helper.assertFalse(world.unlocked(), "Inventory ownership cannot migrate into a crafted-tool prerequisite");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourActualFoodPickupsSetOnlyTheirBitsAndNeverUnlockVillages(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            Item onion = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mite", "onion"));
            helper.assertTrue(onion != Items.AIR, "The real registered onion is available");
            Item[] foods = {Items.WHEAT, Items.CARROT, Items.POTATO, onion};
            int[] expected = {1, 3, 7, 15};
            for (int index = 0; index < foods.length; index++) {
                f.pickup(foods[index]);
                world.assertConditions(expected[index]);
                helper.assertFalse(world.unlocked(), "Crop bits remain below the source integer prerequisite 16");
            }
            f.pickup(Items.WHEAT);
            world.assertConditions(15);
            helper.assertFalse(world.unlocked(), "Repeated food pickups OR their bits rather than incrementing progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedNbtReloadRestoresVersionedConditionsAndMasksUnknownBits(GameTestHelper helper) throws Exception {
        try (WorldState world = new WorldState(helper)) {
            int conditions = StructureProgressData.WHEAT | StructureProgressData.POTATO | StructureProgressData.OBSIDIAN_TOOL
                    | StructureProgressData.UNDERWORLD_VISITED | StructureProgressData.BOW_CRAFTED
                    | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED;
            world.data.fulfill(conditions);
            CompoundTag serialized = world.data.save(new CompoundTag(), world.level.registryAccess());
            helper.assertValueEqual(serialized.getInt("version"), 2, "The saved schema records its version");
            helper.assertValueEqual(serialized.getInt("structure_conditions"), conditions, "Every structure prerequisite bit is serialized");
            helper.assertValueEqual(serialized.getInt("village_conditions"), 21, "The compatibility field retains only the original village bits");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(serialized, bytes);
            CompoundTag decoded = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
            StructureProgressData restored = StructureProgressData.load(decoded, world.level.registryAccess());
            helper.assertTrue(restored != world.data, "The reload constructs a fresh SavedData instance from real NBT bytes");
            helper.assertValueEqual(restored.conditions(), conditions, "The full shared prerequisite state survives serialization");
            world.level.getDataStorage().set(StructureProgressData.NAME, restored);
            R196StructureGeneration.refresh(world.level);
            helper.assertTrue(StructureProgressData.get(world.level) == restored && world.unlocked(),
                    "The reloaded world data repopulates the generation snapshot");
            decoded.putInt("structure_conditions", 511);
            decoded.putInt("village_conditions", 0);
            helper.assertValueEqual(StructureProgressData.load(decoded, world.level.registryAccess()).conditions(), 255,
                    "The new field wins over the compatibility field and masks unknown future bits");
            decoded.remove("structure_conditions");
            decoded.putInt("version", 1);
            decoded.putInt("village_conditions", 63);
            helper.assertValueEqual(StructureProgressData.load(decoded, world.level.registryAccess()).conditions(), 31,
                    "Legacy data cannot reinterpret its unknown bits as newly added prerequisites");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void progressFromEveryDimensionUsesTheSameOverworldSaveAndSnapshot(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper)) {
            ServerLevel underworld = world.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(underworld != null, "The real registered underground dimension is loaded");
            for (var dimension : List.of(Level.OVERWORLD, MiteWorldDimensions.UNDERWORLD, Level.NETHER, Level.END)) {
                ServerLevel level = world.level.getServer().getLevel(dimension);
                helper.assertTrue(level != null && StructureProgressData.get(level) == world.data,
                        "All four dimensions reference the identical overworld SavedData");
            }
            StructureProgressData.get(underworld).fulfill(StructureProgressData.OBSIDIAN_TOOL);
            world.assertConditions(16);
            helper.assertTrue(world.unlocked(), "A prerequisite fulfilled underground updates overworld generation immediately");
            StructureProgressData.get(world.level).fulfill(StructureProgressData.CARROT);
            helper.assertValueEqual(StructureProgressData.get(underworld).conditions(), 18,
                    "Progress added on the surface remains visible underground without synchronization loss");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loginMigrationRequiresACompletedQualifyingToolStatistic(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            f.player.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
            f.player.getStats().setValue(f.player, Stats.ITEM_CRAFTED.get(MiteTools.get("copper_pickaxe")), 1);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(f.player));
            world.assertConditions(0);
            helper.assertFalse(world.unlocked(), "Neither iron-pick ownership nor copper-tool crafting unlocks old saves");

            f.player.getInventory().clearContent();
            f.player.getStats().setValue(f.player, Stats.ITEM_CRAFTED.get(Items.IRON_PICKAXE), 1);
            world.assertConditions(0);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(f.player));
            world.assertConditions(16);
            helper.assertTrue(world.unlocked(), "A completed iron-pick statistic migrates even after the tool is lost");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(f.player));
            world.assertConditions(16);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dimensionEventsRecordOnlyEnteringTheUnderworld(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(f.player, Level.OVERWORLD, Level.NETHER));
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(f.player, Level.NETHER, Level.END));
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(f.player, MiteWorldDimensions.UNDERWORLD, Level.OVERWORLD));
            world.assertConditions(0);
            ServerLevel underworld = world.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(underworld != null, "The registered underworld is loaded for the dimension event");
            f.player.setServerLevel(underworld);
            try {
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(f.player, Level.OVERWORLD, MiteWorldDimensions.UNDERWORLD));
                world.assertConditions(StructureProgressData.UNDERWORLD_VISITED);
            } finally {
                f.player.setServerLevel(f.level);
            }
            helper.assertFalse(world.unlocked(), "Travel progress alone never unlocks villages");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loginMigrationCollectsAllCraftingStatisticsAndTravelEvidence(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            f.player.getStats().setValue(f.player, Stats.ITEM_CRAFTED.get(Items.IRON_PICKAXE), 1);
            f.player.getStats().setValue(f.player, Stats.ITEM_CRAFTED.get(Items.BOW), 1);
            f.player.getStats().setValue(f.player, Stats.ITEM_CRAFTED.get(MiteArmorContent.get("mite:mithril_helmet")), 1);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(f.player));
            int crafted = StructureProgressData.OBSIDIAN_TOOL | StructureProgressData.BOW_CRAFTED
                    | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED;
            world.assertConditions(crafted);
            helper.assertTrue(MiteAchievements.award(f.player, "portal"), "The old save has the real earned underworld advancement");
            world.assertConditions(crafted);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(f.player));
            world.assertConditions(crafted | StructureProgressData.UNDERWORLD_VISITED);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loginInsideTheUnderworldRepairsTravelProgress(GameTestHelper helper) {
        try (WorldState world = new WorldState(helper); Fixture f = new Fixture(helper)) {
            ServerLevel underworld = world.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(underworld != null, "The registered underworld exists for old-save migration");
            f.player.setServerLevel(underworld);
            try {
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(f.player));
                world.assertConditions(StructureProgressData.UNDERWORLD_VISITED);
            } finally {
                f.player.setServerLevel(f.level);
            }
        }
        helper.succeed();
    }

    private static final class WorldState implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final StructureProgressData data;
        private final int originalConditions;
        private final boolean originalDirty;
        private final long originalTime;
        private final Holder<Structure> village;

        WorldState(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel().getServer().overworld();
            data = StructureProgressData.get(level);
            originalConditions = data.conditions();
            originalDirty = data.isDirty();
            originalTime = level.getDayTime();
            village = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(BuiltinStructures.VILLAGE_PLAINS);
            data.restoreConditions(0);
            level.setDayTime(59L * 24000L);
            R196StructureGeneration.refresh(level);
        }

        boolean unlocked() { return R196StructureGeneration.unlocked(level, village); }
        void assertConditions(int expected) {
            helper.assertValueEqual(StructureProgressData.get(level).conditions(), expected, "The shared world prerequisite bits match");
        }

        @Override public void close() {
            level.getDataStorage().set(StructureProgressData.NAME, data);
            data.restoreConditions(originalConditions);
            data.setDirty(originalDirty);
            level.setDayTime(originalTime);
            R196StructureGeneration.refresh(level);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos table;
        private final BlockState original;
        private final CraftingMenu menu;
        private final CraftingContainer grid;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-structures"), false);
            player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 1000);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            original = level.getBlockState(table);
            level.setBlockAndUpdate(table, MiteWorkbenches.TABLES.get("iron").get().defaultBlockState());
            menu = new CraftingMenu(53, player.getInventory(), ContainerLevelAccess.create(level, table));
            player.containerMenu = menu;
            grid = (CraftingContainer)menu.getSlot(1).container;
        }

        void loadIronPick() {
            for (int slot : new int[]{1, 2, 3}) menu.getSlot(slot).set(new ItemStack(Items.IRON_INGOT));
            for (int slot : new int[]{5, 8}) menu.getSlot(slot).set(new ItemStack(Items.STICK));
            tick(1);
            helper.assertTrue(menu.stillValid(player) && menu.getSlot(0).getItem().is(Items.IRON_PICKAXE),
                    "A physically placed iron workbench exposes the real iron-pick recipe");
        }

        void loadMithrilBow() {
            level.setBlockAndUpdate(table, MiteWorkbenches.TABLES.get("mithril").get().defaultBlockState());
            for (int slot : new int[]{2, 4, 8}) menu.getSlot(slot).set(new ItemStack(Items.STICK));
            for (int slot : new int[]{3, 6, 9}) menu.getSlot(slot).set(new ItemStack(Items.STRING));
            menu.getSlot(5).set(new ItemStack(MiteMaterials.MITHRIL_INGOT.get()));
            tick(1);
            helper.assertTrue(menu.stillValid(player) && menu.getSlot(0).getItem().is(MiteWeaponContent.MITHRIL_BOW.get()),
                    "The real mithril workbench exposes the reinforced-bow recipe");
        }

        int period() {
            int period = TimedCraftingController.period(player, menu);
            helper.assertTrue(period >= 25, "The recipe requires the actual nonzero manufacturing period");
            return period;
        }

        void tick(int ticks) { for (int tick = 0; tick < ticks; tick++) TimedCraftingController.tick(player); }

        void assertIngredients() {
            for (int slot : new int[]{1, 2, 3}) helper.assertTrue(menu.getSlot(slot).getItem().is(Items.IRON_INGOT)
                    && menu.getSlot(slot).getItem().getCount() == 1, "Unfinished recipes retain every ingot");
            for (int slot : new int[]{5, 8}) helper.assertTrue(menu.getSlot(slot).getItem().is(Items.STICK)
                    && menu.getSlot(slot).getItem().getCount() == 1, "Unfinished recipes retain both sticks");
        }

        void pickup(Item item) {
            int before = player.getInventory().countItem(item);
            ItemEntity entity = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), new ItemStack(item));
            entity.setNoPickUpDelay();
            level.addFreshEntity(entity);
            try {
                entity.playerTouch(player);
                helper.assertValueEqual(player.getInventory().countItem(item), before + 1,
                        "A real playerTouch transaction picked up " + BuiltInRegistries.ITEM.getKey(item));
            } finally {
                entity.discard();
            }
        }

        @Override public void close() {
            try {
                TimedCraftingController.forget(player);
                grid.clearContent();
                menu.setCarried(ItemStack.EMPTY);
                player.getInventory().clearContent();
                player.containerMenu = player.inventoryMenu;
                level.getServer().getPlayerList().remove(player);
            } finally {
                level.setBlockAndUpdate(table, original);
                channel.finishAndReleaseAll();
            }
        }
    }
}
