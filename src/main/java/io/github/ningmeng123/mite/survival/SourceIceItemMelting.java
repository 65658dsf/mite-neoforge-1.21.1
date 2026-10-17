package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.SourceIceMeltingRules;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Source jv/ss melting affects actual main-inventory and dropped stacks on the server. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class SourceIceItemMelting {
    private SourceIceItemMelting() {}

    public static boolean melts(ItemStack stack) {
        return stack.is(Items.ICE) || stack.is(Items.SNOW) || stack.is(Items.SNOW_BLOCK) || stack.is(Items.SNOWBALL);
    }

    public static float temperature(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        int sourceBiome = R196MobSpawning.biomeId(biome, level);
        // Savanna has no R196 identity; explicitly map the modern hot, dry family to Desert.
        if (level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) && biome.unwrapKey().map(key -> key.location().getNamespace().equals("minecraft")
                && key.location().getPath().contains("savanna")).orElse(false)) sourceBiome = 2;
        return SourceIceMeltingRules.temperature(sourceBiome);
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) return;
        float chance = SourceIceMeltingRules.chance(player.isOnFire() ? 20F : temperature(player.serverLevel(), player.blockPosition()));
        if (!(chance > 0F)) return;
        boolean changed = false;
        // jv iterates ud.a: main inventory and hotbar, excluding armor, offhand and carried/container stacks.
        for (ItemStack stack : player.getInventory().items) {
            if (!melts(stack)) continue;
            int remaining = SourceIceMeltingRules.remaining(stack.getCount(), chance, player.getRandom()::nextFloat);
            if (remaining != stack.getCount()) { stack.setCount(remaining); changed = true; }
        }
        if (changed) player.getInventory().setChanged();
    }

    public static void itemTick(ItemEntity item) {
        if (!(item.level() instanceof ServerLevel level) || !item.isAlive() || !melts(item.getItem())) return;
        float chance = SourceIceMeltingRules.chance(temperature(level, item.blockPosition()));
        ItemStack stack = item.getItem();
        int remaining = SourceIceMeltingRules.remaining(stack.getCount(), chance, item.getRandom()::nextFloat);
        if (remaining == stack.getCount()) return;
        if (remaining == 0) item.discard();
        else item.setItem(stack.copyWithCount(remaining)); // Publish the changed count through native entity data.
    }
}
