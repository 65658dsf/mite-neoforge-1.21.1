package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.client.config.MiteConfigClient;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.OreGenerationDisplay.Entry;
import io.github.ningmeng123.mite.worldgen.OreGenerationDisplay.Settings;
import java.util.List;
import jeresources.api.conditionals.Conditional;
import jeresources.api.distributions.DistributionCustom;
import jeresources.api.distributions.DistributionSquare;
import jeresources.api.drop.LootDrop;
import jeresources.api.restrictions.DimensionRestriction;
import jeresources.api.restrictions.Restriction;
import jeresources.entry.WorldGenEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Data for JER's WorldGenCategory and WorldGenTooltip. Both read the entry again while it is open. @author NingMeng123 */
final class MiteResourceEntry extends WorldGenEntry {
    private static final float[] NO_CURVE = {};
    private static final DistributionCustom EMPTY = new DistributionCustom(NO_CURVE, 0);
    private final Entry ore;
    private final ResourceKey<Level> dimension;
    private final GravelJerDrops.Page gravel;
    private Settings revision;
    private float[] chances = NO_CURVE;
    private int minY, maxY;
    private float blocksPerChunk;

    MiteResourceEntry(Entry ore, GravelJerDrops.Page gravel) {
        super(sourceStack(ore.block(), "mite.jer.veins"), EMPTY, new Restriction(new DimensionRestriction(dimension(ore))),
                requiresSilkTouch(ore.kind()), miningDrops(ore));
        this.ore = ore;
        this.dimension = dimension(ore);
        this.gravel = gravel;
    }

    /** The vanilla Nether gravel placed feature retains its two size-33 attempts at Y 5..41. */
    MiteResourceEntry(GravelJerDrops.Page gravel, Block block) {
        super(sourceStack(block, "mite.jer.nether_gravel"), new DistributionSquare(2, 33, 5, 41), Restriction.NETHER);
        this.gravel = gravel;
        this.dimension = Level.NETHER;
        this.ore = null;
    }

    private static ItemStack sourceStack(Block block, String note) {
        var stack = new ItemStack(block);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(note))));
        return stack;
    }

    private static ResourceKey<Level> dimension(Entry ore) {
        return switch (ore.dimension()) {
            case OVERWORLD -> Level.OVERWORLD;
            case UNDERWORLD -> MiteWorldDimensions.UNDERWORLD;
            case NETHER -> Level.NETHER;
        };
    }

    private static boolean requiresSilkTouch(Kind kind) {
        return switch (kind) {
            case GRAVEL, ANCIENT_DEBRIS, NETHER_GOLD, INFESTED, NETHER_INFESTED -> false;
            default -> true;
        };
    }

    private static LootDrop[] miningDrops(Entry ore) {
        Item mined = switch (ore.kind()) {
            case COAL -> Items.COAL;
            case COPPER -> Items.RAW_COPPER;
            case IRON -> Items.RAW_IRON;
            case GOLD -> Items.RAW_GOLD;
            case SILVER -> MiteMaterials.RAW_SILVER.get();
            case MITHRIL -> MiteMaterials.RAW_MITHRIL.get();
            case ADAMANTIUM, NETHER_ADAMANTIUM -> MiteMaterials.RAW_ADAMANTIUM.get();
            case DIAMOND -> Items.DIAMOND;
            case EMERALD -> Items.EMERALD;
            case REDSTONE -> Items.REDSTONE;
            case LAPIS -> Items.LAPIS_LAZULI;
            case QUARTZ -> Items.QUARTZ;
            case RED_DIAMOND -> BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("modern_ite", "fancy_red"));
            case ANCIENT_DEBRIS, NETHER_GOLD -> ore.block().asItem();
            default -> Items.AIR;
        };
        if (mined == Items.AIR) return new LootDrop[0];
        if (ore.kind() == Kind.ANCIENT_DEBRIS) return new LootDrop[]{new LootDrop(new ItemStack(mined))};
        boolean multiple = ore.kind() == Kind.LAPIS || ore.kind() == Kind.REDSTONE;
        return new LootDrop[]{new LootDrop(new ItemStack(mined), multiple ? 3 : 1, multiple ? 5 : 1, Conditional.affectedByFortune)};
    }

    @Override public List<ItemStack> getDrops() {
        return gravel == null ? super.getDrops() : gravel.getDrops();
    }

    @Override public List<LootDrop> getLootDrops(ItemStack stack) {
        return gravel == null ? super.getLootDrops(stack) : gravel.getLootDrops(stack);
    }

    @Override public float[] getChances() {
        if (ore == null) return super.getChances();
        refresh();
        return chances;
    }

    @Override public int getMinY() {
        if (ore == null) return super.getMinY();
        refresh();
        return minY;
    }

    @Override public int getMaxY() {
        if (ore == null) return super.getMaxY();
        refresh();
        return maxY;
    }

    @Override public float getAverageBlockCountPerChunk() {
        if (ore == null) return super.getAverageBlockCountPerChunk();
        refresh();
        return blocksPerChunk;
    }

    private void refresh() {
        var level = Minecraft.getInstance().level;
        var type = level == null ? null : level.dimension().equals(dimension) ? level.dimensionType()
                : level.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).get(dimension.location());
        int bottom = type == null ? 0 : type.minY();
        int top = type == null ? 0 : bottom + type.height() - 1;
        var settings = type == null ? null : ore.settings(bottom, top + 1, MiteConfigClient.remoteServer());
        if (settings == revision && minY == bottom && maxY == top) return;
        revision = settings;
        minY = bottom;
        maxY = top;
        blocksPerChunk = 0;
        chances = NO_CURVE;
        if (settings == null || !(settings.candidates() * settings.chance() > 0)) return;
        var distribution = new float[top - bottom + 1];
        for (int y = bottom; y <= top; y++) {
            distribution[y - bottom] = (float)settings.blockDensityAt(y);
            blocksPerChunk += distribution[y - bottom] * 256;
        }
        // A zero-length series lets the native graph display its zero scale without dividing samples by zero.
        if (blocksPerChunk > 0) chances = distribution;
    }
}
