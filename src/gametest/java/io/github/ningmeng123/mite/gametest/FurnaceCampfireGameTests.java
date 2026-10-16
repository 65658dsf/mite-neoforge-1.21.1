package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.campfire.CampfireController;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The survival campfire cannot bypass furnace heat gates, including injected and saved input. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FurnaceCampfireGameTests {
    private FurnaceCampfireGameTests() {}

    @GameTest(template = "empty")
    public static void ordinaryAndSoulCampfiresRejectHighHeatInputsWithoutConsumingThem(GameTestHelper helper) {
        for (Block block : new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}) try (Fixture f = new Fixture(helper, block)) {
            for (Item input : new Item[]{item("mite:mithril_ore"), item("mite:adamantium_ore"), Items.ANCIENT_DEBRIS}) {
                ItemStack stack = new ItemStack(input);
                helper.assertTrue(f.campfire.getCookableRecipe(stack).isEmpty(), "The actual campfire query excludes inputs requiring more than modern heat one");
                helper.assertTrue(!f.campfire.placeFood(null, stack, 1), "The actual campfire insertion rejects high heat even if an external caller supplies cooking time");
                helper.assertValueEqual(stack.getCount(), 1, "A rejected native campfire insertion does not consume its raw item");
                helper.assertTrue(f.campfire.getItems().stream().allMatch(ItemStack::isEmpty), "No rejected high-heat input is hidden in any campfire cooking slot");
            }
            helper.assertTrue(f.campfire.getCookableRecipe(new ItemStack(Items.BEEF)).isPresent(), "The same actual campfire still recognizes a source heat-one food recipe");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aForcedHighHeatCampfireSlotCannotProcessWhileAnIndependentLegalFoodStillCooks(GameTestHelper helper) {
        for (Block block : new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}) try (Fixture f = new Fixture(helper, block)) {
            f.campfire.getItems().set(0, new ItemStack(item("mite:mithril_ore")));
            f.campfire.getItems().set(2, new ItemStack(Items.ANCIENT_DEBRIS));
            ItemStack food = new ItemStack(Items.BEEF);
            helper.assertTrue(f.campfire.placeFood(null, food, 1), "An independent legal slot still accepts raw meat beside a forced high-heat input");
            f.tick(1);
            helper.assertTrue(f.campfire.getItems().get(0).isEmpty() && f.campfire.getItems().get(2).isEmpty(),
                    "The survival loop removes unauthorized and excess inputs from the cooking positions");
            helper.assertValueEqual(f.dropped(item("mite:mithril_ore")), 1, "A forced high-heat ore safely returns as the identical raw item");
            helper.assertValueEqual(f.dropped(Items.ANCIENT_DEBRIS), 1, "A forced heat-four material safely returns without processing");
            f.tick(298);
            helper.assertValueEqual(f.dropped(Items.COOKED_BEEF), 0, "A caller-supplied one-tick time cannot bypass the fifteen-second cooking interval");
            f.tick(1);
            helper.assertValueEqual(f.dropped(Items.COOKED_BEEF), 1, "The independent valid meat still cooks at the exact fifteen-second deadline");
            helper.assertValueEqual(f.dropped(item("mite:mithril_ingot")), 0, "An injected raw mithril ore cannot produce a high-heat ingot");
            helper.assertValueEqual(f.dropped(Items.NETHERITE_SCRAP), 0, "The campfire cannot bypass modern debris progression");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedForcedHighHeatSlotsRemainProtectedAfterActualCampfireNbtLoading(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.CAMPFIRE)) {
            f.campfire.getItems().set(0, new ItemStack(item("mite:adamantium_ore")));
            helper.assertTrue(f.campfire.placeFood(null, new ItemStack(Items.PORKCHOP), 1), "The independent source food is scheduled natively before saving");
            CompoundTag saved = f.campfire.saveWithFullMetadata(helper.getLevel().registryAccess());
            f.campfire = (CampfireBlockEntity) BlockEntity.loadStatic(f.pos, helper.getLevel().getBlockState(f.pos), saved, helper.getLevel().registryAccess());
            helper.assertTrue(f.campfire != null, "Native campfire metadata resolves the actual registered block-entity type");
            helper.getLevel().setBlockEntity(f.campfire); f.tick(1);
            helper.assertTrue(f.campfire.getItems().get(0).isEmpty(), "Saved unauthorized high-heat input leaves the cooking positions after the real loading path");
            helper.assertValueEqual(f.dropped(item("mite:adamantium_ore")), 1, "Saved unauthorized ore is safely returned as the original raw item");
            f.tick(298);
            helper.assertValueEqual(f.dropped(Items.COOKED_PORKCHOP), 0, "Loading cannot skip the remaining fifteen-second manufacturing interval");
            f.tick(1);
            helper.assertValueEqual(f.dropped(Items.COOKED_PORKCHOP), 1, "Loading the invalid slot cannot disable a valid meat's full fifteen-second cooking");
            helper.assertValueEqual(f.dropped(item("mite:adamantium_ingot")), 0, "Saved data cannot bypass the source furnace heat-four gate");
        }
        helper.succeed();
    }

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos pos;
        final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        final SpawnedEntityCapture capture;
        CampfireBlockEntity campfire;
        Fixture(GameTestHelper helper, Block block) {
            this.helper = helper; pos = helper.absolutePos(new BlockPos(2, 2, 2));
            AABB area = new AABB(pos).inflate(3);
            capture = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity && entity.getBoundingBox().intersects(area));
            for (BlockPos site : new BlockPos[]{pos.below(), pos, pos.above(2)}) originals.put(site, helper.getLevel().getBlockState(site));
            helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos.above(2), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState().setValue(CampfireBlock.LIT, false));
            campfire = (CampfireBlockEntity) helper.getLevel().getBlockEntity(pos);
            CampfireController.refuel(campfire, 3, true);
        }
        void tick(int count) {
            for (int tick = 0; tick < count; tick++) CampfireBlockEntity.cookTick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), campfire);
        }
        int dropped(Item item) {
            return capture.live(ItemEntity.class).stream().filter(drop -> drop.getItem().is(item)).mapToInt(drop -> drop.getItem().getCount()).sum();
        }
        @Override public void close() {
            campfire.clearContent();
            originals.forEach((site, state) -> helper.getLevel().setBlockAndUpdate(site, state));
            capture.close();
        }
    }
}
