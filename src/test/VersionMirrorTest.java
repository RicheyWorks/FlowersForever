package com.flowerfarm.release;

import com.flowerfarm.automation.mcp.McpProtocolHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the POM version and every hand-written copy of it in step. */
class VersionMirrorTest {

    /** The release line under development. Change this together with pom.xml. */
    private static final String DEVELOPMENT_VERSION = "1.3.0-SNAPSHOT";

    private static final Pattern JAR_REFERENCE =
            Pattern.compile("flowerfarm-manager-(\\d+\\.\\d+\\.\\d+(?:-SNAPSHOT)?)\\.jar");

    @Test
    @DisplayName("the POM is on the 1.3.0 development line")
    void pomIsOnTheDevelopmentLine() throws Exception {
        assertThat(pomVersion()).isEqualTo(DEVELOPMENT_VERSION);
    }

    @Test
    @DisplayName("the MCP server reports the release the POM is building")
    void mcpServerVersionMatchesPom() throws Exception {
        assertThat(McpProtocolHandler.SERVER_VERSION).isEqualTo(pomVersion().replace("-SNAPSHOT", ""));
    }

    @Test
    @DisplayName("README and the demo runbook point at the JAR the build really produces")
    void docsReferenceTheCurrentJar() throws Exception {
        String version = pomVersion();
        for (Path doc : new Path[] {Path.of("README.md"), Path.of("docs", "DEMO.md")}) {
            Matcher matcher = JAR_REFERENCE.matcher(Files.readString(doc, StandardCharsets.UTF_8));
            int found = 0;
            while (matcher.find()) {
                found++;
                assertThat(matcher.group(1)).as("development JAR named in %s", doc).isEqualTo(version);
            }
            assertThat(found).as("JAR references in %s", doc).isPositive();
        }
    }

    private static String pomVersion() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document pom = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        Element project = pom.getDocumentElement();
        for (Node child = project.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && "version".equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        throw new AssertionError("pom.xml has no project version");
    }
}
