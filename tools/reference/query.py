"""Search local class evidence without loading Java. Author: NingMeng123."""
import argparse
import json
import re
from analyze import ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default="", help="Regex matching original SourceFile or JVM class name")
    parser.add_argument("--method", help="Regex matching member method names")
    parser.add_argument("--calls-to", help="Regex matching referenced owner.name")
    parser.add_argument("--details", action="store_true", help="Include matching method code metadata")
    args = parser.parse_args()
    classes = json.loads((ROOT / ".reference/r196/class-details.json").read_text(encoding="utf-8"))
    result = []
    for info in classes:
        if not re.search(args.source, info["source_file"] or info["name"], re.I): continue
        methods = []
        for method in info["methods"]:
            if args.method and not re.search(args.method, method["name"], re.I): continue
            refs = method.get("code", {}).get("references", [])
            if args.calls_to:
                refs = [r for r in refs if isinstance(r["target"], dict) and re.search(args.calls_to, r["target"].get("owner", "") + "." + r["target"].get("name", ""), re.I)]
                if not refs: continue
            row = {"name": method["name"], "descriptor": method["descriptor"]}
            if args.details: row = method.copy()
            if args.calls_to: row["matching_references"] = refs
            methods.append(row)
        if (args.method or args.calls_to) and not methods: continue
        result.append({"class": info["name"], "source_file": info["source_file"], "methods": methods})
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__": main()
