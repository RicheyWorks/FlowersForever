package com.flowerfarm.gui.automation;

import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingControlActionsTest {
    @Test
    void scrollsBothAxesToTheirExactReachableBounds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JScrollPane pane = scrollPane();
            JScrollBar vertical = pane.getVerticalScrollBar();
            JScrollBar horizontal = pane.getHorizontalScrollBar();
            int maxY = maximum(vertical);
            int maxX = maximum(horizontal);
            assertThat(maxY).isGreaterThan(vertical.getMinimum());
            assertThat(maxX).isGreaterThan(horizontal.getMinimum());

            SwingControlActions.scroll(pane, "vertical", maxY);
            SwingControlActions.scroll(pane, "HoRiZoNtAl", maxX);

            assertThat(vertical.getValue()).isEqualTo(maxY);
            assertThat(horizontal.getValue()).isEqualTo(maxX);
            assertThat(pane.getViewport().getViewPosition()).isEqualTo(new Point(maxX, maxY));

            SwingControlActions.scroll(pane, "VERTICAL", vertical.getMinimum());
            SwingControlActions.scroll(pane, "HORIZONTAL", horizontal.getMinimum());
            assertThat(pane.getViewport().getViewPosition()).isEqualTo(new Point(0, 0));
        });
    }

    @Test
    void rejectsInvalidScrollAxesAndBoundsBeforeChangingTheViewport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JScrollPane pane = scrollPane();
            SwingControlActions.scroll(pane, "VERTICAL", 10);
            Point original = pane.getViewport().getViewPosition();
            AtomicInteger changes = new AtomicInteger();
            pane.getVerticalScrollBar().addAdjustmentListener(event -> changes.incrementAndGet());
            pane.getHorizontalScrollBar().addAdjustmentListener(event -> changes.incrementAndGet());

            for (String axis : List.of("depth", "", " VERTICAL ")) {
                assertThatThrownBy(() -> SwingControlActions.scroll(pane, axis, 0))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("axis");
            }
            assertThatThrownBy(() -> SwingControlActions.scroll(pane, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            for (String axis : List.of("HORIZONTAL", "VERTICAL")) {
                JScrollBar bar = axis.equals("VERTICAL")
                        ? pane.getVerticalScrollBar() : pane.getHorizontalScrollBar();
                assertThatThrownBy(() -> SwingControlActions.scroll(pane, axis, bar.getMinimum() - 1))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("within");
                assertThatThrownBy(() -> SwingControlActions.scroll(pane, axis, maximum(bar) + 1))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("within");
            }
            assertThat(changes).hasValue(0);
            assertThat(pane.getViewport().getViewPosition()).isEqualTo(original);
        });
    }

    @Test
    void rejectsDisabledHiddenAndUnavailableScrollAxes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JScrollPane pane = scrollPane();
            JScrollBar vertical = pane.getVerticalScrollBar();
            vertical.setEnabled(false);
            assertThatThrownBy(() -> SwingControlActions.scroll(pane, "VERTICAL", 10))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
            vertical.setEnabled(true);
            vertical.setVisible(false);
            assertThatThrownBy(() -> SwingControlActions.scroll(pane, "VERTICAL", 10))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hidden");
            vertical.setVisible(true);
            pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            assertThatThrownBy(() -> SwingControlActions.scroll(pane, "VERTICAL", 10))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not available");
            pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            pane.setEnabled(false);
            assertThatThrownBy(() -> SwingControlActions.scroll(pane, "VERTICAL", 10))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
            pane.setEnabled(true);
            pane.setVisible(false);
            assertThatThrownBy(() -> SwingControlActions.scroll(pane, "VERTICAL", 10))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hidden");
            assertThat(vertical.getValue()).isZero();
        });
    }

    @Test
    void positionsTheDividerAtBothCurrentLayoutBounds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSplitPane pane = splitPane();
            int minimum = pane.getMinimumDividerLocation();
            int maximum = pane.getMaximumDividerLocation();
            assertThat(maximum).isGreaterThan(minimum);

            SwingControlActions.setDivider(pane, maximum);
            assertThat(pane.getDividerLocation()).isEqualTo(maximum);
            SwingControlActions.setDivider(pane, minimum);
            assertThat(pane.getDividerLocation()).isEqualTo(minimum);
        });
    }

    @Test
    void rejectsDividerBoundsAndUnavailableControlsBeforeMutation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSplitPane pane = splitPane();
            int original = pane.getDividerLocation();
            AtomicInteger changes = new AtomicInteger();
            pane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
                    event -> changes.incrementAndGet());

            assertThatThrownBy(() -> SwingControlActions.setDivider(pane, pane.getMinimumDividerLocation() - 1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("layout bounds");
            assertThatThrownBy(() -> SwingControlActions.setDivider(pane, pane.getMaximumDividerLocation() + 1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("layout bounds");
            pane.setEnabled(false);
            assertThatThrownBy(() -> SwingControlActions.setDivider(pane, original))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
            pane.setEnabled(true);
            pane.setVisible(false);
            assertThatThrownBy(() -> SwingControlActions.setDivider(pane, original))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hidden");
            assertThat(pane.getDividerLocation()).isEqualTo(original);
            assertThat(changes).hasValue(0);
        });
    }

    @Test
    void refusesADividerMoveBeforeThereIsAUsableLayout() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
            int original = pane.getDividerLocation();
            assertThatThrownBy(() -> SwingControlActions.setDivider(pane, 0))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("layout bounds");
            assertThat(pane.getDividerLocation()).isEqualTo(original);
        });
    }

    @Test
    void setsToggleStateThroughListenersOnlyWhenTheStateChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (AbstractButton button : List.of(new JCheckBox(), new JToggleButton(), new JRadioButton(),
                    new JCheckBoxMenuItem(), new JRadioButtonMenuItem())) {
                AtomicInteger actions = new AtomicInteger();
                button.addActionListener(event -> actions.incrementAndGet());

                SwingControlActions.setToggle(button, false);
                assertThat(actions).hasValue(0);
                SwingControlActions.setToggle(button, true);
                assertThat(button.isSelected()).isTrue();
                assertThat(actions).hasValue(1);
                SwingControlActions.setToggle(button, true);
                assertThat(actions).hasValue(1);
                SwingControlActions.setToggle(button, false);
                assertThat(button.isSelected()).isFalse();
                assertThat(actions).hasValue(2);
            }
        });
    }

    @Test
    void preservesRadioGroupConstraintsWithoutFiringARejectedAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JRadioButton first = new JRadioButton("Pickup", true);
            JRadioButton second = new JRadioButton("Delivery");
            ButtonGroup group = new ButtonGroup();
            group.add(first);
            group.add(second);
            AtomicInteger actions = new AtomicInteger();
            first.addActionListener(event -> actions.incrementAndGet());
            second.addActionListener(event -> actions.incrementAndGet());

            assertThatThrownBy(() -> SwingControlActions.setToggle(first, false))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("grouped");
            assertThat(group.getSelection()).isSameAs(first.getModel());
            assertThat(actions).hasValue(0);
            SwingControlActions.setToggle(second, false);
            assertThat(actions).hasValue(0);
            SwingControlActions.setToggle(second, true);
            assertThat(group.getSelection()).isSameAs(second.getModel());
            assertThat(first.isSelected()).isFalse();
            assertThat(actions).hasValue(1);
        });
    }

    @Test
    void rejectsOrdinaryButtonsAndUnavailableToggles() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton ordinary = new JButton("Save");
            JCheckBox toggle = new JCheckBox("Packed");
            AtomicInteger actions = new AtomicInteger();
            ordinary.addActionListener(event -> actions.incrementAndGet());
            toggle.addActionListener(event -> actions.incrementAndGet());
            assertThatThrownBy(() -> SwingControlActions.setToggle(ordinary, true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a checkbox");
            toggle.setEnabled(false);
            assertThatThrownBy(() -> SwingControlActions.setToggle(toggle, true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
            toggle.setEnabled(true);
            toggle.setVisible(false);
            assertThatThrownBy(() -> SwingControlActions.setToggle(toggle, true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hidden");
            assertThat(toggle.isSelected()).isFalse();
            assertThat(actions).hasValue(0);
        });
    }

    @Test
    void reportsAProductionListenerThatDoesNotRetainTheRequestedState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JCheckBox toggle = new JCheckBox("Packed");
            AtomicInteger actions = new AtomicInteger();
            toggle.addActionListener(event -> {
                actions.incrementAndGet();
                toggle.setSelected(false);
            });

            assertThatThrownBy(() -> SwingControlActions.setToggle(toggle, true))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not retain");

            assertThat(actions).hasValue(1);
            assertThat(toggle.isSelected()).isFalse();
        });
    }

    private static JScrollPane scrollPane() {
        JPanel view = new JPanel();
        view.setPreferredSize(new Dimension(600, 500));
        JScrollPane pane = new JScrollPane(view);
        pane.setSize(200, 150);
        pane.doLayout();
        pane.getViewport().doLayout();
        return pane;
    }

    private static JSplitPane splitPane() {
        JPanel first = new JPanel();
        JPanel second = new JPanel();
        first.setMinimumSize(new Dimension(40, 30));
        second.setMinimumSize(new Dimension(40, 30));
        JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, first, second);
        pane.setSize(300, 120);
        pane.setDividerLocation(150);
        pane.doLayout();
        return pane;
    }

    private static int maximum(JScrollBar bar) {
        return bar.getMaximum() - bar.getVisibleAmount();
    }
}
