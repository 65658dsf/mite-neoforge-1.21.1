package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.progression.GearModifier;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Disposable real client world: server upgrades, native inventory sync and rendered tooltips. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class GearModifierClientSmoke {
    private static boolean started, done;
    private static int ticks;
    private static CompletableFuture<Void> setup;
    private static AuditScreen screen;
    private static final JsonObject REPORT = new JsonObject();
    private static final Path OUTPUT = Path.of("build/verification/source-modifier-client.json").toAbsolutePath();
    private GearModifierClientSmoke() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("mite.gearModifierSmoke") || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        try {
            if (!started) {
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(mc.screen instanceof TitleScreen)) return;
                started = true;
                REPORT.addProperty("author", "NingMeng123");
                REPORT.addProperty("existing_worlds_opened", false);
                String world = "source-modifier-smoke-" + System.currentTimeMillis();
                REPORT.addProperty("world", world);
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(123456789L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            check(++ticks < 3600, "Timed out waiting for client synchronization");
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (setup == null) {
                if (mc.screen != null || ticks < 180) return;
                UUID playerId = mc.player.getUUID();
                setup = CompletableFuture.runAsync(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    check(player != null, "Integrated server player exists");
                    player.setGameMode(GameType.SURVIVAL);
                    ItemStack sword = new ItemStack(Items.IRON_SWORD);
                    check(GearProgression.maxLevel(sword) == 25, "Standalone iron sword has a 25-level cap");
                    for (int level = 0; level < 25; level++) {
                        long threshold = (150L + 100L * level) * 1000L;
                        check(GearProgression.nextLevelExperienceMillis(sword, level) == threshold,
                                "The live server sword uses the approved weapon threshold at L" + level);
                        GearProgression.award(sword, threshold, player);
                        check(GearProgression.state(sword).level() == level + 1
                                && GearProgression.state(sword).experienceMillis() == 0,
                                "Each exact current-level threshold advances once without a remainder");
                    }
                    check(GearProgression.state(sword).level() == 25, "Twenty-five independent server awards reach the cap");
                    check(GearProgression.state(sword).affixes().keySet().stream().allMatch(key -> GearModifier.fromId(key) != null),
                            "Server only awards source modifiers");
                    ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
                    pickaxe.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 1, 0,
                            java.util.Map.of(GearModifier.TOOL_EFFICIENCY.id(), 1), 1));
                    player.getInventory().setItem(0, sword);
                    player.getInventory().setItem(1, pickaxe);
                    player.getInventory().selected = 0;
                    player.inventoryMenu.broadcastChanges();
                    player.containerMenu.broadcastChanges();
                }, mc.getSingleplayerServer());
                return;
            }
            if (!setup.isDone()) return;
            setup.join();
            ItemStack sword = mc.player.getInventory().getItem(0), pickaxe = mc.player.getInventory().getItem(1);
            if (!sword.is(Items.IRON_SWORD) || GearProgression.state(sword).level() != 25
                    || GearModifier.TOOL_EFFICIENCY.level(pickaxe) != 1) return;
            if (screen == null) {
                REPORT.addProperty("server_to_client_component_sync", true);
                REPORT.addProperty("sword_level", GearProgression.state(sword).level());
                REPORT.addProperty("sword_modifier_ranks", GearProgression.state(sword).affixes().values().stream().mapToInt(Integer::intValue).sum());
                check(GearProgression.nextLevelExperienceMillis(pickaxe, 1) == 150_000L,
                        "The synchronized level-one iron pickaxe uses the approved one-hundred-fifty-point tool threshold");
                String experienceLine = Component.translatable("tooltip.mite.gear_experience", "0", "150").getString();
                check(pickaxe.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL).stream()
                                .anyMatch(line -> line.getString().equals(experienceLine)),
                        "The real localized tooltip displays zero of one hundred fifty experience on the synchronized pickaxe");
                REPORT.addProperty("iron_pickaxe_level_one_required_experience", 150);
                REPORT.addProperty("iron_pickaxe_experience_tooltip", experienceLine);
                check(GearModifier.values().length == 17, "All 17 source names exist");
                for (GearModifier modifier : GearModifier.values())
                    check(!modifier.displayName().getString().startsWith("modifier.mite."), "Native language resolves " + modifier.id());
                screen = new AuditScreen(sword.copy(), pickaxe.copy());
                mc.setScreen(screen);
                return;
            }
            if (screen.frames < 12) return;
            Path screenshot = OUTPUT.resolveSibling("source-modifier-client.png");
            Files.createDirectories(OUTPUT.getParent());
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(screenshot); }
            REPORT.addProperty("native_tooltip_frames", screen.frames);
            REPORT.addProperty("screenshot", screenshot.toString());
            finish(mc, null);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static final class AuditScreen extends Screen {
        private final ItemStack sword, pickaxe;
        private int frames;
        private AuditScreen(ItemStack sword, ItemStack pickaxe) { super(Component.literal("Source equipment modifiers")); this.sword = sword; this.pickaxe = pickaxe; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff142029);
            graphics.drawString(font, "MITE - source modifiers / real synchronized stacks", 12, 10, 0xffffff);
            graphics.renderItem(sword, 12, 30);
            graphics.renderItem(pickaxe, width / 2, 30);
            List<Component> swordLines = sword.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL);
            List<Component> pickaxeLines = pickaxe.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL);
            graphics.renderComponentTooltip(font, swordLines, 22, 58);
            graphics.renderComponentTooltip(font, pickaxeLines, width / 2 + 12, 58);
            frames++;
        }
    }

    private static void check(boolean passed, String message) { if (!passed) throw new IllegalStateException(message); }
    private static void finish(Minecraft mc, Throwable failure) {
        done = true;
        REPORT.addProperty("status", failure == null ? "passed_real_world_sync_and_rendered_tooltips" : "failed");
        if (failure != null) { REPORT.addProperty("failure", failure.toString()); Mite.LOGGER.error("Source modifier client smoke failed", failure); }
        try { Files.createDirectories(OUTPUT.getParent()); Files.writeString(OUTPUT, new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception writeFailure) { Mite.LOGGER.error("Cannot write source modifier report", writeFailure); }
        mc.stop();
    }
}
