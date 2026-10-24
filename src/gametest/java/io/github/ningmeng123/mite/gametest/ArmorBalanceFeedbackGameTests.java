package io.github.ningmeng123.mite.gametest;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verify actual equipped toughness, slot boundaries and horse health rather than tooltip-only components. @author NingMeng123 */
@GameTestHolder("mite_feedback")
@PrefixGameTestTemplate(false)
public final class ArmorBalanceFeedbackGameTests {
    private ArmorBalanceFeedbackGameTests() {}
    @GameTest(template="empty")
    public static void everyUsableHumanoidAndBodyArmorHasPositiveToughnessOnlyInItsEquipmentSlot(GameTestHelper h) {
        int checked=0;
        for (var item:BuiltInRegistries.ITEM) {
            if (!(item instanceof ArmorItem armor) || io.github.ningmeng123.mite.item.RemovedDiamondEquipment.isRemoved(item)) continue;
            ItemStack stack=new ItemStack(item);
            double[] toughness={0D};
            stack.forEachModifier(armor.getEquipmentSlot(),(attribute,modifier)->{
                if(attribute.equals(Attributes.ARMOR_TOUGHNESS))toughness[0]+=modifier.amount();
            });
            h.assertTrue(toughness[0]>0D,"Every usable armor item exposes positive equipped toughness: "+BuiltInRegistries.ITEM.getKey(item));
            for(var slot:EquipmentSlot.values())if(slot!=armor.getEquipmentSlot()) {
                double[] wrong={0D};
                stack.forEachModifier(slot,(attribute,modifier)->{if(attribute.equals(Attributes.ARMOR_TOUGHNESS))wrong[0]+=modifier.amount();});
                h.assertTrue(wrong[0]==0D,"Toughness never leaks to another equipment slot: "+BuiltInRegistries.ITEM.getKey(item)+"/"+slot);
            }
            checked++;
        }
        h.assertTrue(checked>=60,"Registry coverage includes retained vanilla, every source material and BODY armor");
        for(var weapon:List.of(Items.IRON_SWORD,Items.BOW,Items.MACE,io.github.ningmeng123.mite.registry.MiteTools.get("copper_sword"))) {
            double[] toughness={0D};
            new ItemStack(weapon).forEachModifier(EquipmentSlot.MAINHAND,(attribute,modifier)->{
                if(attribute.equals(Attributes.ARMOR_TOUGHNESS))toughness[0]+=modifier.amount();
            });
            h.assertTrue(toughness[0]==0D,"Held weapons gain no invented armor toughness");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void horseBodyToughnessActuallyReducesARealHitWithoutChangingSourceBypasses(GameTestHelper h) {
        var horse=EntityType.HORSE.create(h.getLevel());
        horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D); horse.setHealth(100F);
        var barding=new ItemStack(io.github.ningmeng123.mite.registry.MiteArmorContent.get("mite:adamantium_horse_armor"));
        horse.setBodyArmorItem(barding); horse.setNoAi(true); horse.tick();
        h.assertTrue(horse.getAttributeValue(Attributes.ARMOR)==7D,"BODY keeps its independent source armor amount");
        h.assertTrue(horse.getAttributeValue(Attributes.ARMOR_TOUGHNESS)==2.5D,"BODY contributes actual equipped toughness");
        var source=horse.damageSources().mobAttack(EntityType.COW.create(h.getLevel()));
        float before=horse.getHealth();horse.hurt(source,12F);float withToughness=before-horse.getHealth();
        h.assertTrue(Math.abs(withToughness-4.5062857143)<.0001,"BODY preserves flat seven protection and adds only the native toughness improvement");
        var control=ResourceLocation.fromNamespaceAndPath("mite","armor_feedback_body_toughness");
        horse.getAttribute(Attributes.ARMOR_TOUGHNESS).addTransientModifier(new AttributeModifier(control,-2.5D,AttributeModifier.Operation.ADD_VALUE));
        try {
            horse.setHealth(100F);horse.invulnerableTime=0;horse.hurt(source,12F);
            h.assertTrue(Math.abs((100F-horse.getHealth())-5D)<.0001,"Removing BODY toughness retains the exact original flat barding protection");
            h.assertTrue(100F-horse.getHealth()>withToughness,"BODY toughness is functional rather than a tooltip value");
        } finally {horse.getAttribute(Attributes.ARMOR_TOUGHNESS).removeModifier(control);}
        horse.setHealth(100F);horse.invulnerableTime=0;horse.hurt(horse.damageSources().lava(),12F);
        h.assertTrue(Math.abs(horse.getHealth()-88F)<.0001,"Source lava still bypasses mundane barding");
        h.assertTrue(barding.getDamageValue()==0,"Horse armor gains no humanoid durability or quality wear");
        horse.discard();h.succeed();
    }
}
