"""Reconcile existing material leaves against registered R196 values. Author: NingMeng123."""
import collections
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def write(path, value):
    (ROOT / path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    catalog = read("spec/item-catalog-015.json")
    source = read("reference-data/runtime-registry.json")
    scope_path = ROOT / "spec/remaining-catalog-028.json"
    if not scope_path.exists():
        remaining = [e for e in catalog["entries"] if e["default_included"] and e["status"] != "added"]
        assert len(remaining) == 60
        write("spec/remaining-catalog-028.json", {
            "author": "NingMeng123", "count": 60,
            "category_counts": dict(collections.Counter(e["category"] for e in remaining)),
            "entries": [{"name": e["name"], "url": e["url"], "category": e["category"],
                         "modern_id": e["proposed_modern_id"], "legacy_id": e["source_registry"]["id"],
                         "metadata": e["source_registry"]["item_metadata"], "source_kind": e["source_registry"]["kind"],
                         "baseline_status": e["status"]} for e in remaining]})
    scope = read("spec/remaining-catalog-028.json")
    materials = {e["scalars"]["akc.name"]: e for e in source["materials"]}
    items = {e["id"]: e for e in source["items"]}
    rows = []
    for e in scope["entries"]:
        if e["category"] != "材料":
            continue
        original = items[e["legacy_id"]]
        flags = {}
        for hazard in ["fire", "lava", "acid", "pepsin"]:
            flags["harmed_by_" + hazard] = any(materials[m]["scalars"]["akc.is_harmed_by_" + hazard] for m in original["materials"])
        rows.append({"id": e["modern_id"], "legacy_id": e["legacy_id"], "materials": original["materials"],
                     "maximum_stack": original["scalars"]["yc.cw"],
                     "component_difficulty": original["scalars"]["yc.crafting_difficulty_as_component"],
                     "furnace_xp": original["scalars"]["yc.xp_reward"], **flags, "source": original["source"]})
    assert len(rows) == 18
    data = {"author": "NingMeng123", "archive_sha256": source["archive_sha256"], "items": rows,
            "scope": "18 existing material identities; source conversion, drop and furnace mechanics already implemented. Fire/lava susceptibility is reconciled here."}
    write("spec/materials-028.json", data)
    write("src/main/resources/mite/materials-028.json", data)
    print("Extracted 18 existing source material profiles; remaining scope contains 60 leaves.")


if __name__ == "__main__":
    main()
