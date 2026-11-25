package io.github.ningmeng123.mite.client.loading;

import java.util.List;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Installs the loading artwork before Minecraft and the mod constructors run. @author NingMeng123 */
public final class MiteLoadingMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MiteEarlyLoadingScreen.install();
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("io.github.ningmeng123.mite.mixin.BetterCombatLastAttackAccess")
                || mixinClassName.equals("io.github.ningmeng123.mite.mixin.BetterCombatVanillaPlayerMixin")) {
            return FMLLoader.getLoadingModList().getModFileById("bettercombat") != null;
        }
        if (mixinClassName.equals("io.github.ningmeng123.mite.mixin.client.PunchyBetterCombatCompatMixin")) {
            var loadingMods = FMLLoader.getLoadingModList();
            return loadingMods.getModFileById("punchy") != null
                    && loadingMods.getModFileById("bettercombat") != null;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
