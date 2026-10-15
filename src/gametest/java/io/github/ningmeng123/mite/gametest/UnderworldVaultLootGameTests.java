package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.UnderworldVaultLoot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual loaded server loot graphs, independent seeded draws and legal output stacks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UnderworldVaultLootGameTests {
    private static final String PREFIX="mite:chests/underworld_trial_chambers/";
    private static final Set<String> ARROWS=Set.of("minecraft:arrow","mite:rusted_iron_arrow","mite:obsidian_arrow",
            "mite:iron_arrow","mite:copper_arrow","mite:golden_arrow","mite:silver_arrow",
            "mite:ancient_metal_arrow","mite:mithril_arrow","mite:adamantium_arrow");
    private static final Set<String> NUGGETS=Set.of("mite:ancient_metal_nugget","mite:mithril_nugget","mite:adamantium_nugget");
    private static final Set<String> DIAMOND_GEAR=Set.of("minecraft:diamond_axe","minecraft:diamond_pickaxe",
            "minecraft:diamond_shovel","minecraft:diamond_hoe","minecraft:diamond_sword","minecraft:diamond_helmet",
            "minecraft:diamond_chestplate","minecraft:diamond_leggings","minecraft:diamond_boots");
    private UnderworldVaultLootGameTests() {}

    @GameTest(template="empty",timeoutTicks=400)
    public static void normalVaultReallyRollsMetalsAncientEquipmentAndEveryMaterialArrow(GameTestHelper helper) {
        verifyRoot(helper,false);
    }

    @GameTest(template="empty",timeoutTicks=400)
    public static void ominousVaultReallyRollsMetalsAncientEquipmentAndEveryMaterialArrow(GameTestHelper helper) {
        verifyRoot(helper,true);
    }

    private static void verifyRoot(GameTestHelper helper,boolean ominous) {
        ServerLevel level=underworld(helper);
        LootTable table=level.getServer().reloadableRegistries().getLootTable(ominous?UnderworldVaultLoot.REWARD_OMINOUS:UnderworldVaultLoot.REWARD);
        LootParams params=params(level);
        Map<String,Integer> occurrences=new HashMap<>();Set<String> enchanted=new HashSet<>();
        int samples=8192;
        for(int trial=1;trial<=samples;trial++) {
            // Explicit nonzero seeds avoid global world RNG and independent table random sequences.
            var stacks=table.getRandomItems(params,0x5641554c54L+trial*1_000_003L+(ominous?17:0));
            helper.assertTrue(!stacks.isEmpty(),"A real vault roll always has its ordinary rewards");
            Set<String> present=new HashSet<>();
            for(ItemStack stack:stacks) {
                String id=id(stack);legal(helper,stack);present.add(id);if(stack.isEnchanted())enchanted.add(id);
            }
            for(String id:present)occurrences.merge(id,1,Integer::sum);
        }
        for(String id:ARROWS)helper.assertTrue(occurrences.getOrDefault(id,0)>0,"The actual root table reaches material arrow "+id);
        for(String id:NUGGETS)helper.assertTrue(occurrences.getOrDefault(id,0)>0,"The actual root table reaches metal nugget "+id);
        for(String id:new String[]{"mite:ancient_metal_axe","mite:ancient_metal_chestplate"})
            helper.assertTrue(enchanted.contains(id),"The replacement retains real enchantment functions: "+id);
        near(helper,occurrences,"minecraft:emerald",samples,ominous?.58277194:.40953170);
        near(helper,occurrences,"minecraft:diamond",samples,ominous?.27587026:.09172447);
        if(ominous) {
            near(helper,occurrences,"minecraft:enchanted_golden_apple",samples,.25);
            near(helper,occurrences,"minecraft:heavy_core",samples,.075);
        } else helper.assertTrue(!occurrences.containsKey("minecraft:heavy_core"),"Normal vault does not gain the ominous-only heavy core");
        System.out.println("[MITE underworld vault loot] mode="+(ominous?"ominous":"normal")+" samples="+samples+" openingsWithItems="+occurrences);
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=400)
    public static void bothArrowTablesKeepQuantitiesAndInverseMaterialOrderOnActualServerRolls(GameTestHelper helper) {
        ServerLevel level=underworld(helper);LootParams params=params(level);
        for(boolean ominous:new boolean[]{false,true}) {
            LootTable table=level.getServer().reloadableRegistries().getLootTable(key(ominous?"arrows_ominous":"arrows"));
            Map<String,Integer> count=new HashMap<>();
            for(int trial=1;trial<=8192;trial++) {
                var stacks=table.getRandomItems(params,0x4152524f57L+trial*1_000_033L);
                helper.assertValueEqual(stacks.size(),1,"One material selection produces one arrow stack");
                ItemStack stack=stacks.getFirst();String id=id(stack);
                helper.assertTrue(ARROWS.contains(id),"Arrow child cannot emit a non-arrow reward");
                helper.assertTrue(stack.getCount()>=(ominous?4:2)&&stack.getCount()<=(ominous?12:8),"Material arrow count executes once in the child");
                legal(helper,stack);count.merge(id,1,Integer::sum);
            }
            for(String id:ARROWS)helper.assertTrue(count.getOrDefault(id,0)>0,"Loaded arrow child reaches "+id);
            // Broad, well-separated rarity comparisons exercise engine selection; exact weights are
            // checked independently against the original tables by the static Fraction audit.
            helper.assertTrue(count.get("minecraft:arrow")>count.get("mite:ancient_metal_arrow"),"Common flint is more frequent than ancient metal");
            helper.assertTrue(count.get("mite:iron_arrow")>count.get("mite:mithril_arrow"),"Common iron is more frequent than mithril");
            helper.assertTrue(count.get("mite:mithril_arrow")>count.get("mite:adamantium_arrow"),"Adamantium remains the rarest material");
        }
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=400)
    public static void vanillaVaultTablesAreStillPresentWithOriginalRewards(GameTestHelper helper) {
        var level=helper.getLevel();LootParams params=params(level);
        Set<String> normal=new HashSet<>(),ominous=new HashSet<>();
        for(String suffix:new String[]{"reward_rare","reward_unique","reward_ominous_rare","reward_ominous_unique"}) {
            LootTable table=level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.parse("minecraft:chests/trial_chambers/"+suffix)));
            for(int trial=1;trial<=512;trial++)for(ItemStack stack:table.getRandomItems(params,0x56414e494c4c41L+trial*1_000_037L))
                (suffix.contains("ominous")?ominous:normal).add(id(stack));
        }
        helper.assertTrue(normal.containsAll(Set.of("minecraft:diamond_axe","minecraft:diamond_chestplate","minecraft:music_disc_precipice",
                "minecraft:guster_banner_pattern","minecraft:bolt_armor_trim_smithing_template")),"Original normal rewards were not globally overwritten");
        helper.assertTrue(ominous.containsAll(Set.of("minecraft:diamond_axe","minecraft:diamond_chestplate","minecraft:music_disc_creator",
                "minecraft:flow_banner_pattern","minecraft:flow_armor_trim_smithing_template")),"Original ominous rewards were not globally overwritten");
        helper.succeed();
    }

    private static void legal(GameTestHelper helper,ItemStack stack) {
        String id=id(stack);
        helper.assertTrue(!DIAMOND_GEAR.contains(id),"Diamond equipment was replaced before rolling: "+id);
        helper.assertTrue(!id.contains("music_disc_")&&!id.endsWith("banner_pattern")&&!id.endsWith("smithing_template"),"Removed decorative rewards never reach a player: "+id);
        helper.assertTrue(stack.getCount()>0&&stack.getCount()<=stack.getMaxStackSize(),"Loot respects MITE stack capacity: "+id);
    }
    private static void near(GameTestHelper helper,Map<String,Integer> counts,String id,int samples,double expected) {
        double actual=counts.getOrDefault(id,0)/(double)samples;
        helper.assertTrue(Math.abs(actual-expected)<.03,"Loaded engine probability sanity for "+id+": "+actual+" expected about "+expected);
    }
    private static LootParams params(ServerLevel level){return new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,new Vec3(0,200,0)).create(LootContextParamSets.VAULT);}
    private static String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    private static ResourceKey<LootTable> key(String suffix){return ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse(PREFIX+suffix));}
    private static ServerLevel underworld(GameTestHelper helper){var level=helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);helper.assertTrue(level!=null,"Underworld loaded");return level;}
}
