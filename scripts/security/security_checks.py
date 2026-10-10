"""Fail closed on incomplete SAST, advisory data, or offline dependency inventory."""
import argparse
import base64
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import re
import sys
from urllib.parse import unquote
from urllib.request import urlopen
import xml.etree.ElementTree as ET
import zipfile


METADATA_URL = "https://storage.googleapis.com/storage/v1/b/osv-vulnerabilities/o/Maven%2Fall.zip"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def strict_json(payload):
    def object_pairs(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "Duplicate advisory JSON field")
            result[key] = value
        return result

    def invalid_constant(value):
        raise ValueError("Non-JSON advisory numeric constant: " + value)

    if isinstance(payload, bytes):
        payload = payload.decode("utf-8")  # No BOM/UTF-16 auto-detection unlike json.loads(bytes).
    result = json.loads(payload, object_pairs_hook=object_pairs, parse_constant=invalid_constant)

    def decoded_value_valid(value):
        if isinstance(value, str):
            value.encode("utf-8")  # Protobuf rejects unpaired surrogate code points.
        elif isinstance(value, (float, int)) and not isinstance(value, bool):
            try:
                require(math.isfinite(float(value)), "Advisory JSON number overflows finite range")
            except OverflowError as error:
                raise ValueError("Advisory JSON integer overflows finite range") from error
        elif isinstance(value, list):
            for item in value:
                decoded_value_valid(item)
        elif isinstance(value, dict):
            for key, item in value.items():
                decoded_value_valid(key)
                decoded_value_valid(item)

    decoded_value_valid(result)
    return result


def severity_valid(value):
    if value is None:
        return
    require(isinstance(value, list), "Invalid advisory severity list")
    for item in value:
        known_fields(item, {"type", "score", "source"})
        require(isinstance(item, dict) and item.get("type") in ("CVSS_V2", "CVSS_V3", "CVSS_V4")
                and isinstance(item.get("score"), str), "Invalid advisory severity")
        if "source" in item:
            require(item["source"] in ("NVD", "CNA", "SELF", "SOURCE_UNSPECIFIED"),
                    "Unknown advisory severity source")


def optional_objects_valid(value, keys):
    for key in keys:
        if key in value:
            require(isinstance(value[key], dict), "Invalid advisory " + key + " object")


def known_fields(value, allowed):
    require(isinstance(value, dict) and set(value).issubset(allowed),
            "Unknown field in typed advisory message")


def spotbugs_complete(path):
    root = ET.parse(path).getroot()
    require(root.tag == "BugCollection", "Not a SpotBugs BugCollection")
    summary, errors = root.find("FindBugsSummary"), root.find("Errors")
    require(summary is not None and errors is not None, "Missing analysis summary/errors")
    classes = int(summary.attrib["total_classes"])
    require(classes > 0, "No application classes analyzed")
    require(int(errors.attrib["errors"]) == 0, "SpotBugs analysis errors")
    require(int(errors.attrib["missingClasses"]) == 0, "SpotBugs missing classes")
    require(not errors.findall("Error") and not errors.findall("MissingClass"),
            "SpotBugs contains incomplete-analysis details")
    return {"analyzed_classes": classes, "errors": 0, "missing_classes": 0}


def metadata_fresh(metadata, now=None):
    require(metadata.get("bucket") == "osv-vulnerabilities"
            and metadata.get("name") == "Maven/all.zip", "Unexpected public database object")
    require(re.fullmatch(r"[0-9]+", metadata.get("generation", "")), "Invalid generation")
    published = datetime.fromisoformat(metadata["timeFinalized"].replace("Z", "+00:00"))
    require(published.tzinfo is not None, "Missing database timestamp timezone")
    now = now or datetime.now(timezone.utc)
    age = (now - published).total_seconds()
    require(-300 <= age <= 24 * 3600, "Public Maven database is stale or future-dated")
    require(int(metadata["size"]) > 0, "Empty database object")
    require(len(base64.b64decode(metadata["md5Hash"], validate=True)) == 16,
            "Invalid public object checksum")
    return age


