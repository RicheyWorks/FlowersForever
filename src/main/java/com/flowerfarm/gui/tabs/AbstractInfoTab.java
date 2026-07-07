package com.flowerfarm.gui.tabs;

import javax.swing.*;
import java.awt.*;

/**
 * Base class for static/informational tabs (Pricing Guidelines, Irrigation Tips, etc.).
 * Provides consistent styling and a simple way to set content.
 */
public abstract class AbstractInfoTab implements FlowerFarmTab {

    protected final JPanel panel;
    protected final JTextArea textArea;

    protected AbstractInfoTab() {
        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JScrollPane scroll = new JScrollPane(textArea);
        panel.add(scroll, BorderLayout.CENTER);
    }

    @Override
    public JComponent getUIComponent() {
        return panel;
    }

    protected void setContent(String content) {
        textArea.setText(content);
        textArea.setCaretPosition(0);
    }
}
