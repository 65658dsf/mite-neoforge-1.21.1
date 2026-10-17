package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.survival.SourceIceItemMelting;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real biome holders, inventory events and the transformed native ItemEntity tick. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SourceIceItemMeltingGameTests {
    @GameTest(template="ranged_027")
    public static void modernJungleAndDesertResolveToSourceTemperaturesRatherThanModernValues(GameTestHelper helper) {
        try (Fixture f=new Fixture(helper)) {
            ResourceKey<Biome>[] biomes = new ResourceKey[]{Biomes.PLAINS,Biomes.DESERT,Biomes.JUNGLE,Biomes.SAVANNA,Biomes.BEACH,Biomes.SNOWY_PLAINS};
            float[] expected={.8F,1.6F,1.2F,1.6F,1F,0F};
            for(int i=0;i<biomes.length;i++) {
                f.biome(biomes[i]);
                helper.assertValueEqual(SourceIceItemMelting.temperature(f.level,f.pos),expected[i],"Actual biome uses the explicit R196 temperature for "+biomes[i].location());
            }
        }
        helper.succeed();
    }

    @GameTest(template="ranged_027")
    public static void burningPlayerMeltsTheFourSourceMaterialsButPreservesOffhandAndCarriedStacks(GameTestHelper helper) {
        try(Fixture f=new Fixture(helper)) {
            f.biome(Biomes.PLAINS);
            f.player.setRemainingFireTicks(200);
            int seed=seedBelow(.0019F);
            for(var item: new net.minecraft.world.item.Item[]{Items.ICE,Items.SNOW,Items.SNOW_BLOCK,Items.SNOWBALL}) {
                f.player.getInventory().clearContent();
                f.player.getInventory().setItem(0,new ItemStack(item,1));
                f.player.getInventory().setItem(1,new ItemStack(Items.STONE,4));
                f.player.getInventory().setItem(40,new ItemStack(item,1));
                f.player.inventoryMenu.setCarried(new ItemStack(item,1));
                f.player.getRandom().setSeed(seed);
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(f.player));
                helper.assertTrue(f.player.getInventory().getItem(0).isEmpty(),"The actual registered player tick melts a source material in a warm-by-fire inventory");
                helper.assertValueEqual(f.player.getInventory().getItem(1).getCount(),4,"Ordinary materials do not melt");
                helper.assertValueEqual(f.player.getOffhandItem().getCount(),1,"The source main-inventory loop excludes the modern offhand");
                helper.assertValueEqual(f.player.inventoryMenu.getCarried().getCount(),1,"A carried menu stack is not silently included in main inventory");
            }
            f.player.clearFire();
            f.player.getInventory().clearContent();
            f.player.getInventory().setItem(0,new ItemStack(Items.ICE,4));
            for(int tick=0;tick<100;tick++) NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(f.player));
            helper.assertValueEqual(f.player.getInventory().getItem(0).getCount(),4,"A temperate biome does not melt inventory ice");
        }
        helper.succeed();
    }

    @GameTest(template="ranged_027")
    public static void nativeDroppedItemTickMeltsInDesertAndKeepsColdStacksWithoutAPlayer(GameTestHelper helper) {
        try(Fixture f=new Fixture(helper)) {
            f.biome(Biomes.DESERT);
            f.player.moveTo(f.pos.getCenter().add(5D,3D,5D));
            ItemEntity item=new ItemEntity(f.level,f.pos.getX()+.5D,f.pos.getY()+.5D,f.pos.getZ()+.5D,new ItemStack(Items.SNOWBALL));
            // Native infinite pickup delay also disables neighbour merging, so this tick still owns exactly one item.
            item.setNoGravity(true); item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); item.setPickUpDelay(32767);
            f.level.addFreshEntity(item);
            try {
                helper.assertValueEqual(SourceIceItemMelting.temperature(f.level,item.blockPosition()),1.6F,"Actual dropped-item position resolves the source desert temperature");
                helper.assertTrue(f.level.noCollision(item,item.getBoundingBox()),"The native item tick starts in real clear space, without an unsticking RNG draw");
                helper.assertValueEqual(item.getItem().getCount(),1,"The one-item melting test starts with exactly one actual registered item");
                item.getRandom().setSeed(seedBelow(.00006F));
                item.tick();
                helper.assertFalse(item.isAlive(),"The real transformed ItemEntity tick removes the final melted item");
            } finally { item.discard(); }
            f.biome(Biomes.SNOWY_PLAINS);
            ItemEntity cold=new ItemEntity(f.level,f.pos.getX()+.5D,f.pos.getY()+.5D,f.pos.getZ()+.5D,new ItemStack(Items.ICE,4));
            cold.setNoGravity(true);cold.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);cold.setPickUpDelay(32767);f.level.addFreshEntity(cold);
            try {
                for(int tick=0;tick<100;tick++) cold.tick();
                helper.assertValueEqual(cold.getItem().getCount(),4,"Cold dropped ice remains a complete native stack");
            } finally {cold.discard();}
        }
        helper.succeed();
    }
    private static int seedBelow(float chance) {
        for(int seed=0;seed<1000000;seed++) if(RandomSource.create(seed).nextFloat()<chance)return seed;
        throw new IllegalStateException("No deterministic melting sample");
    }
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;final BlockPos pos;final ServerPlayer player;final EmbeddedChannel channel;
        final LevelChunkSection section;final PalettedContainerRO<Holder<Biome>> original;final int qx,qy,qz;
        final Map<BlockPos,BlockState> originalBlocks=new LinkedHashMap<>();
        Fixture(GameTestHelper helper) {
            level=helper.getLevel();BlockPos ref=helper.absolutePos(new BlockPos(3,4,3));
            // Chunk-centre climate sampling stays inside one biome section. Use isolated high air rather than
            // rounding sideways out of the low test template into another test's blocks or residual drops.
            pos=new BlockPos((ref.getX()&~15)+8,((level.getMaxBuildHeight()-24)&~15)+8,(ref.getZ()&~15)+8);
            var chunk=level.getChunkAt(pos);section=chunk.getSection(chunk.getSectionIndex(pos.getY()));original=section.getBiomes();
            qx=QuartPos.fromBlock(chunk.getPos().getMinBlockX());qy=QuartPos.fromBlock(pos.getY())&~3;qz=QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            for(BlockPos cell:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,2,1))) {
                originalBlocks.put(cell.immutable(),level.getBlockState(cell));
                level.setBlock(cell,Blocks.AIR.defaultBlockState(),2);
            }
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mite-ice-source"),false);
            player=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
            Connection connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection,player,cookie);player.setGameMode(GameType.SURVIVAL);
            player.moveTo(pos.getCenter());player.setNoGravity(true);player.getInventory().clearContent();
        }
        void biome(ResourceKey<Biome> key) {
            var holder=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
            section.fillBiomesFromNoise((x,y,z,climate)->holder,level.getChunkSource().randomState().sampler(),qx,qy,qz);
        }
        @Override public void close() {
            try {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);player.getInventory().clearContent();player.clearFire();
                player.server.getPlayerList().remove(player);
                section.fillBiomesFromNoise((x,y,z,climate)->original.get(x&3,y&3,z&3),level.getChunkSource().randomState().sampler(),qx,qy,qz);
                originalBlocks.forEach((cell,state)->level.setBlock(cell,state,2));
            }finally{channel.finishAndReleaseAll();}
        }
    }
}
