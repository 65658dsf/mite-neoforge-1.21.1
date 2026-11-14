package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsHandling;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual registered stats, saved base IDs, loaded combat profiles and native throwing callbacks. @author NingMeng123 */
@GameTestHolder("mite_simplyswords")
@PrefixGameTestTemplate(false)
public final class SimplySwordsBalanceGameTests {
    private SimplySwordsBalanceGameTests() {}

    @GameTest(template="empty",batch="simplyswords_balance")
    public static void actualMaterialVariantsHaveIndependentDamageAndSpeedTargets(GameTestHelper h) {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        var expected=Map.of("longsword",9D,"spear",5.85D,"glaive",5.85D,"halberd",4.95D,"greathammer",11.25D);
        var rates=Map.of("longsword",1.3D,"spear",.9D,"glaive",.8D,"halberd",.65D,"greathammer",.56D);
        for (var entry:expected.entrySet()) {
            var stack=new ItemStack(SimplySwordsCompat.weapon("ancient_metal",entry.getKey()));
            close(h, damage(stack), entry.getValue(), "The real nine-point ancient-metal material has its reviewed "+entry.getKey()+" damage");
            close(h, rate(stack), rates.get(entry.getKey()), "The real equipped attack rate includes exactly one handedness adjustment");
        }
        String[] families={"longsword","twinblade","rapier","katana","sai","spear","glaive","warglaive","cutlass","claymore","greataxe","greathammer","chakram","scythe","halberd"};
        double[] copper={8D,5.6D,6D,6.8D,4.8D,5.2D,5.2D,6.4D,6.4D,9.2D,9.6D,10D,4.4D,5.6D,4.4D};
        double[] iron={9D,6.3D,6.75D,7.65D,5.4D,5.85D,5.85D,7.2D,7.2D,10.35D,10.8D,11.25D,4.95D,6.3D,4.95D};
        double[] actualRates={1.3D,1.05D,1.85D,1.7D,2D,.9D,.8D,1.65D,1.75D,.76D,.64D,.56D,.9D,.8D,.65D};
        for(int index=0;index<families.length;index++) {
            close(h,damage(weapon("copper",families[index])),copper[index],"The real copper "+families[index]+" uses its source eight-point sword budget");
            close(h,damage(weapon("silver",families[index])),copper[index],"The real silver "+families[index]+" uses its source eight-point sword budget");
            close(h,damage(weapon("iron",families[index])),iron[index],"The native SS iron "+families[index]+" shares the source iron tier instead of underperforming copper");
            close(h,rate(weapon("copper",families[index])),actualRates[index],"Added material equipment uses the reviewed final family rate");
            close(h,rate(weapon("iron",families[index])),actualRates[index],"Native iron equipment uses the reviewed final family rate");
        }
        close(h,damage(weapon("gold","longsword")),7D,"Native SS gold uses the source seven-point sword basis");
        h.assertTrue(damage(weapon("ancient_metal","greathammer"))>damage(weapon("ancient_metal","longsword")),"The hammer retains the stronger individual hit");
        h.assertTrue(damage(weapon("ancient_metal","greathammer"))*rate(weapon("ancient_metal","greathammer"))
                <damage(weapon("ancient_metal","longsword"))*rate(weapon("ancient_metal","longsword")),"The slower heavy weapon cannot also dominate sustained base damage");
        int added=0;
        for (var item:BuiltInRegistries.ITEM) {
            var stack=new ItemStack(item); var definition=SimplySwordsCompat.definition(stack);
            if (definition==null) continue;
            if (definition.added()) added++;
            h.assertTrue(Double.isFinite(damage(stack))&&damage(stack)>0D&&damage(stack)<=80D,"Every native/unique/added registered family has finite bounded base damage: "+BuiltInRegistries.ITEM.getKey(item));
            var speedProfile=io.github.ningmeng123.mite.rules.SimplySwordsBalanceRules.profile(definition.family());
            double handling=SimplySwordsHandling.twoHanded(stack)?.8D:1D;
            h.assertTrue(rate(stack)>=speedProfile.minimumAttacksPerSecond()*handling-.00001D
                    &&rate(stack)<=speedProfile.maximumAttacksPerSecond()*handling+.00001D,
                    "Every native/unique/added family keeps its bounded rate after exactly one real handedness cost");
            h.assertTrue(SimplySwordsHandling.movementPenalty(stack)>=0D&&SimplySwordsHandling.movementPenalty(stack)<=.25D,"Player-only movement weight stays within its reviewed quarter-speed maximum");
            h.assertFalse(stack.getAttributeModifiers().modifiers().stream().anyMatch(modifier->modifier.attribute().equals(Attributes.MOVEMENT_SPEED)
                    &&modifier.modifier().id().toString().equals("mite:simplyswords_two_handed_movement_speed")),"No registered weapon still supplies the removed item movement penalty");
        }
        h.assertValueEqual(added,105,"All 105 material variants pass the same live attribute path");
        h.succeed();
    }

