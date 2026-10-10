package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.repository.HarvestRepository;
import com.flowerfarm.repository.SyncHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ServerCsvExportServiceTest {
    @TempDir Path temporary;

    private Path root() {
        return temporary.resolve("server-exports");
    }

    private ServerCsvExportService service() {
        return new ServerCsvExportService(root().toString());
    }

    @Test
    void writesUtf8InsideConfiguredRootAndReplacesExistingExport() throws IOException {
        ServerCsvExportService service = service();
        service.exportCsv("harvest_log.csv", writer -> writer.write("Crop,Notes\nRose,été\n"));
        assertThat(Files.readString(root().resolve("harvest_log.csv"), StandardCharsets.UTF_8))
                .isEqualTo("Crop,Notes\nRose,été\n");
        service.exportCsv("harvest_log.csv", writer -> writer.write("Crop,Notes\nDahlia,new\n"));
        assertThat(Files.readString(root().resolve("harvest_log.csv")))
                .isEqualTo("Crop,Notes\nDahlia,new\n");
        assertOnlyExportRemains("harvest_log.csv");
    }

    @Test
    void rejectsPathsAndWindowsSpecialNamesBeforeOpeningAnything() throws IOException {
        Path outside = Files.writeString(temporary.resolve("outside.csv"), "outside sentinel");
        AtomicBoolean invoked = new AtomicBoolean();
        List<String> rejected = List.of(
                "../outside.csv", "..\\outside.csv", outside.toString(), "/tmp/outside.csv",
                "folder/export.csv", "folder\\export.csv", "C:\\outside.csv", "C:outside.csv",
                "\\\\server\\share\\outside.csv", "harvest.csv:stream", "CON.csv", "prn.csv",
                "AUX.csv", "NUL.csv", "COM1.csv", "lpt9.csv", "CON.more.csv", "export.csv.",
                "export.csv ", " export.csv", ".csv", "..csv", "export..csv", "export.txt",
                "export.csv\n", "export\rcsv", "export.csv\u0000", "", "a".repeat(129) + ".csv");
        for (String filename : rejected) {
            assertThatIllegalArgumentException().as(filename)
                    .isThrownBy(() -> service().exportCsv(filename, writer -> invoked.set(true)));
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service().exportCsv(null, writer -> invoked.set(true)));
        assertThat(invoked).isFalse();
        assertThat(Files.exists(root())).isFalse();
        assertThat(Files.readString(outside)).isEqualTo("outside sentinel");
    }

    @Test
    void serializationFailureKeepsExistingFileAndRemovesStagedContent() throws IOException {
        service().exportCsv("history.csv", writer -> writer.write("previous contents"));
        assertThatIllegalStateException().isThrownBy(() -> service().exportCsv("history.csv", writer -> {
            writer.write("partial replacement");
            throw new IOException("fixture write failure");
        })).withMessageContaining("fixture write failure");
        assertThat(Files.readString(root().resolve("history.csv"))).isEqualTo("previous contents");
        assertOnlyExportRemains("history.csv");
    }

    @Test
    void refusesDirectoryDestinationWithoutChangingIt() throws IOException {
        Files.createDirectories(root().resolve("history.csv"));
        assertThatIllegalStateException().isThrownBy(
                () -> service().exportCsv("history.csv", writer -> writer.write("not a directory")));
        assertThat(Files.isDirectory(root().resolve("history.csv"))).isTrue();
        assertOnlyExportRemains("history.csv");
    }

    @Test
    void existingRedirectNeverWritesItsOutsideTarget() throws Exception {
        Files.createDirectories(root());
        Path outsideDirectory = Files.createDirectory(temporary.resolve("outside"));
        Path outside = Files.writeString(outsideDirectory.resolve("history.csv"), "outside sentinel");
        Path redirect = root().resolve("history.csv");
        createCsvRedirect(redirect, outside);
        try {
            assertThatIllegalStateException().isThrownBy(
                    () -> service().exportCsv("history.csv", writer -> writer.write("replacement")));
            assertThat(Files.exists(redirect, java.nio.file.LinkOption.NOFOLLOW_LINKS)).isTrue();
            assertThat(Files.readString(outside)).isEqualTo("outside sentinel");
            assertOnlyExportRemains("history.csv");
        } finally {
            Files.delete(redirect); // Delete the redirect entry, never its target.
        }
    }

    @Test
    void redirectInsertedDuringSerializationIsRejected() throws Exception {
        Files.createDirectories(root());
        Path outsideDirectory = Files.createDirectory(temporary.resolve("outside"));
        Path outside = Files.writeString(outsideDirectory.resolve("history.csv"), "outside sentinel");
        Path redirect = root().resolve("history.csv");
        try {
            assertThatIllegalStateException().isThrownBy(() -> service().exportCsv("history.csv", writer -> {
                createCsvRedirect(redirect, outside);
                writer.write("replacement");
            }));
            assertThat(Files.exists(redirect, java.nio.file.LinkOption.NOFOLLOW_LINKS)).isTrue();
            assertThat(Files.readString(outside)).isEqualTo("outside sentinel");
            assertOnlyExportRemains("history.csv");
        } finally {
            Files.deleteIfExists(redirect);
        }
    }

    @Test
    void directoryRedirectConfiguredRootIsRejectedWithoutOutsideWrites() throws IOException {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        createDirectoryRedirect(root(), outside);
        assertThatIllegalStateException().isThrownBy(
                () -> service().exportCsv("history.csv", writer -> writer.write("replacement")));
        try (var files = Files.list(outside)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void directoryRedirectAncestorIsRejectedBeforeCreatingExportDirectory() throws IOException {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path ancestor = temporary.resolve("linked-parent");
        createDirectoryRedirect(ancestor, outside);
        ServerCsvExportService service = new ServerCsvExportService(ancestor.resolve("exports").toString());
        assertThatIllegalStateException().isThrownBy(
                () -> service.exportCsv("history.csv", writer -> writer.write("replacement")));
        try (var files = Files.list(outside)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void directoryRedirectConfiguredRootCannotWriteOutsideExports() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("history.csv"), "outside sentinel");
        createDirectoryRedirect(root(), outside);
        try {
            assertThatIllegalStateException().isThrownBy(
                    () -> service().exportCsv("history.csv", writer -> writer.write("replacement")));
            assertThat(Files.readString(sentinel)).isEqualTo("outside sentinel");
            try (var files = Files.list(outside)) {
                assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("history.csv");
            }
        } finally {
            Files.delete(root()); // Remove only the junction entry, before @TempDir cleanup.
        }
    }

    @Test
    void directoryRedirectAncestorCannotCreateExportsOutsideRoot() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("sentinel.csv"), "outside sentinel");
        Path ancestor = temporary.resolve("junction-parent");
        createDirectoryRedirect(ancestor, outside);
        try {
            ServerCsvExportService service = new ServerCsvExportService(ancestor.resolve("exports").toString());
            assertThatIllegalStateException().isThrownBy(
                    () -> service.exportCsv("history.csv", writer -> writer.write("replacement")));
            assertThat(Files.exists(outside.resolve("exports"))).isFalse();
            assertThat(Files.readString(sentinel)).isEqualTo("outside sentinel");
        } finally {
            Files.delete(ancestor); // Remove only the junction entry.
        }
    }

    @Test
    void replacingHardLinkEntryLeavesOutsideFileContentsUnchanged() throws IOException {
        Files.createDirectories(root());
        Path outside = Files.writeString(temporary.resolve("outside.csv"), "outside sentinel");
        Files.createLink(root().resolve("history.csv"), outside);
        service().exportCsv("history.csv", writer -> writer.write("new export"));
        assertThat(Files.readString(outside)).isEqualTo("outside sentinel");
        assertThat(Files.readString(root().resolve("history.csv"))).isEqualTo("new export");
        assertThat(Files.isSameFile(root().resolve("history.csv"), outside)).isFalse();
    }

    @Test
    void observedRootReplacementCannotPublishInTheReplacementDirectory() throws IOException {
        Files.createDirectories(root());
        boolean secureProvider;
        try (var stream = Files.newDirectoryStream(root())) {
            secureProvider = stream instanceof SecureDirectoryStream<?>;
        }
        if (!secureProvider) {
            Files.setAttribute(root(), "basic:creationTime", FileTime.from(Instant.parse("2000-01-01T00:00:00Z")));
        }
        Path retired = temporary.resolve("retired-root");
        Path outside = Files.writeString(temporary.resolve("outside.csv"), "outside sentinel");
        AtomicBoolean replaced = new AtomicBoolean();
        AtomicReference<IOException> renameUnavailable = new AtomicReference<>();
        Throwable failure = catchThrowable(() -> service().exportCsv("history.csv", writer -> {
            try {
                Files.move(root(), retired);
            } catch (IOException unavailable) {
                renameUnavailable.set(unavailable);
                throw unavailable;
            }
            Files.createDirectory(root());
            replaced.set(true);
            writer.write("replacement");
        }));
        if (renameUnavailable.get() != null) {
            // Windows may deny moving an open directory. That denial must still abort the export.
            assertThat(failure).isInstanceOf(IllegalStateException.class);
            assertThat(replaced).isFalse();
        } else if (secureProvider) {
            // Unix binds writes to the original directory handle, even after its path is replaced.
            assertThat(failure).isNull();
            assertThat(replaced).isTrue();
            assertThat(Files.readString(retired.resolve("history.csv"))).isEqualTo("replacement");
        } else {
            assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("directory changed");
            assertThat(replaced).isTrue();
        }
        assertThat(Files.exists(root().resolve("history.csv"))).isFalse();
        try (var files = Files.list(root())) {
            assertThat(files.toList()).isEmpty();
        }
        assertThat(Files.readString(outside)).isEqualTo("outside sentinel");
    }

    @Test
    void operatorSelectedPathsRemainSeparateFromServerBasenames() throws IOException {
        Files.createDirectories(root());
        Path outside = Files.writeString(temporary.resolve("outside.csv"), "outside sentinel");
        String selectedLocalPath = root().resolve("../outside.csv").toString();
        HarvestService harvest = new HarvestService(mock(HarvestRepository.class),
                mock(InventoryService.class), mock(SyncHistoryService.class),
                new HarvestReportGenerator(new FarmProfile()), new HarvestCsvExporter(), new FarmProfile());
        SyncHistoryService history = new SyncHistoryService(mock(SyncHistoryRepository.class), new FarmProfile());
        // These unchanged local APIs are the original HTTP sinks documented in the baseline caller receipt.
        harvest.exportToCsv(selectedLocalPath, List.of());
        assertThat(Files.readString(outside)).startsWith("Id,Date,Crop,MarketableQuantity,Unit,BedOrField,Notes");
        history.exportToCsv(selectedLocalPath, List.of());
        assertThat(Files.readString(outside)).startsWith("Id,When,Connector,Operation,Success,RecordCount,Message,Detail");
        Files.writeString(outside, "outside sentinel");
        assertThatIllegalArgumentException().isThrownBy(
                () -> service().exportCsv("../outside.csv", writer -> harvest.writeCsv(writer, List.of())));
        assertThatIllegalArgumentException().isThrownBy(
                () -> service().exportCsv("../outside.csv", writer -> history.writeCsv(writer, List.of())));
        assertThat(Files.readString(outside)).isEqualTo("outside sentinel");
    }

    @Test
    void localHarvestSelectedPathAndServerExportKeepIdenticalCsv() throws IOException {
        HarvestService harvest = new HarvestService(mock(HarvestRepository.class),
                mock(InventoryService.class), mock(SyncHistoryService.class),
                new HarvestReportGenerator(new FarmProfile()), new HarvestCsvExporter(), new FarmProfile());
        HarvestEntry entry = new HarvestEntry(LocalDate.of(2026, 7, 10), "Rose, red", 12.5,
                "stems", "Bed \"A\"", "été \"notes\"");
        entry.setId(42L);
        Path selected = temporary.resolve("desktop-selected.csv");
        harvest.exportToCsv(selected.toString(), List.of(entry));
        service().exportCsv("harvest.csv", writer -> harvest.writeCsv(writer, List.of(entry)));
        assertThat(Files.readAllBytes(root().resolve("harvest.csv"))).isEqualTo(Files.readAllBytes(selected));
        assertThat(Files.readString(selected)).startsWith("Id,Date,Crop,MarketableQuantity,Unit,BedOrField,Notes")
                .contains("42,2026-07-10,\"Rose, red\",12.50,stems,\"Bed \"\"A\"\"\",\"été \"\"notes\"\"\"");
    }

    @Test
    void localInventorySelectedPathAndServerExportKeepIdenticalCsv() throws IOException {
        InventoryCsvExporter exporter = new InventoryCsvExporter();
        com.flowerfarm.model.Item row = new com.flowerfarm.model.Item(
                "Rose, red", "Flowers", 2.5, "stems", 1, 12, "été notes");
        Path selected = temporary.resolve("desktop-inventory.csv");
        exporter.export(selected.toString(), List.of(row));
        service().exportCsv("inventory.csv", writer -> exporter.writeCsv(writer, List.of(row)));
        assertThat(Files.readAllBytes(root().resolve("inventory.csv"))).isEqualTo(Files.readAllBytes(selected));
        assertThat(Files.readString(selected)).startsWith(InventoryService.CSV_HEADER).contains("été notes");
    }

    @Test
    void localHistorySelectedPathAndServerExportKeepIdenticalCsv() throws IOException {
        SyncHistoryService history = new SyncHistoryService(mock(SyncHistoryRepository.class), new FarmProfile());
        SyncHistoryEntry entry = new SyncHistoryEntry("csv", "export", true, "été \"message\"", "detail", 2);
        Path selected = temporary.resolve("desktop-selected.csv");
        history.exportToCsv(selected.toString(), List.of(entry));
        service().exportCsv("history.csv", writer -> history.writeCsv(writer, List.of(entry)));
        assertThat(Files.readAllBytes(root().resolve("history.csv"))).isEqualTo(Files.readAllBytes(selected));
        assertThat(Files.readString(selected)).startsWith("Id,When,Connector,Operation,Success,RecordCount,Message,Detail")
                .contains(",csv,EXPORT,true,2,\"été \"\"message\"\"\",\"detail\"");
    }

    private void assertOnlyExportRemains(String filename) throws IOException {
        try (var files = Files.list(root())) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly(filename);
        }
    }

    private static void createCsvRedirect(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException unavailable) {
            if (!System.getProperty("os.name").startsWith("Windows")) {
                throw new IOException("Required symbolic-link fixture is unavailable", unavailable);
            }
            // Directory junctions need no symlink privilege and still exercise redirected-target rejection.
            createDirectoryRedirect(link, target.getParent());
        }
    }

    private static void createDirectoryRedirect(Path link, Path target) throws IOException {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            Files.createSymbolicLink(link, target);
            return;
        }
        // Literal PowerShell quoting and encoded command prevent path text becoming shell syntax.
        String command = "New-Item -ItemType Junction -Path '" + link.toString().replace("'", "''")
                + "' -Target '" + target.toString().replace("'", "''") + "' -ErrorAction Stop | Out-Null";
        String encoded = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                .redirectErrorStream(true).start();
        boolean completed;
        try {
            completed = process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Directory junction fixture interrupted", interrupted);
        }
        if (!completed) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new IOException("Directory junction creation exceeded fixture budget");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException("Directory junction fixture unavailable: " + output);
        }
    }
}
