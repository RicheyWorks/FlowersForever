package com.flowerfarm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("ExportPathResolver")
class ExportPathResolverTest {

    @TempDir Path temp;

    @Test
    @DisplayName("resolves CSV leaf names beneath the configured directory")
    void resolvesSafeLeafName() {
        ExportPathResolver resolver = new ExportPathResolver(temp.toString());

        Path resolved = resolver.resolveCsv("harvest.csv", "fallback.csv");

        assertThat(resolved).isEqualTo(temp.resolve("harvest.csv").toAbsolutePath().normalize());
        assertThat(resolved.getParent()).isEqualTo(temp.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("rejects traversal, absolute paths, subdirectories, and non-CSV names")
    void rejectsUnsafeNames() {
        ExportPathResolver resolver = new ExportPathResolver(temp.toString());

        assertThatIllegalArgumentException().isThrownBy(() ->
                resolver.resolveCsv("../outside.csv", "fallback.csv"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                resolver.resolveCsv(temp.resolve("absolute.csv").toString(), "fallback.csv"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                resolver.resolveCsv("nested/export.csv", "fallback.csv"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                resolver.resolveCsv("export.txt", "fallback.csv"));
    }
}
