package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.block.MaterialSmithingTableBlock;
import io.github.ningmeng123.mite.furnace.EquipmentRecycling;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Material-upgrade workstations and their reductions to smithing time. @author NingMeng123 */
public enum SmithingTableTier {
    VANILLA("iron", 0),
    ANCIENT_METAL("ancient_metal", 8),
    MITHRIL("mithril", 6),
    ADAMANTIUM("adamantium", 4),
    VIBRANIUM("vibranium", 2);

    private static final List<SmithingTableTier> TIERS = List.of(values());

    private final String material;
    private final int timeReductionDivisor;

    SmithingTableTier(String material, int timeReductionDivisor) {
        this.material = material;
        this.timeReductionDivisor = timeReductionDivisor;
    }

    public Block block() {
        return this == VANILLA ? Blocks.SMITHING_TABLE : MiteFunctionalBlocks.SMITHING_TABLES.get(material).get();
    }

    public int timeReductionDivisor() {
        return timeReductionDivisor;
    }

    public int duration(int baseTicks) {
        return timeReductionDivisor == 0 ? baseTicks : baseTicks - baseTicks / timeReductionDivisor;
    }

    public boolean supports(SmithingTableTier required) {
        return ordinal() >= required.ordinal();
    }

    public List<SmithingTableTier> compatibleTables() {
        return TIERS.subList(ordinal(), TIERS.size());
    }

    public static SmithingTableTier requiredFor(ItemStack result) {
        return forMaterial(EquipmentRecycling.material(result));
    }

    @Nullable
    public static SmithingTableTier current(SmithingMenu menu) {
        return ((SmithingTableAccess) menu).mite$smithingTableTier();
    }

    public static int idForBlock(Block block) {
        if (block == Blocks.SMITHING_TABLE) return VANILLA.id();
        if (block instanceof MaterialSmithingTableBlock table) return forMaterial(table.material()).id();
        return 0;
    }

    public int id() {
        return ordinal() + 1;
    }

    @Nullable
    public static SmithingTableTier fromId(int id) {
        return id > 0 && id <= TIERS.size() ? TIERS.get(id - 1) : null;
    }

    private static SmithingTableTier forMaterial(String material) {
        for (SmithingTableTier tier : TIERS) {
            if (tier.material.equals(material)) return tier;
        }
        return VANILLA;
    }
}
