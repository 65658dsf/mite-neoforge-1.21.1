package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Narrow resource-state rewrites; never rewrite noise references which share an ID prefix. @author NingMeng123 */
public final class DecorativeResourceRules {
    private DecorativeResourceRules() {}
    public static void replaceBlockIdentity(JsonElement element, String from, String to) {
        if (element == null) return;
        if (element.isJsonObject()) for (var entry : element.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("Name") && entry.getValue().isJsonPrimitive() && entry.getValue().getAsString().equals(from))
                entry.setValue(new JsonPrimitive(to));
            else replaceBlockIdentity(entry.getValue(), from, to);
        }
        else if (element.isJsonArray()) element.getAsJsonArray().forEach(entry -> replaceBlockIdentity(entry, from, to));
    }
}
