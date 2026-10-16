package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
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

/** Independent R196 vectors through real item, crafting, mining and interaction paths. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ToolEquipmentGameTests {
    @GameTest(template = "empty")
    public static void completeToolIdentitiesAndLiteralDurabilityVectors(GameTestHelper helper) {
        helper.assertValueEqual(MiteTools.definitions().stream().filter(MiteTools.Definition::isNew).count(), 93L,
                "The included catalog has exactly ninety-three previously absent tool identities");
        String[] paths = {"copper_pickaxe", "copper_hatchet", "copper_axe", "copper_shovel", "copper_hoe",
                "copper_mattock", "copper_scythe", "copper_shears", "copper_dagger", "copper_sword",
                "copper_battle_axe", "copper_war_hammer", "wooden_cudgel", "flint_knife", "obsidian_axe",
                "adamantium_war_hammer", "mithril_pickaxe", "ancient_metal_shears",
                "rusted_iron_pickaxe", "rusted_iron_sword", "rusted_iron_hatchet", "rusted_iron_war_hammer"};
        int[] maxima = {4800, 1600, 4800, 1600, 3200, 6400, 3200, 3200, 1600, 3200, 6400, 8000,
                200, 400, 2400, 512000, 76800, 12800, 3600, 2400, 1200, 6000};
        for (int i = 0; i < paths.length; i++) {
            ItemStack stack = tool(paths[i]);
            helper.assertTrue(!stack.isEmpty() && !stack.is(Items.AIR), "An actual registered tool exists: " + paths[i]);
            helper.assertValueEqual(stack.getMaxDamage(), maxima[i], "Literal default-quality R196 capacity (rusted tools clamp to poor): " + paths[i]);
            helper.assertValueEqual(stack.getMaxStackSize(), 1, "Tools never stack");
        }
        for (String material : new String[]{"copper", "silver", "golden", "rusted_iron", "iron", "ancient_metal", "mithril", "adamantium"}) {
            helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("mite", material + "_knife")),
                    "User-excluded metal knives have not been registered");
        }
        helper.assertValueEqual(new ItemStack(Items.IRON_PICKAXE).getMaxDamage(), 9600, "Existing iron pickaxe ID receives its R196 capacity");
        helper.assertValueEqual(new ItemStack(Items.GOLDEN_PICKAXE).getMaxDamage(), 4800, "Existing gold pickaxe ID receives its R196 capacity");
        helper.assertValueEqual(tool("ancient_metal_pickaxe").getMaxDamage(), 19200, "Ancient metal retains the source sixteen material capacity");
        helper.assertValueEqual(new ItemStack(Items.NETHERITE_PICKAXE).getMaxDamage(), 38400, "Modern netherite mapping is explicitly thirty-two");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualMiningHasMaterialEfficiencyThresholdsAndExactlyOneWearCharge(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        AABB area = new AABB(pos).inflate(5);
        try (Fixture fixture = new Fixture(helper, pos); Session session = player(helper);
                SpawnedEntityCapture drops = new SpawnedEntityCapture(helper.getLevel(),
                        entity -> entity instanceof ItemEntity && entity.getBoundingBox().intersects(area))) {
            ServerPlayer player = session.player();
            ItemStack pick = tool("copper_pickaxe");
            player.setItemInHand(InteractionHand.MAIN_HAND, pick);
            helper.assertValueEqual(player.getDigSpeed(Blocks.STONE.defaultBlockState(), pos), 7.0F, "Fed level-zero copper pick strength is seven");
            helper.assertValueEqual(player.getDigSpeed(Blocks.DIAMOND_ORE.defaultBlockState(), pos), 1.0F, "Copper cannot gain effective speed against diamond material");
            helper.assertTrue(!pick.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()), "Copper cannot collect diamond ore");
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            helper.assertTrue(player.gameMode.destroyBlock(pos), "Actual survival server mining removes stone");
            helper.assertValueEqual(pick.getDamageValue(), 150, "One raw hardness-1.5 stone costs exactly 150, not a duplicate modern charge");
            // A successful synchronous spawn can precede visibility in the chunk entity-section index.
            helper.assertValueEqual(drops.live(ItemEntity.class).stream().filter(item -> item.getItem().is(Items.COBBLESTONE))
                    .mapToInt(item -> item.getItem().getCount()).sum(), 1, "The successful ordinary harvest produced one cobblestone");
            ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(), pick.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(restored.getDamageValue(), 150, "Persistence preserves spent durability");
            helper.assertValueEqual(restored.getMaxDamage(), 4800, "Persistence preserves the source capacity");
            player.setItemInHand(InteractionHand.MAIN_HAND, restored);
            PlayerExperience.setTotal(player, 203000);
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            helper.assertValueEqual(player.getDigSpeed(Blocks.STONE.defaultBlockState(), pos), 35.0F, "The level multiplier applies once above tool strength");
            player.gameMode.destroyBlock(pos);
            helper.assertValueEqual(restored.getDamageValue(), 300, "Level speed does not multiply per-block wear");
            player.setGameMode(GameType.CREATIVE);
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            player.gameMode.destroyBlock(pos);
            helper.assertValueEqual(restored.getDamageValue(), 300, "Creative mining leaves durability unchanged");
            player.setGameMode(GameType.SURVIVAL);
            PlayerExperience.setTotal(player, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool("copper_hatchet"));
            helper.getLevel().setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
            helper.assertTrue(player.gameMode.destroyBlock(pos), "A registered copper hatchet passes the actual log gate");
            helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 160, "Hatchet log wear uses the independent 160-point vector");
            helper.assertTrue(tool("mithril_pickaxe").isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()), "Mithril meets the old diamond material threshold");
            helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Iron meets the old obsidian threshold");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toolFamiliesExposeRealActionsAndSourceTargetSpeeds(GameTestHelper helper) {
        try (Session session = player(helper)) {
            ServerPlayer player = session.player();
            BlockPos pos = player.blockPosition();
            String[] ids = {"flint_axe", "copper_axe", "copper_battle_axe", "copper_war_hammer",
                    "copper_shovel", "copper_mattock", "copper_hoe", "copper_sword", "copper_dagger",
                    "flint_knife", "copper_scythe", "copper_shears"};
            BlockState[] states = {Blocks.SANDSTONE.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                    Blocks.OAK_LOG.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                    Blocks.DIRT.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.COBWEB.defaultBlockState(),
                    Blocks.COBWEB.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(),
                    Blocks.WHEAT.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState()};
            float[] speeds = {2.5F, 7F, 5.25F, 5.25F, 7F, 5.25F, 3.5F, 3.5F, 3.5F, 2.5F, 7F, 7F};
            for (int i = 0; i < ids.length; i++) {
                player.setItemInHand(InteractionHand.MAIN_HAND, tool(ids[i]));
                helper.assertValueEqual(player.getDigSpeed(states[i], pos), speeds[i], "Actual player tool efficiency: " + ids[i]);
            }
            helper.assertTrue(tool("copper_hatchet").canPerformAction(ItemAbilities.AXE_DIG), "Hatchets expose the capability used by the log gate");
            helper.assertTrue(!tool("copper_hatchet").canPerformAction(ItemAbilities.AXE_STRIP), "A newly registered old hatchet does not acquire a modern strip action");
            helper.assertTrue(tool("copper_mattock").canPerformAction(ItemAbilities.HOE_TILL), "Mattocks retain their actual tilling action");
            helper.assertTrue(tool("copper_shears").canPerformAction(ItemAbilities.SHEARS_DIG), "All registered shears expose shearing capability");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualTillingAndShearingConsumeFiftyWithoutReplacingTheTool(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(1, 2, 2));
        sheep.setNoAi(true);
        sheep.setNoGravity(true);
        sheep.setAge(0);
        try (Fixture fixture = new Fixture(helper, pos); Session session = player(helper)) {
            ServerPlayer player = session.player();
            for (String path : new String[]{"copper_hoe", "copper_mattock"}) {
                helper.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
                ItemStack tool = tool(path);
                player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                var result = player.gameMode.useItemOn(player, helper.getLevel(), tool, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false));
                helper.assertTrue(result.consumesAction(), "The real right-click packet path accepts tilling: " + path);
                helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.FARMLAND), "Tilling produces farmland");
                helper.assertValueEqual(tool.getDamageValue(), 50, "Tilling spends fifty points exactly once");
            }
            ItemStack shears = tool("copper_shears");
            player.setItemInHand(InteractionHand.MAIN_HAND, shears);
            helper.assertTrue(player.interactOn(sheep, InteractionHand.MAIN_HAND).consumesAction(), "The actual entity interaction accepts registered copper shears");
            helper.assertTrue(sheep.isSheared(), "The living sheep has actually been sheared");
            helper.assertValueEqual(shears.getDamageValue(), 50, "A sheep shearing costs fifty points once");
            helper.assertTrue(!player.interactOn(sheep, InteractionHand.MAIN_HAND).consumesAction(), "A second attempt cannot shear an already sheared sheep");
            helper.assertValueEqual(shears.getDamageValue(), 50, "The rejected duplicate interaction has no wear");
        } finally {
            sheep.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualSilverAndBluntAttacksUseTargetBonusesAndSingleWear(GameTestHelper helper) {
        Skeleton target = helper.spawn(EntityType.SKELETON, new BlockPos(1, 2, 1));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20); // Independent combat-vector fixture, not the source six-health spawn baseline.
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            target.setItemSlot(slot, ItemStack.EMPTY);
        try (Session session = player(helper)) {
            ServerPlayer player = session.player();
            String[] paths = {"silver_dagger", "silver_war_hammer", "wooden_cudgel"};
            float[] damage = {7.5F, 15.0F, 4.0F};
            int[] wear = {50, 66, 25};
            for (int i = 0; i < paths.length; i++) {
                ItemStack stack = tool(paths[i]);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                for (int tick = 0; tick < 25; tick++) player.doTick();
                player.setOnGround(true);
                player.setSprinting(false);
                player.fallDistance = 0;
                target.invulnerableTime = 0;
                target.setHealth(20);
                player.attack(target);
                helper.assertTrue(Math.abs(target.getHealth() - (20 - damage[i])) < .001F,
                        "Actual full-strength silver/blunt damage follows the independent target vector: " + paths[i]);
                helper.assertValueEqual(stack.getDamageValue(), wear[i], "One successful attack applies exactly one source wear charge");
            }
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allCopperToolShapesActuallyConsumeTheirLiteralIngredients(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        try (Fixture fixture = new Fixture(helper, pos); Session session = player(helper)) {
            helper.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mite:adamantium_crafting_table")).defaultBlockState());
            ServerPlayer player = session.player();
            String[] outputs = {"copper_pickaxe", "copper_hatchet", "copper_axe", "copper_shovel", "copper_hoe",
                    "copper_mattock", "copper_scythe", "copper_shears", "copper_dagger", "copper_sword", "copper_battle_axe", "copper_war_hammer"};
            String[][] patterns = {{"III", " S ", " S "}, {"SI", "S "}, {"II", "IS", " S"}, {"I", "S", "S"},
                    {"II", " S", " S"}, {"III", " SI", " S "}, {"SI ", "S I", "S  "}, {" I", "I "},
                    {"I", "S"}, {"I", "I", "S"}, {"I I", "ISI", " S "}, {"III", "ISI", " S "}};
            for (int index = 0; index < outputs.length; index++) {
                CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
                player.containerMenu = menu;
                try {
                    String[] pattern = patterns[index];
                    for (int row = 0; row < pattern.length; row++) for (int col = 0; col < pattern[row].length(); col++) {
                        char c = pattern[row].charAt(col);
                        if (c != ' ') menu.getSlot(1 + row * 3 + col).set(new ItemStack(c == 'I' ? Items.COPPER_INGOT : Items.STICK));
                    }
                    helper.assertTrue(menu.getSlot(0).getItem().is(MiteTools.get(outputs[index])), "The independently transcribed original shape matches " + outputs[index]);
                    menu.clicked(0, 0, ClickType.PICKUP, player);
                    int period = TimedCraftingController.period(player, menu);
                    helper.assertTrue(period >= 25, "Each real copper recipe has a manufacturing period");
                    for (int tick = 0; tick < period - 1; tick++) TimedCraftingController.tick(player);
                    helper.assertValueEqual(player.getInventory().countItem(MiteTools.get(outputs[index])), 0, "N-1 ticks cannot produce the tool");
                    helper.assertTrue(menu.getCarried().isEmpty(), "Clicking starts work without creating a cursor stack");
                    TimedCraftingController.tick(player);
                    helper.assertValueEqual(player.getInventory().countItem(MiteTools.get(outputs[index])), 1, "Exactly the Nth production tick delivers one real tool");
                    for (int slot = 1; slot <= 9; slot++) helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "Each original ingredient was consumed once");
                    menu.setCarried(ItemStack.EMPTY);
                } finally {
                    menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void timedManufacturingPausesCancelsAndCannotTurnOneDelayIntoMultipleBatches(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        try (Fixture fixture = new Fixture(helper, pos); Session session = player(helper)) {
            helper.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mite:copper_crafting_table")).defaultBlockState());
            ServerPlayer player = session.player();
            CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
            player.containerMenu = menu;
            try {
                menu.getSlot(1).set(new ItemStack(Items.STICK, 2));
                menu.getSlot(2).set(new ItemStack(Items.COPPER_INGOT, 2));
                menu.getSlot(4).set(new ItemStack(Items.STICK, 2));
                BlockPos webPos = player.blockPosition().above();
                BlockState originalWebPos = helper.getLevel().getBlockState(webPos);
                try {
                    helper.getLevel().setBlockAndUpdate(webPos, Blocks.COBWEB.defaultBlockState());
                    helper.assertTrue(!TimedCraftingController.start(player, menu), "An upper-body web prevents manufacturing on the server");
                } finally {
                    helper.getLevel().setBlockAndUpdate(webPos, originalWebPos);
                }
                menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                for (int i = 0; i < 60; i++) TimedCraftingController.tick(player);
                helper.assertValueEqual(TimedCraftingController.progress(player), 60, "The production session has sixty ticks");
                for (int i = 0; i < 10; i++) menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                helper.assertValueEqual(TimedCraftingController.progress(player), 60, "Repeated Shift-clicks cannot accelerate the clock");
                TimedCraftingController.damaged(player, 1F);
                helper.assertValueEqual(TimedCraftingController.progress(player), 10, "One damage point rolls back exactly fifty ticks");
                player.getFoodData().setFoodLevel(0);
                ((io.github.ningmeng123.mite.survival.DualFoodData)player.getFoodData()).mite$setNutrition(1);
                TimedCraftingController.tick(player);
                helper.assertValueEqual(TimedCraftingController.progress(player),11,"Nutrition alone allows manufacture to advance");
                ((io.github.ningmeng123.mite.survival.DualFoodData)player.getFoodData()).mite$setNutrition(0);
                for (int i = 0; i < 40; i++) TimedCraftingController.tick(player);
                helper.assertValueEqual(TimedCraftingController.progress(player), 11, "Both empty food reserves pause and retain progress");
                player.getFoodData().setFoodLevel(6);
                menu.clicked(0, 1, ClickType.PICKUP, player);
                helper.assertValueEqual(TimedCraftingController.progress(player), 0, "Right-click cancels this ordinary-quality session");
                menu.clicked(0, 0, ClickType.PICKUP, player);
                TimedCraftingController.tick(player);
                menu.getSlot(2).set(new ItemStack(Items.COPPER_INGOT, 3));
                menu.getSlot(2).set(new ItemStack(Items.COPPER_INGOT, 2));
                TimedCraftingController.tick(player);
                helper.assertValueEqual(TimedCraftingController.progress(player), 0, "Even an input edit restored within one tick cancels work");
                menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                int period = TimedCraftingController.period(player, menu);
                for (int batch = 0; batch < 2; batch++) {
                    for (int i = 0; i < period - 1; i++) TimedCraftingController.tick(player);
                    helper.assertValueEqual(player.getInventory().countItem(MiteTools.get("copper_hatchet")), batch, "Every batch independently waits through N-1 ticks");
                    helper.assertValueEqual(menu.getSlot(2).getItem().getCount(), 2 - batch, "Ingredients remain unconsumed until completion");
                    TimedCraftingController.tick(player);
                    helper.assertValueEqual(player.getInventory().countItem(MiteTools.get("copper_hatchet")), batch + 1, "The Nth tick makes precisely one batch");
                    helper.assertValueEqual(TimedCraftingController.progress(player), 0, "No leftover credit advances the next batch");
                }
                for (int slot = 1; slot <= 9; slot++) helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "Two batches consume two copies of every ingredient");
            } finally {
                menu.removed(player);
                player.containerMenu = player.inventoryMenu;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void timedDismantlingKeepsKnifeAndLogWithFullCreativeInventory(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        AABB area = new AABB(pos).inflate(5);
        try (Fixture fixture = new Fixture(helper, pos); Session session = player(helper);
                SpawnedEntityCapture drops = new SpawnedEntityCapture(helper.getLevel(),
                        entity -> entity instanceof ItemEntity && entity.getBoundingBox().intersects(area))) {
            // A successful synchronous add can precede section tracking; count its real join event.
            java.util.function.ToIntFunction<Item> dropped = item -> drops.live(ItemEntity.class).stream()
                    .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
            ServerPlayer player = session.player();
            player.setGameMode(GameType.CREATIVE);
            player.containerMenu = player.inventoryMenu;
            for (String material : new String[]{"flint", "obsidian"}) {
                Item table = MiteTools.get(material + "_crafting_table");
                Item knife = MiteTools.get(material + "_knife");
                player.getInventory().clearContent();
                for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                    ItemStack filler = new ItemStack(Items.COBBLESTONE);
                    filler.setCount(filler.getMaxStackSize());
                    player.getInventory().setItem(slot, filler);
                }
                int logsBefore = dropped.applyAsInt(Items.OAK_LOG);
                int knifeBefore = dropped.applyAsInt(knife);
                int fillerBefore = player.getInventory().countItem(Items.COBBLESTONE);
                var menu = player.inventoryMenu;
                menu.getSlot(1).set(new ItemStack(table, 2));
                menu.clicked(0, 0, ClickType.PICKUP, player);
                int period = TimedCraftingController.period(player, menu);
                helper.assertTrue(period >= 25, "Dismantling uses the real manufacturing timer");
                for (int tick = 0; tick < period - 1; tick++) TimedCraftingController.tick(player);
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 2, "N-1 ticks consume no workbench");
                helper.assertValueEqual(dropped.applyAsInt(Items.OAK_LOG), logsBefore, "No remainder exists before completion");
                TimedCraftingController.tick(player);
                helper.assertTrue(menu.getSlot(1).getItem().is(table), "The remaining workbench still occupies the input slot");
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "Only one of the two workbenches was consumed");
                helper.assertValueEqual(player.getInventory().countItem(table) + dropped.applyAsInt(table)
                        + menu.getSlot(1).getItem().getCount(), 1, "Exactly one workbench survives across every output location");
                helper.assertValueEqual(player.getInventory().countItem(knife) + dropped.applyAsInt(knife), knifeBefore + 1, "Exactly one knife survives in inventory or the world");
                helper.assertValueEqual(player.getInventory().countItem(Items.OAK_LOG) + dropped.applyAsInt(Items.OAK_LOG), logsBefore + 1, "The full creative inventory cannot swallow the oak-log remainder");
                helper.assertValueEqual(player.getInventory().countItem(Items.COBBLESTONE), fillerBefore, "Unrelated full inventory stacks remain intact");
                helper.assertTrue(menu.getCarried().isEmpty(), "There is no additional cursor output");
                helper.assertTrue(!TimedCraftingController.isCompletingIn(player.getInventory()), "The transaction marker is cleared after onTake");
                for (int tick = 0; tick < period; tick++) TimedCraftingController.tick(player);
                helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "A normal click stops after one batch without manual cancellation");
                helper.assertValueEqual(dropped.applyAsInt(Items.OAK_LOG), logsBefore + 1, "There is no extra returned log after the single batch");
                helper.assertValueEqual(dropped.applyAsInt(knife), knifeBefore + 1, "There is no extra knife after the single batch");
                menu.getSlot(1).set(ItemStack.EMPTY);
            }
        }
        helper.succeed();
    }

    private static ItemStack tool(String path) { return new ItemStack(MiteTools.get(path)); }

    private static Session player(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-tool-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != null)
                helper.getLevel().getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final Set<UUID> originalItems = new HashSet<>();
        private final AABB area;
        private final boolean tileDrops;
        private Fixture(GameTestHelper helper, BlockPos pos) {
            this.helper = helper;
            originals.put(pos.above(), helper.getLevel().getBlockState(pos.above()));
            originals.put(pos, helper.getLevel().getBlockState(pos));
            area = new AABB(pos).inflate(5);
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(item -> originalItems.add(item.getUUID()));
            tileDrops = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, helper.getLevel().getServer());
        }
        private int count(Item type) {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                    .filter(item -> !originalItems.contains(item.getUUID()) && item.getItem().is(type)).mapToInt(item -> item.getItem().getCount()).sum();
        }
        @Override public void close() {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(tileDrops, helper.getLevel().getServer());
            originals.forEach((pos, state) -> helper.getLevel().setBlockAndUpdate(pos, state));
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().filter(item -> !originalItems.contains(item.getUUID())).forEach(ItemEntity::discard);
        }
    }
}
