package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual player hits guard against the old eight-hit copper scythe lifetime. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ScytheDurabilityGameTests {
    private ScytheDurabilityGameTests() {}

    @GameTest(template = "empty")
    public static void nativeScythesSpendFiftyPointsPerRealHitAndKeepTheirMaterialMaximum(GameTestHelper h) {
        Player player = player(h);
        for (String material : List.of("copper", "iron")) {
            ItemStack stack = new ItemStack(MiteTools.get(material + "_scythe"));
            h.assertValueEqual(stack.getMaxDamage(), material.equals("copper") ? 3200 : 6400,
                    "The correction preserves the existing material maximum: " + material);
            hit(h, player, stack, 16);
            h.assertFalse(stack.isEmpty(), "Sixteen successful attacks cannot destroy a fresh scythe: " + material);
            h.assertValueEqual(stack.getDamageValue(), 800, "Each real hit spends fifty points exactly once: " + material);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void damageEnchantmentsDoNotGateTheCorrectedWearAndRejectedHitsSpendNothing(GameTestHelper h) {
        Player player = player(h);
        var enchantments = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var enchantment : List.of(Enchantments.SHARPNESS,
                net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mite", "vampiric")))) {
            ItemStack stack = new ItemStack(MiteTools.get("copper_scythe"));
            stack.enchant(enchantments.getHolderOrThrow(enchantment), 1);
            hit(h, player, stack, 1);
            h.assertValueEqual(stack.getDamageValue(), 50, "Damage enchantments retain the same ordinary hit cost");
        }
        ItemStack stack = new ItemStack(MiteTools.get("copper_scythe"));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        var target = EntityType.COW.create(h.getLevel());
        try {
            target.moveTo(player.position().add(1D, 0D, 0D));
            target.setInvulnerable(true);
            h.assertTrue(io.github.ningmeng123.mite.combat.SourcePlayerReach.canMelee(player, target),
                    "The invulnerable target is within reach, so range rejection cannot mask this path");
            float health = target.getHealth();
            player.attack(target);
            h.assertValueEqual(target.getHealth(), health, "The reachable target rejects the actual attack");
            h.assertValueEqual(stack.getDamageValue(), 0, "An invulnerable target cannot consume scythe durability");
        } finally { target.discard(); }
        hit(h, player(h, GameType.CREATIVE), stack, 1);
        h.assertValueEqual(stack.getDamageValue(), 0, "Creative attacks still spend no durability");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void simplySwordsCombatScythesUseTheSameCorrectedRealHitBudget(GameTestHelper h) {
        if (SimplySwordsCompat.enabled()) {
            Player player = player(h);
            ItemStack stack = new ItemStack(SimplySwordsCompat.weapon("copper", "scythe"));
            h.assertFalse(stack.isEmpty(), "The optional test uses the registered copper combat scythe");
            hit(h, player, stack, 16);
            h.assertFalse(stack.isEmpty(), "A combat scythe cannot inherit the old farming-tool hit penalty");
            h.assertValueEqual(stack.getDamageValue(), 800, "Original Simply Swords callbacks apply one fifty-point cost per hit");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void grassAndMatureWheatRetainTheirTwoPointHarvestCost(GameTestHelper h) {
        Player player = player(h);
        ItemStack stack = new ItemStack(MiteTools.get("copper_scythe"));
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        stack.mineBlock(h.getLevel(), Blocks.SHORT_GRASS.defaultBlockState(), pos, player);
        h.assertValueEqual(stack.getDamageValue(), 2, "The combat correction preserves grass wear");
        stack.mineBlock(h.getLevel(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), pos, player);
        h.assertValueEqual(stack.getDamageValue(), 4, "Mature wheat still spends two additional points");
        h.succeed();
    }

    private static Player player(GameTestHelper h) {
        return player(h, GameType.SURVIVAL);
    }

    private static Player player(GameTestHelper h, GameType mode) {
        Player player = h.makeMockPlayer(mode);
        player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 15, 1))));
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10D);
        player.setHealth(100F);
        player.setOnGround(true);
        player.setNoGravity(true);
        player.getFoodData().setFoodLevel(6);
        return player;
    }

    private static void hit(GameTestHelper h, Player player, ItemStack stack, int count) {
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        for (int index = 0; index < count; index++) {
            var target = EntityType.COW.create(h.getLevel());
            try {
                target.moveTo(player.position().add(1D, 0D, 0D));
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
                target.setHealth(200F);
                target.setNoAi(true);
                player.attack(target);
                h.assertTrue(target.getHealth() < 200F, "The regression executes an actual successful player attack");
            } finally { target.discard(); }
        }
    }
}
