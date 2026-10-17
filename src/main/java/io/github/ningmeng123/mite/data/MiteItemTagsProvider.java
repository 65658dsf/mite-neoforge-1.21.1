package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.registry.MiteTags;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Explicit tools below the current log-harvesting stage. @author NingMeng123 */
public final class MiteItemTagsProvider extends ItemTagsProvider {
    public MiteItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
            CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, ExistingFileHelper existingFiles) {
        super(output, lookup, blockTags, Mite.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookup) {
        // Preserve native tag unions while excluding only the ten retired item identities.
        for (var equipmentTag : List.of(ItemTags.SWORDS, ItemTags.AXES, ItemTags.PICKAXES, ItemTags.SHOVELS,
                ItemTags.HOES, ItemTags.HEAD_ARMOR, ItemTags.CHEST_ARMOR, ItemTags.LEG_ARMOR, ItemTags.FOOT_ARMOR,
                ItemTags.TRIMMABLE_ARMOR, ItemTags.HEAD_ARMOR_ENCHANTABLE, ItemTags.CHEST_ARMOR_ENCHANTABLE,
                ItemTags.LEG_ARMOR_ENCHANTABLE, ItemTags.FOOT_ARMOR_ENCHANTABLE, ItemTags.ARMOR_ENCHANTABLE,
                ItemTags.SWORD_ENCHANTABLE, ItemTags.FIRE_ASPECT_ENCHANTABLE, ItemTags.SHARP_WEAPON_ENCHANTABLE,
                ItemTags.WEAPON_ENCHANTABLE, ItemTags.MINING_ENCHANTABLE, ItemTags.MINING_LOOT_ENCHANTABLE,
                ItemTags.DURABILITY_ENCHANTABLE, ItemTags.EQUIPPABLE_ENCHANTABLE, ItemTags.VANISHING_ENCHANTABLE)) {
            RemovedDiamondEquipment.IDS.forEach(id -> tag(equipmentTag).remove(id));
        }
        var ancientBow = io.github.ningmeng123.mite.registry.MiteWeaponContent.ANCIENT_METAL_BOW.get();
        var mithrilBow = io.github.ningmeng123.mite.registry.MiteWeaponContent.MITHRIL_BOW.get();
        tag(ItemTags.DURABILITY_ENCHANTABLE).add(ancientBow, mithrilBow);
        tag(ItemTags.VANISHING_ENCHANTABLE).add(ancientBow, mithrilBow);
        for (var definition : io.github.ningmeng123.mite.registry.MiteShields.definitions()) {
            if (!definition.isNew()) continue;
            var shield = io.github.ningmeng123.mite.registry.MiteShields.get(definition.material());
            tag(ItemTags.DURABILITY_ENCHANTABLE).add(shield);
            tag(ItemTags.VANISHING_ENCHANTABLE).add(shield);
            tag(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHIELD).add(shield);
        }
        tag(ItemTags.SMALL_FLOWERS).add(io.github.ningmeng123.mite.registry.MiteDecorativeContent.DAHLIA.get().asItem());
        for (var definition : io.github.ningmeng123.mite.registry.MiteArmorContent.definitions()) {
            if (!definition.isNew() || definition.horse()) continue;
            var item = io.github.ningmeng123.mite.registry.MiteArmorContent.get(definition.id());
            tag(ItemTags.ARMOR_ENCHANTABLE).add(item);
            tag(ItemTags.DURABILITY_ENCHANTABLE).add(item);
            tag(ItemTags.VANISHING_ENCHANTABLE).add(item);
            tag(ItemTags.TRIMMABLE_ARMOR).add(item);
            switch (definition.type()) {
                case HELMET -> tag(ItemTags.HEAD_ARMOR_ENCHANTABLE).add(item);
                case CHESTPLATE -> tag(ItemTags.CHEST_ARMOR_ENCHANTABLE).add(item);
                case LEGGINGS -> tag(ItemTags.LEG_ARMOR_ENCHANTABLE).add(item);
                case BOOTS -> tag(ItemTags.FOOT_ARMOR_ENCHANTABLE).add(item);
                default -> { }
            }
        }
        for (var material : io.github.ningmeng123.mite.registry.MiteRangedItems.rods()) {
            if (material == io.github.ningmeng123.mite.rules.R196RangedRules.Material.IRON) continue;
            tag(ItemTags.DURABILITY_ENCHANTABLE).add(io.github.ningmeng123.mite.registry.MiteRangedItems.rod(material),
                    io.github.ningmeng123.mite.registry.MiteRangedItems.carrotRod(material));
            tag(ItemTags.VANISHING_ENCHANTABLE).add(io.github.ningmeng123.mite.registry.MiteRangedItems.rod(material),
                    io.github.ningmeng123.mite.registry.MiteRangedItems.carrotRod(material));
        }
        tag(MiteTags.BELOW_LOG_MINING_TIER).add(Items.WOODEN_AXE);
        tag(ItemTags.SWORDS).remove(ResourceLocation.withDefaultNamespace("wooden_sword"));
        tag(ItemTags.DURABILITY_ENCHANTABLE).add(Items.WOODEN_SWORD);
        tag(ItemTags.VANISHING_ENCHANTABLE).add(Items.WOODEN_SWORD);
        tag(MiteTags.SHEARS).add(Items.SHEARS);
        for (var definition : MiteTools.definitions()) {
            if (!definition.isNew()) continue;
            var item = MiteTools.get(definition.id());
            tag(ItemTags.DURABILITY_ENCHANTABLE).add(item);
            tag(ItemTags.VANISHING_ENCHANTABLE).add(item);
            switch (definition.family()) {
                case AXE, HATCHET, BATTLE_AXE -> tag(ItemTags.AXES).add(item);
                case PICKAXE, WAR_HAMMER -> tag(ItemTags.PICKAXES).add(item);
                case SHOVEL, MATTOCK -> tag(ItemTags.SHOVELS).add(item);
                case HOE -> tag(ItemTags.HOES).add(item);
                case SWORD, DAGGER, KNIFE, SCYTHE -> tag(ItemTags.SWORDS).add(item);
                case SHEARS -> {
                    tag(MiteTags.SHEARS).add(item);
                    tag(ItemTags.MINING_ENCHANTABLE).add(item);
                }
                default -> { }
            }
        }
    }
}
