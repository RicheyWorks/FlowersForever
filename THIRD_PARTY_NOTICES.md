# FlowersForever third-party software notices

FlowersForever is MIT-licensed software owned by RicheyWorks. It includes
third-party libraries that remain subject to their own licenses; the
FlowersForever MIT License does not replace or restrict those rights.

The bundled CSRBT field-science kit is separate MIT-licensed work by the same
owner, RicheyWorks. Its 41 offline pages, source provenance, checksum manifest,
and MIT license are in `src/main/resources/csrbt-field-kit/`. The bundle is
extracted with its license into the farm data directory when a page is opened.

The release build generates the complete, version-specific inventory at:

`target/generated-resources/licenses/THIRD-PARTY.txt`

That file is bundled inside the executable JAR as
`META-INF/FlowersForever-THIRD-PARTY.txt` and is attached to each release. The
CycloneDX SBOM (`bom.json` and `bom.xml`) provides machine-readable dependency
and license metadata.

Principal runtime components include:

- Spring Boot and Spring Framework — Apache License 2.0
- Apache POI — Apache License 2.0
- SQLite JDBC — Apache License 2.0
- FlatLaf — Apache License 2.0
- H2 Database Engine — MPL 2.0 / EPL 1.0
- OpenPDF — LGPL 2.1 / MPL 2.0
- Hibernate ORM — LGPL 2.1
- JFreeChart — LGPL
- Logback — EPL 2.0 / LGPL 2.1

This summary is informational. The generated release notice is authoritative
for the exact versions and transitive components shipped in a particular build.
Do not remove third-party notices from customer distributions.