def advisory_valid(record):
    """Validate fields the pinned matcher must decode; malformed JSON can be skipped by it."""
    require(isinstance(record, dict), "Advisory must be an object")
    known_fields(record, {"schema_version", "id", "modified", "published", "withdrawn",
                         "aliases", "related", "upstream", "summary", "details", "severity",
                         "affected", "references", "credits", "database_specific"})
    require(isinstance(record.get("id"), str) and record["id"], "Missing advisory ID")
    for key in ("schema_version", "summary", "details"):
        if key in record:
            require(isinstance(record[key], str), "Invalid advisory " + key)
    optional_objects_valid(record, ("database_specific",))
    severity_valid(record.get("severity"))
    references = record.get("references", [])
    require(isinstance(references, list), "Invalid advisory references")
    for reference in references:
        known_fields(reference, {"type", "url"})
        require(isinstance(reference, dict) and reference.get("type") in (
            "ADVISORY", "ARTICLE", "DETECTION", "DISCUSSION", "REPORT", "FIX", "INTRODUCED",
            "GIT", "PACKAGE", "EVIDENCE", "WEB") and isinstance(reference.get("url"), str),
            "Invalid advisory reference")
    credits = record.get("credits", [])
    require(isinstance(credits, list), "Invalid advisory credits")
    for credit in credits:
        known_fields(credit, {"name", "contact", "type"})
        require(isinstance(credit, dict) and isinstance(credit.get("name"), str), "Invalid advisory credit")
        contact = credit.get("contact", [])
        require(isinstance(contact, list) and all(isinstance(v, str) for v in contact),
                "Invalid advisory credit contact")
        if "type" in credit:
            require(credit["type"] in ("FINDER", "REPORTER", "ANALYST", "COORDINATOR",
                    "REMEDIATION_DEVELOPER", "REMEDIATION_REVIEWER", "REMEDIATION_VERIFIER",
                    "TOOL", "SPONSOR", "OTHER"), "Unknown advisory credit type")
    for key in ("modified", "published", "withdrawn"):
        value = record.get(key)
        if value is None and key != "modified":
            continue
        require(isinstance(value, str) and re.fullmatch(
            r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})", value),
            "Invalid advisory " + key + " timestamp")
        try:
            datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)
        except OverflowError as error:
            raise ValueError("Advisory timestamp outside protobuf UTC range") from error
    for key in ("aliases", "related", "upstream"):
        if key in record:
            require(isinstance(record[key], list) and all(isinstance(v, str) for v in record[key]),
                    "Invalid advisory " + key)
    require(isinstance(record.get("affected"), list), "Invalid advisory affected data")
    maven_entries = 0
    for affected in record["affected"]:
        require(isinstance(affected, dict), "Invalid affected entry")
        known_fields(affected, {"package", "ranges", "versions", "severity",
                               "database_specific", "ecosystem_specific"})
        optional_objects_valid(affected, ("database_specific", "ecosystem_specific"))
        severity_valid(affected.get("severity"))
        package = affected.get("package")
        require(isinstance(package, dict), "Invalid advisory package")
        known_fields(package, {"name", "ecosystem", "purl"})
        require(all(isinstance(package.get(k), str) and package[k] for k in ("name", "ecosystem")),
                "Missing advisory package identity")
        if "purl" in package:
            require(isinstance(package["purl"], str), "Invalid advisory package PURL")
        ranges, versions = affected.get("ranges", []), affected.get("versions", [])
        require(isinstance(ranges, list) and isinstance(versions, list), "Invalid advisory ranges/versions")
        require(all(isinstance(v, str) and v for v in versions), "Invalid advisory version")
        for version_range in ranges:
            require(isinstance(version_range, dict), "Invalid advisory version range")
            known_fields(version_range, {"type", "repo", "events", "database_specific"})
            optional_objects_valid(version_range, ("database_specific",))
            if "repo" in version_range:
                require(isinstance(version_range["repo"], str), "Invalid advisory range repository")
            require(version_range.get("type") in ("ECOSYSTEM", "SEMVER", "GIT"), "Unknown range type")
            events = version_range.get("events")
            require(isinstance(events, list) and events, "Missing range events")
            for event in events:
                require(isinstance(event, dict) and len(event) == 1, "Invalid range event")
                key, value = next(iter(event.items()))
                require(key in ("introduced", "fixed", "last_affected", "limit")
                        and isinstance(value, str) and value, "Invalid range event value")
            if version_range["type"] == "GIT":
                require(isinstance(version_range.get("repo"), str) and version_range["repo"],
                        "Missing Git range repository")
        if package["ecosystem"] == "Maven":
            require(len(package["name"].split(":")) == 2
                    and all(package["name"].split(":")), "Invalid Maven advisory identity")
            require(ranges or versions, "Missing Maven advisory version data")
            maven_entries += 1
    return maven_entries


