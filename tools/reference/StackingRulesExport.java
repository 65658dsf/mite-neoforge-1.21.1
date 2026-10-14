/* Guarded R196 stack-limit and identity oracle. Developer: NingMeng123. */
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executes registration and pure in-memory ItemStack methods only. */
public final class StackingRulesExport {
    private static Object row(Object... values) { return RegistryExport.map(values); }

    @SuppressWarnings("removal")
    public static void main(String[] args) throws Exception {
        Path workspace = Path.of(args[0]).toAbsolutePath().normalize();
        var guard = new RegistryExport.ReadOnlyGuard(List.of(workspace,
                Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()));
        Class.forName("RegistryExport$GuardProbe");
        guard.checkPermission(new java.io.FilePermission(workspace.toString(), "read"));
        System.setSecurityManager(guard);
        var checks = RegistryExport.guardTests(workspace);
        Class.forName("aqz"); Class.forName("yc"); aaf.a();
        List<Object> items = new ArrayList<>();
        for (yc item : yc.g) {
            if (item == null) continue;
            List<Object> subtypes = new ArrayList<>();
            for (Object object : item.getSubItems()) {
                ye stack = (ye) object;
                int subtype = stack.getItemSubtype();
                int pristine = item.getItemStackLimit(subtype, 0);
                boolean undamagedStackable = stack.f();
                Boolean damagedStackable = null;
                if (stack.g()) {
                    ye damaged = new ye(item, 1, subtype).setItemDamage(1);
                    damagedStackable = damaged.f();
                }
                subtypes.add(row("subtype", subtype, "source_name", item.d(stack),
                        "max_stack_size", pristine,
                        "limit_damage_1", item.getItemStackLimit(subtype, 1),
                        "limit_damage_max_int", item.getItemStackLimit(subtype, Integer.MAX_VALUE),
                        "is_stackable_pristine", undamagedStackable,
                        "is_stackable_damaged", damagedStackable));
            }
            List<Object> dynamic = new ArrayList<>();
            // Snow's creative variants are 0 and 3; its world drop/placement code uses all 0..7 depths.
            if (item instanceof zc) for (int subtype = 0; subtype < 8; subtype++)
                dynamic.add(row("subtype", subtype, "max_stack_size", item.getItemStackLimit(subtype, 0)));
            items.add(row("legacy_id", item.cv, "class", item.getClass().getName(),
                    "source_name", item.d(new ye(item)), "base_limit", item.getItemStackLimit(0, 0),
                    "limit_method_declared_by", item.getClass().getMethod("getItemStackLimit", int.class, int.class).getDeclaringClass().getName(),
                    "has_subtypes", item.n(), "num_subtypes", item.getNumSubtypes(),
                    "damageable", item.p(), "has_quality", item.hasQuality(),
                    "subtype_limits", subtypes, "dynamic_subtype_limits", dynamic));
        }
        List<Object> blocks = new ArrayList<>();
        for (aqz block : aqz.s) if (block != null)
            blocks.add(row("legacy_id", block.cF, "class", block.getClass().getName(),
                    "max_stack_size", block.getItemStackLimit(),
                    "registered_item_limit", yc.g[block.cF] == null ? null : yc.g[block.cF].getItemStackLimit(0, 0)));
        ye first = new ye(yc.F, 2), same = new ye(yc.F, 7), named = new ye(yc.F, 2);
        named.e = new by(); named.e.a("probe_identity", "different");
        List<Object> identity = new ArrayList<>();
        identity.add(row("case", "ignore_stack_count", "expected", true, "actual", ye.areItemStacksEqual(first, same, true)));
        identity.add(row("case", "keep_stack_count", "expected", false, "actual", ye.areItemStacksEqual(first, same, false)));
        identity.add(row("case", "different_nbt", "expected", false, "actual", ye.areItemStacksEqual(first, named, true)));
        identity.add(row("case", "explicitly_ignore_nbt", "expected", true, "actual", ye.areItemStacksEqual(first, named, true, false, false, true)));
        ye artifact = new ye(yc.F, 2); artifact.setAsArtifact();
        identity.add(row("case", "different_artifact", "expected", false, "actual", ye.areItemStacksEqual(first, artifact, true)));
        ye snowA = new ye(aqz.aX, 1, 0), snowB = new ye(aqz.aX, 1, 3);
        identity.add(row("case", "different_subtype", "expected", false, "actual", ye.areItemStacksEqual(snowA, snowB, true)));
        ye toolA = new ye(yc.i), toolB = new ye(yc.i).setItemDamage(1);
        identity.add(row("case", "different_damage", "expected", false, "actual", ye.areItemStacksEqual(toolA, toolB, true)));
        identity.add(row("case", "explicitly_ignore_damage", "expected", true, "actual", ye.areItemStacksEqual(toolA, toolB, true, false, true)));
        for (Object check : identity) {
            var record = (java.util.Map<?, ?>) check;
            if (!record.get("expected").equals(record.get("actual"))) throw new AssertionError(record);
        }
        System.out.println("STACKING_ORACLE_BEGIN");
        System.out.println(RegistryExport.json(row("author", "NingMeng123", "java_version", System.getProperty("java.version"),
                "guard_checks_passed", checks, "game_main_invoked", false, "world_created", false,
                "activation_flags", row("professions_suppressed", RegistryExport.field(Class.forName("atv"), "professions_suppressed"),
                        "in_dev_mode", RegistryExport.field(Class.forName("atv"), "in_dev_mode")),
                "items", items, "blocks", blocks, "identity_checks", identity)));
        System.out.println("STACKING_ORACLE_END");
    }
}
