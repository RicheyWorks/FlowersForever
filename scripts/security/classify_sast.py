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


# Eligibility is limited to these four reviewed boundaries. Exact raw identities,
# full source/caller inventory and build/analyzer bindings remain required below.
CONTEXTUAL_REVIEW_SELECTORS = {
    "h2_metadata_identifier": (
        "SQL_INJECTION_JDBC", "src/main/java/com/flowerfarm/backup/DatabaseIntegrityGuard.java",
        "com.flowerfarm.backup.DatabaseIntegrityGuard", "check",
        "(Ljava/nio/file/Path;)Lcom/flowerfarm/backup/DatabaseIntegrityGuard$Health;"),
    "installed_app_relaunch": (
        "COMMAND_INJECTION", "src/main/java/com/flowerfarm/update/InstalledAppLauncher.java",
        "com.flowerfarm.update.InstalledAppLauncher", "relaunch", "(Ljava/nio/file/Path;)V"),
    "verified_installer_handoff": (
        "COMMAND_INJECTION", "src/main/java/com/flowerfarm/update/PlatformInstaller.java",
        "com.flowerfarm.update.PlatformInstaller$ProcessStarter", "lambda$system$0",
        "(Ljava/util/List;Ljava/nio/file/Path;)V"),
    "static_authenticode_script": (
        "COMMAND_INJECTION", "src/main/java/com/flowerfarm/update/AuthenticodePublisherVerifier.java",
        "com.flowerfarm.update.AuthenticodePublisherVerifier", "signatureCommand",
        "(Ljava/nio/file/Path;Ljava/nio/file/Path;)Ljava/lang/ProcessBuilder;"),
}
FINDING_HASH_ALGORITHM = "sha256-spotbugs-annotations-unknown-source-order-v1"


def canonical_finding_hash(bug):
    """Preserve annotations while stabilizing unordered taint-source strings."""
    def semantic_text(value):
        return value if value and value.strip() else None

    def annotation(element, primary=False):
        children = [annotation(child) for child in element]
        if primary:
            # FindSecBugs emits this collection in varying order. Sort only its
            # existing slots; retain every value, duplicate and other annotation.
            slots = [index for index, child in enumerate(element)
                     if child.tag == "String" and child.attrib.get("role") == "Unknown source"]
            ordered = sorted((children[index] for index in slots),
                             key=lambda item: json.dumps(item, sort_keys=True, ensure_ascii=False))
            for index, item in zip(slots, ordered):
                children[index] = item
        return {"tag": element.tag,
                "attributes": {name: value for name, value in element.attrib.items()
                               if not (primary and name == "instanceHash")},
                "text": semantic_text(element.text), "tail": semantic_text(element.tail),
                "children": children}

    content = json.dumps(annotation(bug, primary=True), sort_keys=True, ensure_ascii=False,
                         separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(content).hexdigest()


def contextual_selector(finding):
    return (finding["type"], finding["source"], finding.get("class"),
            finding.get("method"), finding.get("method_signature"))


def observed_selector(bug):
    sources = [item for item in bug.findall("SourceLine") if item.attrib.get("primary") == "true"]
    methods = [item for item in bug.findall("Method") if item.attrib.get("primary") == "true"]
    require(len(sources) == 1 and len(methods) == 1, "Missing/ambiguous primary contextual finding location")
    source, method = sources[0], methods[0]
    require(source.attrib["classname"] == method.attrib["classname"],
            "Contextual finding source/method class mismatch")
    return (bug.attrib["type"], "src/main/java/" + source.attrib["sourcepath"],
            method.attrib["classname"], method.attrib["name"], method.attrib["signature"])


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


def reviewed_key(finding):
    return (finding["type"], int(finding["priority"]), finding["canonical_hash"],
            int(finding["occurrence"]), int(finding["occurrence_max"]))


def classify(report, manifest_path, repo, plugin):
    completeness = spotbugs_complete(report)
    root = ET.parse(report).getroot()
    manifest = strict_json(manifest_path.read_bytes())
    require(manifest["schema"] == 2, "Unsupported reviewed-context manifest")
    require(manifest["source_hash_algorithm"] == "sha256-utf8-lf", "Unknown source hash normalization")
    require(manifest["finding_hash_algorithm"] == FINDING_HASH_ALGORITHM,
            "Unknown contextual finding normalization")
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
    contextual_locations = {}
    raw_hashes = {}
    for finding in manifest["reviewed_findings"]:
        require(int(finding["priority"]) == 2, "Unapproved reviewed finding category/priority")
        path_context = finding["type"] in ("PATH_TRAVERSAL_IN", "PATH_TRAVERSAL_OUT")
        if not path_context:
            require(CONTEXTUAL_REVIEW_SELECTORS.get(finding.get("review_kind")) == contextual_selector(finding),
                    "Unapproved reviewed contextual boundary")
        require(finding["source"] in manifest["production_java_inventory"], "Unbound finding source")
        require(finding["rationale"] and finding["caller_bindings"], "Missing contextual review evidence")
        require(all(p in manifest["production_java_inventory"] for p in finding["caller_bindings"]),
                "Unbound relevant finding caller")
        require(all(isinstance(finding.get(field), str) and finding[field]
                    for field in ("class", "method", "method_signature", "instance_hash")),
                "Missing exact contextual finding location/raw provenance")
        require(isinstance(finding["canonical_hash"], str) and len(finding["canonical_hash"]) == 64
                and all(character in "0123456789abcdef" for character in finding["canonical_hash"]),
                "Invalid canonical finding fingerprint")
        fingerprint = reviewed_key(finding)
        require(fingerprint not in reviewed, "Duplicate reviewed fingerprint")
        reviewed[fingerprint] = 1
        contextual_locations[fingerprint] = contextual_selector(finding)
        raw_hashes[fingerprint] = finding["instance_hash"]
    observed = Counter()
    low = 0
    raw_hash_variants = 0
    for bug in root.findall("BugInstance"):
        if bug.attrib["category"] != "SECURITY":
            continue
        priority = int(bug.attrib["priority"])
        require(priority in (1, 2, 3), "Unexpected security finding priority")
        if priority == 3:
            low += 1
            continue
        fingerprint = reviewed_key({"type": bug.attrib["type"], "priority": priority,
                                    "canonical_hash": canonical_finding_hash(bug),
                                    "occurrence": bug.attrib["instanceOccurrenceNum"],
                                    "occurrence_max": bug.attrib["instanceOccurrenceMax"]})
        if fingerprint in contextual_locations:
            require(observed_selector(bug) == contextual_locations[fingerprint],
                    "Observed contextual finding location differs from reviewed boundary")
            if bug.attrib["instanceHash"] != raw_hashes[fingerprint]:
                raw_hash_variants += 1
        observed[fingerprint] += 1
    require(observed == reviewed,
            f"Security gate: {sum((observed-reviewed).values())} unreviewed Medium/High findings; "
            f"{sum((reviewed-observed).values())} stale/unused reviewed contexts")
    return dict(completeness, raw_medium_high=sum(observed.values()),
                reviewed_contextual_findings=sum(reviewed.values()), unreviewed_medium_high=0,
                raw_low=low, raw_instance_hash_variants=raw_hash_variants,
                production_java_files=len(manifest["production_java_inventory"]))


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
