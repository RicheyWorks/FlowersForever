package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.FarmWorkspace;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotebookSavedSnapshotSafetyTest {

    static Stream<Class<? extends FlowerFarmTab>> notebooks() {
        return NotebookDraftDecisionSafetyTest.notebooks();
    }

    static Stream<Class<? extends FlowerFarmTab>> stagedNotebooks() {
        return notebooks().filter(type -> type != BerryBlockPlanTab.class
                && type != EggCollectionTab.class && type != MilkCollectionTab.class
                && type != HerdGroupTab.class);
    }

    @ParameterizedTest(name = "{0}: discard restores an acknowledged edit after readback fails")
    @MethodSource("notebooks")
    void discardUsesLatestSavedEditWhenReadbackFails(Class<? extends FlowerFarmTab> type)
            throws Exception {
        Fixture fixture = onEdt(() -> new Fixture(type));
        onEdt(() -> {
            fixture.showRows(List.of(fixture.committed.get()));
            fixture.table().setRowSelectionInterval(0, 0);
            fixture.notes().setText("Just saved");
            fixture.save().doClick();
        });
        fixture.awaitSettled(1);
        String failedRefresh = onEdt(fixture::feedback);
        onEdt(fixture::renderRecoveryIfRelevant);
        onEdt(() -> {
            fixture.notes().setText("Temporary later edit");
            fixture.discard().doClick();
            assertThat(fixture.notes().getText()).isEqualTo("Just saved");
            assertThat(fixture.table().getSelectedRow()).isZero();
            assertThat(fixture.feedback().toLowerCase(Locale.ROOT)).contains("completed save", "refresh");
            assertThat(fixture.feedback()).contains("Reviewing")
                    .containsOnlyOnce("Choose Refresh to retry.");
        });
        assertThat(failedRefresh.toLowerCase(Locale.ROOT)).contains("completed save", "refresh");
        assertThat(failedRefresh).contains("Your completed save is kept. The list could not refresh.")
                .doesNotContain("Could not load")
                .containsOnlyOnce("Choose Refresh to retry.");
        assertThat(fixture.creates.get()).isZero();
        assertThat(fixture.updates.get()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}: a new saved row survives failed refresh and retry")
    @MethodSource("notebooks")
    void newSavedRecordKeepsIdentityAndDoesNotCreateTwice(Class<? extends FlowerFarmTab> type)
            throws Exception {
        Fixture fixture = onEdt(() -> new Fixture(type));
        onEdt(() -> {
            Object template = fixture.record(null, "New");
            fixture.loadRecord(template);
            fixture.notes().setText("Just created");
            fixture.save().doClick();
        });
        fixture.awaitSettled(1);
        onEdt(() -> {
            fixture.notes().setText("Temporary later edit");
            fixture.discard().doClick();
            assertThat(fixture.notes().getText()).isEqualTo("Just created");
            assertThat(ReflectionTestUtils.getField(fixture.tab, "editingId")).isEqualTo(11L);
            assertThat(fixture.table().getRowCount()).isEqualTo(1);
            fixture.readFails = false;
            fixture.tab.refreshData();
        });
        fixture.awaitSettled(1);
        onEdt(() -> {
            assertThat(fixture.table().getRowCount()).isEqualTo(1);
            fixture.notes().setText("Saved as an edit");
            fixture.save().doClick();
        });
        fixture.awaitSettled(2);
        assertThat(fixture.creates.get()).isEqualTo(1);
        assertThat(fixture.updates.get()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}: discard and another move retain the acknowledged stage")
    @MethodSource("stagedNotebooks")
    void savedStageRemainsCurrentWhenReadbackFails(Class<? extends FlowerFarmTab> type)
            throws Exception {
        Fixture fixture = onEdt(() -> new Fixture(type));
        AtomicReference<Object> movedStage = new AtomicReference<>();
        onEdt(() -> {
            fixture.showRows(List.of(fixture.committed.get()));
            fixture.table().setRowSelectionInterval(0, 0);
            JComboBox<?> stages = fixture.stageField();
            movedStage.set(stages.getItemAt(1));
            fixture.moveTo(movedStage.get());
        });
        fixture.awaitSettled(1);
        onEdt(() -> {
            fixture.notes().setText("Temporary after move");
            fixture.discard().doClick();
            assertThat(fixture.stageField().getSelectedItem()).isEqualTo(movedStage.get());
            // Moving back is possible even while the full-list read remains unavailable.
            fixture.moveTo(fixture.stageField().getItemAt(0));
        });
        fixture.awaitSettled(2);
        assertThat(fixture.moves.get()).isEqualTo(2);
        assertThat(fixture.creates.get()).isZero();
    }

    @ParameterizedTest(name = "{0}: ordinary save, edit and discard remain usable")
    @MethodSource("notebooks")
    void ordinarySaveAndDiscardStillWork(Class<? extends FlowerFarmTab> type) throws Exception {
        Fixture fixture = onEdt(() -> new Fixture(type));
        fixture.readFails = false;
        onEdt(() -> {
            fixture.showRows(List.of(fixture.committed.get()));
            fixture.table().setRowSelectionInterval(0, 0);
            fixture.notes().setText("Ordinary saved note");
            fixture.save().doClick();
        });
        fixture.awaitSettled(1);
        onEdt(() -> {
            fixture.notes().setText("Discard this");
            fixture.discard().doClick();
            assertThat(fixture.notes().getText()).isEqualTo("Ordinary saved note");
        });
        assertThat(fixture.updates.get()).isEqualTo(1);
    }

    @Test
    void hostStatusFailureAfterCommitDoesNotReportWriteFailureOrDuplicateCreate() throws Exception {
        Fixture fixture = onEdt(() -> new Fixture(EggCollectionTab.class));
        fixture.hostFails = true;
        onEdt(() -> {
            fixture.loadRecord(fixture.record(null, "New"));
            fixture.notes().setText("Acknowledged despite UI error");
            fixture.save().doClick();
        });
        fixture.awaitSettled(1);
        onEdt(() -> {
            assertThat(fixture.feedback().toLowerCase(Locale.ROOT)).contains("completed save")
                    .doesNotContain("not saved");
            assertThat(ReflectionTestUtils.getField(fixture.tab, "editingId")).isEqualTo(11L);
            JTextField date = (JTextField) ReflectionTestUtils.getField(fixture.tab, "dateField");
            date.setText("later");
            fixture.save().doClick();
            assertThat(fixture.feedback()).contains("YYYY-MM-DD", "Your completed save is kept.")
                    .containsOnlyOnce("Choose Refresh to retry.");
            date.setText("2026-10-07");
            fixture.hostFails = false;
            fixture.notes().setText("Next edit");
            fixture.save().doClick();
        });
        fixture.awaitSettled(2);
        assertThat(fixture.creates.get()).isEqualTo(1);
        assertThat(fixture.updates.get()).isEqualTo(1);
    }

    private static final class Fixture {
        private final FlowerFarmTab tab;
        private final AtomicReference<Object> committed = new AtomicReference<>();
        private final AtomicInteger creates = new AtomicInteger();
        private final AtomicInteger updates = new AtomicInteger();
        private final AtomicInteger moves = new AtomicInteger();
        private volatile boolean readFails = true;
        private boolean hostFails;

        private Fixture(Class<? extends FlowerFarmTab> type) throws Exception {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
            FarmDialogs dialogs = mock(FarmDialogs.class);
            when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                    any(), any(), any())).thenReturn(1);
            Constructor<?> constructor = Arrays.stream(type.getConstructors())
                    .filter(candidate -> Arrays.asList(candidate.getParameterTypes())
                            .contains(FarmDialogs.class)).findFirst().orElseThrow();
            Answer<Object> serviceAnswer = invocation -> {
                String method = invocation.getMethod().getName();
                if (method.startsWith("list")) {
                    if (readFails) throw new IllegalStateException("Readback unavailable");
                    return List.of(snapshot(committed.get()));
                }
                if (method.equals("create") || method.equals("update")) {
                    Object saved = invocation.getArguments()[invocation.getArguments().length - 1];
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    committed.set(snapshot(saved));
                    if (method.equals("create")) creates.incrementAndGet();
                    else updates.incrementAndGet();
                    return snapshot(saved);
                }
                if (method.equals("updateStage") || method.equals("updateStatus")) {
                    Object saved = snapshot(committed.get());
                    ReflectionTestUtils.setField(saved, method.equals("updateStatus") ? "status" : "stage",
                            invocation.getArguments()[invocation.getArguments().length - 1]);
                    committed.set(snapshot(saved));
                    moves.incrementAndGet();
                    return snapshot(saved);
                }
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            };
            Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                    .map(parameter -> parameter == FarmDialogs.class ? dialogs
                            : parameter == FarmWorkspace.class ? FarmWorkspace.POTATO_FARM
                            : parameter == TabHost.class ? mock(parameter, invocation -> {
                                if (hostFails && invocation.getMethod().getName().equals("setStatus")) {
                                    throw new IllegalStateException("Status display unavailable");
                                }
                                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                            })
                            : mock(parameter, serviceAnswer)).toArray();
            tab = (FlowerFarmTab) constructor.newInstance(arguments);
            tab.getUIComponent();
            committed.set(record(11L, "Original"));
        }

        private void awaitSettled(int writes) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                if (onEdt(() -> creates.get() + updates.get() + moves.get() >= writes
                        && !Boolean.TRUE.equals(ReflectionTestUtils.getField(tab, "saving"))
                        && !Boolean.TRUE.equals(ReflectionTestUtils.getField(tab, "loading")))) return;
                Thread.sleep(15);
            }
            throw new AssertionError(onEdt(this::feedback));
        }

        private JTextArea notes() {
            return (JTextArea) ReflectionTestUtils.getField(tab,
                    tab instanceof HiveCheckTab ? "observationsField" : "notesField");
        }
        private JButton save() { return (JButton) ReflectionTestUtils.getField(tab, "saveButton"); }
        private JButton discard() { return (JButton) ReflectionTestUtils.getField(tab, "discardButton"); }
        private JTable table() { return (JTable) ReflectionTestUtils.getField(tab, "table"); }
        private JComboBox<?> stageField() {
            return (JComboBox<?>) ReflectionTestUtils.getField(tab,
                    tab instanceof HiveCheckTab ? "statusField" : "stageField");
        }
        private String feedback() {
            return ((JLabel) ReflectionTestUtils.getField(tab, "feedback")).getText();
        }
        private void renderRecoveryIfRelevant() throws Exception {
            if (!(tab instanceof EggCollectionTab || tab instanceof VegetablePlantingTab)) return;
            JComponent root = tab.getUIComponent();
            root.setSize(850, 600);
            for (int pass = 0; pass < 6; pass++) layoutTree(root);
            FocusEvent focus = new FocusEvent(save(), FocusEvent.FOCUS_GAINED);
            for (FocusListener listener : save().getFocusListeners()) listener.focusGained(focus);
            JLabel feedback = (JLabel) ReflectionTestUtils.getField(tab, "feedback");
            JScrollPane scroll = Arrays.stream(root.getComponents())
                    .filter(JScrollPane.class::isInstance).map(JScrollPane.class::cast)
                    .findFirst().orElseThrow();
            Rectangle bounds = SwingUtilities.convertRectangle(feedback.getParent(),
                    feedback.getBounds(), scroll.getViewport().getView());
            Path output = Path.of("target", "ui-previews", "notebook-recovery",
                    tab.getClass().getSimpleName() + ".png");
            Files.createDirectories(output.getParent());
            BufferedImage image = new BufferedImage(850, 600, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try { root.printAll(graphics); }
            finally { graphics.dispose(); }
            ImageIO.write(image, "png", output.toFile());
            assertThat(feedback.getHeight()).as("Full wrapping recovery message")
                    .isGreaterThanOrEqualTo(feedback.getPreferredSize().height);
            assertThat(scroll.getViewport().getViewRect().contains(bounds))
                    .as("Recovery guidance visible beside the focused Save action").isTrue();
        }
        private void showRows(List<?> rows) throws Exception {
            invokeNamed("show", List.class, rows);
        }
        private void loadRecord(Object row) throws Exception {
            invokeNamed("load", row.getClass(), row);
        }
        private void invokeNamed(String prefix, Class<?> parameter, Object value) throws Exception {
            Method method = Arrays.stream(tab.getClass().getDeclaredMethods())
                    .filter(candidate -> candidate.getName().startsWith(prefix)
                            && Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{parameter}))
                    .findFirst().orElseThrow();
            method.setAccessible(true);
            method.invoke(tab, value);
        }
        private void moveTo(Object stage) throws Exception {
            Method method = Arrays.stream(tab.getClass().getDeclaredMethods())
                    .filter(candidate -> candidate.getName().startsWith("move")
                            && candidate.getParameterCount() == 2
                            && candidate.getParameterTypes()[0] == Long.class
                            && candidate.getParameterTypes()[1] == stage.getClass())
                    .findFirst().orElseThrow();
            method.setAccessible(true);
            method.invoke(tab, 11L, stage);
        }
        private static Object snapshot(Object value) throws Exception {
            return value.getClass().getMethod("snapshot").invoke(value);
        }
        private Object record(Long id, String label) throws Exception {
            ParameterizedType rows = (ParameterizedType) tab.getClass()
                    .getDeclaredField("lastView").getGenericType();
            Class<?> model = (Class<?>) rows.getActualTypeArguments()[0];
            Constructor<?> constructor = model.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object value = constructor.newInstance();
            for (Field field : model.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                Class<?> fieldType = field.getType();
                Object data;
                if (field.getName().equals("id")) data = id;
                else if (fieldType == String.class) data = label + " " + field.getName();
                else if (fieldType == BigDecimal.class) data = BigDecimal.TEN;
                else if (fieldType == int.class || fieldType == Integer.class) {
                    data = field.getName().toLowerCase(Locale.ROOT).contains("year") ? 2026 : 10;
                } else if (fieldType == LocalDate.class) data = LocalDate.of(2026, 10, 7);
                else if (fieldType == Instant.class) data = Instant.parse("2026-10-07T12:00:00Z");
                else if (fieldType == FarmWorkspace.class) data = FarmWorkspace.POTATO_FARM;
                else if (fieldType.isEnum()) data = fieldType.getEnumConstants()[0];
                else throw new IllegalArgumentException("Unsupported fixture field " + field);
                field.setAccessible(true);
                field.set(value, data);
            }
            return value;
        }
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) layoutTree(container);
        }
    }

    private static <T> T onEdt(Callable<T> operation) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(operation.call()); }
            catch (Throwable exception) { failure.set(exception); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private static void onEdt(CheckedRunnable operation) throws Exception {
        onEdt(() -> { operation.run(); return null; });
    }

    @FunctionalInterface
    private interface CheckedRunnable { void run() throws Exception; }
}
