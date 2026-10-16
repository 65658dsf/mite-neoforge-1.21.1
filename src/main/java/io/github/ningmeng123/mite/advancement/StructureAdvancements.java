package io.github.ningmeng123.mite.advancement;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import io.github.ningmeng123.mite.worldgen.StructureUnlockAnnouncements;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Visible structure requirements mirror the world's current generation gates. @author NingMeng123 */
public final class StructureAdvancements {
    public static final ResourceLocation GUIDE_ID = id("root");

    private record Requirement(String criterion, int bit) {}

    public enum Entry {
        RUINED_PORTAL("ruined_portal", "minecraft:crying_obsidian", StructureUnlockAnnouncements.RUINED_PORTAL,
                R196StructureGeneration.RUINED_PORTAL_DAY,
                new Requirement("visited_underworld", StructureProgressData.UNDERWORLD_VISITED)),
        PILLAGER_OUTPOST("pillager_outpost", "minecraft:crossbow", StructureUnlockAnnouncements.PILLAGER_OUTPOST,
                R196StructureGeneration.PILLAGER_OUTPOST_DAY,
                new Requirement("crafted_bow", StructureProgressData.BOW_CRAFTED),
                new Requirement("crafted_mithril_equipment", StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED)),
        VILLAGE("village", "minecraft:bell", StructureUnlockAnnouncements.VILLAGE,
                R196StructureGeneration.VILLAGE_DAY,
                new Requirement("crafted_obsidian_tool", StructureProgressData.OBSIDIAN_TOOL)),
        STRONGHOLD("stronghold", "minecraft:end_portal_frame", StructureUnlockAnnouncements.END_PORTAL, 0L);

        private final String path;
        private final String icon;
        private final int enabledBit;
        private final long minimumDay;
        private final List<Requirement> requirements;
        private final List<String> criteria;

        Entry(String path, String icon, int enabledBit, long minimumDay, Requirement... requirements) {
            this.path = path;
            this.icon = icon;
            this.enabledBit = enabledBit;
            this.minimumDay = minimumDay;
            this.requirements = List.of(requirements);
            var names = new ArrayList<String>();
            names.add("structures_enabled");
            if (minimumDay > 0L) names.add("date");
            for (Requirement requirement : requirements) names.add(requirement.criterion());
            criteria = List.copyOf(names);
        }

        public ResourceLocation id() { return StructureAdvancements.id(path); }
        public String path() { return path; }
        public String icon() { return icon; }
        public List<String> criteria() { return criteria; }
    }

    private StructureAdvancements() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "structures/" + path);
    }

    /** Each criterion is world-shared; revoking a date after a rollback reflects actual eligibility. */
    public static void sync(ServerPlayer player, int enabledMask, long time, int conditions) {
        var advancements = player.server.getAdvancements();
        // Completing the guide keeps every direct structure child visible even before its unlock.
        syncCriterion(player, advancements.get(GUIDE_ID), "visible", true);
        long day = R196StructureGeneration.displayDay(time);
        for (Entry entry : Entry.values()) {
            AdvancementHolder holder = advancements.get(entry.id());
            if (holder == null) continue;
            syncCriterion(player, holder, "structures_enabled", (enabledMask & entry.enabledBit) != 0);
            if (entry.minimumDay > 0L) syncCriterion(player, holder, "date", day >= entry.minimumDay);
            for (Requirement requirement : entry.requirements)
                syncCriterion(player, holder, requirement.criterion(), (conditions & requirement.bit()) != 0);
        }
    }

    private static void syncCriterion(ServerPlayer player, AdvancementHolder holder, String criterion, boolean fulfilled) {
        if (holder == null) return;
        var progress = player.getAdvancements().getOrStartProgress(holder).getCriterion(criterion);
        if (progress == null || progress.isDone() == fulfilled) return;
        if (fulfilled) player.getAdvancements().award(holder, criterion);
        else player.getAdvancements().revoke(holder, criterion);
    }
}
