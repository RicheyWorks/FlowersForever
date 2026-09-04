package com.flowerfarm.gui.harness;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingUserHarnessFailureTest {

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
