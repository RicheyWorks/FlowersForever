package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.model.FieldScienceExportSummary;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.service.KnowledgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Knowledge Log workspace guidance")
class KnowledgeLogWorkspaceTest {

    @Test
    void searchExampleMatchesWorkspaceAndKeepsRoseDefault() throws Exception {
        assertPrompt(null, "Try: What worked for mildew on this rose last August?");
        assertPrompt(FarmWorkspace.WILDFLOWER_FARM,
                "Try: Which wildflower mix held up best after harvest?");
        assertPrompt(FarmWorkspace.FLOWER_SHOP,
                "Try: Which bouquet display sold well last spring?");
        assertPrompt(FarmWorkspace.CANNABIS_FARM,
                "Try: What changed after the last watering adjustment?");
        assertPrompt(FarmWorkspace.POTATO_FARM,
                "Try: Which field held its potato quality after harvest?");
        assertPrompt(FarmWorkspace.APPLE_ORCHARD,
                "Try: What worked in the Honeycrisp block last season?");
        assertPrompt(FarmWorkspace.BERRY_FARM,
                "Try: Which berry block held quality through the last pick?");
        assertPrompt(FarmWorkspace.NURSERY_FARM,
                "Try: Which growing batch was ready on schedule?");
        assertPrompt(FarmWorkspace.LIVESTOCK_FARM,
                "Try: Which pasture plan worked best for this herd group?");
        assertPrompt(FarmWorkspace.POULTRY_FARM,
                "Try: Which flock had the most consistent egg collection this week?");
        assertPrompt(FarmWorkspace.DAIRY_FARM,
                "Try: Which milking routine worked best for this group?");
        assertPrompt(FarmWorkspace.VINEYARD_FARM,
                "Try: Which block needs the next vineyard check?");
        assertPrompt(FarmWorkspace.HAY_FARM,
                "Try: What changed between this field's cuttings?");
        assertPrompt(FarmWorkspace.HOPS_FARM,
                "Try: Which hop yard needs the next stringing check?");
        assertPrompt(FarmWorkspace.GRAIN_FARM,
                "Try: What worked in this field's last crop rotation?");
        assertPrompt(FarmWorkspace.SEED_FARM,
                "Try: What changed during this seed field's flowering window?");
        assertPrompt(FarmWorkspace.NUT_ORCHARD,
                "Try: What helped this nut block prepare for harvest?");
        assertPrompt(FarmWorkspace.VEGETABLE_FARM,
                "Try: Which planting interval worked for this vegetable crop?");
        assertPrompt(FarmWorkspace.AQUACULTURE_FARM,
                "Try: Which growing-site conditions worked for this aquatic cohort?");
        assertPrompt(FarmWorkspace.CHRISTMAS_TREE_FARM,
                "Try: Which shearing plan worked for this tree block?");
        assertPrompt(FarmWorkspace.APIARY_FARM,
                "Try: What did you observe at the last hive check?");
        assertPrompt(FarmWorkspace.MUSHROOM_FARM,
                "Try: Which substrate and method worked for this mushroom batch?");
        assertPrompt(FarmWorkspace.CRANBERRY_FARM,
                "Try: What worked for this cranberry bed's harvest plan?");
        assertPrompt(FarmWorkspace.PUMPKIN_PATCH,
                "Try: Which patch layout worked for pick-your-own visitors?");
        assertPrompt(FarmWorkspace.CUSTOM_FARM,
                "Try: What worked in field 3 last season?");
    }

