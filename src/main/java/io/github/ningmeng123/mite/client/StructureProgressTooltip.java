package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.advancement.StructureAdvancements.Entry;
import io.github.ningmeng123.mite.network.StructureProgressPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Current server counters alongside every advancement requirement. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class StructureProgressTooltip {
    private StructureProgressTooltip() {}

    public static List<Component> lines(Entry entry, StructureProgressPayload snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("structure_progress.mite.shared").withStyle(ChatFormatting.GRAY));
        if (snapshot == null) {
            lines.add(Component.translatable("structure_progress.mite.awaiting").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(mark((snapshot.enabledMask() & entry.enabledBit()) != 0,
                Component.translatable("structure_progress.mite.enabled")));
        lines.add(mark(snapshot.day() >= entry.minimumDay(),
                Component.translatable("structure_progress.mite.date", snapshot.day(), entry.minimumDay())));
        for (var need : entry.flagRequirements()) lines.add(mark((snapshot.flags() & need.bit()) != 0,
                Component.translatable("structure_progress.mite." + need.criterion())));
        for (var need : entry.definition().requirements()) {
            long value = snapshot.values().getOrDefault(need.metric(), 0L);
            Component label = Component.translatable("structure_progress.mite." + need.metric().key());
            Component current = need.metric().distance()
                    ? Component.translatable("structure_progress.mite.distance", label, blocks(value), blocks(need.target()))
                    : Component.translatable("structure_progress.mite.count", label, value, need.target());
            lines.add(mark(value >= need.target(), current));
        }
        lines.add(Component.translatable("structure_progress.mite.new_chunks").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static String blocks(long centimeters) { return String.format(Locale.ROOT, "%.2f", centimeters / 100D); }
    private static MutableComponent mark(boolean complete, Component label) {
        return Component.literal(complete ? "✓ " : "□ ").append(label)
                .withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        StructureProgressPayload.clearClientState();
    }
}
