package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.model.FarmWorkspace;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises decisions while Swing's modal event loop can deliver other work. */
class NotebookDraftDecisionSafetyTest {

    static Stream<Class<? extends FlowerFarmTab>> notebooks() {
        return Stream.of(AquaticCohortTab.class, BerryBlockPlanTab.class,
                CannabisCultivationTab.class, ChristmasTreeBlockTab.class,
                CranberryBedTab.class, CropAreaPlanTab.class, EggCollectionTab.class,
                GrainFieldPlanTab.class, HayCuttingTab.class, HerdGroupTab.class,
                HiveCheckTab.class, HopYardPlanTab.class, MilkCollectionTab.class,
                MushroomBatchTab.class, NurseryGrowingBatchTab.class, NutBlockPlanTab.class,
                PumpkinPatchTab.class, SeedFieldPlanTab.class, VegetablePlantingTab.class,
                VineyardBlockPlanTab.class);
    }

    @ParameterizedTest(name = "{0}: newer draft is not discarded by an older answer")
    @MethodSource("notebooks")
    void changedDraftDuringDiscardIsKept(Class<? extends FlowerFarmTab> type) throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture(type);
            fixture.notes().setText("Reviewed draft");
            fixture.answer(() -> fixture.notes().setText("Newer unreviewed draft"), 1);
            fixture.discard().doClick();
            assertThat(fixture.notes().getText()).isEqualTo("Newer unreviewed draft");
            assertThat(fixture.feedback()).contains("kept");
        });
    }

    @ParameterizedTest(name = "{0}: refresh cannot load an obsolete selected record")
    @MethodSource("notebooks")
    void refreshedRowsInvalidateSelectionApproval(Class<? extends FlowerFarmTab> type)
            throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture(type);
            Object first = fixture.record(11L, "First");
            Object second = fixture.record(12L, "Old second");
            Object updatedSecond = fixture.record(12L, "New second");
            fixture.showRows(List.of(first, second));
            fixture.table().setRowSelectionInterval(0, 0);
            fixture.notes().setText("Unsaved first draft");
            fixture.answer(() -> fixture.showRows(List.of(first, updatedSecond)), 1);
            fixture.table().setRowSelectionInterval(1, 1);
            assertThat(fixture.notes().getText()).isEqualTo("Unsaved first draft");
            assertThat(ReflectionTestUtils.getField(fixture.tab, "editingId"))
                    .isEqualTo(11L);
            assertThat(fixture.table().getSelectedRow()).isZero();
            assertThat(fixture.feedback()).contains("kept");

            // A fresh decision can then open the current, updated record.
            fixture.answer(() -> { }, 1);
            fixture.table().setRowSelectionInterval(1, 1);
            assertThat(fixture.notes().getText()).isEqualTo("New second "
                    + (type == HiveCheckTab.class ? "observations" : "notes"));
            assertThat(ReflectionTestUtils.getField(fixture.tab, "editingId"))
                    .isEqualTo(12L);
        });
    }

    @ParameterizedTest(name = "{0}: unchanged discard and cancellation still work")
    @MethodSource("notebooks")
    void ordinaryDiscardAndKeepStillWork(Class<? extends FlowerFarmTab> type) throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture(type);
            fixture.notes().setText("Keep this draft");
            fixture.answer(() -> { }, 0);
            fixture.discard().doClick();
            assertThat(fixture.notes().getText()).isEqualTo("Keep this draft");
            fixture.answer(() -> { }, 1);
            fixture.discard().doClick();
            assertThat(fixture.notes().getText()).isEmpty();
        });
    }

    @ParameterizedTest(name = "{0}: changed notebook state keeps the reviewed draft")
    @MethodSource("notebooks")
    void changedNotebookStateInvalidatesDiscard(Class<? extends FlowerFarmTab> type)
            throws Exception {
        onEdt(() -> {
            for (String change : List.of("permission", "loading", "saving", "target", "selection")) {
                Fixture fixture = new Fixture(type);
                fixture.showRows(List.of(fixture.record(11L, "First")));
                fixture.table().setRowSelectionInterval(0, 0);
                fixture.notes().setText("Keep after " + change);
                fixture.answer(() -> {
                    switch (change) {
                        case "permission" -> fixture.tab.applyRolePermissions(false);
                        case "loading" -> ReflectionTestUtils.setField(fixture.tab, "loading", true);
                        case "saving" -> ReflectionTestUtils.setField(fixture.tab, "saving", true);
                        case "target" -> ReflectionTestUtils.setField(fixture.tab, "editingId", 99L);
                        case "selection" -> fixture.table().clearSelection();
                        default -> throw new AssertionError(change);
                    }
                }, 1);
                fixture.discard().doClick();
                assertThat(fixture.notes().getText()).as(change).isEqualTo("Keep after " + change);
                assertThat(fixture.feedback()).as(change).contains("kept");
            }
        });
    }

    @ParameterizedTest(name = "{0}: clean records still open without a discard question")
    @MethodSource("notebooks")
    void cleanRecordNavigationStillWorks(Class<? extends FlowerFarmTab> type) throws Exception {
        onEdt(() -> {
            Fixture fixture = new Fixture(type);
            fixture.showRows(List.of(fixture.record(11L, "First"), fixture.record(12L, "Second")));
            fixture.table().setRowSelectionInterval(0, 0);
            fixture.tab.applyRolePermissions(false);
            fixture.table().setRowSelectionInterval(1, 1);
            assertThat(fixture.notes().getText()).isEqualTo("Second "
                    + (type == HiveCheckTab.class ? "observations" : "notes"));
            assertThat(ReflectionTestUtils.getField(fixture.tab, "editingId")).isEqualTo(12L);
            verify(fixture.dialogs, never()).showOption(any(), any(), anyString(), anyInt(),
                    anyInt(), any(), any(), any());
        });
    }

    private static final class Fixture {
        private final FlowerFarmTab tab;
        private final FarmDialogs dialogs = mock(FarmDialogs.class);

        private Fixture(Class<? extends FlowerFarmTab> type) throws Exception {
            Constructor<?> constructor = Arrays.stream(type.getConstructors())
                    .filter(candidate -> Arrays.asList(candidate.getParameterTypes())
                            .contains(FarmDialogs.class)).findFirst().orElseThrow();
            Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                    .map(parameter -> parameter == FarmDialogs.class ? dialogs
                            : parameter == FarmWorkspace.class ? FarmWorkspace.POTATO_FARM
                            : mock(parameter)).toArray();
            tab = (FlowerFarmTab) constructor.newInstance(arguments);
            tab.getUIComponent();
        }

        private JTextArea notes() {
            return (JTextArea) ReflectionTestUtils.getField(tab,
                    tab instanceof HiveCheckTab ? "observationsField" : "notesField");
        }

        private JButton discard() {
            return (JButton) ReflectionTestUtils.getField(tab, "discardButton");
        }

        private JTable table() {
            return (JTable) ReflectionTestUtils.getField(tab, "table");
        }

        private String feedback() {
            return ((JLabel) ReflectionTestUtils.getField(tab, "feedback")).getText();
        }

        private void answer(CheckedRunnable duringDialog, int choice) {
            when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                    any(), any(), any())).thenAnswer(invocation -> {
                        duringDialog.run();
                        return choice;
                    });
        }

        private void showRows(List<?> rows) throws Exception {
            Method method = Arrays.stream(tab.getClass().getDeclaredMethods())
                    .filter(candidate -> candidate.getName().startsWith("show")
                            && Arrays.equals(candidate.getParameterTypes(),
                            new Class<?>[]{List.class})).findFirst().orElseThrow();
            method.setAccessible(true);
            method.invoke(tab, rows);
        }

        /** Saved row fixtures exercise each notebook's real render/load methods. */
        private Object record(Long id, String label) throws Exception {
            ParameterizedType rows = (ParameterizedType) tab.getClass()
                    .getDeclaredField("lastView").getGenericType();
            Class<?> model = (Class<?>) rows.getActualTypeArguments()[0];
            Constructor<?> constructor = model.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object value = constructor.newInstance();
            for (Field field : model.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                Object data;
                if (field.getName().equals("id")) data = id;
                else if (fieldType == String.class) data = label + " " + field.getName();
                else if (fieldType == BigDecimal.class) data = BigDecimal.TEN;
                else if (fieldType == int.class || fieldType == Integer.class) data = 10;
                else if (fieldType == LocalDate.class) data = LocalDate.of(2026, 10, 7);
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

    private static void onEdt(CheckedRunnable operation) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                operation.run();
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
