package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.PlayerFoodEvents;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual output withdrawals and hopper transfers distinguish source-food XP from recipe history. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodFurnaceExperienceGameTests {
    private FoodFurnaceExperienceGameTests() {}

    @GameTest(template = "empty")
    public static void partialFoodWithdrawalAwardsOnlyTheTakenCountAndPaysRemainingFoodLater(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, false)) {
            f.cook(Items.BEEF, 10);
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 10, "The actual furnace produced ten cooked beef");
            f.menu.clicked(2, 0, ClickType.THROW, f.player);
            helper.assertValueEqual(f.droppedFood(Items.COOKED_BEEF), 1, "The real result-slot Q action removes and drops just one item");
            helper.assertValueEqual(f.xp(), 4, "One taken beef rewards four, not all forty production-history XP");
            helper.assertValueEqual(f.furnace.getItem(2).getCount(), 9, "The nine unclaimed foods remain in the furnace");
            helper.assertValueEqual(f.take(9).getCount(), 9, "The same result slot later removes all remaining food");
            helper.assertValueEqual(f.xp(), 40, "The later nine-food withdrawal rewards thirty-six more after native history was already cleared");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftAndHotbarWithdrawalsPayExactlyOnceOnFurnaceAndSmoker(GameTestHelper helper) {
        for (boolean smoker : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(helper, smoker)) {
                f.cook(Items.PORKCHOP, 4);
                f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.player);
                helper.assertValueEqual(f.player.getInventory().countItem(Items.COOKED_PORKCHOP), 4,
                        "Real shift movement transfers four cooked porkchops");
                helper.assertValueEqual(f.xp(), 12, "Quick-craft plus onTake callbacks pay the actual quantity once, with no native-bank duplicate");
                f.cook(Items.COD, 2);
                helper.assertTrue(f.player.getInventory().getItem(0).isEmpty(), "The hotbar-swap destination starts empty");
                f.menu.clicked(2, 0, ClickType.SWAP, f.player);
                helper.assertTrue(f.player.getInventory().getItem(0).is(Items.COOKED_COD), "The real hotbar swap removes the two fish");
                helper.assertValueEqual(f.player.getInventory().getItem(0).getCount(), 2, "The swap transfers both fish");
                helper.assertValueEqual(f.xp(), 18, "The swap-only callback rewards six more XP for the two fish");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hopperFoodHistorySurvivesSavingWithoutBecomingClaimableExperience(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, false)) {
            f.cook(Items.BEEF, 5);
            helper.getLevel().setBlockAndUpdate(f.hopperPosition, Blocks.HOPPER.defaultBlockState());
            HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(f.hopperPosition);
            for (int tick = 0; tick < 40; tick++)
                HopperBlockEntity.pushItemsTick(helper.getLevel(), f.hopperPosition, helper.getLevel().getBlockState(f.hopperPosition), hopper);
            helper.assertTrue(f.furnace.getItem(2).isEmpty(), "The real hopper withdrew all five food outputs");
            int collected = 0;
            for (int slot = 0; slot < hopper.getContainerSize(); slot++)
                if (hopper.getItem(slot).is(Items.COOKED_BEEF)) collected += hopper.getItem(slot).getCount();
            helper.assertValueEqual(collected, 5, "Exactly five cooked foods reached the hopper");
            helper.assertValueEqual(f.xp(), 0, "Automated withdrawal produces no player food experience");
            CompoundTag saved = f.furnace.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.assertValueEqual(saved.getCompound("RecipesUsed").getInt("minecraft:cooked_beef"), 5,
                    "Native recipe history remains available for unlocking, but it is not a food XP balance");
            f.reload(saved);
            f.cook(Items.BEEF, 1);
            f.take(1);
            helper.assertValueEqual(f.xp(), 4, "After saving and reloading only the newly taken beef pays XP; the five hopper foods never pay retroactively");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyFoodBankIsSuppressedWhileMineralAndModernFoodBanksRetainTheirRewards(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, false)) {
            CompoundTag saved = f.furnace.saveWithoutMetadata(helper.getLevel().registryAccess());
            CompoundTag legacyHistory = new CompoundTag();
            legacyHistory.putInt("minecraft:cooked_cod", 11);
            legacyHistory.putInt("minecraft:netherite_scrap", 3);
            legacyHistory.putInt("minecraft:dried_kelp_from_smelting", 10);
            // This existing key is a crafting recipe, so a stale/replaced history entry must
            // not enter native AbstractCookingRecipe's cast or fabricate cooking experience.
            legacyHistory.putInt("minecraft:dried_kelp", 30);
            saved.put("RecipesUsed", legacyHistory);
            f.reload(saved);
            f.furnace.awardUsedRecipesAndPopExperience(f.player);
            helper.assertValueEqual(f.xp(), 7,
                    "Legacy source fish history pays zero; native scrap 3×2 and modern kelp 10×0.1 still pay seven total");
            helper.assertTrue(f.furnace.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("RecipesUsed").isEmpty(),
                    "The native award path still clears recipe history after unlocking");
            f.furnace.awardUsedRecipesAndPopExperience(f.player);
            helper.assertValueEqual(f.xp(), 7, "A repeated native history claim cannot reproduce cleared XP");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceBreadWithdrawalHasNoRewardOrStoredExperience(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, false)) {
            f.cook(MiteFoods.get("dough"), 2);
            helper.assertTrue(f.take(2).is(Items.BREAD), "The actual dough cooking output is withdrawn from its result slot");
            helper.assertValueEqual(f.xp(), 0, "Bread's explicit source zero reward is respected by both withdrawal and historical-bank paths");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final boolean smoker;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos furnacePosition;
        private final BlockPos hopperPosition;
        private final BlockState previousFurnace;
        private final BlockState previousHopper;
        private final AABB area;
        private final Set<UUID> oldOrbs = new HashSet<>();
        private final Set<UUID> oldItems = new HashSet<>();
        private AbstractFurnaceBlockEntity furnace;
        private AbstractFurnaceMenu menu;

        private Fixture(GameTestHelper helper, boolean smoker) {
            this.helper = helper;
            this.smoker = smoker;
            furnacePosition = helper.absolutePos(new BlockPos(1, 2, 1));
            hopperPosition = furnacePosition.below();
            previousFurnace = helper.getLevel().getBlockState(furnacePosition);
            previousHopper = helper.getLevel().getBlockState(hopperPosition);
            area = new AABB(furnacePosition).inflate(5D);
            for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area)) oldOrbs.add(orb.getUUID());
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) oldItems.add(item.getUUID());
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-food-xp"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            // The real ServerPlayer recipe-award path needs a listener, but these server-only tests
            // deliberately observe world XP rather than transmitting UI packets to a client.
            new ServerGamePacketListenerImpl(player.server, connection, player, cookie) {
                @Override public void send(Packet<?> packet) {}
            };
            player.moveTo(helper.absolutePos(new BlockPos(1, 3, 2)).getCenter());
            player.getInventory().clearContent();
            helper.getLevel().setBlockAndUpdate(furnacePosition, smoker ? Blocks.SMOKER.defaultBlockState() : Blocks.FURNACE.defaultBlockState());
            furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(furnacePosition);
            openMenu();
        }
        private void openMenu() {
            menu = smoker ? new SmokerMenu(72, player.getInventory(), furnace, new SimpleContainerData(4))
                    : new FurnaceMenu(72, player.getInventory(), furnace, new SimpleContainerData(4));
            player.containerMenu = menu;
        }
        private void cook(Item raw, int amount) {
            helper.assertTrue(furnace.getItem(2).isEmpty(), "Cooking starts with no unclaimed previous output");
            furnace.setItem(0, new ItemStack(raw, amount));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));
            for (int tick = 0; tick < amount * 200; tick++)
                AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), furnacePosition, helper.getLevel().getBlockState(furnacePosition), furnace);
            helper.assertValueEqual(furnace.getItem(2).getCount(), amount, "Actual two-hundred-tick recipes produced the requested source quantity");
        }
        private ItemStack take(int amount) { return menu.getSlot(2).safeTake(amount, amount, player); }
        private int droppedFood(Item food) {
            int total = 0;
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area))
                if (!oldItems.contains(item.getUUID()) && item.getItem().is(food)) total += item.getItem().getCount();
            return total;
        }
        private int xp() {
            int total = 0;
            for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area)) {
                if (oldOrbs.contains(orb.getUUID())) continue;
                // Native XP award may merge equal-valued orbs. Saved Count is its actual persisted
                // multiplicity, so this check remains deterministic without accessing private fields.
                CompoundTag saved = orb.saveWithoutId(new CompoundTag());
                total += orb.getValue() * Math.max(saved.getInt("Count"), 1);
            }
            return total;
        }
        private void reload(CompoundTag saved) {
            helper.assertFalse(smoker, "This persistence fixture deliberately exercises the ordinary furnace type");
            FurnaceBlockEntity restored = new FurnaceBlockEntity(furnacePosition, helper.getLevel().getBlockState(furnacePosition));
            restored.loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.getLevel().setBlockEntity(restored);
            furnace = restored;
            openMenu();
        }
        @Override public void close() {
            try {
                player.containerMenu = player.inventoryMenu;
                player.getInventory().clearContent();
                PlayerFoodEvents.loggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
                player.getTextFilter().leave();
                furnace.clearContent();
                if (helper.getLevel().getBlockEntity(hopperPosition) instanceof HopperBlockEntity hopper) hopper.clearContent();
                helper.getLevel().setBlockAndUpdate(furnacePosition, previousFurnace);
                helper.getLevel().setBlockAndUpdate(hopperPosition, previousHopper);
                helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area).stream()
                        .filter(orb -> !oldOrbs.contains(orb.getUUID())).forEach(ExperienceOrb::discard);
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                        .filter(item -> !oldItems.contains(item.getUUID())).forEach(ItemEntity::discard);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}
