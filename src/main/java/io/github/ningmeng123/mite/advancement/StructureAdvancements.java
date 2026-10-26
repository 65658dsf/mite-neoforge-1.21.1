package io.github.ningmeng123.mite.advancement;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.StructureProgressPayload;
import io.github.ningmeng123.mite.rules.StructureUnlockRules;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Definition;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureExplorationData;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Visible structure requirements mirror the world's current generation gates. @author NingMeng123 */
public final class StructureAdvancements {
    public static final ResourceLocation GUIDE_ID = id("root");
    public record FlagRequirement(String criterion, int bit) {}

    public enum Entry {
        RUINED_PORTAL("ruined_portal", 4),
        PILLAGER_OUTPOST("pillager_outpost", 8),
        VILLAGE("village", 1),
        STRONGHOLD("stronghold", 2),
        SHIPWRECK("shipwreck"), OCEAN_RUIN("ocean_ruin"), BURIED_TREASURE("buried_treasure"),
        DESERT_PYRAMID("desert_pyramid"), JUNGLE_PYRAMID("jungle_pyramid"), SWAMP_HUT("swamp_hut"),
        IGLOO("igloo"), TRAIL_RUINS("trail_ruins"), MINESHAFT("mineshaft"), MANSION("mansion"),
        MONUMENT("monument"), ANCIENT_CITY("ancient_city"), FORTRESS("fortress"),
        BASTION_REMNANT("bastion_remnant"), NETHER_FOSSIL("nether_fossil"),
        OVERWORLD_FOSSIL("overworld_fossil"), END_CITY("end_city");

        private final Definition definition;
        private final int enabledBit;
        private final List<FlagRequirement> flagRequirements;
        private final List<String> criteria;

        Entry(String path) { this(path, 0); }
        Entry(String path, int legacyBit) {
            definition = Objects.requireNonNull(StructureUnlockRules.find(path));
            enabledBit = legacyBit == 0 ? 1 << ordinal() : legacyBit;
            flagRequirements = StructureAdvancements.flagRequirements().stream()
                    .filter(need -> (definition.flags() & need.bit()) != 0).toList();
            var names = new ArrayList<String>();
            names.add("structures_enabled");
            names.add("date");
            for (var need : flagRequirements) names.add(need.criterion());
            for (var need : definition.requirements()) names.add(need.criterion());
            criteria = List.copyOf(names);
        }

        public ResourceLocation id() { return StructureAdvancements.id(path()); }
        public String path() { return definition.path(); }
        public String icon() { return definition.icon(); }
        public long minimumDay() { return definition.minimumDay(); }
        public int enabledBit() { return enabledBit; }
        public Definition definition() { return definition; }
        public List<FlagRequirement> flagRequirements() { return flagRequirements; }
        public List<String> criteria() { return criteria; }
    }

    public static final int ALL_ENABLED = (1 << StructureUnlockRules.definitions().size()) - 1;
    private StructureAdvancements() {}

    private static List<FlagRequirement> flagRequirements() {
        return List.of(new FlagRequirement("crafted_obsidian_tool", StructureProgressData.OBSIDIAN_TOOL),
                new FlagRequirement("visited_underworld", StructureProgressData.UNDERWORLD_VISITED),
                new FlagRequirement("crafted_bow", StructureProgressData.BOW_CRAFTED),
                new FlagRequirement("crafted_mithril_equipment", StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "structures/" + path);
    }

    public static Entry entry(String path) {
        for (Entry entry : Entry.values()) if (entry.path().equals(path)) return entry;
        return null;
    }

    public static Entry entry(ResourceLocation id) {
        return id.getNamespace().equals(Mite.MOD_ID) && id.getPath().startsWith("structures/")
                ? entry(id.getPath().substring("structures/".length())) : null;
    }

    public static void sync(ServerPlayer player, int enabledMask, long time, int conditions) {
        sync(player, enabledMask, time, conditions, StructureExplorationData.get(player.serverLevel()).snapshot());
    }

    /** Each criterion is world-shared; rolling back the date revokes current eligibility. */
    public static void sync(ServerPlayer player, int enabledMask, long time, int conditions, Map<Metric, Long> exploration) {
        var advancements = player.server.getAdvancements();
        syncCriterion(player, advancements.get(GUIDE_ID), "visible", true);
        long day = R196StructureGeneration.displayDay(time);
        for (Entry entry : Entry.values()) {
            AdvancementHolder holder = advancements.get(entry.id());
            if (holder == null) continue;
            syncCriterion(player, holder, "structures_enabled", (enabledMask & entry.enabledBit()) != 0);
            syncCriterion(player, holder, "date", day >= entry.minimumDay());
            for (var need : entry.flagRequirements())
                syncCriterion(player, holder, need.criterion(), (conditions & need.bit()) != 0);
            for (var need : entry.definition().requirements())
                syncCriterion(player, holder, need.criterion(), need.met(exploration));
        }
        StructureProgressPayload.send(player, new StructureProgressPayload(enabledMask, day, conditions, exploration));
    }

    private static void syncCriterion(ServerPlayer player, AdvancementHolder holder, String criterion, boolean fulfilled) {
        if (holder == null) return;
        var progress = player.getAdvancements().getOrStartProgress(holder).getCriterion(criterion);
        if (progress == null || progress.isDone() == fulfilled) return;
        if (fulfilled) player.getAdvancements().award(holder, criterion);
        else player.getAdvancements().revoke(holder, criterion);
    }
}
