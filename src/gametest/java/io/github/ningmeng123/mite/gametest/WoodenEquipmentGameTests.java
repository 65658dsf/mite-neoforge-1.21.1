package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteTools;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Normal acquisition, retained-ID club behavior and ordinary sword controls. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WoodenEquipmentGameTests {
    @GameTest(template = "empty")
    public static void clubRecipeLoadsAndRetiredRecipesAndAdvancementsDoNot(GameTestHelper helper) {
        List<ItemStack> clubGrid = emptyGrid();
        clubGrid.set(1, new ItemStack(Items.OAK_PLANKS));
        clubGrid.set(4, new ItemStack(Items.OAK_PLANKS));
        clubGrid.set(7, new ItemStack(Items.STICK));
        CraftingInput input = CraftingInput.of(3, 3, clubGrid);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        ItemStack result = recipe.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(Items.WOODEN_SWORD) && result.getCount() == 1,
                "Two planks above one stick produce one club under the compatible wooden_sword ID");
        helper.assertValueEqual(result.getMaxDamage(), 400, "The crafted output receives the new club default components");
        for (boolean axe : new boolean[]{false, true}) {
            String id = axe ? "wooden_axe" : "wooden_pickaxe";
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(vanilla(id)).isEmpty(), "The retired recipe is absent: " + id);
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(vanilla("recipes/tools/" + id)) == null,
                    "The retired recipe advancement is also absent: " + id);
            helper.assertTrue(helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, retiredToolGrid(axe)), helper.getLevel()).isEmpty(),
                    "The complete 3x3 recipe manager cannot craft " + id);
        }
        for (String id : new String[]{"wooden_sword", "wooden_shovel", "wooden_hoe"}) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(vanilla(id)).isPresent(), "The other wooden recipe remains: " + id);
        }
        helper.succeed();
    }

    @SuppressWarnings("deprecation") // The raw API deliberately provides the identical-seed pre-GLM oracle.
    @GameTest(template = "empty")
    public static void vanillaLootFiltersRetiredToolsWithoutRerollingOtherResults(GameTestHelper helper) {
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))))
                .create(LootContextParamSets.CHEST);
        for (String path : new String[]{"chests/spawn_bonus_chest", "chests/trial_chambers/entrance"}) {
            LootTable table = helper.getLevel().getServer().reloadableRegistries()
                    .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, vanilla(path)));
            int removedStacks = 0;
            int retainedStacks = 0;
            for (long seed = 1; seed <= 64; seed++) {
                List<ItemStack> raw = new ArrayList<>();
                LootContext context = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(Optional.empty());
                table.getRandomItemsRaw(context, LootTable.createStackSplitter(helper.getLevel(), stack -> raw.add(stack.copy())));
                List<ItemStack> expected = raw.stream().filter(stack -> !isRetiredTool(stack)).toList();
                removedStacks += raw.size() - expected.size();
                retainedStacks += expected.size();
                List<ItemStack> actual = table.getRandomItems(params, seed);
                helper.assertValueEqual(actual.size(), expected.size(), "Only retired tool entries disappear: " + path + ", seed " + seed);
                for (int index = 0; index < expected.size(); index++) {
                    assertSameStack(helper, expected.get(index), actual.get(index),
                            "Other rolls retain order, count and components: " + path + ", seed " + seed + ", entry " + index);
                }
            }
            helper.assertTrue(removedStacks > 0, "The fixed seeds actually exercise a removed tool from " + path);
            helper.assertTrue(retainedStacks > 0, "The fixed seeds also exercise unaffected results from " + path);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeParentAndSearchHideOnlyTheRetiredWoodenTools(GameTestHelper helper) {
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess());
        CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            helper.assertFalse(tab.getDisplayItems().stream().anyMatch(WoodenEquipmentGameTests::isRetiredTool),
                    "No creative parent entries expose either retired tool");
            helper.assertFalse(tab.getSearchTabDisplayItems().stream().anyMatch(WoodenEquipmentGameTests::isRetiredTool),
                    "No creative search entries expose either retired tool");
        }
        for (Item retained : new Item[]{Items.WOODEN_SWORD, Items.WOODEN_SHOVEL, Items.WOODEN_HOE}) {
            helper.assertTrue(CreativeModeTabs.searchTab().getDisplayItems().stream().anyMatch(stack -> stack.is(retained)),
                    "Creative search retains the club and unaffected wooden tools: " + retained);
            helper.assertTrue(CreativeModeTabs.allTabs().stream().filter(tab -> tab != CreativeModeTabs.searchTab())
                    .anyMatch(tab -> tab.getDisplayItems().stream().anyMatch(stack -> stack.is(retained))),
                    "A normal creative parent tab also retains " + retained);
        }
        // Removal affects normal acquisition, not registry compatibility for existing or command-created stacks.
        helper.assertTrue(new ItemStack(Items.WOODEN_AXE).isDamageableItem()
                        && new ItemStack(Items.WOODEN_PICKAXE).isDamageableItem(),
                "Both retired registry entries remain usable stack types");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realClubAttacksDoubleOnlySkeletonBaseDamageAndCannotSweep(GameTestHelper helper) {
        Cow neighbor = helper.spawn(EntityType.COW, new BlockPos(1, 2, 0));
        neighbor.moveTo(neighbor.position().add(0.65, 0, 0));
        prepareTarget(neighbor);
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 0));
            try {
                prepareTarget(cow);
                chargeAttack(player);
                helper.assertValueEqual(player.getAttributeValue(Attributes.ATTACK_DAMAGE), 3.0, "The club adds 2 to the player's base attack 1");
                helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - 1.2) < 0.000001,
                        "The modern club attack cooldown is 1.2 attacks per second");
                double defaultSpeed = 4D + Items.WOODEN_SWORD.components()
                        .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                        .filter(entry -> entry.slot().test(EquipmentSlot.MAINHAND)
                                && entry.attribute().equals(Attributes.ATTACK_SPEED))
                        .mapToDouble(entry -> entry.modifier().amount()).sum();
                helper.assertTrue(Math.abs(defaultSpeed - 1.2D) < .000001D,
                        "The club's item defaults carry the same selected family cooldown");
                assertAttackDamage(helper, player, cow, 3.0F, "An actual club hit deals 3 damage to an unarmored cow");
                helper.assertValueEqual(neighbor.getHealth(), neighbor.getMaxHealth(), "A nearby valid sweep target is not hit by the club");
            } finally {
                cow.discard();
            }
            for (EntityType<? extends Mob> type : List.of(EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON,
                    EntityType.BOGGED, EntityType.ZOMBIE, EntityType.SKELETON_HORSE)) {
                Mob target = helper.spawn(type, new BlockPos(1, 2, 0));
                try {
                    prepareTarget(target);
                    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
                    assertAttackDamage(helper, player, target, type == EntityType.ZOMBIE || type == EntityType.SKELETON_HORSE ? 3.0F : 6.0F,
                            "Only humanoid skeleton base damage doubles; zombies and skeleton horses receive ordinary damage: " + type);
                    helper.assertValueEqual(neighbor.getHealth(), neighbor.getMaxHealth(), "None of the club attacks sweep the neighbor");
                    if (type == EntityType.SKELETON) {
                        ItemStack legacyEnchantedClub = new ItemStack(Items.WOODEN_SWORD);
                        legacyEnchantedClub.enchant(enchantment(helper, Enchantments.SHARPNESS), 1);
                        player.setItemSlot(EquipmentSlot.MAINHAND, legacyEnchantedClub);
                        target.setHealth(target.getMaxHealth());
                        target.invulnerableTime = 0;
                        assertAttackDamage(helper, player, target, 7.0F,
                                "A forced legacy Sharpness I adds one damage after the skeleton base multiplier, giving 7 rather than 8");
                    }
                } finally {
                    target.discard();
                }
            }
            Cow swordTarget = helper.spawn(EntityType.COW, new BlockPos(1, 2, 0));
            try {
                prepareTarget(swordTarget);
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                neighbor.invulnerableTime = 0;
                assertAttackDamage(helper, player, swordTarget, 9F, "Source iron sword damage retains the ordinary sword sweep path");
                helper.assertTrue(neighbor.getHealth() < neighbor.getMaxHealth(),
                        "The same fixture permits an ordinary sword to sweep, proving the club's negative check is meaningful");
                helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 50, "R196 sword attack wear is 100 times the source rate 0.5, applied once");
            } finally {
                swordTarget.discard();
            }
        } finally {
            neighbor.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sixteenSuccessfulAttacksWearThePersistedClubOutExactly(GameTestHelper helper) {
        Cow target = helper.spawn(EntityType.COW, new BlockPos(1, 2, 0));
        prepareTarget(target);
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            for (int hit = 1; hit <= 16; hit++) {
                target.setHealth(target.getMaxHealth());
                target.invulnerableTime = 0;
                assertAttackDamage(helper, player, target, 3.0F, "A successful fully charged attack occurs at hit " + hit);
                if (hit < 16) {
                    helper.assertValueEqual(player.getMainHandItem().getDamageValue(), hit * 25, "Each successful hit spends exactly 25 durability");
                    if (hit == 8) {
                        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(),
                                player.getMainHandItem().save(helper.getLevel().registryAccess())).orElseThrow();
                        helper.assertTrue(restored.is(Items.WOODEN_SWORD), "The persisted club retains the compatible registry ID");
                        helper.assertValueEqual(restored.getMaxDamage(), 400, "The stack codec retains club durability capacity");
                        helper.assertValueEqual(restored.getDamageValue(), 200, "The stack codec retains accumulated actual attack wear");
                        player.setItemSlot(EquipmentSlot.MAINHAND, restored);
                    }
                } else {
                    helper.assertTrue(player.getMainHandItem().isEmpty(), "The sixteenth successful hit breaks the 400-durability club");
                }
            }
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clubMinesPanesWithoutSwordWebBambooOrLogPrivileges(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState previous = helper.getLevel().getBlockState(position);
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            ItemStack club = player.getMainHandItem();
            BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
            helper.assertValueEqual(club.getDestroySpeed(pane), 2.0F, "Glass panes are explicit effective club targets");
            for (BlockState ineffective : new BlockState[]{Blocks.GLASS.defaultBlockState(), Blocks.ICE.defaultBlockState(), Blocks.COBWEB.defaultBlockState()}) {
                helper.assertValueEqual(club.getDestroySpeed(ineffective), 1.0F, "Whole glass, ice and cobweb retain baseline strength: " + ineffective);
            }
            helper.getLevel().setBlockAndUpdate(position, pane);
            helper.assertTrue(player.gameMode.destroyBlock(position), "Normal server destruction removes the pane");
            helper.assertTrue(helper.getLevel().getBlockState(position).isAir(), "The pane was actually removed");
            helper.assertValueEqual(club.getDamageValue(), 2, "R196 pane hardness 0.1 costs trunc(0.1 * 25) = 2 durability exactly once");
            helper.assertFalse(club.canPerformAction(ItemAbilities.SWORD_DIG), "The club does not inherit sword digging");
            helper.assertFalse(club.canPerformAction(ItemAbilities.SWORD_SWEEP), "The club does not inherit sword sweeping");
            helper.assertFalse(club.is(ItemTags.SWORDS), "The retained registry ID does not leave the club in the sword item tag");
            float bambooProgress = Blocks.BAMBOO.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position);
            helper.assertTrue(bambooProgress > 0 && bambooProgress < 1, "A club does not inherit the sword's instant bamboo break");
            BlockState log = Blocks.OAK_LOG.defaultBlockState();
            helper.getLevel().setBlockAndUpdate(position, log);
            helper.assertValueEqual(log.getDestroyProgress(player, helper.getLevel(), position), 0.0F, "The log axe requirement blocks club mining progress");
            helper.assertFalse(player.gameMode.destroyBlock(position), "Final server destruction also refuses a club-held log harvest");
            helper.assertTrue(helper.getLevel().getBlockState(position).is(Blocks.OAK_LOG), "The protected log remains");
            helper.assertValueEqual(club.getDamageValue(), 2, "A rejected log harvest spends no extra durability");
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(player.gameMode.destroyBlock(position), "Unlike a normal sword, a creative-held club can actually break blocks");
            helper.assertValueEqual(club.getDamageValue(), 2, "Creative mining does not spend club durability");
        } finally {
            helper.getLevel().setBlockAndUpdate(position, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clubEnchantmentRepairAndCodecRulesLeaveOrdinarySwordsIntact(GameTestHelper helper) {
        ItemStack club = new ItemStack(Items.WOODEN_SWORD);
        helper.assertValueEqual(club.getEnchantmentValue(), 10, "Wood club enchantability is ten");
        helper.assertFalse(club.getItem().isValidRepairItem(club, new ItemStack(Items.OAK_PLANKS)), "Planks cannot supply material repairs for a club");
        for (ResourceKey<Enchantment> key : List.of(Enchantments.KNOCKBACK, Enchantments.LOOTING, Enchantments.UNBREAKING,
                Enchantments.MENDING, Enchantments.VANISHING_CURSE)) {
            Holder<Enchantment> enchantment = enchantment(helper, key);
            helper.assertTrue(club.supportsEnchantment(enchantment), "The club supports its explicit enchantment: " + key.location());
            helper.assertTrue(club.isPrimaryItemFor(enchantment), "The primary-item decision accepts its explicit enchantment: " + key.location());
        }
        for (ResourceKey<Enchantment> key : List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
                Enchantments.FIRE_ASPECT, Enchantments.SWEEPING_EDGE)) {
            Holder<Enchantment> enchantment = enchantment(helper, key);
            helper.assertFalse(club.supportsEnchantment(enchantment), "The club rejects sword-only enchantments: " + key.location());
            helper.assertFalse(club.isPrimaryItemFor(enchantment), "The primary-item decision also rejects: " + key.location());
        }
        Holder<Enchantment> sharpness = enchantment(helper, Enchantments.SHARPNESS);
        club.enchant(sharpness, 2);
        club.setDamageValue(75);
        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(), club.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertValueEqual(restored.getEnchantmentLevel(sharpness), 2, "Saving does not erase a forced or legacy incompatible enchantment");
        helper.assertValueEqual(restored.getDamageValue(), 75, "The legacy enchanted stack retains its wear");
        helper.assertValueEqual(restored.getMaxDamage(), 400, "The legacy enchanted stack retains the modern club defaults");
        for (Item item : new Item[]{MiteTools.get("copper_sword"), Items.IRON_SWORD}) {
            ItemStack sword = new ItemStack(item);
            helper.assertTrue(sword.is(ItemTags.SWORDS), "Ordinary swords retain their item tag");
            helper.assertTrue(sword.canPerformAction(ItemAbilities.SWORD_DIG) && sword.canPerformAction(ItemAbilities.SWORD_SWEEP),
                    "Ordinary swords retain digging and sweeping abilities");
            helper.assertValueEqual(sword.getDestroySpeed(Blocks.COBWEB.defaultBlockState()), item == Items.IRON_SWORD ? 4.0F : 3.5F,
                    "Retained copper and iron swords preserve their source web-cutting efficiency");
            helper.assertValueEqual(sword.getMaxDamage(), item == Items.IRON_SWORD ? 6400 : 3200,
                    "Two-component swords use material factors four (copper) and eight (iron)");
            helper.assertTrue(sword.supportsEnchantment(sharpness), "Ordinary swords still support Sharpness");
        }
        helper.assertFalse(new ItemStack(Items.STONE_SWORD).is(ItemTags.SWORDS), "Retired stone swords remain excluded from active equipment tags");
        ItemStack iron = new ItemStack(Items.IRON_SWORD);
        helper.assertTrue(iron.getItem().isValidRepairItem(iron, new ItemStack(Items.IRON_INGOT)), "Normal iron sword material repairs still work");
        helper.succeed();
    }

    private static List<ItemStack> emptyGrid() {
        return new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
    }

    private static List<ItemStack> retiredToolGrid(boolean axe) {
        List<ItemStack> grid = emptyGrid();
        for (int slot : axe ? new int[]{0, 1, 3} : new int[]{0, 1, 2}) grid.set(slot, new ItemStack(Items.OAK_PLANKS));
        grid.set(4, new ItemStack(Items.STICK));
        grid.set(7, new ItemStack(Items.STICK));
        return grid;
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static boolean isRetiredTool(ItemStack stack) {
        return stack.is(Items.WOODEN_AXE) || stack.is(Items.WOODEN_PICKAXE)
                || stack.is(Items.STONE_SWORD) || stack.is(Items.STONE_AXE) || stack.is(Items.STONE_PICKAXE)
                || stack.is(Items.STONE_SHOVEL) || stack.is(Items.STONE_HOE);
    }

    private static void assertSameStack(GameTestHelper helper, ItemStack expected, ItemStack actual, String message) {
        helper.assertTrue(expected.isEmpty() ? actual.isEmpty()
                : expected.getCount() == actual.getCount() && ItemStack.isSameItemSameComponents(expected, actual), message);
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static void prepareTarget(Mob target) {
        // Combat-vector fixture must survive the largest hit; source ordinary skeletons now have only six health.
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        target.setNoAi(true);
        target.setNoGravity(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) target.setItemSlot(slot, ItemStack.EMPTY);
        if (target.getAttribute(Attributes.ARMOR) != null) target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        if (target.getAttribute(Attributes.ARMOR_TOUGHNESS) != null) target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        target.setDeltaMovement(Vec3.ZERO);
    }

    private static void chargeAttack(ServerPlayer player) {
        for (int tick = 0; tick < 21; tick++) player.doTick();
        player.setOnGround(true);
        player.setSprinting(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    private static void assertAttackDamage(GameTestHelper helper, ServerPlayer player, Mob target, float damage, String message) {
        chargeAttack(player);
        float before = target.getHealth();
        player.attack(target);
        float actual = before - target.getHealth();
        helper.assertTrue(Math.abs(actual - damage) < .00001F, message + ": " + actual + " vs " + damage);
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-wood-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
            player.setNoGravity(true);
            player.setOnGround(true);
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
            return new PlayerSession(player, channel);
        } catch (RuntimeException | Error failure) {
            ServerPlayer registered = helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID());
            if (registered != null) helper.getLevel().getServer().getPlayerList().remove(registered);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record PlayerSession(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override
        public void close() {
            try {
                ServerPlayer registered = player.server.getPlayerList().getPlayer(player.getUUID());
                if (registered != null) player.server.getPlayerList().remove(registered);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}
