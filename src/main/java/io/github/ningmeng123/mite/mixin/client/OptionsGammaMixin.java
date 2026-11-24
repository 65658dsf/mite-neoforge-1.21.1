package io.github.ningmeng123.mite.mixin.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/** Restore the fixed gamma in options files edited outside the game. @author NingMeng123 */
@Mixin(Options.class)
public abstract class OptionsGammaMixin {
    @Shadow @Final private File optionsFile;
    @Unique private static final Pattern MITE_GAMMA_LINE = Pattern.compile("(?<![^\\r\\n])gamma:[^\\r\\n]*");
    @Unique private static final Logger MITE_LOGGER = LogUtils.getLogger();

    @Inject(method = "load(Z)V", at = @At("HEAD"))
    private void mite$restoreSavedMinimumGamma(boolean limited, CallbackInfo ci) {
        if (limited) return;

        try {
            Path optionsPath = optionsFile.toPath();
            if (!Files.isRegularFile(optionsPath)) return;
            Path file = optionsPath.toRealPath();
            String savedOptions = Files.readString(file);
            String lockedOptions = MITE_GAMMA_LINE.matcher(savedOptions).replaceAll("gamma:0.0");
            if (savedOptions.equals(lockedOptions)) return;

            // Saving the full Options object during startup can overwrite keys registered by other mods.
            Path temporary = Files.createTempFile(file.getParent(), ".mite-options-", ".tmp");
            try {
                Files.writeString(temporary, lockedOptions);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            MITE_LOGGER.warn("Could not restore the minimum gamma in options.txt", e);
        }
    }
}
