package io.github.ningmeng123.mite;

import com.mojang.logging.LogUtils;
import io.github.ningmeng123.mite.item.WoodenClubBehavior;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.crafting.DisabledVanillaWorkbench;
import io.github.ningmeng123.mite.mining.GrassMining;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.registry.MiteLootModifiers;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * MITE: Modern entry point.
 *
 * @author NingMeng123
 */
@Mod(Mite.MOD_ID)
public final class Mite {
    public static final String MOD_ID = "mite";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mite(IEventBus modBus) {
        MiteAttachments.register(modBus);
        io.github.ningmeng123.mite.worldgen.UnderworldSpringGeneration.register(modBus);
        io.github.ningmeng123.mite.world.CollapsePhysics.register(modBus);
        io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks.register(modBus);
        MiteItems.register(modBus);
        MiteMaterials.register(modBus);
        io.github.ningmeng123.mite.registry.MiteMetalBlocks.register(modBus);
        MiteShards.register(modBus);
        MiteResourceBlocks.register(modBus);
        io.github.ningmeng123.mite.registry.MiteOreBlocks.register(modBus);
        io.github.ningmeng123.mite.registry.MiteWorldBlocks.register(modBus);
        io.github.ningmeng123.mite.registry.MiteWorldFeatures.register(modBus);
        io.github.ningmeng123.mite.worldgen.MiteWorldDimensions.register(modBus);
        EquipmentDurability.register(modBus);
        MiteShields.register(modBus);
        io.github.ningmeng123.mite.registry.MiteArmorContent.register(modBus);
        io.github.ningmeng123.mite.registry.MiteRangedItems.register(modBus);
        io.github.ningmeng123.mite.registry.MiteFunctionalBlocks.register(modBus);
        io.github.ningmeng123.mite.registry.MiteDecorativeContent.register(modBus);
        io.github.ningmeng123.mite.registry.MiteWeaponContent.register(modBus);
        MiteTools.register(modBus);
        MiteWorkbenches.register(modBus);
        io.github.ningmeng123.mite.registry.MiteFurnaces.register(modBus);
        io.github.ningmeng123.mite.registry.MiteFoodPlants.register(modBus);
        io.github.ningmeng123.mite.registry.MiteFoodSources.register(modBus);
        io.github.ningmeng123.mite.registry.MiteFoods.register(modBus);
        io.github.ningmeng123.mite.registry.MiteUtilityContent.register(modBus);
        io.github.ningmeng123.mite.registry.FoodRecipeSerializers.register(modBus);
        io.github.ningmeng123.mite.registry.MiteFoodCatalog.register(modBus);
        io.github.ningmeng123.mite.registry.QualityRecipeSerializers.register(modBus);
        DisabledVanillaWorkbench.register(modBus);
        MiteLootModifiers.register(modBus);
        modBus.addListener(WoodenClubBehavior::modifyComponents);
        modBus.addListener(GrassMining::modifyShears);
        io.github.ningmeng123.mite.registry.MiteItemStacking.register(modBus);
        io.github.ningmeng123.mite.registry.StackingRecipeSerializers.register(modBus);
        io.github.ningmeng123.mite.registry.MiteMobs.register(modBus);
        io.github.ningmeng123.mite.registry.MiteMobSounds.register(modBus);
        io.github.ningmeng123.mite.registry.MiteMobBlocks.register(modBus);
        io.github.ningmeng123.mite.registry.MiteCreativeTabs.register(modBus);
        io.github.ningmeng123.mite.entity.R196VanillaMobEvents.register(modBus);
        io.github.ningmeng123.mite.worldgen.R196MobSpawning.register(modBus);
        LOGGER.info("MITE: Modern initialized for Minecraft 1.21.1");
    }
}
