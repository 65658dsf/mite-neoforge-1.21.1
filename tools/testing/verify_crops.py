"""Audit crop evidence and generated resource links without starting Minecraft. Author: NingMeng123."""
from __future__ import annotations

import argparse
from collections import Counter
from contextlib import ExitStack
import hashlib
from io import BytesIO
import itertools
import json
from pathlib import Path
import sys
from zipfile import BadZipFile, ZipFile

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key: " + key)
        result[key] = value
    return result


def parse_json(raw: bytes | str):
    return json.loads(raw, object_pairs_hook=unique_object)


def resource_path(identifier: str, folder: str, extension: str) -> str:
    namespace, separator, path = identifier.partition(":")
    if not separator:
        namespace, path = "minecraft", namespace
    if not namespace or not path or ".." in Path(path).parts or path.startswith(("/", "\\")):
        raise ValueError("Invalid resource location: " + identifier)
    return f"assets/{namespace}/{folder}/{path}{extension}"


class Audit:
    def __init__(self):
        self.counts = Counter()
        self.failures: list[str] = []
        self.checksums = {}

    def check(self, condition, message: str):
        self.counts["checks"] += 1
        if not condition:
            self.failures.append(message)
        return bool(condition)

    def stage(self, label: str, operation):
        try:
            operation()
        except (OSError, ValueError, KeyError, TypeError, BadZipFile) as error:
            self.failures.append(f"{label}: {error}")


class Resources:
    """Read actual resource roots, then fall back to the locally pinned vanilla resource JAR."""
    def __init__(self, audit: Audit, roots: list[Path], vanilla: ZipFile):
        self.audit, self.roots, self.vanilla = audit, roots, vanilla
        self.vanilla_names = set(vanilla.namelist())
        self.model_cache = {}
        self.checked_textures = set()
        self.checked_item_models = set()

    def read(self, path: str) -> bytes:
        matches = [root / path for root in self.roots if (root / path).is_file()]
        if matches:
            raw = matches[0].read_bytes()
            for duplicate in matches[1:]:
                self.audit.check(duplicate.read_bytes() == raw, "Conflicting local resources: " + path)
            return raw
        if path in self.vanilla_names:
            return self.vanilla.read(path)
        raise FileNotFoundError("Missing local/vanilla resource: " + path)

    def local_json(self, path: str):
        self.audit.check(any((root / path).is_file() for root in self.roots), "Expected generated/local resource: " + path)
        return parse_json(self.read(path))

    def texture(self, identifier: str):
        if identifier in self.checked_textures:
            return
        self.read(resource_path(identifier, "textures", ".png"))
        self.checked_textures.add(identifier)
        self.audit.counts["resolved_texture_references"] += 1

    def model(self, identifier: str, ancestors=()):
        if identifier in self.model_cache:
            return self.model_cache[identifier]
        if identifier in ancestors:
            raise ValueError("Cyclic model parents: " + " -> ".join((*ancestors, identifier)))
        if identifier.startswith("builtin/"):
            # Engine-provided model loaders have no model JSON. Crop models use ordinary parents.
            return {}, []
        path = resource_path(identifier, "models", ".json")
        model = parse_json(self.read(path))
        textures, elements = {}, []
        if "parent" in model:
            inherited, parent_elements = self.model(model["parent"], (*ancestors, identifier))
            textures.update(inherited)
            elements = parent_elements
        textures.update(model.get("textures", {}))
        elements = model.get("elements", elements)

        # Abstract vanilla parents may intentionally leave a variable for their child.
        # Validate their final inherited graph in each concrete referenced model below.
        self.model_cache[identifier] = textures, elements
        self.audit.counts["resolved_model_files"] += 1
        return textures, elements

    def concrete_model(self, identifier: str):
        textures, elements = self.model(identifier)

        def resolve(value: str, seen=()):
            if not isinstance(value, str):
                raise ValueError("Non-string texture in " + identifier)
            if value.startswith("#"):
                key = value[1:]
                if key in seen or key not in textures:
                    raise ValueError("Unresolved/cyclic texture " + value + " in " + identifier)
                resolve(textures[key], (*seen, key))
            else:
                self.texture(value)

        for value in textures.values():
            resolve(value)
        for element in elements:
            for face in element.get("faces", {}).values():
                resolve(face["texture"])

    def item_model_exists(self, identifier: str):
        if identifier in self.checked_item_models:
            return
        self.read(resource_path(identifier, "models", ".json").replace("/models/", "/models/item/", 1))
        self.checked_item_models.add(identifier)
        self.audit.counts["agricultural_tool_item_models"] += 1


