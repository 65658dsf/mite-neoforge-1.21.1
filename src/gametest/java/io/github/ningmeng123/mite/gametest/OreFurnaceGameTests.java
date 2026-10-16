package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual ore recipe matching, furnace result withdrawals and saved hopper history. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class OreFurnaceGameTests {
    private OreFurnaceGameTests() {}

    @GameTest(template = "empty")
    public static void allSourceOreRecipesMatchOnceOnFurnaceAndModernBlastWithSourceOutputs(GameTestHelper helper) {
        for (Output source : List.of(new Output("minecraft:coal_ore", "minecraft:coal", 1),
                new Output("minecraft:copper_ore", "minecraft:copper_ingot", 1), new Output("minecraft:iron_ore", "minecraft:iron_ingot", 1),
                new Output("minecraft:gold_ore", "minecraft:gold_ingot", 1), new Output("minecraft:lapis_ore", "minecraft:lapis_lazuli", 4),
                new Output("minecraft:redstone_ore", "minecraft:redstone", 4), new Output("minecraft:diamond_ore", "minecraft:diamond", 1),
                new Output("minecraft:emerald_ore", "minecraft:emerald", 1), new Output("mite:silver_ore", "mite:silver_ingot", 1),
                new Output("mite:mithril_ore", "mite:mithril_ingot", 1), new Output("mite:adamantium_ore", "mite:adamantium_ingot", 1),
                new Output("minecraft:nether_gold_ore", "minecraft:gold_ingot", 1), new Output("minecraft:nether_quartz_ore", "minecraft:quartz", 1))) {
            String[] inputs = source.input().contains("nether_") ? new String[]{source.input()}
                    : new String[]{source.input(), source.input().replace(":", ":deepslate_")};
            for (String input : inputs) for (RecipeType<?> type : new RecipeType<?>[]{RecipeType.SMELTING, RecipeType.BLASTING}) {
                var matching = helper.getLevel().getRecipeManager().getRecipes().stream().map(holder -> holder.value())
                        .filter(recipe -> recipe instanceof AbstractCookingRecipe cooking && cooking.getType() == type
                                && cooking.matches(new SingleRecipeInput(new ItemStack(item(input))), helper.getLevel())).toList();
                helper.assertValueEqual(matching.size(), 1, "A source ore cannot retain a duplicate modern-default recipe: " + input + " / " + type);
                AbstractCookingRecipe recipe = (AbstractCookingRecipe) matching.getFirst();
                ItemStack output = recipe.getResultItem(helper.getLevel().registryAccess());
                helper.assertTrue(output.is(item(source.output())), "The actual registered cooking output uses the source identity");
                helper.assertValueEqual(output.getCount(), source.count(), "The actual registered cooking output uses the source quantity");
                helper.assertValueEqual(recipe.getCookingTime(), type == RecipeType.SMELTING ? 200 : 100,
                        "Source ordinary cooking and explicitly adapted modern blast timing are distinct");
                helper.assertValueEqual(recipe.getExperience(), 0F, "The direct source result-slot path cannot also bank a native recipe reward");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialAndShiftMetalWithdrawalsPayOnlyTheActualTakenCount(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper);
                var spawned = new SpawnedEntityCapture(helper.getLevel(), entity ->
                        (entity instanceof ExperienceOrb || entity instanceof ItemEntity) && entity.getBoundingBox().intersects(f.area))) {
            // Observe accepted native spawns synchronously: this same-tick transaction must not
            // depend on the random-grid test chunk becoming visible to entity-section queries.
            java.util.function.IntSupplier xp = () -> spawned.live(ExperienceOrb.class).stream()
                    .mapToInt(orb -> orb.getValue() * Math.max(1, orb.saveWithoutId(new CompoundTag()).getInt("Count"))).sum();
            f.cook(item("mite:mithril_ore"), 3, 3);
            f.menu.clicked(2, 0, ClickType.THROW, f.player);
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 2, "The actual result-slot Q action withdraws one ingot");
            helper.assertValueEqual(xp.getAsInt(), 40, "One withdrawn source mithril ingot pays forty, rather than the whole production history");
            helper.assertValueEqual(f.take(2).getCount(), 2, "The native result slot later withdraws the remaining two ingots");
            helper.assertValueEqual(xp.getAsInt(), 120, "All three independently withdrawn source ingots pay exactly 120");
            f.cook(item("mite:mithril_ore"), 3, 3);
            f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.player);
            helper.assertValueEqual(f.player.getInventory().countItem(item("mite:mithril_ingot")), 3, "Native shift movement transfers all three newly cooked ingots");
            helper.assertValueEqual(xp.getAsInt(), 240, "Quick-craft and onTake callbacks cannot double the actual source withdrawal reward");
            f.furnace.awardUsedRecipesAndPopExperience(f.player);
            helper.assertValueEqual(xp.getAsInt(), 240, "A repeated native history claim cannot duplicate either withdrawal reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneLapisOreActuallyProducesFourUnitsAndTwentyWithdrawalExperience(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.cook(Items.LAPIS_ORE, 1, 4);
            ItemStack output = f.take(4);
            helper.assertTrue(output.is(Items.LAPIS_LAZULI) && output.getCount() == 4, "The real furnace produces the source four-unit lapis output");
            helper.assertValueEqual(f.xp(), 20, "Four actually withdrawn source lapis units pay five each");
            f.furnace.awardUsedRecipesAndPopExperience(f.player);
            helper.assertValueEqual(f.xp(), 20, "A repeated native recipe-history claim cannot pay a second lapis balance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void successfulHopperMetalWithdrawalPaysAtTheFurnaceFrontWithoutSavingADuplicateBalance(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.cook(item("mite:mithril_ore"), 3, 3);
            helper.getLevel().setBlockAndUpdate(f.hopperPosition, Blocks.HOPPER.defaultBlockState());
            var hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(f.hopperPosition);
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                ItemStack full = new ItemStack(Items.COBBLESTONE);
                full.setCount(full.getMaxStackSize());
                hopper.setItem(slot, full);
            }
            for (int tick = 0; tick < 40; tick++)
                HopperBlockEntity.pushItemsTick(helper.getLevel(), f.hopperPosition, helper.getLevel().getBlockState(f.hopperPosition), hopper);
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 3, "A genuinely full hopper cannot withdraw metal outputs");
            helper.assertValueEqual(f.xp(), 0, "Failed full-hopper transfers cannot create source XP");
            hopper.clearContent();
            helper.getLevel().setBlock(f.hopperPosition, helper.getLevel().getBlockState(f.hopperPosition).setValue(HopperBlock.ENABLED, false), 2);
            for (int tick = 0; tick < 40; tick++)
                HopperBlockEntity.pushItemsTick(helper.getLevel(), f.hopperPosition, helper.getLevel().getBlockState(f.hopperPosition), hopper);
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 3, "A disabled native hopper cannot withdraw an otherwise available metal output");
            helper.assertValueEqual(f.xp(), 0, "Disabled transfer attempts cannot create source XP");
            helper.getLevel().setBlock(f.hopperPosition, helper.getLevel().getBlockState(f.hopperPosition).setValue(HopperBlock.ENABLED, true), 2);
            for (int tick = 0; tick < 40; tick++)
                HopperBlockEntity.pushItemsTick(helper.getLevel(), f.hopperPosition, helper.getLevel().getBlockState(f.hopperPosition), hopper);
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The actual hopper withdraws the three ingots");
            int collected = 0;
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) if (hopper.getItem(slot).is(item("mite:mithril_ingot"))) collected += hopper.getItem(slot).getCount();
            helper.assertValueEqual(collected, 3, "Exactly three cooked metal outputs reach the hopper");
            helper.assertValueEqual(f.xp(), 120, "Three successful source hopper withdrawals each immediately emit forty XP");
            Direction facing = helper.getLevel().getBlockState(f.furnacePosition).getValue(BlockStateProperties.HORIZONTAL_FACING);
            Vec3 front = Vec3.atCenterOf(f.furnacePosition.relative(facing));
            var emitted = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, f.area).stream()
                    .filter(orb -> !f.oldOrbs.contains(orb.getUUID())).toList();
            helper.assertValueEqual(emitted.size(), 3, "Every actual one-item transfer emits its own source XP orb");
            for (ExperienceOrb orb : emitted) {
                helper.assertValueEqual(orb.getValue(), 40, "The emitted one-ingot source reward is forty");
                helper.assertTrue(orb.position().distanceToSqr(front) < 1.0E-12, "The actual source orb appears at the center of the furnace's front block");
            }
            CompoundTag saved = f.furnace.saveWithoutMetadata(helper.getLevel().registryAccess());
            var history = saved.getCompound("RecipesUsed");
            helper.assertValueEqual(history.getAllKeys().stream().mapToInt(history::getInt).sum(), 3, "Production history remains stored for recipe unlocking");
            f.reload(saved);
            f.cook(item("mite:mithril_ore"), 1, 1);
            f.take(1);
            helper.assertValueEqual(f.xp(), 160, "A new manually withdrawn ingot adds forty while saved hopper history creates no duplicate balance");
            f.furnace.awardUsedRecipesAndPopExperience(f.player);
            helper.assertValueEqual(f.xp(), 160, "A second history claim cannot duplicate hopper or result-slot XP");
        }
        helper.succeed();
    }

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    private record Output(String input, String output, int count) {}

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final BlockPos furnacePosition, hopperPosition, frontPosition;
        final BlockState oldFurnace, oldHopper, oldFront;
        final AABB area;
        final Set<UUID> oldOrbs = new HashSet<>(), oldItems = new HashSet<>();
        AbstractFurnaceBlockEntity furnace;
        FurnaceMenu menu;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            furnacePosition = helper.absolutePos(new BlockPos(1, 2, 1)); hopperPosition = furnacePosition.below();
            frontPosition = furnacePosition.north();
            oldFurnace = helper.getLevel().getBlockState(furnacePosition); oldHopper = helper.getLevel().getBlockState(hopperPosition);
            oldFront = helper.getLevel().getBlockState(frontPosition);
            area = new AABB(furnacePosition).inflate(5);
            helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area).forEach(orb -> oldOrbs.add(orb.getUUID()));
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(drop -> oldItems.add(drop.getUUID()));
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-ore-furnace"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            new ServerGamePacketListenerImpl(player.server, connection, player, cookie) { @Override public void send(Packet<?> packet) {} };
            player.moveTo(helper.absolutePos(new BlockPos(1, 3, 2)).getCenter()); player.getInventory().clearContent();
            // This XP fixture is against the template wall; open the source furnace's actual front.
            helper.getLevel().setBlockAndUpdate(frontPosition, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(furnacePosition, Blocks.FURNACE.defaultBlockState());
            furnace = (FurnaceBlockEntity) helper.getLevel().getBlockEntity(furnacePosition); open();
        }
        void open() { menu = new FurnaceMenu(73, player.getInventory(), furnace, new SimpleContainerData(4)); player.containerMenu = menu; }
        void cook(Item input, int amount, int expected) {
            helper.assertTrue(furnace.getItem(2).isEmpty(), "The furnace starts with no earlier unclaimed outputs");
            // The withdrawal assertions stay unchanged; the fixture now supplies the real source ore's furnace/fuel tier.
            String path = BuiltInRegistries.ITEM.getKey(input).getPath();
            String furnaceId = path.contains("adamantium") ? "mite:netherrack_furnace"
                    : path.contains("mithril") ? "mite:obsidian_furnace" : "minecraft:furnace";
            var targetBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(furnaceId));
            if (!helper.getLevel().getBlockState(furnacePosition).is(targetBlock)) {
                furnace.clearContent();
                helper.getLevel().setBlockAndUpdate(furnacePosition, targetBlock.defaultBlockState());
                furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(furnacePosition); open();
            }
            furnace.setItem(0, new ItemStack(input, amount));
            furnace.setItem(1, new ItemStack(path.contains("adamantium") ? Items.BLAZE_ROD
                    : path.contains("mithril") ? Items.LAVA_BUCKET : Items.COAL, path.contains("mithril") ? 1 : 8));
            for (int tick = 0; tick < amount * 200; tick++)
                AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), furnacePosition, helper.getLevel().getBlockState(furnacePosition), furnace);
            helper.assertValueEqual(furnace.getItem(2).getCount(), expected, "The real source-duration furnace produces the specified output quantity");
        }
        ItemStack take(int amount) { return menu.getSlot(2).safeTake(amount, amount, player); }
        int xp() {
            return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area).stream().filter(orb -> !oldOrbs.contains(orb.getUUID()))
                    .mapToInt(orb -> orb.getValue() * Math.max(1, orb.saveWithoutId(new CompoundTag()).getInt("Count"))).sum();
        }
        void reload(CompoundTag saved) {
            var state = helper.getLevel().getBlockState(furnacePosition);
            furnace = (AbstractFurnaceBlockEntity) ((BaseEntityBlock) state.getBlock()).newBlockEntity(furnacePosition, state);
            furnace.loadWithComponents(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(furnace); open();
        }
        @Override public void close() {
            try {
                player.containerMenu = player.inventoryMenu; player.getInventory().clearContent(); player.getTextFilter().leave();
                furnace.clearContent();
                if (helper.getLevel().getBlockEntity(hopperPosition) instanceof HopperBlockEntity hopper) hopper.clearContent();
                helper.getLevel().setBlockAndUpdate(furnacePosition, oldFurnace); helper.getLevel().setBlockAndUpdate(hopperPosition, oldHopper);
                helper.getLevel().setBlockAndUpdate(frontPosition, oldFront);
                helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area).stream().filter(orb -> !oldOrbs.contains(orb.getUUID())).forEach(ExperienceOrb::discard);
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().filter(drop -> !oldItems.contains(drop.getUUID())).forEach(ItemEntity::discard);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}
