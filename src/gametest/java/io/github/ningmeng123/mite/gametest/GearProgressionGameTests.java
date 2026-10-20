package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.combat.ShieldGuard;
import io.github.ningmeng123.mite.item.EquipmentQuality;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearModifier;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual growth, item transport and maintenance, with ordinary enchantments kept independent. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class GearProgressionGameTests {
    private GearProgressionGameTests() {}

    @GameTest(template = "empty")
    public static void extensionLevelTwentyEightRoundTripsWithoutChangingStandaloneMaterialCap(GameTestHelper h) {
        ItemStack stack=new ItemStack(MiteTools.get("mite:vibranium_sword"));
        GearProgress progress=new GearProgress(UUID.randomUUID(),28,0,Map.of(),1);
        stack.set(GearProgression.PROGRESS.get(),progress);
        var tag=stack.save(h.getLevel().registryAccess());
        ItemStack restored=ItemStack.parse(h.getLevel().registryAccess(),tag).orElseThrow();
        h.assertTrue(GearProgression.state(restored).equals(progress), "Extension levels through28 retain their identity and growth on item save/load");
        h.assertTrue(GearProgression.maxLevel(restored)==25, "The default policy does not increase standalone vibranium growth");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void integerThresholdAndOversizedAwardsPreserveOrdinaryEnchantments(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ScriptedRandom random = session.random(0, 0, 0);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            var sharpness = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
            sword.enchant(sharpness, 3);
            ItemEnchantments ordinary = sword.get(DataComponents.ENCHANTMENTS);
            long threshold = GearProgression.nextLevelExperienceMillis(sword, 0);
            GearProgression.award(sword, threshold - 1, session.player);
            h.assertTrue(GearProgression.state(sword).level() == 0
                    && GearProgression.state(sword).experienceMillis() == threshold - 1000,
                    "A source integer award discards the fractional point before accumulation");
            GearProgression.award(sword, 999, session.player);
            h.assertTrue(GearProgression.state(sword).experienceMillis() == threshold - 1000,
                    "A separate sub-point award cannot accumulate toward an equipment level");
            GearProgression.award(sword, 1000, session.player);
            h.assertTrue(GearProgression.state(sword).level() == 1 && GearProgression.state(sword).experienceMillis() == 0, "Exact threshold upgrades once");
            GearProgression.award(sword, 500_123, session.player);
            h.assertTrue(GearProgression.state(sword).level() == 2 && GearProgression.state(sword).experienceMillis() == 0,
                    "An oversized award upgrades once and discards every point above that threshold");
            GearProgression.award(sword, GearProgression.nextLevelExperienceMillis(sword, 2), session.player);
            h.assertTrue(GearProgression.state(sword).level() == 3 && GearProgression.state(sword).experienceMillis() == 0,
                    "The next independent award uses the current weapon level's linear threshold");
            h.assertTrue(GearProgression.state(sword).affixes().values().stream().mapToInt(Integer::intValue).sum() == 3,
                    "Each of the three reached levels grants one custom modifier rank");
            h.assertTrue(random.finished() && random.bounds.equals(List.of(74, 74, 74)),
                    "Each level makes exactly one weighted selection without a success-chance roll");
            h.assertTrue(ordinary.equals(sword.get(DataComponents.ENCHANTMENTS)), "Growth never changes ordinary enchantments");
            h.assertTrue(session.player.totalExperience == 0, "Gear experience does not alter player experience");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void anOversizedAwardStopsAfterOneLevelAndLaterAwardsExhaustTheCustomPool(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ScriptedRandom random = session.random(19, 1);
            ItemStack pickaxe = almostFullEfficiencyAndGeology(h);
            GearProgression.award(pickaxe, 600_123, session.player);
            GearProgress after = GearProgression.state(pickaxe);
            h.assertTrue(after.level() == 1 && after.experienceMillis() == 0
                    && after.affixes().get(modifierId("tool/efficiency")) == 10
                    && !after.affixes().containsKey(modifierId("tool/geology")),
                    "A single large award grants one rank and discards all excess experience");
            GearProgression.award(pickaxe, GearProgression.nextLevelExperienceMillis(pickaxe, 1), session.player);
            GearProgression.award(pickaxe, GearProgression.nextLevelExperienceMillis(pickaxe, 2), session.player);
            after = GearProgression.state(pickaxe);
            h.assertTrue(after.level() == 3 && after.experienceMillis() == 0,
                    "Three separate awards can reach three levels even after the candidate pool empties");
            h.assertTrue(after.affixes().get(modifierId("tool/efficiency")) == 10
                    && after.affixes().get(modifierId("tool/geology")) == 1,
                    "The last efficiency rank and the single geology rank each consume one reached level");
            h.assertTrue(random.finished() && random.bounds.equals(List.of(22, 2)),
                    "A capped entry leaves the denominator immediately and an empty pool draws nothing");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vibraniumLevelTwentyFiveAlwaysGrantsAnAffixAndFullGearDoesNotRoll(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ItemStack pickaxe = new ItemStack(MiteTools.get("vibranium_pickaxe"));
            UUID identity = UUID.randomUUID();
            pickaxe.set(GearProgression.PROGRESS.get(), new GearProgress(identity, 24, 0, Map.of(), 1));
            int totalWeight = GearProgression.modifierCandidates(pickaxe).stream().mapToInt(GearModifier::weight).sum();
            h.assertTrue(GearProgression.maxLevel(pickaxe) == 25 && totalWeight > 0,
                    "Vibranium has a level twenty-five cap and an applicable reward pool");
            ScriptedRandom random = session.random(0);
            GearProgression.award(pickaxe, GearProgression.nextLevelExperienceMillis(pickaxe, 24), session.player);
            GearProgress full = GearProgression.state(pickaxe);
            h.assertTrue(full.level() == 25 && full.experienceMillis() == 0 && full.id().equals(identity)
                    && full.affixes().values().stream().mapToInt(Integer::intValue).sum() == 1,
                    "Reaching level twenty-five grants one custom rank and preserves the equipment identity");
            h.assertTrue(random.finished() && random.bounds.equals(List.of(totalWeight)),
                    "The level-twenty-five reward makes exactly one weighted selection");
            GearProgression.award(pickaxe, Long.MAX_VALUE, session.player);
            h.assertTrue(full.equals(GearProgression.state(pickaxe))
                    && random.bounds.equals(List.of(totalWeight)),
                    "Full vibranium gear keeps its exact state without consuming any more random values");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void maximumLevelStopsAwardsAndCreativeDoesNotGain(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ScriptedRandom random = session.random(new int[21]);
            ItemStack copper = new ItemStack(MiteTools.get("copper_sword"));
            for (int level = 0; level < 25; level++)
                GearProgression.award(copper, GearProgression.nextLevelExperienceMillis(copper, level), session.player);
            GearProgress full = GearProgression.state(copper);
            h.assertTrue(full.level() == 25 && full.experienceMillis() == 0
                    && full.affixes().values().stream().mapToInt(Integer::intValue).sum() == 21,
                    "Copper reaches the standalone cap after saturating the weapon pool's twenty-one ranks");
            GearProgression.award(copper, 500_000, session.player);
            h.assertTrue(full.equals(GearProgression.state(copper)), "Full gear ignores further experience");
            session.player.setGameMode(GameType.CREATIVE);
            ItemStack fresh = new ItemStack(Items.IRON_SWORD);
            GearProgression.award(fresh, 100_000, session.player);
            h.assertTrue(!fresh.has(GearProgression.PROGRESS.get()), "Creative use creates no growth state");
            h.assertTrue(random.finished() && random.bounds.size() == 21, "Empty, full and creative equipment never make extra reward rolls");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void candidatesExcludeCursesAndKeepExistingConflictsIndependent(GameTestHelper h) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        pickaxe.enchant(registry.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        var pool = GearProgression.legacyCandidates(pickaxe, h.getLevel());
        h.assertTrue(pool.stream().noneMatch(holder -> holder.is(EnchantmentTags.CURSE)), "No curse is a growth candidate");
        h.assertTrue(pool.stream().noneMatch(holder -> holder.is(Enchantments.MENDING)), "Mending is excluded from growth affixes");
        ItemStack mending = new ItemStack(Items.IRON_PICKAXE);
        mending.enchant(registry.getHolderOrThrow(Enchantments.MENDING), 1);
        h.assertTrue(mending.get(DataComponents.ENCHANTMENTS).getLevel(registry.getHolderOrThrow(Enchantments.MENDING)) == 1,
                "Ordinary Mending remains available");
        h.assertTrue(pool.stream().anyMatch(holder -> holder.is(Enchantments.FORTUNE)), "Normal silk touch does not forbid fortune affixes");
        h.assertTrue(GearProgression.legacyCandidates(new ItemStack(Items.LEATHER_CHESTPLATE), h.getLevel()).stream()
                .anyMatch(holder -> holder.is(Enchantments.UNBREAKING)), "Existing leather Unbreaking support is included");
        h.assertTrue(GearProgression.maxLevel(new ItemStack(Items.CROSSBOW)) == 25
                && GearProgression.maxLevel(new ItemStack(Items.TRIDENT)) == 25
                && GearProgression.maxLevel(new ItemStack(Items.MACE)) == 25, "Standalone special weapons share the uniform cap");
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

    @GameTest(template = "empty")
    public static void weightedCandidatesUseOnlyApplicableUnsaturatedAffixRanks(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var sharpness = registry.getHolderOrThrow(Enchantments.SHARPNESS);
        var efficiency = registry.getHolderOrThrow(Enchantments.EFFICIENCY);
        var silk = registry.getHolderOrThrow(Enchantments.SILK_TOUCH);
        var unbreaking = registry.getHolderOrThrow(Enchantments.UNBREAKING);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(sharpness, 10);
        UUID id = UUID.randomUUID();
        sword.set(GearProgression.PROGRESS.get(), new GearProgress(id, 1, 0,
                Map.of(Enchantments.SHARPNESS.location(), 9), 1));
        h.assertTrue(GearProgression.legacyCandidates(sword, h.getLevel()).contains(sharpness),
                "Ordinary Sharpness X does not consume the independent affix's remaining tenth rank");
        h.assertFalse(GearProgression.legacyCandidates(sword, h.getLevel()).contains(efficiency), "A sword never enters the legacy mining-efficiency pool");
        sword.set(GearProgression.PROGRESS.get(), new GearProgress(id, 1, 0,
                Map.of(Enchantments.SHARPNESS.location(), 10), 1));
        h.assertFalse(GearProgression.legacyCandidates(sword, h.getLevel()).contains(sharpness), "Legacy Sharpness X has reached its own limit");

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        h.assertTrue(GearProgression.affixDefinition(pickaxe, efficiency).weight() == 20
                && GearProgression.affixDefinition(pickaxe, efficiency).maximumLevel() == 10,
                "The live pickaxe resolves the explicit efficiency weight and cap");
        h.assertTrue(GearProgression.affixDefinition(pickaxe, unbreaking).weight() == 40
                && GearProgression.affixDefinition(new ItemStack(Items.IRON_CHESTPLATE), unbreaking).weight() == 20,
                "Armor and tools resolve their distinct Unbreaking weights");
        h.assertTrue(GearProgression.affixDefinition(pickaxe, silk).weight() == 2
                && GearProgression.affixDefinition(pickaxe, silk).maximumLevel() == 1,
                "Silk touch retains its rare, single-rank growth definition");
        pickaxe.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 1, 0,
                Map.of(Enchantments.SILK_TOUCH.location(), 1), 1));
        h.assertFalse(GearProgression.legacyCandidates(pickaxe, h.getLevel()).contains(silk), "An existing silk rank is removed from the legacy pool");
        h.assertTrue(GearProgression.legacyCandidates(pickaxe, h.getLevel()).stream().anyMatch(holder -> holder.is(Enchantments.FORTUNE)),
                "Saturated silk does not suppress a different, conflicting fortune affix");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyAffixPoolsKeepPartialExperienceButDiscardTheRemainderOnEachUpgrade(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ScriptedRandom random = session.random();
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            Map<ResourceLocation, Integer> capped = cappedRanks(h, pickaxe);
            capped.put(ResourceLocation.parse("missing:legacy_affix"), 231);
            UUID id = UUID.randomUUID();
            pickaxe.set(GearProgression.PROGRESS.get(), new GearProgress(id, 0, 0, capped, 1));
            h.assertTrue(GearProgression.modifierCandidates(pickaxe).isEmpty(), "The fixture has no unsaturated custom modifiers");
            long threshold = GearProgression.nextLevelExperienceMillis(pickaxe, 0);
            GearProgression.award(pickaxe, threshold - 1000, session.player);
            h.assertTrue(GearProgression.state(pickaxe).level() == 0
                    && GearProgression.state(pickaxe).experienceMillis() == threshold - 1000,
                    "An empty candidate pool retains ordinary below-threshold integer experience");
            GearProgression.award(pickaxe, 600_123, session.player);
            GearProgress partial = GearProgression.state(pickaxe);
            h.assertTrue(partial.level() == 1 && partial.experienceMillis() == 0 && partial.affixes().equals(capped)
                    && partial.id().equals(id), "An empty pool preserves every old rank and UUID while a large award advances only one level");
            for (int level = 1; level < 25; level++)
                GearProgression.award(pickaxe, GearProgression.nextLevelExperienceMillis(pickaxe, level), session.player);
            GearProgress full = GearProgression.state(pickaxe);
            h.assertTrue(full.level() == 25 && full.experienceMillis() == 0 && full.affixes().equals(capped),
                    "A fully saturated pool still reaches the standalone cap and clears excess experience");
            h.assertTrue(random.finished() && random.bounds.isEmpty(), "An empty pool makes neither chance nor weighted-selection draws");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void consecutiveLevelsRemoveNewlyCappedAffixesAndRecomputeWeightDenominators(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ScriptedRandom random = session.random(20, 19);
            ItemStack pickaxe = almostFullEfficiencyAndGeology(h);
            for (int level = 0; level < 3; level++)
                GearProgression.award(pickaxe, GearProgression.nextLevelExperienceMillis(pickaxe, level), session.player);
            GearProgress state = GearProgression.state(pickaxe);
            h.assertTrue(state.level() == 3 && state.experienceMillis() == 0, "Three separate awards advance even after the second reward empties the pool");
            h.assertTrue(state.affixes().get(modifierId("tool/geology")) == 1
                    && state.affixes().get(modifierId("tool/efficiency")) == 10,
                    "The rare geology reward and the last efficiency rank stop exactly at their individual caps");
            h.assertTrue(GearProgression.modifierCandidates(pickaxe).isEmpty(), "The newly saturated entries are no longer candidates");
            h.assertTrue(random.finished() && random.bounds.equals(List.of(22, 20)),
                    "The second selection divides by twenty after geology's weight two leaves, and the third level draws nothing");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void weightedSelectionsRespectEveryBoundaryOfCommonAndRareIntervals(GameTestHelper h) {
        try (Session session = new Session(h)) {
            for (int roll : new int[]{0, 19, 20, 21}) {
                ScriptedRandom random = session.random(roll);
                ItemStack pickaxe = almostFullEfficiencyAndGeology(h);
                GearProgression.award(pickaxe, GearProgression.nextLevelExperienceMillis(pickaxe, 0), session.player);
                GearProgress state = GearProgression.state(pickaxe);
                boolean common = roll < 20;
                h.assertTrue(state.level() == 1 && state.affixes().get(modifierId("tool/efficiency")) == (common ? 10 : 9)
                        && state.affixes().getOrDefault(modifierId("tool/geology"), 0) == (common ? 0 : 1),
                        "Roll " + roll + " selects the exact twenty-slot common or two-slot rare interval");
                h.assertTrue(random.finished() && random.bounds.equals(List.of(22)), "An upgrade uses one draw from its current total weight");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rewardsNeverClampOldHighRanksOrEraseUnavailableAndExcludedAffixes(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ScriptedRandom random = session.random(0);
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            var efficiency = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.EFFICIENCY);
            pickaxe.enchant(efficiency, 3);
            ItemEnchantments ordinary = pickaxe.get(DataComponents.ENCHANTMENTS);
            ResourceLocation missing = ResourceLocation.parse("missing:temporarily_removed");
            Map<ResourceLocation, Integer> legacy = Map.of(Enchantments.EFFICIENCY.location(), 17,
                    Enchantments.MENDING.location(), 2, missing, 231);
            UUID id = UUID.randomUUID();
            pickaxe.set(GearProgression.PROGRESS.get(), new GearProgress(id, 1, 0, legacy, 1));
            var pool = GearProgression.legacyCandidates(pickaxe, h.getLevel());
            h.assertTrue(pool.stream().noneMatch(holder -> holder.is(Enchantments.EFFICIENCY) || holder.is(Enchantments.MENDING)),
                    "Old over-cap and excluded affixes cannot be selected again");
            GearProgression.award(pickaxe, GearProgression.nextLevelExperienceMillis(pickaxe, 1) + 321, session.player);
            GearProgress after = GearProgression.state(pickaxe);
            h.assertTrue(after.level() == 2 && after.experienceMillis() == 0 && after.id().equals(id),
                    "The existing item progresses without changing identity");
            legacy.forEach((key, rank) -> h.assertTrue(after.affixes().get(key).equals(rank), "The exact old rank remains for " + key));
            h.assertTrue(after.affixes().values().stream().mapToInt(Integer::intValue).sum() == 251
                    && ordinary.equals(pickaxe.get(DataComponents.ENCHANTMENTS)), "Only one new rank is added and ordinary enchants remain untouched");
            h.assertTrue(EnchantmentSources.levels(pickaxe, efficiency).affix() == 17,
                    "The source-effect API still sees the complete historical rank rather than the new acquisition cap");

            ItemStack saved = ItemStack.parse(h.getLevel().registryAccess(), pickaxe.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(GearProgression.state(saved).equals(after), "Saving and reloading do not migrate or truncate old ranks");
            RegistryFriendlyByteBuf bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                ItemStack.STREAM_CODEC.encode(bytes, pickaxe);
                h.assertTrue(GearProgression.state(ItemStack.STREAM_CODEC.decode(bytes)).equals(after), "Network synchronization retains every historical rank");
            } finally { bytes.release(); }
            ItemStack repairSource = pickaxe.copy();
            repairSource.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            repairSource.setDamageValue(5000);
            ItemStack sacrifice = new ItemStack(Items.IRON_PICKAXE); sacrifice.setDamageValue(5000);
            ItemStack repaired = new RepairItemRecipe(CraftingBookCategory.EQUIPMENT).assemble(
                    CraftingInput.of(2, 1, List.of(repairSource, sacrifice)), h.getLevel().registryAccess());
            h.assertTrue(!repaired.isEmpty() && GearProgression.state(repaired).equals(after), "Actual crafted repair preserves the full old growth component");
            h.assertTrue(ordinary.equals(pickaxe.get(DataComponents.ENCHANTMENTS)), "The repair fixture leaves the original item's ordinary enchantments untouched");
            h.assertTrue(random.finished() && random.bounds.equals(List.of(78)), "The new custom modifier consumes one draw independent of legacy enchantment ranks");
        }
        h.succeed();
    }

    private static Map<ResourceLocation, Integer> cappedRanks(GameTestHelper h, ItemStack stack) {
        Map<ResourceLocation, Integer> result = new HashMap<>();
        for (var candidate : GearProgression.modifierCandidates(stack)) result.put(candidate.id(), candidate.maximumLevel());
        return result;
    }
    private static ItemStack almostFullEfficiencyAndGeology(GameTestHelper h) {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        Map<ResourceLocation, Integer> ranks = cappedRanks(h, stack);
        ranks.put(modifierId("tool/efficiency"), 9);
        ranks.remove(modifierId("tool/geology"));
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 0, 0, ranks, 1));
        return stack;
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath("mite", "modifier/" + path);
    }

    private static ItemStack grown(net.minecraft.world.item.Item item, int level, long experience, String affix, int rank) {
        ItemStack stack = new ItemStack(item);
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), level, experience, Map.of(ResourceLocation.parse(affix), rank), 1));
        return stack;
    }
    private static final class Session implements AutoCloseable {
        final ControlledPlayer player;
        final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-growth"), false);
            player = new ControlledPlayer(h, cookie);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            PlayerExperience.setTotal(player, 0);
        }
        ScriptedRandom random(int... rolls) {
            ScriptedRandom source = new ScriptedRandom(rolls);
            player.controlled = source;
            return source;
        }
        @Override public void close() {
            player.controlled = null;
            player.getInventory().clearContent();
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }

    /** Installed only after login so world initialization never consumes a reward's scripted rolls. */
    private static final class ControlledPlayer extends ServerPlayer {
        private RandomSource controlled;
        private ControlledPlayer(GameTestHelper h, CommonListenerCookie cookie) {
            super(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        }
        @Override public RandomSource getRandom() { return controlled == null ? super.getRandom() : controlled; }
    }
    private static final class ScriptedRandom extends LegacyRandomSource {
        private final ArrayDeque<Integer> rolls = new ArrayDeque<>();
        private final List<Integer> bounds = new ArrayList<>();
        private ScriptedRandom(int... values) { super(0L); for (int value : values) rolls.addLast(value); }
        @Override public int nextInt(int bound) {
            bounds.add(bound);
            if (rolls.isEmpty()) throw new AssertionError("Unexpected reward draw with bound " + bound);
            int value = rolls.removeFirst();
            if (value < 0 || value >= bound) throw new AssertionError("Scripted reward roll " + value + " outside bound " + bound);
            return value;
        }
        private boolean finished() { return rolls.isEmpty(); }
    }
}
