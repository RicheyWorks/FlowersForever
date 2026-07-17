# ADR-001: Infrastructure and build expansion

**Status:** Accepted
**Date:** 2026-07-16
**Deciders:** Richmond (RicheyWorks)

## Context

FlowersForever v1.0.18 ships with minimal but working automation: CI runs `mvn clean verify` on Ubuntu + Temurin 17, Dependabot files weekly PRs, and a tag push publishes the fat JAR to GitHub Releases. There is no coverage measurement, no static analysis, no container image, no vulnerability scanning beyond Dependabot version bumps, and the only install artifact is a bare JAR that requires a local JDK 17.

Forces at play:

- The project doubles as a portfolio piece — visible quality signals (coverage, scanning badges, polished release assets) matter.
- Real users are non-technical farm staff on shared barn PCs — "install a JDK and run `java -jar`" is a real adoption barrier; a native installer is not.
- The app is a hybrid: Swing GUI (needs a display) + REST API (does not). Containerization only makes sense for the headless REST side.
- Solo maintainer, free GitHub Actions tier — everything must be zero-cost, low-maintenance, and must not turn CI red on day one from a backlog of pre-existing static-analysis findings.

## Decision

Expand build and infrastructure in five areas, all on GitHub-native, zero-cost tooling, with quality gates introduced **report-first** (visible but non-blocking) so they can be ratcheted later without a big-bang cleanup:

1. **Code quality gates** — JaCoCo coverage reports on every build; SpotBugs + Checkstyle as non-failing report profiles wired into a dedicated CI job.
2. **Docker** — multi-stage Dockerfile producing a headless REST-only image, published to GHCR on release. A headless guard added to `FlowerFarmGUI.run()` so the default profile boots cleanly without a display.
3. **CI matrix + caching** — JDK 17 (baseline) + JDK 21 (forward-compat) matrix; Maven cache already present via `setup-java`.
4. **Supply-chain security** — CodeQL scanning, dependency-review on PRs, CycloneDX SBOM generated every build and attached to releases.
5. **Native installers** — `jpackage` MSI (Windows) and DMG (macOS) built on tag push and attached to the GitHub Release alongside the fat JAR.

## Options Considered

### Option A: GitHub-native + Maven plugins (chosen)

| Dimension | Assessment |
|-----------|------------|
| Complexity | Low–Med — all config lives in `pom.xml` + `.github/workflows` |
| Cost | Free (public repo: Actions, CodeQL, GHCR all free) |
| Scalability | Fine for a solo project; matrix trivially extendable |
| Team familiarity | High — same stack already in use |

**Pros:** no new accounts or secrets; SBOM/scanning/coverage all standard plugins; jpackage ships with the JDK.
**Cons:** unsigned installers (SmartScreen/Gatekeeper warnings); GHCR image is REST-only, not the GUI.

### Option B: Third-party quality platform (SonarCloud, Snyk, Codecov)

| Dimension | Assessment |
|-----------|------------|
| Complexity | Med — external accounts, tokens as repo secrets |
| Cost | Free tiers exist but rate-limited; churn risk |
| Scalability | Better dashboards and history than raw reports |
| Team familiarity | Low — new services to learn and maintain |

**Pros:** richer UI, PR decoration, trend graphs.
**Cons:** token management for a solo repo; free-tier terms change; overkill for current scale. Can be layered on later without undoing Option A.

### Option C: Full re-platform (Gradle, Kubernetes manifests, Terraform)

Rejected outright: no deployment target exists that would justify k8s/IaC, and a Maven→Gradle migration is churn with no user-visible payoff.

## Trade-off Analysis

- **Report-first vs. fail-fast quality gates:** failing the build on SpotBugs/Checkstyle from day one would block all work behind a cleanup of years of accumulated findings. Reports + a separate non-blocking CI job make findings visible immediately; thresholds can be ratcheted (e.g. JaCoCo `check` with a minimum) once the baseline is known.
- **Headless-guard vs. separate server profile for Docker:** a `server` Spring profile excluding the GUI bean was considered, but a `GraphicsEnvironment.isHeadless()` guard in `run()` is smaller, keeps the profile matrix flat, and makes *any* headless environment (CI, container, ssh) behave sensibly by default.
- **jpackage vs. jlink vs. installer frameworks (Install4j, Conveyor):** jpackage is in the JDK, needs no license, and produces MSI/DMG directly on hosted runners (WiX preinstalled on `windows-latest`). Code signing is deferred — certificates cost money; acceptable for a portfolio/farm-use project.
- **JDK 21 in matrix as advisory:** 21 failures shouldn't block merges while the baseline is 17, so the 21 leg is `continue-on-error` — early warning for the Boot 4.x migration noted in the README.

## Consequences

- Easier: onboarding farm users (double-click installer), demoing the REST API (one `docker run`), spotting regressions (coverage + static analysis visible per PR), responding to CVEs (SBOM + CodeQL + dependency-review).
- Harder: release pipeline goes from 1 job to 4 (JAR, image, MSI, DMG) — more surface to break on tag day; installer artifacts are unsigned so users see OS warnings.
- Revisit: ratchet JaCoCo/SpotBugs from report to enforce once baseline is clean; code signing if distribution widens; SonarCloud if trend history becomes valuable; GUI-in-container is explicitly out of scope.

## Action Items

1. [x] JaCoCo, SpotBugs, Checkstyle, CycloneDX in `pom.xml` (reports non-blocking)
2. [x] Headless guard in `FlowerFarmGUI.run()`
3. [x] `Dockerfile` + `.dockerignore`; GHCR publish job in release workflow
4. [x] CI: JDK 17/21 matrix + quality-report job + coverage artifact
5. [x] `codeql.yml` + `dependency-review.yml` workflows
6. [x] `installers.yml`: jpackage MSI + DMG attached to releases
7. [x] Baseline recorded and ratchet set (2026-07-16, see below)
8. [ ] Follow-up: evaluate code signing before any public distribution push

## Baseline & ratchet (recorded 2026-07-16)

SpotBugs baseline was 172 findings: 122 were `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` — mutable-reference noise inherent to Spring constructor injection and JPA entities — now excluded via `config/spotbugs-exclude.xml`. The 12 priority-1 findings (10 platform-default-charset I/O calls, 2 silently swallowed exceptions, 1 per-call `Random`) were fixed in code, bringing the High-priority count to 0.

Ratchet as implemented: the `quality` profile reports everything Medium+ (non-blocking) and **fails on any High-priority finding** (`spotbugs:check`, threshold High) — green at adoption, blocks regressions.

Medium burn-down (2026-07-17): all 13 `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` fixed in connector code (captured `response.getBody()` / `path.getParent()` once instead of re-calling across the null check). `CT_CONSTRUCTOR_THROW` (8) and `DM_EXIT` (3) excluded with rationale in the filter file. Remaining backlog ≈14, mostly `REC_CATCH_EXCEPTION` — deliberate defensive catches, burn down opportunistically.

Checkstyle baseline was 14,439 violations, 89% `IndentationCheck` (google_checks wants 2-space, codebase uses 4-space) — structural style mismatch, not code health. Stays report-only; revisit with a project-tailored ruleset only if it ever earns its keep.
