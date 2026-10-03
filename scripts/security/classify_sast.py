"""Gate raw security findings against a source-bound, explicitly reviewed context manifest."""
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import xml.etree.ElementTree as ET

from security_checks import require, spotbugs_complete, strict_json


def production_inventory(repo):
    root = repo.resolve()
    source_root = root / "src/main/java"
    def regular_source_path(path):
        info = path.lstat()
        require(not stat.S_ISLNK(info.st_mode)
                and not (getattr(info, "st_file_attributes", 0) & 0x400),
                "Production source links/reparse points require re-review")
    for path in (root / "src", root / "src/main", source_root):
        regular_source_path(path)
    result = {}
    for directory, dirs, files in os.walk(source_root, followlinks=False):
        for name in dirs + files:
            regular_source_path(Path(directory) / name)
        for name in sorted(files):
            if not name.endswith(".java"):
                continue
            path = Path(directory) / name
            require(path.resolve().is_relative_to(root), "Production source escapes repository")
            result[path.relative_to(root).as_posix()] = hashlib.sha256(
                path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
    require(result, "No production Java source inventory")
    return dict(sorted(result.items()))


def build_inventory(repo):
    return {"pom.xml": hashlib.sha256((repo / "pom.xml").read_bytes().replace(b"\r\n", b"\n")).hexdigest()}


def key(finding):
    return (finding["type"], int(finding["priority"]), finding["instance_hash"],
            int(finding["occurrence"]), int(finding["occurrence_max"]))


def classify(report, manifest_path, repo, plugin):
    completeness = spotbugs_complete(report)
    root = ET.parse(report).getroot()
    manifest = strict_json(manifest_path.read_bytes())
    require(manifest["schema"] == 1, "Unsupported reviewed-context manifest")
    require(manifest["source_hash_algorithm"] == "sha256-utf8-lf", "Unknown source hash normalization")
    require(root.attrib["version"] == manifest["analyzer"]["spotbugs_version"],
            "SpotBugs analyzer changed; re-review contexts")
    require(manifest["analyzer"]["findsecbugs_version"] == "1.14.0", "FindSecBugs policy changed")
    require(hashlib.sha256(plugin.read_bytes()).hexdigest() == manifest["analyzer"]["findsecbugs_jar_sha256"],
            "FindSecBugs analyzer artifact changed")
    require(production_inventory(repo) == manifest["production_java_inventory"],
            "Production sources/caller inventory changed; contextual re-review required")
    require(build_inventory(repo) == manifest["build_configuration"],
            "Maven build/dependency configuration changed; contextual re-review required")
    reviewed = Counter()
    for finding in manifest["reviewed_findings"]:
        require(finding["type"] in ("PATH_TRAVERSAL_IN", "PATH_TRAVERSAL_OUT")
                and int(finding["priority"]) == 2, "Unapproved reviewed finding category/priority")
        require(finding["source"] in manifest["production_java_inventory"], "Unbound finding source")
        require(finding["rationale"] and finding["caller_bindings"], "Missing contextual review evidence")
        require(all(p in manifest["production_java_inventory"] for p in finding["caller_bindings"]),
                "Unbound relevant finding caller")
        fingerprint = key(finding)
        require(fingerprint not in reviewed, "Duplicate reviewed fingerprint")
        reviewed[fingerprint] = 1
    observed = Counter()
    low = 0
    for bug in root.findall("BugInstance"):
        if bug.attrib["category"] != "SECURITY":
            continue
        priority = int(bug.attrib["priority"])
        require(priority in (1, 2, 3), "Unexpected security finding priority")
        if priority == 3:
            low += 1
            continue
        observed[key({"type": bug.attrib["type"], "priority": priority,
                      "instance_hash": bug.attrib["instanceHash"],
                      "occurrence": bug.attrib["instanceOccurrenceNum"],
                      "occurrence_max": bug.attrib["instanceOccurrenceMax"]})] += 1
    require(observed == reviewed,
            f"Security gate: {sum((observed-reviewed).values())} unreviewed Medium/High findings; "
            f"{sum((reviewed-observed).values())} stale/unused reviewed contexts")
    return dict(completeness, raw_medium_high=sum(observed.values()),
                reviewed_contextual_findings=sum(reviewed.values()), unreviewed_medium_high=0,
                raw_low=low, production_java_files=len(manifest["production_java_inventory"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--plugin", type=Path, required=True,
                        help="Exact FindSecBugs artifact used by the Maven analysis")
    args = parser.parse_args()
    try:
        print(json.dumps(classify(args.report, args.manifest, args.repo, args.plugin), indent=2))
    except (ValueError, KeyError, TypeError, OSError, ET.ParseError) as error:
        print(f"Security evidence invalid: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
