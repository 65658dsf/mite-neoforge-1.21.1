"""Cross-check runtime registrations against independent static evidence.

Developer: NingMeng123. Validates data extraction, not Minecraft gameplay.
"""
import json
from pathlib import Path
from analyze import ROOT, write_json
from catalog_content import base_construct


def validate(runtime, catalog):
    errors = []
    def require(condition, message):
        if not condition: errors.append(message)
    require(runtime.get("game_main_invoked") is False,"Game main must not be invoked")
    require(runtime.get("registration_error_message") is None,"Original registry reported an error")
    require(runtime.get("archive_sha256") == catalog["metadata"]["archive_sha256"],"Archive hash differs between evidence sources")
    item_ids = {r["id"] for r in runtime["items"]}
    matched_symbols, checked_ids, explained_nonregistry_symbols = 0, 0, 0
    for category in ["items","blocks","materials"]:
        rows = runtime[category]
        require(len({r["id"] for r in rows}) == len(rows),f"Duplicate {category} IDs")
        symbols = {s:r for r in rows for s in r["symbols"]}
        detached = {r["symbol"] for r in runtime.get("unregistered_static_"+category,[])}
        for declaration in catalog[category]:
            symbol = declaration["symbol"]
            if symbol in detached:
                explained_nonregistry_symbols += 1
                continue
            require(symbol in symbols,f"Static declaration absent from runtime: {symbol}")
            if symbol not in symbols: continue
            matched_symbols += 1
            constructor = base_construct(declaration["initializer"])
            if category in {"items","blocks"} and constructor and constructor["arguments"]:
                first = constructor["arguments"][0]
                if first["kind"] == "literal" and isinstance(first["value"],int):
                    expected = first["value"] + (256 if category == "items" else 0)
                    require(symbols[symbol]["id"] == expected,f"Constructor ID differs: {symbol}")
                    checked_ids += 1
    recipes = runtime["crafting_recipes"]
    require([r["order"] for r in recipes] == list(range(len(recipes))),"Recipe ordering not contiguous")
    for row in recipes:
        order = row["order"]
        require(row["difficulty"] >= 0,f"Negative recipe difficulty {order}")
        if row["class"] not in {"aai","aaj"}: require(row["dynamic"],f"Special recipe not marked dynamic: {order}")
        if row.get("width") is not None:
            require(len(row["components"]) == row["width"] * row["height"],f"Wrong shaped dimensions {order}")
        if not row["dynamic"]: require(row["output"] is not None and row["components"] is not None,f"Missing static recipe data {order}")
        for stack in [row["output"],*(row["components"] or [])]:
            if stack is None: continue
            require(stack["item_id"] in item_ids,f"Recipe references unknown item {order}: {stack['item_id']}")
            require(stack["count"] > 0 or row["dynamic"],f"Static recipe has nonpositive stack {order}")
    for row in runtime["furnace_recipes"]:
        require(row["input_item_id"] in item_ids,"Furnace input missing")
        require(row["output"]["item_id"] in item_ids,"Furnace output missing")
        require(row["output"]["count"] > 0,"Furnace output count invalid")
        require(row["required_heat"] in {1,2,3,4},"Unexpected furnace heat")
    for category in ["entities","enchantments"]:
        require(len({r["id"] for r in runtime[category]}) == len(runtime[category]),f"Duplicate {category} IDs")
    for row in runtime["entities"]:
        require(row["entity_instantiated"] is False,"Entity constructor was called")
        require(bool(row["registered_name"]),"Entity registry name missing")
    for row in runtime["enchantments"]:
        require(row["max_level"] >= 1,"Enchantment level invalid")
        require(bool(row["name_suffix"]),"Enchantment name missing")
    expected_probes = {"write_file","process","network","native_load","thread_create","disable_guard","unrelated_read"}
    require(set(runtime.get("guard_checks_passed",[])) == expected_probes,"Guard probes missing")
    return {"author":"NingMeng123","success":not errors,"errors":errors,"matched_static_symbols":matched_symbols,"explained_nonregistry_symbols":explained_nonregistry_symbols,"verified_constructor_ids":checked_ids,
            "crafting_recipe_count":len(recipes),"dynamic_recipe_count":sum(r["dynamic"] for r in recipes),"furnace_recipe_count":len(runtime["furnace_recipes"]),"entity_count":len(runtime["entities"]),"enchantment_count":len(runtime["enchantments"]),
            "note":"Four algorithmic recipes and heat-dependent furnace behavior still require semantic specifications; this validation is not a gameplay test."}


def main():
    directory = ROOT / "reference-data"
    runtime = json.loads((directory / "runtime-registry.json").read_text(encoding="utf-8"))
    catalog = json.loads((directory / "content-catalog.json").read_text(encoding="utf-8"))
    result = validate(runtime,catalog)
    write_json(directory / "runtime-validation.json",result)
    print(json.dumps(result,ensure_ascii=False,indent=2))
    if not result["success"]: raise SystemExit(1)


if __name__ == "__main__": main()
