package com.flowerfarm.gui.harness;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingUserHarnessFailureTest {

    @Test
    void spinnerEntryPreservesNumericBoundsAndTypeWithoutFiringInvalidChanges() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 1, 30, 1));
        AtomicInteger changes = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            spinner.getAccessibleContext().setAccessibleName("Packing window in days");
            spinner.addChangeListener(event -> changes.incrementAndGet());
        });
        SwingUserHarness user = SwingUserHarness.on(spinner);

        user.setSpinner("Packing window in days", 1)
                .setSpinner("Packing window in days", 30)
                .setSpinner("Packing window in days", 15L);
        assertThat(changes).hasValue(3);
        SwingUtilities.invokeAndWait(() -> assertThat(spinner.getValue())
                .isInstanceOf(Integer.class).isEqualTo(15));

        for (Object invalid : List.of(0, 31, 2.5, "15", 2147483648L, Double.NaN)) {
            assertThatThrownBy(() -> user.setSpinner("Packing window in days", invalid))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("Packing window in days");
            assertThat(changes).hasValue(3);
            SwingUtilities.invokeAndWait(() -> {
                assertThat(spinner.getValue()).isInstanceOf(Integer.class).isEqualTo(15);
                assertThat(((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText())
                        .isEqualTo("15");
            });
        }
        assertThat(user.transcript()).hasSize(3);

        user.setSpinner("Packing window in days", 2);
        assertThat(changes).hasValue(4);
        SwingUtilities.invokeAndWait(() -> assertThat(spinner.getValue()).isEqualTo(2));
    }

    @Test
    void spinnerEntryRequiresAnExistingListOption() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerListModel(List.of("Seed", "Grow")));
        AtomicInteger changes = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            spinner.getAccessibleContext().setAccessibleName("Production stage");
            spinner.addChangeListener(event -> changes.incrementAndGet());
        });
        SwingUserHarness user = SwingUserHarness.on(spinner);

        user.setSpinner("Production stage", "Grow");
        assertThatThrownBy(() -> user.setSpinner("Production stage", "Harvest"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Production stage");
        assertThat(changes).hasValue(1);
        SwingUtilities.invokeAndWait(() -> assertThat(spinner.getValue()).isEqualTo("Grow"));
        user.setSpinner("Production stage", "Seed");
        assertThat(changes).hasValue(2);
    }

    @Test
    void spinnerEntryRespectsDateBoundsWithoutChangingTheModelOnRejection() throws Exception {
        Date minimum = new Date(1_000L);
        Date maximum = new Date(3_000L);
        JSpinner spinner = new JSpinner(new SpinnerDateModel(
                new Date(2_000L), minimum, maximum, Calendar.SECOND));
        AtomicInteger changes = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            spinner.getAccessibleContext().setAccessibleName("Harvest date");
            spinner.addChangeListener(event -> changes.incrementAndGet());
        });
        SwingUserHarness user = SwingUserHarness.on(spinner);

        user.setSpinner("Harvest date", minimum).setSpinner("Harvest date", maximum);
        for (Object invalid : List.of(new Date(0L), new Date(4_000L), "tomorrow")) {
            assertThatThrownBy(() -> user.setSpinner("Harvest date", invalid))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("Harvest date");
            assertThat(changes).hasValue(2);
            SwingUtilities.invokeAndWait(() -> assertThat(spinner.getValue()).isEqualTo(maximum));
        }
        user.setSpinner("Harvest date", minimum);
        assertThat(changes).hasValue(3);
    }

    @Test
    void usesOnlyTheActiveTabsControlsWhenSelectorsAreRepeated() throws Exception {
        JTabbedPane tabs = new JTabbedPane();
        JButton visibleSave = new JButton("Save");
        JButton hiddenSave = new JButton("Save");
        JTextField visibleName = new JTextField("visible original");
        JTextField hiddenName = new JTextField("hidden original");
        JCheckBox visibleEnabled = new JCheckBox("Enabled");
        JCheckBox hiddenEnabled = new JCheckBox("Enabled");
        AtomicInteger visibleActivations = new AtomicInteger();
        AtomicInteger hiddenActivations = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            JPanel visible = new JPanel();
            JPanel hidden = new JPanel();
            visibleName.getAccessibleContext().setAccessibleName("Name");
            hiddenName.getAccessibleContext().setAccessibleName("Name");
            visible.add(visibleSave);
            visible.add(visibleName);
            visible.add(visibleEnabled);
            hidden.add(hiddenSave);
            hidden.add(hiddenName);
            hidden.add(hiddenEnabled);
            tabs.addTab("Current", visible);
            tabs.addTab("Other", hidden);
            visibleSave.addActionListener(event -> visibleActivations.incrementAndGet());
            hiddenSave.addActionListener(event -> hiddenActivations.incrementAndGet());
        });

        SwingUserHarness.on(tabs).click("Save").type("Name", "Dahlia")
                .setChecked("Enabled", true);

        SwingUtilities.invokeAndWait(() -> {
            assertThat(visibleName.getText()).isEqualTo("Dahlia");
            assertThat(visibleEnabled.isSelected()).isTrue();
            assertThat(hiddenName.getText()).isEqualTo("hidden original");
            assertThat(hiddenEnabled.isSelected()).isFalse();
        });
        assertThat(visibleActivations).hasValue(1);
        assertThat(hiddenActivations).hasValue(0);
    }

    @Test
    void rejectsControlsWhoseAncestorIsHidden() throws Exception {
        JPanel root = new JPanel();
        JPanel hidden = new JPanel();
        JButton save = new JButton("Hidden save");
        JTextField name = new JTextField("original");
        JCheckBox enabled = new JCheckBox("Hidden enabled");
        JTable table = new JTable(new Object[][]{{"Dahlia"}}, new Object[]{"Crop"});
        AtomicInteger activations = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            name.getAccessibleContext().setAccessibleName("Hidden name");
            table.setName("hidden.table");
            hidden.add(save);
            hidden.add(name);
            hidden.add(enabled);
            hidden.add(table);
            root.add(hidden);
            hidden.setVisible(false);
            save.addActionListener(event -> activations.incrementAndGet());
        });
        SwingUserHarness user = SwingUserHarness.on(root);

        assertThatThrownBy(() -> user.click("Hidden save"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("found 0");
        assertThatThrownBy(() -> user.type("Hidden name", "changed"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("found 0");
        assertThatThrownBy(() -> user.setChecked("Hidden enabled", true))
                .isInstanceOf(AssertionError.class).hasMessageContaining("found 0");
        assertThatThrownBy(() -> user.selectRow("hidden.table", 0))
                .isInstanceOf(AssertionError.class).hasMessageContaining("hidden");
        SwingUtilities.invokeAndWait(() -> {
            assertThat(name.getText()).isEqualTo("original");
            assertThat(enabled.isSelected()).isFalse();
            assertThat(table.getSelectedRows()).isEmpty();
        });
        assertThat(activations).hasValue(0);
    }

    @Test
    void waitsForTextToBecomeVisibleThroughTheSelectedTab() throws Exception {
        JTabbedPane tabs = new JTabbedPane();
        SwingUtilities.invokeAndWait(() -> {
            JPanel current = new JPanel();
            current.add(new JLabel("Ready to save"));
            JPanel other = new JPanel();
            other.add(new JLabel("Saved successfully"));
            tabs.addTab("Current", current);
            tabs.addTab("Other", other);
        });
        SwingUserHarness user = SwingUserHarness.on(tabs);

        assertThatThrownBy(() -> user.awaitText("Saved successfully", Duration.ofMillis(60)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Timed out");
        assertThat(user.transcript()).doesNotContain("AWAIT TEXT Saved successfully");
        user.selectTab("Other").awaitText("Saved successfully", Duration.ofMillis(60));
        assertThat(user.transcript()).contains("AWAIT TEXT Saved successfully");
    }

    @Test
    void selectsNestedTabsOnlyOnTheActiveOuterTab() throws Exception {
        JTabbedPane tabs = new JTabbedPane();
        JTabbedPane visibleNested = new JTabbedPane();
        JTabbedPane hiddenNested = new JTabbedPane();
        SwingUtilities.invokeAndWait(() -> {
            visibleNested.addTab("Overview", new JPanel());
            visibleNested.addTab("Details", new JPanel());
            hiddenNested.addTab("Overview", new JPanel());
            hiddenNested.addTab("Details", new JPanel());
            JPanel visible = new JPanel();
            visible.add(visibleNested);
            JPanel hidden = new JPanel();
            hidden.add(hiddenNested);
            tabs.addTab("Current", visible);
            tabs.addTab("Other", hidden);
        });

        SwingUserHarness.on(tabs).selectTab("Details");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(visibleNested.getSelectedIndex()).isEqualTo(1);
            assertThat(hiddenNested.getSelectedIndex()).isEqualTo(0);
        });
    }

    @Test
    void rejectsDisabledTabsAndDisabledTabSetsWithoutNavigating() throws Exception {
        JTabbedPane tabs = new JTabbedPane();
        AtomicInteger navigations = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            tabs.addTab("Overview", new JPanel());
            tabs.addTab("Details", new JPanel());
            tabs.setEnabledAt(1, false);
            tabs.addChangeListener(event -> navigations.incrementAndGet());
        });
        SwingUserHarness user = SwingUserHarness.on(tabs);

        assertThatThrownBy(() -> user.selectTab("Details"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("tab is disabled");
        assertThat(navigations).hasValue(0);
        SwingUtilities.invokeAndWait(() -> {
            assertThat(tabs.getSelectedIndex()).isEqualTo(0);
            tabs.setEnabledAt(1, true);
        });
        user.selectTab("Details");
        assertThat(navigations).hasValue(1);

        SwingUtilities.invokeAndWait(() -> tabs.setEnabled(false));
        assertThatThrownBy(() -> user.selectTab("Overview"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("tab set is disabled");
        assertThat(navigations).hasValue(1);
        SwingUtilities.invokeAndWait(() -> {
            assertThat(tabs.getSelectedIndex()).isEqualTo(1);
            tabs.setEnabled(true);
        });
        user.selectTab("Overview");
        assertThat(navigations).hasValue(2);
        SwingUtilities.invokeAndWait(() -> assertThat(tabs.getSelectedIndex()).isEqualTo(0));
    }

    @Test
    void rejectsUnsupportedMultiSelectionWithoutClearingExistingEvidence() throws Exception {
        JTable table = new JTable(new Object[][]{{"A"}, {"B"}, {"C"}}, new Object[]{"Crop"});
        SwingUtilities.invokeAndWait(() -> {
            table.setName("harvest");
            table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            table.setRowSelectionInterval(1, 1);
        });
        SwingUserHarness user = SwingUserHarness.on(table);
        assertThatThrownBy(() -> user.selectRows("harvest", 0, 2))
                .isInstanceOf(AssertionError.class).hasMessageContaining("contiguous");
        SwingUtilities.invokeAndWait(() ->
                assertThat(table.getSelectedRows()).containsExactly(1));
        assertThatThrownBy(() -> user.selectRows("harvest", 0, 7))
                .isInstanceOf(AssertionError.class).hasMessageContaining("outside table");
        SwingUtilities.invokeAndWait(() ->
                assertThat(table.getSelectedRows()).containsExactly(1));
    }

    @Test
    void menuSweepsRespectDisabledParentsAndCountOnlyTheirOwnClicks() throws Exception {
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Farm");
        JMenuItem command = new JMenuItem("Refresh");
        AtomicInteger activations = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            menuBar.add(menu);
            menu.add(command);
            command.addActionListener(event -> activations.incrementAndGet());
        });
        SwingMenuHarness user = SwingMenuHarness.on(menuBar);
        user.click("Refresh");
        assertThat(user.clickEveryEnabledExcept().clicked()).containsExactly("Refresh");
        assertThat(activations).hasValue(2);
        SwingUtilities.invokeAndWait(() -> menu.setEnabled(false));
        assertThatThrownBy(() -> user.click("Refresh"))
                .isInstanceOf(AssertionError.class);
        assertThat(user.clickEveryEnabledExcept().discovered()).isEmpty();
        assertThat(activations).hasValue(2);
    }

    @Test
    void failsTheSweepWhenTheSurfaceNeverSettles() throws Exception {
        JPanel root = new JPanel();
        JButton changing = new JButton("Start updating");
        AtomicInteger revisions = new AtomicInteger();
        Timer timer = new Timer(5,
                event -> changing.setText("Updating " + revisions.incrementAndGet()));
        SwingUtilities.invokeAndWait(() -> {
            root.add(changing);
            changing.addActionListener(event -> timer.start());
        });
        SwingUserHarness user = SwingUserHarness.on(root);
        try {
            assertThatThrownBy(user::clickEveryEnabledButton)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("did not settle");
            assertThat(user.transcript()).anyMatch(line -> line.contains("SETTLE TIMEOUT"));
            assertThat(user.transcript()).noneMatch(line -> line.startsWith("BUTTON STORM COMPLETE"));
        } finally {
            SwingUtilities.invokeAndWait(timer::stop);
        }
    }
}
