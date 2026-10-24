package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.phantom.PhantomBladeRecipe;
import io.github.ningmeng123.mite.registry.MiteCreativeTabs;
import io.github.ningmeng123.mite.registry.MiteMetalBlocks;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.PhantomBladeRules;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Loaded recipes and the real workbench transaction consume all nine pictured ingredients once. @author NingMeng123 */
@GameTestHolder("mite_phantom")
@PrefixGameTestTemplate(false)
public final class PhantomBladeRecipeGameTests {
    @GameTest(template="empty")
    public static void allMaterialsUseEightSwordsAndTheExactBlockOrPreviousCore(GameTestHelper h) {
        String[] targets={"copper","silver","gold","rusted_iron","iron","iron","ancient_metal","mithril","mithril","adamantium","vibranium"};
        Item[] centers={Items.COPPER_BLOCK,MiteMetalBlocks.STORAGE.get("silver").get().asItem(),Items.GOLD_BLOCK,Items.IRON_BLOCK,
                MitePhantomBlades.get("copper"),MitePhantomBlades.get("silver"),MitePhantomBlades.get("iron"),
                MitePhantomBlades.get("iron"),MitePhantomBlades.get("ancient_metal"),MitePhantomBlades.get("mithril"),MitePhantomBlades.get("adamantium")};
        for(int i=0;i<targets.length;i++) {
            var recipe=recipe(h,targets[i]); var grid=ring(targets[i],new ItemStack(centers[i]));
            h.assertTrue(recipe.matches(grid,h.getLevel()),"The pictured ring accepts its preceding stage: "+targets[i]);
            ItemStack result=recipe.assemble(grid,h.getLevel().registryAccess());
            h.assertTrue(result.is(MitePhantomBlades.get(targets[i]))&&result.getCount()==1,"The recipe produces exactly one target core");
            for(int missing=0;missing<9;missing++) {
                var incomplete=new ArrayList<ItemStack>();for(int slot=0;slot<9;slot++)incomplete.add(slot==missing?ItemStack.EMPTY:grid.getItem(slot));
                h.assertFalse(recipe.matches(CraftingInput.of(3,3,incomplete),h.getLevel()),"No sword or central ingredient can be omitted");
            }
            h.assertTrue(TimedCraftingRecipes.get(ResourceLocation.parse("mite:"+targets[i]+"_phantom_blade"))!=null,"Each formation has a real manufacturing rule");
        }
        h.assertFalse(recipe(h,"iron").matches(ring("iron",new ItemStack(Items.COPPER_BLOCK)),h.getLevel()),"Iron requires the preceding core, not another copper block");
        h.assertFalse(recipe(h,"vibranium").matches(ring("vibranium",new ItemStack(MitePhantomBlades.get("iron"))),h.getLevel()),"The final stage cannot skip mithril and adamantium cores");
        for(var profile:PhantomBladeRules.profiles()) {
            ItemStack core=new ItemStack(MitePhantomBlades.get(profile.material()));
            h.assertValueEqual(core.getMaxStackSize(),1,"Passive cores are nonstackable");
            h.assertTrue(MiteCreativeTabs.group(core.getItem())==MiteCreativeTabs.Group.COMBAT,"Every material appears in the combat creative tab");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void upgradesKeepTheCentralNameAndTargetDefaultsWithoutCopyingSacrificedSwordData(GameTestHelper h) {
        ItemStack center=new ItemStack(MitePhantomBlades.get("adamantium"));center.set(DataComponents.CUSTOM_NAME,Component.literal("NingMeng123"));
        center.set(DataComponents.MAX_STACK_SIZE,64);
        var grid=ring("vibranium",center);
        grid.getItem(0).set(DataComponents.CUSTOM_NAME,Component.literal("Sacrificed Sword"));
        ItemStack result=recipe(h,"vibranium").assemble(grid,h.getLevel().registryAccess());
        h.assertValueEqual(result.getHoverName().getString(),"NingMeng123","Only the old central core supplies personal data");
        h.assertTrue(result.getMaxStackSize()==1&&result.has(DataComponents.FIRE_RESISTANT),"The new material retains its own fire resistance and stack limit");
        var restored=ItemStack.parse(h.getLevel().registryAccess(),result.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(ItemStack.isSameItemSameComponents(result,restored),"The real upgraded item survives NBT save/reload");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void aRealWorkbenchUpgradeCannotSkipItsBenchOrClockAndConsumesEightIronSwords(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,2,2));
        try(Session session=new Session(h)) {
            ServerPlayer player=session.player;player.moveTo(pos.getCenter().add(0,1,1));
            h.getLevel().setBlockAndUpdate(pos,MiteWorkbenches.TABLES.get("copper").get().defaultBlockState());
            CraftingMenu menu=new CraftingMenu(1,player.getInventory(),ContainerLevelAccess.create(h.getLevel(),pos));player.containerMenu=menu;
            var input=ring("iron",new ItemStack(MitePhantomBlades.get("copper")));
            for(int slot=0;slot<9;slot++)menu.getSlot(slot+1).set(input.getItem(slot).copy());
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(),"Copper equipment cannot manufacture an iron formation");
            h.getLevel().setBlockAndUpdate(pos,MiteWorkbenches.TABLES.get("iron").get().defaultBlockState());menu.slotsChanged(menu.getSlot(1).container);
            h.assertTrue(menu.getSlot(0).getItem().is(MitePhantomBlades.get("iron")),"An iron workbench computes the real upgrade output");
            h.assertTrue(menu.quickMoveStack(player,0).isEmpty(),"Direct quick movement cannot bypass the manufacturing timer");
            TimedCraftingTestSupport.craftOne(h,player,menu,ClickType.PICKUP);
            h.assertValueEqual(player.getInventory().countItem(MitePhantomBlades.get("iron")),1,"Exactly one completed core reaches inventory");
            for(int slot=1;slot<=9;slot++)h.assertTrue(menu.getSlot(slot).getItem().isEmpty(),"All eight complete swords and the old core are consumed once");
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(),"The exhausted recipe leaves no duplicate output");
        } finally {h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());}
        h.succeed();
    }
    private static CraftingInput ring(String material,ItemStack center) {
        List<ItemStack> grid=new ArrayList<>();for(int slot=0;slot<9;slot++)grid.add(slot==4?center:new ItemStack(MitePhantomBlades.sword(material)));
        return CraftingInput.of(3,3,grid);
    }
    private static PhantomBladeRecipe recipe(GameTestHelper h,String material) {
        return (PhantomBladeRecipe)h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("mite:"+material+"_phantom_blade")).orElseThrow().value();
    }
    private static final class Session implements AutoCloseable {
        final ServerPlayer player;final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mite-phantom-recipe"),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);player.setInvulnerable(true);player.getInventory().clearContent();
        }
        public void close(){TimedCraftingController.cancel(player);player.server.getPlayerList().remove(player);channel.finishAndReleaseAll();}
    }
}