    @Test
    void nonRoseLessonFormUsesNeutralCropGuidance() throws Exception {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        AtomicReference<Component> dialogBody = new AtomicReference<>();
        when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    dialogBody.set(invocation.getArgument(1));
                    return JOptionPane.CANCEL_OPTION;
                });
        KnowledgeLogTab tab = new KnowledgeLogTab(
                mock(KnowledgeService.class), new TestHost(), dialogs);
        tab.setWorkspace(FarmWorkspace.CANNABIS_FARM);
        JComponent root = onEdt(tab::getUIComponent);

        onEdt(() -> descendants(root, JButton.class).stream()
                .filter(button -> "knowledge.record".equals(button.getName()))
                .findFirst().orElseThrow().doClick());

        JTextField crop = descendants(dialogBody.get(), JTextField.class).stream()
                .filter(field -> "Crop or product".equals(
                        field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(crop.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("Use the crop, product, or operation name your crew recognizes.");
    }

    @Test
    void workspaceSelectionClosesWhenTheUiIsBuilt() throws Exception {
        KnowledgeLogTab tab = new KnowledgeLogTab(
                mock(KnowledgeService.class), new TestHost());
        onEdt(tab::getUIComponent);
        assertThatThrownBy(() -> tab.setWorkspace(FarmWorkspace.CUSTOM_FARM))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fieldScienceFindingShowsSourceButNeedsAFarmObservationBeforeSaving() throws Exception {
        String title = "Soil recipes · Subcool’s Super Soil";
        String source = "https://github.com/RicheyWorks/CSRBT/blob/example/docs/soil-recipes.html";
        FarmDialogs dialogs = mock(FarmDialogs.class);
        KnowledgeService service = mock(KnowledgeService.class);
        AtomicReference<Component> dialogBody = new AtomicReference<>();
        when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    dialogBody.set(invocation.getArgument(1));
                    return JOptionPane.OK_OPTION;
                })
                .thenReturn(JOptionPane.CANCEL_OPTION);
        KnowledgeLogTab tab = new KnowledgeLogTab(service, new TestHost(), dialogs);
        tab.setWorkspace(FarmWorkspace.CANNABIS_FARM);
        onEdt(tab::getUIComponent);

        onEdt((Runnable) () -> tab.recordFieldScienceFinding(title, source));

        assertThat(field(dialogBody.get(), "What happened").getText())
                .isEqualTo("Finding from " + title);
        assertThat(field(dialogBody.get(), "Tags").getText())
                .contains("field-science");
        assertThat(area(dialogBody.get(), "What did you notice?").getText())
                .isBlank();
        JTextArea reference = descendants(dialogBody.get(), JTextArea.class).stream()
                .filter(candidate -> "knowledge.form.fieldScienceSource".equals(
                        candidate.getName()))
                .findFirst().orElseThrow();
        assertThat(reference.getText()).contains(title, source);
        assertThat(reference.isEditable()).isFalse();
        verify(dialogs).showMessage(any(), any(), anyString(), anyInt());
        verify(service, never()).create(any());
    }

    @Test
    void cancelingFieldScienceFindingDoesNotWriteToKnowledgeLog() throws Exception {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        KnowledgeService service = mock(KnowledgeService.class);
        when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.CANCEL_OPTION);
        KnowledgeLogTab tab = new KnowledgeLogTab(service, new TestHost(), dialogs);
        onEdt(tab::getUIComponent);

        onEdt((Runnable) () -> tab.recordFieldScienceFinding("Farm Scout",
                "https://github.com/RicheyWorks/CSRBT/blob/example/docs/farm-scout.html"));

        verify(dialogs).showConfirm(any(), any(), anyString(), anyInt(), anyInt());
        verify(service, never()).create(any());
    }

    @Test
    void editedFieldScienceFindingSavesObservationWithItsSource() throws Exception {
        String title = "Farm Scout";
        String source = "https://github.com/RicheyWorks/CSRBT/blob/example/docs/farm-scout.html";
        FarmDialogs dialogs = mock(FarmDialogs.class);
        KnowledgeService service = mock(KnowledgeService.class);
        CountDownLatch created = new CountDownLatch(1);
        AtomicReference<KnowledgeEntry> saved = new AtomicReference<>();
        when(service.create(any())).thenAnswer(invocation -> {
            KnowledgeEntry entry = invocation.getArgument(0);
            saved.set(entry);
            created.countDown();
            return entry;
        });
        when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    Component body = invocation.getArgument(1);
                    field(body, "What happened").setText("Aphid count rose in north field");
                    area(body, "What did you notice?").setText(
                            "Counted 12 aphids on five sampled plants.");
                    return JOptionPane.OK_OPTION;
                });
        KnowledgeLogTab tab = new KnowledgeLogTab(service, new TestHost(), dialogs);
        onEdt(tab::getUIComponent);

        onEdt((Runnable) () -> tab.recordFieldScienceFinding(title, source));

        assertThat(created.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(saved.get().getSummary()).isEqualTo("Aphid count rose in north field");
        assertThat(saved.get().getObservation())
                .contains("Counted 12 aphids on five sampled plants.", title, source);
        assertThat(saved.get().getTags()).contains("field-science");
    }

    @Test
    void readOnlySessionCannotOpenOrSaveFieldScienceFinding() throws Exception {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        KnowledgeService service = mock(KnowledgeService.class);
        KnowledgeLogTab tab = new KnowledgeLogTab(service, new TestHost(), dialogs);
        onEdt(tab::getUIComponent);
        onEdt((Runnable) () -> tab.applyRolePermissions(false));

        onEdt((Runnable) () -> tab.recordFieldScienceFinding("Farm Scout",
                "https://github.com/RicheyWorks/CSRBT/blob/example/docs/farm-scout.html"));

        verify(dialogs, never()).showConfirm(any(), any(), anyString(), anyInt(), anyInt());
        verify(service, never()).create(any());
    }

    @Test
    void linkedExportFindingRequiresAnOriginalObservation() throws Exception {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        KnowledgeService service = mock(KnowledgeService.class);
        AtomicReference<Component> dialogBody = new AtomicReference<>();
        when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    dialogBody.set(invocation.getArgument(1));
                    return JOptionPane.OK_OPTION;
                }).thenReturn(JOptionPane.CANCEL_OPTION);
        KnowledgeLogTab tab = new KnowledgeLogTab(service, new TestHost(), dialogs);
        onEdt(tab::getUIComponent);

        onEdt((Runnable) () -> tab.recordFieldScienceFindingFromExport(exportSummary()));

        JTextArea reference = descendants(dialogBody.get(), JTextArea.class).stream()
                .filter(candidate -> "knowledge.form.fieldScienceSource".equals(
                        candidate.getName())).findFirst().orElseThrow();
        assertThat(reference.getText()).contains("Saved export #42",
                "Reviewed scout text", "SHA-256", "Farm Scout");
        assertThat(reference.isEditable()).isFalse();
        assertThat(area(dialogBody.get(), "What did you notice?").getText()).isBlank();
        verify(dialogs).showMessage(any(), any(), anyString(), anyInt());
        verify(service, never()).create(any());
    }

    @Test
    void savingLinkedFindingCarriesExactExportIdWithoutCopyingToolOutput()
            throws Exception {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        KnowledgeService service = mock(KnowledgeService.class);
        CountDownLatch created = new CountDownLatch(1);
        AtomicReference<KnowledgeEntry> saved = new AtomicReference<>();
        when(service.create(any())).thenAnswer(invocation -> {
            KnowledgeEntry entry = invocation.getArgument(0);
            entry.setId(77L);
            saved.set(entry);
            created.countDown();
            return entry;
        });
        when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    Component body = invocation.getArgument(1);
                    field(body, "What happened").setText("Scouted north potato field");
                    area(body, "What did you notice?").setText(
                            "Counted eight aphids on four sampled plants.");
                    return JOptionPane.OK_OPTION;
                });
        KnowledgeLogTab tab = new KnowledgeLogTab(service, new TestHost(), dialogs);
        onEdt(tab::getUIComponent);

        onEdt((Runnable) () -> tab.recordFieldScienceFindingFromExport(exportSummary()));

        assertThat(created.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(saved.get().getFieldScienceExportId()).isEqualTo(42L);
        assertThat(saved.get().getSummary()).isEqualTo("Scouted north potato field");
        assertThat(saved.get().getObservation()).contains(
                "Counted eight aphids on four sampled plants.", "Saved export #42")
                .doesNotContain("automatically measured", "tool calculation");
        assertThat(saved.get().getTags()).contains("field-science");
    }

    @Test
    void viewerCanOpenExactLinkedExportFromSavedLesson() throws Exception {
        KnowledgeEntry saved = savedLinkedLesson();
        KnowledgeService service = mock(KnowledgeService.class);
        when(service.search(any())).thenReturn(List.of(saved));
        when(service.distinctTags()).thenReturn(List.of("field-science"));
        TestHost host = new TestHost();
        KnowledgeLogTab tab = new KnowledgeLogTab(service, host, mock(FarmDialogs.class));
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> tab.applyRolePermissions(false));
        onEdt((Runnable) tab::initialize);
        JButton open = descendants(root, JButton.class).stream()
                .filter(button -> "knowledge.openLinkedExport".equals(button.getName()))
                .findFirst().orElseThrow();
        await(open::isEnabled);

        assertThat(descendants(root, JTextArea.class).stream()
                .filter(area -> "knowledge.detail".equals(area.getName()))
                .findFirst().orElseThrow().getText())
                .contains("Field science export #42");
        onEdt((Runnable) open::doClick);
        assertThat(host.openedExportId).isEqualTo(42L);
    }

    private static FieldScienceExportSummary exportSummary() {
        return new FieldScienceExportSummary(42L,
                Instant.parse("2026-10-03T00:00:00Z"), "farm-scout", "Farm Scout",
                "https://github.com/RicheyWorks/CSRBT/blob/example/docs/farm-scout.html",
                "f4c9a25c287ac98b96efb38b99a9d95b281434de", "Reviewed scout text",
                "scout.txt", "Field review", 18, "a".repeat(64));
    }

    private static KnowledgeEntry savedLinkedLesson() {
        KnowledgeEntry entry = new KnowledgeEntry(LocalDate.now(),
                "Scouted north potato field", KnowledgeKind.PROBLEM,
                "Potato", "", "North field", "Growing", "Observed aphids.", "",
                "", "", null, KnowledgeConfidence.NEEDS_REVIEW,
                Set.of("field-science"));
        entry.setId(77L);
        entry.setFieldScienceExportId(42L);
        return entry;
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static JTextField field(Component root, String accessibleName) {
        return descendants(root, JTextField.class).stream()
                .filter(candidate -> accessibleName.equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea area(Component root, String accessibleName) {
        return descendants(root, JTextArea.class).stream()
                .filter(candidate -> accessibleName.equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static void assertPrompt(FarmWorkspace workspace, String expected) throws Exception {
        KnowledgeLogTab tab = new KnowledgeLogTab(
                mock(KnowledgeService.class), new TestHost());
        if (workspace != null) {
            tab.setWorkspace(workspace);
        }
        JComponent root = onEdt(tab::getUIComponent);
        JTextField search = descendants(root, JTextField.class).stream()
                .filter(field -> "knowledge.search".equals(field.getName()))
                .findFirst().orElseThrow();
        assertThat(search.getClientProperty("JTextField.placeholderText"))
                .isEqualTo(expected);
        if (workspace == FarmWorkspace.FLOWER_SHOP
                || workspace == FarmWorkspace.CANNABIS_FARM
                || workspace == FarmWorkspace.POTATO_FARM
                || workspace == FarmWorkspace.APPLE_ORCHARD
                || workspace == FarmWorkspace.BERRY_FARM
                || workspace == FarmWorkspace.VINEYARD_FARM
                || workspace == FarmWorkspace.HAY_FARM
                || workspace == FarmWorkspace.GRAIN_FARM
                || workspace == FarmWorkspace.SEED_FARM
                || workspace == FarmWorkspace.NURSERY_FARM
                || workspace == FarmWorkspace.CUSTOM_FARM) {
            JLabel subtitle = descendants(root, JLabel.class).stream()
                    .filter(label -> "farm.pageSubtitle".equals(label.getName()))
                    .findFirst().orElseThrow();
            assertThat(subtitle.getText()).contains("practical notes").doesNotContain("ground");
        }
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(descendants(child, type));
            }
        }
        return found;
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    private static void onEdt(Runnable action) throws Exception {
        onEdt(() -> {
            action.run();
            return null;
        });
    }

    private static final class TestHost implements TabHost {
        private Long openedExportId;

        @Override public void refreshAll() {
        }

        @Override public void selectTab(String tabTitle) {
        }

        @Override public void setStatus(String message) {
        }

        @Override public void runTrendAnalysis() {
        }

        @Override public void showFieldScienceExport(long exportId) {
            openedExportId = exportId;
        }
    }
}
