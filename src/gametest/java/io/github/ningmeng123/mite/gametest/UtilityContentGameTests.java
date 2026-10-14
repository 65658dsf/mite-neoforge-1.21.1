package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.CoinCrafting;
import io.github.ningmeng123.mite.crafting.UtilityShapelessRecipe;
import io.github.ningmeng123.mite.item.R196CoinItem;
import io.github.ningmeng123.mite.item.R196RecordItem;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import io.github.ningmeng123.mite.utility.StoneBucketCooling;
import io.github.ningmeng123.mite.utility.UniqueMusicSavedData;
import io.github.ningmeng123.mite.utility.UniqueRecordExpiry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native drink, container, jukebox and dropped-item paths verify the source utility rules. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UtilityContentGameTests {
    private UtilityContentGameTests() {}
    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter()); return player;
    }
    @GameTest(template = "ranged_027")
    public static void allEighteenSourceUtilitiesKeepExactStackLimitsAndNoInventedIronCoin(GameTestHelper helper) {
        helper.assertValueEqual(MiteUtilityContent.entries().size(), 18, "The requested utility leaves register eighteen identities");
        int[] values = {5,25,100,500,2500,10000};
        for (int index = 0; index < values.length; index++) {
            ItemStack coin = new ItemStack(MiteUtilityContent.get(MiteUtilityContent.COIN_MATERIALS[index] + "_coin"));
            helper.assertValueEqual(coin.getMaxStackSize(), 64, "Coin stack limit");
            helper.assertValueEqual(((R196CoinItem)coin.getItem()).experienceValue(), values[index], "Source coin value");
            helper.assertValueEqual(CoinCrafting.cost(coin.copyWithCount(3)), values[index] * 3, "Full result quantity pays once per coin");
        }
        for (String metal : MiteUtilityContent.BUCKET_MATERIALS) helper.assertValueEqual(new ItemStack(MiteUtilityContent.get(metal + "_stone_bucket")).getMaxStackSize(), 1, "Solid bucket is unstackable");
        for (String record : MiteUtilityContent.RECORD_NAMES) helper.assertTrue(new ItemStack(MiteUtilityContent.get("music_disc_" + record)).has(DataComponents.JUKEBOX_PLAYABLE), "Records carry native jukebox components");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceBottleRecipeRequiresWaterPotionAndRejectsModernPoisonOrMissingContents(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("mite:utility_028_707")).orElseThrow();
        helper.assertTrue(recipe.value() instanceof UtilityShapelessRecipe, "Exact bottle recipe is registered");
        var crafting = (UtilityShapelessRecipe)recipe.value();
        ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        CraftingInput good = CraftingInput.of(2, 2, List.of(water, new ItemStack(Items.NETHER_WART), new ItemStack(Items.COAL), ItemStack.EMPTY));
        helper.assertTrue(crafting.matches(good, helper.getLevel()), "Source subtype0 water is accepted");
        helper.assertTrue(crafting.assemble(good, helper.getLevel().registryAccess()).is(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get()), "The real recipe produces one source bottle");
        CraftingInput poison = CraftingInput.of(2, 2, List.of(PotionContents.createItemStack(Items.POTION, Potions.POISON), new ItemStack(Items.NETHER_WART), new ItemStack(Items.COAL), ItemStack.EMPTY));
        helper.assertFalse(crafting.matches(poison, helper.getLevel()), "A potion of poison is not source water");
        CraftingInput empty = CraftingInput.of(2, 2, List.of(new ItemStack(Items.POTION), new ItemStack(Items.NETHER_WART), new ItemStack(Items.COAL), ItemStack.EMPTY));
        helper.assertFalse(crafting.matches(empty, helper.getLevel()), "A custom potion with absent contents is not source water");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void drinkingBottleCuresAllStatusEffectsWithoutTouchingEquipmentAndReturnsExactlyOneGlassBottle(GameTestHelper helper) {
        Player owner = player(helper);
        owner.addEffect(new MobEffectInstance(MobEffects.POISON, 100)); owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100));
        ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 1);
        owner.getInventory().setItem(4, sword);
        ItemStack bottle = new ItemStack(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get()); owner.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        helper.assertValueEqual(bottle.getUseDuration(owner), 32, "Source drink duration");
        helper.assertTrue(bottle.getUseAnimation() == UseAnim.DRINK, "Source bottle drinks rather than throws");
        bottle.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.isUsingItem(), "Native right click starts drinking");
        ItemStack remainder = bottle.finishUsingItem(helper.getLevel(), owner);
        helper.assertTrue(owner.getActiveEffects().isEmpty(), "Both beneficial and harmful effects are removed");
        helper.assertTrue(sword.isEnchanted(), "Drink does not invent an equipment enchantment cleanse");
        helper.assertTrue(remainder.is(Items.GLASS_BOTTLE) && remainder.getCount() == 1, "Source returns exactly one empty glass bottle");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceNauseaAndUnderwaterRestrictionsCannotBeBypassedByDirectBottleCompletion(GameTestHelper helper) {
        Player owner=player(helper);ItemStack bottle=new ItemStack(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get());owner.setItemInHand(InteractionHand.MAIN_HAND,bottle);
        owner.addEffect(new MobEffectInstance(MobEffects.CONFUSION,100));
        helper.assertTrue(bottle.use(helper.getLevel(),owner,InteractionHand.MAIN_HAND).getResult()==InteractionResult.FAIL,"Source nausea blocks drinking");
        helper.assertTrue(bottle.finishUsingItem(helper.getLevel(),owner).is(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get()),"Direct finish cannot cure a blocked nausea use");helper.assertTrue(owner.hasEffect(MobEffects.CONFUSION),"Rejected use leaves its blocker unchanged");
        owner.removeAllEffects();helper.setBlock(new BlockPos(2,3,2),Blocks.WATER);helper.setBlock(new BlockPos(2,4,2),Blocks.WATER);helper.setBlock(new BlockPos(2,5,2),Blocks.WATER);owner.baseTick();
        helper.assertTrue(owner.isEyeInFluid(net.minecraft.tags.FluidTags.WATER),"Fixture actually submerges source player eyes");
        helper.assertTrue(bottle.use(helper.getLevel(),owner,InteractionHand.MAIN_HAND).getResult()==InteractionResult.FAIL,"Source eye submersion blocks drinking");
        helper.assertTrue(bottle.finishUsingItem(helper.getLevel(),owner).is(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get()),"Underwater finish consumes no bottle");helper.assertValueEqual(owner.getInventory().countItem(Items.GLASS_BOTTLE),0,"Rejected use creates no empty bottle");helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void anvilDisenchantsCursesAndNormalEnchantmentsWithoutExperienceWearOrDuplicateBottle(GameTestHelper helper) {
        Player owner = player(helper); BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.ANVIL.defaultBlockState());
        AnvilMenu menu = new AnvilMenu(1, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE); armor.setDamageValue(7);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        armor.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 2); armor.enchant(enchantments.getOrThrow(Enchantments.BINDING_CURSE), 1);
        menu.getSlot(0).set(armor); menu.getSlot(1).set(new ItemStack(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get()));
        helper.assertTrue(menu.getSlot(2).hasItem() && !menu.getSlot(2).getItem().isEnchanted(), "The actual anvil preview clears all enchantments including item curses");
        helper.assertTrue(menu.getSlot(2).mayPickup(owner), "Zero-XP user can pick up source disenchantment");
        ItemStack result = menu.getSlot(2).remove(1); menu.getSlot(2).onTake(owner, result);
        helper.assertValueEqual(result.getDamageValue(), 7, "Disenchantment is not free durability repair");
        helper.assertValueEqual(owner.totalExperience, 0, "Source disenchantment costs zero XP");
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "One equipment and one bottle are consumed");
        helper.assertValueEqual(owner.getInventory().countItem(Items.GLASS_BOTTLE), 0, "Source anvil consumes the bottle entirely");
        helper.assertValueEqual(((io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos)).damage(), 0, "Source bottle causes no anvil wear");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void allSevenSolidifiedVesselsDismantleOnceAndPreserveNameWithoutDuplicatingRemainders(GameTestHelper helper) {
        for (String metal : MiteUtilityContent.BUCKET_MATERIALS) {
            ItemStack stone = new ItemStack(MiteUtilityContent.get(metal + "_stone_bucket")); stone.set(DataComponents.CUSTOM_NAME, Component.literal("source vessel"));
            CraftingInput input = CraftingInput.of(1, 1, List.of(stone));
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            ItemStack output = recipe.value().assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(output.is(metal.equals("iron") ? Items.BUCKET : MiteFoods.get(metal + "_bucket")), "Source dismantling returns same-metal empty vessel");
            helper.assertValueEqual(output.getCount(), 1, "Exactly one vessel result");
            helper.assertTrue(output.getHoverName().getString().equals("source vessel"), "Source propagation retains custom name");
            helper.assertTrue(recipe.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "No second empty vessel is created");
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new net.minecraft.world.item.crafting.SingleRecipeInput(stone), helper.getLevel()).isEmpty(), "No invented source stone-bucket smelting recipe");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void carriedLavaSolidifiesOnlyWhenEyesSubmergeAndKeepsEveryMetalIncludingOffhand(GameTestHelper helper) {
        Player owner = player(helper);
        for (int index = 0; index < MiteUtilityContent.BUCKET_MATERIALS.length; index++) {
            String metal = MiteUtilityContent.BUCKET_MATERIALS[index];
            owner.getInventory().setItem(index, new ItemStack(metal.equals("iron") ? Items.LAVA_BUCKET : MiteFoods.get(metal + "_lava_bucket")));
        }
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MiteFoods.get("copper_lava_bucket")));
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(owner));
        helper.assertTrue(owner.getInventory().getItem(0).is(MiteFoods.get("copper_lava_bucket")), "Dry tick cannot solidify lava");
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.WATER); helper.setBlock(new BlockPos(2, 4, 2), Blocks.WATER); helper.setBlock(new BlockPos(2, 5, 2), Blocks.WATER);
        owner.baseTick();
        helper.assertTrue(owner.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "Fixture actually submerges eyes");
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(owner));
        for (int index = 0; index < MiteUtilityContent.BUCKET_MATERIALS.length; index++) helper.assertTrue(owner.getInventory().getItem(index).is(MiteUtilityContent.get(MiteUtilityContent.BUCKET_MATERIALS[index] + "_stone_bucket")), "Every source metal cools in its original slot");
        helper.assertTrue(owner.getOffhandItem().is(MiteUtilityContent.get("copper_stone_bucket")), "Modern offhand follows the carried-vessel rule");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void actualDroppedLavaCoolsInWaterAndSolidBucketsCannotPour(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 3, 2)); helper.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        ItemEntity entity = new ItemEntity(helper.getLevel(), pos.getX()+.5, pos.getY()+.2, pos.getZ()+.5, new ItemStack(MiteFoods.get("silver_lava_bucket")));
        entity.setNoGravity(true); entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); entity.setNeverPickUp(); helper.getLevel().addFreshEntity(entity);
        helper.getLevel().tickNonPassenger(entity);
        helper.assertTrue(entity.getItem().is(MiteUtilityContent.get("silver_stone_bucket")), "Actual entity tick executes source cooling");
        Player owner = player(helper); owner.setItemInHand(InteractionHand.MAIN_HAND, entity.getItem().copy());
        helper.assertTrue(owner.getMainHandItem().use(helper.getLevel(), owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS, "Solid vessel has no liquid-pour interaction");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceUtilitiesDistinguishFireAndLavaAndPreserveAdamantiumVesselAfterStoneBurns(GameTestHelper helper) {
        var fire = helper.getLevel().damageSources().inFire(); var lava = helper.getLevel().damageSources().lava();
        for (String metal : MiteUtilityContent.COIN_MATERIALS) {
            ItemStack coin = new ItemStack(MiteUtilityContent.get(metal+"_coin"));
            helper.assertFalse(coin.canBeHurtBy(fire), "Every metal coin is fireproof");
            helper.assertTrue(coin.canBeHurtBy(lava) != metal.equals("adamantium"), "Only adamantium coin is lava-proof");
        }
        for (String metal : MiteUtilityContent.BUCKET_MATERIALS) {
            ItemStack stone = new ItemStack(MiteUtilityContent.get(metal+"_stone_bucket"));
            helper.assertFalse(stone.canBeHurtBy(fire), "Metal and stone resist fire"); helper.assertTrue(stone.canBeHurtBy(lava), "Stone material makes every full bucket lava-vulnerable");
        }
        ItemStack bottle = new ItemStack(MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get());
        helper.assertFalse(bottle.canBeHurtBy(fire), "Source glass resists fire"); helper.assertTrue(bottle.canBeHurtBy(lava), "Source glass does not resist lava");
        BlockPos pos = helper.absolutePos(new BlockPos(2,3,2)); ItemEntity entity = new ItemEntity(helper.getLevel(),pos.getX(),pos.getY(),pos.getZ(),new ItemStack(MiteUtilityContent.get("adamantium_stone_bucket")));
        helper.getLevel().addFreshEntity(entity); entity.hurt(lava, 10F);
        helper.assertFalse(entity.isRemoved(), "Surviving source adamantium container stays in world"); helper.assertTrue(entity.getItem().is(MiteFoods.get("adamantium_bucket")), "Only impervious empty metal vessel survives contents destruction");
        entity.discard(); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void allFourRecordsResolveRealJukeboxSongsAndPlayEjectWithSourceComposer(GameTestHelper helper) {
        Player owner = player(helper); BlockPos pos = helper.absolutePos(new BlockPos(2,1,2)); helper.getLevel().setBlockAndUpdate(pos,Blocks.JUKEBOX.defaultBlockState());
        var jukebox = (JukeboxBlockEntity)helper.getLevel().getBlockEntity(pos);
        float[] minimums = {122F,119F,123F,62F};
        for (int index=0; index<MiteUtilityContent.RECORD_NAMES.length; index++) {
            String name=MiteUtilityContent.RECORD_NAMES[index]; ItemStack record=new ItemStack(MiteUtilityContent.get("music_disc_"+name));
            var song=JukeboxSong.fromStack(helper.getLevel().registryAccess(),record).orElseThrow();
            helper.assertTrue(song.value().lengthInSeconds()>minimums[index] && song.value().lengthInSeconds()<minimums[index]+1, "Actual OGG duration is registered, not a placeholder song");
            helper.assertTrue(song.value().soundEvent().value().getLocation().toString().equals("mite:record."+name), "Native sound registry resolves source audio");
            JukeboxPlayable.tryInsertIntoJukebox(helper.getLevel(),pos,record,owner);
            helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(JukeboxBlock.HAS_RECORD), "Native insertion marks real jukebox");
            helper.assertTrue(jukebox.getSongPlayer().getSong()!=null, "Native playback starts a registered song");
            helper.assertValueEqual(jukebox.getComparatorOutput(),index+1,"Documented modern comparator output follows source unique-record ordering");
            jukebox.popOutTheItem(); helper.assertTrue(jukebox.getTheItem().isEmpty(),"Native ejection removes one disc");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void uniqueRecordClaimsPersistAndReleaseOnlyForDestructionOrActualExpiry(GameTestHelper helper) {
        UniqueMusicSavedData local = new UniqueMusicSavedData(); helper.assertTrue(local.mark("underworld"),"First source signature is claimed"); helper.assertFalse(local.mark("underworld"),"Same signature cannot be claimed twice");
        CompoundTag tag=local.save(new CompoundTag(),helper.getLevel().registryAccess()); UniqueMusicSavedData loaded=UniqueMusicSavedData.load(tag,helper.getLevel().registryAccess()); helper.assertTrue(loaded.has("underworld"),"Source uniqueness survives save and reload");
        loaded.release("underworld"); helper.assertFalse(loaded.has("underworld"),"Destroyed source disc can be generated again");
        String name="descent";var shared=UniqueMusicSavedData.get(helper.getLevel()); boolean previouslyGenerated=shared.has(name);shared.mark(name);
        ItemEntity entity=new ItemEntity(helper.getLevel(),0,100,0,new ItemStack(MiteUtilityContent.get("music_disc_"+name)));
        ItemExpireEvent prolonged=new ItemExpireEvent(entity); prolonged.setExtraLife(100); UniqueRecordExpiry.expire(prolonged); helper.assertTrue(shared.has(name),"Extending lifetime keeps signature claimed");
        UniqueRecordExpiry.expire(new ItemExpireEvent(entity)); helper.assertFalse(shared.has(name),"Actual expiry releases signature");
        shared.mark(name);((R196RecordItem)entity.getItem().getItem()).onDestroyed(entity,helper.getLevel().damageSources().lava());helper.assertFalse(shared.has(name),"Actual damage destruction releases signature");
        if (previouslyGenerated) shared.mark(name);
        helper.succeed();
    }
}
