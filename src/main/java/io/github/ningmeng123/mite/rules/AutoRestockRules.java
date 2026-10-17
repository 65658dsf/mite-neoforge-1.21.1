package io.github.ningmeng123.mite.rules;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** R196 similarity priorities and circular hotbar-before-inventory traversal. @author NingMeng123 */
public final class AutoRestockRules {
    public static final long INVENTORY_DELAY_MILLIS = 250L;
    public static final long EXPIRES_MILLIS = 3_000L;
    private static final List<String> WEAPONS = List.of("SWORD", "BATTLE_AXE", "WAR_HAMMER", "DAGGER", "CLUB", "PICKAXE", "CUDGEL", "KNIFE");
    private AutoRestockRules() {}

    public static int nextSlot(int selected, int inventorySize, IntUnaryOperator similarity) {
        if (selected < 0 || selected >= 9 || inventorySize < 9) return -1;
        int best = -1, score = 0;
        for (int i = 0; i < inventorySize; i++) {
            int slot = i < 9 ? (selected + i) % 9 : i;
            int candidate = similarity.applyAsInt(slot);
            if (candidate > score) { best = slot; score = candidate; }
        }
        return best;
    }

    public static int familySimilarity(String previous, String candidate) {
        if (previous == null || candidate == null) return 0;
        if (previous.equals(candidate)) return 99;
        List<String> alternatives = switch (previous) {
            case "PICKAXE" -> prepend(List.of("WAR_HAMMER"), WEAPONS);
            case "WAR_HAMMER" -> prepend(List.of("PICKAXE"), WEAPONS);
            case "AXE" -> prepend(List.of("BATTLE_AXE", "HATCHET"), WEAPONS);
            case "BATTLE_AXE" -> prepend(WEAPONS, List.of("AXE", "HATCHET"));
            case "HATCHET" -> List.of("AXE", "BATTLE_AXE");
            case "SHOVEL" -> List.of("MATTOCK", "HOE");
            case "MATTOCK" -> List.of("HOE", "SHOVEL");
            case "HOE" -> List.of("MATTOCK", "SHOVEL");
            case "SCYTHE" -> List.of("SWORD", "DAGGER", "KNIFE");
            case "SWORD", "DAGGER", "KNIFE" -> WEAPONS;
            default -> List.of();
        };
        int index = alternatives.indexOf(candidate);
        return index < 0 ? 0 : 98 - index;
    }
    private static List<String> prepend(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
