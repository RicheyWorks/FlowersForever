package com.flowerfarm.gui.tabs;

import com.flowerfarm.auth.FarmSession;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.lsystem.LSystem;
import com.flowerfarm.lsystem.LSystemDefinition;
import com.flowerfarm.lsystem.LSystemLibrary;
import com.flowerfarm.lsystem.RoseLSystemPresets;
import com.flowerfarm.lsystem.SeasonPalette;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Generative L-System rose visualizer — grow, animate, mutate, save rulesets,
 * and link presets to inventory rose SKUs.
 */
public class RoseVisualizerTab implements FlowerFarmTab {

    private final TabHost host;
    private final InventoryService inventoryService;
    private final LSystemLibrary library = new LSystemLibrary();
    private final Random rng = new Random();

    private JPanel panel;
    private LSystemCanvas canvas;
    private JComboBox<LSystem> presetBox;
    private JComboBox<String> inventorySkuBox;
    private JComboBox<SeasonPalette> seasonBox;
    private JSlider depthSlider;
    private JSlider angleSlider;
    private JSlider stepSlider;
    private JSlider branchSlider;
    private JLabel depthLabel;
    private JLabel angleLabel;
    private JLabel stepLabel;
    private JLabel branchLabel;
    private JTextArea rulesArea;
    private JLabel infoLabel;
    private JButton mutateBtn;
    private JButton saveRulesetBtn;

    private LSystem currentSystem;
    private String lastInstructions = "";
    private String linkedSku = "";

    private Timer animateTimer;
    private int animateDepth;
    private int animateTarget;

    public RoseVisualizerTab(InventoryService inventoryService, TabHost host) {
        this.inventoryService = inventoryService;
        this.host = host;
    }

    @Override public String getTabTitle() { return "Rose Visualizer"; }

