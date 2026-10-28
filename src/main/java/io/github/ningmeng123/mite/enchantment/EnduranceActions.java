package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.rules.BlockHardnessRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Only explicit source actions receive the Endurance discount. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class EnduranceActions {
    private EnduranceActions() {}

    public static float modifier(Player player) {
        return Math.max(0F, 1F - .2F * R196Enchantments.equipped(player, "endurance"));
    }

    public static void spend(Player player, float sourceAmount) {
        if (player == null || player.level().isClientSide || player.isCreative() || player.isSpectator()
                || player.getAbilities().invulnerable || sourceAmount <= 0F) return;
        player.causeFoodExhaustion(sourceAmount * modifier(player));
    }

    public static void mining(Player player) { spend(player, .01F); }
    public static void melee(Player player) { spend(player, .3F); }
    public static void throwing(Player player) { spend(player, .25F); }
    public static void tilling(Player player, float hardness) { spend(player, Math.max(0F, hardness) / 2F); }

    public static void placing(Player player, BlockState placed, BlockPos pos) {
        float hardness = MiningProgress.effectiveHardness(placed, placed.getDestroySpeed(player.level(), pos));
        var id = BuiltInRegistries.BLOCK.getKey(placed.getBlock());
        hardness = BlockHardnessRules.placementHardness(id.getNamespace(), id.getPath(), hardness);
        spend(player, Math.min(hardness, 20F));
    }

    @SubscribeEvent
    public static void bowDraw(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem) spend(player, .01F);
    }
}
