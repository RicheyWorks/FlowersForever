package com.flowerfarm.reference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldScienceBundleTest {

    private static final Pattern LOCAL_DOCUMENT_LINK = Pattern.compile(
            "(?:href|src)=[\\\"']([^\\\"']+\\.(?:html|md))(?:#[^\\\"']*)?[\\\"']",
            Pattern.CASE_INSENSITIVE);

    @TempDir
    Path temporaryDirectory;

    private String priorDataDirectory;

    @BeforeEach
    void useIsolatedDataDirectory() {
        priorDataDirectory = System.getProperty("flowerfarm.data-dir");
        System.setProperty("flowerfarm.data-dir", temporaryDirectory.toString());
    }

    @AfterEach
    void restoreDataDirectory() {
        if (priorDataDirectory == null) {
            System.clearProperty("flowerfarm.data-dir");
        } else {
            System.setProperty("flowerfarm.data-dir", priorDataDirectory);
        }
    }

    @Test
    void extractsEveryCommittedPageWithLicenseAndKeepsRelativePageLinks() throws Exception {
        Path requested = FieldScienceBundle.materialize("soil-recipes.html");
        Path bundle = requested.getParent();
        assertThat(requested).isRegularFile();
        assertThat(requested.toString()).contains(Path.of("field-science",
                FieldScienceBundle.SOURCE_COMMIT.substring(0, 12)).toString());
        assertThat(Files.readString(requested)).contains("Subcool's Super Soil");
        assertThat(Files.readString(bundle.resolve("LICENSE"))).startsWith("MIT License");
        assertThat(Files.readString(bundle.resolve("PROVENANCE.txt"))).contains(
                FieldScienceBundle.SOURCE_COMMIT);
        assertThat(Files.readString(bundle.resolve("tree-proofs.html"))).contains(
                "href=\"ADR-011-evolution-machine-2026-06-10.md\">ADR-011</a>");
        assertThat(bundle.resolve("ADR-011-evolution-machine-2026-06-10.md"))
                .isRegularFile();

        Set<String> pages = new HashSet<>();
        try (var files = Files.list(bundle)) {
            files.filter(file -> file.getFileName().toString().endsWith(".html"))
                    .forEach(file -> pages.add(file.getFileName().toString()));
        }
        assertThat(pages).hasSize(41);
        for (String page : pages) {
            Matcher links = LOCAL_DOCUMENT_LINK.matcher(Files.readString(bundle.resolve(page)));
            while (links.find()) {
                String link = links.group(1);
                if (!link.startsWith("https:") && !link.startsWith("http:")) {
                    assertThat(bundle.resolve(link)).as(page + " -> " + link).isRegularFile();
                }
            }
        }
    }

    @Test
    void preservesMatchingFilesAndRepairsChangedBundleContent() throws Exception {
        Path page = FieldScienceBundle.materialize("soil-recipes.html");
        String source = Files.readString(page);
        FileTime oldTime = FileTime.fromMillis(1_600_000_000_000L);
        Files.setLastModifiedTime(page, oldTime);

        assertThat(FieldScienceBundle.materialize("soil-recipes.html")).isEqualTo(page);
        assertThat(Files.getLastModifiedTime(page)).isEqualTo(oldTime);

        Files.writeString(page, "changed after extraction");
        FieldScienceBundle.materialize("soil-recipes.html");
        assertThat(page).hasContent(source);
    }

    @Test
    void refusesPathsOutsideThePinnedBundleAndUnsafeDestinations() throws Exception {
        for (String name : new String[] {"../soil-recipes.html", "soil-recipes.html/../LICENSE",
                "not-a-bundled-page.html", ""}) {
            assertThatThrownBy(() -> FieldScienceBundle.materialize(name))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        Path obstruction = temporaryDirectory.resolve("field-science");
        Files.writeString(obstruction, "not a directory");
        assertThatThrownBy(() -> FieldScienceBundle.materialize("soil-recipes.html"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void extractionDoesNotWaitForCodeThatLocksTheClass() throws Exception {
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<Path> extracted;
            synchronized (FieldScienceBundle.class) {
                extracted = worker.submit(() -> FieldScienceBundle.materialize("soil-recipes.html"));
                // With a static synchronized method this would time out while the class monitor is held.
                assertThat(extracted.get(30, java.util.concurrent.TimeUnit.SECONDS)).isRegularFile();
            }
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void concurrentExtractionsProduceOneCompleteBundle() throws Exception {
        int threads = 4;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.List<java.util.concurrent.Future<Path>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return FieldScienceBundle.materialize("soil-recipes.html");
                }));
            }
            start.countDown();
            Set<Path> paths = new HashSet<>();
            for (java.util.concurrent.Future<Path> result : results) {
                paths.add(result.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(paths).hasSize(1);
            Path page = paths.iterator().next();
            assertThat(Files.readString(page)).contains("Subcool's Super Soil");
            try (var leftovers = Files.list(page.getParent())) {
                assertThat(leftovers.map(path -> path.getFileName().toString()))
                        .noneMatch(name -> name.endsWith(".tmp"));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
