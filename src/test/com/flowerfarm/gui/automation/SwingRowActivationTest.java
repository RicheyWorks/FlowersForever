package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SortOrder;
import javax.swing.RowSorter;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SwingRowActivationTest {
    @Test
    void refusesRedirectedSelectionBeforeOpeningARecordAndPreservesFailedReplay() throws Exception {
        AtomicBoolean redirect = new AtomicBoolean(true);
        Fixture fixture = fixture();
        SwingUtilities.invokeAndWait(() -> fixture.table().getSelectionModel().addListSelectionListener(event -> {
            if (redirect.get() && fixture.table().getSelectedRow() == 0) {
                fixture.table().setRowSelectionInterval(1, 1);
            }
        }));
        HarnessCommand original = command("redirect");
        HarnessExecution receipt = fixture.plugin().execute(original);
        drain();
        assertFailed(fixture, original.requestId());
        assertThat(fixture.opened()).hasValue(0);
        redirect.set(false);
        HarnessExecution replay = fixture.plugin().replay(original).orElseThrow();
        assertThat(replay.output()).isEqualTo(receipt.output());
        assertThat(fixture.opened()).hasValue(0);

        fixture.plugin().execute(command("recovered"));
        drain();
        assertThat(fixture.bridge().actionStatus("recovered").output()).containsEntry("status", "COMPLETED");
        assertThat(fixture.opened()).hasValue(1);
        assertThat(fixture.openedId()).hasValue(101);
    }

    @Test
    void refusesASelectionClearedByTheApplication() throws Exception {
        Fixture fixture = fixture();
        SwingUtilities.invokeAndWait(() -> fixture.table().getSelectionModel().addListSelectionListener(event -> {
            if (!fixture.table().getSelectionModel().isSelectionEmpty()) {
                fixture.table().clearSelection();
            }
        }));
        fixture.plugin().execute(command("cleared"));
        drain();
        assertFailed(fixture, "cleared");
        assertThat(fixture.opened()).hasValue(0);
    }

    @Test
    void refusesChangedRecordDataEvenWhenTheViewIndexRemainsSelectedAndRemovesTemporaryWatch() throws Exception {
        Fixture fixture = fixture();
        AtomicInteger listeners = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = (DefaultTableModel) fixture.table().getModel();
            fixture.table().getSelectionModel().addListSelectionListener(event -> {
                if (fixture.table().getSelectedRow() == 0) {
                    model.setValueAt(999, 0, 0);
                }
            });
            listeners.set(model.getTableModelListeners().length);
        });
        fixture.plugin().execute(command("replaced-record"));
        drain();
        assertFailed(fixture, "replaced-record");
        assertThat(fixture.opened()).hasValue(0);
        SwingUtilities.invokeAndWait(() -> assertThat(((DefaultTableModel) fixture.table().getModel())
                .getTableModelListeners()).hasSize(listeners.get()));
    }

    @Test
    void refusesSortingThatRetargetsAnUnchangedSelectedViewIndex() throws Exception {
        Fixture fixture = fixture();
        SwingUtilities.invokeAndWait(() -> {
            JTable table = fixture.table();
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>((DefaultTableModel) table.getModel());
            table.setRowSorter(sorter);
            table.setUpdateSelectionOnSort(false);
            AtomicBoolean once = new AtomicBoolean();
            table.getSelectionModel().addListSelectionListener(event -> {
                if (table.getSelectedRow() == 0 && once.compareAndSet(false, true)) {
                    sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.DESCENDING)));
                }
            });
        });
        fixture.plugin().execute(command("resorted-records"));
        drain();
        assertFailed(fixture, "resorted-records");
        assertThat(fixture.opened()).hasValue(0);
        SwingUtilities.invokeAndWait(() -> {
            assertThat(fixture.table().getSelectedRow()).isZero();
            assertThat(fixture.table().getValueAt(0, 0)).isEqualTo(202);
        });
    }

    private static Fixture fixture() throws Exception {
        JTable table = new JTable();
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger openedId = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            table.setModel(new DefaultTableModel(new Object[][]{{101, "Dahlia"}, {202, "Rose"}},
                    new Object[]{"ID", "Crop"}));
            table.setName("harvest.records");
            table.getAccessibleContext();
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setSize(300, 100);
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2) {
                        opened.incrementAndGet();
                        openedId.set((Integer) table.getValueAt(table.getSelectedRow(), 0));
                    }
                }
            });
            bridge.attach(table, route -> false);
        });
        return new Fixture(table, bridge, new SwingHarnessPlugin(bridge), opened, openedId);
    }

    private static HarnessCommand command(String id) {
        return new HarnessCommand(id, "double-click-row", Map.of("table", "harvest.records", "row", 0));
    }

    private static void assertFailed(Fixture fixture, String id) {
        assertThat(fixture.bridge().actionStatus(id).output()).containsEntry("status", "FAILED")
                .containsEntry("failureType", "IllegalStateException");
    }

    private static void drain() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private record Fixture(JTable table, SwingHarnessBridge bridge, SwingHarnessPlugin plugin,
                           AtomicInteger opened, AtomicInteger openedId) { }
}