def verify_source(audit: Audit, manifest, archive_path: Path):
    audit.check(manifest.get("schema_version") == 1, "Unsupported crop-source schema")
    raw_archive = archive_path.read_bytes()
    archive_sha = sha(raw_archive)
    audit.check(archive_sha == manifest["archive_sha256"], "R196 release archive SHA-256 differs from evidence")
    audit.checksums[archive_path.name] = archive_sha
    classes = manifest["classes"]
    methods = manifest["methods"]
    rules = manifest["rules"]
    for key, rows in (("classes", classes), ("methods", methods), ("rules", rules)):
        audit.check(len(rows) == manifest["counts"][key], "Crop evidence count differs: " + key)
    audit.check(len({row["class"] for row in classes}) == len(classes), "Duplicate source class identities")
    audit.check(len({row["id"] for row in rules}) == len(rules), "Duplicate source rule IDs")
    sources = {}
    with ZipFile(BytesIO(raw_archive)) as archive:
        names = archive.namelist()
        for row in classes:
            def check_class():
                name = row["class"]
                candidates = [path for path in names if path.endswith("/class/" + name + ".class")]
                audit.check(len(candidates) == 1, "Ambiguous/missing original binary: " + name)
                if len(candidates) == 1:
                    audit.check(sha(archive.read(candidates[0])) == row["class_sha256"], "Original archive class hash mismatch: " + name)
                    audit.counts["original_class_hashes"] += 1
                source = ROOT / row["source"]
                audit.check(sha(source.read_bytes()) == row["source_sha256"], "Decompiler source hash mismatch: " + name)
                sources[name] = source.read_text(encoding="utf-8").splitlines()
                audit.counts["decompiled_source_hashes"] += 1
            audit.stage("source class " + row["class"], check_class)
    identities = set()
    method_ranges = set()
    for row in methods:
        def check_method():
            name, member = row["class"], row["member"]
            lines = sources[name]
            first, last = row["line"], row["end_line"]
            audit.check(1 <= first <= last <= len(lines), "Method line range out of source bounds: " + name + ":" + member)
            body = "\n".join(lines[first - 1:last]).rstrip()
            audit.check(sha(body.encode("utf-8")) == row["normalized_source_sha256"], f"Method source hash mismatch: {name}:{member} line {first}")
            declaration = "static initializer" if member == "<clinit>" else body[:body.index("{")].strip()
            audit.check(declaration == row["declaration"], f"Method declaration differs: {name}:{member} line {first}")
            identity = name + ":" + ("<init>" if member == name else member)
            identities.add(identity)
            method_ranges.add((name, first, last))
            audit.counts["method_source_hashes"] += 1
        audit.stage("method " + row["class"] + ":" + row["member"], check_method)
    audit.check(len(method_ranges) == len(methods), "Duplicate method source ranges")
    for rule in rules:
        audit.check(bool(rule.get("formula_or_behavior")) and bool(rule.get("acceptance")), "Rule missing behavior/acceptance: " + rule["id"])
        for reference in rule["references"]:
            owner, _, member = reference.partition(":")
            # Java enum constants compile into <clinit>, but CFR renders them as enum declarations.
            # Their complete original class and decompiled-source hashes were checked above.
            implicit_enum_initializer = member == "<clinit>" and owner in sources \
                and any("enum " + owner + " {" in line for line in sources[owner])
            audit.check(reference in identities or implicit_enum_initializer,
                        "Rule references an unindexed method: " + rule["id"] + " -> " + reference)
            if reference not in identities and implicit_enum_initializer:
                audit.counts["implicit_enum_initializer_class_references"] += 1
            audit.counts["rule_method_references"] += 1
    audit.counts["source_rules"] = len(rules)


