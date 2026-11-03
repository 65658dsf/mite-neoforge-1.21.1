package io.github.ningmeng123.mite.client.config;

import java.util.Arrays;
import java.util.Locale;

/** Space-separated words can match different parts of the same setting. @author NingMeng123 */
public final class ConfigSearchQuery {
    private ConfigSearchQuery() {}

    public static boolean matches(String query, String... fields) {
        String text = String.join(" ", fields).toLowerCase(Locale.ROOT);
        return Arrays.stream(query.strip().toLowerCase(Locale.ROOT).split("\\s+"))
                .allMatch(text::contains);
    }
}
