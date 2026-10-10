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
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
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
        tab = edt(() -> new EquipmentReadingsTab(service, host, dialogs, files,
                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC)));
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

    @Test void changesReadsSelectedModelRowInBackgroundForViewerAndKeepsDraft() throws Exception {
        adapter("z-probe", "one"); adapter("a-probe", "two");
        edt(() -> { tab.applyRolePermissions(false); tab.refreshData(); return null; });
        await(() -> table().getRowCount() == 2);
        AtomicReference<Component> popup = new AtomicReference<>();
        doAnswer(call -> { assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); popup.set(call.getArgument(1)); return null; })
                .when(dialogs).showMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt());
        AtomicBoolean background = new AtomicBoolean();
        doAnswer(call -> { background.set(!SwingUtilities.isEventDispatchThread()); return call.callRealMethod(); })
                .when(service).measurementHistory(anyLong());
        assertThat(edt(() -> component("changes", JButton.class).isEnabled())).isFalse();
        edt(() -> {
            table().setAutoCreateRowSorter(true);
            table().getRowSorter().toggleSortOrder(0);
            table().setRowSelectionInterval(1, 1);
            return null;
        });
        click("changes");
        await(() -> popup.get() != null && component("changes", JButton.class).isEnabled());
        var description = (javax.swing.JTextArea) find(popup.get(), "equipmentReadings.changesDescription");
        assertThat(description.getText()).contains("z-probe").doesNotContain("a-probe");
        assertThat(background).isTrue();
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        assertThat(service.count()).isEqualTo(2);
    }

    @Test void changesDropsObsoleteResultAfterSelectionChangesWithoutBlockingRefresh() throws Exception {
        adapter("probe-1", "one"); adapter("probe-2", "two");
        edt(() -> { tab.refreshData(); return null; }); await(() -> table().getRowCount() == 2);
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean returned = new AtomicBoolean();
        doAnswer(call -> {
            entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            var result = call.callRealMethod(); returned.set(true); return result;
        }).when(service).measurementHistory(anyLong());
        edt(() -> { table().setRowSelectionInterval(0, 0); return null; }); click("changes");
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(edt(() -> component("changes", JButton.class).isEnabled())).isFalse();
            edt(() -> { table().setRowSelectionInterval(1, 1); return null; });
        } finally { release.countDown(); }
        await(() -> returned.get() && component("changes", JButton.class).isEnabled());
        org.mockito.Mockito.verify(dialogs, org.mockito.Mockito.never()).showMessage(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt());
    }

    @Test void changesFailureAllowsRetryAndDoesNotResetManualWork() throws Exception {
        adapter("probe", "one");
        edt(() -> { tab.refreshData(); return null; }); await(() -> table().getRowCount() == 1);
        edt(() -> { table().setRowSelectionInterval(0, 0); return null; });
        doThrow(new IllegalStateException("offline")).when(service).measurementHistory(anyLong());
        click("changes"); await(() -> feedback().contains("Could not read") && component("changes", JButton.class).isEnabled());
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        org.mockito.Mockito.doCallRealMethod().when(service).measurementHistory(anyLong());
        AtomicBoolean opened = new AtomicBoolean();
        doAnswer(call -> { opened.set(true); return null; }).when(dialogs).showMessage(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt());
        click("changes"); await(opened::get);
        assertThat(edt(this::feedback)).contains("Measurement history loaded");
        assertThat(service.count()).isEqualTo(1);
    }

    @Test void changesLoadingDisablesSelectionActionAndFailedRefreshKeepsCorrectIdentity() throws Exception {
        adapter("probe", "one");
        edt(() -> { tab.refreshData(); return null; }); await(() -> table().getRowCount() == 1);
        edt(() -> { table().setRowSelectionInterval(0, 0); return null; });
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            throw new IllegalStateException("offline");
        }).when(service).recent(anyLong(), anyInt());
        edt(() -> { tab.refreshData(); return null; }); assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        try { assertThat(edt(() -> component("changes", JButton.class).isEnabled())).isFalse(); }
        finally { release.countDown(); }
        await(() -> feedback().contains("Could not refresh") && component("changes", JButton.class).isEnabled());
        AtomicReference<Component> popup = new AtomicReference<>();
        doAnswer(call -> { popup.set(call.getArgument(1)); return null; }).when(dialogs).showMessage(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt());
        click("changes"); await(() -> popup.get() != null);
        assertThat(((JTable) find(popup.get(), "equipmentReadings.changesHistory")).getRowCount()).isEqualTo(1);
    }

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

    @Test void latestReportedViewUsesMeasuredTimeAndShowsAgeWithoutLosingManualDraft() throws Exception {
        adapterAt("newer", "2026-10-10T11:59:00Z", "GOOD", "22");
        adapterAt("delayed", "2026-10-08T12:00:00Z", "GOOD", "19");
        click("latest");
        await(() -> table().getRowCount() == 2 && component("latest", JButton.class).isEnabled());
        assertThat(edt(() -> table().getValueAt(0, 3))).isEqualTo("19");
        assertThat(edt(() -> table().getValueAt(0, 8))).isEqualTo("2 days old");
        chooseView(1);
        await(() -> table().getRowCount() == 1 && component("view", JComboBox.class).isEnabled());
        assertThat(edt(() -> table().getValueAt(0, 3))).isEqualTo("22");
        assertThat(edt(() -> table().getValueAt(0, 8))).isEqualTo("1 minute old");
        assertThat(edt(() -> component("pageInfo", JLabel.class).getText())).startsWith("Latest reported measurements")
                .contains("Age at " + java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
                        .withZone(java.time.ZoneId.systemDefault()).format(Instant.parse("2026-10-10T12:00:00Z")));
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        assertThat(edt(tab::applicationExitWarning)).contains("unfinished reading");
    }

    @Test void newerMissingAndErrorReportsStayVisibleInsteadOfForwardFillingOldGoodValue() throws Exception {
        adapterAt("good", "2026-10-10T11:00:00Z", "GOOD", "21");
        adapterAt("missing", "2026-10-10T11:59:00Z", "MISSING", null);
        chooseView(1);
        await(() -> table().getRowCount() == 1 && component("latest", JButton.class).isEnabled());
        assertThat(edt(() -> table().getValueAt(0, 3))).isEqualTo("No value");
        assertThat(edt(() -> table().getValueAt(0, 5))).isEqualTo("No reading");
        adapterAt("error", "2026-10-10T11:59:30Z", "ERROR", null);
        click("latest");
        await(() -> "Equipment error".equals(table().getValueAt(0, 5)) && component("latest", JButton.class).isEnabled());
        assertThat(edt(() -> table().getValueAt(0, 3))).isEqualTo("No value");
        assertThat(edt(() -> table().getValueAt(0, 8))).isEqualTo("Under a minute old");
    }

    @Test void viewerCanReviewLatestAndFutureClockIsFlaggedWithoutEnablingWrites() throws Exception {
        adapterAt("clock-ahead", "2026-10-10T12:00:30Z", "GOOD", "22");
        edt(() -> { tab.applyRolePermissions(false); return null; });
        chooseView(1);
        await(() -> table().getRowCount() == 1 && component("view", JComboBox.class).isEnabled());
        assertThat(edt(() -> table().getValueAt(0, 8))).isEqualTo("Clock ahead — check time");
        assertThat(edt(() -> component("save", JButton.class).isEnabled())).isFalse();
        assertThat(edt(() -> component("import", JButton.class).isEnabled())).isFalse();
    }

    @Test void failedOverviewRetainsActualDisplayedScopeAndHistoryThenRetryRecovers() throws Exception {
        adapterAt("one", "2026-10-10T11:00:00Z", "GOOD", "21");
        adapterAt("two", "2026-10-10T11:59:00Z", "GOOD", "22");
        click("latest");
        await(() -> table().getRowCount() == 2 && component("latest", JButton.class).isEnabled());
        doThrow(new IllegalStateException("Database unavailable")).when(service).latestReported(anyLong(), anyInt());
        chooseView(1);
        await(() -> feedback().contains("Could not refresh") && component("view", JComboBox.class).isEnabled());
        assertThat(edt(() -> table().getRowCount())).isEqualTo(2);
        assertThat(edt(() -> component("pageInfo", JLabel.class).getText())).startsWith("All saved readings");
        assertThat(edt(() -> component("older", JButton.class).isEnabled())).isFalse();
        org.mockito.Mockito.doCallRealMethod().when(service).latestReported(anyLong(), anyInt());
        click("latest");
        await(() -> table().getRowCount() == 1 && component("view", JComboBox.class).isEnabled());
        assertThat(edt(() -> component("pageInfo", JLabel.class).getText())).startsWith("Latest reported measurements");
        assertThat(edt(this::feedback)).isEqualTo("Readings refreshed.");
    }

    @Test void obsoleteHistoryCompletionDoesNotOverwriteNewlyChosenOverview() throws Exception {
        adapterAt("actual", "2026-10-10T11:59:00Z", "GOOD", "22");
        var obsolete = service.recent(0, 1).readings().get(0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean background = new AtomicBoolean();
        doAnswer(call -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new FarmReadingService.ReadingPage(List.of(new com.flowerfarm.telemetry.FarmReading(
                    obsolete.id(), obsolete.sourceId(), obsolete.readingKey(), "obsolete-device", obsolete.metric(),
                    obsolete.unit(), obsolete.value(), obsolete.quality(), obsolete.observedAt(), obsolete.receivedAt(),
                    obsolete.location(), obsolete.rawFrame())), obsolete.id(), false);
        }).when(service).recent(anyLong(), anyInt());
        doAnswer(call -> { background.set(!SwingUtilities.isEventDispatchThread()); return call.callRealMethod(); })
                .when(service).latestReported(anyLong(), anyInt());
        List<String> namesShown = new ArrayList<>();
        edt(() -> {
            table().getModel().addTableModelListener(event -> {
                if (table().getRowCount() > 0) namesShown.add(String.valueOf(table().getValueAt(0, 0)));
            });
            return null;
        });
        try {
            click("latest");
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            chooseView(1);
        } finally { release.countDown(); }
        await(() -> table().getRowCount() == 1 && component("view", JComboBox.class).isEnabled());
        assertThat(edt(() -> List.copyOf(namesShown))).doesNotContain("obsolete-device");
        assertThat(background).isTrue();
        assertThat(edt(() -> component("pageInfo", JLabel.class).getText())).startsWith("Latest reported measurements");
    }

    @Test void overviewPagesAcrossDistinctEquipmentAndSwitchingBackResetsHistoryCursor() throws Exception {
        List<Map<String, Object>> readings = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            readings.add(Map.of("readingKey", "row-" + i, "deviceId", "probe-" + i, "metric", "water.ph", "unit", "pH",
                    "value", BigDecimal.ONE, "observedAt", "2026-10-10T11:00:00Z"));
        }
        transaction.executeWithoutResult(status -> service.ingest("logger", readings));
        chooseView(1);
        await(() -> table().getRowCount() == 200 && component("older", JButton.class).isEnabled());
        click("older");
        await(() -> table().getRowCount() == 1 && component("latest", JButton.class).isEnabled());
        assertThat(edt(() -> component("older", JButton.class).isEnabled())).isFalse();
        chooseView(0);
        await(() -> table().getRowCount() == 200 && component("older", JButton.class).isEnabled());
        assertThat(edt(() -> component("pageInfo", JLabel.class).getText())).startsWith("All saved readings");
    }

    @Test void changingReadOnlyViewPreservesKeptSpreadsheetPreviewAndItsRetryIdentity() throws Exception {
        adapterAt("adapter", "2026-10-10T11:00:00Z", "GOOD", "21");
        spreadsheet("1", "Measured");
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.NO_OPTION);
        click("import");
        await(() -> feedback().contains("preview kept"));
        chooseView(1);
        await(() -> table().getRowCount() == 1 && component("view", JComboBox.class).isEnabled());
        assertThat(edt(tab::applicationExitWarning)).contains("spreadsheet preview");
        assertThat(edt(() -> component("save", JButton.class).isEnabled())).isFalse();
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.YES_OPTION);
        click("import");
        await(() -> service.count() == 2 && table().getRowCount() == 2 && component("import", JButton.class).isEnabled());
        assertThat(edt(() -> component("view", JComboBox.class).getSelectedIndex())).isEqualTo(1);
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
    }

    @Test void spreadsheetCopyIncludesFullHistoryWhileLatestViewAndDraftStayIntact() throws Exception {
        for (int i = 0; i < 205; i++) adapterAt("copy-" + i, "2026-10-10T11:00:00Z", "GOOD", "21.125000001");
        chooseView(1);
        await(() -> table().getRowCount() == 1 && component("view", JComboBox.class).isEnabled());
        Path target = copyTarget();
        var background = new AtomicBoolean();
        doAnswer(call -> {
            background.set(!SwingUtilities.isEventDispatchThread());
            return new FarmReadingService(jdbc).snapshotForCopy();
        }).when(service).snapshotForCopy();
        click("export");
        await(() -> !tab.hasPendingSave() && feedback().contains("Spreadsheet copy saved: 205"));
        assertThat(Files.readString(target)).contains("21.125000001");
        assertThat(Files.readAllLines(target)).hasSize(206);
        assertThat(background).isTrue();
        assertThat(edt(() -> table().getRowCount())).isEqualTo(1);
        assertThat(edt(() -> component("view", JComboBox.class).getSelectedIndex())).isEqualTo(1);
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        assertThat(edt(tab::applicationExitWarning)).contains("unfinished reading");
        assertThat(service.count()).isEqualTo(205);
    }

    @Test void viewerCanCopyAlreadyReadableHistoryWithoutSavingOrEnablingEdits() throws Exception {
        adapterAt("viewer", "2026-10-10T11:00:00Z", "MISSING", null);
        when(host.canMutateData()).thenReturn(false);
        edt(() -> { tab.applyRolePermissions(false); return null; });
        Path target = copyTarget(); click("export");
        await(() -> !tab.hasPendingSave() && feedback().contains("Spreadsheet copy saved: 1"));
        assertThat(Files.readString(target)).contains("No reading");
        assertThat(edt(() -> component("save", JButton.class).isEnabled())).isFalse();
        assertThat(edt(() -> component("import", JButton.class).isEnabled())).isFalse();
        assertThat(service.count()).isEqualTo(1);
    }

    @Test void declinedCopyReviewKeepsExistingFileAndExplainsReplacementAndScope() throws Exception {
        adapterAt("review", "2026-10-10T11:00:00Z", "GOOD", "21");
        Path target = copyTarget(); Files.writeString(target, "previous spreadsheet");
        var reviewed = new AtomicReference<String>();
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt(), anyInt()))
                .thenAnswer(call -> {
                    Component review = call.getArgument(1);
                    reviewed.set(((javax.swing.JTextArea) find(review, "equipmentReadings.exportReview")).getText());
                    return JOptionPane.NO_OPTION;
                });
        click("export");
        await(() -> !tab.hasPendingSave() && feedback().contains("copy cancelled"));
        assertThat(reviewed.get()).contains("1 saved readings", "all equipment and sources", "beyond the current page",
                "replace the existing file", target.toRealPath().toString(), "not a bring-in sheet", "full farm backup");
        assertThat(Files.readString(target)).isEqualTo("previous spreadsheet");
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
    }

    @Test void cancelledChooserAndEmptyHistoryNeverCreateOrReplaceFile() throws Exception {
        when(files.chooseSave(org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        click("export");
        assertThat(edt(tab::hasPendingSave)).isFalse();
        Path target = copyTarget(); Files.writeString(target, "old copy");
        click("export");
        await(() -> !tab.hasPendingSave() && feedback().contains("no saved readings"));
        assertThat(Files.readString(target)).isEqualTo("old copy");
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
    }

    @Test void copyFailurePreservesFileAndDraftAndAllowsReviewedRetry() throws Exception {
        adapterAt("failure", "2026-10-10T11:00:00Z", "GOOD", "21");
        Path target = copyTarget(); Files.writeString(target, "old copy");
        doThrow(new IllegalStateException("database unavailable")).when(service).copyPage(
                org.mockito.ArgumentMatchers.any(), anyLong(), anyInt());
        click("export");
        await(() -> !tab.hasPendingSave() && feedback().contains("not completed"));
        assertThat(Files.readString(target)).isEqualTo("old copy");
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        doAnswer(call -> new FarmReadingService(jdbc).copyPage(call.getArgument(0), call.getArgument(1), call.getArgument(2)))
                .when(service).copyPage(org.mockito.ArgumentMatchers.any(), anyLong(), anyInt());
        click("export");
        await(() -> !tab.hasPendingSave() && feedback().contains("Spreadsheet copy saved: 1"));
        assertThat(Files.readString(target)).doesNotContain("old copy");
    }

    @Test void runningCopyBlocksWritesAndExitButDoesNotBlockTheEdt() throws Exception {
        adapterAt("pending", "2026-10-10T11:00:00Z", "GOOD", "21");
        copyTarget();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test release timed out");
            return new FarmReadingService(jdbc).copyPage(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        }).when(service).copyPage(org.mockito.ArgumentMatchers.any(), anyLong(), anyInt());
        try {
            click("export"); assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(edt(tab::hasPendingSave)).isTrue();
            assertThat(edt(tab::applicationExitWarning)).contains("copy still running");
            for (String button : List.of("save", "new", "import", "export")) {
                assertThat(edt(() -> component(button, JButton.class).isEnabled())).isFalse();
            }
            assertThat(edt(() -> text("value").isEnabled())).isFalse();
            assertThat(service.count()).isEqualTo(1);
        } finally { release.countDown(); }
        await(() -> !tab.hasPendingSave() && feedback().contains("Spreadsheet copy saved"));
    }

    @Test void copyingKeepsSpreadsheetPreviewAndManualDraftAvailableForLaterSave() throws Exception {
        adapterAt("kept", "2026-10-10T11:00:00Z", "GOOD", "21");
        spreadsheet("38.5", "Measured");
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.NO_OPTION);
        click("import"); await(() -> feedback().contains("preview kept"));
        Path target = copyTarget();
        when(dialogs.showConfirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.YES_OPTION);
        click("export"); await(() -> !tab.hasPendingSave() && feedback().contains("Spreadsheet copy saved: 1"));
        assertThat(Files.readAllLines(target)).hasSize(2);
        assertThat(edt(tab::applicationExitWarning)).contains("spreadsheet preview");
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        click("import"); await(() -> !tab.hasPendingSave() && service.count() == 2);
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
    }

    @Test void invalidCopyFilenameReportsProblemWithoutClearingUnfinishedEntry() throws Exception {
        when(files.chooseSave(org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(directory.resolve("wrong.txt").toFile()));
        click("export"); await(() -> !tab.hasPendingSave() && feedback().contains("ending in .csv"));
        assertThat(edt(() -> text("value").getText())).isEqualTo("21.125000001");
        assertThat(directory.resolve("wrong.txt")).doesNotExist();
    }

    private Path copyTarget() {
        Path target = directory.resolve("saved-readings.csv");
        when(files.chooseSave(org.mockito.ArgumentMatchers.any(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(target.toFile()));
        return target;
    }

    private void chooseView(int index) throws Exception {
        edt(() -> { component("view", JComboBox.class).setSelectedIndex(index); return null; });
    }

    private void adapterAt(String key, String time, String quality, String value) {
        Map<String, Object> row = new HashMap<>(Map.of("readingKey", key, "deviceId", "probe-1", "metric", "air.temperature",
                "unit", "°C", "quality", quality, "observedAt", time, "location", "North house"));
        if (value != null) row.put("value", new BigDecimal(value));
        transaction.executeWithoutResult(status -> service.ingest("bridge", List.of(row)));
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
    @Test void compactWindowKeepsRowsVisibleAndBottomControlsReachableByScrolling() throws Exception {
        adapterAt("compact", "2026-10-10T11:59:00Z", "GOOD", "22");
        chooseView(1);
        await(() -> table().getRowCount() == 1 && component("view", JComboBox.class).isEnabled());
        edt(() -> {
            com.flowerfarm.gui.harness.SwingUserHarness.on(root).layoutAt(860, 700);
            var scroll = component("pageScroll", javax.swing.JScrollPane.class);
            assertThat(table().getVisibleRect().height).isGreaterThanOrEqualTo(table().getRowHeight() * 3);
            assertThat(scroll.getVerticalScrollBar().isVisible()).isTrue();
            JComponent controls = component("latest", JButton.class);
            java.awt.Rectangle target = javax.swing.SwingUtilities.convertRectangle(controls.getParent(),
                    controls.getBounds(), scroll.getViewport().getView());
            ((JComponent) scroll.getViewport().getView()).scrollRectToVisible(target);
            assertThat(scroll.getViewport().getViewRect().contains(target)).isTrue();
            assertThat(component("pageInfo", JLabel.class).getText()).startsWith("Latest reported measurements");
            return null;
        });
    }

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
