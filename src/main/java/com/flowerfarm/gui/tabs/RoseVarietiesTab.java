package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import javax.swing.*;
import java.awt.*;

/**
 * Rose varieties reference tab with a one-click "add sample Nootka Rose" action.
 *
 * <p>Content is curated for Oregon &amp; Western Washington (west of the
 * Cascades), drawing on course material and regionally hardy native selections.
 */
public class RoseVarietiesTab implements FlowerFarmTab {

    private final InventoryService inventoryService;
    private final TabHost host;

    private JPanel panel;
    private JButton sampleButton;

    public RoseVarietiesTab(InventoryService inventoryService, TabHost host) {
        this.inventoryService = inventoryService;
        this.host = host;
    }

    @Override
    public String getTabTitle() {
        return "Rose Varieties";
    }

    @Override
    public String getDescription() {
        return "PNW rose variety guide (west of the Cascades)";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        GuiPermissions.setWritable(canWrite, sampleButton);
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout());

        JTextArea roseText = new JTextArea(getRoseSuggestionsText());
        roseText.setEditable(false);
        roseText.setLineWrap(true);
        roseText.setWrapStyleWord(true);
        roseText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        roseText.setCaretPosition(0);
        panel.add(new JScrollPane(roseText), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sampleButton = new JButton("Add Sample Nootka Rose to Inventory");
        sampleButton.addActionListener(e -> addSampleRose());
        JButton visualizer = new JButton("Open Rose Visualizer (L-Systems)");
        visualizer.setToolTipText("Generative growth habits for Nootka, rugosa, Bourbon, climbers…");
        visualizer.addActionListener(e -> {
            if (host != null) {
                host.selectTab("Rose Visualizer");
                host.setStatus("Rose Visualizer — grow & mutate PNW rose L-Systems.");
            }
        });
        south.add(sampleButton);
        south.add(visualizer);
        panel.add(south, BorderLayout.SOUTH);
    }

    private void addSampleRose() {
        if (!GuiPermissions.requireWrite(host, panel, "add sample inventory items")) {
            return;
        }
        try {
            inventoryService.addItem(new Item(
                    "Nootka Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50,
                    "Native PNW rose, pink blooms, hardy in wet soils"));
            if (host != null) {
                host.refreshAll();
                host.setStatus("Sample rose added: Nootka Rose.");
            }
            JOptionPane.showMessageDialog(panel, "Nootka Rose added to inventory.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(panel, "Error: " + ex.getMessage(),
                    "Add Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getRoseSuggestionsText() {
        return """
                Rose Varieties — Oregon & Western Washington West of the Cascades
                ──────────────────────────────────────────────────────────────────

                ONE-TIME BLOOMERS (from course slides)
                  Alba       → Alba Maxima: White, fragrant, hardy in shade.
                  Damask     → Ispahan: Pink, strong scent, drought-tolerant.
                  Gallicas   → Charles de Mills: Crimson, compact shrub.
                  Centifolia → Fantin Latour: Pink, very full blooms.
                  Moss       → William Lobb: Purple, mossy buds, vigorous.

                REPEAT BLOOMERS (from course slides)
                  Bourbon          → Zephirine Drouhin: Pink climber, thornless, shade-tolerant.
                  Hybrid Perpetual → Reine des Violettes: Purple, recurrent, fragrant.
                  Portland         → Comte de Chambord: Pink, compact, spicy scent.

                REGIONAL / NATIVE (Hardy, Disease-Resistant for PNW)
                  Nootka Rose (Rosa nutkana)     — Native, pink, wildlife-friendly, tolerates wet soils.
                  Baldhip Rose (Rosa gymnocarpa) — Native, pink, small hips, shade-loving.
                  Cluster Rose (Rosa pisocarpa)  — Native, pink clusters, good for erosion control.
                  Rosa rugosa Hansa              — Purple, rugged, salt-tolerant.
                  New Dawn Climber               — Light pink, vigorous, blackspot-resistant.
                  Queen Elizabeth Hybrid Tea     — Pink, tall, hearty.
                  Strawberry Hill (D.Austin)     — Pink climber, fragrant.
                  Munstead Wood (D.Austin)       — Rich dark red, OGR-type fragrance.
                  Harison's Yellow Shrub         — Yellow, drought-tolerant.

                TIP: For humid PNW conditions choose disease-resistant varieties (rugosas,
                Nootka). Consult Portland Nursery or Swansons (Seattle) for availability.
                """;
    }
}
