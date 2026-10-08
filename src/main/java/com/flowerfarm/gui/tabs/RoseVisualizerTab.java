package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.lsystem.LSystem;
import com.flowerfarm.lsystem.LSystemDefinition;
import com.flowerfarm.lsystem.LSystemLibrary;
import com.flowerfarm.lsystem.RoseLSystemPresets;
import com.flowerfarm.lsystem.SeasonPalette;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.util.PathNames;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generative L-System rose visualizer — grow, animate, mutate, save rulesets,
 * and link presets to inventory rose SKUs.
 */
public class RoseVisualizerTab implements FlowerFarmTab {

    private final TabHost host;
    private final InventoryService inventoryService;
    private final FarmDialogs dialogs;
    private final FarmFileChooser files;
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
    private JLabel inventoryStatusLabel;
    private JButton refreshInventoryBtn;
    private JButton mutateBtn;
    private JButton saveRulesetBtn;

    private LSystem currentSystem;
    private String lastInstructions = "";
    private String linkedSku = "";
    private boolean refreshingInventory;
    private boolean inventoryRefreshQueued;
    private boolean applyingInventorySnapshot;

    private Timer animateTimer;
    private int animateDepth;
    private int animateTarget;

    public RoseVisualizerTab(InventoryService inventoryService, TabHost host) {
        this(inventoryService, host, FarmDialogs.swing(), FarmFileChooser.swing());
    }

    public RoseVisualizerTab(InventoryService inventoryService, TabHost host,
                             FarmDialogs dialogs, FarmFileChooser files) {
        this.inventoryService = inventoryService;
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
        this.files = java.util.Objects.requireNonNull(files, "files");
    }

    @Override public String getTabTitle() { return "Rose Visualizer"; }

    @Override
    public String getDescription() {
        return "Explore rose growth patterns and link them to inventory products";
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
        startInventoryRefresh();
        if (currentSystem == null && presetBox.getItemCount() > 0) {
            applyPreset(presetBox.getItemAt(0));
            growImmediate();
        }
    }

    @Override
    public void refreshData() {
        startInventoryRefresh();
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        // Grow / animate / load / export PNG stay available for VIEWER
        GuiPermissions.setWritable(canWrite, mutateBtn, saveRulesetBtn);
    }