def database_valid(path, metadata, now=None):
    age = metadata_fresh(metadata, now)
    payload = path.read_bytes()
    require(len(payload) == int(metadata["size"]), "Database size mismatch")
    # MD5 is the public object's transport checksum, not a security primitive.
    checksum = base64.b64encode(hashlib.md5(payload, usedforsecurity=False).digest()).decode()
    require(checksum == metadata["md5Hash"], "Database checksum mismatch")
    records, maven_entries = 0, 0
    identifiers = set()
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, "Database ZIP CRC failure")
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            require(entry.filename.endswith(".json"), "Unexpected database entry")
            record = strict_json(archive.read(entry))
            maven_entries += advisory_valid(record)
            identifier = record["id"]
            require(identifier not in identifiers, "Duplicate advisory ID")
            identifiers.add(identifier)
            records += 1
    require(records > 0 and maven_entries > 0, "No decoded Maven advisory records")
    return {"generation": metadata["generation"], "published_utc": metadata["timeFinalized"],
            "age_seconds": age, "bytes": len(payload),
            "sha256": hashlib.sha256(payload).hexdigest(),
            "json_records": records, "maven_affected_entries": maven_entries,
            "zip_crc": "passed", "all_json_decoded": True}


def prepare_database(cache, receipt):
    # These fixed public requests receive no source, SBOM, or package inventory.
    with urlopen(METADATA_URL, timeout=60) as response:
        metadata = json.load(response)
    metadata_fresh(metadata)
    path = cache / "osv-scalibr" / "Maven" / "all.zip"
    path.parent.mkdir(parents=True, exist_ok=True)
    url = ("https://storage.googleapis.com/osv-vulnerabilities/Maven/all.zip?generation="
           + metadata["generation"])
    with urlopen(url, timeout=120) as response:
        path.write_bytes(response.read())
    result = database_valid(path, metadata)
    receipt.parent.mkdir(parents=True, exist_ok=True)
    receipt.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result


def inventory_complete(sbom_path, report_path, fail_on_findings=False):
    bom = json.loads(sbom_path.read_text(encoding="utf-8"))
    require(bom.get("bomFormat") == "CycloneDX", "Expected CycloneDX SBOM")
    components = bom.get("components")
    require(isinstance(components, list) and components, "No resolved SBOM components")
    expected, full_ids, projected_groups = Counter(), set(), {}
    for component in components:
        require(not component.get("components"), "Nested SBOM components are unsupported")
        purl = component.get("purl", "")
        match = re.fullmatch(r"pkg:maven/([^/?#]+)/([^/?#@]+)@([^?#]+)(?:\?[^#]+)?", purl)
        require(match is not None, "Unmapped/non-Maven component PURL")
        group, artifact, version = map(unquote, match.groups())
        require(group == component.get("group") and artifact == component.get("name")
                and version == component.get("version"), "SBOM identity/PURL mismatch")
        full_id = (group, artifact, version)
        require(full_id not in full_ids, "Duplicate Maven SBOM identity")
        full_ids.add(full_id)
        projected = ("Maven", artifact, version)
        # OSV 2.6 JSON omits namespace. Reject ambiguity instead of claiming full parity.
        require(projected not in projected_groups or projected_groups[projected] == group,
                "OSV JSON cannot distinguish same artifact/version in multiple Maven groups")
        projected_groups[projected] = group
        expected[projected] += 1
    report = json.loads(report_path.read_text(encoding="utf-8"))
    require(isinstance(report.get("results"), list), "Missing OSV results")
    observed, vulnerabilities = Counter(), set()
    for result in report["results"]:
        source = result.get("source", {})
        require(source.get("type") == "sbom", "Unexpected OSV source type")
        require(Path(source["path"]).resolve() == sbom_path.resolve(), "Unexpected OSV source path")
        for entry in result.get("packages", []):
            package = entry["package"]
            observed[(package.get("ecosystem"), package.get("name"), package.get("version"))] += 1
            require(not entry.get("suppressed_groups"), "OSV contains suppressed vulnerability groups")
            vulnerabilities.update(v["id"] for v in entry.get("vulnerabilities", []))
    require(observed == expected, "OSV result inventory differs from resolved SBOM identities")
    require(not fail_on_findings or not vulnerabilities,
            "Known vulnerabilities in resolved SBOM: " + ", ".join(sorted(vulnerabilities)))
    return {"sbom_components": len(components), "scanned_packages": sum(observed.values()),
            "namespace_projection_unambiguous": True,
            "vulnerability_ids": sorted(vulnerabilities)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sast = sub.add_parser("spotbugs")
    sast.add_argument("report", type=Path)
    database = sub.add_parser("prepare-db")
    database.add_argument("cache", type=Path)
    database.add_argument("receipt", type=Path)
    inventory = sub.add_parser("inventory")
    inventory.add_argument("sbom", type=Path)
    inventory.add_argument("report", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "spotbugs":
            result = spotbugs_complete(args.report)
        elif args.command == "prepare-db":
            result = prepare_database(args.cache, args.receipt)
        else:
            result = inventory_complete(args.sbom, args.report, fail_on_findings=True)
        print(json.dumps(result, indent=2))
    except (ValueError, KeyError, TypeError, OSError, ET.ParseError, zipfile.BadZipFile) as error:
        print(f"Security evidence invalid: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
