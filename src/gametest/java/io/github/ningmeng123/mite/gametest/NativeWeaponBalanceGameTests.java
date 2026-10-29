package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real component events normalize saved bases without swallowing a distinct modifier. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class NativeWeaponBalanceGameTests {
    private NativeWeaponBalanceGameTests() {}

    @GameTest(template="empty")
    public static void swordsHeavyWeaponsAndLongReachFamiliesHaveDistinctActualBudgets(GameTestHelper h) {
        for (var vector : List.of(new Vector("minecraft:iron_sword",7.2D,2D),
                new Vector("mite:iron_battle_axe",8.1D,.85D), new Vector("mite:iron_war_hammer",5.6D,.75D),
                new Vector("mite:vibranium_sword",11.2D,1.5D),new Vector("mite:copper_sword",6D,1.5D),
                new Vector("mite:silver_sword",6.4D,1.5D))) {
            ItemStack stack=new ItemStack(MiteTools.get(vector.item()));
            close(h, 1D+amount(stack,Attributes.ATTACK_DAMAGE,EquipmentSlot.MAINHAND),vector.damage(),"Actual balanced damage "+vector.item());
            close(h, 4D+amount(stack,Attributes.ATTACK_SPEED,EquipmentSlot.MAINHAND),vector.speed(),"Actual cooldown speed "+vector.item());
            close(h, amount(stack,Attributes.ATTACK_DAMAGE,EquipmentSlot.OFFHAND),0D,"Offhand cannot add a second weapon base");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void everyRegisteredCopperAndSilverFamilyHasItsReviewedSurvivalDamage(GameTestHelper h) {
        String[] families={"sword","dagger","battle_axe","war_hammer","axe","hatchet","pickaxe","shovel","hoe","mattock","scythe","shears"};
        double[] copper={6D,4.5D,6.75D,4.5D,4.265625D,3.65625D,3.65625D,3.046875D,3.046875D,3.046875D,3.28125D,2.25D};
        double[] silver={6.4D,4.8D,7.2D,4.8D,4.55D,3.9D,3.9D,3.25D,3.25D,3.25D,3.5D,2.4D};
        for(int index=0;index<families.length;index++) {
            var copperStack=new ItemStack(MiteTools.get("copper_"+families[index]));
            var silverStack=new ItemStack(MiteTools.get("silver_"+families[index]));
            close(h,1D+amount(copperStack,Attributes.ATTACK_DAMAGE,EquipmentSlot.MAINHAND),copper[index],"Actual copper "+families[index]+" follows the six-point sword material budget");
            close(h,1D+amount(silverStack,Attributes.ATTACK_DAMAGE,EquipmentSlot.MAINHAND),silver[index],"Actual silver "+families[index]+" retains its modest material advantage");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void oldSavedHighBaseCannotBypassBalanceAndOtherBonusesSurviveRepeatedReads(GameTestHelper h) {
        ItemStack stack=new ItemStack(MiteTools.get("vibranium_sword"));
        var extra=ResourceLocation.fromNamespaceAndPath("mite","balance_fixture_extra");
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,13D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,999D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.ANY)
                .add(Attributes.ATTACK_SPEED,new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,-2.4D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,99D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.ANY)
                .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(extra,2.5D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build());
        var tag=stack.save(h.getLevel().registryAccess());
        ItemStack loaded=ItemStack.parse(h.getLevel().registryAccess(),tag).orElseThrow();
        for(int read=0;read<10;read++) {
            close(h,1D+amount(loaded,Attributes.ATTACK_DAMAGE,EquipmentSlot.MAINHAND),13.7D,"Saved base is11.2 and the separate2.5 bonus survives once");
            close(h,4D+amount(loaded,Attributes.ATTACK_SPEED,EquipmentSlot.MAINHAND),1.5D,"Saved speed is normalized once");
            close(h,amount(loaded,Attributes.ATTACK_DAMAGE,EquipmentSlot.OFFHAND),0D,"Duplicate saved ANY bases cannot leak into the offhand");
            h.assertValueEqual(loaded.getAttributeModifiers().modifiers().stream().filter(e->e.modifier().id().equals(extra)).count(),1L,
                    "Repeated reads do not multiply a separate attribute");
        }
        h.assertValueEqual(loaded.save(h.getLevel().registryAccess()),tag,"Normalization never rewrites the saved component payload");
        h.succeed();
    }

    private record Vector(String item,double damage,double speed) {}
    private static double amount(ItemStack stack,net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,EquipmentSlot slot) {
        double[] value={0D};
        stack.forEachModifier(slot,(candidate,modifier)->{if(candidate.equals(attribute)&&modifier.operation()==AttributeModifier.Operation.ADD_VALUE)value[0]+=modifier.amount();});
        return value[0];
    }
    private static void close(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Math.abs(actual-expected)<.00001D,message+": "+actual+" vs "+expected);
    }
}
