package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.SeasonPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Season planting writes preserve acknowledged results and independent recovery drafts")
class SeasonPlannerRecoveryTest {
    private static final int YEAR = LocalDate.now().getYear();
    private static final long ROSE_ID = 11L;
    private static final long DAHLIA_ID = 22L;
    private static final List<String> TEXT_FIELDS = List.of("Crop", "Variety", "Bed or field",
            "Sow date", "Transplant date", "Harvest start", "Harvest end", "Bed length in feet",
            "Expected stems", "Planning notes");

    @ParameterizedTest(name = "{0} committed before {1} reload failure")
    @CsvSource({
            "CREATE, analysis", "UPDATE, analysis", "DELETE, analysis",
            "CREATE, seasons", "UPDATE, seasons", "DELETE, seasons"
    })
    void committedMutationWaitsForFreshSnapshotWithoutBecomingRetryable(Mutation mutation,
                                                                       String failedRead)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.failedRead = failedRead;

        fixture.mutate(mutation);

        assertThat(fixture.attempts).hasValue(1);
        assertThat(fixture.commits).hasValue(1);
        fixture.assertMutationsEnabled(false);
        fixture.assertAcknowledged(mutation);
        // Re-entering action listeners and changing access cannot bypass the reload requirement.
        SwingUtilities.invokeAndWait(() -> {
            fixture.tab.applyRolePermissions(false);
            fixture.tab.applyRolePermissions(true);
            for (String action : List.of("seasonPlanner.add", "seasonPlanner.edit",
                    "seasonPlanner.succession", "seasonPlanner.delete")) {
                JButton button = named(fixture.root, action, JButton.class);
                for (var listener : button.getActionListeners()) {
                    listener.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, action));
                }
            }
        });
        fixture.assertMutationsEnabled(false);
        fixture.refresh();
        fixture.assertAcknowledged(mutation);
        assertThat(fixture.attempts).hasValue(1);

        fixture.failedRead = "";
        fixture.refresh();

        fixture.select("Bed B");
        fixture.assertMutationsEnabled(true);
        fixture.assertAuthoritativeRows(mutation);
        fixture.assertNoRetryDraft();
        assertThat(fixture.attempts).hasValue(1);
        assertThat(fixture.commits).hasValue(1);
    }

    @ParameterizedTest(name = "{0} remains committed when host {1} throws")
    @CsvSource({
            "CREATE, status", "UPDATE, status", "DELETE, status",
            "CREATE, refresh", "UPDATE, refresh", "DELETE, refresh"
    })
    void failedHostCallbackCannotUndoAcknowledgedMutationOrPreventLocalRefresh(Mutation mutation,
                                                                             String callback)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.host.failure = callback;

        fixture.mutate(mutation);

        assertThat(fixture.attempts).hasValue(1);
        assertThat(fixture.commits).hasValue(1);
        assertThat(fixture.reads.get()).isGreaterThanOrEqualTo(2);
        fixture.assertAuthoritativeRows(mutation);
        fixture.select("Bed B");
        fixture.assertMutationsEnabled(true);
        fixture.assertAcknowledged(mutation);
        fixture.assertNoRetryDraft();
        fixture.host.failure = "";
        fixture.refresh();
        assertThat(fixture.attempts).hasValue(1);
    }

    @Test
    void failedEditsKeepTheirOwnRawFieldsAndSavingOneDoesNotRemoveTheOther() throws Exception {
        Fixture fixture = new Fixture();
        SeasonPlanEntry rose = entry(ROSE_ID, "Rose retained", "Bed rose draft", 811);
        SeasonPlanEntry dahlia = entry(DAHLIA_ID, "Dahlia retained", "Bed dahlia draft", 922);
        fixture.failEdit("Bed A", rose);
        fixture.failEdit("Bed B", dahlia);

        fixture.reviewDraft(rose, false);
        fixture.reviewDraft(dahlia, false);
        fixture.assertDraftCount(2);
        fixture.failWrites = false;
        fixture.reviewDraft(rose, true);

        fixture.assertDraftCount(1);
        fixture.reviewDraft(dahlia, false);
        assertThat(fixture.attempts).hasValue(3);
        fixture.reviewDraft(dahlia, true);
        fixture.assertNoRetryDraft();
        assertThat(fixture.attempts).hasValue(4);
        assertThat(fixture.commits).hasValue(2);
    }

    @Test
    void separateFailedNewPlantingsSurviveEachOthersFailuresAndSuccessfulRetry() throws Exception {
        Fixture fixture = new Fixture();
        SeasonPlanEntry first = entry(null, "First new draft", "New bed one", 333);
        SeasonPlanEntry succession = entry(null, "Succession draft", "New bed two", 444);
        fixture.failNew(first);
        when(fixture.service.draftSuccession(any(SeasonPlanEntry.class), anyInt()))
                .thenReturn(succession);
        fixture.select("Bed A");
        fixture.dialogs.confirm("Plan next succession", true, panel -> fillRaw(panel, succession));
        fixture.user.click("Plan succession");
        fixture.awaitIdle();
        fixture.dialogs.assertComplete();

        fixture.assertDraftCount(2);
        fixture.reviewDraft(first, false);
        fixture.reviewDraft(succession, false);
        // Retrying the first draft can fail again without replacing its peer or making a duplicate draft.
        fixture.reviewDraft(first, true);
        fixture.assertDraftCount(2);
        fixture.reviewDraft(succession, false);
        fixture.failWrites = false;
        fixture.reviewDraft(succession, true);

        fixture.assertDraftCount(1);
        fixture.reviewDraft(first, false);
        fixture.reviewDraft(first, true);
        fixture.assertNoRetryDraft();
        assertThat(fixture.attempts).hasValue(5);
        assertThat(fixture.commits).hasValue(2);
    }

    @Test
    void draftReviewAndDiscardAreExplicitAndPreserveUnselectedWork() throws Exception {
        Fixture fixture = new Fixture();
        SeasonPlanEntry rose = entry(ROSE_ID, "Rose discard review", "Bed rose draft", 811);
        SeasonPlanEntry dahlia = entry(DAHLIA_ID, "Dahlia keep review", "Bed dahlia draft", 922);
        fixture.failEdit("Bed A", rose);
        fixture.failEdit("Bed B", dahlia);
        fixture.dialogs.option("Retained planting drafts", "Keep working", panel -> { });
        fixture.user.click("Review drafts (2)");
        fixture.dialogs.assertComplete();
        fixture.assertDraftCount(2);

        fixture.discardDraft(rose, false);
        fixture.assertDraftCount(2);
        fixture.reviewDraft(rose, false);
        fixture.discardDraft(rose, true);

        fixture.assertDraftCount(1);
        fixture.reviewDraft(dahlia, false);
        fixture.discardDraft(dahlia, true);
        fixture.assertNoRetryDraft();
        assertThat(fixture.attempts).hasValue(2);
        assertThat(fixture.commits).hasValue(0);
    }

    @Test
    void successfulFreshCreateDoesNotRemoveAnUnrelatedFailedEdit() throws Exception {
        Fixture fixture = new Fixture();
        SeasonPlanEntry retained = entry(ROSE_ID, "Rose waiting for retry", "Retained bed", 811);
        fixture.failEdit("Bed A", retained);
        fixture.failWrites = false;

        fixture.mutate(Mutation.CREATE);

        fixture.assertDraftCount(1);
        fixture.reviewDraft(retained, false);
        fixture.reviewDraft(retained, true);
        fixture.assertNoRetryDraft();
        assertThat(fixture.attempts).hasValue(3);
    }

    @Test
    void deletingOneFailedEditClearsOnlyThatRecordsDraft() throws Exception {
        Fixture fixture = new Fixture();
        SeasonPlanEntry rose = entry(ROSE_ID, "Rose discarded with delete", "Bed rose draft", 811);
        SeasonPlanEntry dahlia = entry(DAHLIA_ID, "Dahlia survives delete", "Bed dahlia draft", 922);
        fixture.failEdit("Bed A", rose);
        fixture.failEdit("Bed B", dahlia);
        fixture.failWrites = false;

        fixture.mutate(Mutation.DELETE);

        fixture.assertDraftCount(1);
        fixture.reviewDraft(dahlia, false);
        fixture.reviewDraft(dahlia, true);
        fixture.assertNoRetryDraft();
        assertThat(fixture.attempts).hasValue(4);
        assertThat(fixture.commits).hasValue(2);
    }

    @ParameterizedTest(name = "{0}: every raw draft field remains copyable without write access")
    @EnumSource(ReadOnlyReason.class)
    void unavailableRetryStillShowsCompleteReadOnlyDraft(ReadOnlyReason reason) throws Exception {
        Fixture fixture = new Fixture();
        SeasonPlanEntry draft = entry(ROSE_ID, "Raw retained rose", "Retained bed", 811);
        fixture.failEdit("Bed A", draft);
        Map<String, String> expected = rawValues(draft);
        expected.put("Sow date", "not scheduled yet");
        expected.put("Expected stems", "  TBD  ");
        expected.put("Planning notes", "  exact first line\nsecond line  ");
        fixture.dialogs.option("Retained planting drafts", "Review draft",
                panel -> chooseDraft(panel, draft.getCropName()));
        fixture.dialogs.confirm("Retry planting block", false, panel -> {
            assertRaw(panel, draft);
            SwingUserHarness.on(panel).type("Sow date", expected.get("Sow date"))
                    .type("Expected stems", expected.get("Expected stems"))
                    .type("Planning notes", expected.get("Planning notes"));
        });
        fixture.clickReview();

        switch (reason) {
            case VIEWER -> SwingUtilities.invokeAndWait(() -> fixture.tab.applyRolePermissions(false));
            case FAILED_REFRESH -> {
                fixture.failReads = true;
                fixture.refresh();
            }
            case MISSING_TARGET -> {
                fixture.entries.updateAndGet(current -> current.stream()
                        .filter(entry -> !entry.getId().equals(ROSE_ID)).toList());
                fixture.refresh();
            }
            case OTHER_SEASON -> {
                SwingUtilities.invokeAndWait(() -> named(fixture.root, "seasonPlanner.season",
                        JComboBox.class).setSelectedItem(YEAR + 1));
                fixture.awaitIdle();
            }
        }
        fixture.dialogs.option("Retained planting drafts", "Review draft",
                panel -> chooseDraft(panel, draft.getCropName()));
        fixture.dialogs.message("Retained planting draft", panel -> assertReadOnlyRaw(panel, expected));

        fixture.clickReview();

        fixture.assertDraftCount(1);
        assertThat(fixture.attempts).hasValue(1);
        assertThat(fixture.commits).hasValue(0);
    }

    @ParameterizedTest(name = "changed block: {0}, final editor accepted={1}")
    @CsvSource({"Keep draft, false, false", "Use draft values, false, false", "Use draft values, true, true"})
    void changedSavedTargetRequiresExplicitComparisonAndFinalEditorApproval(String conflictChoice,
                                                                          boolean approveEditor,
                                                                          boolean saves)
            throws Exception {
        Fixture fixture = new Fixture();
        SeasonPlanEntry draft = entry(ROSE_ID, "Rose awaiting comparison", "Retained bed", 811);
        fixture.failEdit("Bed A", draft);
        SeasonPlanEntry current = changedSavedRose();
        fixture.replaceRoseAndRefresh(current);
        fixture.failWrites = false;
        fixture.dialogs.option("Retained planting drafts", "Review draft",
                panel -> chooseDraft(panel, draft.getCropName()));
        fixture.dialogs.option("Review changed planting block", conflictChoice,
                panel -> assertComparison(panel, current, draft));
        if ("Use draft values".equals(conflictChoice)) {
            fixture.dialogs.confirm("Retry planting block", approveEditor, panel -> assertRaw(panel, draft));
        }

        fixture.clickReview();

        assertThat(fixture.attempts).hasValue(saves ? 2 : 1);
        assertThat(fixture.commits).hasValue(saves ? 1 : 0);
        if (saves) {
            fixture.assertNoRetryDraft();
            SeasonPlanEntry saved = fixture.entries.get().stream()
                    .filter(entry -> entry.getId().equals(ROSE_ID)).findFirst().orElseThrow();
            assertThat(saved.getCropName()).isEqualTo(draft.getCropName());
            assertThat(saved.getExpectedStems()).isEqualTo(draft.getExpectedStems());
        } else {
            fixture.assertDraftCount(1);
            assertThat(fixture.entries.get()).contains(current);
            if ("Keep draft".equals(conflictChoice)) {
                fixture.dialogs.option("Retained planting drafts", "Review draft",
                        panel -> chooseDraft(panel, draft.getCropName()));
                fixture.dialogs.option("Review changed planting block", "Keep draft",
                        panel -> assertComparison(panel, current, draft));
                fixture.clickReview();
            } else {
                fixture.reviewDraft(draft, false);
            }
        }
    }

    @ParameterizedTest(name = "{0} during changed-block confirmation invalidates approval")
    @EnumSource(ConflictChange.class)
    void changedContextDuringConflictCannotApproveAnOlderDraft(ConflictChange change) throws Exception {
        Fixture fixture = new Fixture();
        SeasonPlanEntry draft = entry(ROSE_ID, "Rose guarded comparison", "Retained bed", 811);
        fixture.failEdit("Bed A", draft);
        SeasonPlanEntry current = changedSavedRose();
        fixture.replaceRoseAndRefresh(current);
        fixture.failWrites = false;
        fixture.dialogs.option("Retained planting drafts", "Review draft",
                panel -> chooseDraft(panel, draft.getCropName()));
        fixture.dialogs.option("Review changed planting block", "Use draft values", panel -> {
            assertComparison(panel, current, draft);
            switch (change) {
                case ACCESS_REVOKED -> fixture.tab.applyRolePermissions(false);
                case REFRESH_STARTED -> fixture.tab.refreshData();
                case TARGET_CHANGED -> current.setNotes("Newer saved notes while comparison was open");
            }
        });

        fixture.clickReview();

        assertThat(fixture.attempts).hasValue(1);
        assertThat(fixture.commits).hasValue(0);
        fixture.assertDraftCount(1);
        // Restored access still requires a fresh comparison; the old confirmation cannot be reused.
        SwingUtilities.invokeAndWait(() -> fixture.tab.applyRolePermissions(true));
        fixture.dialogs.option("Retained planting drafts", "Review draft",
                panel -> chooseDraft(panel, draft.getCropName()));
        fixture.dialogs.option("Review changed planting block", "Keep draft",
                panel -> assertComparison(panel, current, draft));
        fixture.clickReview();
        assertThat(fixture.attempts).hasValue(1);
    }

    private enum ReadOnlyReason { VIEWER, FAILED_REFRESH, MISSING_TARGET, OTHER_SEASON }

    private enum ConflictChange { ACCESS_REVOKED, REFRESH_STARTED, TARGET_CHANGED }

    private enum Mutation {
        CREATE, UPDATE, DELETE
    }

    private static final class Fixture {
        private final SeasonPlanService service = mock(SeasonPlanService.class);
        private final AtomicReference<List<SeasonPlanEntry>> entries = new AtomicReference<>(List.of(
                entry(ROSE_ID, "Nootka Rose", "Bed A", 640),
                entry(DAHLIA_ID, "Dahlia", "Bed B", 480)));
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicLong nextId = new AtomicLong(100);
        private final Dialogs dialogs = new Dialogs();
        private final Host host = new Host();
        private final SeasonPlannerTab tab;
        private final JComponent root;
        private final SwingUserHarness user;
        private volatile String failedRead = "";
        private volatile boolean failReads;
        private volatile boolean failWrites;

        private Fixture() throws Exception {
            when(service.availableSeasons()).thenAnswer(invocation -> {
                if (commits.get() > 0 && "seasons".equals(failedRead)) {
                    throw new IllegalStateException("season list unavailable after commit");
                }
                return List.of(YEAR, YEAR + 1);
            });
            when(service.analyze(anyInt())).thenAnswer(invocation -> {
                reads.incrementAndGet();
                if (failReads || commits.get() > 0 && "analysis".equals(failedRead)) {
                    throw new IllegalStateException("season analysis unavailable after commit");
                }
                int year = invocation.getArgument(0);
                return analysis(entries.get().stream().filter(entry -> entry.getSeasonYear() == year).toList());
            });
            when(service.create(any(SeasonPlanEntry.class))).thenAnswer(invocation -> {
                beginWrite();
                SeasonPlanEntry saved = copy(invocation.getArgument(0), nextId.incrementAndGet());
                entries.updateAndGet(current -> {
                    List<SeasonPlanEntry> updated = new ArrayList<>(current);
                    updated.add(saved);
                    return List.copyOf(updated);
                });
                commits.incrementAndGet();
                return saved;
            });
            when(service.update(anyLong(), any(SeasonPlanEntry.class))).thenAnswer(invocation -> {
                beginWrite();
                long id = invocation.getArgument(0);
                SeasonPlanEntry saved = copy(invocation.getArgument(1), id);
                entries.updateAndGet(current -> current.stream()
                        .map(entry -> entry.getId().equals(id) ? saved : entry).toList());
                commits.incrementAndGet();
                return saved;
            });
            doAnswer(invocation -> {
                beginWrite();
                Long id = invocation.getArgument(0);
                entries.updateAndGet(current -> current.stream()
                        .filter(entry -> !entry.getId().equals(id)).toList());
                commits.incrementAndGet();
                return null;
            }).when(service).delete(anyLong());
            tab = new SeasonPlannerTab(service, host, dialogs);
            JComponent[] built = new JComponent[1];
            SwingUtilities.invokeAndWait(() -> {
                built[0] = tab.getUIComponent();
                tab.initialize();
            });
            root = built[0];
            user = SwingUserHarness.on(root);
            awaitIdle();
            user.selectTab("Plan records");
        }

        private void beginWrite() {
            attempts.incrementAndGet();
            if (failWrites) {
                throw new IllegalStateException("season database busy");
            }
        }

        private void failEdit(String bed, SeasonPlanEntry draft) throws Exception {
            failWrites = true;
            select(bed);
            dialogs.confirm("Edit planting block", true, panel -> fillRaw(panel, draft));
            user.click("Edit selected");
            awaitIdle();
            dialogs.assertComplete();
            assertThat(user.text("seasonPlanner.status")).contains("not saved");
        }

        private void failNew(SeasonPlanEntry draft) {
            failWrites = true;
            dialogs.confirm("Add planting block", true, panel -> fillRaw(panel, draft));
            user.click("Add planting");
            awaitIdle();
            dialogs.assertComplete();
            assertThat(user.text("seasonPlanner.status")).contains("not saved");
        }

        private void reviewDraft(SeasonPlanEntry draft, boolean save) {
            dialogs.option("Retained planting drafts", "Review draft",
                    panel -> chooseDraft(panel, draft.getCropName()));
            dialogs.confirm("Retry planting block", save, panel -> assertRaw(panel, draft));
            clickReview();
        }

        private void clickReview() {
            user.click(named(root, "seasonPlanner.reviewDrafts", JButton.class).getText());
            awaitIdle();
            dialogs.assertComplete();
        }

        private void replaceRoseAndRefresh(SeasonPlanEntry changed) {
            entries.updateAndGet(current -> current.stream()
                    .map(entry -> entry.getId().equals(ROSE_ID) ? changed : entry).toList());
            refresh();
        }

        private void discardDraft(SeasonPlanEntry draft, boolean discard) {
            dialogs.option("Retained planting drafts", "Discard draft",
                    panel -> chooseDraft(panel, draft.getCropName()));
            dialogs.option("Discard planting draft?", discard ? "Discard draft" : "Keep draft", panel -> { });
            user.click(named(root, "seasonPlanner.reviewDrafts", JButton.class).getText());
            dialogs.assertComplete();
        }

        private void assertDraftCount(int expected) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                JButton review = named(root, "seasonPlanner.reviewDrafts", JButton.class);
                assertThat(review.isEnabled()).isTrue();
                assertThat(review.isVisible()).isTrue();
                assertThat(review.getText()).isEqualTo("Review drafts (" + expected + ")");
                assertThat(review.getAccessibleContext().getAccessibleName()).isNotBlank();
                assertThat(tab.applicationExitWarning()).isNotBlank();
            });
        }

        private void mutate(Mutation mutation) throws Exception {
            switch (mutation) {
                case CREATE -> {
                    dialogs.confirm("Add planting block", true,
                            form -> fill(form, entry(null, "Sunflower", "Bed C", 720)));
                    user.click("Add planting");
                }
                case UPDATE -> {
                    select("Bed A");
                    dialogs.confirm("Edit planting block", true,
                            form -> fill(form, entry(ROSE_ID, "Updated Rose", "Bed A revised", 999)));
                    user.click("Edit selected");
                }
                case DELETE -> {
                    select("Bed A");
                    dialogs.confirm("Remove planting block", true, form -> { });
                    user.click("Remove selected");
                }
            }
            awaitIdle();
            dialogs.assertComplete();
        }

        private void select(String bed) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                JTable table = named(root, "seasonPlanner.table", JTable.class);
                for (int row = 0; row < table.getRowCount(); row++) {
                    if (bed.equals(table.getValueAt(row, 0))) {
                        table.setRowSelectionInterval(row, row);
                        return;
                    }
                }
                throw new AssertionError("Missing planting bed " + bed);
            });
        }

        private void refresh() {
            user.click("Refresh");
            awaitIdle();
        }

        private void awaitIdle() {
            user.awaitButtonEnabled("Refresh", true);
        }

        private void assertMutationsEnabled(boolean expected) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                for (String name : List.of("seasonPlanner.add", "seasonPlanner.edit",
                        "seasonPlanner.succession", "seasonPlanner.delete")) {
                    assertThat(named(root, name, JButton.class).isEnabled()).as(name).isEqualTo(expected);
                }
            });
        }

        private void assertAcknowledged(Mutation mutation) {
            String status = user.text("seasonPlanner.status").toLowerCase(java.util.Locale.ROOT);
            assertThat(status).doesNotContain("not saved", "not removed", "retry planting");
            assertThat(status).contains(mutation == Mutation.DELETE ? "removed" : "saved");
        }

        private void assertNoRetryDraft() throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                assertThat(named(root, "seasonPlanner.add", JButton.class).getText()).isEqualTo("Add planting");
                assertThat(tab.applicationExitWarning()).isNull();
                assertThat(tab.hasPendingSave()).isFalse();
            });
        }

        private void assertAuthoritativeRows(Mutation mutation) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                JTable table = named(root, "seasonPlanner.table", JTable.class);
                List<String> beds = new ArrayList<>();
                for (int row = 0; row < table.getRowCount(); row++) {
                    beds.add(String.valueOf(table.getValueAt(row, 0)));
                }
                switch (mutation) {
                    case CREATE -> assertThat(beds).containsExactlyInAnyOrder("Bed A", "Bed B", "Bed C");
                    case UPDATE -> assertThat(beds).containsExactlyInAnyOrder("Bed A revised", "Bed B");
                    case DELETE -> assertThat(beds).containsExactly("Bed B");
                }
            });
        }
    }

    private static void fill(JComponent panel, SeasonPlanEntry entry) {
        SwingUserHarness form = SwingUserHarness.on(panel);
        form.type("Crop", entry.getCropName()).type("Variety", entry.getVariety())
                .type("Bed or field", entry.getBedOrField())
                .type("Sow date", entry.getSowDate().toString())
                .type("Transplant date", entry.getTransplantDate() == null ? "" : entry.getTransplantDate().toString())
                .type("Harvest start", entry.getHarvestStart().toString())
                .type("Harvest end", entry.getHarvestEnd().toString())
                .type("Bed length in feet", Double.toString(entry.getBedFeet()))
                .type("Expected stems", Integer.toString(entry.getExpectedStems()))
                .select("Planting status", entry.getStatus().getLabel())
                .type("Planning notes", entry.getNotes());
    }

    private static void fillRaw(JComponent panel, SeasonPlanEntry entry) {
        fill(panel, entry);
        SwingUserHarness.on(panel).type("Bed length in feet", "080.00")
                .type("Expected stems", "00" + entry.getExpectedStems())
                .type("Planning notes", "  " + entry.getNotes() + "  ");
    }

    private static void assertRaw(JComponent panel, SeasonPlanEntry expected) {
        SwingUserHarness form = SwingUserHarness.on(panel);
        assertThat(form.fieldValue("Crop")).isEqualTo(expected.getCropName());
        assertThat(form.fieldValue("Variety")).isEqualTo(expected.getVariety());
        assertThat(form.fieldValue("Bed or field")).isEqualTo(expected.getBedOrField());
        assertThat(form.fieldValue("Sow date")).isEqualTo(expected.getSowDate().toString());
        assertThat(form.fieldValue("Transplant date")).isEqualTo(expected.getTransplantDate().toString());
        assertThat(form.fieldValue("Harvest start")).isEqualTo(expected.getHarvestStart().toString());
        assertThat(form.fieldValue("Harvest end")).isEqualTo(expected.getHarvestEnd().toString());
        assertThat(form.fieldValue("Bed length in feet")).isEqualTo("080.00");
        assertThat(form.fieldValue("Expected stems")).isEqualTo("00" + expected.getExpectedStems());
        assertThat(form.selectedOption("Planting status")).isEqualTo(expected.getStatus().getLabel());
        assertThat(form.fieldValue("Planning notes")).isEqualTo("  " + expected.getNotes() + "  ");
    }

    private static Map<String, String> fieldValues(SeasonPlanEntry entry) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Crop", entry.getCropName());
        values.put("Variety", entry.getVariety());
        values.put("Bed or field", entry.getBedOrField());
        values.put("Sow date", entry.getSowDate().toString());
        values.put("Transplant date", entry.getTransplantDate() == null ? "" : entry.getTransplantDate().toString());
        values.put("Harvest start", entry.getHarvestStart().toString());
        values.put("Harvest end", entry.getHarvestEnd().toString());
        values.put("Bed length in feet", Double.toString(entry.getBedFeet()));
        values.put("Expected stems", Integer.toString(entry.getExpectedStems()));
        values.put("Planting status", entry.getStatus().getLabel());
        values.put("Planning notes", entry.getNotes());
        return values;
    }

    private static Map<String, String> rawValues(SeasonPlanEntry entry) {
        Map<String, String> values = fieldValues(entry);
        values.put("Bed length in feet", "080.00");
        values.put("Expected stems", "00" + entry.getExpectedStems());
        values.put("Planning notes", "  " + entry.getNotes() + "  ");
        return values;
    }

    private static void assertReadOnlyRaw(JComponent panel, Map<String, String> expected) {
        for (String label : TEXT_FIELDS) {
            JTextComponent field = accessible(panel, label, JTextComponent.class);
            assertThat(field).as(label).isNotNull();
            assertThat(field.getText()).as(label).isEqualTo(expected.get(label));
            assertThat(field.isEditable()).as(label + " remains read only").isFalse();
            assertThat(field.isEnabled()).as(label + " remains copyable").isTrue();
            field.selectAll();
            assertThat(field.getSelectedText()).as(label + " can be selected for copying")
                    .isEqualTo(expected.get(label));
        }
        JComboBox<?> status = accessible(panel, "Planting status", JComboBox.class);
        assertThat(status.isEnabled()).isFalse();
        assertThat(status.getSelectedItem().toString()).isEqualTo(expected.get("Planting status"));
    }

    private static void assertComparison(JComponent panel, SeasonPlanEntry current, SeasonPlanEntry draft) {
        JTextComponent details = accessible(panel, "Current planting and retained draft details", JTextComponent.class);
        assertThat(details).isNotNull();
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isEditable()).isFalse();
        String text = details.getText();
        assertThat(text).contains("CURRENT SAVED BLOCK", "RETAINED DRAFT");
        int divider = text.indexOf("RETAINED DRAFT");
        String savedText = text.substring(0, divider);
        String draftText = text.substring(divider);
        assertThat(savedText).contains("Season: " + current.getSeasonYear());
        assertThat(draftText).contains("Season: " + draft.getSeasonYear());
        fieldValues(current).forEach((label, value) -> assertThat(savedText).contains(label + ": " + value + "\n"));
        rawValues(draft).forEach((label, value) -> assertThat(draftText).contains(label + ": " + value + "\n"));
    }

    private static SeasonPlanEntry changedSavedRose() {
        SeasonPlanEntry entry = entry(ROSE_ID, "Saved by another worker", "Changed saved bed", 1200);
        entry.setVariety("Changed saved variety");
        entry.setSowDate(LocalDate.of(YEAR, 3, 5));
        entry.setTransplantDate(LocalDate.of(YEAR, 4, 25));
        entry.setHarvestStart(LocalDate.of(YEAR, 7, 1));
        entry.setHarvestEnd(LocalDate.of(YEAR, 10, 15));
        entry.setBedFeet(140.5);
        entry.setStatus(SeasonPlanStatus.HARVESTING);
        entry.setNotes("Saved planning details from another worker\nSecond saved line");
        return entry;
    }

    private static void chooseDraft(JComponent panel, String crop) {
        JComboBox<?> choices = named(panel, "seasonPlanner.draftChoice", JComboBox.class);
        assertThat(choices).isNotNull();
        assertThat(choices.getAccessibleContext().getAccessibleName()).isNotBlank();
        for (int index = 0; index < choices.getItemCount(); index++) {
            if (String.valueOf(choices.getItemAt(index)).contains(crop)) {
                choices.setSelectedIndex(index);
                return;
            }
        }
        throw new AssertionError("Missing retained planting draft for " + crop);
    }

    private static SeasonPlanEntry entry(Long id, String crop, String bed, int expected) {
        SeasonPlanEntry entry = new SeasonPlanEntry(YEAR, crop, "Trial variety", bed,
                LocalDate.of(YEAR, 3, 1), LocalDate.of(YEAR, 4, 12),
                LocalDate.of(YEAR, 6, 20), LocalDate.of(YEAR, 9, 10),
                80, expected, SeasonPlanStatus.PLANTED, "Market harvest notes for " + crop);
        entry.setId(id);
        return entry;
    }

    private static SeasonPlanEntry copy(SeasonPlanEntry source, Long id) {
        SeasonPlanEntry copy = new SeasonPlanEntry(source.getSeasonYear(), source.getCropName(),
                source.getVariety(), source.getBedOrField(), source.getSowDate(), source.getTransplantDate(),
                source.getHarvestStart(), source.getHarvestEnd(), source.getBedFeet(), source.getExpectedStems(),
                source.getStatus(), source.getNotes());
        copy.setId(id);
        return copy;
    }

    private static SeasonPlanService.SeasonAnalysis analysis(List<SeasonPlanEntry> entries) {
        long expected = entries.stream().mapToLong(SeasonPlanEntry::getExpectedStems).sum();
        SeasonPlanService.SeasonSummary summary = new SeasonPlanService.SeasonSummary(
                entries.size(), entries.size(), entries.size(), entries.size(), entries.size() * 80,
                expected, entries.isEmpty() ? null : LocalDate.of(YEAR, 6, 20),
                entries.isEmpty() ? null : LocalDate.of(YEAR, 9, 10));
        return new SeasonPlanService.SeasonAnalysis(entries, summary, List.of(), List.of(),
                List.of(), List.of(), 0, 0, 0);
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        if (name.equals(root.getName()) && type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T result = named(child, name, type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T accessible(Component root, String name, Class<T> type) {
        if (root.getAccessibleContext() != null
                && name.equals(root.getAccessibleContext().getAccessibleName()) && type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T result = accessible(child, name, type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static final class Dialogs implements FarmDialogs {
        private final Deque<Answer> answers = new ArrayDeque<>();

        private void confirm(String title, boolean approve, Consumer<JComponent> inspect) {
            answers.addLast(new Answer(title, approve, null, inspect));
        }

        private void option(String title, String choice, Consumer<JComponent> inspect) {
            answers.addLast(new Answer(title, false, choice, inspect));
        }

        private void message(String title, Consumer<JComponent> inspect) {
            answers.addLast(new Answer(title, false, "MESSAGE", inspect));
        }

        private void assertComplete() {
            assertThat(answers).isEmpty();
        }

        @Override
        public int showConfirm(Component parent, Object message, String title, int optionType, int messageType) {
            Answer answer = answers.pollFirst();
            assertThat(answer).as("Expected a scripted confirmation for %s", title).isNotNull();
            assertThat(title).isEqualTo(answer.title());
            assertThat(answer.option()).isNull();
            answer.inspect().accept((JComponent) message);
            return answer.approve() ? JOptionPane.OK_OPTION : JOptionPane.CANCEL_OPTION;
        }

        @Override
        public int showOption(Component parent, Object message, String title, int optionType, int messageType,
                              Icon icon, Object[] options, Object initialValue) {
            Answer answer = answers.pollFirst();
            assertThat(answer).as("Expected a scripted option for %s", title).isNotNull();
            assertThat(title).isEqualTo(answer.title());
            assertThat(initialValue).isEqualTo(options[0]);
            if ("Review changed planting block".equals(title)) {
                assertThat(options).containsExactly("Keep draft", "Use draft values");
            } else {
                assertThat(options[0]).isEqualTo("Discard planting draft?".equals(title) ? "Keep draft" : "Keep working");
            }
            answer.inspect().accept((JComponent) message);
            for (int index = 0; index < options.length; index++) {
                if (answer.option().equals(String.valueOf(options[index]))) {
                    return index;
                }
            }
            throw new AssertionError("Missing option " + answer.option() + " in " + title);
        }

        @Override
        public void showMessage(Component parent, Object message, String title, int messageType) {
            Answer answer = answers.pollFirst();
            assertThat(answer).as("Expected a scripted message for %s", title).isNotNull();
            assertThat(title).isEqualTo(answer.title());
            assertThat(answer.option()).isEqualTo("MESSAGE");
            answer.inspect().accept((JComponent) message);
        }
    }

    private record Answer(String title, boolean approve, String option, Consumer<JComponent> inspect) { }

    private static final class Host implements TabHost {
        private final List<String> statuses = new CopyOnWriteArrayList<>();
        private volatile String failure = "";

        @Override public void refreshAll() {
            if ("refresh".equals(failure)) {
                throw new IllegalStateException("host refresh unavailable");
            }
        }
        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) {
            statuses.add(message);
            if ("status".equals(failure)) {
                throw new IllegalStateException("host status unavailable");
            }
        }
        @Override public void runTrendAnalysis() { }
    }
}
