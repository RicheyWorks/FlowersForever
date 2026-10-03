# Security policy

## Supported versions

| Component | Version | Notes |
|-----------|---------|--------|
| **Application** | `1.0.x` on `main` | Latest release tag is the supported line |
| **Spring Boot** | **3.5.16** (parent) | Final OSS patch of the 3.5 line; prefer upgrading to supported Boot when moving to production long-term |
| **Java** | 17+ | Temurin 17 in CI |

Spring Boot **3.4** and **3.5** are past open-source end of life. This project stays on **3.5.16** (last 3.5 OSS release) rather than jumping to Boot **4.x** until a deliberate migration is planned. Commercial / extended support options exist from Spring/vendors if you must remain on 3.x in production.

## Reporting a vulnerability

Please open a **private** security advisory on GitHub if available, or contact the repository owner. Do not file a public issue for credential leaks or remote exploit details until a fix is ready.

## Application security model

### Default (open API)

With **no** `auth` profile, the embedded REST API allows all local requests (barn single-user / demo). This is intentional for offline farm PCs. **Do not expose port 8080 to the public internet** in this mode.

### Barn auth (`auth` profile)

Enable with:

```bash
java -jar flowerfarm-manager-*.jar --spring.profiles.active=auth
```

| Role | REST | GUI writes | Clear connector history |
|------|------|------------|-------------------------|
| **OWNER** | full | yes | yes |
| **HAND** | read + write | yes | no |
| **VIEWER** | GET only | blocked | no |

- Transport: **HTTP Basic** (local LAN / shared PC). Use HTTPS reverse-proxy if you ever put this on a wider network.
- CSRF is disabled (stateless API + desktop client). Suitable for local barn use, not multi-tenant SaaS.
- Actuator: `/actuator/health` and `/actuator/info` remain public when auth is on; other actuator endpoints follow Spring Boot defaults for the profile.

### Default demo accounts

Configured in `application-auth.properties` (change before any shared deployment):

| User | Password | Role |
|------|----------|------|
| `farm` | `kitsap` | OWNER |
| `hand` | `harvest` | HAND |
| `viewer` | `view` | VIEWER |

Override with `flowerfarm.auth.users=user:pass:ROLE,...`. Passwords are encoded with **BCrypt** at runtime for Spring Security; the property file still holds the cleartext source of truth for the barn directory — treat that file as sensitive on shared machines.

### Connectors and secrets

- **Offline (default):** dual-mode connectors write JSON mirrors under `data/` — no cloud keys required.
- **Live mode:** put API tokens in `application-local.properties` (gitignored) or environment-specific config. Never commit live keys.
- Webhook HMAC secrets and shop tokens should be rotated if leaked.

### Data at rest

Default inventory DB is local **H2** (`./data/flowerfarm.mv.db`) or optional **SQLite**. Protect the `data/` directory with OS file permissions on multi-user machines.

## Dependency hygiene

- CI runs `mvn clean verify` on every push/PR to `main`.
- Dependabot opens weekly PRs for Maven and GitHub Actions.
- Major framework bumps (e.g. Spring Boot 4, OpenPDF 3) are reviewed deliberately; minor/patch deps are preferred.

## Security scans

[Security scans](.github/workflows/security-oss.yml) runs on pushes and PRs to `main`, plus a weekly sweep. It replaces the unavailable private-repository CodeQL upload and PR dependency-review workflows under the owner's approved standalone scanning policy. It does not enable paid security features or alter repository visibility, access, or application authentication.

| Check | Enforced policy and limits |
| --- | --- |
| FindSecBugs 1.14.0 / SpotBugs 4.8.6 | Analyze compiled application classes; fail on every **unreviewed** Medium/High `SECURITY` finding and on incomplete analysis. Exactly 21 reviewed desktop/operator-path heuristics are retained in raw reports and matched by a source-bound classifier. Low findings remain in XML/SARIF for review. Detector findings are not all confirmed vulnerabilities. |
| OSV Scanner 2.6.0 | Match every resolved Maven component in the generated CycloneDX SBOM against a locally validated public Maven advisory snapshot. Fail on every known match, scanner error, stale/incomplete database, unsupported identity, or incomplete inventory. No severity exemption or ignore policy is configured. |

These checks have different rule sets and analysis models from CodeQL. The OSV gate checks the whole resolved SBOM rather than GitHub's PR dependency delta, and does not provide GitHub's native dependency-review policy or code-scanning alerts. It cannot establish the absence of vulnerabilities. Test-only dependencies, Maven build plugins, GitHub Actions, the JRE and container OS are outside this Maven application SBOM gate; Dependabot and the existing build/test jobs remain separate controls.

The [reviewed contexts](config/security-reviewed-findings.md) preserve desktop chooser destinations and operator-configured file locations. They are exact finding fingerprints/occurrences, not category, class or method exclusions. The manifest binds the analyzer artifact/version, **every production Java source path and content hash under `src/main/java`**, and `pom.xml`. A changed/added/removed production source, Maven configuration change, changed finding, or unused review entry fails and requires contextual re-review. Source symlinks and directory reparse points are rejected. These bindings cover the reviewed source/build inputs, not a complete runtime call graph. Hashes normalize only CRLF to LF for Windows/Linux portability. Raw XML/SARIF retain all reviewed findings; a successful gate means zero unreviewed Medium/High findings, not zero raw findings.

