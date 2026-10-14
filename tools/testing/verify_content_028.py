"""Audit batch 028 assets, the full included catalog and the deliverable JAR. Author: NingMeng123."""
import argparse
import hashlib
import json
import tomllib
from collections import Counter
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def split_id(identifier):
    return identifier.split(":", 1) if ":" in identifier else ("minecraft", identifier)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, default=ROOT / "build/libs/mite-0.1.0.jar")
    parser.add_argument("--minecraft-client", type=Path, default=Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar")
    parser.add_argument("--minecraft-assets", type=Path, default=Path.home() / ".gradle/caches/neoformruntime/assets")
    parser.add_argument("--minecraft-asset-index", default="17")
    parser.add_argument("--classes", type=Path, default=ROOT / "build/classes/java/main")
    args = parser.parse_args()
    failures = []
    counts = Counter()
    decorative = read("spec/decorative-028.json")
    utility = read("spec/utility-028.json")
    weapons = read("spec/weapons-028.json")
    assets = decorative["textures"] + weapons["textures"] + utility["assets"]
    pack_path = ROOT / "MITE Resource Pack 1.6.4.zip"
    pack_hash = hashlib.sha256(pack_path.read_bytes()).hexdigest()
    if pack_hash != utility["source_pack_sha256"]:
        failures.append("Original resource ZIP SHA-256 differs from the source manifest")
    index_path = args.minecraft_assets / "indexes" / (args.minecraft_asset_index + ".json")
    asset_index = json.loads(index_path.read_text(encoding="utf-8"))["objects"]

    def indexed_asset(ref):
        record = asset_index.get(ref.removeprefix("assets/"))
        if record is None:
            return None
        digest = record["hash"]
        path = args.minecraft_assets / "objects" / digest[:2] / digest
        if not path.is_file():
            return None
        data = path.read_bytes()
        if hashlib.sha1(data).hexdigest() != digest:
            failures.append("Minecraft indexed asset SHA-1 mismatch: " + ref)
        return data

    with ZipFile(pack_path) as pack, ZipFile(args.jar) as mod, ZipFile(args.minecraft_client) as base:
        names, vanilla = set(mod.namelist()), set(base.namelist())

        def exists(ref):
            return ref in names or ref in vanilla or (
                ref.startswith("assets/minecraft/") and ref.removeprefix("assets/") in asset_index
                and (args.minecraft_assets / "objects" / asset_index[ref.removeprefix("assets/")]["hash"][:2]
                     / asset_index[ref.removeprefix("assets/")]["hash"]).is_file())

        def load_resource(ref):
            if ref in names:
                return mod.read(ref)
            if ref in vanilla:
                return base.read(ref)
            return indexed_asset(ref)

        def resource(identifier, kind, suffix, source):
            if not isinstance(identifier, str) or identifier.startswith("#"):
                return
            namespace, path = split_id(identifier)
            if kind == "models" and path.startswith("builtin/"):
                return
            ref = f"assets/{namespace}/{kind}/{path}{suffix}"
            counts["resource_references_checked"] += 1
            if not exists(ref):
                failures.append(f"Missing resource {ref} referenced by {source}")

        targets = set()
        for row in assets:
            target = row["target"].removeprefix("src/main/resources/")
            if target in targets:
                failures.append("Duplicate 028 source asset target: " + target)
            targets.add(target)
            counts["original_pngs_checked" if target.endswith(".png") else "original_oggs_checked"] += 1
            expected = pack.read(row["source"])
            if not row.get("sha256") or hashlib.sha256(expected).hexdigest() != row["sha256"]:
                failures.append("Source asset manifest SHA-256 mismatch: " + target)
            if row.get("bytes") is not None and len(expected) != row["bytes"]:
                failures.append("Source asset manifest byte length mismatch: " + target)
            local = ROOT / "src/main/resources" / target
            if not local.is_file() or local.read_bytes() != expected:
                failures.append("Local source asset differs from original resource ZIP: " + target)
            if target not in names or mod.read(target) != expected:
                failures.append("Packaged source asset differs from original resource ZIP: " + target)
        if counts["original_pngs_checked"] != 115 or counts["original_oggs_checked"] != 4:
            failures.append("Expected 115 source PNGs and four source OGGs in the three 028 manifests")

        documents = {}
        for name in sorted(names):
            if not name.endswith(".json"):
                continue
            try:
                documents[name] = json.loads(mod.read(name))
            except (ValueError, UnicodeDecodeError) as error:
                failures.append(f"Invalid packaged JSON {name}: {error}")
        counts["packaged_json_files_checked"] = len(documents)
        for name, data in documents.items():
            if "/models/" in name:
                counts["packaged_models_checked"] += 1
                resource(data.get("parent"), "models", ".json", name)
                for value in data.get("textures", {}).values():
                    resource(value, "textures", ".png", name)
                for override in data.get("overrides", []):
                    resource(override.get("model"), "models", ".json", name)
            if "/blockstates/" in name:
                counts["packaged_blockstates_checked"] += 1
                variants = list(data.get("variants", {}).values())
                variants += [row["apply"] for row in data.get("multipart", [])]
                for variant in variants:
                    for row in variant if isinstance(variant, list) else [variant]:
                        resource(row.get("model"), "models", ".json", name)

        languages = {}
        mod_languages = {}
        for language in ("zh_cn", "en_us"):
            native_ref = f"assets/minecraft/lang/{language}.json"
            native = load_resource(native_ref)
            if native is None:
                failures.append("Missing local vanilla language asset: " + native_ref)
                languages[language] = {}
            else:
                languages[language] = json.loads(native)
            mod_languages[language] = documents.get(f"assets/mite/lang/{language}.json", {})
            if not mod_languages[language]:
                failures.append("Missing packaged MITE language: " + language)
            languages[language].update(mod_languages[language])
        catalog = read("spec/item-catalog-015.json")
        included = [entry for entry in catalog["entries"] if entry["default_included"]]
        counts["included_catalog_entries_checked"] = len(included)
        if len(included) != 347:
            failures.append("Expected all 347 included catalog entries")
        item_models = set()
        for entry in included:
            identifier = entry.get("actual_modern_id")
            if not identifier:
                failures.append("Missing actual registered catalog identity: " + entry["name"])
                continue
            namespace, path = split_id(identifier)
            keys = (f"item.{namespace}.{path}", f"block.{namespace}.{path}")
            for language in languages:
                translations = mod_languages[language] if namespace == "mite" else languages[language]
                if not any(translations.get(key) for key in keys):
                    failures.append(f"Missing {language} catalog translation: {identifier}")
                else:
                    counts[f"catalog_{language}_translations_checked"] += 1
            if entry.get("source_registry", {}).get("normally_carryable") is False:
                # Mantle is an included world block with no legal Item registration.
                counts["catalog_noncarryable_blocks_checked"] += 1
                ref = f"assets/{namespace}/blockstates/{path}.json"
                if not exists(ref):
                    failures.append("Missing noncarryable catalog block state: " + ref)
            else:
                item_models.add(identifier)
                counts["catalog_carryable_entries_checked"] += 1
                resource(f"{namespace}:item/{path}", "models", ".json", "included catalog")
        counts["distinct_catalog_item_models_checked"] = len(item_models)

        sound_events = {}
        native_sounds = load_resource("assets/minecraft/sounds.json")
        if native_sounds is None:
            failures.append("Missing indexed vanilla sounds.json")
        else:
            sound_events.update({f"minecraft:{name}": data for name, data in json.loads(native_sounds).items()})
        for name, data in documents.items():
            if name.startswith("assets/") and name.endswith("/sounds.json"):
                namespace = name.split("/")[1]
                sound_events.update({f"{namespace}:{event}": value for event, value in data.items()})
        for name, data in documents.items():
            if name.startswith("assets/") and name.endswith("/sounds.json"):
                namespace = name.split("/")[1]
                for event_name, event in data.items():
                    counts["packaged_sound_events_checked"] += 1
                    for sound in event.get("sounds", []):
                        identifier = sound if isinstance(sound, str) else sound.get("name")
                        if not identifier:
                            failures.append("Sound entry missing name: " + name + ":" + event_name)
                            continue
                        identifier = identifier if ":" in identifier else namespace + ":" + identifier
                        if isinstance(sound, dict) and sound.get("type", "file") == "event":
                            if identifier not in sound_events:
                                failures.append("Missing referenced sound event: " + identifier)
                        else:
                            resource(identifier, "sounds", ".ogg", name)
        for name, data in documents.items():
            if name.startswith("data/") and "/jukebox_song/" in name:
                counts["packaged_jukebox_songs_checked"] += 1
                event = data.get("sound_event")
                identifier = event if isinstance(event, str) else event.get("sound_id") if isinstance(event, dict) else None
                if identifier not in sound_events:
                    failures.append(f"Missing jukebox sound event {identifier} referenced by {name}")
                description = data.get("description", {})
                if isinstance(description, dict) and description.get("translate"):
                    for language, values in languages.items():
                        if not values.get(description["translate"]):
                            failures.append(f"Missing {language} jukebox description: " + description["translate"])
                if data.get("length_in_seconds", 0) <= 0 or not 1 <= data.get("comparator_output", 0) <= 15:
                    failures.append("Invalid modern jukebox song duration/comparator: " + name)
        for song in utility["songs"]:
            ref = f"data/mite/jukebox_song/{song['name']}.json"
            data = documents.get(ref)
            if data is None or abs(data.get("length_in_seconds", 0) - song["length_in_seconds"]) > 1e-6:
                failures.append("Packaged jukebox duration differs from source OGG granule manifest: " + ref)
            if data is None or data.get("comparator_output") != song["comparator_output"]:
                failures.append("Packaged jukebox comparator differs from declared modern adaptation: " + ref)

        compiled = {path.relative_to(args.classes).as_posix(): path for path in args.classes.rglob("*.class")}
        counts["compiled_main_classes_checked"] = len(compiled)
        if not compiled:
            failures.append("No compiled main classes found")
        for name, path in compiled.items():
            if name not in names or path.read_bytes() != mod.read(name):
                failures.append("Packaged class differs from current main compilation: " + name)
        for name in names:
            if name.endswith(".class") and name not in compiled:
                failures.append("Foreign/test/API class leaked into JAR: " + name)
            if (name.startswith(("mezz/jei/", ".reference/", "reference-data/", "spec/", "docs/", "tools/", "build/"))
                    or "/gametest/" in name or "/.cache/" in name
                    or name.endswith((".java", ".zip", ".jar", ".bbmodel"))):
                failures.append("Development/reference/dependency content leaked into JAR: " + name)
        metadata = tomllib.loads(mod.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        for dependencies in metadata.get("dependencies", {}).values():
            for dependency in dependencies:
                if dependency.get("modId") == "jei":
                    failures.append("JEI dependency unexpectedly declared; compatibility must remain optional")
        for metadata_mod in metadata.get("mods", []):
            if metadata_mod.get("modId") == "mite" and metadata_mod.get("authors") != "NingMeng123":
                failures.append("Incorrect packaged developer attribution")
        mixins = documents.get("mite.mixins.json", {})
        for name in mixins.get("mixins", []) + mixins.get("client", []):
            counts["declared_mixins_checked"] += 1
            path = mixins["package"].replace(".", "/") + "/" + name.replace(".", "/") + ".class"
            if path not in names:
                failures.append("Missing declared Mixin class: " + path)

    report = {"author": "NingMeng123", "batch": "028", **dict(counts),
              "original_resource_zip_sha256": pack_hash,
              "jar_sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
              "failures": failures, "passed": not failures,
              "client_visual_test": "pending_user_test"}
    output = ROOT / "build/verification/028-resource-audit.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({**report, "failures": failures[:20], "failure_count": len(failures)}, ensure_ascii=False))
    raise SystemExit(bool(failures))


if __name__ == "__main__":
    main()