def verify_copied_textures(audit: Audit, manifest, pack_path: Path):
    audit.check(manifest.get("schema_version") == 1, "Unsupported crop-assets schema")
    pack_raw = pack_path.read_bytes()
    audit.check(sha(pack_raw) == manifest["resource_pack_sha256"], "Original resource-pack archive SHA-256 mismatch")
    audit.checksums[pack_path.name] = sha(pack_raw)
    entries = manifest["copied_textures"]
    audit.check(len(entries) == manifest["counts"]["copied_textures"], "Copied texture manifest count differs")
    audit.check(len({row["destination"] for row in entries}) == len(entries), "Duplicate copied texture destinations")
    with ZipFile(BytesIO(pack_raw)) as pack:
        for row in entries:
            def check_texture():
                original = pack.read(row["source_member"])
                copied = (ROOT / row["destination"]).read_bytes()
                audit.check(sha(original) == row["sha256"] == sha(copied), "Crop texture hash mismatch: " + row["destination"])
                audit.check(row["copied_unchanged"] and copied == original, "Texture is not an unchanged source copy: " + row["destination"])
                with Image.open(BytesIO(copied)) as png:
                    png.load()
                    audit.check(png.format == "PNG" and list(png.size) == row["size"] == [16, 16], "Crop source sprite must be a valid 16x16 PNG: " + row["destination"])
                audit.counts["copied_texture_hashes_and_sizes"] += 1
            audit.stage("copied texture " + row["destination"], check_texture)


def variants_by_state(blockstate):
    result = {}
    for key, value in blockstate["variants"].items():
        properties = tuple(sorted(tuple(part.split("=", 1)) for part in key.split(",")))
        if properties in result:
            raise ValueError("Duplicate semantic blockstate variant: " + key)
        result[properties] = value
    return result


def variant_model(resources: Resources, value, expected: str | None = None):
    choices = value if isinstance(value, list) else [value]
    resources.audit.check(bool(choices), "Empty blockstate model variant")
    for choice in choices:
        if expected is not None:
            resources.audit.check(choice["model"] == expected, "Blockstate model differs from evidence: " + expected)
        resources.concrete_model(choice["model"])


