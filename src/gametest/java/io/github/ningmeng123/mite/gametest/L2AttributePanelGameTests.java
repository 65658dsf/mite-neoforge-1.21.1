package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.client.L2AttributePanel;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Effective panel distances share gameplay calculations and preserve real attributes. @author NingMeng123 */
@GameTestHolder("mite_l2_panel")
@PrefixGameTestTemplate(false)
public final class L2AttributePanelGameTests {
    private static final List<Holder<Attribute>> RANGES = List.of(Attributes.BLOCK_INTERACTION_RANGE,
            Attributes.ENTITY_INTERACTION_RANGE, L2AttributePanel.MELEE_REACH);

    private L2AttributePanelGameTests() {}

    @GameTest(templateNamespace = "mite", template = "empty")
    public static void survivalRowsSeparateBlockInteractionEntityInteractionAndCurrentLevelMelee(GameTestHelper helper) {
        try (ReachSettings ignored = new ReachSettings()) {
            Player player = player(helper);
            ItemStack[] weapons = {ItemStack.EMPTY, new ItemStack(Items.IRON_SWORD), new ItemStack(MiteTools.get("iron_war_hammer"))};
            double[] bases = {1.5D, 2.25D, 2D};
            int[] levels = {0, 9, 10, 99, 100, 9};
            double[] bonuses = {0D, 0D, .1D, .9D, 1D, 0D};
            for (int weapon = 0; weapon < weapons.length; weapon++) {
                player.setItemInHand(InteractionHand.MAIN_HAND, weapons[weapon]);
                for (int index = 0; index < levels.length; index++) {
                    player.experienceLevel = levels[index];
                    assertRows(helper, player, 2.75D, 2.5D, bases[weapon] + bonuses[index]);
                    assertNumericLine(helper, L2AttributePanel.details(player, L2AttributePanel.MELEE_REACH, true),
                            "tooltip.mite.l2_range.level_bonus", bonuses[index]);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite", template = "empty")
    public static void independentAttributesApplyOnceAndLegacyToolReachDoesNotDoubleTheDistance(GameTestHelper helper) {
        try (ReachSettings ignored = new ReachSettings()) {
            Player player = player(helper);
            player.experienceLevel = 42;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "l2_panel_block_fixture"), .5D,
                    AttributeModifier.Operation.ADD_VALUE));
            player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "l2_panel_entity_fixture"), .75D,
                    AttributeModifier.Operation.ADD_VALUE));
            assertRows(helper, player, 3.25D, 3.25D, 3.4D);
            for (Holder<Attribute> attribute : List.of(Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE)) {
                player.getAttribute(attribute).addTransientModifier(new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath("mite", "tool_reach"), .5D,
                        AttributeModifier.Operation.ADD_VALUE));
            }
            assertRows(helper, player, 3.25D, 3.25D, 3.4D);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite", template = "empty")
    public static void creativeRowsFollowTheRealCreativeRulesAndKeepTheLevelMeleeBonus(GameTestHelper helper) {
        try (ReachSettings ignored = new ReachSettings()) {
            Player player = player(helper);
            player.getAbilities().instabuild = true;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "l2_panel_creative_block_fixture"), 2D,
                    AttributeModifier.Operation.ADD_VALUE));
            player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "l2_panel_creative_entity_fixture"), 2D,
                    AttributeModifier.Operation.ADD_VALUE));
            int[] levels = {0, 42, 100};
            double[] bonuses = {0D, .4D, 1D};
            for (int index = 0; index < levels.length; index++) {
                player.experienceLevel = levels[index];
                assertRows(helper, player, 5D, 5D, 5D + bonuses[index]);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite", template = "empty")
    public static void unrelatedAttributesAndOtherLivingEntitiesRetainTheirNativeDisplayPath(GameTestHelper helper) {
        Player player = player(helper);
        for (Holder<Attribute> attribute : List.of(Attributes.MAX_HEALTH, Attributes.ARMOR,
                Attributes.ARMOR_TOUGHNESS, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED, Attributes.MOVEMENT_SPEED)) {
            helper.assertFalse(L2AttributePanel.handles(player, attribute), "Ordinary attributes retain the panel's native path");
            helper.assertTrue(L2AttributePanel.value(player, attribute) == null
                    && L2AttributePanel.details(player, attribute, true).isEmpty(), "Ordinary attributes receive no range projection");
        }
        var cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "The ordinary nonplayer fixture is available");
        for (Holder<Attribute> attribute : RANGES) {
            helper.assertFalse(L2AttributePanel.handles(cow, attribute), "Nonplayer entities retain their native attributes");
            helper.assertTrue(L2AttributePanel.value(cow, attribute) == null
                    && L2AttributePanel.details(cow, attribute, true).isEmpty(), "Nonplayer tooltips receive no player range projection");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite", template = "empty")
    public static void optionalSimplySwordsNewAndSavedGreathammersUseTheirActualFamilyLength(GameTestHelper helper) {
        if (!SimplySwordsCompat.enabled()) { helper.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings()) {
            Player player = player(helper);
            ItemStack hammer = new ItemStack(SimplySwordsCompat.weapon("iron", "greathammer"));
            for (boolean saved : new boolean[]{false, true}) {
                ItemStack weapon = hammer.copy();
                if (saved) {
                    weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, weapon.getAttributeModifiers());
                    weapon = ItemStack.parse(helper.getLevel().registryAccess(), weapon.save(helper.getLevel().registryAccess())).orElseThrow();
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
                int[] levels = {0, 42, 100};
                double[] bonuses = {0D, .4D, 1D};
                for (int index = 0; index < levels.length; index++) {
                    player.experienceLevel = levels[index];
                    assertRows(helper, player, 2.75D, 2.5D, 3.25D + bonuses[index]);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mite", template = "empty")
    public static void optionalBetterCombatMoveLimitsReachBothTheValueAndItsExpandedDetails(GameTestHelper helper) {
        if (!BetterCombatBridge.enabled()) { helper.succeed(); return; }
        try (ReachSettings ignored = new ReachSettings()) {
            Player player = player(helper);
            player.experienceLevel = 100;
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            BetterCombatPlayerReach.duringAttack(player, sword, .5D, () -> {
                Double limit = BetterCombatPlayerReach.reach(player);
                helper.assertTrue(limit != null, "A real optional attack scope exposes its move limit");
                double expected = Math.min(limit, SourcePlayerReach.sourceMeleeReach(player, player));
                assertRows(helper, player, 2.75D, 2.5D, expected);
                assertNumericLine(helper, L2AttributePanel.details(player, L2AttributePanel.MELEE_REACH, true),
                        "tooltip.mite.l2_range.attack_limit", limit);
            });
        }
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(4.5D);
        player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(3D);
        player.setNoGravity(true);
        player.setOnGround(true);
        return player;
    }

    private static void assertRows(GameTestHelper helper, Player player, double block, double interaction, double melee) {
        var attributesBefore = player.getAttributes().save();
        var weaponBefore = player.getMainHandItem().copy();
        double[] expected = {block, interaction, melee};
        for (int index = 0; index < RANGES.size(); index++) {
            Holder<Attribute> attribute = RANGES.get(index);
            helper.assertTrue(L2AttributePanel.handles(player, attribute), "The panel handles each effective player range");
            Double actual = L2AttributePanel.value(player, attribute);
            helper.assertTrue(actual != null && Math.abs(actual - expected[index]) < .00001D,
                    "Panel distance agrees with the independent expected range for " + attribute.value().getDescriptionId());
            for (boolean shift : new boolean[]{false, true}) {
                List<Component> details = L2AttributePanel.details(player, attribute, shift);
                assertNumericLine(helper, details, "tooltip.mite.l2_range.value", expected[index]);
                long hints = details.stream().filter(line -> translated(line, "tooltip.mite.l2_range.shift")).count();
                helper.assertValueEqual(hints, shift ? 0L : 1L, "Shift reveals range details and consumes the expansion hint");
            }
        }
        helper.assertValueEqual(player.getAttributes().save(), attributesBefore, "Rendering panel values and details preserves every native attribute");
        helper.assertTrue(ItemStack.matches(player.getMainHandItem(), weaponBefore), "Rendering panel values and details preserves the held item");
        helper.assertTrue(player.getAttribute(L2AttributePanel.MELEE_REACH) == null,
                "The extra melee row remains a display-only holder outside the player's attributes");
    }

    private static void assertNumericLine(GameTestHelper helper, List<Component> lines, String key, double expected) {
        List<Component> matches = lines.stream().filter(line -> translated(line, key)).toList();
        helper.assertValueEqual(matches.size(), 1, "The tooltip contains one numeric line for " + key);
        var text = (TranslatableContents)matches.getFirst().getContents();
        helper.assertTrue(text.getArgs().length == 1
                && ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(expected).equals(text.getArgs()[0]),
                "Tooltip values use the same formatted effective range as the panel row");
    }

    private static boolean translated(Component line, String key) {
        return line.getContents() instanceof TranslatableContents text && text.getKey().equals(key);
    }

    private static final class ReachSettings implements AutoCloseable {
        private final double multiplier = MiteConfig.WEAPON_REACH_MULTIPLIER.get();
        private final double reduction = MiteConfig.WEAPON_REACH_REDUCTION.get();

        private ReachSettings() {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(1D);
            MiteConfig.WEAPON_REACH_REDUCTION.set(0D);
            MiteConfig.refreshEnchantmentRules();
        }

        @Override public void close() {
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(multiplier);
            MiteConfig.WEAPON_REACH_REDUCTION.set(reduction);
            MiteConfig.refreshEnchantmentRules();
        }
    }
}
