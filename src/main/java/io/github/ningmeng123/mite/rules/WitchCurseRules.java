package io.github.ningmeng123.mite.rules;

import java.util.Objects;
import java.util.Random;

/** Pure release-selection oracle from tv.cursePlayer and Curse.getRandomCurse. @author NingMeng123 */
public final class WitchCurseRules {
    public static final int REALIZATION_DELAY = 6000;
    private WitchCurseRules() {}
    public static int selectId(int witchSeed, String username) {
        Objects.requireNonNull(username, "username");
        int usernameHash = 0;
        for (int i = 0; i < username.length(); ++i) usernameHash += username.charAt(i) * i;
        Random random = new Random(witchSeed + usernameHash);
        int id;
        do { id = random.nextInt(64); } while (id < 1 || id > 16);
        return id;
    }
}