def verify_models(audit: Audit, manifest, resources: Resources):
    mapping = manifest["blockstate_mappings"]
    audit.check(len(mapping) == manifest["counts"]["crop_blockstates"], "Crop blockstate count differs from manifest")
    for crop, frames in mapping.items():
        def check_crop():
            variants = variants_by_state(resources.local_json(f"assets/minecraft/blockstates/{crop}.json"))
            maximum = frames["max_age"]
            audit.check(all(len(frames[category]) == maximum + 1 for category in ("healthy_models", "blighted_models", "dead_models")), "Incomplete recorded model age mapping: " + crop)
            expected_states = set()
            for age, blighted, dead in itertools.product(range(maximum + 1), (False, True), (False, True)):
                properties = tuple(sorted({"age": str(age), "mite_blighted": str(blighted).lower(), "mite_dead": str(dead).lower()}.items()))
                expected_states.add(properties)
                category = "dead_models" if dead else "blighted_models" if blighted else "healthy_models"
                variant_model(resources, variants[properties], frames[category][age])
                audit.counts["native_crop_age_disease_death_variants"] += 1
            audit.check(set(variants) == expected_states, "Missing/extra crop age/disease/death states: " + crop)
            resources.local_json(f"data/minecraft/loot_table/blocks/{crop}.json")
            audit.counts["crop_loot_tables"] += 1
        audit.stage("blockstate " + crop, check_crop)
    # Onion retains its pre-existing property names; validate all persisted combinations too.
    def check_onion():
        variants = variants_by_state(resources.local_json("assets/mite/blockstates/onion_crop.json"))
        expected_states = set()
        for age, blighted, dead in itertools.product(range(8), (False, True), (False, True)):
            properties = tuple(sorted({"age": str(age), "blighted": str(blighted).lower(), "dead": str(dead).lower()}.items()))
            expected_states.add(properties)
            variant_model(resources, variants[properties])
            audit.counts["preserved_onion_variants"] += 1
        audit.check(set(variants) == expected_states, "Missing/extra preserved onion save-state variants")
    audit.stage("preserved onion blockstate", check_onion)

    def check_farmland():
        variants = variants_by_state(resources.local_json("assets/minecraft/blockstates/farmland.json"))
        expected_states = set()
        for moisture, fertilized in itertools.product(range(8), (False, True)):
            properties = tuple(sorted({"moisture": str(moisture), "mite_fertilized": str(fertilized).lower()}.items()))
            expected_states.add(properties)
            if fertilized:
                model = "mite:block/farmland_" + ("wet" if moisture else "dry") + "_fertilized"
            else:
                model = "minecraft:block/" + ("farmland_moist" if moisture else "farmland")
            variant_model(resources, variants[properties], model)
            audit.counts["farmland_moisture_fertilizer_variants"] += 1
        audit.check(set(variants) == expected_states and len(variants) == 16, "Farmland must cover all 16 wetness/fertilizer save states")
    audit.stage("farmland blockstate", check_farmland)
    for fruit in ("melon", "pumpkin"):
        audit.stage("fruit explosion loot " + fruit, lambda fruit=fruit: resources.local_json(f"data/mite/loot_table/crop_explosion/{fruit}.json"))
    audit.stage("melon loot", lambda: resources.local_json("data/minecraft/loot_table/blocks/melon.json"))
    def check_snow_support():
        support = resources.local_json("data/minecraft/tags/block/snow_layer_can_survive_on.json")
        audit.check("minecraft:farmland" in support["values"], "Crop snowfall soil support tag is missing farmland")
    audit.stage("snow support tag", check_snow_support)


def verify_enchantments(audit: Audit, manifest, resources: Resources):
    specification = manifest["enchantment_data"]
    ids = specification["ids"]
    audit.check(len(ids) == manifest["counts"]["enchantments"], "Enchantment manifest count differs")
    for identifier in ids:
        def check_enchantment():
            namespace, path = identifier.split(":", 1)
            data = resources.local_json(f"data/{namespace}/enchantment/{path}.json")
            for field in ("max_level", "weight", "min_cost", "max_cost", "anvil_cost", "slots", "supported_items"):
                audit.check(data[field] == specification[field], "Enchantment differs from recorded data: " + identifier + "/" + field)
            audit.counts["agricultural_enchantments"] += 1
        audit.stage("enchantment " + identifier, check_enchantment)
    supported = specification["supported_items"]
    audit.check(supported.startswith("#"), "Agricultural supported-items reference must be a tag")
    namespace, path = supported[1:].split(":", 1)
    tag = resources.local_json(f"data/{namespace}/tags/item/{path}.json")
    audit.check(tag.get("replace") is False and bool(tag["values"]), "Agricultural tool tag is empty or replacing external entries")
    values = tag["values"]
    audit.check(len(values) == len(set(values)), "Duplicate agricultural tool entries")
    for identifier in values:
        audit.stage("agricultural tool " + identifier, lambda identifier=identifier: resources.item_model_exists(identifier))
    for tag_name in ("in_enchanting_table", "tradeable", "on_random_loot", "non_treasure"):
        data = resources.local_json(f"data/minecraft/tags/enchantment/{tag_name}.json")
        audit.check(data.get("replace") is False and set(ids) <= set(data["values"]), "Missing normal acquisition tags for agricultural enchantments: " + tag_name)
        audit.counts["agricultural_enchantment_tags"] += 1


