package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.RangedRodConversionRecipe;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.ranged.ArrowRecovery;
import io.github.ningmeng123.mite.ranged.MaterialArrowView;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native ammunition, NBT, launchers and recipe execution use source-literal expectations. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class RangedContentGameTests {
    private RangedContentGameTests() {}
    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        player.getInventory().clearContent();
        return player;
    }
    @GameTest(template = "ranged_027")
    public static void AllRegisteredArrowsAndRodsExposeExactSourceLimits(GameTestHelper helper) {
        int[] maxima = {3, 4, 8, 8, 8, 8, 16, 32, 128, 512};
        for (int index = 0; index < Material.values().length; index++) {
            Material material = Material.values()[index];
            ItemStack arrow = new ItemStack(MiteRangedItems.arrow(material));
            helper.assertTrue(arrow.getItem() instanceof ArrowItem && arrow.is(net.minecraft.tags.ItemTags.ARROWS), "Every source material is real native bow/crossbow ammunition");
            helper.assertValueEqual(arrow.getMaxStackSize(), 16, "Source ammunition stacks to sixteen");
            if (!material.hasRod()) continue;
            ItemStack rod = new ItemStack(MiteRangedItems.rod(material));
            helper.assertValueEqual(rod.getMaxDamage(), maxima[index], "Source hook durability is unscaled");
            helper.assertValueEqual(rod.getEnchantmentValue(), material.enchantability, "Material enchantability reaches the actual stack-aware API");
            helper.assertTrue(!EquipmentDurability.isManaged(rod), "Source rods have no equipment quality");
            helper.assertTrue(rod.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.FISHING_ROD_CAST), "All source rods support actual native cast ability");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void AllTenMaterialsKeepIdentityAndFractionalDamageAcrossNbtAndTrackingData(GameTestHelper helper) {
        Player player = player(helper);
        float[] expected = {1F, 1.5F, 2F, 2F, 1.5F, 1.5F, 2.5F, 2.5F, 3F, 3.5F};
        for (int index = 0; index < Material.values().length; index++) {
            Material material = Material.values()[index];
            Arrow arrow = (Arrow)((ArrowItem)MiteRangedItems.arrow(material)).createArrow(helper.getLevel(), new ItemStack(MiteRangedItems.arrow(material)), player, new ItemStack(Items.BOW));
            helper.assertValueEqual(arrow.getBaseDamage(), (double)expected[index], "Real arrow constructors use source material damage");
            helper.assertTrue(arrow.getPickupItemStackOrigin().is(MiteRangedItems.arrow(material)), "Pickup retains exact ammunition type");
            CompoundTag saved = new CompoundTag(); arrow.saveWithoutId(saved);
            Arrow loaded = new Arrow(EntityType.ARROW, helper.getLevel()); loaded.load(saved);
            helper.assertTrue(loaded.getPickupItemStackOrigin().is(MiteRangedItems.arrow(material)), "Native arrow NBT restores the exact source item");
            helper.assertValueEqual(loaded.getBaseDamage(), (double)expected[index], "Saved fractional damage survives reload");
            helper.assertTrue(((MaterialArrowView)loaded).mite$arrowMaterial().equals(material.id), "Loaded material is visible to the renderer");
            Arrow trackedPeer = new Arrow(EntityType.ARROW, helper.getLevel());
            var metadata = loaded.getEntityData().getNonDefaultValues();
            if (metadata != null) trackedPeer.getEntityData().assignValues(metadata);
            helper.assertTrue(((MaterialArrowView)trackedPeer).mite$arrowMaterial().equals(material.id), "Native tracking metadata preserves material without a custom spawn packet");
        }
        Arrow tipped = (Arrow)((ArrowItem)Items.TIPPED_ARROW).createArrow(helper.getLevel(), new ItemStack(Items.TIPPED_ARROW), player, new ItemStack(Items.BOW));
        helper.assertValueEqual(tipped.getBaseDamage(), 2D, "Modern tipped ammunition keeps its native coefficient");
        helper.assertTrue(((MaterialArrowView)tipped).mite$arrowMaterial().isEmpty(), "Modern tipped rendering remains native");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void RealArrowHitsUseFractionalSourceDamageAndSilverUndeadBonus(GameTestHelper helper) {
        Player player = player(helper);
        float[] sourceDamage = {3F, 4.5F, 6F, 6F, 4.5F, 4.5F, 7.5F, 7.5F, 9F, 10.5F};
        for (int index = 0; index < Material.values().length; index++) {
            Material material = Material.values()[index];
            Cow target = new Cow(EntityType.COW, helper.getLevel());
            Arrow arrow = (Arrow)((ArrowItem)MiteRangedItems.arrow(material)).createArrow(helper.getLevel(), new ItemStack(MiteRangedItems.arrow(material)), player, new ItemStack(Items.BOW));
            arrow.setDeltaMovement(3D, 0D, 0D); arrow.setCritArrow(false);
            float actual = hit(helper, arrow, target);
            helper.assertValueEqual(actual, sourceDamage[index], "The native damage transaction preserves source fractional damage for " + material.id);
        }
        Zombie target = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        Arrow silver = (Arrow)((ArrowItem)MiteRangedItems.arrow(Material.SILVER)).createArrow(helper.getLevel(), new ItemStack(MiteRangedItems.arrow(Material.SILVER)), player, new ItemStack(Items.BOW));
        silver.setDeltaMovement(3D, 0D, 0D);
        helper.assertValueEqual(hit(helper, silver, target), 7.5F, "Silver's source 25 percent undead bonus reaches actual damage events before armor");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void NativeCriticalArrowUsesTheSourceFloatMultiplier(GameTestHelper helper) {
        Arrow arrow = (Arrow)((ArrowItem)MiteRangedItems.arrow(Material.IRON)).createArrow(helper.getLevel(), new ItemStack(MiteRangedItems.arrow(Material.IRON)), player(helper), new ItemStack(Items.BOW));
        arrow.setDeltaMovement(3D, 0D, 0D); arrow.setCritArrow(true); arrow.getRandom().setSeed(196L);
        float expected = 7.5F * (1.5F + RandomSource.create(196L).nextFloat() * .5F);
        helper.assertValueEqual(hit(helper, arrow, new Cow(EntityType.COW, helper.getLevel())), expected, "Native critical transaction uses source 1.5..2 multiplier rather than integer additive critical damage");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void mobArrowHitsKeepGrowingAcrossBloodMoonsAndBlueMoons(GameTestHelper helper) {
        var world = helper.getLevel().getServer().overworld();
        long time = world.getDayTime();
        try {
            for (long day : new long[]{1L, 2L, 33L, 65L, 129L, 130L, 161L, 2L}) {
                world.setDayTime((day - 1L) * 24_000L + 6_000L);
                checkMonsterBowHits(helper, .1D * (day - 1L));
            }
        } finally {
            world.setDayTime(time);
        }
        helper.succeed();
    }

    private static void checkMonsterBowHits(GameTestHelper helper, double growth) {
        for (EntityType<?> type : List.of(EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.ZOMBIFIED_PIGLIN)) {
            Mob shooter = (Mob)type.create(helper.getLevel());
            helper.assertTrue(shooter != null, "The bow-wielding mob fixture exists");
            for (boolean modern : new boolean[]{false, true}) {
                ItemStack ammo = modern ? new ItemStack(Items.TIPPED_ARROW) : new ItemStack(MiteRangedItems.arrow(Material.RUSTED_IRON));
                // Hold the ammunition fixed to measure calendar damage independently of material upgrades on firing.
                Arrow arrow = new Arrow(helper.getLevel(), shooter, ammo, new ItemStack(Items.BOW));
                arrow.setDeltaMovement(3D, 0D, 0D); arrow.setCritArrow(false);
                Cow target = bowTarget(helper);
                float before = target.getHealth();
                // Skeletons get the material minimum; other mobs retain velocity damage before the half multiplier.
                float expected = modern ? 3F : type == EntityType.ZOMBIFIED_PIGLIN ? 2.25F : 2.5F;
                helper.assertValueEqual(hit(helper, arrow, target), expected, "The native hit halves mob bow damage for " + type);
                helper.assertTrue(Math.abs(before - target.getHealth() - expected - growth) < 1E-5D,
                        "The halved bow hit gains " + growth + " calendar damage for " + type);
                arrow.discard(); target.discard();
            }
            shooter.discard();
        }
        Mob shooter = EntityType.PILLAGER.create(helper.getLevel());
        ItemStack ammo = new ItemStack(MiteRangedItems.arrow(Material.RUSTED_IRON));
        Arrow arrow = (Arrow)((ArrowItem)ammo.getItem()).createArrow(helper.getLevel(), ammo, shooter, new ItemStack(Items.CROSSBOW));
        arrow.setDeltaMovement(3D, 0D, 0D); arrow.setCritArrow(false);
        Cow target = bowTarget(helper);
        float before = target.getHealth();
        helper.assertValueEqual(hit(helper, arrow, target), 4.5F, "The crossbow retains its original base hit");
        helper.assertTrue(Math.abs(before - target.getHealth() - 4.5D - growth) < 1E-5D,
                "The pillager's full crossbow hit gains " + growth + " calendar damage once");
        arrow.discard(); target.discard(); shooter.discard();
    }

    private static Cow bowTarget(GameTestHelper helper) {
        Cow target = new Cow(EntityType.COW, helper.getLevel());
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(32D);
        target.setHealth(32F);
        return target;
    }
    @GameTest(template = "ranged_027")
    public static void NativeBowAndCrossbowFireAndConsumeSourceAmmunition(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack bow = new ItemStack(Items.BOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        ItemStack ammo = new ItemStack(MiteRangedItems.arrow(Material.MITHRIL), 2);
        ammo.set(DataComponents.CUSTOM_NAME, Component.literal("027-bow-" + UUID.randomUUID()));
        player.getInventory().setItem(1, ammo);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        ((BowItem)Items.BOW).releaseUsing(bow, helper.getLevel(), player, 71980);
        player.stopUsingItem();
        helper.assertValueEqual(ammo.getCount(), 1, "The actual survival bow consumes one source arrow");
        var fired = helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(5D)).stream().filter(entity ->
                ItemStack.isSameItemSameComponents(entity.getPickupItemStackOrigin(), ammo)).toList();
        helper.assertValueEqual(fired.size(), 1, "Native bow spawns exactly one source projectile");
        helper.assertValueEqual(fired.getFirst().getBaseDamage(), 3D, "Mithril damage survives native bow spawning");
        fired.forEach(Arrow::discard);
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        ItemStack loaded = new ItemStack(MiteRangedItems.arrow(Material.ADAMANTIUM));
        loaded.set(DataComponents.CUSTOM_NAME, Component.literal("027-crossbow-" + UUID.randomUUID()));
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(loaded));
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        ((CrossbowItem)Items.CROSSBOW).performShooting(helper.getLevel(), player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 0F, null);
        var bolts = helper.getLevel().getEntitiesOfClass(Arrow.class, player.getBoundingBox().inflate(5D)).stream().filter(entity ->
                ItemStack.isSameItemSameComponents(entity.getPickupItemStackOrigin(), loaded)).toList();
        helper.assertValueEqual(bolts.size(), 1, "The actual modern crossbow fires source ammunition");
        helper.assertTrue(crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty(), "Loaded ammunition clears after native shooting");
        helper.assertValueEqual(bolts.getFirst().getBaseDamage(), 3.5D, "Adamantium damage survives modern crossbow spawning");
        bolts.forEach(Arrow::discard);
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void PoweredVanillaDispenserSpawnsCorrectMaterialProjectile(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        DispenserBlockEntity dispenser = (DispenserBlockEntity)helper.getBlockEntity(pos);
        ItemStack ammo = new ItemStack(MiteRangedItems.arrow(Material.ADAMANTIUM), 2);
        ammo.set(DataComponents.CUSTOM_NAME, Component.literal("027-dispensed-" + UUID.randomUUID()));
        dispenser.setItem(0, ammo);
        helper.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(dispenser.getItem(0).getCount(), 1, "Actual scheduled dispenser consumes exactly one arrow");
            var arrows = helper.getLevel().getEntitiesOfClass(Arrow.class, helper.getBounds().inflate(8D)).stream().filter(arrow ->
                    ItemStack.isSameItemSameComponents(arrow.getPickupItemStackOrigin(), ammo)).toList();
            helper.assertValueEqual(arrows.size(), 1, "Actual native dispenser behavior spawns a missile rather than dropping an item");
            helper.assertValueEqual(arrows.getFirst().getBaseDamage(), 3.5D, "Dispenser retains source material damage");
            helper.assertTrue(arrows.getFirst().pickup == AbstractArrow.Pickup.ALLOWED, "Dispensed projectile is recoverable");
            arrows.forEach(Arrow::discard);
            helper.succeed();
        });
    }
    @GameTest(template = "ranged_027")
    public static void SourceRecipesProduceOneArrowAndOneMatchingHeadAndKeepRustedUncraftable(GameTestHelper helper) {
        for (Material material : Material.values()) {
            if (!material.hasCraftableArrow()) continue;
            CraftingInput input = CraftingInput.of(1, 3, List.of(new ItemStack(MiteRangedItems.arrowhead(material)), new ItemStack(Items.STICK), new ItemStack(Items.FEATHER)));
            var source = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            ItemStack result = source.value().assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(MiteRangedItems.arrow(material)), "Source shape selects correct material");
            helper.assertValueEqual(result.getCount(), 1, "Source craft returns one arrow rather than vanilla four");
            CraftingInput dismantle = CraftingInput.of(1, 1, List.of(result));
            var reverse = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, dismantle, helper.getLevel()).orElseThrow();
            helper.assertTrue(reverse.value().assemble(dismantle, helper.getLevel().registryAccess()).is(MiteRangedItems.arrowhead(material)), "Source dismantle returns the matching head only");
            helper.assertValueEqual(TimedCraftingRecipes.get(reverse.id()).difficulty(), 40F, "Source reverse process has forty difficulty and no metal bench requirement");
        }
        for (var recipe : helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))
            helper.assertTrue(!recipe.value().getResultItem(helper.getLevel().registryAccess()).is(MiteRangedItems.arrow(Material.RUSTED_IRON)), "Registered source rusted arrow has no invented crafting recipe");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void RodCarrotConversionsKeepHookAndNameButResetDamageLikeSourceTagPropagation(GameTestHelper helper) {
        for (Material material : MiteRangedItems.rods()) {
            ItemStack rod = new ItemStack(MiteRangedItems.rod(material));
            rod.setDamageValue(1); rod.set(DataComponents.CUSTOM_NAME, Component.literal("source-hook"));
            var attach = new RangedRodConversionRecipe(material, true);
            ItemStack carrot = attach.assemble(CraftingInput.of(2, 1, List.of(rod, new ItemStack(Items.CARROT))), helper.getLevel().registryAccess());
            helper.assertTrue(carrot.is(MiteRangedItems.carrotRod(material)), "Hook material survives carrot attachment");
            helper.assertValueEqual(carrot.getMaxDamage(), 25, "All source carrots use twenty-five durability");
            helper.assertValueEqual(carrot.getDamageValue(), 0, "R196 propagates NBT, not old separate damage");
            helper.assertTrue(carrot.getHoverName().getString().equals("source-hook"), "Source name/NBT survives modern component conversion");
            carrot.setDamageValue(20);
            ItemStack restored = new RangedRodConversionRecipe(material, false).assemble(CraftingInput.of(1, 1, List.of(carrot)), helper.getLevel().registryAccess());
            helper.assertTrue(restored.is(MiteRangedItems.rod(material)), "Dismantling restores exact original hook material");
            helper.assertValueEqual(restored.getMaxDamage(), material.rodDurability(), "Transmutation cannot carry carrot's max-damage component to a rod");
            helper.assertValueEqual(restored.getDamageValue(), 0, "Source reverse restores a fresh rod");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void ContainedArrowsSurviveNativeLivingEntityNbt(GameTestHelper helper) {
        Cow cow = new Cow(EntityType.COW, helper.getLevel());
        ArrowRecovery.contain(cow, new ItemStack(MiteRangedItems.arrow(Material.SILVER)));
        ArrowRecovery.contain(cow, new ItemStack(MiteRangedItems.arrow(Material.ADAMANTIUM)));
        CompoundTag saved = new CompoundTag(); cow.saveWithoutId(saved);
        Cow loaded = new Cow(EntityType.COW, helper.getLevel()); loaded.load(saved);
        helper.assertTrue(loaded.getData(ArrowRecovery.CONTAINED).equals(List.of("mite:silver_arrow", "mite:adamantium_arrow")), "Actual serialized entity attachments preserve contained source arrows across saves");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void ActualMaterialRecoveryRollsMatchSourceAndNeverRecoverCreativeOrPiercingCopies(GameTestHelper helper) {
        Player player = player(helper);
        for (Material material : Material.values()) for (int sample = 0; sample < 64; sample++) {
            long seed = sample * 104729L;
            Cow target = new Cow(EntityType.COW, helper.getLevel());
            Arrow arrow = (Arrow)((ArrowItem)MiteRangedItems.arrow(material)).createArrow(helper.getLevel(), new ItemStack(MiteRangedItems.arrow(material)), player, new ItemStack(Items.BOW));
            arrow.setDeltaMovement(2D, 0D, 0D); arrow.getRandom().setSeed(seed);
            boolean expected = RandomSource.create(seed).nextFloat() < material.recovery;
            hit(helper, arrow, target);
            helper.assertValueEqual(target.getData(ArrowRecovery.CONTAINED).size(), expected ? 1 : 0,
                    "Real native post-hit source recovery matches the exact material roll: " + material.id + " seed " + seed);
            if (expected) helper.assertTrue(target.getData(ArrowRecovery.CONTAINED).getFirst().equals(MiteRangedItems.arrowId(material).toString()), "Contained loot preserves source arrow material");
        }
        for (boolean pierced : new boolean[]{false, true}) {
            Cow target = new Cow(EntityType.COW, helper.getLevel());
            Arrow arrow = (Arrow)((ArrowItem)MiteRangedItems.arrow(Material.ADAMANTIUM)).createArrow(helper.getLevel(), new ItemStack(MiteRangedItems.arrow(Material.ADAMANTIUM)), player, new ItemStack(Items.CROSSBOW));
            arrow.setDeltaMovement(2D, 0D, 0D);
            if (pierced) {
                try { Method method = AbstractArrow.class.getDeclaredMethod("setPierceLevel", byte.class); method.setAccessible(true); method.invoke(arrow, (byte)1); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException("Native piercing fixture setup failed", error); }
            } else arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            hit(helper, arrow, target);
            helper.assertTrue(target.getData(ArrowRecovery.CONTAINED).isEmpty(), "Piercing continuations and creative/infinity copies cannot create an additional source loot arrow");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void WallPickupReturnsExactAmmunitionOnceAndContainedDeathDropsClearOnce(GameTestHelper helper) {
        Player player = player(helper);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
        BlockPos wall = helper.absolutePos(new BlockPos(4, 2, 2));
        for (Material material : Material.values()) {
            player.getInventory().clearContent();
            Arrow arrow = (Arrow)((ArrowItem)MiteRangedItems.arrow(material)).createArrow(helper.getLevel(), new ItemStack(MiteRangedItems.arrow(material)), player, new ItemStack(Items.BOW));
            arrow.setPos(Vec3.atCenterOf(wall));
            try {
                Method impact = AbstractArrow.class.getDeclaredMethod("onHitBlock", BlockHitResult.class); impact.setAccessible(true);
                impact.invoke(arrow, new BlockHitResult(Vec3.atCenterOf(wall), Direction.WEST, wall, false));
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native arrow-wall impact failed", error); }
            for (int tick = 0; tick < 7; tick++) arrow.tick();
            arrow.playerTouch(player);
            helper.assertValueEqual(player.getInventory().countItem(MiteRangedItems.arrow(material)), 1, "Native wall pickup returns exactly the source material without a breakage roll");
            helper.assertTrue(arrow.isRemoved(), "Successful native pickup removes the projectile");
        }
        Cow cow = new Cow(EntityType.COW, helper.getLevel());
        ArrowRecovery.contain(cow, new ItemStack(MiteRangedItems.arrow(Material.SILVER)));
        List<ItemEntity> drops = new ArrayList<>();
        NeoForge.EVENT_BUS.post(new LivingDropsEvent(cow, helper.getLevel().damageSources().generic(), drops, true));
        helper.assertValueEqual(drops.size(), 1, "A real drops event includes exactly the contained source arrow");
        helper.assertTrue(drops.getFirst().getItem().is(MiteRangedItems.arrow(Material.SILVER)), "Contained death drop keeps correct material");
        NeoForge.EVENT_BUS.post(new LivingDropsEvent(cow, helper.getLevel().damageSources().generic(), drops, true));
        helper.assertValueEqual(drops.size(), 1, "Consumed contained-arrow state cannot create duplicate drops on another death callback");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void AllCarrotHookMaterialsSteerPigsAndReturnTheCorrectRodWhenEaten(GameTestHelper helper) {
        for (Material material : MiteRangedItems.rods()) {
            Player player = player(helper);
            Pig pig = new Pig(EntityType.PIG, helper.getLevel());
            pig.equipSaddle(new ItemStack(Items.SADDLE), SoundSource.NEUTRAL);
            player.startRiding(pig, true);
            ItemStack carrot = new ItemStack(MiteRangedItems.carrotRod(material));
            carrot.set(DataComponents.CUSTOM_NAME, Component.literal("old-hook"));
            player.setItemInHand(InteractionHand.MAIN_HAND, carrot);
            helper.assertTrue(pig.getControllingPassenger() == player, "Source hook variants steer native saddled pigs: " + material.id);
            carrot.setDamageValue(19);
            var refused = carrot.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(refused.getObject() == carrot && carrot.getDamageValue() == 19, "Source boost requires seven remaining carrot durability");
            carrot.setDamageValue(18);
            var boosted = carrot.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(boosted.getObject().is(MiteRangedItems.rod(material)), "A pig nibble consuming the carrot returns the original hook material");
            helper.assertValueEqual(boosted.getObject().getMaxDamage(), material.rodDurability(), "Returned source fishing rod has its own material durability");
            helper.assertValueEqual(boosted.getObject().getDamageValue(), 0, "Returned source hook is fresh");
            helper.assertTrue(boosted.getObject().getHoverName().getString().equals("old-hook"), "Consumed carrot preserves source name/component data");
            player.stopRiding();
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void FatalArrowRecoveryUsesTheRealDropsEventAndRespectsCancellation(GameTestHelper helper) {
        Player player = player(helper);
        for (boolean canceled : new boolean[]{false, true}) {
            Cow target = new Cow(EntityType.COW, helper.getLevel());
            target.moveTo(helper.absolutePos(new BlockPos(4, 3, 4)).getCenter()); target.setHealth(1F);
            helper.getLevel().addFreshEntity(target);
            Arrow arrow = (Arrow)((ArrowItem)MiteRangedItems.arrow(Material.ADAMANTIUM)).createArrow(helper.getLevel(), new ItemStack(MiteRangedItems.arrow(Material.ADAMANTIUM)), player, new ItemStack(Items.BOW));
            arrow.setDeltaMovement(2D, 0D, 0D); arrow.getRandom().setSeed(196L);
            int[] recoveredInEvent = {0};
            Consumer<LivingDropsEvent> callback = event -> {
                if (event.getEntity() != target) return;
                recoveredInEvent[0] = event.getDrops().stream().filter(drop -> drop.getItem().is(MiteRangedItems.arrow(Material.ADAMANTIUM))).mapToInt(drop -> drop.getItem().getCount()).sum();
                if (canceled) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, false, LivingDropsEvent.class, callback);
            try { hit(helper, arrow, target); }
            finally { NeoForge.EVENT_BUS.unregister(callback); }
            helper.assertValueEqual(recoveredInEvent[0], 1, "The fatal recovered missile is part of the actual cancellable death-drops collection");
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(3D));
            long arrows = drops.stream().filter(drop -> drop.getItem().is(MiteRangedItems.arrow(Material.ADAMANTIUM))).mapToInt(drop -> drop.getItem().getCount()).sum();
            helper.assertValueEqual(arrows, canceled ? 0L : 1L, "A canceled fatal drop event cannot be bypassed by a late direct source-arrow spawn");
            helper.assertTrue(!target.hasData(ArrowRecovery.CONTAINED), "Fatal recovery cannot remain queued for duplicate drop callbacks");
            drops.forEach(ItemEntity::discard); target.discard();
        }
        helper.succeed();
    }
    private static float hit(GameTestHelper helper, Arrow arrow, LivingEntity target) {
        float[] amount = {-1F};
        Consumer<LivingIncomingDamageEvent> capture = event -> { if (event.getEntity() == target && event.getSource().getDirectEntity() == arrow) amount[0] = event.getOriginalAmount(); };
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, capture);
        try {
            Method method = AbstractArrow.class.getDeclaredMethod("onHitEntity", EntityHitResult.class); method.setAccessible(true); method.invoke(arrow, new EntityHitResult(target));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native source arrow hit failed", error); }
        finally { NeoForge.EVENT_BUS.unregister(capture); }
        helper.assertTrue(amount[0] >= 0F, "The test reached a real native damage transaction");
        return amount[0];
    }
}
