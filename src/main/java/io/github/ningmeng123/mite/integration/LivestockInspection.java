package io.github.ningmeng123.mite.integration;

import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.Livestock032View;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Reads the same synchronized wellness values as the animal's body texture. @author NingMeng123 */
public final class LivestockInspection {
    public record Snapshot(float food, float water, float freedom, int recoveryTicks) {
        public Snapshot {
            food = LivestockRules032.clamp(food);
            water = LivestockRules032.clamp(water);
            freedom = LivestockRules032.clamp(freedom);
            recoveryTicks = Math.clamp(recoveryTicks, 0, LivestockRules032.RECOVERY_TICKS);
        }

        public boolean needsMet() { return new LivestockRules032.Needs(food, water, freedom).well(); }
        public boolean well() { return needsMet() && recoveryTicks == 0; }
    }

    private LivestockInspection() {}

    /** No environment scan, state initialization, simulation or extra server request. */
    @Nullable public static Snapshot inspect(Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()
                || !Livestock032.sourceLivestock(mob) || !(mob instanceof Livestock032View view)) return null;
        var needs = view.mite$livestockNeeds();
        return new Snapshot(needs.food(), needs.water(), needs.freedom(), view.mite$recoveryTicks());
    }

    public static List<Component> lines(@Nullable Snapshot snapshot) {
        if (snapshot == null || snapshot.well()) return List.of();
        List<Component> lines = new ArrayList<>();
        String status = snapshot.needsMet() ? "recovering" : "sick";
        lines.add(Component.translatable("tooltip.mite.livestock.health",
                Component.translatable("tooltip.mite.livestock.health." + status).withStyle(ChatFormatting.YELLOW)));
        if (snapshot.food() < .25F) lines.add(cause("food", snapshot.food()));
        if (snapshot.water() < .25F) lines.add(cause("water", snapshot.water()));
        if (snapshot.freedom() < .25F) lines.add(cause("freedom", snapshot.freedom()));
        // The timer only advances while all three needs meet the existing wellness threshold.
        if (snapshot.needsMet()) lines.add(Component.translatable("tooltip.mite.livestock.recovery",
                (snapshot.recoveryTicks() + 19) / 20).withStyle(ChatFormatting.YELLOW));
        return List.copyOf(lines);
    }

    private static Component cause(String resource, float value) {
        // Flooring keeps 24.999...% from being displayed as the healthy 25% boundary.
        return Component.translatable("tooltip.mite.livestock.cause_" + resource,
                (int)Math.floor(value * 100F)).withStyle(ChatFormatting.YELLOW);
    }
}