def find_vanilla_resources(explicit: Path | None) -> Path:
    if explicit is not None:
        return explicit
    candidates = sorted((ROOT / "build/moddev/artifacts").glob("*client-extra*minecraft-resources.jar"))
    if not candidates:
        cache = Path.home() / ".gradle/caches/neoformruntime"
        candidates = sorted(cache.rglob("*client-extra*.jar")) if cache.exists() else []
    for candidate in candidates:
        with ZipFile(candidate) as jar:
            if "assets/minecraft/models/block/wheat_stage0.json" in jar.namelist():
                return candidate
    raise FileNotFoundError("No local Minecraft vanilla resource JAR; run the pinned project setup or pass --vanilla-resources")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-archive", type=Path, default=ROOT / "MITE 1.6.4 R196.zip")
    parser.add_argument("--resource-pack", type=Path, default=ROOT / "MITE Resource Pack 1.6.4.zip")
    parser.add_argument("--vanilla-resources", type=Path)
    parser.add_argument("--resources", type=Path, action="append", help="Resource roots; defaults to main and generated sources")
    parser.add_argument("--report", type=Path, default=ROOT / "build/verification/crop-resource-source-audit.json")
    args = parser.parse_args()
    audit = Audit()
    source = parse_json((ROOT / "spec/crop-source.json").read_bytes())
    assets = parse_json((ROOT / "spec/crop-assets.json").read_bytes())
    for path in ("spec/crop-source.json", "spec/crop-assets.json"):
        audit.checksums[path] = sha((ROOT / path).read_bytes())
    audit.stage("source evidence", lambda: verify_source(audit, source, args.source_archive))
    audit.stage("source texture copies", lambda: verify_copied_textures(audit, assets, args.resource_pack))
    vanilla_path = None
    try:
        vanilla_path = find_vanilla_resources(args.vanilla_resources)
        roots = args.resources or [ROOT / "src/main/resources", ROOT / "src/generated/resources"]
        with ExitStack() as stack:
            vanilla = stack.enter_context(ZipFile(vanilla_path))
            audit.checksums[vanilla_path.name] = sha(vanilla_path.read_bytes())
            resources = Resources(audit, roots, vanilla)
            audit.stage("generated crop models", lambda: verify_models(audit, assets, resources))
            audit.stage("agricultural enchantments", lambda: verify_enchantments(audit, assets, resources))
    except (OSError, ValueError, BadZipFile) as error:
        audit.failures.append("Vanilla resource lookup: " + str(error))
    report = {
        "schema_version": 1,
        "author": "NingMeng123",
        "status": "passed" if not audit.failures else "failed",
        "counts": dict(audit.counts),
        "checksums": audit.checksums,
        "vanilla_resource_jar": str(vanilla_path) if vanilla_path else None,
        "failures": audit.failures,
        "limits": [
            "Static evidence and resource validation only; no game, renderer or GPU was executed.",
            "Original method identity checks require the decompiled files named by crop-source.json; no unrelated reference-data manifests are read.",
            "Implicit enum static initializers use the already-verified whole class/source identity when CFR does not expose a standalone method body.",
            "Model graph coverage is the crop-assets manifest, preserved onion blockstates and farmland, not every resource in the repository.",
            "This audit does not by itself validate crop growth behavior, optional-mod compatibility or player visual acceptance."
        ]
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "counts": report["counts"], "failure_count": len(audit.failures), "report": str(args.report)}, ensure_ascii=False))
    for failure in audit.failures[:12]:
        print("FAIL: " + failure, file=sys.stderr)
    return 1 if audit.failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
