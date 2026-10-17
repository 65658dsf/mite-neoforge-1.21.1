"""Catalog the locally resolved Minecraft 1.21.1 sources/resources without running them.

Author: NingMeng123. IDs are literal registrations or resolved BlockItem aliases.
Legacy name matches are candidates, never proof that content is new or equivalent.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import zipfile


SOURCE_FILES = {
    "block": "net/minecraft/world/level/block/Blocks.java",
    "item": "net/minecraft/world/item/Items.java",
    "entity_type": "net/minecraft/world/entity/EntityType.java",
    "mob_effect": "net/minecraft/world/effect/MobEffects.java",
    "enchantment": "net/minecraft/world/item/enchantment/Enchantments.java",
    "potion": "net/minecraft/world/item/alchemy/Potions.java",
    "fluid": "net/minecraft/world/level/material/Fluids.java",
    "villager_profession": "net/minecraft/world/entity/npc/VillagerProfession.java",
    "villager_type": "net/minecraft/world/entity/npc/VillagerType.java",
    "menu": "net/minecraft/world/inventory/MenuType.java",
    "attribute": "net/minecraft/world/entity/ai/attributes/Attributes.java",
    "particle_type": "net/minecraft/core/particles/ParticleTypes.java",
    "sound_event": "net/minecraft/sounds/SoundEvents.java",
}
TRADE_SOURCE = "net/minecraft/world/entity/npc/VillagerTrades.java"
FIELD_RE = re.compile(r"\bpublic\s+static\s+final\s+([^;=\n]+?)\s+(\w+)\s*=\s*")
COMMENT_RE = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
REF_RE = re.compile(r"\b(Items|Blocks|MobEffects|Enchantments|Potions|VillagerProfession|VillagerType)\.([A-Z][A-Z0-9_]*)")
REF_KINDS = {Path(path).stem: kind for kind, path in SOURCE_FILES.items()}


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def strip_comments(text: str) -> str:
    def replace(match: re.Match) -> str:
        value = match.group()
        return re.sub(r"[^\n]", " ", value) if value.startswith(("//", "/*")) else value
    return COMMENT_RE.sub(replace, text)


def balanced_end(text: str, start: int, terminal: str = ";") -> int:
    """Find a statement terminator, or matching bracket; strings are opaque."""
    stack: list[str] = []
    quoted = None
    escaped = False
    pairs = {"(": ")", "[": "]", "{": "}"}
    for index in range(start, len(text)):
        char = text[index]
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quoted:
                quoted = None
            continue
        if char in "\"'":
            quoted = char
        elif char in pairs:
            stack.append(pairs[char])
        elif stack and char == stack[-1]:
            stack.pop()
            if terminal == "bracket" and not stack:
                return index + 1
        elif char == terminal and not stack:
            return index
    raise ValueError(f"Unterminated Java expression at offset {start}")


def fields(text: str):
    cleaned = strip_comments(text)
    for match in FIELD_RE.finditer(cleaned):
        end = balanced_end(cleaned, match.end())
        yield {
            "symbol": match[2], "declared_type": match[1].strip(),
            "expression": cleaned[match.end():end].strip(),
            "start": match.start(), "end": end,
            "line": text.count("\n", 0, match.start()) + 1,
            "end_line": text.count("\n", 0, end) + 1,
        }


def normalized_name(name: str) -> str:
    name = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name)
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def legacy_name_index(legacy: dict) -> dict:
    indices: dict[str, dict] = defaultdict(lambda: defaultdict(list))
    blocks = {row["id"]: row for row in legacy.get("blocks", [])}
    kind_mapping = {"items": "item", "blocks": "block", "entities": "entity_type", "enchantments": "enchantment"}
    for group, kind in kind_mapping.items():
        for position, row in enumerate(legacy.get(group, [])):
            names = set()
            for key in ("registered_name", "name_suffix"):
                if isinstance(row.get(key), str):
                    names.add(row[key])
            for key in ("yc.cB", "aqz.b", "aqz.f"):
                if isinstance(row.get("scalars", {}).get(key), str):
                    names.add(row["scalars"][key])
            if group == "items" and row["id"] in blocks:
                block = blocks[row["id"]]
                names.update(block.get("scalars", {}).get(key) for key in ("aqz.b", "aqz.f") if isinstance(block.get("scalars", {}).get(key), str))
            for symbol in row.get("symbols", []):
                suffix = symbol.rsplit(".", 1)[-1]
                if len(suffix) > 3:
                    names.add(suffix)
            for name in names:
                indices[kind][normalized_name(name)].append({
                    "id": row["id"], "name_evidence": name,
                    "pointer": f"/{group}/{position}",
                    "source_file": row.get("source", {}).get("source_file"),
                })
    return indices


def legacy_comparison(kind: str, registry_id: str | None, indices: dict) -> dict:
    if kind not in {"item", "block", "entity_type", "enchantment"}:
        return {"status": "no_comparable_legacy_registry_in_snapshot", "new_content": None}
    candidates = indices.get(kind, {}).get(normalized_name(registry_id.split(":", 1)[-1]), []) if registry_id else []
    unique = {(row["id"], row["name_evidence"]): row for row in candidates}
    return {
        "status": "name_candidates_require_semantic_review" if unique else "no_name_candidate_mapping_pending",
        "new_content": None, "candidates": list(unique.values()),
    }


def classify(kind: str, registry_id: str | None, expression: str = "") -> tuple[str, list[str]]:
    name = (registry_id or "").split(":", 1)[-1]
    exact = {
        "shield": "shield", "crossbow": "crossbow", "trident": "trident", "elytra": "elytra",
        "mace": "mace", "mending": "mending", "crafter": "crafter", "hopper": "hopper",
        "ancient_debris": "ancient_debris", "netherite_ingot": "netherite_ingot",
        "piston": "redstone_machines", "sticky_piston": "redstone_machines", "observer": "redstone_machines",
        "dispenser": "redstone_machines", "dropper": "redstone_machines",
        "blast_furnace": "blast_furnace_and_smoker", "smoker": "blast_furnace_and_smoker",
    }
    if kind in {"item", "block", "enchantment"} and name in exact:
        return "specific_modern_mechanic", [exact[name]]
    equipment = re.search(r"_(?:sword|pickaxe|axe|shovel|hoe|helmet|chestplate|leggings|boots)$", name)
    if kind == "item" and equipment:
        families = ["diamond_equipment"] if name.startswith("diamond_") else ["netherite_equipment"] if name.startswith("netherite_") else []
        return "equipment_requires_material_review", families
    if kind == "item" and ".food(" in expression:
        return "food_requires_nutrition_mapping", ["new_food_and_crops"]
    if kind == "item" and name.endswith("_spawn_egg"):
        return "spawn_egg", ["new_passive_and_hostile_mobs"]
    if kind == "block" and (name.endswith("_ore") or name in {"raw_copper_block", "raw_iron_block", "raw_gold_block"}):
        return "ore_and_material_source", ["ore_distribution"]
    if kind in {"block", "item"} and any(term in name for term in ("beehive", "bee_nest", "honey")):
        return "bees_and_honey", ["bees_and_honey"]
    if kind in {"block", "item"} and ("shulker_box" in name or name.endswith(("_boat", "_raft", "_minecart")) or name in {"bundle", "minecart", "compass", "recovery_compass"}):
        return "storage_and_transport", ["modern_storage_and_transport"]
    if kind == "block":
        return "block_behavior_requires_review", ["decorations_and_building"]
    if kind == "entity_type":
        return "entity_including_projectiles_requires_review", ["new_passive_and_hostile_mobs"]
    if kind == "enchantment":
        return "enchantment", ["modern_enchantments"]
    if kind in {"villager_profession", "villager_type", "trade_constructor"}:
        return "trading", ["villager_economy"]
    if kind in {"sound_event", "particle_type"}:
        return "audiovisual", []
    if kind == "recipe":
        return "manufacturing_recipe", []
    if kind == "loot_table":
        return "loot_source", ["structure_loot"]
    if kind.startswith("worldgen/"):
        return "world_generation_resource", ["overworld_terrain"]
    if kind == "structure":
        return "structure_template", ["structure_loot"]
    if kind.startswith("tags/"):
        return "tag_membership", []
    return {"item": "item_behavior_requires_review", "mob_effect": "status_effect", "potion": "potion", "attribute": "entity_attribute", "menu": "container_ui"}.get(kind, kind), []


def source_inventory(source_jar: zipfile.ZipFile, indices: dict) -> tuple[list, dict, dict, list]:
    entries, source_hashes, unresolved = [], {}, []
    symbols: dict[str, dict] = {}
    key_references = {}
    for owner in ("Blocks", "Items"):
        path = f"net/minecraft/references/{owner}.java"
        raw = source_jar.read(path)
        source_hashes[path] = sha256(raw)
        for declaration in fields(raw.decode("utf-8")):
            match = re.fullmatch(r'createKey\("([a-z0-9_./-]+)"\)', declaration["expression"])
            if match:
                key_references[owner + "." + declaration["symbol"]] = {
                    "id": "minecraft:" + match[1], "path": path, "line": declaration["line"]}
    for kind, path in SOURCE_FILES.items():
        raw = source_jar.read(path)
        text = raw.decode("utf-8")
        source_hashes[path] = sha256(raw)
        symbols[Path(path).stem] = {}
        for declaration in fields(text):
            expression = declaration["expression"]
            literal = re.match(r'[\w.]+\s*\(\s*"([a-z0-9_.:/-]+)"', expression)
            registry_id = None
            resolution = None
            block_alias = None
            reference_evidence = None
            if literal:
                registry_id = literal[1] if ":" in literal[1] else "minecraft:" + literal[1]
                resolution = "explicit_registration_or_resource_key_literal"
            elif reference := re.match(r"[\w.]+\s*\(\s*net\.minecraft\.references\.(Items|Blocks)\.([A-Z_]+)", expression):
                reference_evidence = key_references.get(reference[1] + "." + reference[2])
                if reference_evidence:
                    registry_id = reference_evidence["id"]
                    resolution = "explicit_ResourceKey_reference"
            elif kind == "item" and expression.startswith("registerBlock("):
                block_ref = re.search(r"\bBlocks\.([A-Z][A-Z0-9_]*)", expression)
                if block_ref:
                    block_alias = block_ref[1]
                    registry_id = symbols["Blocks"].get(block_alias)
                    resolution = "BlockItem_uses_registered_Block_key" if registry_id else None
            if not registry_id:
                helper = declaration["declared_type"].startswith(("Predicate<", "Codec<", "StreamCodec<")) or declaration["declared_type"] in {"int", "float", "double", "boolean"}
                unresolved.append({"registry": kind, "symbol": declaration["symbol"], "declared_type": declaration["declared_type"], "source": path, "line": declaration["line"], "expression": expression, "status": "nonregistry_helper" if helper else "dynamic_registration_not_guessed", "category": "nonregistry_helper" if helper else "unresolved_registration", "adaptation_status": "not_applicable" if helper else "pending_identifier_resolution"})
                continue
            category, families = classify(kind, registry_id, expression)
            row = {
                "registry": kind, "id": registry_id, "symbol": declaration["symbol"],
                "category": category, "adaptation_status": "pending_per_entry_review",
                "rule_family_candidates": families, "family_assignment_verified": False,
                "source": {"path": path, "line": declaration["line"], "end_line": declaration["end_line"]},
                "id_resolution": resolution,
                "explicit_feature_flags": sorted(set(re.findall(r"\bFeatureFlags\.([A-Z][A-Z0-9_]*)", expression))),
                "feature_activation": "not_runtime_verified",
                "legacy_comparison": legacy_comparison(kind, registry_id, indices),
            }
            implementation = re.search(r"\bnew\s+([\w.]+)\s*[<(]", expression)
            if implementation:
                row["declared_implementation"] = implementation[1]
            if block_alias:
                row["block_symbol"] = block_alias
            if reference_evidence:
                row["referenced_key_source"] = reference_evidence
            food = re.search(r"\.food\(Foods\.([A-Z][A-Z0-9_]*)\)", expression)
            if food:
                row["food_properties_symbol"] = food[1]
            entries.append(row)
            symbols[Path(path).stem][declaration["symbol"]] = registry_id
    return entries, symbols, source_hashes, unresolved


def references(value, pointer: str = ""):
    if isinstance(value, dict):
        for key, child in value.items():
            yield from references(child, pointer + "/" + key.replace("~", "~0").replace("/", "~1"))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from references(child, pointer + "/" + str(index))
    elif isinstance(value, str) and re.fullmatch(r"#?[a-z0-9_.-]+:[a-z0-9_./-]+", value):
        yield {"pointer": pointer, "value": value}


def resource_identity(path: str) -> tuple[str, str, str] | None:
    nested = re.fullmatch(r"data/minecraft/datapacks/([^/]+)/data/([^/]+)/(.+)\.(json|nbt)", path)
    base = re.fullmatch(r"data/([^/]+)/(.+)\.(json|nbt)", path)
    if nested:
        pack, namespace, tail = nested[1], nested[2], nested[3]
    elif base and not base[2].startswith("datapacks/"):
        pack, namespace, tail = "vanilla", base[1], base[2]
    else:
        return None
    parts = tail.split("/")
    width = 3 if parts[:2] == ["tags", "worldgen"] else 2 if parts[0] in {"worldgen", "tags"} else 1
    if len(parts) <= width:
        return None
    return pack, "/".join(parts[:width]), namespace + ":" + "/".join(parts[width:])


def resource_inventory(resource_jar: zipfile.ZipFile) -> tuple[list, list]:
    entries, packs = [], []
    for path in sorted(resource_jar.namelist()):
        if re.fullmatch(r"data/minecraft/datapacks/[^/]+/pack\.mcmeta", path):
            raw = resource_jar.read(path)
            packs.append({"id": path.split("/")[3], "source": path, "sha256": sha256(raw), "metadata": json.loads(raw), "activation_status": "not_assumed_enabled"})
        identity = resource_identity(path)
        if identity is None:
            continue
        pack, kind, registry_id = identity
        raw = resource_jar.read(path)
        category, families = classify(kind, registry_id)
        row = {
            "pack": pack, "resource_kind": kind, "id": registry_id,
            "category": category, "adaptation_status": "pending_per_entry_review",
            "rule_family_candidates": families, "family_assignment_verified": False,
            "source": {"path": path, "sha256": sha256(raw)},
            "legacy_comparison": "resource_schema_mapping_pending_not_automatically_new",
        }
        if path.endswith(".json"):
            parsed = json.loads(raw)
            row["format"] = "json"
            row["resource_references"] = list(references(parsed))
            if isinstance(parsed, dict):
                row["declared_type"] = parsed.get("type")
                if kind == "recipe":
                    row["result"] = parsed.get("result")
                    row["dynamic_result"] = "result" not in parsed
                if kind.startswith("tags/"):
                    row["replace"] = parsed.get("replace", False)
        else:
            row["format"] = "nbt_not_decoded"
        entries.append(row)
    return entries, packs


def trade_inventory(source_jar: zipfile.ZipFile, symbols: dict) -> tuple[list, str]:
    raw = source_jar.read(TRADE_SOURCE)
    text = raw.decode("utf-8")
    cleaned = strip_comments(text)
    sections = [field for field in fields(text) if "TRADE" in field["symbol"]]
    profession_contexts = []
    for match in re.finditer(r"\.put\s*\(\s*VillagerProfession\.([A-Z_]+)\s*,|VillagerProfession\.([A-Z_]+)\s*,\s*toIntMap\s*\(", cleaned):
        opening = cleaned.find("(", match.start())
        profession_contexts.append((match.start(), balanced_end(cleaned, opening, "bracket"), match[1] or match[2]))
    map_key_contexts = []
    for match in re.finditer(r"(\d+)\s*,\s*new\s+VillagerTrades\.ItemListing\[\]\s*\{", cleaned):
        opening = cleaned.rfind("{", match.start(), match.end())
        map_key_contexts.append((opening, balanced_end(cleaned, opening, "bracket"), int(match[1])))
    rows = []
    for match in re.finditer(r"\bnew\s+VillagerTrades\.(\w+)\s*\(", cleaned):
        end = balanced_end(cleaned, match.end() - 1, "bracket")
        args = cleaned[match.end():end - 1]
        section = next((field for field in sections if field["start"] <= match.start() <= field["end"]), None)
        resolved_refs, unresolved_refs = [], []
        for owner, symbol in dict.fromkeys(REF_RE.findall(args)):
            registry_id = symbols.get(owner, {}).get(symbol)
            target = {"symbol": owner + "." + symbol}
            if registry_id:
                target.update({"registry": REF_KINDS[owner], "id": registry_id})
                resolved_refs.append(target)
            else:
                unresolved_refs.append(target)
        line = text.count("\n", 0, match.start()) + 1
        profession = next((value for start, stop, value in profession_contexts if start <= match.start() < stop), None)
        map_key = next((value for start, stop, value in map_key_contexts if start <= match.start() < stop), None)
        rows.append({
            "source_site_id": "VillagerTrades.java:" + str(line) + ":" + str(match.start()),
            "constructor": match[1], "declaration_section": section["symbol"] if section else "factory_or_method_body",
            "profession_id": symbols["VillagerProfession"].get(profession) if profession else None,
            "literal_listing_map_key": map_key,
            "category": "trade_constructor_site", "adaptation_status": "pending_per_entry_review",
            "rule_family_candidates": ["villager_economy"], "family_assignment_verified": False,
            "source": {"path": TRADE_SOURCE, "line": line, "end_line": text.count("\n", 0, end) + 1},
            "arguments": re.sub(r"\s+", " ", args).strip(),
            "resolved_registry_references": resolved_refs, "unresolved_symbol_references": unresolved_refs,
            "semantic_status": "constructor_arguments_only_not_fully_expanded_trade_offers",
            "randomized_or_runtime_outputs": "not_guessed",
        })
    return rows, sha256(raw)


def extract(source_path: Path, resources_path: Path, legacy_path: Path) -> dict:
    legacy_raw = legacy_path.read_bytes()
    legacy = json.loads(legacy_raw)
    with zipfile.ZipFile(source_path) as sources, zipfile.ZipFile(resources_path) as resources:
        registry, symbols, source_hashes, unresolved = source_inventory(sources, legacy_name_index(legacy))
        data_resources, packs = resource_inventory(resources)
        trades, trade_hash = trade_inventory(sources, symbols)
        source_hashes[TRADE_SOURCE] = trade_hash
    result = {
        "schema_version": 1, "author": "NingMeng123", "minecraft": "1.21.1", "neoforge": "21.1.259",
        "status": "static_inventory_extracted_per_entry_adaptation_pending",
        "game_launched_by_extractor": False,
        "input_files": {str(path.as_posix()): sha256(path.read_bytes()) for path in (source_path, resources_path, legacy_path)},
        "source_entry_sha256": source_hashes,
        "summary": {
            "registry_declarations": len(registry), "registry_counts": dict(sorted(Counter(row["registry"] for row in registry).items())),
            "unresolved_static_declarations": len(unresolved),
            "unresolved_declaration_status_counts": dict(sorted(Counter(row["status"] for row in unresolved).items())),
            "data_resources": len(data_resources), "resource_counts": dict(sorted(Counter(row["resource_kind"] for row in data_resources).items())),
            "resource_pack_counts": dict(sorted(Counter(row["pack"] for row in data_resources).items())),
            "trade_constructor_sites": len(trades), "trade_section_counts": dict(sorted(Counter(row["declaration_section"] for row in trades).items())),
            "legacy_mapping_status_counts": dict(sorted(Counter(row["legacy_comparison"]["status"] for row in registry).items())),
            "adaptation_verified_entries": 0,
        },
        "semantics": {
            "id_resolution": "Literal registration/resource-key ID or BlockItem helper alias to an explicitly registered block; no inferred IDs for dynamic fields.",
            "legacy_comparison": "Name normalization finds review candidates only. No match is not evidence of novelty; subtypes, removed recipes, aliases and behavior require review.",
            "rule_family_candidates": "Routing hints, not approved per-entry design. The broad family design does not prove this entry has been balanced.",
            "resource_references": "Direct JSON strings only; tags and nested tables are not recursively resolved.",
            "trade_constructor_sites": "Includes nested factories and experimental declarations. A constructor callsite is not a fully expanded or currently enabled merchant offer.",
            "feature_flags": "Explicit declaration flags and built-in pack metadata are recorded; their presence is not runtime activation proof.",
            "static_inventory_limit": "Only listed vanilla source declarations and data resources; not every NeoForge/add-on dynamic registry or executable rule.",
        },
        "registries": registry, "unresolved_static_declarations": unresolved,
        "data_packs": packs, "data_resources": data_resources, "trade_constructor_sites": trades,
    }
    validate(result)
    return result


def validate(catalog: dict) -> None:
    registry_keys = [(row["registry"], row["id"]) for row in catalog["registries"]]
    if len(registry_keys) != len(set(registry_keys)):
        raise ValueError("Duplicate registry ID in static declarations; audit alias before merging")
    resource_keys = [(row["pack"], row["resource_kind"], row["id"]) for row in catalog["data_resources"]]
    if len(resource_keys) != len(set(resource_keys)):
        raise ValueError("Duplicate resource identity")
    required = {("item", "minecraft:diamond_pickaxe"), ("item", "minecraft:mace"), ("block", "minecraft:crafter"), ("entity_type", "minecraft:breeze"), ("enchantment", "minecraft:mending")}
    if not required.issubset(set(registry_keys)):
        raise ValueError("Missing known source declarations; inspect parser coverage")
    for row in catalog["registries"] + catalog["data_resources"] + catalog["trade_constructor_sites"]:
        if row["adaptation_status"] != "pending_per_entry_review" or row["family_assignment_verified"]:
            raise ValueError("Extractor cannot certify adaptation")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    artifacts = Path("build/moddev/artifacts")
    parser.add_argument("--sources", type=Path, default=artifacts / "neoforge-21.1.259-sources.jar")
    parser.add_argument("--resources", type=Path, default=artifacts / "neoforge-21.1.259-client-extra-aka-minecraft-resources.jar")
    parser.add_argument("--legacy", type=Path, default=Path("reference-data/runtime-registry.json"))
    parser.add_argument("--output", type=Path, default=Path("spec/modern-content-catalog.json"))
    parser.add_argument("--check", action="store_true", help="Compare deterministic output without writing files")
    args = parser.parse_args()
    result = extract(args.sources, args.resources, args.legacy)
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if args.output.read_text(encoding="utf-8") != text:
            raise SystemExit("Modern catalog is stale or was changed outside the extractor")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    print(json.dumps(result["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
