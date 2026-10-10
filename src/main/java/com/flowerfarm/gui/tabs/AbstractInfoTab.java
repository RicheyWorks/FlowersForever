package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * Base class for static/informational tabs (Pricing Guidelines, Irrigation Tips, etc.).
 * Provides consistent styling and a simple way to set content.
 */
public abstract class AbstractInfoTab implements FlowerFarmTab {

    private JPanel panel;
    protected JTextArea textArea;
    private String content = "";

    protected AbstractInfoTab() {
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            panel = FarmUi.page();
            panel.add(FarmUi.pageHeader(
                    getTabTitle(), getDescription(), null), BorderLayout.NORTH);
            textArea = FarmUi.displayArea(22, 64);
            textArea.setName("reference.guide");
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            textArea.setText(content);
            textArea.setCaretPosition(0);
            panel.add(FarmUi.section(
                    "Working reference",
                    "Use this guide while planning products, pricing, and market preparation.",
                    new JScrollPane(textArea)), BorderLayout.CENTER);
        }
        return panel;
    }

    protected void setContent(String content) {
        this.content = content == null ? "" : content;
        if (textArea != null) {
            textArea.setText(this.content);
            textArea.setCaretPosition(0);
        }
    }
}
