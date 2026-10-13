package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteFoods;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Source nine cooking pairs through actual lethal ItemEntity fire damage. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodFireGameTests {
    private FoodFireGameTests() {}

    @GameTest(template="empty")
    public static void allNineSourceFireCookingPairsPreserveStackAndEntityOwnership(GameTestHelper helper) {
        Item[][] pairs={
                {Items.PORKCHOP,Items.COOKED_PORKCHOP},{Items.COD,Items.COOKED_COD},
                {Items.BEEF,Items.COOKED_BEEF},{Items.CHICKEN,Items.COOKED_CHICKEN},
                {Items.POTATO,Items.BAKED_POTATO},{Items.MUTTON,Items.COOKED_MUTTON},
                {MiteFoods.DOUGH.get(),Items.BREAD},{MiteFoods.RAW_LARGE_FISH.get(),MiteFoods.COOKED_LARGE_FISH.get()},
                {MiteFoods.RAW_WORM.get(),MiteFoods.COOKED_WORM.get()}
        };
        for(Item[] pair:pairs) {
            ItemEntity item=entity(helper,pair[0]);
            UUID owner=UUID.randomUUID();
            item.setTarget(owner);
            CompoundTag before=item.saveWithoutId(new CompoundTag());
            before.putShort("Age",(short)43);
            before.putShort("PickupDelay",(short)17);
            item.readAdditionalSaveData(before);
            helper.assertTrue(item.hurt(helper.getLevel().damageSources().inFire(),10F),"The real fire damage is accepted");
            helper.assertFalse(item.isRemoved(),"The full uncooked stack becomes cooked rather than disappearing");
            helper.assertTrue(item.getItem().is(pair[1]),"Lethal fire uses the independently specified source result");
            helper.assertValueEqual(item.getItem().getCount(),3,"Fire preserves all three food items without duplication");
            CompoundTag after=item.saveWithoutId(new CompoundTag());
            helper.assertValueEqual(after.getUUID("Owner"),owner,"Cooking does not change item ownership");
            helper.assertValueEqual(after.getShort("Age"),(short)43,"Modern item age is preserved");
            helper.assertValueEqual(after.getShort("PickupDelay"),(short)17,"Modern pickup delay is preserved");
            helper.assertValueEqual(after.getShort("Health"),(short)5,"The cooked item has normal item health");
            item.discard();
        }
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void nonlethalFireAndNonfireDestructionDoNotProvideInstantOrInfiniteCooking(GameTestHelper helper) {
        ItemEntity raw=entity(helper,MiteFoods.RAW_LARGE_FISH.get());
        raw.hurt(helper.getLevel().damageSources().inFire(),1F);
        helper.assertTrue(raw.getItem().is(MiteFoods.RAW_LARGE_FISH.get()),"Nonlethal damage does not skip the source destruction condition");
        raw.hurt(helper.getLevel().damageSources().inFire(),10F);
        helper.assertTrue(raw.getItem().is(MiteFoods.COOKED_LARGE_FISH.get()),"Lethal fire cooks the raw fish once");
        raw.hurt(helper.getLevel().damageSources().inFire(),10F);
        helper.assertTrue(raw.isRemoved(),"Already cooked food cannot reset item health indefinitely in fire");
        ItemEntity generic=entity(helper,MiteFoods.DOUGH.get());
        generic.hurt(helper.getLevel().damageSources().generic(),10F);
        helper.assertTrue(generic.isRemoved(),"Nonfire destruction does not produce bread");
        helper.succeed();
    }

    private static ItemEntity entity(GameTestHelper helper,Item item) {
        var pos=helper.absolutePos(new BlockPos(1,2,1)).getCenter();
        ItemEntity entity=new ItemEntity(helper.getLevel(),pos.x,pos.y,pos.z,new ItemStack(item,3));
        entity.setNoGravity(true);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
