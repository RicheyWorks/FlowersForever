package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.telemetry.EquipmentSpreadsheet;
import com.flowerfarm.telemetry.FarmReadingService;
import com.flowerfarm.telemetry.ManualFarmReading;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class EquipmentReadingsTabTest {
    private JdbcTemplate jdbc;
    private FarmReadingService service;
    private TransactionTemplate transaction;
    private TabHost host;
    private FarmDialogs dialogs;
    private EquipmentReadingsTab tab;
    private JComponent root;
    private FarmFileChooser files;
    @TempDir Path directory;

    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource data = new DriverManagerDataSource(
                "jdbc:h2:mem:equipment-ui-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/h2/V50__farm_readings.sql")).execute(data);
        jdbc = new JdbcTemplate(data);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(data));
        FarmReadingService real = new FarmReadingService(jdbc);
        service = spy(real);
        doAnswer(call -> transaction.execute(status -> real.ingest(call.getArgument(0), call.getArgument(1))))
                .when(service).ingest(anyString(), anyCollection());
        host = mock(TabHost.class);
        when(host.canMutateData()).thenReturn(true);
        dialogs = mock(FarmDialogs.class);
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyInt(), anyInt())).thenReturn(JOptionPane.YES_OPTION);
        files = mock(FarmFileChooser.class);
        when(files.chooseEquipmentSpreadsheet(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(Optional.empty());
        tab = edt(() -> new EquipmentReadingsTab(service, host, dialogs, files));
        root = tab.getUIComponent();
        edt(() -> {
            text("equipment").setText("North greenhouse probe");
            text("area").setText("Tomato house");
            text("unit").setText("°C");
            text("value").setText("21.125000001");
            component("observed", JSpinner.class).setValue(Date.from(Instant.parse("2026-10-01T12:00:00Z")));
            return null;
        });
    }

    @AfterEach void close() { jdbc.execute("SHUTDOWN"); }

    @Test void reviewedSavePersistsExactValueNameAreaAndTimeWithoutBlockingEdt() throws Exception {
        AtomicBoolean background = new AtomicBoolean();
        doAnswer(call -> {
            background.set(!SwingUtilities.isEventDispatchThread());
            return transaction.execute(status -> new FarmReadingService(jdbc)
                    .ingest(call.getArgument(0), call.getArgument(1)));
        }).when(service).ingest(anyString(), anyCollection());
        click("save");
        await(() -> service.count() == 1 && table().getRowCount() == 1 && text("value").getText().isEmpty());
        var saved = service.history(ManualFarmReading.SOURCE, 0, 10).readings().get(0);
        assertThat(saved.value()).isEqualByComparingTo("21.125000001");
        assertThat(saved.unit()).isEqualTo("°C");
        assertThat(saved.observedAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(saved.location()).isEqualTo("Tomato house");
        assertThat(ManualFarmReading.equipmentName(saved)).isEqualTo("North greenhouse probe");
        assertThat(background).isTrue();
        assertThat(edt(() -> table().getValueAt(0, 3))).isEqualTo("21.125000001");
        assertThat(edt(tab::applicationExitWarning)).isNull();
    }

    @Test void cancelledReviewKeepsDraftAndWritesNothing() throws Exception {
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyInt(), anyInt())).thenReturn(JOptionPane.NO_OPTION);
        click("save");
        assertThat(service.count()).isZero();
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        assertThat(edt(tab::applicationExitWarning)).contains("unfinished reading");
    }

    @Test void missingObservationIsNotConvertedToZero() throws Exception {
        edt(() -> {
            text("value").setText("");
            component("condition", JComboBox.class).setSelectedIndex(2);
            return null;
        });
        click("save");
        await(() -> service.count() == 1 && table().getRowCount() == 1);
        assertThat(service.history(ManualFarmReading.SOURCE, 0, 10).readings().get(0).value()).isNull();
        assertThat(edt(() -> table().getValueAt(0, 3))).isEqualTo("No value");
        assertThat(edt(() -> table().getValueAt(0, 5))).isEqualTo("No reading");
    }

    @Test void invalidNumberAndConflictingMissingValueKeepDraftWithoutWrites() throws Exception {
        edt(() -> { text("value").setText("21 °C"); return null; });
        click("save");
        assertThat(edt(this::feedback)).contains("Enter a number", "draft is kept");
        edt(() -> {
            text("value").setText("0");
            component("condition", JComboBox.class).setSelectedIndex(2);
            return null;
        });
        click("save");
        assertThat(edt(this::feedback)).contains("missing is not zero", "draft is kept");
        assertThat(service.count()).isZero();
    }

    @Test void measuredBlankAndExcessPrecisionAreRejectedBeforeReview() throws Exception {
        edt(() -> { text("value").setText(""); return null; });
        click("save");
        assertThat(edt(this::feedback)).contains("Enter a value");
        edt(() -> { text("value").setText("0.1234567890123"); return null; });
        click("save");
        assertThat(edt(this::feedback)).contains("12 digits after");
        assertThat(service.count()).isZero();
    }

    @Test void roleRevocationDuringReviewPreventsTheSaveAndKeepsDraft() throws Exception {
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyInt(), anyInt())).thenAnswer(call -> {
                    when(host.canMutateData()).thenReturn(false);
                    tab.applyRolePermissions(false);
                    return JOptionPane.YES_OPTION;
                });
        click("save");
        assertThat(service.count()).isZero();
        assertThat(edt(this::feedback)).contains("access changed", "draft is kept");
        assertThat(edt(() -> component("save", JButton.class).isEnabled())).isFalse();
    }

    @Test void viewerCanReadAdapterHistoryWhileEntryIsDisabled() throws Exception {
        adapter("probe-1", "before");
        edt(() -> { tab.applyRolePermissions(false); tab.refreshData(); return null; });
        await(() -> table().getRowCount() == 1);
        assertThat(edt(() -> component("save", JButton.class).isEnabled())).isFalse();
        assertThat(edt(() -> text("equipment").isEnabled())).isFalse();
        assertThat(edt(() -> component("latest", JButton.class).isEnabled())).isTrue();
        assertThat(edt(() -> table().getValueAt(0, 0))).isEqualTo("probe-1");
        assertThat(edt(() -> table().getValueAt(0, 7))).isEqualTo("farm-bridge");
    }

    @Test void unconfirmedCommittedSaveRetriesTheSameKeyWithoutDuplicatingIt() throws Exception {
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            var result = transaction.execute(status -> new FarmReadingService(jdbc)
                    .ingest(call.getArgument(0), call.getArgument(1)));
            if (first.getAndSet(false)) throw new IllegalStateException("Simulated lost confirmation");
            return result;
        }).when(service).ingest(anyString(), anyCollection());
        click("save");
        await(() -> feedback().contains("Could not confirm") && component("save", JButton.class).isEnabled());
        assertThat(edt(tab::applicationExitWarning)).contains("could not be confirmed");
        assertThat(service.count()).isEqualTo(1);
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        click("save");
        await(() -> text("value").getText().isEmpty() && table().getRowCount() == 1);
        assertThat(service.count()).isEqualTo(1);
        assertThat(edt(this::feedback)).contains("No duplicate");
    }

    @Test void doubleClickDuringSlowSaveProducesOneObservation() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return transaction.execute(status -> new FarmReadingService(jdbc)
                    .ingest(call.getArgument(0), call.getArgument(1)));
        }).when(service).ingest(anyString(), anyCollection());
        try {
            click("save");
            assertThat(edt(() -> component("save", JButton.class).isEnabled())).isFalse();
            assertThat(edt(tab::hasPendingSave)).isTrue();
            assertThat(edt(tab::applicationExitWarning)).contains("save still running");
            click("save");
        } finally { release.countDown(); }
        await(() -> service.count() == 1 && component("save", JButton.class).isEnabled());
        assertThat(service.count()).isEqualTo(1);
    }

    @Test void failedRefreshRetainsPreviouslyDisplayedReadingsAndAllowsRetry() throws Exception {
        adapter("probe-1", "one");
        click("latest");
        await(() -> table().getRowCount() == 1 && component("latest", JButton.class).isEnabled());
        doThrow(new IllegalStateException("Database unavailable")).when(service).recent(anyLong(), anyInt());
        click("latest");
        await(() -> feedback().contains("Could not refresh"));
        assertThat(edt(() -> table().getRowCount())).isEqualTo(1);
        assertThat(edt(() -> component("latest", JButton.class).isEnabled())).isTrue();
    }

    @Test void newestReceiptPagingShowsAllSourcesWithoutRepeatingPageBoundary() throws Exception {
        for (int i = 0; i < 201; i++) adapter("probe-1", "reading-" + i);
        click("latest");
        await(() -> table().getRowCount() == 200 && component("older", JButton.class).isEnabled());
        click("older");
        await(() -> table().getRowCount() == 1 && component("latest", JButton.class).isEnabled());
        assertThat(edt(() -> component("older", JButton.class).isEnabled())).isFalse();
        click("latest");
        await(() -> table().getRowCount() == 200);
    }

    @Test void refreshRequestedDuringOlderPageLoadEventuallyReturnsToLatest() throws Exception {
        adapter("probe-1", "one");
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            if (first.getAndSet(false)) {
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return new FarmReadingService.ReadingPage(List.of(), 1, false);
            }
            return new FarmReadingService(jdbc).recent(call.getArgument(0), call.getArgument(1));
        }).when(service).recent(anyLong(), anyInt());
        try {
            edt(() -> { tab.refreshData(); tab.refreshData(); return null; });
        } finally { release.countDown(); }
        await(() -> table().getRowCount() == 1 && component("latest", JButton.class).isEnabled());
    }

    @Test void newReadingRequiresExplicitDraftDiscardAndKeepsEquipmentContext() throws Exception {
        click("new");
        assertThat(edt(() -> text("value").getText())).isEmpty();
        assertThat(edt(() -> text("equipment").getText())).isEqualTo("North greenhouse probe");
        assertThat(edt(() -> text("area").getText())).isEqualTo("Tomato house");
        assertThat(edt(() -> text("unit").getText())).isEqualTo("°C");
        assertThat(edt(this::feedback)).contains("new reading");
        assertThat(edt(tab::applicationExitWarning)).isNull();
    }

    @Test void cancelledNewReadingKeepsTheWholeDraft() throws Exception {
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyInt(), anyInt())).thenReturn(JOptionPane.NO_OPTION);
        click("new");
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        assertThat(service.count()).isZero();
    }

    @Test void futureObservationTimeCannotBeSaved() throws Exception {
        edt(() -> {
            component("observed", JSpinner.class).setValue(Date.from(Instant.now().plusSeconds(3600)));
            return null;
        });
        click("save");
        assertThat(edt(this::feedback)).contains("five minutes ahead", "draft is kept");
        assertThat(service.count()).isZero();
    }

    @Test void importedMarkupStaysLiteralInTheHistoryRenderer() throws Exception {
        transaction.executeWithoutResult(status -> new FarmReadingService(jdbc).ingest("farm-bridge",
                List.of(Map.of("readingKey", "html", "deviceId", "probe", "metric", "water.ph", "unit", "pH",
                        "value", BigDecimal.ONE, "observedAt", "2026-10-01T12:00:00Z",
                        "location", "<html><img src='https://example.invalid/pixel'>"))));
        click("latest");
        await(() -> table().getRowCount() == 1);
        edt(() -> {
            var renderer = (JComponent) table().prepareRenderer(table().getCellRenderer(0, 1), 0, 1);
            assertThat(renderer.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
            assertThat(((JLabel) renderer).getText()).startsWith("<html>");
            return null;
        });
    }

    @Test void spreadsheetReviewSavesExactRowsInBackgroundWithoutChangingManualDraft() throws Exception {
        spreadsheet("21.125000000001", "Measured");
        AtomicBoolean background = new AtomicBoolean();
        doAnswer(call -> {
            background.set(!SwingUtilities.isEventDispatchThread());
            return transaction.execute(status -> new FarmReadingService(jdbc).ingest(call.getArgument(0), call.getArgument(1)));
        }).when(service).ingest(anyString(), anyCollection());
        click("import");
        await(() -> service.count() == 1 && table().getRowCount() == 1 && component("import", JButton.class).isEnabled());
        var row = service.history(EquipmentSpreadsheet.SOURCE, 0, 10).readings().get(0);
        assertThat(row.value()).isEqualByComparingTo("21.125000000001");
        assertThat(ManualFarmReading.equipmentName(row)).isEqualTo("Sheet probe");
        assertThat(background).isTrue();
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        assertThat(edt(() -> table().getValueAt(0, 7))).isEqualTo("Equipment spreadsheet");
        assertThat(edt(tab::applicationExitWarning)).contains("unfinished reading");
        click("import");
        await(() -> feedback().contains("0 new readings; 1 already saved") && component("import", JButton.class).isEnabled());
        assertThat(service.count()).isEqualTo(1);
    }

    @Test void cancelSelectionWritesNothingAndKeepsManualDraft() throws Exception {
        click("import");
        assertThat(service.count()).isZero();
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        assertThat(edt(() -> component("save", JButton.class).isEnabled())).isTrue();
    }

    @Test void accessChangeDuringFileSelectionPreventsReadingOrSavingFile() throws Exception {
        Path file = spreadsheet("1", "Measured");
        when(files.chooseEquipmentSpreadsheet(org.mockito.ArgumentMatchers.any(), anyString())).thenAnswer(call -> {
            when(host.canMutateData()).thenReturn(false);
            tab.applyRolePermissions(false);
            return Optional.of(file.toFile());
        });
        click("import");
        assertThat(service.count()).isZero();
        assertThat(edt(this::feedback)).contains("access changed", "No spreadsheet");
        assertThat(edt(tab::applicationExitWarning)).contains("unfinished reading");
    }

    @Test void unreadableSelectedFileLeavesNoPreviewAndPreservesManualDraft() throws Exception {
        Path file = spreadsheet("1", "Measured");
        Files.delete(file);
        click("import");
        await(() -> feedback().contains("Choose a spreadsheet") && component("import", JButton.class).isEnabled());
        assertThat(service.count()).isZero();
        assertThat(edt(() -> component("save", JButton.class).isEnabled())).isTrue();
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
    }

    @Test void declinedPreviewIsKeptAndExplicitLeaveRestoresManualDraft() throws Exception {
        spreadsheet("0", "Measured");
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.NO_OPTION);
        click("import");
        await(() -> feedback().contains("preview kept"));
        assertThat(service.count()).isZero();
        assertThat(edt(tab::applicationExitWarning)).contains("spreadsheet preview");
        click("leaveImport");
        assertThat(edt(tab::applicationExitWarning)).contains("spreadsheet preview");
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.YES_OPTION);
        click("leaveImport");
        assertThat(edt(() -> component("save", JButton.class).isEnabled())).isTrue();
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
    }

    @Test void invalidLaterRowAndMissingWithZeroPreventAllWritesBeforeReview() throws Exception {
        Path file = spreadsheet("1", "Measured");
        Files.writeString(file, Files.readString(file) + "Other probe,House,Soil moisture,not a number,%,Measured,2026-10-01T12:00:01Z\n");
        click("import");
        await(() -> feedback().contains("Reading 2:") && component("import", JButton.class).isEnabled());
        assertThat(service.count()).isZero();
        spreadsheet("0", "No reading");
        click("import");
        await(() -> feedback().contains("missing is not zero") && component("import", JButton.class).isEnabled());
        assertThat(service.count()).isZero();
    }

    @Test void roleRevocationDuringSpreadsheetReviewKeepsPreviewWithoutSaving() throws Exception {
        spreadsheet("1", "Measured");
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt(), anyInt()))
                .thenAnswer(call -> { when(host.canMutateData()).thenReturn(false); tab.applyRolePermissions(false); return JOptionPane.YES_OPTION; });
        click("import");
        await(() -> feedback().contains("access changed"));
        assertThat(service.count()).isZero();
        assertThat(edt(tab::applicationExitWarning)).contains("spreadsheet preview");
        assertThat(edt(() -> component("import", JButton.class).isEnabled())).isFalse();
    }

    @Test void uncertainSpreadsheetCommitRetriesKeptRowsEvenIfFileChanges() throws Exception {
        Path file = spreadsheet("1", "Measured");
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            var result = transaction.execute(status -> new FarmReadingService(jdbc).ingest(call.getArgument(0), call.getArgument(1)));
            if (first.getAndSet(false)) throw new IllegalStateException("Lost confirmation");
            return result;
        }).when(service).ingest(anyString(), anyCollection());
        click("import");
        await(() -> feedback().contains("Could not confirm the spreadsheet save") && component("import", JButton.class).isEnabled());
        assertThat(edt(tab::applicationExitWarning)).contains("could not be confirmed");
        Files.writeString(file, "broken sheet");
        click("import");
        await(() -> feedback().contains("0 new readings; 1 already saved") && component("import", JButton.class).isEnabled());
        assertThat(service.count()).isEqualTo(1);
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
    }

    @Test void existingConflictRollsBackEarlierNewRowInSpreadsheetBatch() throws Exception {
        Path file = spreadsheet("1", "Measured");
        var existing = EquipmentSpreadsheet.read(file, service);
        transaction.executeWithoutResult(status -> service.ingest(EquipmentSpreadsheet.SOURCE, existing));
        String conflicting = Files.readString(file).replace(",1,", ",9,");
        Files.writeString(file, String.join(",", EquipmentSpreadsheet.HEADINGS) + "\n"
                + "New probe,House,Soil moisture,30,%,Measured,2026-10-01T12:00:01Z\n"
                + conflicting.substring(conflicting.indexOf('\n') + 1));
        click("import");
        await(() -> feedback().contains("Could not confirm the spreadsheet save") && component("import", JButton.class).isEnabled());
        assertThat(service.count()).isEqualTo(1);
        assertThat(service.history(EquipmentSpreadsheet.SOURCE, 0, 10).readings().get(0).value()).isEqualByComparingTo("1");
    }

    @Test void slowSpreadsheetSaveBlocksRepeatClicksAndReportsPendingExit() throws Exception {
        spreadsheet("1", "Measured");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return transaction.execute(status -> new FarmReadingService(jdbc).ingest(call.getArgument(0), call.getArgument(1)));
        }).when(service).ingest(anyString(), anyCollection());
        try {
            click("import");
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(edt(tab::hasPendingSave)).isTrue();
            assertThat(edt(tab::applicationExitWarning)).contains("spreadsheet being read or saved");
            assertThat(edt(() -> component("import", JButton.class).isEnabled())).isFalse();
            assertThat(edt(() -> component("save", JButton.class).isEnabled())).isFalse();
            click("import");
        } finally { release.countDown(); }
        await(() -> service.count() == 1 && component("import", JButton.class).isEnabled());
    }

    @Test void previewRendererKeepsSpreadsheetMarkupLiteralAndMissingValueAbsent() throws Exception {
        Path file = spreadsheet("", "No reading");
        Files.writeString(file, Files.readString(file).replace("House", "<html><img src='https://example.invalid/pixel'>"));
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt(), anyInt()))
                .thenAnswer(call -> {
                    JTable preview = (JTable) find((Component) call.getArgument(1), "equipmentReadings.importPreview");
                    assertThat(preview.getRowCount()).isEqualTo(1);
                    assertThat(preview.getValueAt(0, 3)).isEqualTo("No value");
                    JComponent rendered = (JComponent) preview.prepareRenderer(preview.getCellRenderer(0, 1), 0, 1);
                    assertThat(rendered.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
                    return JOptionPane.YES_OPTION;
                });
        click("import");
        await(() -> service.count() == 1 && component("import", JButton.class).isEnabled());
        assertThat(service.history(EquipmentSpreadsheet.SOURCE, 0, 10).readings().get(0).value()).isNull();
    }

    private Path spreadsheet(String value, String quality) throws Exception {
        Path file = directory.resolve("equipment.csv");
        Files.writeString(file, String.join(",", EquipmentSpreadsheet.HEADINGS) + "\n"
                + "Sheet probe,House,Soil moisture," + value + ",%," + quality + ",2026-10-01T12:00:00Z\n");
        when(files.chooseEquipmentSpreadsheet(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(Optional.of(file.toFile()));
        return file;
    }

    private void adapter(String device, String key) {
        transaction.executeWithoutResult(status -> new FarmReadingService(jdbc).ingest("farm-bridge",
                List.of(Map.of("readingKey", key, "deviceId", device, "metric", "water.ph", "unit", "pH",
                        "value", new BigDecimal("6.75"), "observedAt", "2026-10-01T12:00:00Z"))));
    }
    private JTextField text(String id) { return component(id, JTextField.class); }
    private JTable table() { return component("history", JTable.class); }
    private String feedback() { return component("feedback", JLabel.class).getText(); }
    private void click(String id) throws Exception {
        edt(() -> { component(id, JButton.class).doClick(); return null; });
    }
    private <T extends Component> T component(String id, Class<T> type) {
        return type.cast(find(root, "equipmentReadings." + id));
    }
    private static Component find(Component component, String name) {
        if (name.equals(component.getName())) return component;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component match = find(child, name);
                if (match != null) return match;
            }
        }
        return null;
    }
    private static <T> T edt(Callable<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { value.set(action.call()); } catch (Exception exception) { failure.set(exception); }
        });
        if (failure.get() != null) throw failure.get();
        return value.get();
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (edt(condition)) return;
            Thread.sleep(10);
        }
        assertThat(edt(condition)).isTrue();
    }
}
