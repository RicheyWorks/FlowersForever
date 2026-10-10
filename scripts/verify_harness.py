"""Run clean Maven verification and bind its evidence to the tested source files.

Inspired by CSRBT's per-class evidence floors. Uses only Python's standard library.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
FLOORS = Path("config/harness-test-floors.json")
EVIDENCE = Path("dist/harness-evidence/latest.json")
INPUT_ROOTS = ("src", "config", "scripts", ".github/workflows", ".mvn")
QUALITY_REPORTS = ("target/checkstyle-result.xml", "target/spotbugsXml.xml",
                   "target/site/jacoco/jacoco.xml")


def source_digest(root):
    """Hash actual build inputs, including new and ignored sources Maven may compile."""
    paths = {root / name for name in ("pom.xml", ".gitignore", "mvnw", "mvnw.cmd")
             if (root / name).is_file()}
    for name in INPUT_ROOTS:
        directory = root / name
        if directory.is_dir():
            for path in directory.rglob("*"):
                if "__pycache__" in path.parts or path.suffix == ".pyc":
                    continue
                if path.is_symlink() and path.is_dir():
                    raise ValueError("Directory symlinks in build inputs are not supported")
                if path.is_file():
                    paths.add(path)
    digest = hashlib.sha256()
    for path in sorted(paths):
        name = path.relative_to(root).as_posix()
        digest.update(name.encode("utf-8") + b"\0")
        digest.update(hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def collect_reports(root, started, build_directory="target"):
    suites = {}
    for phase, directory in (("unit", "surefire-reports"), ("packaged", "failsafe-reports")):
        reports = sorted((root / build_directory / directory).glob("TEST-*.xml"))
        if not reports:
            raise ValueError("No " + phase + " test reports")
        for path in reports:
            if path.stat().st_mtime < started - 2:
                raise ValueError("Stale test report: " + path.name)
            suite = ET.parse(path).getroot()
            if suite.tag != "testsuite" or not suite.get("name"):
                raise ValueError("Invalid test report: " + path.name)
            key = phase + ":" + suite.attrib["name"]
            if key in suites:
                raise ValueError("Duplicate test suite: " + key)
            counts = {field: int(suite.attrib[field]) for field in
                      ("tests", "failures", "errors", "skipped")}
            cases = suite.findall("testcase")
            identities = {(case.get("classname"), case.get("name")) for case in cases}
            if len(identities) != len(cases) or any(not owner or not name for owner, name in identities):
                raise ValueError("Missing or duplicate test identity: " + key)
            # Surefire 3.5.6 can collapse the header count for identically named
            # methods in different @Nested classes; the testcase records remain complete.
            nested_count = len({name for _, name in identities})
            valid_counts = {len(cases)}
            if len({owner for owner, _ in identities}) > 1:
                valid_counts.add(nested_count)
            if counts["tests"] < 1 or counts["tests"] not in valid_counts:
                raise ValueError("Inconsistent test count: " + key)
            if any(counts[field] != 0 for field in ("failures", "errors", "skipped")):
                raise ValueError("Failed or skipped tests: " + key)
            if any(list(case) and any(child.tag in
                   ("failure", "error", "skipped", "flakyFailure", "flakyError", "rerunFailure", "rerunError")
                   for child in case) for case in cases):
                raise ValueError("Failed, retried, or skipped test case: " + key)
            if int(suite.get("flakes", "0")) != 0:
                raise ValueError("Flaky test suite: " + key)
            suites[key] = len(cases)
    return suites


def validate_floors(suites, floors):
    if not floors or any(not isinstance(value, int) or isinstance(value, bool) or value < 1
                         for value in floors.values()):
        raise ValueError("Test floors must contain positive per-class counts")
    missing = {name: {"minimum": minimum, "actual": suites.get(name, 0)}
               for name, minimum in floors.items() if suites.get(name, 0) < minimum}
    if missing:
        raise ValueError("Test coverage floor dropped: " + json.dumps(missing, sort_keys=True))


def report_digest(root, build_directory="target"):
    digest = hashlib.sha256()
    for directory in ("surefire-reports", "failsafe-reports"):
        for path in sorted((root / build_directory / directory).glob("TEST-*.xml")):
            digest.update((directory + "/" + path.name).encode("utf-8") + b"\0")
            digest.update(hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def quality_evidence(root, started, build_directory="target"):
    reports = {}
    trees = []
    for name in QUALITY_REPORTS:
        path = root / build_directory / Path(name).relative_to("target")
        if not path.is_file() or path.stat().st_mtime < started - 2:
            raise ValueError("Missing or stale quality report: " + name)
        reports[name] = hashlib.sha256(path.read_bytes()).hexdigest()
        trees.append(ET.parse(path).getroot())
    checkstyle, spotbugs, coverage = trees
    if checkstyle.tag != "checkstyle" or checkstyle.findall(".//error"):
        raise ValueError("Checkstyle report is invalid or contains violations")
    errors = spotbugs.find("Errors")
    if spotbugs.tag != "BugCollection" or errors is None or \
            int(errors.get("errors", "-1")) != 0 or int(errors.get("missingClasses", "-1")) != 0 or \
            spotbugs.findall(".//Error") or any(int(bug.get("priority", "1")) <= 2
                                              for bug in spotbugs.findall("BugInstance")):
        raise ValueError("SpotBugs report is invalid or contains findings/errors")
    counters = {counter.get("type"): counter.attrib for counter in coverage.findall("counter")}
    ratios = {}
    for kind in ("LINE", "BRANCH"):
        counter = counters.get(kind, {})
        covered = int(counter.get("covered", "0"))
        missed = int(counter.get("missed", "0"))
        if coverage.tag != "report" or covered < 1 or missed < 0:
            raise ValueError("Missing or invalid " + kind + " coverage evidence")
        ratios[kind] = covered / (covered + missed)
    # Apply the same bundle limits configured for Maven's JaCoCo gate.
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ET.parse(root / "pom.xml").getroot()
    limits = pom.findall(".//m:plugin[m:artifactId='jacoco-maven-plugin']//m:limit", namespace)
    checked = set()
    for limit in limits:
        kind = limit.findtext("m:counter", namespaces=namespace)
        if kind in ratios and limit.findtext("m:value", namespaces=namespace) == "COVEREDRATIO":
            if ratios[kind] < float(limit.findtext("m:minimum", namespaces=namespace)):
                raise ValueError(kind + " coverage fell below the configured gate")
            checked.add(kind)
    if checked != {"LINE", "BRANCH"}:
        raise ValueError("Expected LINE and BRANCH coverage gates in pom.xml")
    return {"reports": reports, "coverage": ratios}


def write_evidence(root, evidence):
    path = root / EVIDENCE
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def check_evidence(root):
    evidence = json.loads((root / EVIDENCE).read_text(encoding="utf-8"))
    if evidence.get("status") != "passed":
        raise ValueError("Latest harness verification did not pass")
    if evidence.get("sourceSha256") != source_digest(root):
        raise ValueError("Harness evidence does not match current source")
    build_directory = evidence.get("buildDirectory", "target")
    suites = collect_reports(root, evidence["startedAt"], build_directory)
    if suites != evidence["suites"]:
        raise ValueError("Test reports changed since verification")
    if report_digest(root, build_directory) != evidence["reportsSha256"]:
        raise ValueError("Test report contents changed since verification")
    if quality_evidence(root, evidence["startedAt"], build_directory) != evidence["quality"]:
        raise ValueError("Quality reports changed since verification")
    validate_floors(suites, json.loads((root / FLOORS).read_text(encoding="utf-8")))
    return evidence


def verify(root, offline=False, isolated=False):
    build_directory = "target/harness-verification" if isolated else "target"
    evidence = {"status": "running", "startedAt": time.time(), "buildDirectory": build_directory}
    # Invalidate any previous success even when setup, Maven, or report parsing fails.
    write_evidence(root, evidence)
    try:
        evidence["sourceSha256"] = source_digest(root)
        evidence["gitHead"] = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
        executable = shutil.which("mvn.cmd" if os.name == "nt" else "mvn")
        if not executable:
            raise ValueError("Maven executable was not found")
        command = [executable, "-B", "clean", "verify", "-Pquality"]
        if isolated:
            command.append("-Dflowerfarm.build.directory=" + build_directory)
        if offline:
            command.append("-o")
        evidence["command"] = ["mvn", *command[1:]]
        evidence["offline"] = offline
        result = subprocess.run(command, cwd=root, check=False)
        evidence["mavenExitCode"] = result.returncode
        if result.returncode:
            raise ValueError("Maven verification failed: exit " + str(result.returncode))
        if evidence["sourceSha256"] != source_digest(root):
            raise ValueError("Source changed during verification")
        suites = collect_reports(root, evidence["startedAt"], build_directory)
        validate_floors(suites, json.loads((root / FLOORS).read_text(encoding="utf-8")))
        evidence.update(status="passed", suites=suites, reportsSha256=report_digest(root, build_directory),
                        quality=quality_evidence(root, evidence["startedAt"], build_directory),
                        tests=sum(suites.values()),
                        unitTests=sum(n for key, n in suites.items() if key.startswith("unit:")),
                        packagedTests=sum(n for key, n in suites.items() if key.startswith("packaged:")))
    except (OSError, ValueError, KeyError, ET.ParseError, subprocess.SubprocessError) as error:
        evidence.update(status="failed", error=str(error))
    finally:
        evidence["finishedAt"] = time.time()
        write_evidence(root, evidence)
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="Use cached Maven dependencies; no new SBOM")
    parser.add_argument("--isolated", action="store_true", help="Build in target/harness-verification, preserving native outputs")
    parser.add_argument("--check", action="store_true", help="Check existing evidence against current source")
    args = parser.parse_args()
    try:
        evidence = check_evidence(ROOT) if args.check else verify(ROOT, args.offline, args.isolated)
        if evidence["status"] != "passed":
            raise ValueError(evidence.get("error", "Harness verification failed"))
        print("Harness verified: {unitTests} tests + {packagedTests} packaged checks; source {sourceSha256}".format(**evidence))
        return 0
    except (OSError, ValueError, KeyError, ET.ParseError, subprocess.SubprocessError) as error:
        print("Harness verification refused: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