    private void buildUI() {
        panel = FarmUi.page();
        infoLabel = FarmUi.statusBadge("Ready to grow", FarmUi.Tone.NEUTRAL);
        panel.add(FarmUi.pageHeader(
                "Rose growth visualizer",
                "Explore PNW rose habits, connect inventory products, and save useful variants.",
                infoLabel), BorderLayout.NORTH);
        canvas = new LSystemCanvas();
        canvas.setName("rose.visualizer.preview");
        canvas.getAccessibleContext().setAccessibleName("Rose growth preview");

        JScrollPane controlScroll = new JScrollPane(buildControls());
        controlScroll.setName("rose.visualizer.controls");
        controlScroll.setBorder(null);
        controlScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        controlScroll.getVerticalScrollBar().setUnitIncrement(16);
        controlScroll.setPreferredSize(new Dimension(360, 540));

        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                FarmUi.section("Growth preview",
                        "Redraw after changing a variety, season, or growth control.",
                        canvas),
                FarmUi.section("Growth controls",
                        "Start with a preset, then adjust or evolve the branching habit.",
                        controlScroll));
        workspace.setName("rose.visualizer.workspace");
        workspace.setResizeWeight(0.72);
        workspace.setDividerLocation(0.72);
        workspace.setBorder(null);
        panel.add(workspace, BorderLayout.CENTER);
        panel.add(buildBottomBar(), BorderLayout.SOUTH);
    }

    private JPanel buildControls() {
        JPanel east = new JPanel();
        east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
        east.setOpaque(false);
        east.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 8));

        presetBox = new JComboBox<>();
        FarmUi.initialFocus(presetBox);
        presetBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        presetBox.getAccessibleContext().setAccessibleName("Rose variety or saved ruleset");
        presetBox.addActionListener(e -> {
            LSystem sel = (LSystem) presetBox.getSelectedItem();
            if (sel != null && e.getActionCommand() != null) {
                applyPreset(sel);
                growImmediate();
            }
        });

        inventorySkuBox = new JComboBox<>();
        inventorySkuBox.setName("rose.visualizer.inventorySku");
        inventorySkuBox.addItem("(none — use variety preset)");
        inventorySkuBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        inventorySkuBox.getAccessibleContext().setAccessibleName("Inventory rose product");
        inventorySkuBox.setToolTipText("Choose an inventory rose to apply a matching growth habit.");
        inventorySkuBox.addActionListener(e -> onInventorySkuSelected());
        inventoryStatusLabel = FarmUi.statusBadge(
                "Inventory products not loaded", FarmUi.Tone.NEUTRAL);
        inventoryStatusLabel.setName("rose.visualizer.inventoryStatus");
        inventoryStatusLabel.getAccessibleContext().setAccessibleName(
                "Rose visualizer inventory refresh status");
        refreshInventoryBtn = FarmUi.secondaryButton("Refresh products");
        refreshInventoryBtn.setName("rose.visualizer.refreshInventory");
        refreshInventoryBtn.setToolTipText(
                "Reload flower and rose products that can be linked to this preview.");
        refreshInventoryBtn.addActionListener(event -> startInventoryRefresh());

        seasonBox = new JComboBox<>(SeasonPalette.values());
        seasonBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        seasonBox.getAccessibleContext().setAccessibleName("Rose season color palette");
        seasonBox.addActionListener(e -> growImmediate());

        depthSlider = slider(1, LSystem.MAX_DEPTH, 4,
                "Rose growth recursion depth");
        angleSlider = slider(8, 55, 25, "Rose branch angle");
        stepSlider = slider(4, 28, 8, "Rose stem segment length");
        branchSlider = slider(0, 100, 40, "Rose branching bias");

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
        rulesArea.getAccessibleContext().setAccessibleName("Current rose growth rules");
        rulesArea.getAccessibleContext().setAccessibleDescription(
                "Axiom and rewrite rules used for the current growth preview.");
        rulesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        rulesArea.setLineWrap(true);
        rulesArea.setWrapStyleWord(true);

        east.add(section("Variety / saved ruleset"));
        east.add(presetBox);
        east.add(Box.createVerticalStrut(6));
        east.add(section("Inventory rose product"));
        east.add(inventorySkuBox);
        east.add(Box.createVerticalStrut(4));
        JPanel inventoryActions = FarmUi.toolbar(inventoryStatusLabel, refreshInventoryBtn);
        inventoryActions.setName("rose.visualizer.inventoryActions");
        inventoryActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        inventoryActions.setMaximumSize(new Dimension(
                Integer.MAX_VALUE, inventoryActions.getPreferredSize().height));
        east.add(inventoryActions);
        east.add(Box.createVerticalStrut(6));
        east.add(section("Season / colors"));
        east.add(seasonBox);
        east.add(Box.createVerticalStrut(8));
        east.add(labeled(depthLabel, depthSlider));
        east.add(labeled(angleLabel, angleSlider));
        east.add(labeled(stepLabel, stepSlider));
        east.add(labeled(branchLabel, branchSlider));
        east.add(Box.createVerticalStrut(6));

        JPanel buttons = new JPanel(new GridLayout(0, 2, 6, 6));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton growButton = FarmUi.primaryButton("Grow / redraw");
        growButton.addActionListener(e -> growImmediate());
        buttons.add(growButton);
        buttons.add(actionButton("Animate grow", e -> animateGrow()));
        buttons.add(actionButton("Stop animation", e -> stopAnimation()));
        mutateBtn = actionButton("Evolve / mutate", e -> mutate());
        saveRulesetBtn = actionButton("Save ruleset…", e -> saveRuleset());
        buttons.add(mutateBtn);
        buttons.add(saveRulesetBtn);
        buttons.add(actionButton("Load ruleset…", e -> loadRuleset()));
        buttons.add(actionButton("Export PNG…", e -> exportPng()));
        buttons.add(actionButton("Open variety guide", e -> {
            if (host != null) {
                host.selectTab("Rose Varieties");
                host.setStatus("PNW rose variety guide — pair with visualizer presets.");
            }
        }));
        east.add(buttons);

        east.add(Box.createVerticalStrut(8));
        east.add(section("Axiom & rules"));
        JScrollPane rulesScroll = new JScrollPane(rulesArea);
        rulesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        east.add(rulesScroll);

        updateSliderLabels();
        return east;
    }

    private JPanel buildBottomBar() {
        JPanel south = new JPanel(new BorderLayout());
        JLabel tip = FarmUi.mutedLabel("Tip: F=stem, +/−=turn, []=branch, B=bloom · depth max "
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

    void startInventoryRefresh() {
        if (inventorySkuBox == null) {
            return;
        }
        if (inventoryService == null) {
            showInventoryRefreshState("Inventory link unavailable",
                    "This session has no inventory service. Variety presets remain available.",
                    FarmUi.Tone.WARNING);
            inventorySkuBox.setEnabled(false);
            refreshInventoryBtn.setEnabled(false);
            return;
        }
        if (refreshingInventory) {
            inventoryRefreshQueued = true;
            showInventoryRefreshState("Refresh queued · current product load finishing",
                    "One newer inventory-product snapshot will load next.", FarmUi.Tone.INFO);
            return;
        }
        refreshingInventory = true;
        inventorySkuBox.setEnabled(false);
        refreshInventoryBtn.setEnabled(false);
        showInventoryRefreshState("Loading inventory products…",
                "Flower and rose products are loading in the background.", FarmUi.Tone.INFO);

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return inventoryService.getAllItems().stream()
                        .filter(RoseVisualizerTab::looksLikeRose)
                        .map(Item::getName)
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::trim)
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }

            @Override
            protected void done() {
                try {
                    List<String> skus = get();
                    if (!inventoryRefreshQueued) {
                        applyInventorySnapshot(skus);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (!inventoryRefreshQueued) {
                        failInventoryRefresh(exception);
                    }
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (!inventoryRefreshQueued) {
                        failInventoryRefresh(exception.getCause() == null
                                ? exception : exception.getCause());
                    }
                } catch (RuntimeException exception) {
                    if (!inventoryRefreshQueued) {
                        failInventoryRefresh(exception);
                    }
                } finally {
                    finishInventoryRefresh();
                }
            }
        }.execute();
    }

    private void applyInventorySnapshot(List<String> skus) {
        String previous = (String) inventorySkuBox.getSelectedItem();
        applyingInventorySnapshot = true;
        try {
            inventorySkuBox.removeAllItems();
            inventorySkuBox.addItem("(none — use variety preset)");
            skus.forEach(inventorySkuBox::addItem);
            if (previous != null && skus.contains(previous)) {
                inventorySkuBox.setSelectedItem(previous);
            } else {
                inventorySkuBox.setSelectedIndex(0);
                linkedSku = "";
            }
        } finally {
            applyingInventorySnapshot = false;
        }
        String message = skus.isEmpty()
                ? "No flower or rose products found"
                : skus.size() + " linkable product" + (skus.size() == 1 ? "" : "s") + " ready";
        String description = skus.isEmpty()
                ? "Add a flower or rose product in Inventory, then refresh products. Variety presets remain available."
                : "Inventory product choices are current. The previous link was kept when that product still exists.";
        showInventoryRefreshState(message, description,
                skus.isEmpty() ? FarmUi.Tone.NEUTRAL : FarmUi.Tone.SUCCESS);
    }

    private void failInventoryRefresh(Throwable failure) {
        String detail = FarmUi.actionableError("Loading visualizer inventory products", failure);
        showInventoryRefreshState("Product refresh failed · retry",
                detail + " Existing product choices and the current preview were kept.",
                FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus("Rose Visualizer product refresh failed. Existing choices were kept.");
        }
    }

    private void finishInventoryRefresh() {
        refreshingInventory = false;
        inventorySkuBox.setEnabled(true);
        refreshInventoryBtn.setEnabled(true);
        if (inventoryRefreshQueued) {
            inventoryRefreshQueued = false;
            startInventoryRefresh();
        }
    }

    private void showInventoryRefreshState(String message, String description,
                                           FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(inventoryStatusLabel, message, tone);
        inventoryStatusLabel.setToolTipText(description);
        inventoryStatusLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private static boolean looksLikeRose(Item item) {
        String n = item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
        String c = item.getCategory() == null ? "" : item.getCategory().toLowerCase(Locale.ROOT);
        return n.contains("rose") || n.contains("rosa") || c.contains("flower") || c.contains("plant");
    }

    private void onInventorySkuSelected() {
        if (applyingInventorySnapshot) {
            return;
        }
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
                    "Growth habit linked to inventory product \"" + sku + "\" using "
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
                host.setStatus("Visualizer linked to inventory product: " + sku
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
            rules.append("\nLinked inventory product: ").append(linkedSku).append('\n');
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
                + (linkedSku.isBlank() ? "" : " · Product " + linkedSku);
        canvas.setStatusLine(status);
        FarmUi.updateStatusBadge(infoLabel, status, FarmUi.Tone.SUCCESS);
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
            dialogs.showMessage(panel, "Nothing to save yet.", "Save",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JTextField nameField = new JTextField(currentSystem.getName(), 28);
        nameField.getAccessibleContext().setAccessibleName("Ruleset name");
        JScrollPane nameForm = rulesetSaveDialog(panel, nameField);
        String name = null;
        while (name == null) {
            int choice = dialogs.showConfirm(panel, nameForm, "Save L-System ruleset",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                if (host != null) {
                    host.setStatus("Ruleset was not saved; the current rose is unchanged.");
                }
                return;
            }
            if (nameField.getText().isBlank()) {
                FarmUi.markFieldError(nameField, "Enter a name for this ruleset.");
            } else {
                FarmUi.clearFieldError(nameField);
                name = nameField.getText().trim();
            }
        }
        if (name.isBlank()) {
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
            dialogs.showMessage(panel, "Saved to:\n" + file.toAbsolutePath(),
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            dialogs.showMessage(panel, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadRuleset() {
        try {
            List<Path> files = library.listFiles();
            if (files.isEmpty()) {
                dialogs.showMessage(panel,
                        "No saved rulesets in " + library.getDirectory().toAbsolutePath()
                                + "\nSave one first.",
                        "Load", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String[] names = files.stream().map(PathNames::fileName).toArray(String[]::new);
            JComboBox<String> rulesetBox = new JComboBox<>(names);
            rulesetBox.getAccessibleContext().setAccessibleName("Saved ruleset to load");
            JScrollPane loadForm = rulesetLoadDialog(panel, rulesetBox);
            int choice = dialogs.showConfirm(panel, loadForm, "Load L-System",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            String pick = String.valueOf(rulesetBox.getSelectedItem());
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
            dialogs.showMessage(panel, ex.getMessage(), "Load failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    static JScrollPane rulesetSaveDialog(Component owner, JTextField nameField) {
        nameField.setName("rose.rulesetName");
        FarmUi.entryControl(nameField, null,
                "Name this ruleset before saving it to your library.");
        FarmUi.initialFocus(nameField);
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setName("rose.rulesetSaveForm");
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel guidance = FarmUi.wrappingHint(
                "Name this ruleset before saving it to your library.");
        guidance.setLabelFor(nameField);
        form.add(guidance, BorderLayout.NORTH);
        form.add(nameField, BorderLayout.CENTER);
        JScrollPane scroll = FarmUi.dialogScroll(owner, form, new Dimension(520, 100));
        scroll.setName("rose.rulesetSaveScroll");
        return scroll;
    }

    static JScrollPane rulesetLoadDialog(Component owner, JComboBox<String> rulesetBox) {
        rulesetBox.setName("rose.savedRuleset");
        rulesetBox.setPrototypeDisplayValue("Rose_ruleset_collection_2026.json");
        FarmUi.entryControl(rulesetBox, null, "Choose a saved ruleset:");
        FarmUi.initialFocus(rulesetBox);
        rulesetBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focus);
                label.putClientProperty("html.disable", Boolean.TRUE);
                String name = value == null ? "" : value.toString();
                label.setText(name);
                label.setToolTipText(rulesetFilenameTooltip(name));
                label.getAccessibleContext().setAccessibleName(name);
                return label;
            }
        });
        JTextArea selectedName = FarmUi.displayArea(3, 36);
        selectedName.setName("rose.selectedRulesetFilename");
        selectedName.setFont(selectedName.getFont().deriveFont(14f));
        selectedName.getAccessibleContext().setAccessibleName("Selected ruleset filename");
        Runnable showSelectedName = () -> {
            Object selected = rulesetBox.getSelectedItem();
            String name = selected == null ? "" : selected.toString();
            selectedName.setText(name);
            selectedName.setCaretPosition(0);
            selectedName.getAccessibleContext().setAccessibleDescription(name);
            rulesetBox.setToolTipText(rulesetFilenameTooltip(name));
            rulesetBox.getAccessibleContext().setAccessibleDescription(name);
        };
        rulesetBox.addActionListener(event -> showSelectedName.run());
        showSelectedName.run();
        JScrollPane filenameScroll = new JScrollPane(selectedName,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        filenameScroll.setName("rose.selectedRulesetFilenameScroll");
        filenameScroll.setBorder(BorderFactory.createLineBorder(FarmUi.borderColor()));
        filenameScroll.setPreferredSize(new Dimension(480, 82));
        filenameScroll.setMinimumSize(new Dimension(100, 82));
        filenameScroll.getVerticalScrollBar().setUnitIncrement(18);
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setName("rose.rulesetLoadForm");
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.add(FarmUi.entryField("Choose a saved ruleset:", rulesetBox, rulesetBox, '\0'),
                BorderLayout.NORTH);
        form.add(filenameScroll, BorderLayout.CENTER);
        JScrollPane scroll = FarmUi.dialogScroll(owner, form, new Dimension(520, 170));
        scroll.setName("rose.rulesetLoadScroll");
        return scroll;
    }

    private static String rulesetFilenameTooltip(String name) {
        return "<html>" + name.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;") + "</html>";
    }

    private void exportPng() {
        try {
            String base = currentSystem == null ? "rose" : currentSystem.getName().replaceAll("\\W+", "_");
            var selected = files.chooseSave(panel, "Export L-System rose as PNG",
                    new File(base + "-lsystem.png"));
            if (selected.isEmpty()) {
                FarmUi.updateStatusBadge(infoLabel, "PNG export cancelled", FarmUi.Tone.NEUTRAL);
                if (host != null) {
                    host.setStatus("PNG export cancelled. No file was changed.");
                }
                return;
            }
            File file = ensurePngExtension(selected.get());
            if (file.exists() && dialogs.showConfirm(panel,
                    "A file named " + file.getName() + " already exists. Replace it?",
                    "Replace existing PNG?", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                FarmUi.updateStatusBadge(
                        infoLabel, "PNG not saved · existing file kept", FarmUi.Tone.NEUTRAL);
                if (host != null) {
                    host.setStatus("PNG export cancelled. Existing file was kept.");
                }
                return;
            }
            if (host != null) {
                host.setStatus("⏳ Exporting rose PNG…");
            }
            writePngAtomically(file.toPath(), canvas.toImage());
            FarmUi.updateStatusBadge(infoLabel, "PNG saved · " + file.getName(),
                    FarmUi.Tone.SUCCESS);
            if (host != null) {
                host.setStatus("Exported rose visualizer PNG → " + file.getName());
            }
            if (panel.isShowing()) {
                dialogs.showMessage(panel,
                        "Saved:\n" + file.getAbsolutePath(),
                        "Export complete", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            FarmUi.updateStatusBadge(infoLabel, "PNG export failed · retry", FarmUi.Tone.DANGER);
            if (host != null) {
                host.setStatus("Rose PNG export failed. Review the destination and retry.");
            }
            if (panel.isShowing()) {
                dialogs.showMessage(panel, ex.getMessage(), "Export failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static File ensurePngExtension(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(".png")
                ? file : new File(file.getParentFile(), file.getName() + ".png");
    }

    private static void writePngAtomically(Path destination, BufferedImage image)
            throws Exception {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("Choose an existing destination folder.");
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", encoded) || encoded.size() == 0) {
            throw new IllegalStateException("The generated PNG was empty.");
        }
        Path temporary = Files.createTempFile(parent, ".rose-export-", ".tmp");
        try {
            Files.write(temporary, encoded.toByteArray());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private boolean ensureCanEdit() {
        if (host != null && !host.canMutateData()) {
            dialogs.showMessage(panel,
                    "VIEWER role cannot mutate or save rulesets.\nLog in as HAND or OWNER.",
                    "Permission denied", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void updateSliderLabels() {
        depthLabel.setText("Depth: " + depthSlider.getValue() + " / " + LSystem.MAX_DEPTH);
        angleLabel.setText("Branch angle: " + angleSlider.getValue() + "°");
        stepLabel.setText("Stem segment: " + stepSlider.getValue() + " px");
        branchLabel.setText("Branching bias: " + branchSlider.getValue() + "%");
    }

    private static JButton actionButton(String label, java.awt.event.ActionListener a) {
        JButton b = FarmUi.secondaryButton(label);
        b.addActionListener(a);
        return b;
    }

    private static JSlider slider(int min, int max, int value, String accessibleName) {
        JSlider s = new JSlider(min, max, value);
        s.setMajorTickSpacing(Math.max(1, (max - min) / 4));
        s.setPaintTicks(true);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.getAccessibleContext().setAccessibleName(accessibleName);
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
        label.setLabelFor(slider);
        p.add(label, BorderLayout.NORTH);
        p.add(slider, BorderLayout.CENTER);
        return p;
    }
}
