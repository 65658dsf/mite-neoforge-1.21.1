package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ArmorProfile;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.util.List;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.*;

/** MITE-ITE vibranium material values applied to this project's existing equipment families. @author NingMeng123 */
public final class VibraniumEquipmentRules {
    public static final float MATERIAL_DURABILITY = 512F;
    public static final int ENCHANTABILITY = 120;
    public static final int HARVEST_LEVEL = 6;
    public static final int MATERIAL_PROTECTION = 15;
    public static final Quality MAXIMUM_QUALITY = Quality.LEGENDARY;
    public static final List<ToolFamily> TOOL_FAMILIES = List.of(HATCHET, AXE, PICKAXE, SHOVEL,
            HOE, MATTOCK, SCYTHE, SHEARS, DAGGER, KNIFE, SWORD, BATTLE_AXE, WAR_HAMMER);

    public record ToolStats(float speed, float damage, float reach) {}

    private VibraniumEquipmentRules() {}

    /** Existing family base speed/damage multiplied by source efficiency 3.5 / added to material damage 9. */
    public static ToolStats toolStats(ToolFamily family) {
        return switch (family) {
            case HATCHET -> new ToolStats(7F, 11F, 0.5F);
            case AXE -> new ToolStats(14F, 12F, 0.75F);
            case PICKAXE -> new ToolStats(14F, 11F, 0.75F);
            case SHOVEL -> new ToolStats(14F, 10F, 0.75F);
            case HOE -> new ToolStats(7F, 10F, 0.75F);
            case MATTOCK -> new ToolStats(10.5F, 10F, 0.75F);
            case SCYTHE -> new ToolStats(14F, 10F, 1F);
            case SHEARS -> new ToolStats(14F, 9F, 0.5F);
            case DAGGER -> new ToolStats(7F, 11F, 0.5F);
            case KNIFE -> new ToolStats(7F, 10F, 0.25F);
            case SWORD -> new ToolStats(7F, 13F, 0.75F);
            case BATTLE_AXE -> new ToolStats(10.5F, 13F, 0.75F);
            case WAR_HAMMER -> new ToolStats(10.5F, 11F, 0.75F);
            default -> throw new IllegalArgumentException("No vibranium version of tool family: " + family);
        };
    }

    public static ArmorProfile armorProfile(int components) {
        return new ArmorProfile(components, MATERIAL_DURABILITY, MATERIAL_PROTECTION, false, MAXIMUM_QUALITY);
    }
}
