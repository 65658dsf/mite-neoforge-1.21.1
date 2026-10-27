package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Instant whole-gem sacrifice; fragments never qualify. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class GemSacrifice {
    public static final int COOLDOWN_TICKS = 10;

    private GemSacrifice() {}

    public static int experienceValue(ItemStack stack) {
        int original=stack.is(Items.LAPIS_LAZULI)?25:stack.is(Items.QUARTZ)?50:stack.is(Items.EMERALD)?250:stack.is(Items.DIAMOND)?500:0;
        return Math.max(0,io.github.ningmeng123.mite.api.MiteExtensions.policy().gemSacrificeExperience(stack,original));
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        int value = experienceValue(stack);
        if (event.isCanceled() || value == 0 || stack.isEmpty() || event.getEntity().isSpectator()
                || !io.github.ningmeng123.mite.api.MiteExtensions.policy().canSacrificeGem(event.getEntity(),stack,true)) return;
        var player = event.getEntity();
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }
        for (var item : new net.minecraft.world.item.Item[]{Items.LAPIS_LAZULI, Items.QUARTZ, Items.EMERALD, Items.DIAMOND})
            player.getCooldowns().addCooldown(item, COOLDOWN_TICKS);
        if (!event.getLevel().isClientSide()) {
            var item = stack.getItem();
            ItemStack broken = stack.copyWithCount(1);
            stack.shrink(1);
            PlayerExperience.addPoints(player, value);
            player.awardStat(Stats.ITEM_USED.get(item));
            event.getLevel().playSound(null, player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.375F, 0.8F + player.getRandom().nextFloat() * 0.4F);
            if (event.getLevel() instanceof ServerLevel level) breakingParticles(level, player, broken);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }

    private static void breakingParticles(ServerLevel level, Player player, ItemStack broken) {
        float pitch = -player.getXRot() * (float) (Math.PI / 180.0D);
        float yaw = -player.getYRot() * (float) (Math.PI / 180.0D);
        var random = player.getRandom();
        var particle = new ItemParticleOption(ParticleTypes.ITEM, broken);
        for (int fragment = 0; fragment < 5; fragment++) {
            Vec3 velocity = new Vec3((random.nextFloat() - 0.5D) * 0.1D, random.nextDouble() * 0.1D + 0.1D, 0D)
                    .xRot(pitch).yRot(yaw);
            Vec3 position = new Vec3((random.nextFloat() - 0.5D) * 0.3D, -random.nextFloat() * 0.6D - 0.3D, 0.6D)
                    .xRot(pitch).yRot(yaw).add(player.getX(), player.getEyeY(), player.getZ());
            level.sendParticles(particle, position.x, position.y, position.z, 0, velocity.x, velocity.y + 0.05D, velocity.z, 1D);
        }
    }
}
