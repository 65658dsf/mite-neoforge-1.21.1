package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Modern date announcements; PlayerPersisted survives native save/load and respawn cloning. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class LunarAnnouncements {
    public static final String DATA_KEY = "mite:lunar_announcement";
    private LunarAnnouncements() {}

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) announce(player);
    }

    public static boolean announce(ServerPlayer player) {
        if (!player.level().dimension().equals(Level.OVERWORLD) || player.connection == null || player.isRemoved()) return false;
        var state = LunarCalendar.state(player.level().getDayTime(), true);
        if (state.event() == LunarCalendar.Event.ORDINARY) return false;
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        var previous = persisted.getCompound(DATA_KEY);
        if (previous.contains("last_day", Tag.TAG_LONG) && previous.getLong("last_day") == state.day()
                && previous.getString("last_event").equals(state.event().name())) return false;

        String name;
        ChatFormatting color;
        switch (state.event()) {
            case HARVEST -> { name = "lunar.mite.harvest"; color = ChatFormatting.GOLD; }
            case BLOOD -> { name = "lunar.mite.blood"; color = ChatFormatting.RED; }
            case BLUE -> { name = "lunar.mite.blue"; color = ChatFormatting.BLUE; }
            case MOON_DOG -> { name = "lunar.mite.moon_dog"; color = ChatFormatting.AQUA; }
            default -> { return false; }
        }
        player.sendSystemMessage(Component.translatable("lunar.mite.announcement", state.day(), Component.translatable(name))
                .withStyle(color), false);
        previous.putInt("version", 1);
        previous.putLong("last_day", state.day());
        previous.putString("last_event", state.event().name());
        persisted.put(DATA_KEY, previous);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return true;
    }
}
