package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.registry.MiteTools.Definition;
import io.github.ningmeng123.mite.registry.MiteTags;
import io.github.ningmeng123.mite.registry.MiteOreBlocks;
import io.github.ningmeng123.mite.registry.MiteFurnaces;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/** Shared R196 tool behavior with explicit modern block families and cooldowns. @author NingMeng123 */
public final class R196ToolItem extends Item implements EquipmentDurability.Profiled {
    private static final List<Block> GRASSES = List.of(Blocks.SHORT_GRASS, Blocks.FERN,
            Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.DEAD_BUSH);
    private static List<Block> sourceGrasses() {
        List<Block> plants = new ArrayList<>(GRASSES);
        plants.add(io.github.ningmeng123.mite.registry.MiteDecorativeContent.WITHERWOOD_BUSH.get());
        return plants;
    }
    /** Stained and tinted modern glass share the source glass material, including their pane variants. */
    private static List<Block> glassBlocks() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof net.minecraft.world.level.block.TransparentBlock
                        || block == Blocks.GLASS_PANE || block instanceof net.minecraft.world.level.block.StainedGlassPaneBlock).toList();
    }
    private static final List<Block> SANDSTONE = List.of(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE,
            Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.RED_SANDSTONE,
            Blocks.CUT_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE,
            Blocks.SANDSTONE_SLAB, Blocks.CUT_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE_SLAB,
            Blocks.RED_SANDSTONE_SLAB, Blocks.CUT_RED_SANDSTONE_SLAB, Blocks.SMOOTH_RED_SANDSTONE_SLAB,
            Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_STAIRS,
            Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.SANDSTONE_WALL, Blocks.RED_SANDSTONE_WALL);
    private final Definition definition;

    public R196ToolItem(Definition definition, Properties properties) {
        super(properties.durability(EquipmentDurabilityRules.baseToolDurability(definition.profile()))
                .component(DataComponents.TOOL, tool(definition)).attributes(attributes(definition)));
        this.definition = definition;
    }

    public Definition definition() { return definition; }

    @Override
    public ToolProfile durabilityProfile() { return definition.profile(); }

    public static ItemAttributeModifiers attributes(Definition definition) {
        // Cooldown is a 1.21 design choice, independent of R196's integer damage bonus.
        double attackSpeed = switch (definition.family()) {
            case AXE, HATCHET, BATTLE_AXE, WAR_HAMMER -> -3.0;
            case PICKAXE, SHOVEL, MATTOCK -> -2.8;
            case HOE -> -2.0;
            default -> -2.4;
        };
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                        definition.damage(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID,
                        attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public static Tool tool(Definition definition) {
        List<Tool.Rule> rules = new ArrayList<>();
        int level = definition.harvestLevel();
        // Furnace minerals use their source material threshold rather than vanilla tier names.
        if (level < 3) deny(rules, List.of(MiteFurnaces.OBSIDIAN_FURNACE.get()));
        if (level < 3) rules.add(new Tool.Rule(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOrCreateTag(
                io.github.ningmeng123.mite.registry.MiteTags.RUNESTONES), Optional.of(1.0F), Optional.of(false)));
        // Known R196 mineral thresholds override modern tier names; modern additions use their existing tags below.
        if (level < 4) deny(rules, List.of(MiteOreBlocks.ADAMANTIUM_ORE.get(), MiteOreBlocks.DEEPSLATE_ADAMANTIUM_ORE.get()));
        if (level < 3) deny(rules, List.of(MiteOreBlocks.MITHRIL_ORE.get(), MiteOreBlocks.DEEPSLATE_MITHRIL_ORE.get()));
        if (level < 4) deny(rules, List.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.DIAMOND_BLOCK, Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK));
        if (level < 3) deny(rules, List.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.EMERALD_BLOCK,
                Blocks.IRON_BLOCK, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL));
        if (definition.family() == ToolFamily.PICKAXE || definition.family() == ToolFamily.WAR_HAMMER)
            // Source clay materials do not require a minimum harvest level.
            rules.add(Tool.Rule.minesAndDrops(List.of(MiteFurnaces.CLAY_OVEN.get(), MiteFurnaces.LARGE_CLAY_OVEN.get()), definition.speed()));
        if (level < 2 && (definition.family() == ToolFamily.PICKAXE || definition.family() == ToolFamily.WAR_HAMMER))
            rules.add(new Tool.Rule(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.MINEABLE_WITH_PICKAXE),
                Optional.of(1.0F), Optional.of(false)));
        float speed = definition.speed();
        switch (definition.family()) {
            case PICKAXE, WAR_HAMMER -> {
                rules.add(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, speed));
                if (definition.family() == ToolFamily.WAR_HAMMER)
                    rules.add(Tool.Rule.minesAndDrops(List.of(Blocks.CAKE, Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN,
                            Blocks.JACK_O_LANTERN, Blocks.MELON), speed));
            }
            case AXE, HATCHET, BATTLE_AXE -> {
                rules.add(Tool.Rule.minesAndDrops(glassBlocks(), speed));
                rules.add(Tool.Rule.minesAndDrops(List.of(MiteFurnaces.SANDSTONE_FURNACE.get()), speed * 0.5F));
                rules.add(Tool.Rule.minesAndDrops(SANDSTONE, speed * 0.5F));
                rules.add(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_AXE, speed));
                rules.add(Tool.Rule.minesAndDrops(List.of(Blocks.CACTUS, Blocks.CLAY, Blocks.GLASS, Blocks.GLASS_PANE,
                        Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.LADDER, Blocks.SUGAR_CANE), speed));
                rules.add(Tool.Rule.minesAndDrops(BlockTags.TERRACOTTA, speed));
            }
            case SHOVEL, MATTOCK, HOE -> {
                rules.add(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL, speed));
                rules.add(Tool.Rule.minesAndDrops(BlockTags.CROPS, speed));
                rules.add(Tool.Rule.minesAndDrops(List.of(Blocks.CAKE), speed));
                if (definition.family() == ToolFamily.HOE)
                    rules.add(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_HOE, speed));
                if (!List.of("wood", "flint", "obsidian").contains(definition.material()))
                    rules.add(Tool.Rule.minesAndDrops(glassBlocks(), speed));
            }
            case SWORD, DAGGER, KNIFE, SHEARS -> {
                rules.add(Tool.Rule.minesAndDrops(BlockTags.LEAVES, speed));
                rules.add(Tool.Rule.minesAndDrops(BlockTags.WOOL, speed));
                rules.add(Tool.Rule.minesAndDrops(BlockTags.FLOWERS, speed));
                rules.add(Tool.Rule.minesAndDrops(BlockTags.SAPLINGS, speed));
                rules.add(Tool.Rule.minesAndDrops(sourceGrasses(), speed));
                rules.add(Tool.Rule.minesAndDrops(List.of(Blocks.COBWEB, Blocks.VINE, Blocks.GLOW_LICHEN,
                        Blocks.TRIPWIRE, Blocks.SUGAR_CANE, Blocks.HANGING_ROOTS), speed));
                if (definition.family() != ToolFamily.SHEARS)
                    rules.add(Tool.Rule.minesAndDrops(List.of(Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Blocks.MELON), speed));
            }
            case SCYTHE -> {
                rules.add(Tool.Rule.minesAndDrops(sourceGrasses(), speed));
                rules.add(Tool.Rule.minesAndDrops(BlockTags.CROPS, speed));
            }
            // Both wooden blunt tools have harvest level zero; whole glass/ice require one.
            case CLUB, CUDGEL -> rules.add(Tool.Rule.minesAndDrops(MiteTags.WOODEN_CLUB_EFFICIENT, speed));
        }
        return new Tool(rules, 1.0F, 0);
    }

    private static void deny(List<Tool.Rule> rules, List<Block> blocks) {
        rules.add(new Tool.Rule(HolderSet.direct(blocks.stream().map(Block::builtInRegistryHolder).toList()),
                Optional.of(1.0F), Optional.of(false)));
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return switch (definition.family()) {
            case AXE, HATCHET, BATTLE_AXE -> ability == ItemAbilities.AXE_DIG;
            case PICKAXE, WAR_HAMMER -> ability == ItemAbilities.PICKAXE_DIG;
            case SHOVEL -> ability == ItemAbilities.SHOVEL_DIG;
            case MATTOCK -> ability == ItemAbilities.SHOVEL_DIG || ability == ItemAbilities.HOE_TILL;
            case HOE -> ability == ItemAbilities.HOE_DIG || ability == ItemAbilities.HOE_TILL;
            case SHEARS -> ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(ability);
            case SWORD, DAGGER -> ability == ItemAbilities.SWORD_DIG || ability == ItemAbilities.SWORD_SWEEP;
            case KNIFE, SCYTHE -> ability == ItemAbilities.SWORD_DIG;
            default -> false;
        };
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        return EquipmentDurability.mineBlock(stack, level, state, pos, miner);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) { return true; }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        EquipmentDurability.postHurtEnemy(stack, attacker);
    }

    @Override
    public int getEnchantmentValue() { return definition.enchantability(); }

    @Override
    public int getBurnTime(ItemStack stack, RecipeType<?> recipeType) {
        return definition.material().equals("wood") ? 200 : 0;
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
        float factor = 1.0F;
        if (definition.material().equals("silver") && target.getType().is(EntityTypeTags.UNDEAD)) factor *= 1.25F;
        if (target instanceof AbstractSkeleton && (definition.family() == ToolFamily.CUDGEL
                || definition.family() == ToolFamily.CLUB || definition.family() == ToolFamily.WAR_HAMMER)) factor *= 2.0F;
        return damage * (factor - 1.0F);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (definition.family() == ToolFamily.HOE || definition.family() == ToolFamily.MATTOCK) return till(context);
        if (definition.family() != ToolFamily.SHEARS) return InteractionResult.PASS;
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (player == null || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) return InteractionResult.PASS;
        // Modern trimming remains available without converting crops or protected blocks to drops.
        BlockState trimmed = state.getToolModifiedState(context, ItemAbilities.SHEARS_TRIM, false);
        if (trimmed != null) {
            if (!level.isClientSide) {
                level.setBlockAndUpdate(pos, trimmed);
                EquipmentDurability.hurt(context.getItemInHand(), 50, player, LivingEntity.getSlotForHand(context.getHand()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // R196's whole-block clipping has no offhand equivalent. destroyBlock uses the main-hand stack,
        // so do not let offhand shears charge an unrelated main-hand tool or produce its ordinary drops.
        if (context.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.is(Blocks.COBWEB)
                || io.github.ningmeng123.mite.mining.GrassMining.matches(state) || state.is(Blocks.VINE))) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // The same final event, permission and loot path as a normal player harvest.
            if (!serverPlayer.gameMode.destroyBlock(pos)) return InteractionResult.FAIL;
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private InteractionResult till(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null || context.getClickedFace() != Direction.UP
                || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) return InteractionResult.PASS;
        BlockState result = level.getBlockState(pos).getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
        if (result == null) return InteractionResult.PASS;
        level.playSound(player, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!level.isClientSide) {
            level.setBlock(pos, result, 11);
            EquipmentDurability.hurt(context.getItemInHand(), 50, player, LivingEntity.getSlotForHand(context.getHand()));
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, result));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (definition.family() != ToolFamily.SHEARS || !(entity instanceof IShearable target)) return InteractionResult.PASS;
        BlockPos pos = entity.blockPosition();
        if (!target.isShearable(player, stack, entity.level(), pos)) return InteractionResult.PASS;
        List<ItemStack> drops = target.onSheared(player, stack, entity.level(), pos);
        if (!entity.level().isClientSide) {
            for (ItemStack drop : drops) target.spawnShearedDrop(entity.level(), pos, drop);
            EquipmentDurability.hurt(stack, 50, player, LivingEntity.getSlotForHand(hand));
        }
        entity.gameEvent(GameEvent.SHEAR, player);
        return InteractionResult.sidedSuccess(entity.level().isClientSide);
    }
}