The existing CycloneDX package output is `target/classes/META-INF/sbom/application.cdx.json`. Compile, provided, runtime and system dependencies are included by the plugin defaults; test dependencies are excluded. The guard validates each full Maven PURL against its component identity and checks the scanner's complete output inventory. OSV's JSON report omits Maven namespaces, so ambiguous same-artifact/version identities in different groups fail the guard rather than receiving a partial-coverage pass.

The workflow downloads only the fixed public Maven advisory export and the checksum-pinned scanner binary. It validates the export's generation, publication time (at most 24 hours old, with five minutes of future clock tolerance), size, public-object checksum, ZIP CRC and every advisory JSON record before matching. Matching uses `--offline --no-resolve` and an explicitly empty scanner configuration; source code and dependency inventory are not sent to the OSV API. Maven still downloads build dependencies from the configured Maven repositories.

XML, SARIF, scanner JSON, SBOM and integrity/completeness receipts are uploaded as private repository Actions artifacts, including on scan failure, with seven-day retention. The full public advisory archive is not uploaded. Reports can contain source paths and dependency identities; artifact access follows repository access.

### Run locally

Use JDK 17+, Maven 3.9+, Python 3.9+ and the pinned OSV Scanner 2.6.0 binary for your platform:

```bash
python3 -m unittest discover -s scripts/security -p 'test_*.py' -v
mvn -B -U package -DskipTests -Psecurity-oss
mvn -B -U -Psecurity-oss com.github.spotbugs:spotbugs-maven-plugin:4.8.6.6:spotbugs
python3 scripts/security/classify_sast.py target/spotbugsXml.xml config/security-reviewed-findings.json --plugin "$HOME/.m2/repository/com/h3xstream/findsecbugs/findsecbugs-plugin/1.14.0/findsecbugs-plugin-1.14.0.jar"
export OSV_SCANNER_LOCAL_DB_CACHE_DIRECTORY="$(mktemp -d)"
python3 scripts/security/security_checks.py prepare-db "$OSV_SCANNER_LOCAL_DB_CACHE_DIRECTORY" target/security/database-receipt.json
osv-scanner scan source --offline --no-ignore --no-resolve --config=config/osv-scanner-empty.toml --all-packages --all-vulns --format=json --output-file=target/security/osv.json -L target/classes/META-INF/sbom/application.cdx.json
python3 scripts/security/security_checks.py inventory target/classes/META-INF/sbom/application.cdx.json target/security/osv.json
```

The classifier requires the exact plugin artifact used by Maven. If you set `-Dmaven.repo.local`, pass `--plugin` beneath that repository instead of the default `$HOME/.m2/repository`; a leftover JAR in `target/` is not used by the recipe.

The security compile skips tests; the existing CI JDK 17/21 jobs still run the test suite. The general `quality` profile's report-only policy is unchanged. The CI job has a 15-minute timeout and cancellation of superseded runs; private Actions minutes and artifact storage remain subject to the account's quota and billing.

### Tool and advisory licenses

Standalone private use requires no additional scanner license. [SpotBugs 4.8.6](https://github.com/spotbugs/spotbugs/blob/4.8.6/LICENSE) is LGPL-2.1, [FindSecBugs 1.14.0](https://github.com/find-sec-bugs/find-sec-bugs/blob/version-1.14.0/LICENSE) is LGPL-3.0, and [OSV Scanner 2.6.0](https://github.com/google/osv-scanner/blob/v2.6.0/LICENSE) and the [SpotBugs Maven integration](https://github.com/spotbugs/spotbugs-maven-plugin/blob/spotbugs-maven-plugin-4.8.6.6/pom.xml) are Apache-2.0. Analyzer binaries are CI tools, not packaged application dependencies; any later redistribution must respect their licenses.

Advisory data has source-specific licenses: [OSV's data documentation](https://google.github.io/osv.dev/data/) identifies GitHub Advisory Database data as CC-BY-4.0; [OpenSSF malicious-packages](https://github.com/ossf/malicious-packages/blob/main/LICENSE) is Apache-2.0. Reports retain advisory IDs and source references. The initial validated Maven export contained those two providers; the guard decodes every record without discarding unfamiliar advisory IDs.

### Server CSV exports

The harvest and connector audit HTTP export routes accept a CSV leaf filename beneath `flowerfarm.exports.directory` (default `exports`). HTTP callers cannot choose an arbitrary server path. The directory **and every ancestor** must remain protected from writes or renames by untrusted local users. On filesystems without secure relative directory operations or stable file keys, path/creation metadata checks are best-effort and cannot prove safety against malicious local replacement; filesystem permissions are the trust boundary. Desktop exports require an approved file chooser selection and retain the user's selected destination; configured CSV/Excel connectors and startup seed paths are operator configuration.

## Hardening checklist (shared barn PC)

1. Enable `auth` profile; change all default passwords.
2. Keep `8080` off the public internet (firewall / bind to localhost or LAN only if needed).
3. Restrict filesystem access to `data/` and any `application-local.properties`.
4. Prefer VIEWER accounts for office/guest kiosks.
5. Review connector audit history for unexpected live syncs.
