package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.AnvilDurabilityAccess;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual menu listeners pass every update through the native signed-short packet codec. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AnvilDurabilityMenuGameTests {
    @GameTest(template="empty")
    public static void adamantiumDurabilityAndUnsignedWordBoundariesSurviveRealMenuPacketEncoding(GameTestHelper helper) {
        try(Fixture f=new Fixture(helper,MiteFunctionalBlocks.ANVILS.get("adamantium").get())) {
            f.entity().addDamage(12345);
            AnvilMenu client=new AnvilMenu(71,f.owner.getInventory());
            helper.assertValueEqual(view(client).mite$anvilMaximumDurability(),0,"A remote NULL-access menu cannot guess from the player's world");
            Bridge bridge=new Bridge(f.menu,client);
            helper.assertValueEqual(view(f.menu).mite$anvilMaximumDurability(),12697600,"Server reads the full source adamantium maximum immediately");
            helper.assertValueEqual(view(client).mite$anvilMaximumDurability(),12697600,"Native short packets reconstruct the full eight-digit maximum");
            helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),12685255,"Remaining durability above65535 survives actual packet encoding");
            helper.assertTrue(bridge.decoded.stream().anyMatch(packet->packet.getValue()<0),"The real packet codec sign-extends a word whose bit15 is set");

            f.entity().addDamage(12697600-12345-65536);f.menu.broadcastChanges();
            helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),65536,"The high word remains present when the low word becomes zero");
            f.entity().addDamage(1);f.menu.broadcastChanges();
            helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),65535,"A low word decoded as minus1 is restored as unsigned65535");

            int before=f.entity().damage();
            for(var packet:List.copyOf(bridge.decoded)) f.menu.setData(packet.getId(),0);
            helper.assertValueEqual(f.entity().damage(),before,"Applying remote data setters never writes physical block-entity damage");
            helper.assertValueEqual(view(f.menu).mite$anvilRemainingDurability(),65535,"Server getters ignore the remote mirror and resolve the physical anvil");
        }
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void currentBlockEntityIsReadAcrossIronStagesMaterialReplacementAndRemoval(GameTestHelper helper) {
        try(Fixture f=new Fixture(helper,Blocks.ANVIL)) {
            AnvilMenu client=new AnvilMenu(71,f.owner.getInventory());new Bridge(f.menu,client);
            MaterialAnvilBlockEntity pristine=f.entity();
            pristine.addDamage(198400);
            helper.assertTrue(f.levelState().is(Blocks.CHIPPED_ANVIL),"The native iron block really changes identity at50percent wear");
            helper.assertTrue(f.entity()!=pristine,"Stage replacement created a new physical block entity");
            helper.assertValueEqual(view(f.menu).mite$anvilRemainingDurability(),198400,"The menu getter resolves the replacement before another broadcast");
            f.menu.broadcastChanges();
            helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),198400,"The same remote menu receives the replacement's preserved wear");
            f.entity().addDamage(119040);f.menu.broadcastChanges();
            helper.assertTrue(f.levelState().is(Blocks.DAMAGED_ANVIL),"The second native iron stage also replaces the block");
            helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),79360,"Second-stage wear remains exact on the remote menu");

            f.replace(MiteFunctionalBlocks.ANVILS.get("mithril").get());f.menu.broadcastChanges();
            helper.assertValueEqual(view(client).mite$anvilMaximumDurability(),3174400,"Replacing the physical material updates maximum durability on the existing menu");
            helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),3174400,"A new material's pristine state does not inherit the old cached block entity");
            f.owner.level().removeBlock(f.pos,false);
            helper.assertValueEqual(view(f.menu).mite$anvilMaximumDurability(),0,"Removal is visible immediately to the server getter");
            f.menu.broadcastChanges();
            helper.assertValueEqual(view(client).mite$anvilMaximumDurability(),0,"An invalidated physical menu clears the remote maximum");
            helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),0,"An invalidated physical menu clears the remote remaining value");
        }
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void actualRepairPickupUpdatesDurabilityThroughStageChangeAndCompleteBreakage(GameTestHelper helper) {
        for(boolean breaks:new boolean[]{false,true}) try(Fixture f=new Fixture(helper,Blocks.ANVIL)) {
            int startingDamage=breaks?395800:197000;
            f.entity().addDamage(startingDamage);
            AnvilMenu client=new AnvilMenu(71,f.owner.getInventory());Bridge bridge=new Bridge(f.menu,client);
            ItemStack pickaxe=new ItemStack(MiteTools.get("mite:copper_pickaxe"));pickaxe.setDamageValue(3450);
            f.menu.getSlot(0).set(pickaxe);f.menu.getSlot(1).set(new ItemStack(MiteMaterials.COPPER_NUGGET.get(),5));
            helper.assertValueEqual(f.menu.getSlot(2).getItem().getDamageValue(),250,"The actual source repair preview restores3200 tool durability");
            helper.assertTrue(bridge.decoded.stream().anyMatch(packet->packet.getId()==1&&packet.getValue()==9),
                    "The real repair listener preserves the established source-mode data index1 and four-nugget consumed count");
            f.menu.clicked(2,0,ClickType.PICKUP,f.owner);
            helper.assertValueEqual(f.menu.getCarried().getDamageValue(),250,"A real result-slot transaction completes the repair");
            f.menu.broadcastChanges();
            if(breaks) {
                helper.assertTrue(f.levelState().isAir(),"Real repair wear beyond the remaining1000 removes the anvil");
                helper.assertValueEqual(view(f.menu).mite$anvilMaximumDurability(),0,"A physically broken anvil immediately loses its maximum");
                helper.assertValueEqual(view(client).mite$anvilMaximumDurability(),0,"The remote repair menu receives the broken-anvil state");
                helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),0,"No stale durability remains after a real repair breaks the block");
            } else {
                helper.assertTrue(f.levelState().is(Blocks.CHIPPED_ANVIL),"The real repair crosses the native iron appearance threshold");
                helper.assertValueEqual(f.entity().damage(),200200,"Real repair contributes exactly3200 anvil wear");
                helper.assertValueEqual(view(f.menu).mite$anvilRemainingDurability(),196600,"Server menu reads the actual replacement entity's repaired wear");
                helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),196600,"Native synchronization publishes the final repair wear to a NULL-access client view");
            }
        }
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void absentPhysicalEntityAndUnsynchronizedRemoteMenuExposeZeroDurability(GameTestHelper helper) {
        try(Fixture f=new Fixture(helper,Blocks.AIR)) {
            AnvilMenu client=new AnvilMenu(71,f.owner.getInventory());new Bridge(f.menu,client);
            helper.assertValueEqual(view(f.menu).mite$anvilMaximumDurability(),0,"A physically empty menu has no displayable durability maximum");
            helper.assertValueEqual(view(client).mite$anvilMaximumDurability(),0,"An empty remote menu also has no displayable durability maximum");
            f.replace(Blocks.ANVIL);f.menu.broadcastChanges();
            helper.assertValueEqual(view(client).mite$anvilMaximumDurability(),396800,"The same menu can observe a newly placed valid anvil");
            f.owner.level().removeBlockEntity(f.pos);f.menu.broadcastChanges();
            helper.assertTrue(f.levelState().is(Blocks.ANVIL),"The missing-entity control retains the anvil block state");
            helper.assertValueEqual(view(f.menu).mite$anvilMaximumDurability(),0,"A block state alone cannot invent missing exact wear data");
            helper.assertValueEqual(view(client).mite$anvilRemainingDurability(),0,"A missing block entity also clears the remote remaining value");
            helper.assertValueEqual(view(client).mite$anvilMaximumDurability(),0,"A missing block entity clears the remote display qualification");
        }
        helper.succeed();
    }

    private static AnvilDurabilityAccess view(AnvilMenu menu) { return (AnvilDurabilityAccess)menu; }
    private static final class Bridge implements ContainerListener {
        final AnvilMenu client;final List<ClientboundContainerSetDataPacket> decoded=new ArrayList<>();
        Bridge(AnvilMenu server,AnvilMenu client) { this.client=client;server.addSlotListener(this); }
        @Override public void slotChanged(AbstractContainerMenu menu,int slot,ItemStack stack) {}
        @Override public void dataChanged(AbstractContainerMenu menu,int index,int value) {
            FriendlyByteBuf bytes=new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundContainerSetDataPacket.STREAM_CODEC.encode(bytes,new ClientboundContainerSetDataPacket(menu.containerId,index,value));
                var packet=ClientboundContainerSetDataPacket.STREAM_CODEC.decode(bytes);decoded.add(packet);
                client.setData(packet.getId(),packet.getValue());
            } finally {bytes.release();}
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;final Player owner;final BlockPos pos;final AnvilMenu menu;
        final BlockState original,below;
        Fixture(GameTestHelper helper,Block block) {
            this.helper=helper;pos=helper.absolutePos(new BlockPos(1,1,1));
            original=helper.getLevel().getBlockState(pos);below=helper.getLevel().getBlockState(pos.below());
            helper.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);replace(block);
            owner=helper.makeMockPlayer(GameType.SURVIVAL);owner.moveTo(pos.getCenter());PlayerExperience.setTotal(owner,0);
            menu=new AnvilMenu(71,owner.getInventory(),ContainerLevelAccess.create(helper.getLevel(),pos));
        }
        void replace(Block block) { helper.getLevel().setBlock(pos,block.defaultBlockState(),3); }
        MaterialAnvilBlockEntity entity() { return (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos); }
        BlockState levelState() { return helper.getLevel().getBlockState(pos); }
        @Override public void close() {
            menu.setCarried(ItemStack.EMPTY);menu.getSlot(0).set(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);owner.getInventory().clearContent();
            helper.getLevel().setBlock(pos,original,3);helper.getLevel().setBlock(pos.below(),below,3);
        }
    }
}
