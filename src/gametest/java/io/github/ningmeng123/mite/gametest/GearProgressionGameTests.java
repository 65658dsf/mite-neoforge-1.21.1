package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.ShieldGuard;
import io.github.ningmeng123.mite.item.EquipmentQuality;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual growth, item transport and maintenance, with ordinary enchantments kept independent. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class GearProgressionGameTests {
    private GearProgressionGameTests() {}

    @GameTest(template = "empty")
    public static void thresholdAndMultiLevelAwardsPreserveOrdinaryEnchantments(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            var sharpness = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
            sword.enchant(sharpness, 3);
            ItemEnchantments ordinary = sword.get(DataComponents.ENCHANTMENTS);
            GearProgression.award(sword, 99_999, session.player);
            h.assertTrue(GearProgression.state(sword).level() == 0, "A partial point cannot cross the first threshold");
            GearProgression.award(sword, 1, session.player);
            h.assertTrue(GearProgression.state(sword).level() == 1 && GearProgression.state(sword).experienceMillis() == 0, "Exact threshold upgrades once");
            GearProgression.award(sword, 500_123, session.player);
            h.assertTrue(GearProgression.state(sword).level() == 3 && GearProgression.state(sword).experienceMillis() == 123, "One award can cross two thresholds and preserves the remainder");
            h.assertTrue(GearProgression.state(sword).affixes().values().stream().mapToInt(Integer::intValue).sum() == 3, "Every gained level awards exactly one rank");
            h.assertTrue(ordinary.equals(sword.get(DataComponents.ENCHANTMENTS)), "Growth never changes ordinary enchantments");
            h.assertTrue(session.player.totalExperience == 0, "Gear experience does not alter player experience");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void maximumLevelStopsAwardsAndCreativeDoesNotGain(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ItemStack copper = new ItemStack(MiteTools.get("copper_sword"));
            GearProgression.award(copper, Long.MAX_VALUE, session.player);
            GearProgress full = GearProgression.state(copper);
            h.assertTrue(full.level() == 5 && full.experienceMillis() == 0
                    && full.affixes().values().stream().mapToInt(Integer::intValue).sum() == 5, "Copper stops at five rewards");
            GearProgression.award(copper, 500_000, session.player);
            h.assertTrue(full.equals(GearProgression.state(copper)), "Full gear ignores further experience");
            session.player.setGameMode(GameType.CREATIVE);
            ItemStack fresh = new ItemStack(Items.IRON_SWORD);
            GearProgression.award(fresh, 100_000, session.player);
            h.assertTrue(!fresh.has(GearProgression.PROGRESS.get()), "Creative use creates no growth state");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void candidatesExcludeCursesAndKeepExistingConflictsIndependent(GameTestHelper h) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        pickaxe.enchant(registry.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        var pool = GearProgression.candidates(pickaxe, h.getLevel());
        h.assertTrue(pool.stream().noneMatch(holder -> holder.is(EnchantmentTags.CURSE)), "No curse is a growth candidate");
        h.assertTrue(pool.stream().noneMatch(holder -> holder.is(Enchantments.MENDING)), "Mending is excluded from growth affixes");
        ItemStack mending = new ItemStack(Items.IRON_PICKAXE);
        mending.enchant(registry.getHolderOrThrow(Enchantments.MENDING), 1);
        h.assertTrue(mending.get(DataComponents.ENCHANTMENTS).getLevel(registry.getHolderOrThrow(Enchantments.MENDING)) == 1,
                "Ordinary Mending remains available");
        h.assertTrue(pool.stream().anyMatch(holder -> holder.is(Enchantments.FORTUNE)), "Normal silk touch does not forbid fortune affixes");
        h.assertTrue(GearProgression.candidates(new ItemStack(Items.LEATHER_CHESTPLATE), h.getLevel()).stream()
                .anyMatch(holder -> holder.is(Enchantments.UNBREAKING)), "Existing leather Unbreaking support is included");
        h.assertTrue(GearProgression.maxLevel(new ItemStack(Items.CROSSBOW)) == 5
                && GearProgression.maxLevel(new ItemStack(Items.TRIDENT)) == 20
                && GearProgression.maxLevel(new ItemStack(Items.MACE)) == 20, "Special weapons have explicit caps");
        for (var item : List.of(Items.FISHING_ROD, Items.ELYTRA, Items.IRON_HORSE_ARMOR, Items.DIAMOND_SWORD))
            h.assertTrue(GearProgression.maxLevel(new ItemStack(item)) == 0, "Excluded and removed equipment is not restored");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void savedAndNetworkStacksPreserveIdentityExperienceAndUnknownAffixIds(GameTestHelper h) {
        ItemStack stack = grown(Items.IRON_PICKAXE, 4, 123_456, "minecraft:efficiency", 4);
        GearProgress source = GearProgression.state(stack);
        var saved = stack.save(h.getLevel().registryAccess());
        ItemStack loaded = ItemStack.parse(h.getLevel().registryAccess(), saved).orElseThrow();
        h.assertTrue(source.equals(GearProgression.state(loaded)), "Disk round trip preserves exact growth");
        RegistryFriendlyByteBuf bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(bytes, stack);
            ItemStack decoded = ItemStack.STREAM_CODEC.decode(bytes);
            h.assertTrue(source.equals(GearProgression.state(decoded)), "Network round trip preserves exact growth");
        } finally { bytes.release(); }
        ItemStack unknown = grown(Items.IRON_PICKAXE, 1, 0, "missing:temporarily_removed", 1);
        h.assertTrue(GearProgression.state(ItemStack.parse(h.getLevel().registryAccess(), unknown.save(h.getLevel().registryAccess())).orElseThrow())
                .affixes().containsKey(ResourceLocation.parse("missing:temporarily_removed")), "Unavailable registry entries are preserved as IDs");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repairRenameAndGrindstoneInheritOnlyFirstGrowth(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        PlayerExperience.setTotal(owner, 1_000_000);
        ItemStack first = grown(Items.IRON_PICKAXE, 3, 123, "minecraft:efficiency", 3);
        ItemStack second = grown(Items.IRON_PICKAXE, 2, 456, "minecraft:fortune", 2);
        first.setDamageValue(5000); second.setDamageValue(5000);
        ItemStack crafted = new RepairItemRecipe(CraftingBookCategory.EQUIPMENT).assemble(CraftingInput.of(2, 1, List.of(first, second)), h.getLevel().registryAccess());
        h.assertTrue(GearProgression.state(crafted).equals(GearProgression.state(first)), "Crafted repair keeps only the first input's growth");
        AnvilMenu anvil = new AnvilMenu(1, owner.getInventory());
        anvil.getSlot(0).set(first.copy()); anvil.setItemName("NingMeng123");
        h.assertTrue(!anvil.getSlot(2).getItem().isEmpty() && GearProgression.state(anvil.getSlot(2).getItem()).equals(GearProgression.state(first)), "Rename keeps progression");
        GrindstoneMenu grindstone = new GrindstoneMenu(2, owner.getInventory());
        grindstone.getSlot(0).set(first.copy()); grindstone.getSlot(1).set(second.copy());
        h.assertTrue(!grindstone.getSlot(2).getItem().isEmpty() && GearProgression.state(grindstone.getSlot(2).getItem()).equals(GearProgression.state(first)), "Grindstone does not merge growth");
        ItemStack upgrade = new ItemStack(MiteTools.get("vibranium_pickaxe"));
        EquipmentQuality.preserve(first, upgrade);
        h.assertTrue(GearProgression.state(upgrade).equals(GearProgression.state(first)) && GearProgression.maxLevel(upgrade) == 25, "Material upgrades inherit growth with the new cap");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void growthOnlyChangesDoNotMaskDamageNamesOrOrdinaryEnchantments(GameTestHelper h) {
        ItemStack plain = new ItemStack(Items.IRON_PICKAXE);
        ItemStack grown = grown(Items.IRON_PICKAXE, 1, 100, "minecraft:efficiency", 1);
        h.assertTrue(GearProgression.sameIgnoringGrowth(plain, grown), "Growth is excluded from the animation comparison");
        ItemStack another = grown(Items.IRON_PICKAXE, 1, 100, "minecraft:efficiency", 1);
        h.assertFalse(GearProgression.sameIgnoringGrowth(grown, another), "Different physical UUIDs are genuine equipment replacements");
        h.assertFalse(GearProgression.growthChanged(grown, grown.copy()), "Unchanged growth does not override other mods' hand hooks");
        grown.setDamageValue(1);
        h.assertFalse(GearProgression.sameIgnoringGrowth(plain, grown), "Damage is still a real component change");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldGuardDisplayUpdatesPreservePoseWithoutMaskingRealEquipmentChanges(GameTestHelper h) {
        ItemStack plain = new ItemStack(Items.SHIELD);
        ItemStack guarded = plain.copy();
        guarded.set(ShieldGuard.GUARD.get(), new ShieldGuard.State(4D, 1));
        h.assertTrue(GearProgression.guardChanged(plain, guarded) && !GearProgression.growthChanged(plain, guarded),
                "A guard-only packet is recognized independently of experience");
        h.assertTrue(GearProgression.keepProgressionHandPose(plain, guarded, false), "Guard-only updates retain the raised shield pose");
        h.assertFalse(GearProgression.sameIgnoringGrowth(plain, guarded), "Mining retains its existing growth-only exception");

        ItemStack before = grown(Items.SHIELD, 1, 100, "minecraft:unbreaking", 1);
        before.set(ShieldGuard.GUARD.get(), new ShieldGuard.State(4D, 1));
        ItemStack after = before.copy();
        GearProgress progress = GearProgression.state(before);
        after.set(GearProgression.PROGRESS.get(), new GearProgress(progress.id(), progress.level(), 200, progress.affixes(), progress.version()));
        after.set(ShieldGuard.GUARD.get(), new ShieldGuard.State(8D, 2));
        h.assertTrue(GearProgression.growthChanged(before, after) && GearProgression.guardChanged(before, after)
                && GearProgression.keepProgressionHandPose(before, after, false), "Combined guard and experience packets retain the same pose");
        h.assertFalse(GearProgression.keepProgressionHandPose(before, after, true), "Changing slots retains native animation behavior");
        h.assertFalse(GearProgression.keepProgressionHandPose(before, before.copy(), false), "Unchanged bookkeeping does not override other item hooks");

        ItemStack reset = guarded.copy(); reset.remove(ShieldGuard.GUARD.get());
        h.assertTrue(GearProgression.keepProgressionHandPose(guarded, reset, false), "Removing the guard component is also display-only");
        ItemStack changed = after.copy(); changed.setDamageValue(1);
        h.assertFalse(GearProgression.keepProgressionHandPose(before, changed, false), "Real durability changes retain native animation behavior");
        changed = after.copy(); changed.setCount(2);
        h.assertFalse(GearProgression.keepProgressionHandPose(before, changed, false), "Count changes are not hidden");
        changed = after.copy(); changed.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("NingMeng123"));
        h.assertFalse(GearProgression.keepProgressionHandPose(before, changed, false), "Renaming remains a real component change");
        changed = after.copy();
        changed.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 1);
        h.assertFalse(GearProgression.keepProgressionHandPose(before, changed, false), "Ordinary enchantment changes are not hidden");
        changed = after.copy();
        changed.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), progress.level(), 200, progress.affixes(), progress.version()));
        h.assertFalse(GearProgression.keepProgressionHandPose(before, changed, false), "A different physical shield identity still replaces the rendered item");
        h.assertFalse(GearProgression.keepProgressionHandPose(before, new ItemStack(Items.IRON_SWORD), false), "Different item types keep native behavior");
        h.succeed();
    }

    private static ItemStack grown(net.minecraft.world.item.Item item, int level, long experience, String affix, int rank) {
        ItemStack stack = new ItemStack(item);
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), level, experience, Map.of(ResourceLocation.parse(affix), rank), 1));
        return stack;
    }
    private static final class Session implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-growth"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            PlayerExperience.setTotal(player, 0);
        }
        @Override public void close() {
            player.getInventory().clearContent();
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
