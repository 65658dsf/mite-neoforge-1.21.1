package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.UnderworldDungeonSpawns;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real caged spawns, saved identities and mode isolation. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UnderworldSpawnerGameTests {
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void ordinarySpawnerRewritesBothWeightedAndCurrentDataWithoutLosingAuthoredRules(GameTestHelper helper) {
        var level = underworld(helper);
        BlockPos pos = site(0);
        CompoundTag tag = baseTag("minecraft:zombie");
        var current = tag.getCompound("SpawnData");
        current.getCompound("entity").putString("CustomName", "\"地底地牢\"");
        CompoundTag rules = new CompoundTag();
        CompoundTag light = new CompoundTag(); light.putInt("min_inclusive", 0); light.putInt("max_inclusive", 14);
        rules.put("block_light_limit", light); current.put("custom_spawn_rules", rules);
        ListTag potentials = new ListTag();
        potentials.add(weighted("mite:bone_lord", 7));
        potentials.add(weighted("mite:ancient_bone_lord", 3));
        tag.put("SpawnPotentials", potentials);
        BaseSpawner first = base(); first.load(level, pos, tag);
        var saved = first.save(new CompoundTag());
        helper.assertValueEqual(id(saved.getCompound("SpawnData")), "mite:revenant", "Current dungeon zombie becomes revenant");
        helper.assertValueEqual(saved.getCompound("SpawnData").getCompound("entity").getString("CustomName"), "\"地底地牢\"", "Authored name survives");
        helper.assertValueEqual(SpawnData.CODEC.parse(NbtOps.INSTANCE,saved.getCompound("SpawnData")).getOrThrow()
                .getCustomSpawnRules().orElseThrow().blockLightLimit(),new InclusiveRange<>(0,14),"Custom light limits survive codec canonicalization");
        var savedPool = saved.getList("SpawnPotentials", 10);
        helper.assertValueEqual(savedPool.size(), 2, "Both potential entries survive");
        helper.assertValueEqual(savedPool.getCompound(0).getInt("weight"), 7, "First weight survives");
        helper.assertValueEqual(savedPool.getCompound(1).getInt("weight"), 3, "Second weight survives");
        for (int i=0;i<savedPool.size();i++) helper.assertValueEqual(id(savedPool.getCompound(i).getCompound("data")), "mite:longdead_guardian", "Neither lord can remain in potential pool");
        BaseSpawner restored = base(); restored.load(level, pos, saved);
        helper.assertTrue(saved.equals(restored.save(new CompoundTag())), "Ordinary spawner survives save/reload without another rewrite");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void ordinarySpawnerActuallyCreatesAndFinalizesTheReplacement(GameTestHelper helper) {
        ServerLevel level = underworld(helper); BlockPos pos = site(1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try (var equipmentDate = new SourceEquipmentDateFixture(helper);
                Room room = new Room(level, pos, false); SpawnedEntityCapture capture = new SpawnedEntityCapture(level, entity -> entity instanceof Mob && entity.blockPosition().distSqr(pos)<100)) {
            player.teleportTo(level, pos.getX()+3, pos.getY(), pos.getZ(), 0, 0);
            for (int attempt=0;attempt<12 && capture.live(Mob.class).isEmpty();attempt++) {
                BaseSpawner spawner=base();spawner.load(level,pos,baseTag("minecraft:zombie"));spawner.serverTick(level,pos);
            }
            var spawned=capture.live(Mob.class);
            helper.assertTrue(!spawned.isEmpty(), "Real underworld dungeon cycle must add a mob");
            for (Mob mob:spawned) {
                helper.assertTrue(mob.getType()==MiteMobs.type(MobKind.REVENANT), "No ordinary zombie is added");
                helper.assertTrue(mob.getSpawnType()==MobSpawnType.SPAWNER, "Id-only data runs the normal equipment finalization");
                helper.assertTrue(!mob.getMainHandItem().isEmpty(), "Revenant source weapon was initialized");
            }
        } finally { level.getServer().getPlayerList().remove(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void trialDataKeepsBothModesEquipmentRulesRewardsAndCountersAcrossReload(GameTestHelper helper) {
        ServerLevel level=underworld(helper);BlockPos pos=site(2);
        var blockEntity=new TrialSpawnerBlockEntity(pos,Blocks.TRIAL_SPAWNER.defaultBlockState());blockEntity.setLevel(level);
        CompoundTag input=trialTag("minecraft:skeleton","mite:ancient_bone_lord",null);
        input.putInt("total_mobs_spawned",2);input.putLong("cooldown_ends_at",98765L);input.putLong("next_mob_spawns_at",12345L);
        input.putInt("target_cooldown_length",2400);input.putInt("required_player_range",12);
        var normal=input.getCompound("normal_config");normal.putFloat("total_mobs",9);normal.putFloat("simultaneous_mobs",3);normal.putInt("ticks_between_spawn",77);
        var configuredData=normal.getList("spawn_potentials",10).getCompound(0).getCompound("data");
        var equipment=new CompoundTag();equipment.putString("loot_table","minecraft:equipment/trial_chamber");equipment.putFloat("slot_drop_chances",0.25F);configuredData.put("equipment",equipment);
        var customRules=new CompoundTag();var limit=new CompoundTag();limit.putInt("min_inclusive",0);limit.putInt("max_inclusive",14);customRules.put("block_light_limit",limit);configuredData.put("custom_spawn_rules",customRules);
        blockEntity.loadWithComponents(input,level.registryAccess());
        CompoundTag saved=blockEntity.saveWithoutMetadata(level.registryAccess());
        helper.assertValueEqual(poolId(saved,"normal_config"),"mite:longdead","Normal config is rewritten");
        helper.assertValueEqual(poolId(saved,"ominous_config"),"mite:longdead_guardian","Ominous lord is downgraded");
        var actualData=saved.getCompound("normal_config").getList("spawn_potentials",10).getCompound(0).getCompound("data");
        helper.assertTrue(actualData.getCompound("equipment").equals(equipment),"Equipment table and drop chance remain intact");
        helper.assertValueEqual(SpawnData.CODEC.parse(NbtOps.INSTANCE,actualData).getOrThrow()
                .getCustomSpawnRules().orElseThrow().blockLightLimit(),new InclusiveRange<>(0,14),"Trial-specific authored light range remains intact");
        helper.assertValueEqual(saved.getInt("total_mobs_spawned"),2,"Round counter preserved");
        helper.assertTrue(saved.getLong("cooldown_ends_at")==98765L&&saved.getLong("next_mob_spawns_at")==12345L,"Cooldown and schedule preserved");
        helper.assertValueEqual(saved.getInt("target_cooldown_length"),2400,"Configured cooldown preserved");
        helper.assertValueEqual(saved.getCompound("normal_config").getInt("ticks_between_spawn"),77,"Spawn interval preserved");
        var restored=new TrialSpawnerBlockEntity(pos,Blocks.TRIAL_SPAWNER.defaultBlockState());restored.setLevel(level);restored.loadWithComponents(saved,level.registryAccess());
        helper.assertTrue(saved.equals(restored.saveWithoutMetadata(level.registryAccess())),"Two-mode data is stable across a real BE round trip");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void litCopperTrialFloorSpawnsAllAdaptedRolesIncludingOozeAndSilverfish(GameTestHelper helper) {
        ServerLevel level=underworld(helper);BlockPos pos=site(3);Room room=new Room(level,pos,true);
        helper.runAtTickTime(399,room::close);
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(level.getMaxLocalRawBrightness(pos.east(2))>=8,
                "Lit room waits for real asynchronous chunk lighting")).thenExecute(()->{
            try (room;SpawnedEntityCapture capture=new SpawnedEntityCapture(level,e->e instanceof Mob&&e.blockPosition().distSqr(pos)<100)) {
                BlockPos spawn=pos.east(2);
                helper.assertTrue(level.getMaxLocalRawBrightness(spawn)>=8,"Fixture is truly lit, not an accidental dark natural-spawn test");
                Map<String,String> expected=Map.of("minecraft:zombie","mite:revenant","minecraft:skeleton","mite:longdead","minecraft:bogged","mite:longdead_guardian","minecraft:spider","mite:demon_spider","minecraft:cave_spider","mite:phase_spider","minecraft:slime","mite:ooze","minecraft:silverfish","mite:hoary_silverfish");
                for (var row:expected.entrySet()) {
                    TrialSpawnerBlockEntity be=trial(level,pos,trialTag(row.getKey(),row.getKey(),spawn));
                    var result=be.getTrialSpawner().spawnMob(level,pos);
                    helper.assertTrue(result.isPresent(),"Lit copper trial accepts "+row.getKey());
                    Mob mob=capture.live(Mob.class).stream().filter(e->e.getUUID().equals(result.orElseThrow())).findFirst().orElseThrow();
                    helper.assertValueEqual(EntityType.getKey(mob.getType()).toString(),row.getValue(),"Returned UUID belongs to the actual replacement");
                    helper.assertTrue(mob.isPersistenceRequired(),"Trial mob stays persistent for wave tracking");mob.discard();
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void ominousTrialCannotSpawnALordFromCurrentOrConfiguredData(GameTestHelper helper) {
        ServerLevel level=underworld(helper);BlockPos pos=site(4);
        try (Room room=new Room(level,pos,false);SpawnedEntityCapture capture=new SpawnedEntityCapture(level,e->e instanceof Mob&&e.blockPosition().distSqr(pos)<100)) {
            var be=trial(level,pos,trialTag("mite:bone_lord","mite:ancient_bone_lord",pos.east(2)));
            be.getTrialSpawner().applyOminous(level,pos);
            Optional<java.util.UUID> result=Optional.empty();
            for(int i=0;i<20&&result.isEmpty();i++)result=be.getTrialSpawner().spawnMob(level,pos);
            helper.assertTrue(result.isPresent(),"Ominous mode still produces its downgraded enemy");
            for (Mob mob:capture.live(Mob.class))helper.assertTrue(mob.getType()==MiteMobs.type(MobKind.LONGDEAD_GUARDIAN),"Ominous mode never adds either lord");
            var saved=be.saveWithoutMetadata(level.registryAccess());
            helper.assertValueEqual(poolId(saved,"normal_config"),"mite:longdead_guardian","Normal lord removed too");
            helper.assertValueEqual(poolId(saved,"ominous_config"),"mite:longdead_guardian","Ominous lord stays removed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void trialCustomLightRulesStillRejectOtherwiseAllowedReplacement(GameTestHelper helper) {
        ServerLevel level=underworld(helper);BlockPos pos=site(5);
        try(Room room=new Room(level,pos,false);SpawnedEntityCapture capture=new SpawnedEntityCapture(level,e->e instanceof Mob&&e.blockPosition().distSqr(pos)<100)) {
            CompoundTag tag=trialTag("minecraft:zombie","minecraft:zombie",pos.east(2));
            var rules=new CompoundTag();var light=new CompoundTag();light.putInt("min_inclusive",15);light.putInt("max_inclusive",15);rules.put("sky_light_limit",light);
            tag.getCompound("spawn_data").put("custom_spawn_rules",rules);
            var be=trial(level,pos,tag);
            for(int i=0;i<8;i++) helper.assertTrue(be.getTrialSpawner().spawnMob(level,pos).isEmpty(),"Custom sky light15 rejects a skyless dimension");
            helper.assertTrue(capture.live(Mob.class).isEmpty(),"No entity was added despite custom-rule rejection");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cagedLordsCannotBypassExclusionAsPassengers(GameTestHelper helper) {
        ServerLevel level=underworld(helper);BlockPos pos=site(6);
        CompoundTag tag=baseTag("minecraft:breeze");
        ListTag passengers=new ListTag();
        CompoundTag lord=new CompoundTag();lord.putString("id","mite:bone_lord");lord.putString("CustomName","\"乘客\"");
        ListTag nested=new ListTag();CompoundTag ancient=new CompoundTag();ancient.putString("id","mite:ancient_bone_lord");nested.add(ancient);lord.put("Passengers",nested);passengers.add(lord);
        tag.getCompound("SpawnData").getCompound("entity").put("Passengers",passengers);
        BaseSpawner spawner=base();spawner.load(level,pos,tag);
        CompoundTag entity=spawner.save(new CompoundTag()).getCompound("SpawnData").getCompound("entity");
        helper.assertValueEqual(entity.getString("id"),"minecraft:breeze","Unmapped vehicle remains a breeze");
        CompoundTag mapped=entity.getList("Passengers",10).getCompound(0);
        helper.assertValueEqual(mapped.getString("id"),"mite:longdead_guardian","Lord rider is excluded");
        helper.assertValueEqual(mapped.getList("Passengers",10).getCompound(0).getString("id"),"mite:longdead_guardian","Nested ancient lord rider is excluded too");
        helper.assertValueEqual(mapped.getString("CustomName"),"\"乘客\"","Passenger authored NBT survives");
        helper.assertValueEqual(lord.getString("id"),"mite:bone_lord","Shared input NBT was never mutated");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overworldCagedEntitiesKeepTheirVanillaIdentities(GameTestHelper helper) {
        var level=helper.getLevel();BlockPos pos=helper.absolutePos(new BlockPos(1,1,1));
        BaseSpawner first=base();first.load(level,pos,baseTag("minecraft:zombie"));
        helper.assertValueEqual(id(first.save(new CompoundTag()).getCompound("SpawnData")),"minecraft:zombie","Overworld ordinary spawner remains unchanged");
        var be=new TrialSpawnerBlockEntity(pos,Blocks.TRIAL_SPAWNER.defaultBlockState());be.setLevel(level);be.loadWithComponents(trialTag("minecraft:skeleton","minecraft:bogged",null),level.registryAccess());
        var saved=be.saveWithoutMetadata(level.registryAccess());
        helper.assertValueEqual(poolId(saved,"normal_config"),"minecraft:skeleton","Overworld normal trial unchanged");
        helper.assertValueEqual(poolId(saved,"ominous_config"),"minecraft:bogged","Overworld ominous trial unchanged");
        helper.succeed();
    }

    private static BaseSpawner base() { return new BaseSpawner(){@Override public void broadcastEvent(Level level,BlockPos pos,int event){}}; }
    private static CompoundTag data(String id) { CompoundTag entity=new CompoundTag();entity.putString("id",id);return (CompoundTag)SpawnData.CODEC.encodeStart(NbtOps.INSTANCE,new SpawnData(entity,Optional.empty(),Optional.empty())).getOrThrow(); }
    private static CompoundTag weighted(String id,int weight) { CompoundTag entry=new CompoundTag();entry.putInt("weight",weight);entry.put("data",data(id));return entry; }
    private static CompoundTag baseTag(String id) {
        CompoundTag tag=new CompoundTag();tag.putBoolean(UnderworldDungeonSpawns.CONFIGURED_TAG,true);tag.putShort("Delay",(short)0);tag.putShort("MinSpawnDelay",(short)200);tag.putShort("MaxSpawnDelay",(short)800);tag.putShort("SpawnCount",(short)1);tag.putShort("SpawnRange",(short)1);tag.put("SpawnData",data(id));return tag;
    }
    private static CompoundTag trialTag(String normal,String ominous,BlockPos spawn) {
        CompoundTag tag=new CompoundTag();
        for(String mode:new String[]{"normal_config","ominous_config"}) {
            CompoundTag config=new CompoundTag();ListTag pool=new ListTag();pool.add(weighted(mode.equals("normal_config")?normal:ominous,5));config.put("spawn_potentials",pool);tag.put(mode,config);
        }
        if(spawn!=null){CompoundTag next=data(normal);ListTag coordinates=new ListTag();coordinates.add(DoubleTag.valueOf(spawn.getX()+.5));coordinates.add(DoubleTag.valueOf(spawn.getY()));coordinates.add(DoubleTag.valueOf(spawn.getZ()+.5));next.getCompound("entity").put("Pos",coordinates);tag.put("spawn_data",next);}
        return tag;
    }
    private static String id(CompoundTag data){return data.getCompound("entity").getString("id");}
    private static String poolId(CompoundTag tag,String mode){
        // Vanilla omits an identical ominous config; its effective value inherits the normal one.
        CompoundTag config=tag.getCompound(mode);
        if(mode.equals("ominous_config")&&!config.contains("spawn_potentials"))config=tag.getCompound("normal_config");
        return id(config.getList("spawn_potentials",10).getCompound(0).getCompound("data"));
    }
    private static BlockPos site(int index){return new BlockPos(-9200+index*32,180,9200);}
    private static ServerLevel underworld(GameTestHelper helper){var level=helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);helper.assertTrue(level!=null,"Underworld loaded");return level;}
    private static TrialSpawnerBlockEntity trial(ServerLevel level,BlockPos pos,CompoundTag tag){
        level.setBlock(pos,Blocks.TRIAL_SPAWNER.defaultBlockState(),2);var be=(TrialSpawnerBlockEntity)level.getBlockEntity(pos);be.loadWithComponents(tag,level.registryAccess());return be;
    }
    private static final class Room implements AutoCloseable {
        private final ServerLevel level;private final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();
        private final Set<Long> addedTickets=new HashSet<>();private boolean closed;
        private Room(ServerLevel level,BlockPos center,boolean lit){
            this.level=level;
            // The real light engine only retains light for loaded chunks. These rooms can straddle
            // four chunks, so pin all of them rather than trusting getBlockState's short-lived ticket.
            for(int x=Math.floorDiv(center.getX()-6,16);x<=Math.floorDiv(center.getX()+6,16);x++)
                for(int z=Math.floorDiv(center.getZ()-6,16);z<=Math.floorDiv(center.getZ()+6,16);z++){
                    long chunk=ChunkPos.asLong(x,z);
                    if(!level.getForcedChunks().contains(chunk)){level.setChunkForced(x,z,true);addedTickets.add(chunk);}
                }
            for(BlockPos p:BlockPos.betweenClosed(center.offset(-6,-1,-6),center.offset(6,5,6))){
                previous.put(p.immutable(),level.getBlockState(p));
                boolean shell=p.getY()==center.getY()-1||p.getY()==center.getY()+5||Math.abs(p.getX()-center.getX())==6||Math.abs(p.getZ()-center.getZ())==6;
                level.setBlock(p,(shell?(lit?Blocks.CUT_COPPER:Blocks.STONE):Blocks.AIR).defaultBlockState(),2);
            }
            if(lit) for(int x=-3;x<=3;x+=3)for(int z=-3;z<=3;z+=3)level.setBlock(center.offset(x,4,z),Blocks.GLOWSTONE.defaultBlockState(),2);
        }
        @Override public void close(){
            if(closed)return;closed=true;previous.forEach((p,state)->level.setBlock(p,state,2));
            for(long ticket:addedTickets){ChunkPos chunk=new ChunkPos(ticket);level.setChunkForced(chunk.x,chunk.z,false);}
        }
    }
}
