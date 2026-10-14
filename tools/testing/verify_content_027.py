"""Validate packaged resources and byte-identical R196 assets for batch 027. Author: NingMeng123."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, default=ROOT / "build/libs/mite-0.1.0.jar")
    parser.add_argument("--minecraft-client", type=Path, default=Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar")
    args = parser.parse_args()
    failures = []
    texture_records = list(read("spec/armor-content-027.json")["texture_manifest"])
    texture_records += read("spec/ranged-027.json")["textures"]
    texture_records += read("spec/metal-storage-027-textures.json")["textures"]
    pack_path = ROOT / "MITE Resource Pack 1.6.4.zip"
    with ZipFile(pack_path) as pack:
        for name in pack.namelist():
            target = None
            if name.endswith(".png"):
                if name.startswith("assets/minecraft/textures/blocks/anvil/"):
                    target = "assets/mite/textures/block/anvil/" + name.split("blocks/anvil/")[1]
                elif name.startswith("assets/minecraft/textures/entity/chest/"):
                    target = "assets/mite/textures/entity/chest/" + name.rsplit("/", 1)[1]
                elif "emerald_enchanting_table_" in name:
                    target = "assets/mite/textures/block/" + name.rsplit("/", 1)[1]
            if target:
                texture_records.append({"source": name, "target": target})
        with ZipFile(args.jar) as mod, ZipFile(args.minecraft_client) as base:
            names, vanilla = set(mod.namelist()), set(base.namelist())
            for row in texture_records:
                target = row["target"].removeprefix("src/main/resources/")
                expected = pack.read(row["source"])
                if target not in names or mod.read(target) != expected:
                    failures.append("Source PNG mismatch: " + target)
                if row.get("sha256") and hashlib.sha256(expected).hexdigest() != row["sha256"]:
                    failures.append("Manifest SHA mismatch: " + target)

            def resource(identifier, kind, suffix, source):
                if not isinstance(identifier, str) or identifier.startswith("#"):
                    return
                namespace, path = identifier.split(":", 1) if ":" in identifier else ("minecraft", identifier)
                if kind == "models" and path.startswith("builtin/"):
                    return
                ref = f"assets/{namespace}/{kind}/{path}{suffix}"
                if ref not in names and ref not in vanilla:
                    failures.append(f"Missing resource {ref} referenced by {source}")

            models = 0
            for name in sorted(names):
                if not name.endswith(".json"):
                    continue
                try:
                    data = json.loads(mod.read(name))
                except (ValueError, UnicodeDecodeError) as error:
                    failures.append(f"Invalid JSON {name}: {error}")
                    continue
                if "/models/" in name:
                    models += 1
                    resource(data.get("parent"), "models", ".json", name)
                    for value in data.get("textures", {}).values():
                        resource(value, "textures", ".png", name)
                    for override in data.get("overrides", []):
                        resource(override.get("model"), "models", ".json", name)
                if "/blockstates/" in name:
                    variants = list(data.get("variants", {}).values())
                    variants += [row["apply"] for row in data.get("multipart", [])]
                    for variant in variants:
                        for row in variant if isinstance(variant, list) else [variant]:
                            resource(row.get("model"), "models", ".json", name)

            scope = read("spec/content-catalog-027.json")
            translations = json.loads(mod.read("assets/mite/lang/zh_cn.json"))
            for entry in scope["entries"]:
                namespace, path = entry["modern_id"].split(":")
                if namespace != "mite":
                    continue
                key = f"{'block' if entry['source_kind'] == 'block' else 'item'}.{namespace}.{path}"
                if key not in translations:
                    failures.append("Missing requested Chinese translation: " + key)
                if not entry["block_only"]:
                    resource(f"{namespace}:item/{path}", "models", ".json", "requested catalog")
            for name in names:
                if name.startswith("mezz/jei/") or "/gametest/" in name or name.startswith(".reference/") or name.endswith(".java"):
                    failures.append("Development/API source leaked into JAR: " + name)
            metadata = mod.read("META-INF/neoforge.mods.toml").decode()
            if 'modId="jei"' in metadata.replace(" ", ""):
                failures.append("JEI dependency unexpectedly declared")
            mixins = json.loads(mod.read("mite.mixins.json"))
            for name in mixins["mixins"] + mixins["client"]:
                path = mixins["package"].replace(".", "/") + "/" + name.replace(".", "/") + ".class"
                if path not in names:
                    failures.append("Missing declared Mixin class: " + path)

    report = {"author": "NingMeng123", "requested_catalog_entries": scope["count"],
              "packaged_models_checked": models, "original_pngs_checked": len(texture_records),
              "jar_sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(), "failures": failures,
              "client_visual_test": "pending_user_test"}
    output = ROOT / "build/verification/027-resource-audit.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    raise SystemExit(bool(failures))


if __name__ == "__main__":
    main()
