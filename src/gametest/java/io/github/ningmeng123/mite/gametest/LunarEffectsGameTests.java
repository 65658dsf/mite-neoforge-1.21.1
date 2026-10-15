package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Wolf;
import io.github.ningmeng123.mite.entity.animal.R196AnimalAi032;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.world.LunarCropEffects;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual level, random-crop and animal integration with synchronous clock restoration. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class LunarEffectsGameTests {
    private static long seed(float ceiling,boolean below) {
        for(long seed=0;seed<100000;seed++) if((new java.util.Random(seed).nextFloat()<ceiling)==below)return seed;
        throw new AssertionError("Independent draw fixture not found");
    }
    private static BlockPos crop(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(2,4,2));
        h.getLevel().setBlock(p.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),2);
        h.getLevel().setBlock(p,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),2);return p;
    }
    @GameTest(template="empty") public static void nativeLevelAndSpawningReadTheSameSourcePhase(GameTestHelper h) {
        long old=h.getLevel().getDayTime();
        try {
            long[] times={0,168000,744000,2856000,3048000};int[] phases={1,0,0,0,0};
            for(int i=0;i<times.length;i++) {
                h.getLevel().setDayTime(times[i]);h.assertValueEqual(h.getLevel().getMoonPhase(),phases[i],"Actual LevelTimeAccess follows the source day-plus-one phase");
                h.assertValueEqual(R196SpawnRules.moon(times[i],true).phase(),phases[i],"Existing spawn calendar agrees with native engine");
            }
            h.getLevel().setDayTime(0);h.assertValueEqual(h.getLevel().getMoonBrightness(),.75F,"Native phase brightness remains a fraction, not enhanced visual moonlight");
            h.getLevel().setDayTime(168000);h.assertValueEqual(h.getLevel().getMoonBrightness(),1F,"Full moon native fraction is exactly one");
        } finally {h.getLevel().setDayTime(old);}h.succeed();
    }
    @GameTest(template="empty") public static void bloodMoonInfectsExposedMatureCropThroughItsRealRandomTick(GameTestHelper h) {
        long old=h.getLevel().getDayTime();BlockPos p=crop(h);
        try {
            h.getLevel().setDayTime(31L*24000+18000);
            h.assertTrue(h.getLevel().getBlockState(p).isRandomlyTicking(),"Source mature crops still receive random ticks");
            h.getLevel().getBlockState(p).randomTick(h.getLevel(),p,RandomSource.create(seed(.25F,true)));
            h.assertTrue(CubeCropBlight.blighted(h.getLevel().getBlockState(p))!=h.getLevel().getServer().isDedicatedServer(),"Actual source mode: integrated-server blood infection, dedicated-server exclusion");
        } finally {h.getLevel().setDayTime(old);}h.succeed();
    }
    @GameTest(template="empty") public static void bloodMoonDrawMissAndOrdinaryOrBlueNightsDoNotInfectHealthyCrops(GameTestHelper h) {
        long old=h.getLevel().getDayTime();BlockPos p=crop(h);
        try {
            for(long time:new long[]{18000,23L*24000+18000,127L*24000+18000,31L*24000+12999,31L*24000+23001}) {
                h.getLevel().setDayTime(time);h.assertFalse(LunarCropEffects.infectOnRandomTick(h.getLevel(),p,h.getLevel().getBlockState(p),RandomSource.create(seed(.25F,true))),"Extra disease requires the exact blood-night window");
            }
            h.getLevel().setDayTime(31L*24000+18000);
            h.assertFalse(LunarCropEffects.infectOnRandomTick(h.getLevel(),p,h.getLevel().getBlockState(p),RandomSource.create(seed(.25F,false))),"An independent missed one-in-four draw leaves a healthy crop");
            h.assertFalse(CubeCropBlight.blighted(h.getLevel().getBlockState(p)),"No false disease state was written");
        } finally {h.getLevel().setDayTime(old);}h.succeed();
    }
    @GameTest(template="empty") public static void solidEnclosureProtectsBloodCropsButGlassDoesNotPretendToBeOpaque(GameTestHelper h) {
        BlockPos p=crop(h);
        Map<BlockPos,BlockState> original=new LinkedHashMap<>();
        try {
            for(BlockPos q:BlockPos.betweenClosed(p.offset(-5,1,-5),p.offset(5,4,5))) {
                original.put(q.immutable(),h.getLevel().getBlockState(q));
                h.getLevel().setBlock(q,Blocks.STONE.defaultBlockState(),2);
            }
            h.assertFalse(LunarCropEffects.exposed(h.getLevel(),p),"Full solid overhead enclosure protects the source crop");
            for(BlockPos q:BlockPos.betweenClosed(p.above(),p.above(4))) h.getLevel().setBlock(q,Blocks.GLASS.defaultBlockState(),2);
            h.assertTrue(LunarCropEffects.exposed(h.getLevel(),p),"The source standard opaque-column test does not treat glass as an opaque shelter");
        } finally { original.forEach((position,state)->h.getLevel().setBlock(position,state,2)); }
        h.succeed();
    }
    @GameTest(template="empty") public static void blueMoonSquidTruceDoesNotExtendIntoTheDaytime(GameTestHelper h) {
        long old=h.getLevel().getDayTime();var cow=EntityType.COW.create(h.getLevel());var squid=EntityType.SQUID.create(h.getLevel());
        try {
            h.getLevel().setDayTime(127L*24000+6000);h.assertTrue(R196AnimalAi032.potentialPredator(cow,squid),"The blue date is not a daytime squid truce");
            h.getLevel().setDayTime(127L*24000+13000);h.assertFalse(R196AnimalAi032.potentialPredator(cow,squid),"Truce begins at source sunset");
            h.getLevel().setDayTime(127L*24000+23000);h.assertFalse(R196AnimalAi032.potentialPredator(cow,squid),"Source sunrise endpoint is still night");
            h.getLevel().setDayTime(127L*24000+23001);h.assertTrue(R196AnimalAi032.potentialPredator(cow,squid),"Animals recognize squid danger again after sunrise");
        } finally {h.getLevel().setDayTime(old);cow.discard();squid.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void blueMoonSuppressesOnlyTheDireWolfsExtraRandomGrowl(GameTestHelper h) throws Exception {
        long old=h.getLevel().getDayTime();var wolf=(R196Wolf)MiteMobs.type(MobKind.DIRE_WOLF).create(h.getLevel());
        Method ambient=R196Wolf.class.getDeclaredMethod("getAmbientSound");ambient.setAccessible(true);
        try {
            wolf.setTame(false,false);wolf.setRemainingPersistentAngerTime(0);
            h.getLevel().setDayTime(127L*24000+6000);wolf.getRandom().setSeed(seed(.1F,true));
            h.assertTrue(ambient.invoke(wolf)==SoundEvents.WOLF_GROWL,"An adult wild dire wolf can use the source ten-percent extra growl during the blue date's daytime");
            h.getLevel().setDayTime(127L*24000+18000);wolf.getRandom().setSeed(seed(.1F,true));
            h.assertTrue(ambient.invoke(wolf)!=SoundEvents.WOLF_GROWL,"Blue night skips that extra random growl without forcing every native sound silent");
        } finally {h.getLevel().setDayTime(old);wolf.discard();}h.succeed();
    }
    @GameTest(template="empty") public static void liveBlightedPotatoesDropExactlyOnePoisonousPotatoAndDeadCropsDropNothing(GameTestHelper h) {
        var level=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(2,4,2));
        boolean old=level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        List<ItemStack> observed=new ArrayList<>();int[] calls={0};
        Consumer<BlockDropsEvent> observe=event->{if(event.getPos().equals(pos)) {
            calls[0]++;observed.clear();event.getDrops().forEach(item->observed.add(item.getItem().copy()));
        }};
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,BlockDropsEvent.class,observe);
        try {
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,level.getServer());
            for(int age:new int[]{0,3,7}) {
                var state=Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE,age).setValue(CubeCropBlight.BLIGHTED,true);
                Block.dropResources(state,level,pos);
                h.assertTrue(observed.size()==1&&observed.get(0).is(Items.POISONOUS_POTATO)&&observed.get(0).getCount()==1,"Actual native drop event replaces any blighted live potato yield with exactly one toxic potato at age "+age);
                Block.dropResources(state.setValue(CubeCropBlight.DEAD,true),level,pos);
                h.assertTrue(observed.isEmpty(),"Dead potatoes never produce a toxic recovery drop");
            }
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false,level.getServer());
            Block.dropResources(Blocks.POTATOES.defaultBlockState().setValue(CubeCropBlight.BLIGHTED,true),level,pos);
            h.assertTrue(observed.isEmpty()&&calls[0]==7,"The replacement still respects native doTileDrops, through all seven actual events");
        } finally {
            NeoForge.EVENT_BUS.unregister(observe);
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(old,level.getServer());
        }h.succeed();
    }
    @GameTest(template="empty") public static void actualExplosionDoesNotRecoverBlightedPotatoOrWheatLoot(GameTestHelper h) {
        BlockPos pos=crop(h);var level=h.getLevel();
        for(var block:List.of(Blocks.WHEAT,Blocks.POTATOES)) for(var interaction:List.of(Explosion.BlockInteraction.DESTROY,Explosion.BlockInteraction.DESTROY_WITH_DECAY)) {
            var state=block.defaultBlockState().setValue(CropBlock.AGE,7).setValue(CubeCropBlight.BLIGHTED,true);
            level.setBlock(pos,state,2);List<ItemStack> drops=new ArrayList<>();
            var explosion=new Explosion(level,null,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,1F,false,interaction);
            state.onExplosionHit(level,pos,explosion,(stack,at)->drops.add(stack));
            h.assertTrue(level.isEmptyBlock(pos)&&drops.isEmpty(),"The actual explosion destroys diseased crops without entering normal toxic-potato harvest: "+block+" "+interaction);
        }h.succeed();
    }
}
