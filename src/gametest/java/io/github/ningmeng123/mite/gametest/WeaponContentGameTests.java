package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.entity.GelatinousSphere;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.loot.ArmorStructureLootModifier;
import io.github.ningmeng123.mite.ranged.SphereCorrosion;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native bow release, item use, tracking and world contact use independently extracted source values. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WeaponContentGameTests {
    private WeaponContentGameTests() {}
    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absolutePos(new BlockPos(2,3,2)).getCenter()); player.getInventory().clearContent();
        player.experienceLevel = 200; player.totalExperience = 1_000_000; return player;
    }
    private static List<ItemStack> spheres() { return List.of(new ItemStack(Items.SLIME_BALL), new ItemStack(MiteWeaponContent.OCHRE_JELLY.get()),
            new ItemStack(MiteWeaponContent.CRIMSON_BLOB.get()), new ItemStack(MiteWeaponContent.GRAY_OOZE.get()), new ItemStack(MiteWeaponContent.BLACK_PUDDING.get())); }
    @GameTest(template="ranged_027")
    public static void ReinforcedBowDurabilityQualityEnchantabilityAndBenchMatchSource(GameTestHelper helper) {
        ItemStack ancient = new ItemStack(MiteWeaponContent.ANCIENT_METAL_BOW.get()), mithril = new ItemStack(MiteWeaponContent.MITHRIL_BOW.get());
        helper.assertValueEqual(ancient.getMaxDamage(),64,"Ancient bow average durability"); helper.assertValueEqual(mithril.getMaxDamage(),128,"Mithril bow average durability");
        helper.assertTrue(EquipmentDurability.maximumQuality(ancient)==Quality.MASTERWORK && EquipmentDurability.maximumQuality(mithril)==Quality.LEGENDARY,"Highest constituent material controls source bow quality");
        EquipmentDurability.setQuality(ancient,Quality.MASTERWORK); EquipmentDurability.setQuality(mithril,Quality.LEGENDARY);
        helper.assertValueEqual(ancient.getMaxDamage(),192,"Ancient masterwork durability"); helper.assertValueEqual(mithril.getMaxDamage(),448,"Mithril legendary durability");
        helper.assertValueEqual(ancient.getEnchantmentValue(),40,"Ancient enchantability"); helper.assertValueEqual(mithril.getEnchantmentValue(),100,"Mithril enchantability");
        helper.assertValueEqual(WorkbenchRules.required(ancient),16F,"Ancient metal workbench requirement"); helper.assertValueEqual(WorkbenchRules.required(mithril),64F,"Mithril workbench requirement");
        helper.assertTrue(ancient.is(ItemTags.BOW_ENCHANTABLE) && ancient.is(ItemTags.DURABILITY_ENCHANTABLE) && mithril.is(ItemTags.BOW_ENCHANTABLE),"Native enchantment tags allow real bow enchantments");
        helper.assertValueEqual(CraftingQuality.lowestDifficulty(ancient),1750F,"Source genuine minimum manufacturing difficulty"); helper.assertValueEqual(CraftingQuality.lowestDifficulty(mithril),6550F,"Mithril genuine minimum difficulty");
        helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void SourceBowRecipesExecuteNativeShapesAndTimedRequirements(GameTestHelper helper) {
        for (boolean mithril : List.of(false,true)) {
            var ingot = mithril ? MiteMaterials.MITHRIL_INGOT.get() : MiteMaterials.ANCIENT_METAL_INGOT.get();
            var expected = mithril ? MiteWeaponContent.MITHRIL_BOW.get() : MiteWeaponContent.ANCIENT_METAL_BOW.get();
            CraftingInput input = CraftingInput.of(3,3,List.of(ItemStack.EMPTY,new ItemStack(Items.STICK),new ItemStack(Items.STRING),new ItemStack(Items.STICK),new ItemStack(ingot),new ItemStack(Items.STRING),ItemStack.EMPTY,new ItemStack(Items.STICK),new ItemStack(Items.STRING)));
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,helper.getLevel()).orElseThrow();
            helper.assertTrue(recipe.value().assemble(input,helper.getLevel().registryAccess()).is(expected),"Native source bow shape resolves the reinforcement material");
            helper.assertValueEqual(TimedCraftingRecipes.get(recipe.id()).difficulty(),mithril?6550F:1750F,"Timed process source difficulty");
        }
        helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void RealNativeBowReleaseHasSourceVelocityAndOneWearAndOneConsumedArrow(GameTestHelper helper) {
        Player player=player(helper);
        for (var bowItem : List.of(MiteWeaponContent.ANCIENT_METAL_BOW.get(), MiteWeaponContent.MITHRIL_BOW.get())) {
            ItemStack bow=new ItemStack(bowItem), ammo=new ItemStack(MiteRangedItems.arrow(Material.MITHRIL),2);
            ammo.set(DataComponents.CUSTOM_NAME,Component.literal("028-arrow-"+UUID.randomUUID()));
            player.setItemInHand(InteractionHand.MAIN_HAND,bow); player.getInventory().setItem(1,ammo); player.startUsingItem(InteractionHand.MAIN_HAND);
            bowItem.releaseUsing(bow,helper.getLevel(),player,71980); player.stopUsingItem();
            var arrows=helper.getLevel().getEntitiesOfClass(Arrow.class,player.getBoundingBox().inflate(5)).stream().filter(a -> ItemStack.isSameItemSameComponents(a.getPickupItemStackOrigin(),ammo)).toList();
            helper.assertValueEqual(arrows.size(),1,"Real bow release creates exactly one matching source arrow"); helper.assertValueEqual(ammo.getCount(),1,"Exactly one ammunition consumed");
            helper.assertValueEqual(bow.getDamageValue(),1,"Exactly one durability point per real shot");
            double expected=bowItem==MiteWeaponContent.MITHRIL_BOW.get()?3.75:3.3;
            helper.assertTrue(Math.abs(arrows.getFirst().getDeltaMovement().length()-expected)<.1,"Native launch applies reinforcement velocity with narrow high-level source spread");
            helper.assertTrue(arrows.getFirst().isCritArrow(),"Full source pull remains a native critical arrow"); arrows.forEach(Arrow::discard);
        }
        helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void ReinforcedBowMaterialRepairUsesNuggetsAndSourceAnvilWear(GameTestHelper helper) {
        Player player=player(helper);
        ItemStack bow=new ItemStack(MiteWeaponContent.ANCIENT_METAL_BOW.get()); bow.setDamageValue(40);
        var anvil=io.github.ningmeng123.mite.registry.MiteFunctionalBlocks.ANVILS.get("ancient_metal").get().defaultBlockState();
        var plan=MaterialAnvilRules.plan(player,anvil,bow,new ItemStack(MiteMaterials.ANCIENT_METAL_NUGGET.get()));
        helper.assertTrue(plan!=null && plan.allowed(),"Real reinforced bow material exposes source nugget repair");
        helper.assertValueEqual(plan.output().getDamageValue(),8,"One nugget restores half of average source bow durability");
        helper.assertValueEqual(plan.consumed(),1,"One source repair component consumed"); helper.assertValueEqual(plan.anvilWear(),6400,"Source bow ratio applies 200 anvil wear per repaired point");
        helper.assertTrue(!MaterialAnvilRules.plan(player,Blocks.ANVIL.defaultBlockState(),bow,new ItemStack(MiteMaterials.ANCIENT_METAL_NUGGET.get())).allowed(),"Iron anvil cannot repair ancient reinforcement");
        helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void AllFiveRealItemUsesSpawnConsumeAndTrackSourceSphereIdentities(GameTestHelper helper) {
        Player player=player(helper);
        for (ItemStack prototype:spheres()) {
            ItemStack stack=prototype.copyWithCount(2); player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var result=stack.getItem().use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getResult().consumesAction(),"Source sphere right click is a real native item use");
            helper.assertValueEqual(stack.getCount(),1,"Survival throwing consumes exactly one sphere");
            var projectiles=helper.getLevel().getEntitiesOfClass(GelatinousSphere.class,player.getBoundingBox().inflate(5)).stream().filter(s -> s.getOwner()==player && s.getItem().is(prototype.getItem())).toList();
            helper.assertValueEqual(projectiles.size(),1,"Each actual source item creates one tracked sphere");
            GelatinousSphere sphere=projectiles.getFirst(); CompoundTag tag=new CompoundTag(); sphere.saveWithoutId(tag);
            GelatinousSphere reloaded=new GelatinousSphere(MiteWeaponContent.SPHERE.get(),helper.getLevel()); reloaded.load(tag);
            helper.assertTrue(reloaded.getItem().is(prototype.getItem()),"Source sphere item identity survives native NBT");
            var data=sphere.getEntityData().getNonDefaultValues(); GelatinousSphere peer=new GelatinousSphere(MiteWeaponContent.SPHERE.get(),helper.getLevel()); if(data!=null)peer.getEntityData().assignValues(data);
            helper.assertTrue(peer.getItem().is(prototype.getItem()),"Native tracked item stack selects the matching source sphere renderer");
            sphere.discard(); reloaded.discard(); peer.discard();
        }
        helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void ActualSphereHitsHaveSourceThrownDamageAndRespectCancellation(GameTestHelper helper) throws Exception {
        Player player=player(helper); int[] expected={2,3,4,4,5};
        for(int i=0;i<5;i++) {
            Cow target=new Cow(EntityType.COW,helper.getLevel()); target.moveTo(player.position());
            GelatinousSphere sphere=new GelatinousSphere(helper.getLevel(),player,spheres().get(i));
            final float[] damage={-1}; final String[] type={""};
            java.util.function.Consumer<LivingIncomingDamageEvent> listener=e->{if(e.getEntity()==target){damage[0]=e.getOriginalAmount(); type[0]=e.getSource().getMsgId(); e.setCanceled(true);}};
            NeoForge.EVENT_BUS.addListener(listener);
            try { hit(sphere,new EntityHitResult(target)); } finally {NeoForge.EVENT_BUS.unregister(listener);}
            helper.assertValueEqual(damage[0],(float)expected[i],"Actual source sphere transaction uses independently extracted thrown damage");
            helper.assertTrue(type[0].equals("thrown"),"Sphere entity damage remains ordinary thrown damage rather than inventing acid equipment corrosion");
            helper.assertValueEqual(target.getHealth(),target.getMaxHealth(),"Cancelled native damage leaves health untouched"); helper.assertTrue(sphere.isRemoved(),"Source missile is consumed after impact");
            Cow acceptedTarget=new Cow(EntityType.COW,helper.getLevel()); float before=acceptedTarget.getHealth();
            hit(new GelatinousSphere(helper.getLevel(),player,spheres().get(i)),new EntityHitResult(acceptedTarget));
            helper.assertValueEqual(before-acceptedTarget.getHealth(),(float)expected[i],"Accepted real sphere damage reduces unarmored target health by the source value");
        }
        helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void ActualAcidImpactDissolvesHitAndFacingNeighborWithoutDrops(GameTestHelper helper) throws Exception {
        BlockPos hitPos=new BlockPos(3,2,3); helper.setBlock(hitPos.east().below(),Blocks.STONE); helper.setBlock(hitPos,Blocks.WHITE_WOOL); helper.setBlock(hitPos.east(),Blocks.WHITE_CARPET);
        int oldDrops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,helper.getBounds()).size();
        GelatinousSphere sphere=new GelatinousSphere(helper.getLevel(),player(helper),new ItemStack(MiteWeaponContent.GRAY_OOZE.get()));
        hit(sphere,new BlockHitResult(helper.absolutePos(hitPos).getCenter(),Direction.EAST,helper.absolutePos(hitPos),false));
        helper.assertBlockPresent(Blocks.AIR,hitPos); helper.assertBlockPresent(Blocks.AIR,hitPos.east());
        helper.assertValueEqual(helper.getLevel().getEntitiesOfClass(ItemEntity.class,helper.getBounds()).size(),oldDrops,"Instant source dissolution never generates loot");
        helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void PepsinInstantlyRemovesWebButOnlyShowsSteamOnWoolAndBeds(GameTestHelper helper) throws Exception {
        BlockPos pos=new BlockPos(3,2,3); helper.setBlock(pos,Blocks.COBWEB);
        hit(new GelatinousSphere(helper.getLevel(),player(helper),new ItemStack(MiteWeaponContent.OCHRE_JELLY.get())),new BlockHitResult(helper.absolutePos(pos).getCenter(),Direction.UP,helper.absolutePos(pos),false));
        helper.assertBlockPresent(Blocks.AIR,pos);
        helper.setBlock(pos,Blocks.WHITE_WOOL);
        hit(new GelatinousSphere(helper.getLevel(),player(helper),new ItemStack(Items.SLIME_BALL)),new BlockHitResult(helper.absolutePos(pos).getCenter(),Direction.UP,helper.absolutePos(pos),false));
        helper.assertBlockPresent(Blocks.WHITE_WOOL,pos); helper.assertValueEqual(SphereCorrosion.period(Blocks.WHITE_WOOL.defaultBlockState(),false),400,"Pepsin cannot accumulate 400 ticks from a single projectile");
        helper.assertValueEqual(SphereCorrosion.period(Blocks.IRON_BLOCK.defaultBlockState(),true),400,"Source acid metal block response is gradual");
        helper.assertValueEqual(SphereCorrosion.period(Blocks.GOLD_BLOCK.defaultBlockState(),true),-1,"Source gold is acid resistant");
        helper.assertValueEqual(SphereCorrosion.period(Blocks.COPPER_DOOR.defaultBlockState(),true),400,"Retained modern copper door follows source copper corrosion");
        var decorative=io.github.ningmeng123.mite.registry.MiteDecorativeContent.DOORS;
        helper.assertValueEqual(SphereCorrosion.period(decorative.get("ancient_metal").get().defaultBlockState(),true),400,"New ancient door follows guarded source period");
        helper.assertValueEqual(SphereCorrosion.period(decorative.get("mithril").get().defaultBlockState(),true),-1,"New mithril door remains acid resistant");
        helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void AcidConvertsOnlyGrassAndMyceliumTopSurfaceToDirt(GameTestHelper helper) {
        BlockPos grass=new BlockPos(3,2,3), mycelium=new BlockPos(5,2,3);
        helper.setBlock(grass,Blocks.GRASS_BLOCK); helper.setBlock(mycelium,Blocks.MYCELIUM);
        SphereCorrosion.contact(helper.getLevel(),helper.absolutePos(grass),Direction.NORTH,true); helper.assertBlockPresent(Blocks.GRASS_BLOCK,grass);
        SphereCorrosion.contact(helper.getLevel(),helper.absolutePos(grass),Direction.UP,true); SphereCorrosion.contact(helper.getLevel(),helper.absolutePos(mycelium),Direction.UP,true);
        helper.assertBlockPresent(Blocks.DIRT,grass); helper.assertBlockPresent(Blocks.DIRT,mycelium); helper.succeed();
    }
    @GameTest(template="ranged_027")
    public static void AncientBowIsInTheExistingSourceUnderworldDungeonWeightedPool(GameTestHelper helper) {
        var pool=ArmorStructureLootModifier.pools().get("dungeon_underworld");
        var entry=pool.entries().stream().filter(e->e.item().equals("mite:ancient_metal_bow")).findFirst().orElseThrow();
        helper.assertValueEqual(entry.weight(),2,"Source afp ancient bow weight"); helper.assertValueEqual(entry.minimum(),1,"One source ancient bow minimum"); helper.assertValueEqual(entry.maximum(),1,"One source ancient bow maximum");
        helper.succeed();
    }
    private static void hit(GelatinousSphere sphere,HitResult result) throws Exception { Method method=GelatinousSphere.class.getDeclaredMethod("onHit",HitResult.class);method.setAccessible(true);method.invoke(sphere,result); }
}
