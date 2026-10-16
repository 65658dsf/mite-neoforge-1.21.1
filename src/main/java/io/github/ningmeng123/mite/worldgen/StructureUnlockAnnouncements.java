package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.advancement.StructureAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Announce actual structure eligibility once per player, without creating new progression gates. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class StructureUnlockAnnouncements {
    public static final int VILLAGE=1, END_PORTAL=2, RUINED_PORTAL=4, PILLAGER_OUTPOST=8;
    private static final int ALL_NOTICES=VILLAGE|END_PORTAL|RUINED_PORTAL|PILLAGER_OUTPOST;
    public static final String DATA_KEY="mite:structure_unlock_notices";
    private StructureUnlockAnnouncements() {}

    public static int available(ServerLevel level) {
        return available(level,level.getServer().getWorldData().worldGenOptions());
    }

    /** Uses the generator's enabled sets and the same gate as natural generation and locating. */
    public static int available(ServerLevel level,WorldOptions options) {
        return structureMask(level,options,true);
    }

    public static int enabled(ServerLevel level,WorldOptions options) {
        return structureMask(level,options,false);
    }

    private static int structureMask(ServerLevel level,WorldOptions options,boolean requireUnlocked) {
        ServerLevel overworld=level.getServer().overworld();
        if(overworld==null || !options.generateStructures())return 0;
        int result=0;
        for(ServerLevel generationLevel:overworld.getServer().getAllLevels()) {
            for(var set:generationLevel.getChunkSource().getGeneratorState().possibleStructureSets()) {
                for(var entry:set.value().structures()) {
                    var key=entry.structure().unwrapKey();
                    if(key.isEmpty() || !key.get().location().getNamespace().equals("minecraft"))continue;
                    String id=key.get().location().getPath();
                    boolean portal=id.equals("ruined_portal")||id.startsWith("ruined_portal_");
                    if(generationLevel!=overworld&&!portal)continue;
                    if(requireUnlocked&&!R196StructureGeneration.unlocked(generationLevel,entry.structure()))continue;
                    if(id.startsWith("village_"))result|=VILLAGE;
                    else if(id.equals("stronghold"))result|=END_PORTAL;
                    else if(portal)result|=RUINED_PORTAL;
                    else if(id.equals("pillager_outpost"))result|=PILLAGER_OUTPOST;
                }
            }
        }
        return result;
    }

    public static int announce(ServerPlayer player) {
        ServerLevel overworld=player.server.overworld();
        if(overworld==null)return 0;
        StructureAdvancements.sync(player,enabled(overworld,player.server.getWorldData().worldGenOptions()),
                overworld.getDayTime(),StructureProgressData.get(overworld).conditions());
        return deliver(player,available(overworld),R196StructureGeneration.displayDay(overworld.getDayTime()));
    }

    /** An authoritative server snapshot; there is no client request that can supply these values. */
    public static int deliver(ServerPlayer player,int available,long day) {
        if(player.connection==null || !player.connection.isAcceptingMessages() || player.isRemoved())return 0;
        var data=player.getPersistentData();
        var persisted=data.getCompound(Player.PERSISTED_NBT_TAG);
        var notices=persisted.getCompound(DATA_KEY);
        int seen=notices.getInt("seen");
        int pending=(available&ALL_NOTICES)&~seen;
        if(pending==0)return 0;
        if((pending&END_PORTAL)!=0)player.sendSystemMessage(Component.translatable(
                "message.mite.structure_unlock.end_portal",day).withStyle(ChatFormatting.AQUA),false);
        if((pending&VILLAGE)!=0)player.sendSystemMessage(Component.translatable(
                "message.mite.structure_unlock.village",day).withStyle(ChatFormatting.GREEN),false);
        if((pending&RUINED_PORTAL)!=0)player.sendSystemMessage(Component.translatable(
                "message.mite.structure_unlock.ruined_portal",day).withStyle(ChatFormatting.GOLD),false);
        if((pending&PILLAGER_OUTPOST)!=0)player.sendSystemMessage(Component.translatable(
                "message.mite.structure_unlock.pillager_outpost",day).withStyle(ChatFormatting.RED),false);
        notices.putInt("version",1);
        notices.putInt("seen",seen|pending);
        persisted.put(DATA_KEY,notices);
        data.put(Player.PERSISTED_NBT_TAG,persisted);
        return Integer.bitCount(pending);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)announce(player);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)
                || Math.floorMod(level.getGameTime(),20L)!=0)return;
        var players=level.getServer().getPlayerList().getPlayers();
        if(players.isEmpty())return;
        int available=available(level);
        int enabled=enabled(level,level.getServer().getWorldData().worldGenOptions());
        int conditions=StructureProgressData.get(level).conditions();
        long day=R196StructureGeneration.displayDay(level.getDayTime());
        for(ServerPlayer player:players) {
            StructureAdvancements.sync(player,enabled,level.getDayTime(),conditions);
            if(available!=0)deliver(player,available,day);
        }
    }
}
