package io.github.ningmeng123.mite.gametest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Explicit modern campfire heat-one adaptation validates both input and every native cooking slot. @author NingMeng123 */
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
            f.campfire.getItems().set(1, new ItemStack(Items.ANCIENT_DEBRIS));
            ItemStack food = new ItemStack(Items.BEEF);
            helper.assertTrue(f.campfire.placeFood(null, food, 1), "An independent legal native food slot still accepts source food");
            f.tick();
            helper.assertTrue(f.campfire.getItems().get(0).is(item("mite:mithril_ore")) && f.campfire.getItems().get(0).getCount() == 1,
                    "The native cooking loop preserves a forced high-heat raw ore rather than processing or dropping it");
            helper.assertTrue(f.campfire.getItems().get(1).is(Items.ANCIENT_DEBRIS), "An injected modern heat-four material is independently blocked");
            helper.assertValueEqual(f.dropped(Items.COOKED_BEEF), 1, "The legal independent native cooking slot produces exactly one cooked source beef");
            helper.assertValueEqual(f.dropped(item("mite:mithril_ore")), 0, "The protected ore cannot be ejected through native no-recipe fallback");
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
            helper.getLevel().setBlockEntity(f.campfire); f.tick();
            helper.assertTrue(f.campfire.getItems().get(0).is(item("mite:adamantium_ore")), "Saved unauthorized high-heat input remains raw after the real loading path");
            helper.assertValueEqual(f.dropped(Items.COOKED_PORKCHOP), 1, "Loading the invalid slot cannot disable an independent valid source-food slot");
            helper.assertValueEqual(f.dropped(item("mite:adamantium_ingot")), 0, "Saved data cannot bypass the source furnace heat-four gate");
        }
        helper.succeed();
    }

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos pos;
        final BlockState old;
        final AABB area;
        final Set<UUID> oldDrops = new HashSet<>();
        CampfireBlockEntity campfire;
        Fixture(GameTestHelper helper, Block block) {
            this.helper = helper; pos = helper.absolutePos(new BlockPos(2, 2, 2));
            old = helper.getLevel().getBlockState(pos); area = new AABB(pos).inflate(3);
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(drop -> oldDrops.add(drop.getUUID()));
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            campfire = (CampfireBlockEntity) helper.getLevel().getBlockEntity(pos);
        }
        void tick() { CampfireBlockEntity.cookTick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), campfire); }
        int dropped(Item item) {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                    .filter(drop -> !oldDrops.contains(drop.getUUID()) && drop.getItem().is(item)).mapToInt(drop -> drop.getItem().getCount()).sum();
        }
        @Override public void close() {
            campfire.clearContent(); helper.getLevel().setBlockAndUpdate(pos, old);
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().filter(drop -> !oldDrops.contains(drop.getUUID())).forEach(ItemEntity::discard);
        }
    }
}
