package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.OnionCropBlock;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real registered manure interactions cure living disease without altering crop ages. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ManureCropTreatmentGameTests {
    private ManureCropTreatmentGameTests() {}

    @GameTest(template="empty")
    public static void everyFoodCropCanBeTreatedThroughCropOrSoilEvenWhenAlreadyFertilized(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            for(CropBlock crop:crops())for(boolean fertilized:new boolean[]{false,true})for(boolean soilClick:new boolean[]{false,true}) {
                BlockState diseased=blighted(crop.getStateForAge(2));
                f.set(f.pos.below(),soil(fertilized)); f.set(f.pos,diseased);
                Player player=f.player(GameType.SURVIVAL);
                var event=f.use(player,soilClick?f.pos.below():f.pos,false,false);
                BlockState after=f.level().getBlockState(f.pos);
                h.assertTrue(event.isCanceled()&&event.getCancellationResult().consumesAction(),"The installed native right-click handler accepts a real living crop treatment");
                h.assertFalse(CubeCropBlight.blighted(after)||CubeCropBlight.dead(after),"Manure cures the saved living disease flag for every food species");
                h.assertValueEqual(crop.getAge(after),2,"Treatment adds no age and cannot instantly mature the crop");
                h.assertTrue(FoodAgriculture.fertilized(f.level().getBlockState(f.pos.below())),"Treatment also retains or applies the existing soil fertility");
                h.assertValueEqual(player.getMainHandItem().getCount(),1,"Cure and soil improvement together consume exactly one manure");
                f.use(player,soilClick?f.pos.below():f.pos,false,false);
                h.assertValueEqual(player.getMainHandItem().getCount(),1,"A healthy crop over already fertile soil cannot consume another manure");
            }
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void manureCannotReviveDeadCropsAndCreativeTreatmentDoesNotConsumeIt(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            for(CropBlock crop:crops()) {
            BlockState dead=blighted(crop.getStateForAge(2)).setValue(crop instanceof OnionCropBlock?OnionCropBlock.DEAD:CubeCropBlight.DEAD,true);
            f.set(f.pos.below(),soil(true)); f.set(f.pos,dead);
            Player player=f.player(GameType.SURVIVAL);
            f.use(player,f.pos,false,false);
            h.assertValueEqual(f.level().getBlockState(f.pos),dead,"Manure cannot revive or change a dead crop");
            h.assertValueEqual(player.getMainHandItem().getCount(),2,"Dead crops over fertile soil consume no manure");
            f.set(f.pos,blighted(crop.getStateForAge(2)));
            Player creative=f.player(GameType.CREATIVE);
            f.use(creative,f.pos,false,false);
            h.assertFalse(CubeCropBlight.blighted(f.level().getBlockState(f.pos)),"Creative mode still performs a real cure");
            h.assertValueEqual(creative.getMainHandItem().getCount(),2,"Creative treatment keeps the original native consumption exemption");
            }
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void protectedCancelledCooldownAndSpectatorTreatmentsCannotModifyOrConsume(GameTestHelper h) {
        try(Fixture f=new Fixture(h)) {
            for(int denied=0;denied<5;denied++) {
            BlockState diseased=blighted(((CropBlock)Blocks.WHEAT).getStateForAge(2));
            f.set(f.pos.below(),soil(true));f.set(f.pos,diseased);
            Player player=f.player(denied==0?GameType.SPECTATOR:denied==4?GameType.ADVENTURE:GameType.SURVIVAL);
            if(denied==1)player.getCooldowns().addCooldown(MiteFoods.get("manure"),20);
            if(denied==4)player.getAbilities().mayBuild=false;
            f.use(player,f.pos,denied==2,denied==3);
            h.assertValueEqual(f.level().getBlockState(f.pos),diseased,"A rejected interaction cannot clear the disease flag");
            h.assertValueEqual(player.getMainHandItem().getCount(),2,"A rejected interaction cannot charge manure");
            }
        }
        h.succeed();
    }

    private static CropBlock[] crops(){return new CropBlock[]{(CropBlock)Blocks.WHEAT,(CropBlock)Blocks.CARROTS,(CropBlock)Blocks.POTATOES,(CropBlock)Blocks.BEETROOTS,MiteFoodPlants.ONIONS.get()};}
    private static BlockState soil(boolean fertile){return Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7).setValue(FoodAgriculture.FERTILIZED,fertile);}
    private static BlockState blighted(BlockState state){return state.setValue(state.getBlock() instanceof OnionCropBlock?OnionCropBlock.BLIGHTED:CubeCropBlight.BLIGHTED,true);}
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper; final BlockPos pos; final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
        Fixture(GameTestHelper helper){this.helper=helper;pos=helper.absolutePos(new BlockPos(2,3,2));set(pos.above(),Blocks.AIR.defaultBlockState());}
        net.minecraft.server.level.ServerLevel level(){return helper.getLevel();}
        void set(BlockPos p,BlockState state){originals.putIfAbsent(p.immutable(),level().getBlockState(p));level().setBlock(p,state,2);}
        Player player(GameType mode){
            Player player=helper.makeMockPlayer(mode);
            // makeMockPlayer overrides isCreative/isSpectator only. Native ItemStack.consume
            // checks hasInfiniteMaterials (Abilities.instabuild), so apply real GameType abilities.
            mode.updatePlayerAbilities(player.getAbilities());
            helper.assertTrue(player.hasInfiniteMaterials()==mode.isCreative(),"The interaction fixture uses the native mode's material-consumption abilities");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MiteFoods.get("manure"),2));
            return player;
        }
        PlayerInteractEvent.RightClickBlock use(Player player,BlockPos clicked,boolean deny,boolean cancel){
            var hit=new BlockHitResult(clicked.getCenter(),Direction.UP,clicked,false);
            var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,clicked,hit);
            if(deny)event.setUseItem(TriState.FALSE);if(cancel)event.setCanceled(true);
            NeoForge.EVENT_BUS.post(event);return event;
        }
        @Override public void close(){originals.forEach((p,state)->level().setBlock(p,state,2));}
    }
}
