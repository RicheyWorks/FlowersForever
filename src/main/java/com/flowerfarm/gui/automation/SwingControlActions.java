package com.flowerfarm.gui.automation;

import javax.swing.AbstractButton;
import javax.swing.DefaultButtonModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import java.awt.Component;

/** Validated control operations invoked on the EDT by the runtime harness. */
final class SwingControlActions {
    private SwingControlActions() { }

    static void scroll(JScrollPane pane, String axis, int value) {
        requireUsable(pane, "Scroll pane");
        JScrollBar bar;
        int policy;
        int never;
        if ("HORIZONTAL".equalsIgnoreCase(axis)) {
            bar = pane.getHorizontalScrollBar();
            policy = pane.getHorizontalScrollBarPolicy();
            never = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER;
        } else if ("VERTICAL".equalsIgnoreCase(axis)) {
            bar = pane.getVerticalScrollBar();
            policy = pane.getVerticalScrollBarPolicy();
            never = JScrollPane.VERTICAL_SCROLLBAR_NEVER;
        } else {
            throw new IllegalArgumentException("Scroll axis must be HORIZONTAL or VERTICAL");
        }
        if (bar == null || policy == never) {
            throw new IllegalArgumentException("The requested scroll axis is not available");
        }
        requireUsable(bar, "Scroll axis");
        int minimum = bar.getMinimum();
        long maximum = (long) bar.getMaximum() - bar.getVisibleAmount();
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Scroll value must be within " + minimum + ".." + maximum);
        }
        bar.setValue(value);
        if (bar.getValue() != value) {
            throw new IllegalStateException("Scroll position did not retain the requested value");
        }
    }

    static void setDivider(JSplitPane pane, int value) {
        requireUsable(pane, "Split pane");
        int minimum = pane.getMinimumDividerLocation();
        int maximum = pane.getMaximumDividerLocation();
        if (minimum > maximum || value < minimum || value > maximum) {
            throw new IllegalArgumentException("Divider value must be within the current layout bounds "
                    + minimum + ".." + maximum);
        }
        pane.setDividerLocation(value);
        if (pane.getDividerLocation() != value) {
            throw new IllegalStateException("Divider did not retain the requested location");
        }
    }

    static void setToggle(AbstractButton button, boolean selected) {
        if (!(button instanceof JToggleButton || button instanceof JCheckBoxMenuItem
                || button instanceof JRadioButtonMenuItem)) {
            throw new IllegalArgumentException("The control is not a checkbox, radio button, or toggle");
        }
        requireUsable(button, "Toggle");
        if (button.isSelected() == selected) {
            return;
        }
        if (!selected && button.getModel() instanceof DefaultButtonModel model
                && model.getGroup() != null) {
            throw new IllegalArgumentException(
                    "A selected grouped toggle cannot be cleared; select another group option");
        }
        button.doClick();
        if (button.isSelected() != selected) {
            throw new IllegalStateException("Toggle did not retain the requested selected state");
        }
    }

    private static void requireUsable(Component component, String kind) {
        if (component == null) {
            throw new IllegalArgumentException(kind + " is required");
        }
        if (!component.isVisible()) {
            throw new IllegalArgumentException(kind + " is hidden");
        }
        if (!component.isEnabled()) {
            throw new IllegalArgumentException(kind + " is disabled");
        }
    }
}