    @Override
    public String getDescription() {
        return "L-System roses — animate, mutate, save rulesets, link to inventory SKUs";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    @Override
    public void initialize() {
        reloadPresetList();
        refreshInventorySkus();
        if (currentSystem == null && presetBox.getItemCount() > 0) {
            applyPreset(presetBox.getItemAt(0));
            growImmediate();
        }
    }

    @Override
    public void refreshData() {
        refreshInventorySkus();
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        // Grow / animate / load / export PNG stay available for VIEWER
        GuiPermissions.setWritable(canWrite, mutateBtn, saveRulesetBtn);
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel header = new JLabel("L-System Rose Visualizer — generative biology for PNW rose habits");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(header, BorderLayout.NORTH);

        canvas = new LSystemCanvas();
        panel.add(new JScrollPane(canvas), BorderLayout.CENTER);

        panel.add(buildControls(), BorderLayout.EAST);
        panel.add(buildBottomBar(), BorderLayout.SOUTH);
    }

    private JPanel buildControls() {
        JPanel east = new JPanel();
        east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
        east.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        east.setPreferredSize(new Dimension(320, 560));

        presetBox = new JComboBox<>();
        presetBox.addActionListener(e -> {
            LSystem sel = (LSystem) presetBox.getSelectedItem();
            if (sel != null && e.getActionCommand() != null) {
                applyPreset(sel);
                growImmediate();
            }
        });

        inventorySkuBox = new JComboBox<>();
        inventorySkuBox.setToolTipText("Inventory rose SKUs — pick one to apply a matching growth habit.");
        inventorySkuBox.addActionListener(e -> onInventorySkuSelected());

        seasonBox = new JComboBox<>(SeasonPalette.values());
        seasonBox.addActionListener(e -> growImmediate());

        depthSlider = slider(1, LSystem.MAX_DEPTH, 4);
        angleSlider = slider(8, 55, 25);
        stepSlider = slider(4, 28, 8);
        branchSlider = slider(0, 100, 40);

        depthLabel = new JLabel();
        angleLabel = new JLabel();
        stepLabel = new JLabel();
        branchLabel = new JLabel();

        ChangeListener relabel = e -> {
            updateSliderLabels();
            if (!depthSlider.getValueIsAdjusting()
                    && !angleSlider.getValueIsAdjusting()
                    && !stepSlider.getValueIsAdjusting()
                    && (animateTimer == null || !animateTimer.isRunning())) {
                growImmediate();
            }
        };
        depthSlider.addChangeListener(relabel);
        angleSlider.addChangeListener(relabel);
        stepSlider.addChangeListener(relabel);
        branchSlider.addChangeListener(e -> updateSliderLabels());

        rulesArea = new JTextArea(7, 22);
        rulesArea.setEditable(false);
        rulesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        rulesArea.setLineWrap(true);
        rulesArea.setWrapStyleWord(true);

        infoLabel = new JLabel(" ");
        infoLabel.setFont(infoLabel.getFont().deriveFont(11f));

        east.add(section("Variety / saved ruleset"));
        east.add(presetBox);
        east.add(Box.createVerticalStrut(6));
        east.add(section("Inventory rose SKU"));
        east.add(inventorySkuBox);
        east.add(Box.createVerticalStrut(6));
        east.add(section("Season / colors"));
        east.add(seasonBox);
        east.add(Box.createVerticalStrut(8));
        east.add(labeled(depthLabel, depthSlider));
        east.add(labeled(angleLabel, angleSlider));
        east.add(labeled(stepLabel, stepSlider));
        east.add(labeled(branchLabel, branchSlider));
        east.add(Box.createVerticalStrut(6));

        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        buttons.add(btn("Grow / redraw", e -> growImmediate()));
        buttons.add(btn("Animate grow", e -> animateGrow()));
        buttons.add(btn("Stop animation", e -> stopAnimation()));
        mutateBtn = btn("Evolve / Mutate", e -> mutate());
        saveRulesetBtn = btn("Save ruleset…", e -> saveRuleset());
        buttons.add(mutateBtn);
        buttons.add(saveRulesetBtn);
        buttons.add(btn("Load ruleset…", e -> loadRuleset()));
        buttons.add(btn("Export PNG…", e -> exportPng()));
        buttons.add(btn("Open Rose Varieties guide", e -> {
            if (host != null) {
                host.selectTab("Rose Varieties");
                host.setStatus("PNW rose variety guide — pair with visualizer presets.");
            }
        }));
        east.add(buttons);

        east.add(Box.createVerticalStrut(8));
        east.add(section("Axiom & rules"));
        east.add(new JScrollPane(rulesArea));
        east.add(Box.createVerticalStrut(4));
        east.add(infoLabel);

        updateSliderLabels();
        return east;
    }

    private JPanel buildBottomBar() {
        JPanel south = new JPanel(new BorderLayout());
        JLabel tip = new JLabel("Tip: F=stem, +/−=turn, []=branch, B=bloom · depth max "
                + LSystem.MAX_DEPTH + " · save custom rules under data/lsystems/");
        tip.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        south.add(tip, BorderLayout.CENTER);
        return south;
    }

    private void reloadPresetList() {
        LSystem selected = (LSystem) presetBox.getSelectedItem();
        presetBox.removeAllItems();
        for (LSystem p : RoseLSystemPresets.all()) {
            presetBox.addItem(p);
        }
        for (LSystemDefinition def : library.loadAll()) {
            LSystem custom = def.toLSystem();
            presetBox.addItem(custom);
        }
        if (selected != null) {
            presetBox.setSelectedItem(selected);
        }
    }

    private void refreshInventorySkus() {
        if (inventorySkuBox == null || inventoryService == null) {
            return;
        }
        String prev = (String) inventorySkuBox.getSelectedItem();
        inventorySkuBox.removeAllItems();
        inventorySkuBox.addItem("(none — use variety preset)");
        for (Item item : inventoryService.getAllItems()) {
            if (looksLikeRose(item)) {
                inventorySkuBox.addItem(item.getName());
            }
        }
        if (prev != null) {
            inventorySkuBox.setSelectedItem(prev);
        }
    }

    private static boolean looksLikeRose(Item item) {
        String n = item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
        String c = item.getCategory() == null ? "" : item.getCategory().toLowerCase(Locale.ROOT);
        return n.contains("rose") || n.contains("rosa") || c.contains("flower") || c.contains("plant");
    }

    private void onInventorySkuSelected() {
        String sku = (String) inventorySkuBox.getSelectedItem();
        if (sku == null || sku.startsWith("(none")) {
            linkedSku = "";
            return;
        }
        linkedSku = sku;
        LSystem matched = matchPresetForSku(sku);
        if (matched != null) {
            // Select matching preset without losing custom if exact name match in list
            for (int i = 0; i < presetBox.getItemCount(); i++) {
                if (presetBox.getItemAt(i).getName().equalsIgnoreCase(matched.getName())
                        || sku.toLowerCase(Locale.ROOT).contains(
                        presetBox.getItemAt(i).getName().toLowerCase(Locale.ROOT).split(" ")[0])) {
                    // Prefer built-in matched system
                    break;
                }
            }
            applyPreset(matched);
            // Keep display name tied to SKU
            currentSystem = new LSystem(
                    sku + " (from inventory)",
                    "Growth habit linked to inventory SKU \"" + sku + "\" using "
                            + matched.getName() + " rules.",
                    matched.getAxiom(),
                    new LinkedHashMap<>(matched.getRules()),
                    matched.getDefaultAngleDegrees(),
                    matched.getDefaultStep(),
                    matched.getDefaultDepth()
            );
            refreshRulesArea(currentSystem);
            growImmediate();
            if (host != null) {
                host.setStatus("Visualizer linked to inventory SKU: " + sku
                        + " → " + matched.getName() + " habit.");
            }
        }
    }

    /**
     * Map inventory rose names to the closest built-in growth habit.
     */
    static LSystem matchPresetForSku(String sku) {
        if (sku == null) {
            return RoseLSystemPresets.nootka();
        }
        String s = sku.toLowerCase(Locale.ROOT);
        if (s.contains("nootka") || s.contains("nutkana") || s.contains("baldhip") || s.contains("native")) {
            return RoseLSystemPresets.nootka();
        }
        if (s.contains("rugosa") || s.contains("hansa") || s.contains("shrub")) {
            return RoseLSystemPresets.rugosa();
        }
        if (s.contains("bourbon") || s.contains("zephirine") || s.contains("old garden")) {
            return RoseLSystemPresets.bourbon();
        }
        if (s.contains("climb") || s.contains("dawn") || s.contains("trellis") || s.contains("pillar")) {
            return RoseLSystemPresets.climber();
        }
        if (s.contains("damask") || s.contains("ispahan") || s.contains("fragrance") || s.contains("scent")) {
            return RoseLSystemPresets.damask();
        }
        // Default PNW native habit
        return RoseLSystemPresets.nootka();
    }

    private void applyPreset(LSystem system) {
        stopAnimation();
        currentSystem = system;
        linkedSku = "";
        depthSlider.setValue(system.getDefaultDepth());
        angleSlider.setValue((int) Math.round(system.getDefaultAngleDegrees()));
        stepSlider.setValue((int) Math.round(system.getDefaultStep()));
        refreshRulesArea(system);
        updateSliderLabels();
    }

    private void refreshRulesArea(LSystem system) {
        StringBuilder rules = new StringBuilder();
        rules.append("Axiom: ").append(system.getAxiom()).append('\n');
        system.getRules().forEach((k, v) -> rules.append(k).append(" → ").append(v).append('\n'));
        if (linkedSku != null && !linkedSku.isBlank()) {
            rules.append("\nLinked inventory SKU: ").append(linkedSku).append('\n');
        }
        rules.append('\n').append(system.getDescription());
        rulesArea.setText(rules.toString());
        rulesArea.setCaretPosition(0);
    }

    private void growImmediate() {
        stopAnimation();
        renderAtDepth(depthSlider.getValue());
    }

    private void animateGrow() {
        if (!FarmSession.canMutateData() && FarmSession.isAuthenticated()) {
            // VIEWER can still animate for fun — allowed
        }
        stopAnimation();
        if (currentSystem == null) {
            currentSystem = (LSystem) presetBox.getSelectedItem();
            if (currentSystem != null) {
                applyPreset(currentSystem);
            }
        }
        if (currentSystem == null) {
            return;
        }
        animateDepth = 0;
        animateTarget = depthSlider.getValue();
        if (host != null) {
            host.setStatus("⏳ Animating L-System grow…");
        }
        animateTimer = new Timer(280, e -> {
            renderAtDepth(animateDepth);
            animateDepth++;
            if (animateDepth > animateTarget) {
                stopAnimation();
                if (host != null) {
                    host.setStatus("Animation complete — " + currentSystem.getName()
                            + " depth " + animateTarget + ".");
                }
            }
        });
        animateTimer.setInitialDelay(0);
        animateTimer.start();
    }

    private void stopAnimation() {
        if (animateTimer != null && animateTimer.isRunning()) {
            animateTimer.stop();
        }
        animateTimer = null;
    }

    private void renderAtDepth(int depth) {
        if (currentSystem == null) {
            currentSystem = (LSystem) presetBox.getSelectedItem();
            if (currentSystem != null) {
                applyPreset(currentSystem);
            }
        }
        if (currentSystem == null) {
            return;
        }

        double angle = angleSlider.getValue();
        double step = stepSlider.getValue();
        lastInstructions = currentSystem.expand(depth);
        if (!lastInstructions.contains("B")) {
            lastInstructions = lastInstructions.replace("X", "FB").replace("Y", "B");
        }
        SeasonPalette season = (SeasonPalette) seasonBox.getSelectedItem();
        canvas.setScene(lastInstructions, angle, step, season);

        String status = currentSystem.getName()
                + " · depth " + depth
                + " · " + lastInstructions.length() + " symbols"
                + (linkedSku.isBlank() ? "" : " · SKU " + linkedSku);
        canvas.setStatusLine(status);
        infoLabel.setText("<html><body style='width:250px'>" + status + "</body></html>");
    }

    private void mutate() {
        if (!ensureCanEdit()) {
            return;
        }
        if (currentSystem == null) {
            growImmediate();
        }
        if (currentSystem == null) {
            return;
        }
        int bias = branchSlider.getValue();
        LSystem mutant = currentSystem;
        int times = 1 + bias / 40;
        for (int i = 0; i < times; i++) {
            mutant = mutant.mutate(rng);
        }
        currentSystem = mutant;
        angleSlider.setValue((int) Math.round(mutant.getDefaultAngleDegrees()));
        stepSlider.setValue((int) Math.round(mutant.getDefaultStep()));
        refreshRulesArea(mutant);
        growImmediate();
        if (host != null) {
            host.setStatus("Evolved mutant — save ruleset to keep it.");
        }
    }

    private void saveRuleset() {
        if (!ensureCanEdit()) {
            return;
        }
        if (currentSystem == null) {
            JOptionPane.showMessageDialog(panel, "Nothing to save yet.", "Save", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = JOptionPane.showInputDialog(panel, "Ruleset name:", currentSystem.getName());
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            LSystemDefinition def = LSystemDefinition.from(new LSystem(
                    name.trim(),
                    currentSystem.getDescription(),
                    currentSystem.getAxiom(),
                    new LinkedHashMap<>(currentSystem.getRules()),
                    angleSlider.getValue(),
                    stepSlider.getValue(),
                    depthSlider.getValue()
            ));
            if (linkedSku != null && !linkedSku.isBlank()) {
                def.setLinkedSku(linkedSku);
            }
            Path file = library.save(def);
            reloadPresetList();
            if (host != null) {
                host.setStatus("Saved L-System ruleset → " + file.getFileName());
            }
            JOptionPane.showMessageDialog(panel, "Saved to:\n" + file.toAbsolutePath(),
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadRuleset() {
        try {
            List<Path> files = library.listFiles();
            if (files.isEmpty()) {
                JOptionPane.showMessageDialog(panel,
                        "No saved rulesets in " + library.getDirectory().toAbsolutePath()
                                + "\nSave one first.",
                        "Load", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String[] names = files.stream().map(p -> p.getFileName().toString()).toArray(String[]::new);
            String pick = (String) JOptionPane.showInputDialog(panel, "Choose ruleset:",
                    "Load L-System", JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
            if (pick == null) {
                return;
            }
            Path file = library.getDirectory().resolve(pick);
            LSystemDefinition def = library.load(file);
            currentSystem = def.toLSystem();
            linkedSku = def.getLinkedSku() == null ? "" : def.getLinkedSku();
            depthSlider.setValue(Math.min(LSystem.MAX_DEPTH, Math.max(1, def.getDepth())));
            angleSlider.setValue((int) Math.round(def.getAngleDegrees()));
            stepSlider.setValue((int) Math.round(def.getStep()));
            refreshRulesArea(currentSystem);
            reloadPresetList();
            growImmediate();
            if (host != null) {
                host.setStatus("Loaded ruleset: " + def.getName());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(), "Load failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportPng() {
        try {
            if (host != null) {
                host.setStatus("⏳ Exporting rose PNG…");
            }
            BufferedImage img = canvas.toImage();
            JFileChooser chooser = new JFileChooser();
            String base = currentSystem == null ? "rose" : currentSystem.getName().replaceAll("\\W+", "_");
            chooser.setSelectedFile(new File(base + "-lsystem.png"));
            chooser.setDialogTitle("Export L-System rose as PNG");
            if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
                if (host != null) {
                    host.setStatus("PNG export cancelled.");
                }
                return;
            }
            File file = chooser.getSelectedFile();
            ImageIO.write(img, "png", file);
            if (host != null) {
                host.setStatus("Exported rose visualizer PNG → " + file.getName());
            }
            JOptionPane.showMessageDialog(panel,
                    "Saved:\n" + file.getAbsolutePath(),
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean ensureCanEdit() {
        if (FarmSession.isAuthenticated() && !FarmSession.canMutateData()) {
            JOptionPane.showMessageDialog(panel,
                    "VIEWER role cannot mutate or save rulesets.\nLog in as HAND or OWNER.",
                    "Permission denied", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void updateSliderLabels() {
        depthLabel.setText("Recursion depth: " + depthSlider.getValue() + " (max " + LSystem.MAX_DEPTH + ")");
        angleLabel.setText("Branch angle: " + angleSlider.getValue() + "°");
        stepLabel.setText("Segment length: " + stepSlider.getValue() + " px");
        branchLabel.setText("Mutation / branching bias: " + branchSlider.getValue() + "%");
    }

    private static JButton btn(String label, java.awt.event.ActionListener a) {
        JButton b = new JButton(label);
        b.addActionListener(a);
        return b;
    }

    private static JSlider slider(int min, int max, int value) {
        JSlider s = new JSlider(min, max, value);
        s.setMajorTickSpacing(Math.max(1, (max - min) / 4));
        s.setPaintTicks(true);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        return s;
    }

    private static JLabel section(String title) {
        JLabel l = new JLabel(title);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel labeled(JLabel label, JSlider slider) {
        JPanel p = new JPanel(new BorderLayout(2, 2));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        p.add(label, BorderLayout.NORTH);
        p.add(slider, BorderLayout.CENTER);
        return p;
    }
}
