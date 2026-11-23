package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real equipment modifiers, cooldowns and table eligibility for the attack speed enchantment. @author NingMeng123 */
@GameTestHolder("mite_enchanting_chance")
@PrefixGameTestTemplate(false)
public final class AttackSpeedEnchantmentGameTests {
    private AttackSpeedEnchantmentGameTests() {}

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void attackSpeedScalesTheEquippedWeaponAndRecoversWhenSwapped(GameTestHelper helper) {
        var enchantment = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(R196Enchantments.key("attack_speed"));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setPos(helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D)));
        for (Item weapon : weapons()) {
            ItemStack ordinary = new ItemStack(weapon);
            player.setItemSlot(EquipmentSlot.MAINHAND, ordinary);
            player.tick();
            double baseline = player.getAttributeValue(Attributes.ATTACK_SPEED);
            double baselineDelay = player.getCurrentItemAttackStrengthDelay();
            for (int rank = 1; rank <= 3; rank++) {
                ItemStack enchanted = ordinary.copy();
                enchanted.enchant(enchantment, rank);
                player.setItemSlot(EquipmentSlot.MAINHAND, enchanted);
                player.tick();
                double multiplier = 1D + .1D * rank;
                helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - baseline * multiplier) < 1E-6D,
                        "Attack speed rank " + rank + " multiplies actual main-hand speed for " + weapon);
                helper.assertTrue(Math.abs(player.getCurrentItemAttackStrengthDelay() - baselineDelay / multiplier) < 1E-5D,
                        "Attack speed rank " + rank + " shortens actual attack cooldown for " + weapon);
            }

            player.setItemSlot(EquipmentSlot.OFFHAND, player.getMainHandItem());
            player.setItemSlot(EquipmentSlot.MAINHAND, ordinary);
            player.tick();
            helper.assertValueEqual(player.getAttributeValue(Attributes.ATTACK_SPEED), baseline,
                    "Moving the enchanted weapon to the offhand restores ordinary main-hand speed for " + weapon);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite")
    public static void attackSpeedEntersTableSelectionForMeleeWeapons(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var enchantment = registry.getHolderOrThrow(R196Enchantments.key("attack_speed"));
        var candidates = registry.getTag(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow().stream()
                .filter(enchantment::equals).toList();
        helper.assertValueEqual(enchantment.value().definition().maxLevel(), 3, "Default maximum rank");
        for (Item weapon : weapons()) {
            ItemStack stack = new ItemStack(weapon);
            helper.assertTrue(stack.supportsEnchantment(enchantment), "The weapon accepts the enchantment: " + weapon);
            var results = VanillaTableEnchanting.select(stack, 30, RandomSource.create(196L), candidates, 100);
            helper.assertTrue(results.size() == 1 && results.getFirst().level == 3,
                    "The registered table candidate can reach rank III for " + weapon);
        }
        ItemStack bow = new ItemStack(Items.BOW);
        helper.assertFalse(bow.supportsEnchantment(enchantment), "The bow does not accept the melee enchantment");
        helper.assertTrue(VanillaTableEnchanting.select(bow, 30, RandomSource.create(196L), candidates, 100).isEmpty(),
                "The table does not offer the melee enchantment for a bow");
        helper.succeed();
    }

    private static List<Item> weapons() {
        return List.of(Items.IRON_SWORD, Items.IRON_AXE, Items.WOODEN_SWORD,
                MiteTools.get("iron_dagger"), MiteTools.get("iron_war_hammer"));
    }
}
