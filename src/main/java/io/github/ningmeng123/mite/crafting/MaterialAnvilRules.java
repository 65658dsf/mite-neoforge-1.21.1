package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.block.MaterialAnvilBlock;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.EquipmentQuality;
import io.github.ningmeng123.mite.item.R196RepairMaterial;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import java.util.ArrayDeque;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import io.github.ningmeng123.mite.Mite;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/** Repair resources, material capacity and source wear are independent of JEI. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MaterialAnvilRules {
    private static final ThreadLocal<ArrayDeque<UpdateScope>> UPDATE_SCOPES = new ThreadLocal<>();
    public static final class UpdateScope implements AutoCloseable {
        private AnvilUpdateEvent event;
        private UpdateScope() {}
        @Override public void close() {
            ArrayDeque<UpdateScope> scopes = UPDATE_SCOPES.get();
            if (scopes.peek() != this) throw new IllegalStateException("Anvil update scope closed out of order");
            scopes.pop();
            if (scopes.isEmpty()) UPDATE_SCOPES.remove();
        }
    }
    public record Material(String name, int components, boolean armor, boolean chainmail, boolean bow, boolean rod) {}
    public record Plan(ItemStack output, int consumed, int anvilWear, boolean repair) {
        public boolean allowed() { return !output.isEmpty(); }
    }
    private MaterialAnvilRules() {}
    public static UpdateScope beginUpdate() {
        UpdateScope scope = new UpdateScope();
        ArrayDeque<UpdateScope> scopes = UPDATE_SCOPES.get();
        if (scopes == null) { scopes = new ArrayDeque<>(); UPDATE_SCOPES.set(scopes); }
        scopes.push(scope);
        return scope;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void captureUpdate(AnvilUpdateEvent event) {
        ArrayDeque<UpdateScope> scopes = UPDATE_SCOPES.get();
        UpdateScope scope = scopes == null ? null : scopes.peek();
        // Native createResult posts one update event. Listener-initiated updates use a
        // nested scope; further manually posted events cannot replace this menu's event.
        if (scope != null && scope.event == null) scope.event = event;
    }
    public static boolean otherModControlsUpdate() {
        ArrayDeque<UpdateScope> scopes = UPDATE_SCOPES.get();
        UpdateScope scope = scopes == null ? null : scopes.peek();
        AnvilUpdateEvent event = scope == null ? null : scope.event;
        return event != null && (event.isCanceled() || !event.getOutput().isEmpty());
    }
    public static float capacity(BlockState state) {
        return state.getBlock() instanceof MaterialAnvilBlock anvil ? WorkbenchRules.materialCapacity(anvil.material())
                : state.getBlock() instanceof AnvilBlock ? 8F : 0F;
    }
    public static Material material(ItemStack stack) {
        var shield = MiteShields.definition(stack);
        if (shield != null) return new Material(shield.material(), 1, false, false, false, false);
        if (stack.is(Items.FISHING_ROD)) return new Material("iron", 1, false, false, false, true);
        if (stack.getItem() instanceof R196RepairMaterial item)
            return new Material(item.repairMaterial(), item.repairComponents(), false, false, !item.repairFishingRod(), item.repairFishingRod());
        var tool = MiteTools.definition(stack);
        if (tool != null) return new Material(tool.material(), tool.family().components(), false, false, false, false);
        var armor = EquipmentDurability.armorProfile(stack);
        if (armor == null) return null;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        // Diamond/netherite are explicitly modern; their retained vanilla repairs are separate.
        if (id.startsWith("diamond_") || id.startsWith("netherite_")) return null;
        String name = id.startsWith("golden_") ? "gold" : id.startsWith("chainmail_") ? "iron"
                : id.startsWith("rusted_iron_") ? "rusted_iron" : id.startsWith("ancient_metal_") ? "ancient_metal"
                : id.substring(0, id.indexOf('_'));
        return new Material(name, armor.components(), true, armor.chainmail(), false, false);
    }
    public static Item repairItem(String material) {
        return switch (material) {
            case "copper" -> MiteMaterials.COPPER_NUGGET.get();
            case "silver" -> MiteMaterials.SILVER_NUGGET.get();
            case "gold" -> Items.GOLD_NUGGET;
            case "iron", "rusted_iron" -> Items.IRON_NUGGET;
            case "ancient_metal" -> MiteMaterials.ANCIENT_METAL_NUGGET.get();
            case "mithril" -> MiteMaterials.MITHRIL_NUGGET.get();
            case "adamantium" -> MiteMaterials.ADAMANTIUM_NUGGET.get();
            default -> null;
        };
    }
    public static Plan plan(Player player, BlockState anvil, ItemStack first, ItemStack second) {
        Plan disenchanting = io.github.ningmeng123.mite.utility.UtilityDisenchanting.plan(first, second);
        if (disenchanting != null) return disenchanting;
        Plan enchanting = io.github.ningmeng123.mite.enchantment.R196BookAnvil.plan(first, second);
        if (enchanting != null) return enchanting;
        Plan repair = previewRepair(anvil, first, second);
        if (repair != null && repair.allowed() && repair.repair() && !CraftingQuality.canRepair(player, first))
            return new Plan(ItemStack.EMPTY, 0, 0, true);
        return repair;
    }

    /** Pure material-repair preview; the live menu separately rechecks the owner's quality ability. */
    public static Plan previewRepair(BlockState anvil, ItemStack first, ItemStack second) {
        Material material = material(first);
        if (material == null || !first.isDamageableItem() || second.isEmpty()
                || second.has(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)) return null;
        Item repairItem = repairItem(material.name());
        boolean consumable = repairItem != null && second.is(repairItem);
        boolean combination = second.is(first.getItem()) && first.isDamaged()
                && (first.isEnchanted() || second.isDamaged()) && !second.isEnchanted()
                && EquipmentQuality.sameQuality(first, second)
                && (MiteShields.definition(first) == null || EquipmentQuality.sameColor(first, second));
        // R196 rejects ingot repairs; also stop mismatching source equipment combinations.
        if (!consumable && !combination) return new Plan(ItemStack.EMPTY, 0, 0, false);
        if (!first.isDamaged() || WorkbenchRules.materialCapacity(material.name()) > capacity(anvil))
            return new Plan(ItemStack.EMPTY, 0, 0, true);
        ItemStack result = first.copyWithCount(1);
        EquipmentQuality.preserve(first, result);
        if (combination) {
            EquipmentQuality.combineWithoutBonus(first, second, result);
            return new Plan(result, 1, 0, true);
        }
        int cost = material.rod() ? 1 : material.components() * 2;
        var repair = FunctionalBlockRules.repair(first.getDamageValue(), first.getMaxDamage(), cost,
                material.chainmail(), second.getCount());
        if (repair.consumed() < 1) return new Plan(ItemStack.EMPTY, 0, 0, true);
        result.setDamageValue(repair.damage());
        // vc: mattockIron(12800)/bootsIron(64) = 200; shovelMithril(25600)/bowMithril(128) = 200.
        int multiplier = material.armor() || material.bow() ? 200 : material.rod() ? 200 / 9 : 1;
        return new Plan(result, repair.consumed(), (int)Math.min((long)repair.restored() * multiplier, Integer.MAX_VALUE), true);
    }
}
