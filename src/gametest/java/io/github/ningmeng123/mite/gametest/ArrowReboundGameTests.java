package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real returning-arrow traces must leave the shooter's head and retain native pickup. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ArrowReboundGameTests {
    private ArrowReboundGameTests() {}

    private static Player target(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 11, 1), new BlockPos(10, 17, 10)))
            helper.setBlock(pos, Blocks.AIR);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 10, 1), new BlockPos(10, 10, 10)))
            helper.setBlock(pos, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos origin = helper.absolutePos(new BlockPos(5, 11, 5));
        player.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D);
        player.setNoGravity(true);
        player.setOnGround(true);
        player.getInventory().clearContent();
        helper.assertTrue(helper.getLevel().addFreshEntity(player), "The survival shooter enters the actual entity manager");
        return player;
    }

    private static AbstractArrow returning(GameTestHelper helper, Player player, ItemStack ammo, double speed) {
        AbstractArrow arrow = ((ArrowItem)ammo.getItem()).createArrow(helper.getLevel(), ammo, player, null);
        arrow.setCritArrow(false);
        arrow.setNoGravity(true);
        arrow.setPos(player.getX(), player.getBoundingBox().maxY + 3D, player.getZ());
        arrow.setDeltaMovement(0D, .1D, 0D);
        arrow.tick();
        CompoundTag escaped = new CompoundTag();
        arrow.saveWithoutId(escaped);
        helper.assertTrue(escaped.getBoolean("LeftOwner"), "A returning projectile has naturally left its owner before falling back");
        arrow.setPos(player.getX(), player.getBoundingBox().maxY + .35D, player.getZ());
        arrow.setDeltaMovement(0D, -speed, 0D);
        arrow.setNoGravity(false);
        return arrow;
    }

    private static boolean embedded(AbstractArrow arrow) {
        CompoundTag saved = new CompoundTag();
        arrow.saveWithoutId(saved);
        return saved.getBoolean("inGround");
    }

    private static void landAndPickup(GameTestHelper helper, Player player, AbstractArrow arrow, ItemStack expected) {
        for (int tick = 0; tick < 100 && !arrow.isRemoved() && !embedded(arrow); tick++) arrow.tick();
        helper.assertTrue(!arrow.isRemoved() && embedded(arrow), "The arrow reaches the solid floor instead of hovering on its shooter");
        for (int tick = 0; tick < 7; tick++) arrow.tick();
        arrow.playerTouch(player);
        helper.assertTrue(arrow.isRemoved(), "Native playerTouch removes the landed projectile after pickup");
        int count = player.getInventory().items.stream()
                .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected)).mapToInt(ItemStack::getCount).sum();
        helper.assertValueEqual(count, 1, "Native pickup returns exactly one arrow with its original item and components");
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void everyMaterialFallsThroughItsShootersHeadAndKeepsNamedAmmunitionOnPickup(GameTestHelper helper) {
        Player player = target(helper);
        helper.runAfterDelay(1, () -> {
            try {
                for (Material material : Material.values()) {
                    player.getInventory().clearContent();
                    ItemStack ammo = new ItemStack(MiteRangedItems.arrow(material));
                    ammo.set(DataComponents.CUSTOM_NAME, Component.literal("returning-" + material.id));
                    AbstractArrow arrow = returning(helper, player, ammo, .4D);
                    try {
                        float health = player.getHealth();
                        double initialY = arrow.getY();
                        arrow.tick();
                        helper.assertTrue(arrow.getY() < initialY - .39D && arrow.getDeltaMovement().y < 0D,
                                "A slow " + material.id + " arrow crosses the head without reversing upward");
                        helper.assertValueEqual(player.getHealth(), health, "A slow material arrow does not damage its shooter");
                        landAndPickup(helper, player, arrow, ammo);
                        helper.assertValueEqual(player.getHealth(), health, "Falling through the body retains the low-speed damage rule");
                    } finally { arrow.discard(); }
                }
            } finally { player.discard(); }
            helper.succeed();
        });
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void exactlyOneBlockPerTickStillHurtsTheReturningShootersHead(GameTestHelper helper) {
        Player player = target(helper);
        helper.runAfterDelay(1, () -> {
            AbstractArrow arrow = returning(helper, player, new ItemStack(Items.ARROW), 1D);
            try {
                float health = player.getHealth();
                arrow.tick();
                helper.assertTrue(player.getHealth() < health && arrow.isRemoved(),
                        "The inclusive one-block-per-tick threshold retains a real damaging impact and consumes the arrow");
            } finally { arrow.discard(); player.discard(); }
            helper.succeed();
        });
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void slowPiercingArrowPassesThroughTheShooterWithoutRepeatingTheCollisionLoop(GameTestHelper helper) {
        Player player = target(helper);
        helper.runAfterDelay(1, () -> {
            ItemStack ammo = new ItemStack(MiteRangedItems.arrow(Material.IRON));
            AbstractArrow arrow = returning(helper, player, ammo, .4D);
            CompoundTag saved = new CompoundTag();
            arrow.saveWithoutId(saved);
            saved.putByte("PierceLevel", (byte)2);
            arrow.load(saved);
            try {
                float health = player.getHealth();
                arrow.tick();
                helper.assertTrue(!arrow.isRemoved() && arrow.getDeltaMovement().y < 0D && arrow.getPierceLevel() == 2,
                        "The real piercing tick completes while the low-speed entity candidate is excluded");
                helper.assertValueEqual(player.getHealth(), health, "A slow piercing arrow cannot hurt its owner");
                landAndPickup(helper, player, arrow, ammo);
            } finally { arrow.discard(); player.discard(); }
            helper.succeed();
        });
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void rejectedFastArrowsBounceOnceThenFallPastTheShooterAndRemainRecoverable(GameTestHelper helper) {
        Player player = target(helper);
        helper.runAfterDelay(1, () -> {
            try {
                for (boolean invulnerable : List.of(false, true)) {
                    player.getInventory().clearContent();
                    player.setInvulnerable(false);
                    player.invulnerableTime = 0;
                    player.setHealth(player.getMaxHealth());
                    if (invulnerable) player.setInvulnerable(true);
                    else helper.assertTrue(player.hurt(helper.getLevel().damageSources().generic(), 10F),
                            "An actual prior hit establishes the damage cooldown");
                    ItemStack ammo = new ItemStack(Items.ARROW);
                    AbstractArrow arrow = returning(helper, player, ammo, 2D);
                    try {
                        float health = player.getHealth();
                        arrow.tick();
                        helper.assertTrue(!arrow.isRemoved() && arrow.getDeltaMovement().y > 0D,
                                "A rejected fast impact retains its first native rebound");
                        helper.assertValueEqual(player.getHealth(), health, "The fast arrow is rejected by the active protection");
                        landAndPickup(helper, player, arrow, ammo);
                        helper.assertValueEqual(player.getHealth(), health, "Its slower return cannot repeatedly hit the protected shooter");
                    } finally { arrow.discard(); }
                }
            } finally { player.setInvulnerable(false); player.discard(); }
            helper.succeed();
        });
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void modernPotionAndSpectralArrowsKeepTheirNativeLowSpeedRebound(GameTestHelper helper) {
        Player player = target(helper);
        helper.runAfterDelay(1, () -> {
            player.setInvulnerable(true);
            ItemStack tipped = new ItemStack(Items.TIPPED_ARROW);
            tipped.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
            try {
                for (ItemStack ammo : List.of(tipped, new ItemStack(Items.SPECTRAL_ARROW))) {
                    AbstractArrow arrow = returning(helper, player, ammo, .4D);
                    try {
                        double initialY = arrow.getY();
                        arrow.tick();
                        helper.assertTrue(!arrow.isRemoved() && arrow.getY() > initialY,
                                "Unmanaged modern ammunition retains native low-speed entity collision: " + ammo.getItem());
                        helper.assertTrue(ItemStack.isSameItemSameComponents(arrow.getPickupItemStackOrigin(), ammo),
                                "Native rebound preserves the modern ammunition and potion payload");
                    } finally { arrow.discard(); }
                }
            } finally { player.setInvulnerable(false); player.discard(); }
            helper.succeed();
        });
    }
}