    @GameTest(template="empty",batch="simplyswords_balance")
    public static void loadedNativeComboPeaksPreserveTheReviewedDamageBudget(GameTestHelper h) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled() || !io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge.enabled()) {
            h.succeed();
            return;
        }
        String[] families={"longsword","twinblade","rapier","katana","sai","spear","glaive","warglaive",
                "cutlass","claymore","greataxe","greathammer","chakram","scythe","halberd"};
        double[] expectedPeak={1.4D,1.2D,1.1D,1.2D,1D,1D,1.4D,1.2D,1.3D,1.25D,1.4D,1.5D,1D,1.4D,1.6D};
        var registry=Class.forName("net.bettercombat.logic.WeaponRegistry").getMethod("getAttributes",ItemStack.class);
        for (String material:java.util.List.of("copper","iron")) {
            double swordPeak=damage(weapon(material,"longsword"))*1.4D;
            for (int index=0;index<families.length;index++) {
                ItemStack stack=weapon(material,families[index]);
                Object attributes=registry.invoke(null,stack);
                h.assertTrue(attributes!=null,"The installed combat registry resolves the actual material weapon");
                Object[] attacks=(Object[])attributes.getClass().getMethod("attacks").invoke(attributes);
                double peak=0D;
                for (Object attack:attacks) {
                    double multiplier=((Number)attack.getClass().getMethod("damageMultiplier").invoke(attack)).doubleValue();
                    peak=Math.max(peak,multiplier);
                }
                double expected=material.equals("iron")&&families[index].equals("scythe")?1D:expectedPeak[index];
                close(h,peak,expected,"The original parsed combo remains intact for "+material+" "+families[index]);
                boolean heavy=java.util.Set.of("claymore","greataxe","greathammer").contains(families[index]);
                if (heavy) h.assertTrue(damage(stack)*peak>swordPeak,"The heavy weapon retains its stronger peak hit");
                else h.assertTrue(damage(stack)*peak<=swordPeak+.0001D,"Fast and long weapons keep their peak hit within the sword budget");
            }
        }
        h.succeed();
    }

    @GameTest(template="empty",batch="simplyswords_balance")
    public static void oldChakramBaseNormalizesOnceWhileKeepingIndependentAffixes(GameTestHelper h) {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        var stack = weapon("iron", "chakram");
        close(h, damage(stack), 4.95D, "The real iron chakram applies its single .55 family budget");
        var extra = ResourceLocation.fromNamespaceAndPath("mite", "chakram_balance_test_affix");
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 4.76D,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(extra, 2D,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
        stack = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
        var saved = stack.save(h.getLevel().registryAccess());
        for (int query = 0; query < 4; ++query) {
            close(h, damage(stack), 6.95D, "Saved base becomes 4.95 while the independent two-point affix remains intact");
            h.assertValueEqual(stack.getAttributeModifiers().modifiers().stream()
                    .filter(entry -> entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID)).count(), 1L,
                    "Repeated reads do not duplicate the normalized base");
        }
        h.assertValueEqual(stack.save(h.getLevel().registryAccess()), saved, "Effective normalization leaves the saved payload stable");
        h.succeed();
    }

    @GameTest(template="empty",batch="simplyswords_balance")
    public static void oldSavedBaseAttributesNormalizeWithoutRemovingOtherAffixes(GameTestHelper h) {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        var stack=weapon("ancient_metal","greathammer");
        var extra=ResourceLocation.fromNamespaceAndPath("mite","balance_test_extra_damage");
        var legacy=ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,999D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,998D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.ANY)
                .add(Attributes.ATTACK_SPEED,new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,99D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,98D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.ANY)
                .add(Attributes.ATTACK_SPEED,new AttributeModifier(ResourceLocation.parse("mite:simplyswords_two_handed_attack_speed"),-.2D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,new AttributeModifier(ResourceLocation.parse("mite:simplyswords_two_handed_attack_speed"),-.4D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),EquipmentSlotGroup.ANY)
                .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(extra,2D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.MOVEMENT_SPEED,new AttributeModifier(ResourceLocation.parse("mite:simplyswords_two_handed_movement_speed"),-.15D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.MOVEMENT_SPEED,new AttributeModifier(ResourceLocation.parse("mite:simplyswords_two_handed_movement_speed"),-.35D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),EquipmentSlotGroup.ANY).build();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS,legacy);
        var saved=stack.save(h.getLevel().registryAccess());
        stack=ItemStack.parse(h.getLevel().registryAccess(),saved).orElseThrow();
        for (int query=0;query<8;query++) {
            close(h,damage(stack),13.25D,"Old 999 damage becomes reviewed 11.25 base plus the existing separate two-point affix");
            close(h,rate(stack),.56D,"Old fast-heavy components cannot bypass the heavy .7 family rate and its one20% handling cost");
            h.assertTrue(stack.getAttributeModifiers().modifiers().stream().anyMatch(entry->entry.modifier().id().equals(extra)&&entry.modifier().amount()==2D),"Separate affix IDs and amounts remain intact");
            h.assertFalse(stack.getAttributeModifiers().modifiers().stream().anyMatch(entry->entry.modifier().id().toString().equals("mite:simplyswords_two_handed_movement_speed")),"Old item-based movement components are excluded from effective attributes");
            for (var base:java.util.List.of(Item.BASE_ATTACK_DAMAGE_ID,Item.BASE_ATTACK_SPEED_ID,ResourceLocation.parse("mite:simplyswords_two_handed_attack_speed")))
                h.assertValueEqual(stack.getAttributeModifiers().modifiers().stream().filter(entry->entry.modifier().id().equals(base)).count(),1L,
                        "Each canonical/handling ID appears exactly once after clearing duplicated saved slots: "+base);
            double[] offhand={0D};stack.forEachModifier(EquipmentSlot.OFFHAND,(attribute,modifier)->{if(attribute.equals(Attributes.ATTACK_DAMAGE))offhand[0]+=modifier.amount();});
            close(h,offhand[0],0D,"Old ANY base modifiers are corrected back to main-hand-only stats");
            double[] offhandSpeed={0D};stack.forEachModifier(EquipmentSlot.OFFHAND,(attribute,modifier)->{if(attribute.equals(Attributes.ATTACK_SPEED))offhandSpeed[0]+=modifier.amount();});
            close(h,offhandSpeed[0],0D,"Old ANY speed and two-handed penalties cannot affect the offhand");
        }
        h.assertValueEqual(stack.save(h.getLevel().registryAccess()),saved,"Queries never rewrite or multiply the saved component payload");
        h.succeed();
    }

    @GameTest(template="empty",batch="simplyswords_balance")
    public static void actualNativeThrowsReadBalancedNewAndOldSavedSpears(GameTestHelper h) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        for (boolean old:new boolean[]{false,true}) {
            var owner=h.makeMockPlayer(GameType.CREATIVE);owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5,3,1.5)));
            // The native throwable checks the ability field directly; the lightweight mock's isCreative override is insufficient.
            owner.getAbilities().instabuild=true;
            ItemStack stack=weapon("ancient_metal","spear");
            if (old) {
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,999D,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build());
                stack=ItemStack.parse(h.getLevel().registryAccess(),stack.save(h.getLevel().registryAccess())).orElseThrow();
            }
            owner.setItemSlot(EquipmentSlot.MAINHAND,stack);
            AtomicReference<AbstractArrow> observed=new AtomicReference<>();
            Consumer<EntityJoinLevelEvent> listener=event->{if(event.getEntity() instanceof AbstractArrow arrow
                    &&arrow.getClass().getName().equals("net.sweenus.simplyswords.entity.ThrownSpearEntity")&&arrow.getOwner()==owner)observed.set(arrow);};
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                stack.getItem().use(h.getLevel(),owner,InteractionHand.MAIN_HAND);
                h.assertTrue(observed.get()!=null&&observed.get().isAddedToLevel(),"The original throwable item's actual use path creates its real projectile");
                h.assertFalse(stack.isEmpty(),"The explicit creative-ability fixture preserves the source stack for the companion helper check");
                close(h,Class.forName("net.sweenus.simplyswords.entity.ThrownSpearEntity").getField("primaryBaseDamage").getFloat(observed.get()),4.85D,
                        "Native throwing reads reviewed spear damage through its helper for "+(old?"old saved":"new default")+" components");
                double helper=(Double)Class.forName("net.sweenus.simplyswords.util.HelperMethods")
                        .getMethod("getAttackFromStack",ItemStack.class,EquipmentSlotGroup.class).invoke(null,stack,EquipmentSlotGroup.MAINHAND);
                close(h,helper,4.85D,"The companion native skill helper reads the same normalized stat");
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);if(observed.get()!=null)observed.get().discard();owner.discard();
            }
        }
        h.succeed();
    }

    private static ItemStack weapon(String material,String family) { return new ItemStack(SimplySwordsCompat.weapon(material,family)); }
    private static double damage(ItemStack stack) {
        double[] value={1D};stack.forEachModifier(EquipmentSlot.MAINHAND,(attribute,modifier)->{if(attribute.equals(Attributes.ATTACK_DAMAGE)
                &&modifier.operation()==AttributeModifier.Operation.ADD_VALUE)value[0]+=modifier.amount();});return value[0];
    }
    private static double rate(ItemStack stack) {
        double[] value={4D},factor={1D};stack.forEachModifier(EquipmentSlot.MAINHAND,(attribute,modifier)->{if(attribute.equals(Attributes.ATTACK_SPEED)) {
            if(modifier.operation()==AttributeModifier.Operation.ADD_VALUE)value[0]+=modifier.amount();
            else if(modifier.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)factor[0]*=1D+modifier.amount();
        }});return value[0]*factor[0];
    }
    private static void close(GameTestHelper h,double actual,double expected,String label) { h.assertTrue(Math.abs(actual-expected)<.0001D,label+": "+actual+" vs "+expected); }
}
